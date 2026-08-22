package org.fasola.minutes.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

class MinutesRepository(context: Context) : AutoCloseable {
    private val database: SQLiteDatabase by lazy {
        SQLiteDatabase.openDatabase(
            DatabaseInstaller.current(context).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
    }

    override fun close() {
        if (database.isOpen) database.close()
    }

    fun singings(query: String = ""): List<SingingSummary> {
        val filter = query.trim()
        val sql = buildString {
            append("SELECT id, name, location, date, year FROM minutes ")
            append("WHERE NOT (year = 1992 AND DateOrdinal < 727230) ")
            if (filter.isNotEmpty()) append("AND (name LIKE ? OR location LIKE ? OR date LIKE ?) ")
            // DateOrdinal is only populated for the oldest imported minutes. IDs are assigned
            // chronologically within each annual minutes volume, so use them to order the rows
            // in each year and show the oldest singings first.
            append("ORDER BY year ASC, id ASC")
        }
        val args = if (filter.isEmpty()) emptyArray() else Array(3) { "%$filter%" }
        return database.rawQuery(sql, args).use { cursor ->
            cursor.mapRows {
                SingingSummary(int("id"), text("name").removeNewLines(), text("location"), text("date"), int("year"))
            }
        }
    }

    fun singing(id: Int): SingingDetail? {
        val summary = database.rawQuery(
            """SELECT id, name, location, date, year, minutes,
                      (SELECT COUNT(DISTINCT leader_id) FROM song_leader_joins WHERE minutes_id = minutes.id) AS leader_count,
                      (SELECT COUNT(DISTINCT song_id) FROM song_leader_joins WHERE minutes_id = minutes.id) AS song_count
               FROM minutes WHERE id = ?""",
            arrayOf(id.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            SingingDetail(
                summary = SingingSummary(cursor.int("id"), cursor.text("name").removeNewLines(), cursor.text("location"), cursor.text("date"), cursor.int("year")),
                minutes = cursor.text("minutes"),
                lessons = emptyList(),
                leaderCount = cursor.int("leader_count"),
                songCount = cursor.int("song_count"),
            )
        }
        val lessons = database.rawQuery(
            """SELECT song_leader_joins.lesson_id, songs.id AS song_id,
                      book_song_joins.page_num, songs.title,
                      GROUP_CONCAT(DISTINCT leaders.name) AS leader_names,
                      MAX(song_leader_joins.audio_url) AS audio_url
               FROM song_leader_joins
               JOIN songs ON songs.id = song_leader_joins.song_id
               JOIN leaders ON leaders.id = song_leader_joins.leader_id
               JOIN minutes ON minutes.id = song_leader_joins.minutes_id
               JOIN books ON books.year = minutes.DensonYear
               JOIN book_song_joins ON book_song_joins.song_id = songs.id AND book_song_joins.book_id = books.id
               WHERE minutes.id = ?
               GROUP BY song_leader_joins.lesson_id, songs.id, book_song_joins.page_num, songs.title
               ORDER BY song_leader_joins.lesson_id""",
            arrayOf(id.toString()),
        ).use { cursor ->
            cursor.mapRows {
                Lesson(
                    id = int("lesson_id"),
                    songId = int("song_id"),
                    page = text("page_num"),
                    title = text("title"),
                    leader = text("leader_names"),
                    audioUrl = text("audio_url").ifBlank { null },
                )
            }
        }
        return summary.copy(lessons = lessons)
    }

    fun books(): List<BookSummary> = database.rawQuery(
        "SELECT id, title, year FROM books ORDER BY year DESC",
        emptyArray(),
    ).use { cursor ->
        cursor.mapRows { BookSummary(int("id"), text("title"), int("year")) }
    }

    fun songs(bookId: Int, query: String = ""): List<SongSummary> {
        val filter = query.trim().replace("'", "’")
        val sql = """SELECT songs.id, book_song_joins.page_num, songs.title, songs.meter,
                    book_song_joins.keys, book_song_joins.times, book_song_joins.orientation,
                    COALESCE(SUM(song_stats.lesson_count), 0) AS lesson_count
                    FROM songs
                    JOIN book_song_joins ON book_song_joins.song_id = songs.id
                    LEFT JOIN song_stats ON song_stats.song_id = songs.id
                    WHERE book_song_joins.book_id = ?
                    ${if (filter.isEmpty()) "" else "AND (songs.title LIKE ? OR book_song_joins.page_num LIKE ? OR book_song_joins.text LIKE ? OR songs.music_attribution LIKE ? OR book_song_joins.words_attribution LIKE ?)"}
                    GROUP BY songs.id, book_song_joins.book_id
                    ORDER BY songs.id"""
        val args = if (filter.isEmpty()) {
            arrayOf(bookId.toString())
        } else {
            arrayOf(bookId.toString(), "%$filter%", "%$filter%", "%$filter%", "%$filter%", "%$filter%")
        }
        return database.rawQuery(sql, args).use { cursor ->
            cursor.mapRows {
                SongSummary(
                    int("id"), text("page_num"), text("title"), text("meter"), int("lesson_count"),
                    text("keys"), text("times"), text("orientation"),
                )
            }
        }
    }

    fun song(id: Int): SongDetail? {
        val detail = database.rawQuery(
            """SELECT songs.id, page_num, title, meter, text, words_attribution,
               music_attribution, keys, times,
               COALESCE((SELECT SUM(lesson_count) FROM song_stats WHERE song_id = songs.id), 0) lesson_count
               FROM songs JOIN book_song_joins ON songs.id = book_song_joins.song_id
               WHERE songs.id = ? ORDER BY book_id DESC LIMIT 1""",
            arrayOf(id.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            SongDetail(
                SongSummary(cursor.int("id"), cursor.text("page_num"), cursor.text("title"), cursor.text("meter"), cursor.int("lesson_count")),
                cursor.text("text"), cursor.text("words_attribution"), cursor.text("music_attribution"),
                cursor.text("keys"), cursor.text("times"), emptyList(), emptyList(), emptyList(), emptyList(),
            )
        }
        val stats = database.rawQuery(
            "SELECT year, lesson_count FROM song_stats WHERE song_id = ? ORDER BY year",
            arrayOf(id.toString()),
        ).use { cursor -> cursor.mapRows { YearCount(int("year"), int("lesson_count")) } }
        val leaders = database.rawQuery(
            """SELECT leaders.id, leaders.name, leader_song_stats.lesson_count
               FROM leader_song_stats
               JOIN leaders ON leaders.id = leader_song_stats.leader_id
               WHERE leader_song_stats.song_id = ?
               ORDER BY leader_song_stats.lesson_count DESC, leaders.name COLLATE NOCASE""",
            arrayOf(id.toString()),
        ).use { cursor ->
            cursor.mapRows { SongLeader(int("id"), text("name"), int("lesson_count")) }
        }
        val recordings = database.rawQuery(
            """SELECT minutes.id AS minutes_id, minutes.name AS minutes_name,
                      minutes.location, minutes.date, minutes.year,
                      song_leader_joins.lesson_id,
                      GROUP_CONCAT(DISTINCT leaders.name) AS leader_names,
                      MAX(song_leader_joins.audio_url) AS audio_url
               FROM song_leader_joins
               JOIN leaders ON leaders.id = song_leader_joins.leader_id
               JOIN minutes ON minutes.id = song_leader_joins.minutes_id
               WHERE song_leader_joins.song_id = ?
                 AND song_leader_joins.audio_url IS NOT NULL
                 AND song_leader_joins.lesson_id = (
                     SELECT MIN(first_lesson.lesson_id)
                     FROM song_leader_joins AS first_lesson
                     WHERE first_lesson.song_id = song_leader_joins.song_id
                       AND first_lesson.minutes_id = song_leader_joins.minutes_id
                       AND first_lesson.audio_url IS NOT NULL
                 )
               GROUP BY minutes.id, song_leader_joins.lesson_id
               ORDER BY minutes.id, song_leader_joins.lesson_id""",
            arrayOf(id.toString()),
        ).use { cursor ->
            cursor.mapRows {
                SongRecording(
                    Lesson(
                        int("lesson_id"), detail.summary.id, detail.summary.page, detail.summary.title,
                        text("leader_names"), text("audio_url").ifBlank { null },
                    ),
                    SingingSummary(
                        int("minutes_id"), text("minutes_name").removeNewLines(), text("location"),
                        text("date"), int("year"),
                    ),
                )
            }
        }
        val neighbors = database.rawQuery(
            """SELECT songs.id, book_song_joins.page_num, songs.title
               FROM song_neighbors
               JOIN songs ON songs.id = song_neighbors.to_song_id
               JOIN book_song_joins ON book_song_joins.id = (
                   SELECT id FROM book_song_joins
                   WHERE song_id = songs.id ORDER BY book_id DESC LIMIT 1
               )
               WHERE song_neighbors.from_song_id = ?
               ORDER BY song_neighbors.rank LIMIT 10""",
            arrayOf(id.toString()),
        ).use { cursor ->
            cursor.mapRows { SongNeighbor(int("id"), text("page_num"), text("title")) }
        }
        return detail.copy(yearlyUse = stats, recordings = recordings, topLeaders = leaders, neighbors = neighbors)
    }

    fun leaderSongLessons(leaderId: Int, songId: Int): List<LeaderSongLesson> = database.rawQuery(
        """SELECT song_leader_joins.lesson_id, minutes.id, minutes.name, minutes.location,
                  minutes.date, minutes.year
           FROM song_leader_joins
           JOIN minutes ON minutes.id = song_leader_joins.minutes_id
           WHERE song_leader_joins.leader_id = ? AND song_leader_joins.song_id = ?
           ORDER BY minutes.year, minutes.id, song_leader_joins.lesson_id""",
        arrayOf(leaderId.toString(), songId.toString()),
    ).use { cursor ->
        cursor.mapRows {
            LeaderSongLesson(
                lessonId = int("lesson_id"),
                singing = SingingSummary(int("id"), text("name").removeNewLines(), text("location"), text("date"), int("year")),
            )
        }
    }

    fun leaders(query: String = ""): List<LeaderSummary> {
        val filter = query.trim()
        val sql = """SELECT id, name, lesson_count, location_count, top20_count FROM leaders
            ${if (filter.isEmpty()) "" else "WHERE name LIKE ?"}"""
        val args = if (filter.isEmpty()) emptyArray() else arrayOf("%$filter%")
        return database.rawQuery(sql, args).use { cursor ->
            cursor.mapRows {
                LeaderSummary(int("id"), text("name"), int("lesson_count"), int("location_count"), int("top20_count"))
            }
        }.sortedWith(leaderNameComparator)
    }

    fun leader(id: Int): LeaderDetail? {
        val summary = database.rawQuery(
            "SELECT id, name, lesson_count, location_count, top20_count FROM leaders WHERE id = ?",
            arrayOf(id.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            LeaderSummary(
                cursor.int("id"), cursor.text("name"), cursor.int("lesson_count"),
                cursor.int("location_count"), cursor.int("top20_count"),
            )
        }
        val aliases = database.rawQuery(
            "SELECT alias FROM leader_name_aliases WHERE leader_id = ? ORDER BY alias COLLATE NOCASE",
            arrayOf(id.toString()),
        ).use { cursor -> cursor.mapRows { text("alias") } }
        val yearlySingings = database.rawQuery(
            """SELECT minutes.year, COUNT(DISTINCT song_leader_joins.minutes_id) AS singing_count
               FROM song_leader_joins
               JOIN minutes ON minutes.id = song_leader_joins.minutes_id
               WHERE song_leader_joins.leader_id = ?
               GROUP BY minutes.year ORDER BY minutes.year""",
            arrayOf(id.toString()),
        ).use { cursor -> cursor.mapRows { YearCount(int("year"), int("singing_count")) } }
        val songs = database.rawQuery(
            """SELECT songs.id, songs.title, leader_song_stats.lesson_count,
                      leader_song_stats.lesson_rank <= 20 AND leader_song_stats.lesson_count >= 5 AS is_top_twenty
               FROM leader_song_stats
               JOIN songs ON songs.id = leader_song_stats.song_id
               WHERE leader_song_stats.leader_id = ?
               ORDER BY leader_song_stats.lesson_count DESC""",
            arrayOf(id.toString()),
        ).use { cursor ->
            cursor.mapRows {
                LeaderSong(int("id"), text("title"), int("lesson_count"), int("is_top_twenty") != 0)
            }
        }
        return LeaderDetail(summary, aliases, yearlySingings, songs)
    }

    fun leaderLessons(id: Int): List<LeaderLesson> = database.rawQuery(
        """SELECT song_leader_joins.lesson_id, songs.id AS song_id,
                  book_song_joins.page_num, songs.title, leaders.name AS leader_name,
                  song_leader_joins.audio_url,
                  minutes.id AS minutes_id, minutes.name AS minutes_name,
                  minutes.location, minutes.date, minutes.year
           FROM song_leader_joins
           JOIN leaders ON leaders.id = song_leader_joins.leader_id
           JOIN songs ON songs.id = song_leader_joins.song_id
           JOIN minutes ON minutes.id = song_leader_joins.minutes_id
           JOIN books ON books.year = minutes.DensonYear
           JOIN book_song_joins ON book_song_joins.song_id = songs.id
               AND book_song_joins.book_id = books.id
           WHERE song_leader_joins.leader_id = ?
           ORDER BY song_leader_joins.id""",
        arrayOf(id.toString()),
    ).use { cursor ->
        cursor.mapRows {
            LeaderLesson(
                lesson = Lesson(
                    id = int("lesson_id"),
                    songId = int("song_id"),
                    page = text("page_num"),
                    title = text("title"),
                    leader = text("leader_name"),
                    audioUrl = text("audio_url").ifBlank { null },
                ),
                singing = SingingSummary(
                    id = int("minutes_id"),
                    name = text("minutes_name").removeNewLines(),
                    location = text("location"),
                    date = text("date"),
                    year = int("year"),
                ),
            )
        }
    }

    fun locations(singingId: Int? = null, leaderId: Int? = null): List<MapLocation> {
        val (sql, args) = when {
            singingId != null -> Pair(
                """SELECT DISTINCT locations.id, locations.name, locations.gps_lat, locations.gps_long
                   FROM locations JOIN minutes_location_joins ON minutes_location_joins.location_id = locations.id
                   WHERE locations.gps_lat IS NOT NULL AND minutes_location_joins.minutes_id = ?""",
                arrayOf(singingId.toString()),
            )
            leaderId != null -> Pair(
                """SELECT DISTINCT locations.id, locations.name, locations.gps_lat, locations.gps_long
                   FROM locations JOIN minutes_location_joins ON minutes_location_joins.location_id = locations.id
                   JOIN song_leader_joins ON song_leader_joins.minutes_id = minutes_location_joins.minutes_id
                   WHERE locations.gps_lat IS NOT NULL AND song_leader_joins.leader_id = ?""",
                arrayOf(leaderId.toString()),
            )
            else -> Pair(
                "SELECT id, name, gps_lat, gps_long FROM locations WHERE gps_lat IS NOT NULL",
                emptyArray(),
            )
        }
        return database.rawQuery(sql, args).use { cursor ->
            cursor.mapRows { MapLocation(int("id"), text("name"), double("gps_lat"), double("gps_long")) }
        }
    }

    fun singingHasMappedLocation(singingId: Int): Boolean = database.rawQuery(
        """SELECT EXISTS(
               SELECT 1 FROM minutes_location_joins
               JOIN locations ON locations.id = minutes_location_joins.location_id
               WHERE minutes_location_joins.minutes_id = ?
                 AND locations.gps_lat IS NOT NULL
                 AND locations.gps_long IS NOT NULL
           ) AS has_location""",
        arrayOf(singingId.toString()),
    ).use { cursor -> cursor.moveToFirst() && cursor.int("has_location") != 0 }

    fun songHasMappedLocation(songId: Int): Boolean = database.rawQuery(
        """SELECT EXISTS(
               SELECT 1 FROM song_leader_joins
               JOIN minutes_location_joins ON minutes_location_joins.minutes_id = song_leader_joins.minutes_id
               JOIN locations ON locations.id = minutes_location_joins.location_id
               WHERE song_leader_joins.song_id = ?
                 AND locations.gps_lat IS NOT NULL
                 AND locations.gps_long IS NOT NULL
           ) AS has_location""",
        arrayOf(songId.toString()),
    ).use { cursor -> cursor.moveToFirst() && cursor.int("has_location") != 0 }

    fun leaderHasMappedLocation(leaderId: Int): Boolean = database.rawQuery(
        """SELECT EXISTS(
               SELECT 1 FROM song_leader_joins
               JOIN minutes_location_joins ON minutes_location_joins.minutes_id = song_leader_joins.minutes_id
               JOIN locations ON locations.id = minutes_location_joins.location_id
               WHERE song_leader_joins.leader_id = ?
                 AND locations.gps_lat IS NOT NULL
                 AND locations.gps_long IS NOT NULL
           ) AS has_location""",
        arrayOf(leaderId.toString()),
    ).use { cursor -> cursor.moveToFirst() && cursor.int("has_location") != 0 }

    fun singingsAtLocation(locationId: Int, leaderId: Int? = null): List<SingingSummary> {
        val leaderJoin = if (leaderId == null) "" else
            "JOIN song_leader_joins ON song_leader_joins.minutes_id = minutes.id"
        val leaderFilter = if (leaderId == null) "" else "AND song_leader_joins.leader_id = ?"
        val args = if (leaderId == null) arrayOf(locationId.toString()) else
            arrayOf(locationId.toString(), leaderId.toString())
        return database.rawQuery(
            """SELECT DISTINCT minutes.id, minutes.name, minutes.location, minutes.date, minutes.year, minutes.DateOrdinal
               FROM minutes JOIN minutes_location_joins ON minutes_location_joins.minutes_id = minutes.id
               $leaderJoin
               WHERE minutes_location_joins.location_id = ? $leaderFilter
               ORDER BY minutes.DateOrdinal DESC""",
            args,
        ).use { cursor ->
            cursor.mapRows { SingingSummary(int("id"), text("name").removeNewLines(), text("location"), text("date"), int("year")) }
        }
    }

    fun songHeatPoints(songId: Int): List<SongHeatPoint> {
        val totalLessons = database.rawQuery(
            "SELECT COUNT(*) AS count FROM (SELECT DISTINCT lesson_id, song_id, minutes_id FROM song_leader_joins)",
            emptyArray(),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.int("count") else 0 }
        val songLessons = database.rawQuery(
            "SELECT COUNT(*) AS count FROM (SELECT DISTINCT lesson_id, minutes_id FROM song_leader_joins WHERE song_id = ?)",
            arrayOf(songId.toString()),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.int("count") else 0 }
        if (totalLessons == 0 || songLessons == 0) return emptyList()
        val overallFraction = songLessons.toDouble() / totalLessons
        return database.rawQuery(
            """SELECT locations.id, locations.name, locations.gps_lat, locations.gps_long,
                      COUNT(song_leader_joins.id) AS song_count,
                      (SELECT COUNT(*) FROM song_leader_joins all_lessons
                       JOIN minutes_location_joins all_locations ON all_locations.minutes_id = all_lessons.minutes_id
                       WHERE all_locations.location_id = locations.id) AS location_count
               FROM locations
               JOIN minutes_location_joins ON minutes_location_joins.location_id = locations.id
               JOIN song_leader_joins ON song_leader_joins.minutes_id = minutes_location_joins.minutes_id
               WHERE locations.gps_lat IS NOT NULL AND song_leader_joins.song_id = ?
               GROUP BY locations.id""",
            arrayOf(songId.toString()),
        ).use { cursor ->
            cursor.mapRows {
                val localFraction = int("song_count").toDouble() / int("location_count").coerceAtLeast(1)
                SongHeatPoint(
                    MapLocation(int("id"), text("name"), double("gps_lat"), double("gps_long")),
                    kotlin.math.ln(localFraction / overallFraction + 1e-8),
                )
            }
        }
    }

    private inline fun <T> Cursor.mapRows(block: Cursor.() -> T): List<T> = buildList {
        while (moveToNext()) add(block())
    }

    private fun Cursor.columnIndex(column: String): Int =
        columnNames.indexOfFirst { it.equals(column, ignoreCase = true) }
            .takeIf { it >= 0 }
            ?: throw IllegalArgumentException(
                "Column '$column' does not exist. Available columns: ${columnNames.contentToString()}",
            )

    private fun Cursor.text(column: String): String =
        columnIndex(column).let { if (isNull(it)) "" else getString(it) }

    private fun Cursor.int(column: String): Int =
        columnIndex(column).let { if (isNull(it)) 0 else getInt(it) }

    private fun Cursor.double(column: String): Double =
        columnIndex(column).let { if (isNull(it)) 0.0 else getDouble(it) }
}
