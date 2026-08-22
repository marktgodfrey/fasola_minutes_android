package org.fasola.minutes.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream

internal object DatabaseInstaller {
    private const val DATABASE_NAME = "minutes.db"

    fun current(context: Context): File {
        val preferences = context.getSharedPreferences(DatabaseUpdater.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val downloadedName = preferences.getString(DatabaseUpdater.ACTIVE_DATABASE_KEY, null)
        if (downloadedName != null) {
            val downloaded = File(context.filesDir, downloadedName)
            if (downloaded.isFile && downloaded.length() > 0L) return downloaded
            preferences.edit().remove(DatabaseUpdater.ACTIVE_DATABASE_KEY).apply()
        }
        return install(context)
    }

    fun install(context: Context): File {
        val destination = context.getDatabasePath(DATABASE_NAME)
        if (destination.exists() && destination.length() > 0L) return destination

        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "$DATABASE_NAME.tmp")
        context.assets.open(DATABASE_NAME).use { input ->
            FileOutputStream(temporary).use { output -> input.copyTo(output) }
        }
        check(temporary.renameTo(destination)) { "Could not install the minutes database" }
        return destination
    }
}
