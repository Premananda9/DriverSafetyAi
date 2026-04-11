# DriverSafetyAi

AI-powered driver safety system that monitors a driver (and optionally the road scene) to detect risky conditions such as drowsiness, distraction, phone usage, or unsafe behavior and provides real-time alerts.

> **Status:** Active development  
> **Repo:** Premananda9/DriverSafetyAi

---

## Table of Contents
- [Overview](#overview)
- [Key Features](#key-features)
- [How It Works](#how-it-works)
- [Demo](#demo)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Requirements](#requirements)
- [Installation](#installation)
- [Configuration](#configuration)
- [Run the App](#run-the-app)
- [Training (Optional)](#training-optional)
- [Evaluation (Optional)](#evaluation-optional)
- [Troubleshooting](#troubleshooting)
- [Roadmap](#roadmap)
- [Security & Privacy Notes](#security--privacy-notes)
- [Contributing](#contributing)
- [License](#license)
- [Contact](#contact)

---

## Overview

**DriverSafetyAi** is designed to improve road safety by detecting dangerous driver states (e.g., drowsiness or distraction) and generating alerts in real time. The system typically runs on a webcam / in-cabin camera feed and can be extended to support edge devices.

Common use cases:
- Driver drowsiness detection (eye closure / blink rate)
- Distraction detection (looking away from the road)
- Phone usage detection
- Safety score / event logging for later analysis

---

## Key Features

- **Real-time camera processing** (webcam / video file)
- **Face & landmark tracking** (for eyes, mouth, head pose)
- **Drowsiness detection** (e.g., EAR thresholding or ML classifier)
- **Distraction detection** (gaze direction / head pose)
- **Alerts** via on-screen overlay (and optional audio)
- **Event logging** (timestamps, event type, confidence)
- Modular design so detectors can be swapped (heuristics ↔ ML models)

> Note: Exact features depend on the detectors enabled in your configuration.

---

## How It Works

1. Capture frames from a camera or video input  
2. Detect face (and optionally facial landmarks)  
3. Compute driver-state signals such as:
   - Eye Aspect Ratio (EAR)
   - Mouth Aspect Ratio (MAR) / yawning
   - Head pose / gaze direction
4. Apply thresholds or a trained model to classify risk events  
5. Trigger alerts and optionally record events to a log file

---

## Demo

### Live webcam
- Displays a video window with overlays (state + confidence)
- Plays an alert when risk is detected

### Video input
- Run the pipeline on a sample driving video and output annotated results

*(Add screenshots / GIFs here when available.)*

---

## Tech Stack

Typical stack for this kind of project (update this to match your code):
- **Python 3.10+**
- **OpenCV** for video capture & visualization
- **MediaPipe** or **dlib** for face landmarks (optional)
- **PyTorch / TensorFlow** if using deep learning models (optional)
- **NumPy** for numeric operations

---

## Project Structure

> Update this section to match your repo layout.

Example:
```
DriverSafetyAi/
  ├─ src/
  │   ├─ main.py
  │   ├─ detectors/
  │   │   ├─ drowsiness.py
  │   │   ├─ distraction.py
  │   ├─ utils/
  │   └─ config.py
  ├─ models/
  ├─ data/
  ├─ requirements.txt
  ├─ .env.example
  └─ README.md
```

---

## Requirements

- Python 3.10+ recommended
- A working webcam (for live mode)
- (Optional) CUDA-compatible GPU if using deep learning inference

---

## Installation

### 1) Clone
```bash
git clone https://github.com/Premananda9/DriverSafetyAi.git
cd DriverSafetyAi
```

### 2) Create a virtual environment (recommended)
```bash
python -m venv .venv
# Windows:
.venv\Scripts\activate
# macOS/Linux:
source .venv/bin/activate
```

### 3) Install dependencies
```bash
pip install -r requirements.txt
```

If you use `pyproject.toml` instead:
```bash
pip install .
```

---

## Configuration

If your project uses environment variables, create a `.env` file.

Example:
```bash
cp .env.example .env
```

Common configuration options you may have:
- `CAMERA_INDEX=0`
- `ALERT_SOUND=true`
- `DROWSINESS_THRESHOLD=...`
- `DISTRACTION_THRESHOLD=...`
- `LOG_EVENTS=true`
- `OUTPUT_DIR=outputs/`

> If your repo doesn’t have `.env.example`, you can remove this section.

---

## Run the App

### Live webcam
```bash
python main.py --source webcam
```

Or if your entry file is in `src/`:
```bash
python -m src.main --source webcam
```

### Run on a video file
```bash
python main.py --source video --path path/to/video.mp4
```

### Common optional flags (example)
```bash
python main.py \
  --camera-index 0 \
  --show-fps \
  --save-output \
  --log events.csv
```

---

## Training (Optional)

If your project includes training code, describe it here.

Example:
```bash
python train.py --data data/ --epochs 30 --batch-size 32
```

Output artifacts might be saved to:
- `models/`
- `runs/`
- `checkpoints/`

---

## Evaluation (Optional)

Example:
```bash
python eval.py --model models/best.pt --data data/test/
```

---

## Troubleshooting

**Camera not opening**
- Try a different camera index (`0`, `1`, `2`…)
- Close apps using the camera (Zoom/Teams/browser)
- On Linux, check camera permissions

**Import errors / dependency issues**
- Confirm you activated the venv
- Reinstall requirements:
  ```bash
  pip install --upgrade pip
  pip install -r requirements.txt
  ```

**Low FPS**
- Reduce input resolution
- Disable heavy detectors
- Use GPU acceleration if applicable

---

## Roadmap

Planned improvements (edit as you like):
- [ ] Better calibration per user (adaptive thresholds)
- [ ] Multi-class driver state model
- [ ] On-device deployment (Raspberry Pi / Jetson)
- [ ] Export safety reports (CSV + charts)
- [ ] Add unit tests and CI

---

## Security & Privacy Notes

- Camera frames may contain sensitive biometric data.
- Avoid storing raw video unless necessary.
- If logging events, store only minimal metadata (timestamp + type).
- For production use, ensure compliance with local privacy laws and obtain consent.

---

## Contributing

Contributions are welcome:
1. Fork the repo
2. Create a feature branch
3. Commit changes
4. Open a pull request

---

## License

Add your license here (e.g., MIT).  
If you haven’t chosen one yet, create a `LICENSE` file.

---

## Contact

Created by **Premananda9**  
Open an issue for bugs/feature requests.
