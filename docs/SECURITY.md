# Phase 1 security

All application processing is local. The APK has no INTERNET permission and includes no bundled speech/AI engines or background listening service. Android's explicitly on-device recognizer is used only when its availability probe succeeds; no general recognizer fallback is permitted. Verify OEM recognition behavior on release devices using airplane mode and traffic inspection.

Audio is held briefly in memory and discarded. Transcripts, SMS bodies, contact data and app inventories are neither logged nor persisted. Only theme, onboarding and permission-prompt history persist. Android backup and transfer are disabled. Crash reports/analytics are not integrated.

Treat typed input and future model output as untrusted. Typed actions are validated before policy/dispatch and at Android adapter boundaries. Phone values accept only optional leading + and 3–15 ASCII digits; USSD/MMI, URI syntax, separators/extensions and control codes cannot reach telephony intents. The parser normalizes spaces, hyphens and parentheses before validation. Emergency numbers only enter the visible dialer; Tara never places a call. SMS always requires exact recipient/body confirmation and opens a composer, never sends.

Confirmations expire after 30 seconds, cancel on backgrounding and are never restored after process death. Grants are checked again at execution; no broad accessibility automation, root, hidden APIs, authentication bypass or device-owner authority. Scoped launcher queries avoid full-package visibility. Media controls do not change DND policy or protected settings.

Play distribution still requires an actual policy/privacy review, signed release verification, hardware/API testing and accurate store disclosures. Device-admin force-lock has Android credential-unlock implications; explain authorization and deactivation clearly. Phase 1 implementation is not proof of Play approval. Future direct calling, wake-word FGS, contacts permission and AI runtimes each require their own review.
