# Permissions, Platform Compliance & Play Store Limitations — Tara

## 1. Architecture Philosophy: Offline-First & Privacy-By-Design

Tara operates with zero internet access. No microphone audio, transcripts, contacts, or notification data ever leaves the local device. The application does not declare the `android.permission.INTERNET` permission in its AndroidManifest.

---

## 2. Permissions Matrix

| Permission                                         | Tiers / Versions          | Purpose                                                                                            | Fallback Strategy if Denied                                                                         |
| :------------------------------------------------- | :------------------------ | :------------------------------------------------------------------------------------------------- | :-------------------------------------------------------------------------------------------------- |
| `android.permission.RECORD_AUDIO`                  | **Core (Runtime)**        | Continuous 16 kHz Mono PCM audio recording for local wake-word ("Hey Tara") and command capture.   | Service cannot start. Clear rationale dialog displayed to the user.                                 |
| `android.permission.FOREGROUND_SERVICE`            | **Core (Normal)**         | Keeps background wake-listening active with persistent notification.                               | Service killed by system when app leaves foreground.                                                |
| `android.permission.FOREGROUND_SERVICE_MICROPHONE` | **Android 14+ (API 34+)** | Declares microphone foreground service type as strictly required by Android 14+.                   | Service throws `SecurityException` on start.                                                        |
| `android.permission.POST_NOTIFICATIONS`            | **Android 13+ (API 33+)** | Displays ongoing microphone status and actionable authorization notifications.                     | State cannot be visually indicated; interactive biometric approval fails closed.                    |
| `android.permission.RECEIVE_BOOT_COMPLETED`        | **System (Normal)**       | Automatically restores hands-free "Hey Tara" listening after phone restart if enabled in settings. | Hands-free mode must be manually re-enabled by opening the app after restart.                       |
| `android.permission.READ_CONTACTS`                 | **Optional (Runtime)**    | Resolves names to phone numbers for "Call [Name]" and "Message [Name]".                            | Numeric phone numbers (e.g. "Call 9876543210") still function. Named lookups prompt for permission. |
| `android.permission.CALL_PHONE`                    | **Optional (Runtime)**    | Initiates direct voice calls when authorized.                                                      | Gracefully falls back to opening `ACTION_DIAL` with the resolved number in phone dialer.            |
| `android.permission.ANSWER_PHONE_CALLS`            | **Optional (Runtime)**    | Answers incoming calls on voice command "Answer call".                                             | Reports permission required via TTS.                                                                |
| `android.permission.CAMERA`                        | **Optional (Runtime)**    | Controls camera LED torch for flashlight on/off commands.                                          | Command fails with descriptive explanation.                                                         |
| `android.permission.SCHEDULE_EXACT_ALARM`          | **Optional (Special)**    | Sets exact time alarms via `AlarmClock.ACTION_SET_ALARM`.                                          | Inexact alarm scheduling fallback.                                                                  |
| `android.permission.USE_BIOMETRIC`                 | **Security (Normal)**     | Authenticates high-risk actions before sensitive execution.                                        | High-risk actions fail closed.                                                                      |
| `BIND_DEVICE_ADMIN`                                | **System (Special)**      | Locks screen securely upon voice command "Lock my phone".                                          | Reports Device Administrator activation required.                                                   |
| `BIND_NOTIFICATION_LISTENER_SERVICE`               | **System (Special)**      | Reads notifications and performs direct replies via RemoteInput.                                   | Notification actions unavailable.                                                                   |

---

## 3. Google Play Store Policy Compliance

### 3.1 SMS and Call Log Permission Restrictions

Google Play enforces strict policies restricting `SEND_SMS` and `READ_CALL_LOG` to default SMS/Phone handling apps:

- **Tara Compliance Architecture**:
  - Tara **does NOT request `SEND_SMS`**. Instead, messaging commands parse the contact, format the message body, read back the details via offline TTS ("Draft message to Dad: I reached college..."), and launch `Intent.ACTION_SENDTO` targeting `smsto:<number>` with `sms_body` extra.
  - The user reviews the pre-populated message and taps send in their preferred SMS app.
  - This eliminates Google Play permission declaration exceptions, avoids account suspension risks, and guarantees no silent message transmission occurs.
- **Calling Policy Compliance**:
  - Direct calling requires explicit runtime `CALL_PHONE` consent. If denied or omitted, `Intent.ACTION_DIAL` provides a seamless zero-permission fallback.

---

## 4. Android 13, 14, 15, and 16 Platform Limitations

### 4.1 Background Activity Launch (BAL) Restrictions

Beginning in Android 10 and tightened progressively through Android 13–16:

- Background services are restricted from launching Activities directly without user interaction.
- **Tara Solution**:
  1. All system intent dispatches (e.g., Settings, Dialer, Home screen) explicitly include `Intent.FLAG_ACTIVITY_NEW_TASK`.
  2. For sensitive or high-risk actions requiring approval, Tara never attempts to pop open an activity from the background. Instead, an urgent notification with a PendingIntent is enqueued in `PendingCommandStore` (30-second TTL), allowing the user to tap and authorize.

### 4.2 Foreground Service Microphone Rules (API 34+)

Android 14+ enforces that microphone recording in a background service must have foreground service type `microphone`:

- Manifest declaration:
  ```xml
  <service
      android:name=".service.VoiceAssistantService"
      android:exported="false"
      android:foregroundServiceType="microphone" />
  ```
- `startForeground()` is called immediately within `onStartCommand()`, supplying notification channel `CHANNEL_SERVICE` with an ongoing notification.
- Tara **never falsely claims** the microphone is inactive while background listening is engaged.

### 4.3 Screen Locking via Device Policy Manager

- `DevicePolicyManager.lockNow()` requires user-approved Device Administrator privileges.
- Implemented via `VoiceAdminReceiver` registered with `<device-admin>` metadata declaring `<uses-policies><force-lock /></uses-policies>`.
- When enabled by the user in Onboarding / Settings, "Hey Tara, lock my phone" executes `lockNow()`.
- **Security Floor**: Tara never attempts to bypass or tamper with the device lock screen, PIN, password, pattern, or biometrics. The user must provide their credential to unlock the phone. If Device Administrator privileges are revoked, the app catches `SecurityException` and prompts for re-enrollment.

### 4.4 OEM Battery Optimization & Doze Mode

- Hands-free wake word listening running in background is subject to OEM battery killer restrictions (e.g. Xiaomi MIUI, Samsung OneUI, OnePlus OxygenOS).
- The onboarding flow prompts users to exclude Tara from aggressive battery optimization so the foreground service remains alive during screen-off standby.
- `BootReceiver` listens for `ACTION_BOOT_COMPLETED` and automatically restores listening if hands-free was enabled prior to reboot.
