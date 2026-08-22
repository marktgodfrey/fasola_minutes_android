# FaSoLa Minutes for Android

FaSoLa Minutes is a native Android app for browsing and searching the Minutes of Sacred Harp Singings. It is an Android port of the existing iOS app and uses the same minutes database and update source.

The current app version is `0.1.0` and supports Android 8.0 (API 26) and newer.

## Features

- Browse and search singers, songs, and singings
- View singer activity, frequently led songs, individual lessons, and mapped locations
- Browse the 2025 and 1991 editions of *The Sacred Harp*
- Sort and filter songs by musical and page metadata
- Read song metadata and hymn text
- View yearly song-use and singer-activity charts
- Read complete singing minutes and follow links among singings, songs, and singers
- Stream available recordings with queue, play/pause, and next controls
- Explore singing locations and song-popularity heatmaps
- Receive verified minutes-database updates

## Data and network access

The approximately 79 MB read-only database is not stored in Git. Before a normal build, Gradle reads the current hash from the iOS-compatible update manifest, downloads the database when needed, verifies its SHA-256 hash, and packages it as a generated Android asset. Verified downloads are cached in the ignored `.gradle/minutes-database/` directory.

The first build therefore requires internet access. After one successful build, `./gradlew --offline …` can reuse the verified cached database. On first app launch, the packaged database is copied into private application storage.

On subsequent launches, the app checks the same update manifest used by the iOS edition. A replacement database is activated only after its SHA-256 hash matches the manifest and it passes an SQLite integrity check. Internet access is also used for database updates, OpenStreetMap tiles, and streamed recordings. Android backup is disabled for the app's private data.

## Development setup

1. Install Android Studio and Android SDK 37.
2. Open this repository in Android Studio.
3. Allow the initial Gradle sync to finish.
4. Run the shared `app` configuration on an Android 8.0 (API 26) or newer device or emulator.

The project uses Kotlin, Jetpack Compose, Material 3, Java 17, Android Gradle Plugin 9.3.1, and Gradle 9.5.

To build from a macOS terminal using Android Studio's bundled JDK:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug
```

The resulting debug APK is located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Run the local unit tests and Android lint checks with:

```sh
./gradlew test lint
```

## Project status

The main browsing, detail, search, sorting, filtering, chart, map, audio, help, and database-update paths are implemented. The app remains an early release; current work is focused on iOS parity, device testing, polish, accessibility, and release readiness.

See `AGENTS.md` for the architecture map, parity references, and contribution guidance.

## License

The Android application source code is available under the [MIT License](LICENSE).

The Sacred Harp minutes database, recordings, artwork, names, trademarks, and other third-party content are not covered by the MIT License and remain subject to their respective rights and terms.
