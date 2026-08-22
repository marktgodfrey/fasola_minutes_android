package org.fasola.minutes.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LeaderSummaryTest {
    @Test
    fun `singers sort by last name then first name`() {
        val singers = listOf(
            leader(1, "Zelda Brown"),
            leader(2, "Alpha Smith"),
            leader(3, "Beta Brown"),
            leader(4, "Alpha Brown"),
        )

        assertEquals(
            listOf("Alpha Brown", "Beta Brown", "Zelda Brown", "Alpha Smith"),
            singers.sortedWith(leaderNameComparator).map(LeaderSummary::name),
        )
    }

    @Test
    fun `surname initial comes from final name component`() {
        val singer = leader(1, "  L. E. Hannah  ")

        assertEquals("Hannah", singer.lastName)
        assertEquals("L. E.", singer.firstName)
        assertEquals("H", singer.lastNameInitial)
    }

    private fun leader(id: Int, name: String) = LeaderSummary(id, name, 0, 0)
}
