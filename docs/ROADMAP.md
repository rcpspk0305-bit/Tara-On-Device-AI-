# Roadmap

## Phase 1 — Android control foundation

Implement the supplied master brief: modular Kotlin/Compose, deterministic typed action pipeline, explicit permissions/confirmation, foreground-only local speech where supported, typed fallback, privacy/settings and reproducible verification. Acceptance remains subject to the actual checks recorded in qa/phase1/REPORT.md, including hardware gates.

## Phase 2 — Bundled offline speech and response

Evaluate sherpa-onnx model/runtime licensing and Android API29 compatibility; pin model checksums and download/install provenance. Benchmark RAM, cold-start, real-time factor and language accuracy on student-accessible low/mid-range phones. Integrate VAD and a single audio-focus/capture owner. Add offline-only TTS voice availability checks and multilingual tests. Validate zero traffic in airplane mode.

## Phase 3 — Hey Tara

Train/evaluate a custom wake model with consented data; measure false accepts/rejects and battery drain. Add explicit opt-in foreground-microphone service only where Android and Play policies support it. Evaluate assistant-role invocation and optional hotword hardware; never promise universal custom DSP detection. Document OEM background limits and visible stop controls.

## Phase 4 — FunctionGemma 270M

Fine-tune on the allowlisted typed schema and evaluate held-out commands. Select a compatible quantized runtime after licensing, memory and latency tests. Replace CommandParser through an adapter with strict structured decoding; retain ActionValidator, entity resolution, policy checks and all Android handlers. Add bounded multi-turn slots with expiration and adversarial malformed-output tests. No arbitrary tool-name execution.

## Phase 5 — Release

Security/privacy and Play policy review; 29/30/31/34/35/36 and physical OEM matrix; offline, crash, battery, accessibility, tablet/foldable, external-intent and process-death testing. Verify release signing, R8, native 16KB compatibility if native dependencies are later added, staged rollout and rollback. Resolve every open acceptance gate before claiming production readiness.
