# Voice Offline — Android-first reference implementation

## What is delivered

Native Kotlin project, **no `INTERNET` permission**, no HTTP libraries, no analytics SDK, no telemetry,
no server or chat interface. Microphone samples and transcripts are processed in RAM and discarded, except for notes the user explicitly requests to save. SQLite notes are
AES-256-GCM encrypted under a nonexportable Android Keystore key, with app backups disabled.

**Status: reference implementation, not a certified production release.** The base project source is
complete but a speech model must be packaged before it can listen. Local compile/unit/lint and emulator results are recorded in
[the lifecycle report](../docs/ASSISTANT_LIFECYCLE.md). It has not been verified on a physical device. Do not publish before completing the
real-device security/performance and Play-policy verification checklist below.

## Setup

1. Install Android Studio, JDK 17, Android SDK Platform 36, and Gradle 8.13. Open this folder.
2. Download `vosk-model-small-en-us-0.15.zip` from the official
   [Vosk models page](https://alphacephei.com/vosk/models); check its license and integrity.
3. Unzip **contents of its top-level folder** directly into
   `app/src/main/assets/vosk-model/`. Directory must contain `am/`, `conf/`, `graph/` and possibly `ivector/`.
4. Import into Android Studio, let Gradle resolve dependencies at **build time**. There are deliberately
   no network permissions in the resulting application, and it does not download models at runtime.
5. The Gradle 8.13 wrapper JAR and scripts are bundled. Run `./gradlew testOfflineDebugUnitTest
   lintOfflineDebug assembleOfflineDebug` (Windows: `gradlew.bat`) from this directory.
   `.github/workflows/android.yml` runs these same gates on pushes and pull requests.
6. Install the app. Grant microphone permission, notification permission, and optional permissions only
   for the actions you want. Enable notification listener in Android settings for message replies,
   and enable the device administrator role for `lock screen`.
7. Open the app and tap **Speak one command** while the activity is visible. Say the command directly;
   no wake phrase is needed. After endpoint silence or the 7-second command limit, capture is released
   before processing. The worker foreground service stops afterward. Check result/approval notifications.
8. In **Settings**, choose **Voice as default assistant** through Android's system role request. A supported
   system gesture opens a compact Voice session and routes one command to the same offline worker.
   Role availability and gesture configuration depend on the device. Microphone permission is still required.
9. Optionally choose **Enable hands-free Hey Voice** and accept the disclosure. Vosk software wake
   monitoring keeps the microphone active; it is not a verified low-power or hardware/DSP engine.
   Stop it from the app or microphone notification. The option does not restart on boot or process death.
10. To test that runtime is offline, disable Wi-Fi and mobile data. The app contains no INTERNET permission.

### Voice commands

- Tap **Speak one command** → `open camera`
- `hey voice` → `lock screen`
- `hey voice` → `take a note buy milk`
- `hey voice` → `call Alice` (requires exact, unique name with one phone number, user approval)
- `hey voice` → `answer call` (requires permission; legacy best-effort API)
- `hey voice` → `reply to Alice saying I am on my way` (requires active notification with direct-reply action and biometric approval)

**Important:** Vosk wake detection requires a clean wake phrase and endpoint silence. This is a
restricted-vocabulary decoder, not a dedicated ultra-low-power wake word model, and may consume
material power. Some Vosk models may not reliably recognize wake-phrases from their vocabulary.
Always-on listening may be interrupted by other applications or the OEM's battery management.

### Security and policy notes

- Android 14+ restricts microphone foreground service startup. Manual capture starts from a visible
  Activity; system invocation starts from the selected VoiceInteractionService/session. Unsupported
  starts fail with feedback. There is no BOOT_COMPLETED restart; the worker is START_NOT_STICKY.
- Notifications must be allowed for approval prompts. Android does not permit arbitrary background
  biometric activity launches or screen wake; a **user taps a notification** to open AuthorizationActivity.
- The biometrics signing key requires `BIOMETRIC_STRONG`, is bound to Android Keystore, and is hardware-backed
  only on devices that report hardware support. Treat `KeyInfo.securityLevel` as an audited device capability,
  not an assumption. Biometric unavailability **fails closed**.
- External activity launching is deferred to the approval Activity in foreground. No activity start
  is attempted directly from a background service.
- Replies work only for currently posted notifications with a direct-reply action. The RemoteInput
  callback confirms handing off data to the app, **not** end-to-end message delivery. There is no
  background SMS permission.
- `acceptRingingCall()` is deprecated since API 29 and cannot answer every type of call.
  This is not a compliant replacement for a full default dialer/InCallService integration.
- Avoid claiming 200ms speech-to-action without device benchmarks. Current deterministic intent parsing
  is quick; Vosk decoding and endpointing take longer, and the microphone FGS is power-intensive.
- App targets Android 16 API 36, compiles against SDK 36; test Android 11–16, OEM battery policies,
  lock screen, interruptions, missing services, notification listener restarts and permission revocations.
- App backup is disabled; uninstalling removes notes permanently. Keystore encryption does not guarantee
  hardware security on all devices.
- Google Play Billing is **deliberately not added**: a strictly air-gapped, no-INTERNET runtime cannot
  purchase/verify Play Billing entitlements. Use a separate connected purchase/activation channel or
  revise the absolute air-gapped requirement. Do not grant Pro based on locally editable preferences.

### Expected production work before shipping

1. Replace Vosk wake decoder with a validated, efficiently quantized KWS model (ONNX Runtime Mobile,
   Sherpa-ONNX KWS, or vendor DSP hotword if legitimately accessible). Supply licensed model binaries,
   verify wake-word false accepts and user-trained word support. Keep wake and command processing isolated.
2. Train/validate direct acoustic classifier if desired. The optional `Mfcc39Extractor` implements
   MFCC + delta + delta-delta features, but the default **Vosk STT** pipeline correctly uses Vosk's
   own acoustic features instead of wastefully recomputing them. Do not claim this optional module
   is an inference model.
3. Add app-specific microphone contention handling, audio focus/lifecycle tests, cancellation rules,
   stringent speech confidence thresholds, structured local-only audit logs, user-visible cancel,
   onboarding disclosures, accessibility support, and handling of absent notifications.
4. Verify exact wake phrase, command parsing, contact ambiguous results, biometric enrollment changes,
   potential keystore key loss, encrypted note DB migration, local retention/deletion tools.
5. Confirm every native action and Play policy requirement with target-device tests; benchmark P50/P95
   intent dispatch, end-to-end latency, CPU/battery, thermals, and memory for 24-hour operation.
6. Supply distributable Gradle wrapper and signed release configuration plus reproducible dependency
   checksums, model license notices, CI tests, Play privacy/data-safety documentation.

## File map

- `VoiceAssistantService.kt` — foreground microphone lifecycle, PCM capture, notifications
- `audio/SpeechPipeline.kt`, `audio/OfflineSpeechEngine.kt` — swappable recognizer interface and Vosk wake/command state machine
- `audio/Mfcc39Extractor.kt` — optional 39-component speech features
- `commands/VoiceCommand.kt`, `commands/IntentParser.kt` — typed intent schema and safe grammar
- `security/PendingCommands.kt`, `security/AuthorizationActivity.kt`, `security/BiometricSigner.kt`
- `system/ActionExecutor.kt` — Android native action adapters
- `system/ReplyNotificationListener.kt` — notification RemoteInput
- `system/VoiceAdminReceiver.kt` — user-approved screen lock privilege
- `storage/PrivateNoteStore.kt` — Keystore AES-GCM + SQLite
- `ui/MainActivity.kt` — minimal permission and service start/stop interface
