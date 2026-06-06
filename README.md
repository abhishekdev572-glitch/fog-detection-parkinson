<div align="center">

# FOG Detection System

### Future Work

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

## 📄 Citation

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

## 👥 Contributors

| Name | Role | Affiliation |
|---|---|---|
| Abhishek Kumar Rai | Lead Developer, ML Engineer, Embedded Engineer | Kalinga Institute of Industrial Technology|
| Dr. Vikas Kumar | Research Supervisor | Ahilya Biomedical Ltd. |

Contributions, issues, and pull requests are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting.

---

## 🙏 Acknowledgements

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

## ⚖️ License

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



</div>
