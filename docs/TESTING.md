# Phase 1 testing

Run from the active isolated worktree with JDK 17 and SDK 36.

~~~powershell
.\gradlew.bat :core:domain:test testDebugUnitTest lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat :app:installDebug
.\gradlew.bat :app:connectedDebugAndroidTest :feature:onboarding:connectedDebugAndroidTest :feature:assistant:connectedDebugAndroidTest :platform:androidactions:connectedDebugAndroidTest
~~~

On Windows, the same compiled instrumentation APKs can also run directly through ADB without Gradle UTP host setup:

~~~powershell
.\tools\run-instrumentation.ps1 -Adb "<Android SDK>\platform-tools\adb.exe" -Serial "<device serial>"
~~~

Omit Serial when exactly one device is connected. The runner discovers test package IDs from Gradle output metadata, verifies real instrumentation status codes and test discovery, and writes raw diagnostics plus an explicit passed/skipped summary under qa/phase1. It never places calls or sends messages.

The available local AVD runs API 37, not API 36; no API 29–36 system image or physical device was installed during this session. Espresso 3.7.0 is pinned explicitly for its InputManager compatibility fix ([AndroidX release notes](https://developer.android.com/jetpack/androidx/releases/test)). CI is configured for API 36; its hosted run remains unverified until pushed. The microphone diagnostic integration test skips when native on-device recognition is advertised, because that device follows the native recognizer path.

Unit tests cover typed validation, exhaustive registry dispatch, denied/revoked permissions, malformed phone/SMS/time inputs, parser ambiguity/overflow, duplicate contacts through a fake resolver, bounded confirmations, cancellation and concurrent resolution. Android module tests exercise permission classification, capture ownership/stale callbacks and scoped app matching. ViewModel tests use fake speech and platform gateways.

Compose tests require an API29+ emulator/device. Adapter instrumentation records outgoing intents using a Context wrapper; it does not place calls, deliver SMS, lock hardware or alter torch state. No automated test may make a real call or send SMS.

## Reproducible manual hardware checks

1. Install debug APK and complete onboarding without granting access: typed timer/app commands remain available; mic/torch show contextual permission guidance. Deny twice, return from Android settings after grant/revoke, and confirm the dashboard reflects current status without automatic repeated prompts.
2. In airplane mode on API31+, tap mic, speak volume down, Stop, review transcript and Execute. On unavailable recognizers/API29/30, verify diagnostic capture reports real duration/amplitude and no transcript. Cancel, press Home, rotate, receive another audio-focus interaction and revoke mic permission; Android's mic indicator must stop and no capture may restart automatically. Repeat start/stop 20 times and inspect adb shell dumpsys audio recording configuration.
3. Type sms <your test number> saying a unique harmless message. Verify exact number/body. Cancel and wait 30 seconds: no composer opens. Confirm a new request: composer opens with exact payload; Tara says handoff, not sent. Do not tap Send.
4. call <test number> must only open dialer. Test invalid numbers/control codes and uninstalled dialer on a suitable test image. Do not place real or emergency calls.
5. Use contact-number picker with duplicate names/multiple numbers: select the intended number, review editor, then execute. Cancel picker and check no action occurs. App names colliding must show exact choices. Disable/uninstall a selected app before executing to verify graceful failure.
6. With no admin, lock phone must not lock. Activate Tara admin explicitly; with no secure screen lock, Tara warns. Configure a secure screen lock on a test device, confirm lock and verify interaction/audio terminate. Deactivate admin after test.
7. On physical flash hardware, grant camera and toggle on/off; open a conflicting camera app and retry. Verify no repeated hardware toggling while unavailable and accurate state after recovery. Test camera privacy toggle and devices lacking flash.
8. Adjust media volume with headphones/Bluetooth, DND and fixed-volume devices. Verify current route and protected settings are respected.
9. Create 7 am/19:30 alarm and 5 minute timer; review external clock UI and local timezone. Test missing clock app and invalid hours/durations. Tara must never claim a saved alarm/started timer without evidence.
10. Check TalkBack, 200% font, dark/light/system, phone/tablet/foldable/rotation, edge-to-edge, predictive Back and IME. Kill/restart the process: appearance/onboarding persist; audio, command and confirmation do not.

See qa/phase1/REPORT.md for observed commands and unresolved gates. Historical test counts in older documents are not Phase 1 acceptance evidence.
