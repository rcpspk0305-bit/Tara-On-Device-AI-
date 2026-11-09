# Phase 1 architecture

Dependencies flow toward Android-free core:domain. The app is the Hilt composition root and hosts the ViewModel/activity-result bridges. Compose screens expose events and never invoke system actions.

| Module | Responsibility |
|---|---|
| :app | Application, Hilt wiring, lifecycle, permission/contact launchers, ViewModel |
| :core:domain | Eight sealed actions, validated contracts, exhaustive registry, policy, parser and session state |
| :core:audio | Exclusive on-device recognizer or AudioRecord diagnostic; lifecycle cleanup |
| :core:permissions | OS permission probes, denial history and explicit setup intents |
| :core:designsystem | Material 3 colors, typography and adaptive page/cards |
| :platform:androidactions | Android APIs, scoped app resolver, device-admin receiver |
| :data:settings | DataStore appearance/onboarding only |
| :feature:assistant | Command editor, mic controls, preview, confirmation and results |
| :feature:onboarding | Honest capability explanation and contextual permission setup |
| :feature:privacy | Live status and local processing explanations |
| :feature:settings | Light/Dark/System and onboarding reset |

Command text or a reviewed transcript enters CommandParser. SessionState.transcript holds only actual recognizer output; typed editor content stays separate and is never labeled as recognized speech. Entity resolution chooses a scoped app or asks for an exact target. ActionValidator rejects malformed arguments; ActionPolicy checks current grants and confirmation requirements. ActionRegistry exhaustively dispatches the sealed action to PlatformActions. Android adapters recheck security at the actual OS boundary. Future inference replaces CommandParser; handlers remain unchanged. No reflection or arbitrary function-name execution exists.

AssistantSessionManager exposes StateFlow: Idle → Listening → Transcribing → Idle (review transcript); Execute → Parsing → optional Resolving → Executing → Completed/Error/AwaitingPermission/AwaitingConfirmation. Confirm revalidates authorization and executes once. Cancel invalidates pending resolution/confirmation. Confirmations and choices expire after 30 seconds; backgrounding cancels pending work. New sessions do not restore sensitive commands or pending approvals after process death. DataStore restores appearance/onboarding.

A lock request drops pending confirmation before invoking lockNow. The microphone is cancelled before any typed execution; backgrounding also cancels capture. Android OS side effects already handed off cannot be undone by cancellation.

The former monolith is retained outside the active source set so this phase does not ship its background service, broad permissions, inference emulation or model assets.
