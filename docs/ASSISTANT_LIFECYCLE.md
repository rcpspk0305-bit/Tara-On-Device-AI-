# On-demand microphone and Android assistant integration

Scope: Workflows 1 and 2 from the supplied development request. Preserve the existing native UI,
Vosk recognizers, action registry and authorization policy. No UI rewrite or new inference backend.

## Implementation design

- Manual activation is on-demand by default: prepare resources, capture one command, release audio,
  process the transcript, clean up, return to IDLE, remove the foreground notification, stop the service.
- A single-flight coroutine controller owns the lifecycle. AudioRecord has one owner and cancellable,
  bounded reads. Cancellation, denied permission, capture silencing, failures and destruction release it.
- Hands-free is a separate user action with disclosure: the existing restricted-grammar Vosk wake
  decoder keeps the microphone active. It is software monitoring, not a measured low-power DSP engine.
  Release capture during command processing; reopen wake capture only after processing finishes.
- Use the official VoiceInteractionService and session services with BIND_VOICE_INTERACTION. The
  persistent system service loads no models and opens no microphone. A visible session routes one
  invocation to the same worker service; dismissal cancels its owned interaction. Missing permission
  sends users to the app. Role loss cancels system-owned work.
- RoleManager requests ROLE_ASSISTANT; OEM gesture availability and DSP capability remain separate.
  Do not capture assist screen contents, use Accessibility, or bypass lock-screen/background restrictions.
- Android's service metadata requires both sessionService and recognitionService. The added
  OfflineRecognitionService implements the platform speech protocol with local Vosk, bounded capture,
  stop/cancel handling and no action execution. A process-wide capture guard prevents it from opening
  a second microphone alongside the assistant worker.
- Some Android role configurations mark ASSISTANT as non-requestable. A declined/unsupported role
  request does not imply selection; a separate **Open Android assistant settings** button provides
  the supported settings fallback. Returning from settings refreshes the actual role state.

## Verification plan

Regression tests cover release-before-processing, silence, maximum duration despite repeated partials,
duplicate activation, hands-free re-entry without model reload, cancellation and inference failures.
Compile and run offline unit tests, lint, assemble the offline debug APK, and inspect merged permissions.
Real-device acceptance remains separate from these checks.

## Android references

- [VoiceInteractionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionService)
- [VoiceInteractionSession](https://developer.android.com/reference/android/service/voice/VoiceInteractionSession)
- [RoleManager](https://developer.android.com/reference/android/app/role/RoleManager)
- [Foreground microphone restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Input sharing and silenced capture](https://developer.android.com/media/platform/sharing-audio-input)
- [AOSP assistant role configuration, including required metadata and requestability](https://android.googlesource.com/platform/packages/modules/Permission/+/774cf953ac2fac2b1f18226b5682121a76a97299/PermissionController/res/xml/roles.xml)
- [AOSP hotword APIs and system permissions](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/service/voice/VoiceInteractionService.java)

## Implementation boundaries

The persistent VoiceInteractionService opens no microphone and initializes no model. Models are
prepared only for explicit interactions, reused across wake-command cycles, and closed when the worker
ends. Idle state changes do not trigger initialization. A later explicit on-demand activation loads a
new Vosk instance; retaining/evicting a warm model cache remains performance work for a later phase.

The existing EmulatedLiteRtInferenceAdapter remains a deterministic compatibility command parser.
There is no real FunctionGemma/LiteRT-LM inference in this change. Schemas, native actions and
biometric policies are preserved. Commands requiring foreground UI or authentication retain the
notification-driven approval flow; handing off approval is not reported as action success.

Software wake mode uses the existing restricted-grammar Vosk decoder, with the full microphone
privacy indicator. It is not a dedicated low-power KWS engine and has no verified DSP integration.
The reviewed AOSP hotword APIs include hidden/system APIs and privileged permissions; the assistant
role alone does not establish enrollment or hardware access. No reflection or indicator bypass is used.

The older OfflineSpeechEngine/SpeechPipeline is not used by the active service. It now requires explicit
startListening and defaults to one-command decoding; it does not silently enter wake mode on construction.
Existing legacy state names remain for compatibility; the active worker publishes the new lifecycle states.

## Verification evidence — 22 October 2026

| Check | Evidence and status |
| --- | --- |
| Kotlin compilation, offline JVM tests | PASS: 58 tests, zero failures/errors; 14 new session/deadline/invocation regressions |
| Offline lint and debug APK assembly | PASS on final sources including RecognitionService and assistant-settings fallback: BUILD SUCCESSFUL, 53 tasks; lint has zero errors and 149 warnings |
| Registration/offline contract | PASS: tools/check-assistant-registration.ps1 resolves both metadata targets to declared system-protected services and checks merged manifest has no INTERNET permission |
| Whitespace/conflict check | PASS: git diff --check |
| Emulator idle and permission request | Observed API 37 emulator: app displays microphone off; tapping Speak opens Android's microphone permission dialog |
| Emulator one-command silence path | PASS for the APK preceding the metadata/fallback correction: AudioRecord starts, releases, state returns IDLE, dumpsys shows no worker, AppOps shows a completed recording |
| Emulator role request | Initial request returned without a chooser. Missing recognitionService metadata was corrected, and a settings fallback was added. Corrected registration NOT RETESTED because the emulator disconnected |
| Spoken command, assistant gesture, hands-free wake/re-entry | NOT TESTED on device |
| Permission denial/revocation, call interruption, other mic clients | Unit/source error paths exist; device scenarios NOT TESTED. The emulator denial step was declined at tool approval |
| Locked device, biometric approval and airplane-mode actions | NOT TESTED on device in this change |
| Physical privacy indicator, OEM behavior, wake accuracy, battery | NOT TESTED; no physical device connected |

Emulator evidence is in ignored local files under qa/assistant-lifecycle (lifecycle-log.txt,
services.txt, window.xml). Logs contain lifecycle state names, not transcripts or raw audio.

The captured emulator log sequence was:

```text
14:33:02.729  mode=ON_DEMAND state=ACTIVATING
14:33:27.081  AudioRecord started
14:33:27.107  mode=ON_DEMAND state=LISTENING
14:33:32.511  AudioRecord released
14:33:32.544  mode=ON_DEMAND state=RECOGNIZING
14:33:32.575  mode=ON_DEMAND state=CLEANUP
14:33:33.050  mode=ON_DEMAND state=IDLE
```

This single initial-install emulator sample took 24.352 seconds from ACTIVATING to AudioRecord start,
5.430 seconds from recorder start to release, and 30.321 seconds to IDLE. AppOps recorded a completed
5.301-second microphone operation. This is a silence/cleanup smoke test while local builds were running,
not a speech/inference benchmark or evidence that timeout thresholds are exact under load.
Cold-start performance is not production-ready evidence and requires profiling in the later performance
workflow. Wake/STT accuracy, inference latency, memory and one-hour battery measurements are unavailable.

## Reproduction

From VoiceOffline with JDK 17 and Android SDK 36 installed:

```powershell
./gradlew.bat testOfflineDebugUnitTest lintOfflineDebug assembleOfflineDebug --offline --no-daemon --console=plain
```

From the repository root:

```powershell
powershell -NoProfile -File tools/check-assistant-registration.ps1
powershell -NoProfile -File tools/assistant-emulator.ps1 -Step Install
powershell -NoProfile -File tools/assistant-emulator.ps1 -Step Inspect
powershell -NoProfile -File tools/assistant-emulator.ps1 -Step Manual
powershell -NoProfile -File tools/assistant-emulator.ps1 -Step Evidence
```

The emulator helper defaults to emulator-5554; use -Serial for another test device. It installs an update,
operates visible controls, and writes local evidence. It does not clear app data, grant permissions, or
change assistant selection. Complete onboarding/permission dialogs explicitly. The Manual step samples
AppOps and captures logs; it does not claim that a spoken action passed.

For remaining acceptance: choose Voice in system assistant settings, verify the selected role, invoke a
device-supported assistant gesture, cancel during startup/capture/inference, replace the assistant while
a session is active, test calls and capture silencing, and verify sensitive actions remain approval-gated.
On a physical device, observe the Android microphone indicator and AppOps before/during/after every
scenario. In hands-free mode it must stay active during wake monitoring and stop when the user disables it.
No full Siri/Gemini privilege parity or production release readiness is claimed.
