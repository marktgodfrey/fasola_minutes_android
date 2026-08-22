package org.fasola.minutes.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StringCleanupTest {
    @Test
    fun removeNewLines_matchesIosCleanup() {
        assertEquals(
            "Friday-Saturday-Sunday",
            "Friday\\nSaturday\\\nSunday".removeNewLines(),
        )
    }

    @Test
    fun removeNewLines_leavesOrdinaryNamesUnchanged() {
        assertEquals("All-Day Singing", "All-Day Singing".removeNewLines())
    }
}
