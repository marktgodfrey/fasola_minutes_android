package org.fasola.minutes.data

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatabaseUpdaterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun sha256HashesFileContents() {
        val file = temporaryFolder.newFile("database.db")
        file.writeText("FaSoLa Minutes")

        assertEquals(
            "beee04cb922cc4f65903f0b592498d37b51c9443c15794908370196442839358",
            DatabaseUpdater.sha256(file),
        )
    }
}
