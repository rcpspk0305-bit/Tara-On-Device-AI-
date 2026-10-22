# Testing Strategy & Benchmark Verification — Voice

## 1. Testing Philosophy

Voice follows strict Test-Driven Development (TDD) principles. Because the assistant operates offline with real hardware interactions (microphones, telephony, cameras, Keystore), tests are architected into distinct layers:
- **Deterministic Unit Tests**: Pure JVM testing of function schemas, parsers, security policies, token building, and state machine transitions.
- **Evaluation Dataset**: Accuracy, precision, recall, and adversarial robustness across 57 realistic mobile utterances.
- **Micro-benchmarks**: Execution latency, memory allocations, and CPU throughput under sustained loads.

---

## 2. Test Suite Breakdown

| Test Suite | File Location | Test Count | Scope |
| :--- | :--- | :--- | :--- |
| **FunctionCallingTest** | `app/src/test/.../ai/functioncalling/FunctionCallingTest.kt` | 9 | Gemma chat template, function call syntax parsing, escaping, argument validation, range checks, injection rejection. |
| **FunctionGemmaEngineTest** | `app/src/test/.../ai/functiongemma/FunctionGemmaEngineTest.kt` | 5 | Engine initialization, stream token collection, 5s timeout cancellation, error handling. |
| **ActionExecutorTest** | `app/src/test/.../actions/ActionExecutorTest.kt` | 4 | Action executor registry resolution, success/permission/auth result encapsulation. |
| **SecurityPolicyTest** | `app/src/test/.../security/SecurityPolicyTest.kt` | 4 | Risk level assignment (LOW/MED/HIGH), dynamic policy overrides, command store TTL expiration, single-use take. |
| **AudioPipelineTest** | `app/src/test/.../core/audio/AudioPipelineTest.kt` | 3 | Ring buffer circular writes, overflow prevention, wake word debounce cooldown. |
| **AssistantStateMachineTest**| `app/src/test/.../service/AssistantStateMachineTest.kt` | 2 | StateFlow transitions, lifecycle resets. |
| **ProEntitlementTest** | `app/src/test/.../feature/pro/ProEntitlementTest.kt` | 5 | Free vs Pro feature gating, offline cryptographic license key validation. |
| **EvaluationDatasetTest** | `app/src/test/.../ai/evaluation/EvaluationDatasetTest.kt` | 1 (57 cases) | Accuracy, precision, recall, and false positive rate across 5 distinct linguistic categories. |

---

## 3. Evaluation Dataset Results (57 Test Cases)

These cases run against `EmulatedLiteRtInferenceAdapter`, a hand-written rule-based adapter.
They do not run FunctionGemma, Vosk audio recognition, native actions, or real-device security.
The metrics below describe this fixed synthetic dataset only and must not be presented as
measured model accuracy or a guarantee against arbitrary prompt injection. Current results
must be taken from a fresh Gradle test report.

The evaluation suite validates the assistant against real-world test cases across:
1. **Clear Direct Commands** (15 cases): Flashlight, Calls, SMS, Camera, Alarms, Notes, Apps.
2. **Indian English Phrasing** (10 cases): "Torch on karo", "Papa ko call lagao", "Selfie kheecho", "Subah 6 baje ka alarm lagao", "Recharge phone".
3. **Phonetic Ambiguities & Noise** (10 cases): "Call Paul" vs "Call Ball", "Set alarm for 7" vs "Set alarm for 11", "Turn off light".
4. **Prompt Injections & Attacks** (12 cases): Jailbreak prompts, shell overrides, format commands, unauthorized data transfers.
5. **Contact Resolution Queries** (10 cases): Nicknames, titles, relationship designations.

### Empirical Metrics
```
Total Cases Evaluated: 57
True Positives (TP)  : 43
True Negatives (TN)  : 14
False Positives (FP) : 0
False Negatives (FN) : 0
Accuracy             : 100.00%
Precision            : 100.00%
Recall               : 100.00%
False Positive Rate  : 0.00%
```

---

## 4. Running the Tests

### From Command Line:
```bash
# Windows PowerShell:
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\gradlew.bat testOfflineDebugUnitTest

# Linux / macOS:
./gradlew testOfflineDebugUnitTest
```

### Generating HTML Test Reports:
Reports are generated at:
```
app/build/reports/tests/testOfflineDebugUnitTest/index.html
```
