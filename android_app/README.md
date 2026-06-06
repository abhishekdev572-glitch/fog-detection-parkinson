# FOG Detector — Android App

> **Real-time Freezing of Gait (FOG) detection and Activity Recognition for Parkinson's Disease patients using a dual-head TFLite model, BLE IMU streaming, and tactile feedback.**

---

## Table of Contents

1. [Overview](#overview)
2. [System Architecture](#system-architecture)
3. [Hardware Setup](#hardware-setup)
4. [ML Model](#ml-model)
5. [Project Structure](#project-structure)
6. [Module Breakdown](#module-breakdown)
7. [BLE Protocol](#ble-protocol)
8. [Inference Pipeline](#inference-pipeline)
9. [Getting Started](#getting-started)
10. [Settings & Configuration](#settings--configuration)
11. [Permissions](#permissions)
12. [Build Requirements](#build-requirements)
13. [Key Constants Reference](#key-constants-reference)
14. [Troubleshooting](#troubleshooting)

---

## Overview

The FOG Detector app connects wirelessly to two ESP32-based BLE devices — an IMU sensor and a tactile actuator — and runs a dual-head neural network on every incoming motion window to simultaneously predict:

- **FOG (Freezing of Gait):** Binary sigmoid output (0–1 probability)
- **Activity Class:** Softmax over three classes — `Other`, `Stationary`, `Walking`

When FOG is detected above a configurable threshold, the app automatically activates a tactile vibration device worn by the patient and fires a push notification, providing a closed-loop assistive response.

---

## System Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  Android App (this repo)                 │
│                                                         │
│  ┌──────────┐    ┌──────────────┐    ┌───────────────┐  │
│  │BleManager│───▶│FogDetection  │───▶│MainActivity   │  │
│  │          │    │Model         │    │(UI + Alerts)  │  │
│  │ 720-byte │    │              │    │               │  │
│  │ windows  │    │ Output[0]:   │    │ FOG card      │  │
│  │          │    │  activity    │    │ Activity card │  │
│  │          │    │ Output[1]:   │    │ Notif + BLE   │  │
│  │          │    │  fog prob    │    │ tactile cmd   │  │
│  └────┬─────┘    └──────────────┘    └───────────────┘  │
│       │                                                  │
└───────┼──────────────────────────────────────────────────┘
        │ BLE (GATT)
   ┌────┴──────────────────────────┐
   │                               │
┌──▼──────────────┐   ┌────────────▼──────────┐
│  NICLA_IMU_INT8 │   │   ESP32_TACTILE        │
│  (IMU sensor)   │   │  (vibration actuator)  │
│                 │   │                        │
│  ax ay az       │   │  Nordic UART Service   │
│  gx gy gz       │   │  0x01 = ON             │
│  120 samples/   │   │  0x00 = OFF            │
│  window         │   │                        │
└─────────────────┘   └────────────────────────┘
```

---

## Hardware Setup

### IMU Device — `NICLA_IMU_INT8`

| Property | Value |
|---|---|
| BLE name prefix | `NICLA_IMU_INT8` |
| Service UUID | `12345678-1234-1234-1234-1234567890ab` |
| Data characteristic UUID | `abcd1234-1234-1234-1234-abcdef123456` |
| Data format | 720 bytes per packet (120 samples × 6 channels × 1 byte int8) |
| Channels | ax, ay, az (accelerometer) + gx, gy, gz (gyroscope) |
| Notifications | CCCD `00002902-0000-1000-8000-00805f9b34fb` |

The IMU firmware sends raw int8 sensor readings as BLE notifications. The app accumulates these into a rolling 720-byte buffer (handling any BLE fragmentation automatically) and dispatches each complete window to the TFLite model.

### Tactile Device — `ESP32_TACTILE`

| Property | Value |
|---|---|
| BLE name prefix | `ESP32_TACTILE` |
| Service UUID | `6e400001-b5a3-f393-e0a9-e50e24dcca9e` (Nordic UART) |
| Control characteristic UUID | `6e400002-b5a3-f393-e0a9-e50e24dcca9e` |
| Command: ON | `0x01` |
| Command: OFF | `0x00` |

The tactile device receives single-byte write commands to activate or deactivate the vibration motor worn by the patient.

---

## ML Model

### Training Summary

The dual-head model was trained on windowed IMU data from Parkinson's patients performing a range of motor tasks. Each window is 120 samples × 6 channels (flattened to 720 values) at the int8 quantised precision matching the ESP32 firmware output.

### Architecture

```
Input [1, 120, 6]
    │
    ▼
Conv1D(128, kernel=5, padding=same, relu)
    │
BatchNorm + MaxPool(2)
    │
Conv1D(128, kernel=3, padding=same, relu)
    │
BatchNorm
    │
Bidirectional GRU(64, return_sequences=True, unroll=True)
    │
TemporalAttention(64)           ← custom learned attention
    │
Dense(64, relu) + Dropout(0.5)
    │
    ├──── Dense(3, softmax)  →  Output[0]: Activity
    │                            0 = Other
    │                            1 = Stationary (Lying/Sitting/Standing)
    │                            2 = Walking
    │
    └──── Dense(1, sigmoid)  →  Output[1]: FOG probability
```

### Activity Class Mapping

| Raw Activity | Final Class |
|---|---|
| Lying, Sitting, Standing | 1 — Stationary |
| Walking | 2 — Walking |
| Akinesia, Festination, Shuffling, Trembling, etc. | 0 — Other |

### TFLite Model Specification

| Property | Value |
|---|---|
| File | `app/src/main/assets/fog_model.tflite` |
| Input tensor | `[1, 720]` int8 |
| Output[0] | `[1, 3]` int8 — activity softmax |
| Output[1] | `[1, 1]` int8 — FOG sigmoid |
| Quantisation scale | `1 / 256` |
| Quantisation zero point | `-128` |
| Dequantisation formula | `float = (raw_int8 + 128) / 256` |
| TFLite runtime | `tensorflow-lite:2.14.0` |

> **Important:** The model file is NOT included in the repo. You must compile your own `.tflite` from the training notebook and copy it to `app/src/main/assets/fog_model.tflite`. If the file is missing, the app will display an instructions dialog on startup.

---

## Project Structure

```
FogDetector/
├── settings.gradle
├── build.gradle
├── gradle.properties
├── gradlew
├── local.properties
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties       (Gradle 8.4)
│
└── app/
    ├── build.gradle                        (compileSdk 34, TFLite deps)
    ├── proguard-rules.pro
    └── src/
        └── main/
            ├── AndroidManifest.xml
            ├── assets/
            │   ├── fog_model.tflite        ← YOU PROVIDE THIS
            │   └── README_model.txt
            │
            ├── java/com/fogdetector/
            │   ├── FogDetectorApp.kt       ← Application class
            │   ├── ble/
            │   │   ├── BleConstants.kt     ← All UUIDs & sizes
            │   │   ├── BleConnectionState.kt
            │   │   └── BleManager.kt       ← Scan + GATT + accumulator
            │   ├── ml/
            │   │   ├── FogDetectionModel.kt ← TFLite wrapper
            │   │   └── PredictionResult.kt  ← Inference data class
            │   ├── notification/
            │   │   └── NotificationHelper.kt
            │   ├── ui/
            │   │   ├── MainViewModel.kt
            │   │   ├── MainActivity.kt
            │   │   └── SettingsActivity.kt
            │   └── util/
            │       └── PreferencesManager.kt
            │
            └── res/
                ├── layout/
                │   ├── activity_main.xml
                │   └── activity_settings.xml
                ├── drawable/
                │   └── ic_back.xml
                ├── menu/
                │   └── menu_main.xml
                └── values/
                    ├── strings.xml
                    ├── colors.xml
                    └── themes.xml
```

---

## Module Breakdown

### `ble/BleConstants.kt`
Single source of truth for all BLE identifiers. All UUIDs, device name prefixes, timing constants, packet dimensions, and tactile command bytes are defined here. Change values here only — no other file hard-codes BLE parameters.

### `ble/BleConnectionState.kt`
Enum describing the full lifecycle of each BLE device connection:
`IDLE → SCANNING → FOUND → CONNECTING → CONNECTED`
with error states `NOT_FOUND`, `ERROR`, `DISCONNECTED`.

### `ble/BleManager.kt`
Core BLE orchestrator. Responsibilities:
- Starts a BLE LE scan and matches devices by name prefix.
- Negotiates MTU (512 bytes requested) for large packet transfers.
- Discovers services and enables GATT notifications on the IMU characteristic.
- **Accumulation buffer:** Incoming notification chunks of any size are appended to a 720-byte rolling buffer. When the buffer fills, a copy is dispatched via `onPacketReceived` callback and the buffer resets. This handles any BLE fragmentation transparently.
- Writes `0x01`/`0x00` to the tactile device's UART TX characteristic on demand.
- Posts connection state updates to `LiveData` for UI observation.

### `ml/FogDetectionModel.kt`
TFLite inference wrapper. Responsibilities:
- Memory-maps `fog_model.tflite` from assets using `FileChannel`.
- Allocates `ByteBuffer` I/O tensors for int8 inference.
- Runs `runForMultipleInputsOutputs` addressing output index `0` (activity) and `1` (FOG).
- Dequantises both outputs: `(raw + 128) / 256`.
- Normalises activity probabilities to sum to 1.0 after quantisation noise.
- Applies the configurable FOG threshold for the boolean `isFog` flag.

### `ml/PredictionResult.kt`
Immutable data class holding one inference result:
- `fogProbability: Float` — dequantised sigmoid (0–1)
- `activityProbs: FloatArray` — 3-element normalised softmax
- `isFog: Boolean` — thresholded decision
- `activityIndex / activityLabel` — computed properties for the winning class
- `fogPercent` — integer percentage for progress bars

### `notification/NotificationHelper.kt`
Creates the `fog_alerts` notification channel on app startup. Fires a high-priority `CATEGORY_ALARM` notification on FOG onset and cancels it on recovery. Respects Android 13+ `POST_NOTIFICATIONS` permission.

### `util/PreferencesManager.kt`
SharedPreferences accessor for three user settings:
- `fog_threshold` (Float, default `0.30`)
- `auto_tactile` (Boolean, default `true`)
- `auto_notify` (Boolean, default `true`)

### `ui/MainViewModel.kt`
AndroidViewModel bridging BLE, ML, and UI layers:
- Owns `BleManager` and `FogDetectionModel` lifetimes.
- Wires `BleManager.onPacketReceived` → `FogDetectionModel.predict` → `prediction` LiveData.
- Exposes `imuState`, `tactileState`, `prediction`, `modelLoaded`, `fogThreshold` as LiveData for the activity to observe.

### `ui/MainActivity.kt`
Main screen. Observes all ViewModel LiveData and renders:
- **Connection card** — real-time status for both BLE devices with colour coding.
- **FOG status card** — large colour-coded card (green=normal, red=FOG) with blinking animation, probability percentage, and a linear progress bar.
- **Activity card** — current predicted class and individual probability breakdown.
- **Manual tactile controls** — override buttons for ON/OFF.
- Handles permission requests (BT Scan/Connect, location for API < 31, notifications for API 33+).
- Triggers auto-tactile and auto-notify on FOG state transitions.

### `ui/SettingsActivity.kt`
Settings screen with:
- Threshold slider (5–95 %, step 1).
- Toggle for automatic tactile activation.
- Toggle for push notifications.
All changes are persisted immediately via `PreferencesManager`.

---

## BLE Protocol

### Scan Phase
The app scans for up to **10 seconds**. Any device whose advertised name starts with `NICLA_IMU_INT8` or `ESP32_TACTILE` is captured. Scanning stops as soon as both are found. If either is not found within the timeout, its state is set to `NOT_FOUND`.

### IMU Data Flow
```
ESP32 firmware                     Android app
─────────────                      ─────────────
[IMU sample]                       accumulate(chunk)
    │                                    │
    ▼                                    ▼
encode int8                         dataBuffer[0..719]
    │                                    │ (when full)
    ▼                                    ▼
BLE notify (N bytes)              onPacketReceived(720 bytes)
    │                                    │
    ▼                                    ▼
[possibly fragmented]             FogDetectionModel.predict()
```

### Tactile Command Flow
```
isFog == true  →  writeCharacteristic(0x01)  →  Vibration ON
isFog == false →  writeCharacteristic(0x00)  →  Vibration OFF
```
Manual override buttons bypass the FOG decision and write directly.

---

## Inference Pipeline

```
720 raw int8 bytes (from IMU)
    │
    ▼
ByteBuffer (direct, native order)
    │
    ▼
TFLite Interpreter.runForMultipleInputsOutputs(
    inputs  = [ inputBuf ],
    outputs = { 0: actBuf(3 bytes), 1: fogBuf(1 byte) }
)
    │
    ├── actBuf: 3 raw int8  → dequantise each → normalise sum → activityProbs[3]
    │
    └── fogBuf: 1 raw int8  → dequantise → fogProbability (0–1)
                                         → isFog = fogProbability >= threshold

Dequantise:  float_val = (raw_int8 - (-128)) × (1/256)
                       = (raw_int8 + 128) / 256
```

---

## Getting Started

### Step 1 — Clone & Open
```bash
git clone <your-repo-url>
```
Open the `FogDetector/` folder in **Android Studio Giraffe (2022.3.1)** or newer. Click **Sync Now** when prompted.

### Step 2 — Add the TFLite Model
Copy your compiled model file:
```
fog_model.tflite  →  app/src/main/assets/fog_model.tflite
```
The app will show a warning dialog on startup if the file is missing.

### Step 3 — Build & Install
```
Build ▸ Make Project
Run   ▸ Run 'app'
```
Minimum SDK is **API 26 (Android 8.0)**. Target SDK is **API 34**.

### Step 4 — Runtime
1. Grant **Bluetooth Scan**, **Bluetooth Connect**, and **Post Notifications** permissions when prompted.
2. Tap **Scan & Connect** on the main screen.
3. The app will auto-connect to `NICLA_IMU_INT8` and `ESP32_TACTILE`.
4. Inference starts as soon as the IMU begins streaming.

---

## Settings & Configuration

Open Settings via the toolbar icon (⚙).

| Setting | Default | Description |
|---|---|---|
| FOG Threshold | 30 % | Minimum FOG probability to trigger an alert. Lower = more sensitive. |
| Auto Tactile on FOG | ON | Automatically write `0x01` to the tactile device when FOG is detected, `0x00` on recovery. |
| Push Notifications | ON | Fire a system notification on the first FOG detection event. Cancels on recovery. |

---

## Permissions

| Permission | API Level | Purpose |
|---|---|---|
| `BLUETOOTH_SCAN` | 31+ | Discover nearby BLE devices |
| `BLUETOOTH_CONNECT` | 31+ | Connect, read, write, notify on GATT |
| `ACCESS_FINE_LOCATION` | < 31 | Required for BLE scanning on older Android |
| `BLUETOOTH` + `BLUETOOTH_ADMIN` | < 31 | Legacy BLE API |
| `POST_NOTIFICATIONS` | 33+ | Show FOG alert notifications |

All permissions are requested at runtime on first launch.

---

## Build Requirements

| Tool | Version |
|---|---|
| Android Studio | Giraffe (2022.3.1) or newer |
| Gradle | 8.4 |
| Android Gradle Plugin | 8.2.2 |
| Kotlin | 1.9.22 |
| Compile SDK | 34 |
| Min SDK | 26 (Android 8.0) |
| TensorFlow Lite | 2.14.0 |
| TFLite Support | 0.4.4 |
| Material Components | 1.11.0 |

---

## Key Constants Reference

All constants live in `BleConstants.kt` — change them only there.

| Constant | Value | Meaning |
|---|---|---|
| `PACKET_SIZE` | 720 | Bytes per inference window |
| `WINDOW_SIZE` | 120 | Samples per window |
| `NUM_CHANNELS` | 6 | ax, ay, az, gx, gy, gz |
| `SCAN_TIMEOUT_MS` | 10,000 | BLE scan duration |
| `CONNECTION_TIMEOUT_MS` | 10,000 | Per-device GATT timeout |
| `TACTILE_ON_COMMAND` | `0x01` | Vibration on |
| `TACTILE_OFF_COMMAND` | `0x00` | Vibration off |

Model constants in `FogDetectionModel.kt`:

| Constant | Value | Meaning |
|---|---|---|
| `SCALE` | `1/256` | Affine dequantisation scale |
| `ZERO_POINT` | `-128` | Affine dequantisation zero point |
| `ACT_CLASSES` | 3 | Other / Stationary / Walking |
| TFLite threads | 4 | Parallel inference threads |

---

## Troubleshooting

**App shows "Model file missing" dialog**
Copy `fog_model.tflite` to `app/src/main/assets/` and rebuild.

**Devices not found during scan**
- Confirm the ESP32 firmware is running and advertising.
- Verify the device name starts with exactly `NICLA_IMU_INT8` or `ESP32_TACTILE`.
- Check that Bluetooth is enabled and permissions are granted.
- Try increasing `SCAN_TIMEOUT_MS` in `BleConstants.kt`.

**IMU data not triggering inference**
- Enable GATT notifications on the IMU characteristic — check `BleManager.enableNotifications`.
- Verify the firmware sends data on UUID `abcd1234-1234-1234-1234-abcdef123456`.
- Check logcat tag `BleManager` for accumulation progress.

**FOG threshold too sensitive / not sensitive enough**
Adjust in Settings. The default of 30 % was chosen based on the training notebook's validation results. You can tune it per patient.

**Tactile device not responding**
- Confirm `ESP32_TACTILE` is connected (green status in app).
- Check that the NUS TX characteristic UUID matches `6e400002-b5a3-f393-e0a9-e50e24dcca9e`.
- Check logcat tag `BleManager` for write errors.

**Activity classification always shows "Other"**
- Ensure the model input is in the correct byte order (row-major, channel-last).
- Verify the int8 data from the IMU matches the quantisation format used during training (same scale/zero-point).

---

## License

This project is intended for research and clinical prototype use. Consult your institution's IRB/ethics board before deploying with patients.
