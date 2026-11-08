# Architecture Specification — Voice Offline

## 1. High-Level System Architecture

Voice is an actions-first, 100% offline mobile assistant for Android powered by Google's **FunctionGemma 270M Mobile Actions** model running via **LiteRT-LM** (TensorFlow Lite Runtime for Language Models).

```mermaid
flowchart TD
    subgraph Audio_Pipeline ["Audio Processing Pipeline (16 kHz Mono PCM)"]
        MIC[AudioRecordManager] -->|16-bit PCM Chunks| RING[AudioRingBuffer (10s)]
        RING --> WAKE[VoskWakeWordDetector]
        WAKE -->|Wake Detected: 'Hey Tara'| VAD[VoskSpeechRecognizer (VAD)]
        VAD -->|Command Audio| STT[Offline Speech-to-Text]
    end

    subgraph Reasoning_Engine ["On-Device Reasoning & Validation"]
        STT -->|Raw Transcript| PROMPT[FunctionCallingPromptBuilder]
        PROMPT -->|Chat Template Prompt| ENGINE[FunctionGemmaEngine (LiteRT-LM)]
        ENGINE -->|Streaming Output| PARSER[FunctionCallParser]
        PARSER -->|RawFunctionCall| VALIDATOR[FunctionSchemaValidator]
    end

    subgraph Security_Gate ["Hardware Security & Policy Gate"]
        VALIDATOR -->|StructuredAction| POLICY[SensitiveActionPolicy]
        POLICY -->|Risk Level Check| RISK{Risk Level}
        RISK -->|LOW| REGISTRY[ActionExecutorRegistry]
        RISK -->|MEDIUM / HIGH| STORE[PendingCommandStore (30s TTL)]
        STORE --> AUTH[BiometricPrompt + Keystore EC P-256]
        AUTH -->|Signature Validated| REGISTRY
    end

    subgraph Native_Execution ["Android Native Subsystems"]
        REGISTRY --> EX_CALL[CallActionExecutor]
        REGISTRY --> EX_MSG[MessagingActionExecutor]
        REGISTRY --> EX_NOTIF[NotificationActionExecutor]
        REGISTRY --> EX_CAM[CameraActionExecutor]
        REGISTRY --> EX_SCR[ScreenActionExecutor]
        REGISTRY --> EX_FLUSH[FlashlightActionExecutor]
        REGISTRY --> EX_ALARM[AlarmActionExecutor]
        REGISTRY --> EX_NOTE[NoteActionExecutor (AES-256 Encrypted)]
        REGISTRY --> EX_APP[AppActionExecutor]
    end

    subgraph Feedback ["Audio Chime & State Feedback"]
        REGISTRY --> FEEDBACK[Chime & Notification Feedback]
        FEEDBACK --> STATE[VoiceAssistantService (StateFlow)]
        STATE -->|Return to Listening| WAKE
    end
```

---

## 2. Core Subsystems

### 2.1 Audio Pipeline & Wake Word Detection
- **Audio Capture (`AudioRecordManager`)**: Runs on a dedicated background `Dispatchers.IO` coroutine. Captures standard 16,000 Hz, 16-bit Mono PCM audio frames (1,280 bytes / 40 ms per frame).
- **Ring Buffer (`AudioRingBuffer`)**: Fixed-size non-allocating circular memory buffer storing up to 10 seconds of uncompressed PCM audio. Prevents audio clipping and allows capturing speech onset before wake detection.
- **Wake Word Detector (`VoskWakeWordDetector`)**: Listens continuously for trigger phrases `"hey tara"` and `"okay tara"` (and backwards-compatible `"hey voice"`). Implements a 2,000 ms cooldown debounce timer to prevent acoustic feedback loops.
- **Voice Activity Detection (`VoskSpeechRecognizer`)**: Detects start of speech and endpoint silence. Closes speech input automatically after 3 seconds of sustained silence or 7 seconds maximum recording duration.

### 2.2 FunctionGemma Inference Engine
- **Model Adapter (`LiteRtInferenceAdapter`)**: Implements streaming token generation against Google LiteRT-LM. Fallback emulation is provided for CI and testing environments (`EmulatedLiteRtInferenceAdapter`).
- **Engine Coordination (`FunctionGemmaEngine`)**: Enforces a strict 5,000 ms cancellation timeout. Streams tokens, concatenates output, and delegates parsing and validation.
- **Grammar & Parsing (`FunctionCallParser`)**: Extracts official Gemma function call syntax:
  ```
  <start_function_call>call:function_name{arg1:val1,arg2:<escape>string_val<escape>}<end_function_call>
  ```
- **Strict Schema Validator (`FunctionSchemaValidator`)**:
  - Validates argument types, mandatory parameters, string length limits, and numerical ranges (e.g., alarm hours `0..23`, minutes `0..59`).
  - Rejects unrecognized tools, arbitrary shell escapes, and prompt injection attempts.
  - Normalizes valid calls into typed `StructuredAction` sealed classes.

### 2.3 Hardware Biometrics & Cryptographic Policy Gate
- **Risk Classification (`SensitiveActionPolicy`)**:
  - `LOW`: Flashlight, Camera, Local Note Creation, App Launch. Executed immediately with audio chime feedback.
  - `MEDIUM`: Phone Calls, Alarms, Read Notifications. Requires interactive confirmation or local policy approval.
  - `HIGH`: Outgoing SMS/Messages, Notification Replies, Remote Screen Lock.
- **Replay Prevention (`PendingCommandStore`)**:
  - Commands requiring authentication are stored with a 30-second TTL (Time-To-Live).
  - High-precision timestamp and UUID assigned to each command.
  - Atomic `.take(commandId)` operation ensures a command can only be executed once (no replay).
- **Android Keystore Manager (`KeystoreManager`)**:
  - Generates Elliptic Curve (EC P-256) key pair backed by device hardware (`StrongBox` or `Trusted Execution Environment`).
  - Flags key with `setUserAuthenticationRequired(true)`.
  - Audits `KeyInfo.securityLevel` at runtime.
- **Biometric Authorization (`BiometricAuthorizationManager` & `AuthorizationActivity`)**:
  - Uses `BiometricPrompt` with `BIOMETRIC_STRONG`.
  - Android 14/15/16 compliant: Triggered only via interactive user tap from ongoing notification (no background activity start).

### 2.4 Modular Native Action Executors
- `CallActionExecutor`: TelecomManager / Intent.ACTION_CALL / TelecomManager.acceptRingingCall.
- `MessagingActionExecutor`: Resolves contacts and dispatches native SMS via Telephony.
- `NotificationActionExecutor`: Interacts with `NotificationListenerService` and `RemoteInput` for direct message replies without opening messaging apps.
- `CameraActionExecutor`: Launches CameraX / system camera intent.
- `ScreenActionExecutor`: Enforces device lock via `DevicePolicyManager` admin component.
- `FlashlightActionExecutor`: Controls camera torch via `CameraManager.setTorchMode()`.
- `AlarmActionExecutor`: Dispatches native `AlarmClock.ACTION_SET_ALARM` with label and time.
- `NoteActionExecutor`: Persists encrypted text notes using hardware-backed AES-256-GCM (`PrivateNoteStore`).
- `AppActionExecutor`: Resolves package manager installed apps and launches foreground intent.

### 2.5 State Machine & Reactive Lifecycle
The assistant is modeled as a deterministic state machine through `AssistantState`:
```
STOPPED -> INITIALIZING -> LISTENING -> WAKE_DETECTED
        -> RECORDING_COMMAND -> TRANSCRIBING -> INFERENCING
        -> AUTHORIZING (if High-Risk) -> EXECUTING -> CONFIRMING
        -> LISTENING (automatic loop)
```
States are broadcast via Kotlin `StateFlow<AssistantState>` to UI components and foreground service notifications.

---

## 3. Supported Action Schemas (v1)

| Action Name | Parameters | Risk Level | Description |
| :--- | :--- | :--- | :--- |
| `turn_flashlight_on` | _None_ | LOW | Turns device LED torch on |
| `turn_flashlight_off` | _None_ | LOW | Turns device LED torch off |
| `open_camera` | _None_ | LOW | Opens native camera interface |
| `lock_screen` | _None_ | HIGH | Locks device display via DeviceAdmin |
| `answer_call` | _None_ | MEDIUM | Answers incoming phone call |
| `reject_call` | _None_ | MEDIUM | Declines incoming phone call |
| `make_call` | `contact_name` (string) | MEDIUM | Initiates phone call to named contact |
| `text_msg` | `contact_name` (string), `body` (string) | HIGH | Sends SMS to contact |
| `read_notifications` | _None_ | MEDIUM | Reads out active notifications |
| `reply_to_notification` | `notification_id` (string), `body` (string) | HIGH | Sends inline reply to notification |
| `set_alarm` | `hour` (int 0..23), `minute` (int 0..59), `label` (string) | MEDIUM | Sets native system alarm |
| `take_notes` | `content` (string) | LOW | Encrypts and saves local note |
| `open_app` | `package_name` (string) | LOW | Launches installed application |
