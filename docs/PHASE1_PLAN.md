# Phase 1 implementation plan

The supplied master brief is the implementation contract. Work stays on the existing feature branch.

1. Replace the active monolithic app build with eleven modules and a version catalog. Preserve old source outside the active source set; do not ship legacy services or model assets.
2. Establish Android-free typed domain actions, validation, exhaustive registry/policy dispatch, deterministic parser and bounded session state.
3. Implement Android actions and permission status adapters. Only dial/composer/clock handoffs; no automatic calls or SMS. Resolve entities before execution.
4. Implement foreground-only audio with explicit on-device recognition on API 31+, exclusive capture diagnostic otherwise. Bound timeouts and release resources on interruption.
5. Implement Compose/MVVM onboarding, assistant, confirmation, privacy and persisted appearance settings. Wire dependencies in the application through Hilt.
6. Run unit, build, lint and available instrumentation checks. Record observed results and hardware blockers, with reproducible manual tests.

No FunctionGemma, background listening, wake word, contacts inventory persistence, network permission or direct SMS permission in this phase.
