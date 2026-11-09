# Tara — Android voice agent

Phase 1: local typed commands, explicitly on-device push-to-talk where Android supports it, and real Android action adapters. No network permission, model download, background microphone service, wake word, direct calling or SMS delivery.

Local verification: **49 JVM tests passed; 17 instrumentation tests passed, 1 conditional skip**, on the available API 37 emulator. Build, lint and 16 KB alignment checks pass; 22 unsuppressed upgrade/compatibility warnings remain. Physical device and API 29–36 acceptance are still pending. See the [verification report](qa/phase1/REPORT.md).

## Build and run

Use Android Studio supporting AGP 8.13.2, JDK 17, SDK Platform 36 and Build Tools 35.0.0+. Open this directory (the isolated worktree when applicable), not the original legacy checkout. Set Android Studio's Gradle JDK to 17. Create an untracked local.properties with sdk.dir pointing at your SDK, or set ANDROID_HOME.

Windows PowerShell:

~~~powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat testDebugUnitTest :core:domain:test lintDebug assembleDebug
.\gradlew.bat :app:installDebug
adb shell am start -n dev.voice.offline/dev.tara.app.MainActivity
~~~

Linux/macOS: use JDK 17 and bash ./gradlew with the same task names. Debug APK: app/build/outputs/apk/debug/app-debug.apk. No signing keys are needed for debug. Release builds require your own signing configuration; production signing is deliberately not wired to the debug key.

## Commands

| Command | Behavior |
|---|---|
| lock phone | Explicit admin setup, secure screen lock and confirmation required |
| call +919876543210 | Opens dialer; user places call |
| sms 9876543210 saying Meet at 5 PM | Shows exact recipient/body; opens composer only after confirmation |
| open Camera | Resolves scoped launchable apps; asks when names collide |
| flashlight on / flashlight off | Camera permission and actual torch hardware required |
| volume up / volume down | Adjusts media volume on current audio route |
| alarm for 7 am / alarm at 19:30 | Opens clock using local time; no claim that alarm was saved |
| timer for 5 minutes | Opens clock; duration 1 second–24 hours |

Choose contact number uses Android's phone-number picker without contacts permission. It fills the command editor; review and execute. Contact-name commands explain the picker route. Named contact resolution via contacts permission is not enabled in this phase. Ambiguous app/name resolutions are bounded to 30 seconds.

Tap the microphone to start and Stop to finish; Cancel discards capture. On API 31+ Tara uses only createOnDeviceSpeechRecognizer when available. Otherwise a real PCM capture diagnostic reports capture duration/amplitude, discards samples and leaves typed commands available. No fabricated transcripts and no network recognizer fallback. Android may require a locally installed language pack.

## Source layout

The active application source is app/src/phase1. Eleven modules separate domain, audio, permissions, design, Android adapters, settings and features. Legacy app/src/main/java and old assets remain in Git for reference and are excluded from the active build. Do not use historical offline/play flavor tasks or legacy wake-word/model setup instructions for Phase 1.

See [architecture](docs/ARCHITECTURE.md), [permissions](docs/PERMISSIONS.md), [security](docs/SECURITY.md), [testing](docs/TESTING.md), [roadmap](docs/ROADMAP.md), and [verification report](qa/phase1/REPORT.md).
