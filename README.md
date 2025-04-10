# 😴 Drowsiness Detection Android App

A real-time drowsiness detection Android application using Jetpack Compose, CameraX, and a Python backend with dlib facial landmark analysis. Ideal for driver safety and fatigue monitoring.

---

## 🚀 Features

- 📷 Real-time camera streaming with CameraX
- 🧠 Drowsiness detection via Eye Aspect Ratio (EAR) using Python backend
- 🔊 Plays alert sound when drowsiness is detected
- 📍 Autofill live GPS location using Fused Location API
- 📱 Clean Jetpack Compose UI with input validation
- 🔐 Permissions handling for camera and location
- 🔄 Front camera image rotation and mirroring handled for accurate face detection

---

## 📱 UI Workflow

1. User enters:
   - Name (alphabets only)
   - Phone number (digits only)
   - Location (manual or live GPS autofill)

2. Clicks **"Start Streaming"** to activate front camera.

3. App sends frames to a Python backend server for EAR-based drowsiness analysis.

4. If drowsiness is detected, a loud **alert sound** plays until the user is awake.

---

## 🧰 Tech Stack

| Layer         | Technology                |
| ------------- | ------------------------- |
| UI            | Jetpack Compose           |
| Camera        | CameraX                   |
| Location      | Fused Location Provider   |
| Network       | Retrofit                  |
| Backend       | Python (Flask / FastAPI)  |
| Detection     | dlib facial landmarks     |
| Format        | Base64 image transfer     |

---

## 🔧 Setup Instructions

### Android App

1. Clone this repository:
   ```bash
   git clone https://github.com/your-username/drowsiness-detection-app.git
   cd drowsiness-detection-app
