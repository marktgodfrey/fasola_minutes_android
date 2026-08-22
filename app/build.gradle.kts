import groovy.json.JsonSlurper
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val databaseManifestUrl = "https://fivefiftyfour.com/-/databases.json"
val databaseDownloadUrl = "https://fivefiftyfour.com/minutes.db"
val generatedDatabaseAssets = layout.buildDirectory.dir("generated/minutesDatabase/assets")

val prepareMinutesDatabase by tasks.registering {
    group = "build setup"
    description = "Downloads and verifies the latest Sacred Harp minutes database."
    outputs.dir(generatedDatabaseAssets)
    outputs.upToDateWhen { false }

    doLast {
        val cacheDirectory = rootProject.layout.projectDirectory.dir(".gradle/minutes-database").asFile
        val cachedHashFile = cacheDirectory.resolve("current-hash.txt")
        cacheDirectory.mkdirs()

        fun connection(url: String) = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 60_000
            useCaches = false
            instanceFollowRedirects = true
            setRequestProperty("Cache-Control", "no-cache")
        }

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        val expectedHash = if (gradle.startParameter.isOffline) {
            cachedHashFile.takeIf(File::isFile)?.readText()?.trim()?.takeIf {
                it.matches(Regex("[0-9a-fA-F]{64}"))
            } ?: error("The minutes database has not been cached. Run a build without --offline first.")
        } else {
            val manifestConnection = connection(databaseManifestUrl)
            try {
                val manifest = manifestConnection.inputStream.bufferedReader().use { it.readText() }
                val entries = JsonSlurper().parseText(manifest) as? List<*>
                val first = entries?.firstOrNull() as? Map<*, *>
                (first?.get("hash") as? String)?.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }
                    ?: error("The database manifest did not contain a valid SHA-256 hash.")
            } finally {
                manifestConnection.disconnect()
            }
        }.lowercase()

        val cachedDatabase = cacheDirectory.resolve("minutes-$expectedHash.db")
        if (!cachedDatabase.isFile || sha256(cachedDatabase) != expectedHash) {
            val temporary = Files.createTempFile(cacheDirectory.toPath(), "minutes-", ".db.tmp").toFile()
            try {
                val databaseConnection = connection(databaseDownloadUrl)
                try {
                    databaseConnection.inputStream.use { input ->
                        temporary.outputStream().use { output -> input.copyTo(output) }
                    }
                } finally {
                    databaseConnection.disconnect()
                }
                check(sha256(temporary) == expectedHash) {
                    "Downloaded minutes database does not match the manifest SHA-256 hash."
                }
                Files.move(
                    temporary.toPath(),
                    cachedDatabase.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } finally {
                temporary.delete()
            }
        }
        cachedHashFile.writeText("$expectedHash\n")

        val outputDatabase = generatedDatabaseAssets.get().file("minutes.db").asFile
        outputDatabase.parentFile.mkdirs()
        if (!outputDatabase.isFile || sha256(outputDatabase) != expectedHash) {
            Files.copy(cachedDatabase.toPath(), outputDatabase.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        logger.lifecycle("Using minutes database SHA-256 $expectedHash")
    }
}

android {
    namespace = "org.fasola.minutes"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.fasola.minutes"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets["main"].assets.directories.add(generatedDatabaseAssets.get().asFile.absolutePath)

}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(prepareMinutesDatabase)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
