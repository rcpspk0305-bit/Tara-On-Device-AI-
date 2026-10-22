# FunctionGemma 270M Model Setup & Installation

**Not integrated:** The active service instantiates `EmulatedLiteRtInferenceAdapter` and does
not load a FunctionGemma binary. The FunctionGemma instructions below are preparation for a
future native adapter; copying a model to the device does not activate it. Its latency and
memory figures are targets, not device measurements. Vosk model packaging is used today.

This guide explains how to prepare, verify, and install Google's **FunctionGemma 270M Mobile Actions** model on your Android device for 100% offline, private tool calling.

---

## 1. Model Overview

- **Model ID**: `litert-community/functiongemma-270m-ft-mobile-actions`
- **Architecture**: FunctionGemma 270M (Transformer-based SLM fine-tuned for tool calling)
- **Quantization**: INT4 / INT8 LiteRT-LM format
- **Footprint**: ~160 MB to ~280 MB RAM peak
- **Inference Latency Target**: 350 ms – 750 ms on modern Snapdragon / Tensor / Dimensity SoCs.

---

## 2. Model Download Instructions

### Option A: Direct Download via Hugging Face CLI
Ensure you have the Hugging Face CLI installed:
```bash
pip install huggingface_hub
huggingface-cli download litert-community/functiongemma-270m-ft-mobile-actions \
    --local-dir ./functiongemma-270m-mobile-actions \
    --include "*.bin" "*.tflite" "tokenizer.json"
```

### Option B: Direct Browser Download
1. Visit the repository: [https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions](https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions)
2. Download the model binary:
   - Primary: `functiongemma-270m-mobile-actions.bin` (LiteRT-LM format)
   - Alternative: `functiongemma-270m-mobile-actions.tflite`
3. Download `tokenizer.json` and `sentencepiece.model`.

---

## 3. Cryptographic SHA-256 Integrity Verification

Before transferring the model to your device, verify its SHA-256 checksum:

### Linux / macOS:
```bash
shasum -a 256 functiongemma-270m-mobile-actions.bin
```

### Windows PowerShell:
```powershell
Get-FileHash -Algorithm SHA256 .\functiongemma-270m-mobile-actions.bin
```

Compare the output hash against the expected checksum published on the Hugging Face model card. The app's `MobileActionsModelManager` validates the checksum on initial load to protect against corrupted or tampered binaries.

---

## 4. Transferring the Model to Android Device

The offline application reads the model file either from its app-specific external storage or internal private directory.

### Step 1: Connect your device via USB and verify ADB
```bash
adb devices
```

### Step 2: Push model to app storage directory
```bash
# Push model file to device storage
adb push functiongemma-270m-mobile-actions.bin /sdcard/Download/

# Alternatively, push directly to the Voice application external files directory:
adb push functiongemma-270m-mobile-actions.bin /sdcard/Android/data/dev.voice.offline/files/models/
```

---

## 5. Vosk Offline STT Model Setup

Voice uses Vosk for local wake phrase detection and speech recognition without network access.

1. Download the lightweight US English model `vosk-model-small-en-us-0.15.zip` from [Vosk Models](https://alphacephei.com/vosk/models).
2. Unpack the zip file.
3. Copy the extracted folders (`am/`, `conf/`, `graph/`, `ivector/`) into:
   ```
   VoiceOffline/app/src/main/assets/vosk-model/
   ```
4. Verify the directory structure:
   ```
   app/src/main/assets/vosk-model/
   ├── am/
   │   └── final.mdl
   ├── conf/
   │   └── mfcc.conf
   ├── graph/
   │   └── HCLG.fst
   └── ivector/
   ```

---

## 6. Configuring Model in App Settings

1. Open the **Voice** app.
2. Select the **Settings** tab.
3. Under **Model Configuration**:
   - The default model path is: `/sdcard/Android/data/dev.voice.offline/files/models/functiongemma-270m-mobile-actions.bin`
   - If stored elsewhere, specify the custom path and tap **Verify & Apply**.
4. The app will audit file existence, test model load, and show memory benchmark statistics.
