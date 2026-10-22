# Workflow and code repairs — 22 October 2026

Reviewed the Android build and the active speech-to-action, authorization, and shutdown paths.
Existing uncommitted UI changes were preserved. No commits or remote changes were made.

## Repairs

- Added `.github/workflows/android.yml` for pushes, pull requests, and manual runs. It installs
  JDK 17 and the Android SDK, validates the wrapper through Gradle's setup action, runs unit
  tests/lint/debug assembly, requires actual test discovery, and uploads reports and the APK.
  Repository permissions are read-only; checkout credentials are not persisted.
- Pinned the Gradle 8.13 distribution checksum to the official published SHA-256.
- Fixed the lint-blocking microphone permission error with explicit `SecurityException`
  handling. Recorder cleanup now detaches its handle before release to avoid double release.
- Required a single complete function call. Truncated calls, surrounding prose, multiple
  calls, duplicate keys, malformed arguments, and undeclared arguments are rejected.
  Escaped note/message values retain embedded braces and commas.
- Rejected reserved prompt control tokens and oversized transcripts. Limited generated
  output to 16,384 characters; included initialization in the inference timeout; propagated
  cancellation instead of converting it into an ordinary inference failure.
- Made screen locking require biometrics. Policy overrides can strengthen the default
  policy but cannot lower its security floor.
- Routed camera/app/call/alarm launches through a user-tapped notification and a foreground
  confirmation screen. High-risk actions retain biometric authorization. Missing notification
  access is reported without enqueueing an unreachable approval. Notifications expire after
  30 seconds, and approved results reach the home screen.
- Serialized recognition, inference, and native-model cleanup in one pipeline coroutine.
  Microphone input uses a bounded four-chunk channel; stop cancels the pipeline, and native
  resources close in its `finally` block. Native action exceptions return failures.
- Added the alarm permission and launcher package-visibility declaration.
- Corrected documentation that implied that the emulated adapter was real FunctionGemma
  inference or that its fixed dataset proved general prompt-injection resistance.

## Verification

Seven regression failures were reproduced before their repairs. The final command was:

```powershell
cd VoiceOffline
.\gradlew.bat testOfflineDebugUnitTest lintOfflineDebug assembleOfflineDebug --offline --no-daemon --console=plain
```

| Check | Result |
| --- | --- |
| JVM unit tests | PASS: 44 tests across 10 suites; zero failures/errors |
| Android lint | PASS: zero errors; 160 warnings remain |
| Offline debug assembly | PASS: `BUILD SUCCESSFUL` |
| Workflow YAML parsing | PASS using a YAML parser; hosted execution not tested |
| Diff whitespace | PASS: `git diff --check` |
| Device microphone, native actions, biometrics, shutdown behavior | NOT TESTED |
| Hosted GitHub Actions run | NOT TESTED; workflow changes are local |

The APK is `VoiceOffline/app/build/outputs/apk/offline/debug/app-offline-debug.apk`.
It is a debug artifact, not a signed release or evidence of hardware behavior.

## Limits

The service still uses `EmulatedLiteRtInferenceAdapter`, a rule-based fallback. Native
LiteRT-LM integration and FunctionGemma model execution are not implemented or verified.
The fixed evaluation dataset measures the fallback only. Existing state/cooldown tests
include synthetic logic and do not prove the real service or Vosk lifecycle. Remaining
lint warnings include UI localization, draw allocations, and platform compatibility items.

This review is not an exhaustive security audit or release certification. In particular,
legacy contact matching and local Pro entitlement stubs need further validation before
production use. No device latency, battery, model accuracy, or store-readiness claim is made.

Android's background-launch rules informed the foreground confirmation repair:
[Activity security](https://developer.android.com/guide/components/activities/secure-bal).
CI follows the [Gradle setup action](https://github.com/gradle/actions/tree/v4/setup-gradle).
