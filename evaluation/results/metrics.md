# FOG Assistive System

> Real-time Freezing of Gait (FOG) detection and tactile intervention system for Parkinson's Disease patients using wearable IMU sensing, Edge AI, BLE communication, and haptic feedback.

## Overview

Freezing of Gait (FOG) is one of the most debilitating symptoms of Parkinson's Disease, often leading to falls and reduced mobility. This project presents a complete end-to-end assistive system capable of detecting FOG episodes in real time and providing immediate tactile feedback to the user.

The system combines:

* Wearable IMU sensing using Arduino Nicla Sense ME
* BLE-based wireless communication
* Dual-head Deep Learning architecture for simultaneous Activity Recognition and FOG Detection
* Android-based TensorFlow Lite inference
* Haptic intervention using a XIAO nRF52840 and DRV2605L tactile module

## System Architecture

```text
Nicla Sense ME
      │
      ▼
BLE IMU Streaming
      │
      ▼
Android Application
      │
      ▼
Dual-Head TFLite Model
      │
      ├── Activity Recognition
      │
      └── FOG Detection
                │
                ▼
      Tactile Feedback Trigger
                │
                ▼
XIAO nRF52840 + DRV2605L
                │
                ▼
          Vibration Cue
```

---

## Key Features

* Real-time Freezing of Gait Detection
* Human Activity Recognition (HAR)
* Quantized TensorFlow Lite Deployment
* BLE-Based Wearable Sensor Streaming
* Closed-Loop Haptic Feedback
* Android Mobile Inference
* Edge AI Architecture
* Subject-Independent Evaluation

---

## Hardware Components

| Component              | Purpose              |
| ---------------------- | -------------------- |
| Arduino Nicla Sense ME | IMU Data Acquisition |
| Android Smartphone     | Real-Time Inference  |
| XIAO nRF52840          | Tactile Controller   |
| DRV2605L               | Haptic Driver        |
| ERM Vibration Motor    | User Feedback        |

---

## Machine Learning Pipeline

```text
Raw IMU Signals
        │
        ▼
Windowing (120 Samples)
        │
        ▼
Normalization
        │
        ▼
Dual-Head Deep Learning Model
        │
        ├── Activity Classification
        └── FOG Detection
        │
        ▼
TensorFlow Lite Conversion
        │
        ▼
Android Deployment
```

### Input Configuration

| Parameter         | Value                |
| ----------------- | -------------------- |
| Sampling Rate     | 60 Hz                |
| Window Length     | 120 Samples          |
| Window Duration   | 2 Seconds            |
| Input Channels    | 6                    |
| Deployment Format | TensorFlow Lite INT8 |

---

## Performance

### Human Activity Recognition

| Metric            | Value  |
| ----------------- | ------ |
| Accuracy          | 75.46% |
| Macro F1 Score    | 62.72% |
| Weighted F1 Score | 72.33% |

### FOG Detection

| Metric    | Value  |
| --------- | ------ |
| Accuracy  | 81.58% |
| Precision | 63.95% |
| Recall    | 82.07% |
| F1 Score  | 71.89% |
| ROC-AUC   | 0.905  |

The model is intentionally optimized for high FOG recall to minimize missed freezing events during real-world deployment.

---

## Repository Structure

```text
FOG-Assistive-System/
│
├── android_app/
├── firmware/
│   ├── imu_module/
│   └── tactile_module/
├── ml/
│   ├── training/
│   └── models/
├── evaluation/
├── docs/
└── hardware/
```

---

## Modules

### IMU Module

Streams quantized accelerometer and gyroscope data from the Arduino Nicla Sense ME over BLE.

### Android Application

Receives BLE data, performs TensorFlow Lite inference, displays predictions, and controls the tactile module.

### Machine Learning Pipeline

Dual-head architecture performing:

* Human Activity Recognition
* Freezing of Gait Detection

### Tactile Module

Provides haptic intervention through a DRV2605L-driven vibration motor when FOG events are detected.

---

## Evaluation

Evaluation artifacts are available in:

```text
evaluation/
├── confusion_matrices/
├── roc_curves/
└── results/
```

---

## Future Work

* On-device TinyML deployment
* Adaptive patient-specific thresholding
* Multi-sensor fusion
* Long-term clinical validation
* Cloud-assisted analytics

---

## License

This project is intended for research and educational purposes.
