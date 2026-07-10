# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

"Message in a Jar" — a single-module Android app (Jetpack Compose, Kotlin). Users write messages that are "sealed in a jar" and revealed on a hardcoded unlock date (December 21 of each year; the jar locks again on January 1). Package: `com.ritwikg.messageinajar`. minSdk 26, target/compileSdk 36.

## Commands

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected device/emulator
./gradlew test                   # run unit tests (JVM)
./gradlew connectedAndroidTest   # run instrumented tests (needs device/emulator)
./gradlew bundleRelease          # build release .aab
./gradlew lint                   # Android lint
```

Run a single unit test class:
```bash
./gradlew test --tests "com.ritwikg.messageinajar.ExampleUnitTest"
```

## Architecture

The entire app lives in two Kotlin files under `app/src/main/java/com/ritwikg/messageinajar/`:

- **`MainActivity.kt`** — everything except the alarm receiver: the `JarScreen` composable (the only screen), the `JarMessage` data class, unlock-date logic (`isJarUnlocked`, `daysUntilUnlock`, `unlockDateForYear`), notification helpers, and the DataStore singleton. There is no ViewModel/repository layering; state flows directly from DataStore into Compose via `collectAsState`.
- **`JarAlarmReceiver.kt`** — `BroadcastReceiver` that posts the "jar unlocked" notification with Open/Snooze/Remind-later actions; "remind later" reschedules itself via `AlarmManager.setExactAndAllowWhileIdle`.

Key mechanics to know before changing things:

- **Persistence**: messages are stored as a single JSON string (kotlinx.serialization) under the `jar_messages` key in Preferences DataStore (`settings`). Reading/writing always decodes the full list, appends, and re-encodes.
- **Unlock logic is date-based, not per-message**: the UI re-derives "today" every second from the system clock/timezone and compares against the hardcoded Dec 21 unlock date. Messages don't carry individual unlock times (the `createdAt`/`tag` fields exist but unlock is global).
- **Notifications** use channel id `"jar_channel"` (created in `MainActivity.onCreate`). All notification posting must check the POST_NOTIFICATIONS runtime permission (Android 13+) first — see `notifyIfPermitted`/`sendJarNotification` for the existing pattern.

## Gotchas

- Dependencies are mostly declared in `gradle/libs.versions.toml` (version catalog), but a few (datastore, serialization-json, activity) are added as direct string coordinates at the bottom of `app/build.gradle.kts`.
- The manifest references `@mipmap/messageinajar` as the app icon; the PNG currently only exists in `mipmap-hdpi` (plus stray copies in `res/drawable` and directly under `res/` — the latter is not a valid resource location).
- Kotlin serialization requires the `kotlin.serialization` Gradle plugin (already wired); `@Serializable` classes won't compile without it.
