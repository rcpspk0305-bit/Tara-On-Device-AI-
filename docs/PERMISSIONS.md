# Phase 1 permissions and compatibility

| Manifest access | Purpose | User intervention |
|---|---|---|
| RECORD_AUDIO | Foreground push-to-talk or PCM diagnostic | Runtime request only after user selects access |
| CAMERA | CameraManager torch control | Runtime request at point of use |
| MODIFY_AUDIO_SETTINGS | Media volume and audio focus | Normal permission |
| com.android.alarm.permission.SET_ALARM | Clock app alarm/timer intents | Normal permission; review clock UI |
| BIND_DEVICE_ADMIN on receiver | Protect force-lock receiver | Explicit Android admin activation; not a runtime permission |

Only force-lock is declared in the device-admin policy. Tara does not request device-owner status. A secure PIN/pattern/password must be configured before lock. Deactivate Tara in Android device-admin settings before uninstalling if it was activated.

No INTERNET, QUERY_ALL_PACKAGES, READ_CONTACTS, SEND_SMS, READ_SMS, Call Log, CALL_PHONE, ANSWER_PHONE_CALLS, foreground-service or notification-listener permissions are included. Direct calling is disabled; a future opt-in route would need a separate policy and permission review. Android's phone picker grants access to the selected number only.

Dashboard checks the OS on each resume and again at execution. Status includes granted, denied, permanently denied, revoked and unavailable. Requests happen only after a visible user action. Permanent denials link to app settings; admin and secure-lock setup open their dedicated Android screens.

| Android API | Speech | Actions/UI |
|---|---|---|
| 29–30 | Real capture diagnostic plus typed commands | All eight adapters subject to hardware, admin and target-app availability |
| 31–35 | Explicit on-device recognition if available; diagnostic otherwise | Same; mic privacy switch, permission revocation and policy restrictions respected |
| 36 | Same on-device-only contract | Target/compile SDK 36, edge-to-edge and predictive back enabled |

Tablet, foldable, fixed-volume and telephony-free devices may lack individual capabilities; the app reports this without claiming completion. Emulator testing cannot replace OEM hardware testing.
