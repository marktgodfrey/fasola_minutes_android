package org.fasola.minutes.data

internal fun String.removeNewLines(): String =
    replace("\\n", "-").replace("\\\n", "-")

data class SingingSummary(
    val id: Int,
    val name: String,
    val location: String,
    val date: String,
    val year: Int,
)

data class SingingDetail(
    val summary: SingingSummary,
    val minutes: String,
    val lessons: List<Lesson>,
    val leaderCount: Int,
    val songCount: Int,
)

data class Lesson(
    val id: Int,
    val songId: Int,
    val page: String,
    val title: String,
    val leader: String,
    val audioUrl: String?,
)

data class SongSummary(
    val id: Int,
    val page: String,
    val title: String,
    val meter: String,
    val lessonCount: Int,
    val keys: String = "",
    val times: String = "",
    val orientation: String = "",
)

data class SongDetail(
    val summary: SongSummary,
    val text: String,
    val wordsAttribution: String,
    val musicAttribution: String,
    val keys: String,
    val times: String,
    val yearlyUse: List<YearCount>,
    val recordings: List<SongRecording>,
    val topLeaders: List<SongLeader>,
    val neighbors: List<SongNeighbor>,
)

data class SongRecording(val lesson: Lesson, val singing: SingingSummary)

data class YearCount(val year: Int, val count: Int)

data class SongLeader(val id: Int, val name: String, val lessonCount: Int)

data class SongNeighbor(val id: Int, val page: String, val title: String)

data class BookSummary(
    val id: Int,
    val title: String,
    val year: Int,
)

data class LeaderSummary(
    val id: Int,
    val name: String,
    val lessonCount: Int,
    val locationCount: Int,
    val top20Count: Int = 0,
) {
    private val nameParts = name.trim().split(Regex("\\s+")).filter(String::isNotEmpty)

    val lastName: String
        = nameParts.lastOrNull().orEmpty()

    val firstName: String
        = nameParts.dropLast(1).joinToString(" ")

    val lastNameInitial: String
        = lastName.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
}

val leaderNameComparator: Comparator<LeaderSummary> =
    compareBy(String.CASE_INSENSITIVE_ORDER, LeaderSummary::lastName)
        .thenBy(String.CASE_INSENSITIVE_ORDER, LeaderSummary::firstName)
        .thenBy(LeaderSummary::id)

data class LeaderDetail(
    val summary: LeaderSummary,
    val aliases: List<String>,
    val yearlySingings: List<YearCount>,
    val songs: List<LeaderSong>,
)

data class LeaderLesson(
    val lesson: Lesson,
    val singing: SingingSummary,
)

data class LeaderSongLesson(
    val lessonId: Int,
    val singing: SingingSummary,
)

data class LeaderSong(
    val id: Int,
    val title: String,
    val lessonCount: Int,
    val isTopTwentyLeader: Boolean,
)

data class MapLocation(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

data class SongHeatPoint(
    val location: MapLocation,
    val relativePopularity: Double,
)
