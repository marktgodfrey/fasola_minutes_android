package org.fasola.minutes.ui

import org.fasola.minutes.data.SongSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongFilterTest {
    private val song = SongSummary(
        id = 2,
        page = "27t",
        title = "Bethel",
        meter = "Common Meter",
        lessonCount = 42,
        keys = "F Minor, F Major",
        times = "4/4, 3/4",
        orientation = "top",
    )

    @Test
    fun `empty filter accepts a song`() {
        assertTrue(SongFilter().matches(song))
    }

    @Test
    fun `filters combine using iOS semantics`() {
        assertTrue(
            SongFilter(
                pageLow = 27,
                pageHigh = 27,
                positions = setOf("top"),
                sides = setOf("Right"),
                times = setOf("3/4"),
                modes = setOf("Minor"),
                keys = setOf("F"),
                meters = setOf("Common Meter"),
            ).matches(song),
        )
        assertFalse(SongFilter(times = setOf("6/8")).matches(song))
        assertFalse(SongFilter(sides = setOf("Left")).matches(song))
        assertFalse(SongFilter(positions = setOf("bottom")).matches(song))
    }

    @Test
    fun `selecting major and minor does not restrict mode`() {
        assertTrue(SongFilter(modes = setOf("Major", "Minor")).matches(song))
    }

    @Test
    fun `flat and sharp display forms normalize`() {
        val flatSong = song.copy(keys = "B♭ Major")
        assertTrue(SongFilter(keys = setOf("Bb")).matches(flatSong))
    }
}
