# Architecture Specification — Tara (Offline Voice Assistant)

## 1. High-Level System Architecture

Tara is an actions-first, 100% offline voice assistant for Android activated by the custom wake phrase **"Hey Tara"**. It leverages a compact, high-efficiency **ONNX neural Keyword Spotting (KWS)** model for low-power standby monitoring, offline acoustic speech recognition, Google's **FunctionGemma 270M Mobile Actions** model for intent reasoning, strict schema validation, and native Android system APIs for execution and speech feedback (TTS).

```mermaid
flowchart TD
    subgraph Audio_Pipeline ["Audio Processing Pipeline (16 kHz Mono PCM)"]
        MIC[AudioRecordManager] -->|16-bit PCM Chunks| RING[Sliding Circular Buffer (1.0s)]
        RING --> MFCC[Mfcc39Extractor (39-dim MFCCs)]
        MFCC --> ONNX[OnnxWakeWordDetector (hey_tara.onnx)]
        ONNX -->|Wake Detected: 'Hey Tara'| CHIME[Activation Chime]
        CHIME --> STT[VoskSpeechRecognizer (Full STT Enabled)]
        STT -->|Command Audio Stream| TRANSCRIPT[Offline Command Transcript]
    end

    subgraph Reasoning_Engine ["On-Device Reasoning & Validation"]
        TRANSCRIPT --> PROMPT[FunctionCallingPromptBuilder]
        PROMPT --> ENGINE[FunctionGemmaEngine (270M LiteRT-LM)]
        ENGINE --> PARSER[FunctionCallParser]
        PARSER --> VALIDATOR[FunctionSchemaValidator]
    end

    subgraph Security_Gate ["Hardware Security & Policy Gate"]
        VALIDATOR -->|StructuredAction| POLICY[SensitiveActionPolicy]
        POLICY -->|Risk Level Check| RISK{Requires Interaction?}
        RISK -->|Direct Execution| REGISTRY[ActionExecutorRegistry]
        RISK -->|Interactive Approval| STORE[PendingCommandStore (30s TTL)]
        STORE --> NOTIF[Action Approval Notification]
        NOTIF --> AUTH[BiometricPrompt / AuthorizationActivity]
        AUTH -->|Approved| REGISTRY
    end

    subgraph Native_Execution ["Android Native Action Executors"]
        REGISTRY --> EX_SCR[ScreenActionExecutor (DevicePolicyManager.lockNow)]
        REGISTRY --> EX_CALL[CallActionExecutor (ContactResolver + ACTION_CALL / ACTION_DIAL)]
        REGISTRY --> EX_MSG[MessagingActionExecutor (ContactResolver + ACTION_SENDTO Draft)]
        REGISTRY --> EX_SET[SettingsActionExecutor (WiFi, Bluetooth, Sound, Display)]
        REGISTRY --> EX_VOL[VolumeActionExecutor (AudioManager Media Stream)]
        REGISTRY --> EX_NAV[NavigationActionExecutor (System Home Category)]
        REGISTRY --> EX_NOTIF[NotificationActionExecutor (Reply & Read)]
        REGISTRY --> EX_CAM[CameraActionExecutor (Camera Intent)]
        REGISTRY --> EX_FLUSH[FlashlightActionExecutor (CameraManager Torch)]
        REGISTRY --> EX_ALARM[AlarmActionExecutor (AlarmClock Intent)]
        REGISTRY --> EX_NOTE[NoteActionExecutor (Encrypted Store)]
        REGISTRY --> EX_APP[AppActionExecutor (PackageManager)]
    end

    subgraph Feedback ["Offline Voice & Audio Feedback"]
        REGISTRY --> OUTCOME[Execution Result]
        OUTCOME --> TTS[OfflineTextToSpeechEngine (Embedded Synthesis)]
        TTS -->|Spoken Confirmation / Error Explanation| SPEAKER[User Audio Output]
        OUTCOME --> RESET[Release STT & Return to Wake Word Standby]
        RESET --> ONNX
    end
```

---

## 2. Core Subsystems

### 2.1 Audio Pipeline & Custom Wake Word Detection (`wakeword/`)
- **Acoustic Preprocessing (`Mfcc39Extractor`)**:
  - Computes 39-dimensional acoustic feature vectors (13 cepstral + 13 delta + 13 delta-delta coefficients) across 98 overlapping frames using 400-sample Hamming windows (25 ms) and 160-sample hops (10 ms).
- **ONNX Keyword Spotting (`OnnxWakeWordDetector`)**:
  - Implements `WakeWordDetector`.
  - Runs lightweight inference every 100 ms (1,600 samples) on a 1.0-second sliding buffer against `hey_tara.onnx` (<1 MB).
  - Keeps full speech recognition completely dormant until a verified trigger occurs.
  - 2,000 ms cooldown timer prevents duplicate activations.
  - Consumes <1.4% CPU in standby, reducing battery consumption by ~85% compared to continuous Kaldi grammar decoding.

### 2.2 Offline Speech Recognition & Audio Capture (`ai/stt/`, `core/audio/`)
- **Audio Capture (`AudioRecordManager`)**: Captures 16 kHz, 16-bit Mono Linear PCM audio.
- **Offline Speech Recognizer (`VoskSpeechRecognizer`)**:
  - Activated only after wake detection.
  - Employs Voice Activity Detection (VAD) with 3.0s silence endpoint timeout or 7.0s maximum duration cutoff.
  - Immediately released once the command transcript is acquired.

### 2.3 FunctionGemma Reasoning & Schema Validation (`ai/functiongemma/`, `ai/functioncalling/`)
- **Mobile Actions 270M Model**: Formats transcripts into official FunctionGemma function-calling prompts.
- **Strict Schema Validator (`FunctionSchemaValidator`)**:
  - Enforces strict parameter limits, typed ranges, and allowed action catalogs.
  - Explicitly rejects unauthorized tool invocations, shell commands, or arbitrary code.
  - Emits validated `StructuredAction` sealed types.

### 2.4 Contact Resolution (`contacts/`)
- **`ContactResolver`**:
  - Fast-path regex for direct numeric digits (no permissions required).
  - Queries `ContactsContract.CommonDataKinds.Phone` when `READ_CONTACTS` is granted.
  - Parses phone type qualifiers ("on mobile", "at home", "at work").
  - Identifies contact ambiguity (multiple matching candidates) and prompts for disambiguation.

### 2.5 Offline Text-To-Speech (`tts/`)
- **`OfflineTextToSpeechEngine`**:
  - Decoupled via `TtsSpeaker` interface and `AndroidTtsSpeaker` driver.
  - Utilizes Android framework TTS configured with `KEY_FEATURE_EMBEDDED_SYNTHESIS` to ensure zero network requests.
  - Provides `confirmSuccess()` and `explainFailure()` audio responses to close the voice loop naturally.

### 2.6 Modular Native Action Executors (`actions/`)
- **Screen Lock (`ScreenActionExecutor`)**:
  - Integrates `DevicePolicyManager.lockNow()` via user-authorized `VoiceAdminReceiver`.
  - Configurable hands-free direct lock via `SensitiveActionPolicy.directScreenLockEnabled`.
  - Never bypasses lock-screen PIN/password/biometric security.
- **Phone Calls (`CallActionExecutor`)**:
  - Resolves contact name/number.
  - Dispatches `ACTION_CALL` when `CALL_PHONE` permission is granted.
  - Seamlessly falls back to `ACTION_DIAL` when ungranted.
- **Messaging (`MessagingActionExecutor`)**:
  - Resolves recipient contact.
  - Reads back recipient and message body via TTS.
  - Launches `ACTION_SENDTO` (`smsto:<number>`) with `sms_body` extra for user review and transmission in SMS app (complying with Google Play policies).
- **Settings Navigation (`SettingsActionExecutor`)**:
  - Opens Wi-Fi, Bluetooth, Sound, Display, or Battery settings via standard platform intents.
- **Media Volume (`VolumeActionExecutor`)**:
  - Adjusts `AudioManager.STREAM_MUSIC` (raise, lower, mute, unmute, percentage).
- **System Navigation (`NavigationActionExecutor`)**:
  - Navigates to Home screen via `Intent.CATEGORY_HOME`.
- **System Boot (`BootReceiver`)**:
  - Listens for `ACTION_BOOT_COMPLETED` and automatically restores hands-free listening if enabled in user preferences.
