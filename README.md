<div align="center">

# FOG Detection System

### *Real-time Freezing of Gait Detection & Tactile Alerting for Parkinson's Disease*

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-API%2026%2B-green?logo=android)](https://developer.android.com/)
[![TensorFlow Lite](https://img.shields.io/badge/TFLite-2.14.0-orange?logo=tensorflow)](https://www.tensorflow.org/lite)
[![Python](https://img.shields.io/badge/Python-3.10%2B-blue?logo=python)](https://www.python.org/)
[![Arduino](https://img.shields.io/badge/Arduino-nRF52840-teal?logo=arduino)](https://www.arduino.cc/)
[![Stars](https://img.shields.io/github/stars/abhishekdev572-glitch/fog-detection-parkinson?style=social)](https://github.com/abhishekdev572-glitch/fog-detection-parkinson)

<br/>

**A closed-loop, edge-AI wearable system that detects Freezing of Gait (FOG) episodes in real time, delivers instant tactile feedback, and recognises human activity — all on a smartphone, with no cloud dependency.**

<br/>

[ Overview](#-overview) · [Architecture](#-system-architecture) · [Hardware](#-hardware-architecture) · [ML Pipeline](#-machine-learning-pipeline) · [Installation](#-installation) · [Results](#-results) · [Citation](#-citation)

</div>

---

## Table of Contents

1. [Overview](#-overview)
2. [Key Features](#-key-features)
3. [System Architecture](#-system-architecture)
4. [Hardware Architecture](#-hardware-architecture)
5. [Software Architecture](#-software-architecture)
6. [Machine Learning Pipeline](#-machine-learning-pipeline)
7. [Model Architecture](#-model-architecture)
8. [BLE Communication Protocol](#-ble-communication-protocol)
9. [Repository Structure](#-repository-structure)
10. [Installation](#-installation)
11. [Usage](#-usage)
12. [Results](#-results)
13. [Performance Analysis](#-performance-analysis)
14. [Screenshots & Demo](#-screenshots--demo)
15. [Future Work](#-future-work)
16. [Citation](#-citation)
17. [Contributors](#-contributors)
18. [Acknowledgements](#-acknowledgements)
19. [License](#-license)

---

## Overview

Freezing of Gait (FOG) is one of the most debilitating motor symptoms of Parkinson's Disease — a sudden, involuntary cessation of movement that dramatically increases fall risk and reduces quality of life. Existing clinical monitoring is episodic and lab-bound; patients receive no real-time intervention during daily living.

This project presents a **complete, end-to-end wearable + mobile system** for continuous FOG detection and immediate assistive response:

- A miniature IMU sensor node (**Arduino Nicla Sense ME**) streams quantised inertial data over BLE.
- An **Android smartphone** runs a dual-head deep learning model in real time, simultaneously classifying human activity and detecting FOG episodes.
- Upon FOG detection, the phone wirelessly commands a **tactile actuator** (nRF52840 + DRV2605L haptic driver) worn by the patient to vibrate, delivering sensory cueing shown to break FOG episodes.

The entire inference chain runs **on-device** with no internet connection — protecting patient privacy and ensuring reliability in clinical and home environments.

**Who is this for?**

| Audience | Use Case |
|---|---|
| Researchers | Reproducible FOG detection baseline with open training code |
| Clinicians | Prototype assistive device for patient monitoring |
| Engineers | Reference design for edge-AI + BLE wearable systems |
| Students | End-to-end ML-on-device + embedded systems project |

---

## Key Features

| Feature | Description | Status |
|---|---|---|
| Real-time FOG Detection | Classifies each 2-second IMU window for FOG at inference | ✅ Live |
| Activity Recognition | Simultaneously classifies Other / Stationary / Walking | ✅ Live |
| BLE IMU Streaming | Nicla Sense ME streams int8 quantised windows at 60 Hz | ✅ Live |
| Edge AI Inference | TFLite dual-head model runs entirely on Android, no cloud | ✅ Live |
| Tactile Feedback | Haptic actuator triggers automatically on FOG onset | ✅ Live |
| Push Notifications | Android alarm-category notification fires on FOG event | ✅ Live |
| Configurable Threshold | Per-patient FOG sensitivity slider (5–95 %) | ✅ Live |
| Ultra-low Power IMU | Nicla Sense ME + int8 quantisation minimises compute | ✅ Live |
| Subject-Independent Validation | GroupShuffleSplit prevents data leakage across patients | ✅ Live |
| Dual-Task Learning | Shared backbone + task-specific attention heads | ✅ Live |
| Auto-Reconnect BLE | Handles fragmented packets and reconnection gracefully | ✅ Live |
| No Internet Required | Fully offline operation for privacy and reliability | ✅ Live |

---

## System Architecture

```
╔══════════════════════════════════════════════════════════════════════╗
║                        PATIENT (Parkinson's)                         ║
║                                                                      ║
║   ┌──────────────────┐             ┌──────────────────────────────┐  ║
║   │  Nicla Sense ME  │             │    nRF52840 Tactile Module    │  ║
║   │  (Wrist / Ankle) │             │    (Wrist / Belt)            │  ║
║   │                  │             │                              │  ║
║   │  IMU @ 60 Hz     │             │  DRV2605L Haptic Driver      │  ║
║   │  acc + gyro      │             │  ERM Vibration Motor         │  ║
║   │  int8 quantised  │             │  Open-loop mode, max amp.    │  ║
║   └────────┬─────────┘             └──────────────┬───────────────┘  ║
║            │ BLE Notify                           │ BLE Write        ║
║            │ 720 bytes/window                     │ 0x01/0x00        ║
╚════════════╪═════════════════════════════════════╪══════════════════╝
             │                                     │
             ▼                                     ▲
╔══════════════════════════════════════════════════╪══════════════════╗
║                    ANDROID APPLICATION            │                  ║
║                                                   │                  ║
║  ┌─────────────────────────────────────────────── │ ─────────────┐  ║
║  │  BleManager                                    │              │  ║
║  │  • Scan → Connect → GATT notify subscription  │              │  ║
║  │  • 720-byte window accumulator                 │              │  ║
║  │  • Tactile write commands ────────────────────►┘              │  ║
║  └─────────────────────┬───────────────────────────────────────┘  ║
║                         │ onPacketReceived(720 bytes)               ║
║  ┌──────────────────────▼─────────────────────────────────────────┐  ║
║  │  FogDetectionModel  (TFLite)                                   │  ║
║  │                                                                │  ║
║  │  Input  [1, 720]  int8                                        │  ║
║  │    └──► TFLite Interpreter (4 threads)                        │  ║
║  │           ├──► Output[0]  [1,3] int8  → Activity probs        │  ║
║  │           └──► Output[1]  [1,1] int8  → FOG probability       │  ║
║  └──────────────────────┬─────────────────────────────────────────┘  ║
║                          │ PredictionResult                           ║
║  ┌───────────────────────▼────────────────────────────────────────┐  ║
║  │  MainViewModel → MainActivity                                  │  ║
║  │                                                                │  ║
║  │  • FOG card (green ↔ red + probability %)                     │  ║
║  │  • Activity card (Other / Stationary / Walking)               │  ║
║  │  • Auto-tactile command                                       │  ║
║  │  • Push notification (CATEGORY_ALARM)                        │  ║
║  └────────────────────────────────────────────────────────────────┘  ║
╚══════════════════════════════════════════════════════════════════════╝
```

---

## Hardware Architecture

### Components

| Component | Model | Role |
|---|---|---|
| IMU Sensor Board | Arduino Nicla Sense ME | Accelerometer + Gyroscope acquisition |
| IMU Sensor Chip | Bosch BHI260 (via BHY2) | 6-axis MEMS IMU at 60 Hz |
| Haptic Controller Board | Seeed XIAO nRF52840 | BLE peripheral + haptic driver control |
| Haptic Driver IC | TI DRV2605L | LRA/ERM haptic driver over I2C |
| Vibration Motor | ERM Motor | Tactile cueing actuator |
| Mobile Processor | Android Phone (API 26+) | BLE central, ML inference, UI |

### Hardware Block Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│                        IMU MODULE                                │
│                                                                  │
│  ┌───────────────┐    I2C / SPI    ┌─────────────────────────┐  │
│  │  Bosch BHI260 │ ◄────────────── │  Arduino Nicla Sense ME │  │
│  │  (6-axis IMU) │                 │  (nRF52832 host MCU)    │  │
│  │  acc + gyro   │                 │  ArduinoBLE stack       │  │
│  │  60 Hz        │                 │  BLE 5.0 TX             │  │
│  └───────────────┘                 └───────────┬─────────────┘  │
│                                                │ BLE Advertising │
│                                                │ NICLA_IMU_INT8  │
└────────────────────────────────────────────────┼─────────────────┘
                                                 │
                               ┌─────────────────▼──────────────────┐
                               │         ANDROID PHONE              │
                               │  BLE Central (GATT)                │
                               │  TFLite Inference Engine           │
                               └─────────────────┬──────────────────┘
                                                 │
┌────────────────────────────────────────────────┼─────────────────┐
│                       TACTILE MODULE           │                  │
│                                                │ BLE Advertising  │
│                                                │ ESP32_TACTILE    │
│  ┌──────────────┐     I2C (SDA=D4, SCL=D5)   ┌▼───────────────┐  │
│  │  DRV2605L    │ ◄────────────────────────── │  XIAO nRF52840 │  │
│  │  Haptic IC   │                             │  Bluefruit BLE │  │
│  │  ERM mode    │                             │  Nordic UART   │  │
│  │  Open-loop   │                             │  Service       │  │
│  └──────┬───────┘                             └────────────────┘  │
│         │                                                          │
│  ┌──────▼───────┐                                                  │
│  │  ERM Motor   │  ← 7-second vibration on FOG command            │
│  └──────────────┘                                                  │
└──────────────────────────────────────────────────────────────────┘
```

### Wiring Reference — Tactile Module

| Signal | Pin |
|---|---|
| SDA | D4 |
| SCL | D5 |
| DRV2605L I2C Address | 0x5A |
| Power | 3.3 V |

---

## Software Architecture

### Component Overview

```
fog-detection-system/
│
├── [ANDROID APP]  FogDetector/                ← Kotlin/Gradle Android project
│   ├── BleManager          (BLE scan, GATT, accumulator, tactile commands)
│   ├── FogDetectionModel   (TFLite wrapper, dequantisation, dual output)
│   ├── MainViewModel       (MVVM bridge — BLE → ML → LiveData)
│   ├── MainActivity        (UI: FOG card, Activity card, BLE status)
│   └── SettingsActivity    (Threshold, auto-tactile, notifications)
│
├── [ML TRAINING]  model-training/             ← Python / Google Colab notebook
│   ├── FOG_Activitytraining_2_head.ipynb      (full dual-head training pipeline)
│   ├── fog_activity.keras                     (saved Keras model)
│   └── fog_model.tflite                       (deployment artifact)
│
├── [IMU FIRMWARE]  IMU-module/                ← Arduino (Nicla Sense ME)
│   └── nicla_imu_int8.ino                     (IMU read → int8 quantise → BLE notify)
│
└── [TACTILE FIRMWARE]  Tactile-module/        ← Arduino (XIAO nRF52840)
    └── nrf52840_tactile_actuator.ino          (BLE receive → DRV2605L → ERM motor)
```

### Software Data Flow

```
[IMU Firmware]                [Android App]              [Tactile Firmware]
      │                             │                            │
      │  60 Hz samples              │                            │
      │  int8 quantise              │                            │
      │  Buffer 120 × 6             │                            │
      │  Split → 3 × 240 B          │                            │
      │──── BLE Notify ────────────►│                            │
      │                             │  Accumulate 720 bytes      │
      │                             │  runForMultipleOutputs()   │
      │                             │  Dequantise outputs        │
      │                             │  Apply FOG threshold       │
      │                             │                            │
      │                             │  if isFog:                 │
      │                             │    write(0x01) ───────────►│
      │                             │    fire notification       │  Vibrate 7 s
      │                             │                            │
      │                             │  if !isFog:                │
      │                             │    write(0x00) ───────────►│
      │                             │                            │  Motor idle
```

---

## Machine Learning Pipeline

### Dataset

| Property | Value |
|---|---|
| Source | Windowed IMU CSV files from Parkinson's patient sessions |
| Input Format | Per-subject CSV with `window_id`, `acc_x/y/z`, `gyro_x/y/z`, `activity`, `label` |
| Window Size | 120 samples × 6 channels |
| Sampling Rate | 60 Hz (≈ 2 seconds per window) |
| Split Strategy | Subject-based `GroupShuffleSplit` — no subject appears in multiple splits |
| Train / Val / Test | 60 % / 20 % / 20 % |

### Activity Label Mapping

| Raw Activity String | Final Class ID | Class Name |
|---|---|---|
| Lying, Sitting, Standing | 1 | Stationary |
| Walking (normal gait) | 2 | Walking |
| Akinesia, Festination, Shuffling, Trembling, FOG-adjacent | 0 | Other |
| FOG episodes | binary `label` column | FOG = 1 |

### Full Pipeline Diagram

```
Raw CSV Files (per subject)
        │
        ▼
┌───────────────────────┐
│  Window Validation    │  Filter: exactly 120 rows per window_id
│  Column Check         │  Skip malformed or short windows
└──────────┬────────────┘
           │
           ▼
┌───────────────────────┐
│  Subject-Based Split  │  GroupShuffleSplit → train/val/test
│  (no leakage)         │  Assert zero subject overlap between splits
└──────────┬────────────┘
           │
           ▼
┌───────────────────────┐
│  StandardScaler       │  Fit on train only; transform val & test
│  (per channel)        │  Output: mean≈0, std≈1 per axis
└──────────┬────────────┘
           │
           ▼
┌───────────────────────┐
│  Sample Weighting     │  Class-frequency weights for minority balance
│  (both heads)         │  Applied per sample during training
└──────────┬────────────┘
           │
           ▼
┌───────────────────────┐
│  Dual-Head Model      │  Input shape: (120, 6)
│  Training             │  Mixed precision (float16) if GPU available
│                       │  Batch=64, Epochs=60, Adam(1e-4)
└──────────┬────────────┘
           │
           ▼
┌───────────────────────┐
│  FOG Threshold Tuning │  Scan validation set for optimal threshold
│  on Validation Set    │  Recall floor: 0.90 | Precision floor: 0.70
└──────────┬────────────┘
           │
           ▼
┌───────────────────────┐
│  Test Set Evaluation  │  Activity report + FOG report
│                       │  Confusion matrices, ROC curve
└──────────┬────────────┘
           │
           ▼
┌───────────────────────┐
│  TFLite Conversion    │  SELECT_TF_OPS + float16 supported types
│                       │  Flex ops for GRU layers
└──────────┬────────────┘
           │
           ▼
    fog_model.tflite
    (deployed to Android)
```

### Training Configuration

| Hyperparameter | Value |
|---|---|
| Batch Size | 64 |
| Epochs | 60 |
| Optimizer | Adam (lr = 1e-4) |
| Early Stopping Patience | 8 epochs |
| LR Reduction Patience | 4 epochs |
| Mixed Precision | float16 (if GPU) |
| Recommended Hardware | Google Colab T4 GPU |

### Combined Validation Metric

The model's stopping criterion is a blended score reflecting clinical priorities:

```
val_combined = 0.6 × activity_macro_F1  +  0.4 × FOG_F2
```

FOG recall is weighted heavily via F2 to minimise missed episodes, while activity F1 retains overall motion classification quality.

---

## Model Architecture

### Architecture Diagram

```
Input: (1, 120, 6)   ← 120-sample window, 6 IMU channels
        │
        ├──► Conv1D(k=3,  128 filters)  ─┐
        ├──► Conv1D(k=7,  128 filters)   ├── Concatenate  ← Multiscale feature extraction
        └──► Conv1D(k=13, 128 filters)  ─┘
                          │
                    MaxPool(2)
                          │
             ┌────────────┴────────────┐
             │  Residual Conv1D Block  │  × 2  (128 filters, BN, skip connection)
             └────────────┬────────────┘
                    MaxPool(2)
                          │
          ┌───────────────┴───────────────┐
          │  Bidirectional GRU (64 units) │  × 2  return_sequences=True
          └───────────────┬───────────────┘
                          │
              ┌───────────┴───────────┐
              │                       │
     ┌────────▼──────────┐   ┌────────▼──────────┐
     │ TemporalAttention │   │ TemporalAttention │
     │  (Activity head)  │   │   (FOG head)      │
     └────────┬──────────┘   └────────┬──────────┘
              │                       │
     Dense(64, relu)         Dense(64, relu)
     Dropout(0.5)            Dropout(0.5)
              │                       │
     Dense(3, softmax)       Dense(1, sigmoid)
              │                       │
     Output[0]: Activity     Output[1]: FOG probability
     [Other, Stationary,     [0.0 – 1.0]
      Walking]
```

### Model Specification

| Property | Value |
|---|---|
| Input Shape | `(1, 120, 6)` → flattened to `[1, 720]` int8 for TFLite |
| Input Dtype | `int8` (quantised; scale=2.8394, zero_point=−18 set at IMU) |
| Activity Output Shape | `[1, 3]` int8 |
| FOG Output Shape | `[1, 1]` int8 |
| Dequantisation | `float = (raw_int8 + 128) / 256` |
| TFLite Ops | Built-in + SELECT_TF_OPS (Flex for GRU) |
| TFLite Threads | 4 (Android) |
| Saved Format | `.keras` + `.tflite` |

### Loss Functions

| Head | Loss | Details |
|---|---|---|
| Activity | Multiclass Focal Loss | Per-class alpha, minority-class boost, γ = 3.0 |
| FOG | Binary Focal Loss | Positive-class weighting by imbalance ratio, γ = 2.0 |
| Combined Loss Weight | 2.5 : 2.5 | Equal contribution at optimiser level |

---

## 📡 BLE Communication Protocol

### Device Discovery

| Device | Advertised Name | Role |
|---|---|---|
| IMU Sensor | `NICLA_IMU_INT8` | BLE peripheral — data source |
| Tactile Actuator | `ESP32_TACTILE` | BLE peripheral — command target |

> **Scan timeout:** 10 seconds. Both devices must be advertising. Scan stops immediately when both are found.

---

### IMU BLE Service

| Property | Value |
|---|---|
| Service UUID | `12345678-1234-1234-1234-1234567890ab` |
| Data Characteristic UUID | `abcd1234-1234-1234-1234-abcdef123456` |
| Characteristic Type | BLENotify |
| Packet Size | 240 bytes |
| Windows per Inference | 3 consecutive notifications = 1 × 720-byte window |
| CCCD UUID | `00002902-0000-1000-8000-00805f9b34fb` |

#### IMU Packet Format

Each window is transmitted as 3 sequential BLE notifications:

```
Notification 1:  bytes [0   – 239]   ← samples 0–39
Notification 2:  bytes [240 – 479]   ← samples 40–79
Notification 3:  bytes [480 – 719]   ← samples 80–119
```

#### IMU Payload Layout (per window)

```
Offset  | Byte(s) | Field
--------|---------|------------------------
0       | 1 byte  | Sample 0 — ax (int8)
1       | 1 byte  | Sample 0 — ay (int8)
2       | 1 byte  | Sample 0 — az (int8)
3       | 1 byte  | Sample 0 — gx (int8)
4       | 1 byte  | Sample 0 — gy (int8)
5       | 1 byte  | Sample 0 — gz (int8)
6–11    | 6 bytes | Sample 1 — ax..gz
...
714–719 | 6 bytes | Sample 119 — ax..gz
```

Total: 120 samples × 6 channels × 1 byte = **720 bytes**

#### IMU Quantisation Parameters

| Parameter | Value |
|---|---|
| Scale (`INPUT_SCALE`) | `2.8394392` |
| Zero Point (`INPUT_ZERO_POINT`) | `-18` |
| Quantisation Formula | `q = clamp(round(x / scale) + zero_point, -128, 127)` |

---

### Tactile BLE Service (Nordic UART)

| Property | Value |
|---|---|
| Service UUID | `6e400001-b5a3-f393-e0a9-e50e24dcca9e` |
| Write Characteristic UUID | `6e400002-b5a3-f393-e0a9-e50e24dcca9e` |
| Notify Characteristic UUID | `6e400003-b5a3-f393-e0a9-e50e24dcca9e` |

#### Command Protocol

| Byte Value | Command | Actuator Behaviour |
|---|---|---|
| `0x01` | FOG ON | Motor vibrates for 7 seconds; resets timer on repeat |
| `0x00` | FOG OFF | Window expires naturally (motor is NOT force-stopped) |
| Other | — | Ignored; logged as unknown |

#### Tactile Timing

| Parameter | Value |
|---|---|
| Vibration Window | 7000 ms |
| Status Heartbeat | 5000 ms |
| Disconnect Alert Pulses | 3 |
| Alert ON Duration | 200 ms |
| Alert OFF Duration | 300 ms |
| BLE TX Power | 4 dBm |

---

## Repository Structure

```
fog-detection-system/
│
├── FogDetector/                          Android Studio project (Kotlin)
│   ├── settings.gradle
│   ├── build.gradle
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradle/wrapper/
│   │   └── gradle-wrapper.properties        Gradle 8.4
│   └── app/
│       ├── build.gradle                     compileSdk 34, TFLite deps
│       ├── proguard-rules.pro
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── assets/
│           │   ├── fog_model.tflite         ← YOU SUPPLY THIS FILE
│           │   └── README_model.txt         Model placement instructions
│           ├── java/com/fogdetector/
│           │   ├── FogDetectorApp.kt        Application class + notification channel
│           │   ├── ble/
│           │   │   ├── BleConstants.kt      All UUIDs, sizes, commands (single source of truth)
│           │   │   ├── BleConnectionState.kt IDLE→SCANNING→CONNECTING→CONNECTED enum
│           │   │   └── BleManager.kt        Scan, GATT, 720-byte accumulator, tactile write
│           │   ├── ml/
│           │   │   ├── FogDetectionModel.kt TFLite interpreter wrapper + dequantisation
│           │   │   └── PredictionResult.kt  Inference output data class
│           │   ├── notification/
│           │   │   └── NotificationHelper.kt FOG alarm notifications (API 33+ compatible)
│           │   ├── ui/
│           │   │   ├── MainViewModel.kt     MVVM bridge: BLE→ML→LiveData
│           │   │   ├── MainActivity.kt      Main screen: FOG card, activity card, BLE status
│           │   │   └── SettingsActivity.kt  Threshold slider, auto-tactile, notifications
│           │   └── util/
│           │       └── PreferencesManager.kt SharedPreferences accessor (3 settings)
│           └── res/
│               ├── layout/                  XML layouts for Main + Settings
│               ├── drawable/                ic_back.xml and other vector drawables
│               ├── menu/                    Toolbar menu
│               └── values/                 strings, colors, themes
│
├── model-training/                       Python / Google Colab training pipeline
│   ├── FOG_Activitytraining_2_head.ipynb    Full dual-head model training notebook
│   ├── fog_activity.keras                   Saved Keras model
│   └── fog_model.tflite                     TFLite deployment artifact
│
├── 📡 IMU-module/                           Arduino firmware — Nicla Sense ME
│   └── nicla_imu_int8/
│       ├── nicla_imu_int8.ino               IMU read → int8 quantise → BLE notify
│       └── README.md
│
├── Tactile-module/                       Arduino firmware — XIAO nRF52840
│   ├── nrf52840_tactile_actuator.ino        BLE receive → DRV2605L → ERM motor
│   └── README.md
│
├── LICENSE
└── README.md                                ← this file
```

---

## Installation

### Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Android Studio | Giraffe (2022.3.1)+ | For app build and deployment |
| Android SDK | API 26–34 | Minimum API 26 |
| Python | 3.10+ | For ML training |
| Google Colab / Jupyter | Any recent | Notebook runtime |
| Arduino IDE | 2.x | For firmware |
| nRF52 Board Support | Latest | Adafruit Bluefruit for nRF52840 |
| Arduino_BHY2 | Latest | For Nicla Sense ME |
| ArduinoBLE | Latest | For Nicla BLE stack |

---

### 1. Clone Repository

```bash
git clone https://github.com/YOUR_USERNAME/fog-detection-system.git
cd fog-detection-system
```

---

### 2. Python / ML Setup

```bash
# Create a virtual environment (recommended)
python -m venv .venv
source .venv/bin/activate        # Linux/macOS
.venv\Scripts\activate           # Windows

# Install dependencies
pip install tensorflow>=2.15.0 \
            scikit-learn>=1.3.0 \
            numpy>=1.24.0 \
            pandas>=2.0.0 \
            matplotlib>=3.7.0 \
            seaborn>=0.12.0
```

Or run directly in Google Colab (recommended — no local GPU required):

```
Upload FOG_Activitytraining_2_head.ipynb to Google Colab
Runtime → Change runtime type → T4 GPU
```

---

### 3. Android App Setup

```bash
# Open the Android project in Android Studio
File → Open → fog-detection-system/FogDetector/

# Sync Gradle when prompted
# (TFLite 2.14.0 + TFLite Support 0.4.4 will be downloaded automatically)
```

**Required: Add the TFLite model**

```bash
# After training, copy the exported model:
cp path/to/fog_model.tflite FogDetector/app/src/main/assets/fog_model.tflite
```

Build and run:
```
Build ▸ Make Project
Run   ▸ Run 'app'   (connect an Android device, min API 26)
```

---

### 4. IMU Firmware Setup (Nicla Sense ME)

1. Open **Arduino IDE** → **File → Open** → `IMU-module/nicla_imu_int8/nicla_imu_int8.ino`
2. Install board support:
   - **Tools → Board → Boards Manager** → Search `Arduino Mbed OS Nicla Boards` → Install
3. Install libraries:
   - **Tools → Manage Libraries** → Install `Arduino_BHY2` and `ArduinoBLE`
4. Select **Tools → Board → Arduino Nicla Sense ME**
5. Select the correct COM port
6. Click **Upload**
7. Open **Serial Monitor** at `115200 baud` to verify sensor readings

---

### 5. Tactile Firmware Setup (XIAO nRF52840)

1. Open **Arduino IDE** → **File → Open** → `Tactile-module/nrf52840_tactile_actuator.ino`
2. Install board support:
   - **Tools → Board → Boards Manager** → Search `Adafruit nRF52` → Install
3. Install library:
   - `Adafruit_DRV2605` (for DRV2605L haptic driver)
4. Wire the DRV2605L: `SDA → D4`, `SCL → D5`, VCC → 3.3 V
5. Select **Tools → Board → Seeed XIAO nRF52840**
6. Click **Upload**
7. Open **Serial Monitor** at `115200 baud` — you should see DRV2605L detected and BLE advertising

---

## Usage

### Training the Model

```bash
# Option A: Google Colab (recommended)
# 1. Open model-training/FOG_Activitytraining_2_head.ipynb in Colab
# 2. Mount Google Drive and upload your CSV dataset
# 3. Update CSV_DIR in the notebook to point to your dataset

CSV_DIR = "/content/drive/MyDrive/Colab Notebooks/csv_activity_fog"

# 4. Run all cells in order (Runtime → Run all)
# 5. Model artifacts will be saved to your Drive:
#    fog_activity.keras
#    fog_activity.tflite
```

**Expected CSV schema:**

```
window_id, subject, acc_x, acc_y, acc_z, gyro_x, gyro_y, gyro_z, activity, label
```

---

### Exporting the TFLite Model

The training notebook handles TFLite conversion automatically. To re-convert manually:

```python
import tensorflow as tf

model = tf.keras.models.load_model("fog_activity.keras")

converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS,
    tf.lite.OpsSet.SELECT_TF_OPS
]
converter.target_spec.supported_types = [tf.float16]

tflite_model = converter.convert()
with open("fog_model.tflite", "wb") as f:
    f.write(tflite_model)
```

---

### Uploading IMU Firmware

```bash
# From Arduino IDE:
# 1. Open nicla_imu_int8.ino
# 2. Select Board: Arduino Nicla Sense ME
# 3. Upload
# 4. Verify in Serial Monitor: 115200 baud
#    Expected output:
#    [BHY2] IMU initialized at 60 Hz
#    [BLE] Advertising as NICLA_IMU_INT8
#    [BLE] Central connected
#    [IMU] Window 1 sent: 720 bytes
```

---

### Uploading Tactile Firmware

```bash
# From Arduino IDE:
# 1. Open nrf52840_tactile_actuator.ino
# 2. Select Board: Seeed XIAO nRF52840
# 3. Upload
# 4. Verify in Serial Monitor: 115200 baud
#    Expected output:
#    [DRV2605L] Found at 0x5A
#    [BLE] Advertising as ESP32_TACTILE
#    [BLE] Central connected
#    [CMD] 0x01 received → motor ON (7000 ms)
```

---

### Running the End-to-End System

```
1. Power on the Nicla Sense ME (IMU module)
2. Power on the XIAO nRF52840 (Tactile module)
3. Open the FOG Detector Android app
4. Tap "Scan & Connect"
   → App connects to NICLA_IMU_INT8
   → App connects to ESP32_TACTILE
5. Inference begins automatically when IMU data flows
6. FOG detection:
   → Red card appears on screen
   → Push notification fires
   → Tactile vibration activates (7 seconds)
7. Adjust FOG threshold via Settings (⚙) if needed
```

---

## Results

### FOG Detection Performance

| Metric | Value |
|---|---|
| Accuracy | — |
| Precision | — |
| Recall | — |
| F1 Score | — |
| F2 Score | — |
| AUC-ROC | — |
| Inference Latency (Android) | — |
| Optimal Threshold | — |

> Results will be updated upon completion of final validation. Recall floor target: **≥ 0.90**; Precision floor target: **≥ 0.70**.

### Activity Recognition Performance

| Class | Precision | Recall | F1 Score | Support |
|---|---|---|---|---|
| Other (0) | — | — | — | — |
| Stationary (1) | — | — | — | — |
| Walking (2) | — | — | — | — |
| **Macro Avg** | — | — | — | — |
| **Weighted Avg** | — | — | — | — |

### System Latency

| Stage | Latency |
|---|---|
| IMU window collection (120 samples @ 60 Hz) | ≈ 2000 ms |
| BLE transmission (3 × 240-byte notifications) | ≈ 15–30 ms |
| TFLite inference (Android, 4 threads) | — |
| Tactile command write | ≈ 5–10 ms |
| **Total end-to-end latency** | **≈ 2000 + inference ms** |

---

## Performance Analysis

### Strengths

- **Subject-independent validation** via `GroupShuffleSplit` prevents data leakage and gives clinically honest performance estimates.
- **Dual-task learning** with shared temporal backbone enables efficient, single-model inference.
- **Tunable recall-precision tradeoff** — the per-patient threshold slider allows clinicians to balance sensitivity vs. false-alarm rate.
- **Fully offline** — no patient data leaves the device; compatible with clinical data governance requirements.
- **Edge quantisation** — int8 pipeline from firmware to model minimises BLE payload and model memory footprint.
- **Multiscale temporal features** — parallel Conv1D branches (k=3, 7, 13) capture both fine and coarse gait rhythms.

### Limitations

- **Single IMU placement** — a single wrist/ankle sensor may miss atypical FOG presentations; multi-sensor fusion is future work.
- **Flex TFLite ops** — GRU + custom attention requires `SELECT_TF_OPS`, which increases TFLite binary size on Android.
- **Fixed window stride** — non-overlapping 2-second windows may occasionally straddle a FOG onset, introducing up to one window delay.
- **Training dataset** — model performance is bounded by the diversity and size of the training dataset; clinical validation on a larger cohort is needed.
- **`0x00` command behaviour** — the tactile firmware does not support immediate motor stop; the 7-second window must expire naturally.

### Computational Requirements

| Component | Requirement |
|---|---|
| Training | Google Colab T4 GPU (recommended) or CPU (~4× slower) |
| Android Inference | Any modern smartphone (API 26+); 4 TFLite threads |
| IMU Firmware | Arduino Nicla Sense ME (nRF52832 host) |
| Tactile Firmware | XIAO nRF52840 (Cortex-M4F @ 64 MHz) |
| Battery (IMU) | Nicla built-in LiPo / USB-C |
| Battery (Tactile) | 3.3 V regulated; estimated > 8 hrs continuous |

---

##  Screenshots & Demo

### App Screenshots

| Main Screen — Normal | Main Screen — FOG Detected | Settings |
|---|---|---|
| *(screenshot placeholder)* | *(screenshot placeholder)* | *(screenshot placeholder)* |

### Hardware Photos

| IMU Module | Tactile Module | Full System |
|---|---|---|
| *(photo placeholder)* | *(photo placeholder)* | *(photo placeholder)* |



> To add screenshots: place images in `docs/images/` and update the table above.

---

## Future Work

### Core System

- [x] Real-time IMU BLE streaming (int8 quantised)
- [x] Dual-head TFLite inference on Android
- [x] BLE tactile feedback actuation
- [x] Configurable per-patient FOG threshold
- [x] Push notifications on FOG onset
- [x] Subject-independent model validation

### Near-Term

- [ ] Overlapping window stride for lower onset detection latency
- [ ] Multi-sensor fusion (bilateral wrist + ankle IMUs)
- [ ] On-device model fine-tuning / personalisation per patient
- [ ] Immediate motor stop command support in tactile firmware
- [ ] BLE reconnection logic for dropped connections

### Medium-Term

- [ ] Cloud analytics dashboard for clinician review
- [ ] Longitudinal FOG event logging and export
- [ ] REST API for remote threshold configuration
- [ ] iOS companion application
- [ ] Battery monitoring and low-power mode

### Long-Term

- [ ] Clinical validation study (IRB-approved, n ≥ 30 patients)
- [ ] FDA/CE medical device regulatory pathway assessment
- [ ] On-device model retraining from patient feedback
- [ ] Integration with hospital EHR systems
- [ ] Multi-modal fusion (audio cueing + vibration)

---

## Citation

If you use this work in your research, please cite:

### BibTeX

```bibtex
@misc{fogdetector2026,
  author       = {[YOUR NAME(S)]},
  title        = {{FOG Detection System}: Real-time Freezing of Gait Detection and Tactile Alerting for Parkinson's Disease using Edge AI and BLE Wearables},
  year         = {2026},
  howpublished = {\url{https://github.com/abhishekdev572-glitch/fog-detection-parkinson}},
  note         = {[Conference/Venue Name, if applicable]}
}
```

### APA

> Abhishek Kumar Rai. (2026). *FOG Detection System: Real-time Freezing of Gait Detection and Tactile Alerting for Parkinson's Disease using Edge AI and BLE Wearables* [Computer software]. GitHub. https://github.com/abhishekdev572-glitch/fog-detection-parkinson



---

## Contributors

| Name | Role | Affiliation |
|---|---|---|
| Abhishek Kumar Rai | Lead Developer, ML Engineer, Embedded Engineer | Kalinga Institute of Industrial Technology|
| Dr. Vikas Kumar | Research Supervisor | Ahilya Biomedical Ltd. |

Contributions, issues, and pull requests are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting.

---

## Acknowledgements

This project builds on the following open-source libraries, frameworks, and institutions:

**Machine Learning & Data**
- [TensorFlow / TensorFlow Lite](https://www.tensorflow.org/) — Model training and on-device inference
- [scikit-learn](https://scikit-learn.org/) — GroupShuffleSplit, StandardScaler, evaluation metrics
- [NumPy](https://numpy.org/), [pandas](https://pandas.pydata.org/) — Data processing
- [matplotlib](https://matplotlib.org/), [seaborn](https://seaborn.pydata.org/) — Visualisation

**Embedded / Hardware**
- [Arduino](https://www.arduino.cc/) — Firmware development environment
- [Adafruit Bluefruit (nRF52)](https://github.com/adafruit/Adafruit_nRF52_Arduino) — BLE stack for nRF52840
- [Arduino_BHY2](https://github.com/arduino-libraries/Arduino_BHY2) — Bosch sensor hub driver
- [ArduinoBLE](https://github.com/arduino-libraries/ArduinoBLE) — BLE library for Nicla
- Texas Instruments DRV2605L — Haptic driver IC

**Android**
- [Material Components for Android](https://material.io/develop/android) — UI components
- [TensorFlow Lite Android](https://www.tensorflow.org/lite/android) — On-device inference runtime

**Institutions**
- *[Your University / Research Lab — placeholder]*
- *[Clinical Partner — placeholder]*
- *[Funding Agency — placeholder]*

---

## License

```
MIT License

Copyright (c) 2026 [YOUR NAME]

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

> **⚕️ Clinical Notice:** This project is a research prototype. It is **not a certified medical device**. Consult your institution's IRB / ethics board before any deployment involving patients.

---

<div align="center">

Made for the Parkinson's Disease research community

Star this repo if it helped your research

</div>
