# Tara Phase 1 verification — 2026-11-09

Phase 1 implementation and local software checks are complete. Final hardware acceptance remains open: no physical Android device or API 29–36 system image was available. The installed AVD is Android 17 / API 37 with 16 KB pages. This is additional compatibility evidence, not an API 29–36 device matrix or a production-release approval.

## Workspace and delivery

- Branch: codex/tara-phase-one; base commit: 307875def855d01f3a438d030ad69a6ed8c5653d.
- Isolated managed checkout: C:/Users/rc821/.codex/worktrees/tara-phase-one/Tara- On Device AI.
- Recovery preserved the other process's work in the original Desktop checkout. No commit, push, PR, deployment or store submission was performed.
- Debug APK: [app-debug.apk](../../app/build/outputs/apk/debug/app-debug.apk), 29250347 bytes.
- SHA-256: def90a8fafc725c8c68339bb9ca047459cf507496fc3c398333f3476831d5d2c.
- Machine-readable evidence: [verification.json](verification.json) and [instrumentation-summary.json](instrumentation-summary.json).

## Actual results

| Check | Result |
|---|---|
| Domain JVM tests | 29 passed |
| Audio ownership JVM tests | 2 passed |
| Permission classification JVM tests | 7 passed |
| Scoped app matching JVM tests | 6 passed |
| Assistant ViewModel JVM tests | 5 passed |
| Total JVM tests | **49 passed, 0 failed** |
| Full app instrumentation | 4 passed, 1 conditional skip |
| Onboarding Compose instrumentation | 2 passed |
| Assistant Compose instrumentation | 3 passed |
| Android intent/handoff instrumentation | 8 passed |
| Total instrumentation | **17 passed, 1 skipped, 0 failed** |
| Android lint across all 10 Android modules | 0 errors; 22 visible warnings in app, 0 in other modules |
| Debug application and instrumentation APK compilation | Passed |
| APK installation and launch | Passed on API 37 emulator |
| 16 KB ZIP alignment | Passed with SDK zipalign |
| Native ELF LOAD alignment | All four arm64-v8a/x86_64 AndroidX library entries use 16384-byte alignment |
| Diff integrity | git diff --check passed |
| Hosted GitHub Actions | Workflow created; hosted execution not performed |

The conditional skip is diagnosticMicrophoneReleasesItsActualRecordingOnCancel: this emulator advertises native on-device recognition, so Tara follows its native recognizer path instead of the AudioRecord diagnostic path. The diagnostic path still needs an API 29/30 device or a device without native recognition.

The original Compose test run exposed Espresso 3.5.0 calling a removed InputManager method on API 37. Pinning test-only Espresso 3.7.0 resolved that failure; all UI suites above were rerun from the corrected test APKs. The latest direct ADB runner receipts are authoritative; older Gradle-connected XML reports may retain the superseded Espresso failures.

## Manual runtime observations

- Completed onboarding without microphone or device-admin grants and reached the working command editor.
- Typed volume down reached the real parser → policy → registry → AudioManager path. Android media volume changed from 5 to 4 and Tara reported the same result. A later volume up command restored the original value 5; the final SDK query confirmed 5 of 15.
- Native listening showed Listening, Stop and Cancel. Android AppOps reported RECORD_AUDIO running. Returning Home produced Tara's Cancelled state, and a later AppOps query showed a completed recording duration of 5.581 seconds with no running operation. This does not establish recognition accuracy or an end-to-end spoken action.
- The temporary microphone grant was revoked after testing, and emulator animation overrides were restored.
- Actual speech transcription was not verified: no controlled spoken audio was supplied to the native recognizer. No transcript was fabricated.
- The final preview exposed a typed-input labeling defect. A new test first failed with expected empty transcript versus volume up, then passed after SessionState preserved only actual recognizer output. A full-app UI regression now confirms typed input does not claim speech recognition.

## Architecture and implementation map

| Module | Principal files / behavior |
|---|---|
| :app | src/phase1/AndroidManifest.xml; MainActivity.kt, TaraApplication.kt, TaraModule.kt, AssistantViewModel.kt; Hilt wiring and lifecycle bridges |
| :core:domain | Actions.kt, Execution.kt, CommandParser.kt, AssistantSessionManager.kt; Android-free typed contracts, exhaustive dispatch, validation, policy and expiring session state |
| :core:audio | SpeechController.kt, AndroidSpeechController.kt; one capture owner, explicit on-device recognition, actual PCM diagnostic fallback, timeout/interruption cleanup |
| :core:permissions | AndroidPermissionManager.kt; current OS grants, revoked/permanent-denial handling and explicit settings/admin intents |
| :core:designsystem | TaraTheme.kt, Components.kt; Material 3 light/dark colors, accessible typography and bounded scrolling pages |
| :platform:androidactions | AndroidPlatformActions.kt, AndroidEntityResolver.kt, ActionIntents.kt, AppTargetMatcher.kt, TaraAdminReceiver.kt and force-lock XML |
| :data:settings | SettingsRepository.kt; DataStore appearance and onboarding |
| :feature:assistant | AssistantScreen.kt; microphone controls, editor, action preview, exact confirmation, target choices and result states |
| :feature:onboarding | OnboardingScreens.kt; accurate capability explanation and optional contextual setup |
| :feature:privacy | PrivacyScreen.kt; permission status and local-processing explanations |
| :feature:settings | SettingsScreen.kt; Light/Dark/System appearance and onboarding reset |

The app uses app/src/phase1 as its active source and resource set. Legacy app/src/main/java, services, AI emulation and model assets remain as history and are excluded from this build. Gradle uses the existing compatible AGP 8.13.2 / Kotlin 2.2.20 / JDK 17 baseline, SDK 36, min SDK 29, a version catalog, Compose, Hilt, Coroutines/Flow and DataStore.

All eight real action adapters are implemented: force-lock, dialer, SMS composer, scoped app launch, flashlight, media volume, clock alarm and timer. Dialer/SMS/clock results are ExternalHandoff, never claims that a call connected, a message was sent or an alarm was saved. SMS and lock require expiring confirmation. Lock additionally requires active device administration and a secure keyguard. The system phone-number picker provides contact choice without address-book permission.

Review also fixed lost permission callback identity during Activity recreation and a stale target-choice deadline that could hide microphone controls. The expiry bug was reproduced by a failing test before the fix. A read-only review rechecked those paths without remaining Critical/Important findings.

## Permissions and security

Manifest runtime permissions: RECORD_AUDIO and CAMERA. Normal permissions: MODIFY_AUDIO_SETTINGS and SET_ALARM. The device-admin receiver is protected by BIND_DEVICE_ADMIN and declares only force-lock; activation requires Android's user-facing admin flow.

No INTERNET, CALL_PHONE, SEND_SMS, READ_SMS, call logs, READ_CONTACTS, QUERY_ALL_PACKAGES, Accessibility service or foreground microphone service is shipped. No audio, transcripts, contact inventory or message content is persisted or logged by the app. Appearance, onboarding and permission-request history are local. Android backup/transfer is disabled. System actions validate input and recheck authorization at the Android boundary. No root, hidden APIs or ADB runtime dependency is used.

## Reproducing the checks

From this checkout, use JDK 17 and an SDK 36 installation:

~~~powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat :core:domain:test testDebugUnitTest lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :feature:onboarding:assembleDebugAndroidTest :feature:assistant:assembleDebugAndroidTest :platform:androidactions:assembleDebugAndroidTest
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\run-instrumentation.ps1 -Adb '<SDK>\platform-tools\adb.exe' -Serial '<device serial>'
.\gradlew.bat :app:installDebug
adb shell am start -n dev.voice.offline/dev.tara.app.MainActivity
~~~

ExecutionPolicy Bypass applies only to that test-runner process. The runner derives package IDs from APK metadata, records actual Android instrumentation status codes and fails on missing tests or failures. Standard Gradle connectedDebugAndroidTest tasks remain supported and are configured in CI for API 36. The local direct runner avoids repeated UTP host setup while running the same compiled tests.

## Remaining acceptance gates

See the reproducible step-by-step matrix in [TESTING.md](../../docs/TESTING.md). Exact blockers:

1. No physical USB/Wi-Fi Android device was connected. Physical force-lock/admin deactivation, flashlight/camera conflicts, microphone routing/privacy switches, fixed-volume/Bluetooth/DND behavior and real external dialer/SMS/clock UI still require hardware checks.
2. Only API 37 system images were installed. API 29, 30, 31, 34, 35 and 36 runtime compatibility is unverified; compile/target SDK 36 does not replace those tests.
3. Native recognizer availability was advertised, but real spoken transcript accuracy, local-language pack availability and airplane-mode recognition were not validated. Diagnostic AudioRecord operation was conditionally skipped on this emulator.
4. TalkBack, large font, tablet/foldable layouts, OEM interruption/reboot behavior, permission-dialog rotation and full physical process-death scenarios remain manual checks.
5. Hosted CI, release signing/R8 validation, performance/battery profiling and Play policy/store review remain later release gates. Direct calling, TTS, bundled ASR, wake word and FunctionGemma are deliberately outside this phase.

The 22 unsuppressed lint warnings are upgrade/compatibility notices: OldTargetApi (1), UnusedAttribute for predictive back on older APIs (1), AndroidGradlePluginVersion (2), GradleDependency (7) and NewerVersionAvailable (11). Target SDK remains the requested 36. Test annotation-processing may print unrecognized Hilt processor options; debug native symbol stripping may package AndroidX libraries unstripped. Native alignment was checked independently; no warning baseline or check suppression was added.

Next milestone: complete the physical/API 29–36 acceptance matrix, then start Phase 2 bundled offline ASR, VAD and offline TTS after licensing, compatibility, language and memory benchmarks. The full staged plan is in [ROADMAP.md](../../docs/ROADMAP.md).
