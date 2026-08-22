package org.fasola.minutes.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal object DatabaseUpdater {
    private const val TAG = "DatabaseUpdater"
    internal const val PREFERENCES_NAME = "database_updates"
    internal const val ACTIVE_DATABASE_KEY = "active_database"
    private const val MANIFEST_URL = "https://fivefiftyfour.com/-/databases.json"
    private const val DATABASE_URL = "https://fivefiftyfour.com/minutes.db"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 60_000

    /** Returns true after a newer, verified database has been activated. */
    fun checkForUpdate(context: Context): Boolean {
        val applicationContext = context.applicationContext
        val expectedHash = fetchLatestHash() ?: return false
        val current = DatabaseInstaller.current(applicationContext)
        val preferences = applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val storedHash = preferences.getString(hashKey(current), null)
        val currentHash = storedHash ?: sha256(current).also {
            preferences.edit().putString(hashKey(current), it).apply()
        }
        Log.i(TAG, "Manifest hash=$expectedHash, current hash=$currentHash")
        if (expectedHash.equals(currentHash, ignoreCase = true)) {
            Log.i(TAG, "Database is already up to date")
            return false
        }

        val temporary = File.createTempFile("minutes-update-", ".db", applicationContext.cacheDir)
        return try {
            Log.i(TAG, "Downloading database update")
            download(DATABASE_URL, temporary)
            val downloadedHash = sha256(temporary)
            check(expectedHash.equals(downloadedHash, ignoreCase = true)) {
                "Downloaded database hash does not match the manifest"
            }
            validateDatabase(temporary)
            Log.i(TAG, "Downloaded database passed hash and SQLite validation")

            val fileName = "minutes-$downloadedHash.db"
            val destination = File(applicationContext.filesDir, fileName)
            if (!destination.exists()) {
                check(temporary.renameTo(destination)) { "Could not activate the database update" }
            }
            preferences.edit()
                .putString(ACTIVE_DATABASE_KEY, fileName)
                .putString(hashKey(destination), downloadedHash)
                .apply()
            removeOldUpdates(applicationContext, destination)
            Log.i(TAG, "Activated database update $fileName")
            true
        } finally {
            temporary.delete()
        }
    }

    private fun fetchLatestHash(): String? {
        val connection = openConnection(MANIFEST_URL)
        return try {
            connection.inputStream.bufferedReader().use { reader ->
                val entries = JSONArray(reader.readText())
                if (entries.length() == 0) null
                else entries.optJSONObject(0)?.optString("hash")?.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun download(url: String, destination: File) {
        val connection = openConnection(url)
        try {
            connection.inputStream.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            instanceFollowRedirects = true
            setRequestProperty("Cache-Control", "no-cache")
        }

    private fun validateDatabase(file: File) {
        val database = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        database.use {
            it.rawQuery("PRAGMA quick_check", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)) {
                    "Downloaded database failed SQLite validation"
                }
            }
        }
    }

    private fun removeOldUpdates(context: Context, active: File) {
        context.filesDir.listFiles { file ->
            file.name.startsWith("minutes-") && file.extension == "db" && file != active
        }?.forEach(File::delete)
    }

    private fun hashKey(file: File) = "hash_${file.name}"

    internal fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
