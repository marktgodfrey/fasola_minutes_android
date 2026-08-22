# FaSoLa Minutes Android — Agent Guide

## Product direction

This project is the native Android edition of the sibling iOS app in `../fasola_minutes`.

Treat the iOS implementation as the product specification. Match its displayed metadata, wording, ordering, sorting and filtering behavior, section structure, navigation, colors, typography, charts, maps, and playback behavior unless the user explicitly asks for an Android-specific departure. Inspect the corresponding Objective-C controller or view before implementing or changing a feature.

The Android app is now a functional port rather than a list-only prototype. Favor completing and polishing iOS parity, correctness, and release readiness over broad redesigns.

## Repositories and data

- Android project: this repository (`fasola_minutes_android`)
- Canonical iOS app: `../fasola_minutes`
- Canonical iOS database: `../fasola_minutes/minutes.db`
- Android generated database asset: `app/build/generated/minutesDatabase/assets/minutes.db`

Do not modify the iOS repository while doing Android work unless the user explicitly requests it. Existing iOS changes belong to the user.

The database is not committed to this repository. Before a normal build, Gradle reads the iOS-compatible update manifest, downloads the current approximately 79 MB read-only SQLite database when it is not already cached, verifies its SHA-256 hash, and exposes it as a generated Android asset. Verified downloads are cached under the ignored `.gradle/minutes-database/` directory; `--offline` builds use that cache and fail clearly if it has not been populated.

On first launch the app copies the generated asset into private application storage. At launch it also checks the same update manifest, downloads a changed database, verifies its SHA-256 hash and SQLite integrity, and activates it only after both checks pass. Android backup is disabled, so installed or downloaded database state is not backed up.

Database column names include inconsistent capitalization; use the repository's case-insensitive cursor helpers. Preserve the database schema and iOS query semantics. Use explicit SQL aliases where Android cursor labels would otherwise be ambiguous.

## Android stack

- Kotlin and Java 17
- Jetpack Compose and Material 3
- Android Gradle Plugin 9.3.1
- Gradle 9.5
- Minimum Android API 26 (Android 8.0)
- Compile/target API 37
- Direct read-only Android SQLite access
- osmdroid for maps
- Android `MediaPlayer` for streamed recordings

## Current product surface

- Singers, Songs, and Singings bottom-navigation sections
- Search across singers, songs, and singings
- Alphabetic/year side indexes where applicable
- Singer sorting by name, total lessons, or top-20 count
- Singer details, yearly activity chart, songs by frequency, all lessons, song-specific lessons, and mapped locations
- 2025 and 1991 Sacred Harp book selection
- Song sorting by page or lesson count
- Song filters for page range, page position/side, time, mode, key, and meter
- Song metadata, hymn text, yearly-use chart, recordings, top leaders, neighboring songs, and relative-popularity heatmap
- Singing details, ordered lessons, complete minutes text, mapped location, and song/leader navigation
- Streaming audio queues with play/pause and next controls
- All-singings map plus filtered singer and singing maps using OpenStreetMap tiles
- Help and credits screen
- Automatic verified database updates

## Code map

- `app/src/main/java/org/fasola/minutes/MainActivity.kt` — application entry point, database opening, and background update check
- `app/src/main/java/org/fasola/minutes/data/Models.kt` — UI/domain models and singer-name helpers
- `app/src/main/java/org/fasola/minutes/data/DatabaseInstaller.kt` — bundled/downloaded database selection and first-launch installation
- `app/src/main/java/org/fasola/minutes/data/DatabaseUpdater.kt` — manifest check, download, hashing, validation, and activation
- `app/build.gradle.kts` — build-time manifest lookup, verified database download/cache, and generated-asset wiring
- `app/src/main/java/org/fasola/minutes/data/MinutesRepository.kt` — SQLite queries and model hydration
- `app/src/main/java/org/fasola/minutes/ui/MinutesApp.kt` — Compose navigation and the main lists/detail screens
- `app/src/main/java/org/fasola/minutes/ui/SongFilters.kt` — song filter model and dialog
- `app/src/main/java/org/fasola/minutes/ui/AudioPlayer.kt` — recording queue and `MediaPlayer` lifecycle
- `app/src/main/java/org/fasola/minutes/ui/MapScreens.kt` — location maps and song heatmaps
- `app/src/main/java/org/fasola/minutes/ui/theme/Theme.kt` — shared colors and theme
- `app/src/test/java/org/fasola/minutes/` — local unit tests for update hashing, sorting, cleanup, and song filters

`MinutesApp.kt` remains large. When making substantial additions, split coherent features into their own files while preserving navigation and behavior.

## iOS parity references

Useful canonical files in `../fasola_minutes/minutes`:

- `SongsListViewController.m` — book selector, rows, sorting, and filtering
- `SongViewController.m` — song details, recordings, leaders, and neighboring songs
- `SongUseGraphCell.m` — yearly bar chart behavior
- `SongHeatmapViewController.m` — song popularity heatmap behavior
- `SingingsListViewController.m` — year sections, search, and rows
- `SingingViewController.m` and `SingingTextViewController.m` — singing details and full minutes
- `LeadersListViewController.m` — singer rows, sections, search, and sort modes
- `LeaderViewController.m` — singer details and lesson navigation
- `SingingLocationsViewController.m` — singing and singer location maps
- `MinutesData.m` — canonical SQL and model hydration
- `MinutesHelper.m` — canonical colors, fonts, strings, and display helpers

## Build and verification

Android Studio supplies the JDK and SDK on the development Mac. From a terminal:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Run local tests and lint with:

```sh
./gradlew test lint
```

For UI, navigation, map, or playback changes, do not stop at compilation. Install the APK on a running emulator/device, exercise the affected path, inspect logcat for crashes, and visually inspect screenshots when layout matters. Network-dependent database updates, map tiles, navigation intents, and recordings need device-level verification.

## Working rules

- Inspect the corresponding iOS behavior before implementing a parity change.
- Preserve the bundled database schema; do not migrate or rewrite it casually.
- Keep database, hashing, and network work off the Compose UI thread.
- Treat a downloaded database as untrusted until both its manifest hash and SQLite check pass.
- Keep the build-time downloader aligned with the runtime updater's manifest and database URLs.
- Every lazy-list item must have a stable, unique key. Songs can occur in both book editions.
- Default to the newest book while retaining the edition selector.
- Preserve audio-player lifecycle cleanup and queue behavior when changing navigation.
- Keep osmdroid lifecycle handling inside the managed map wrapper.
- Handle query, display, playback, map, and update failures without terminating the app.
- Do not commit database binaries, `local.properties`, `.idea`, `.gradle`, `app/build`, or generated APKs.
- Build after every meaningful change, run focused tests, and test affected UI paths on an emulator/device when available.
