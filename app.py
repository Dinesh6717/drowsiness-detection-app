from flask import Flask, request, jsonify
import base64
import cv2
import dlib
import numpy as np
from scipy.spatial import distance
from PIL import Image, ExifTags
import io
from datetime import datetime

app = Flask(__name__)

# Load dlib models
detector = dlib.get_frontal_face_detector()
predictor = dlib.shape_predictor("shape_predictor_68_face_landmarks.dat")  # Make sure this file is in the same folder

# Eye landmark indices
LEFT_EYE = list(range(36, 42))
RIGHT_EYE = list(range(42, 48))

# Drowsiness threshold and timer
DROWSY_THRESHOLD_SECONDS = 0.7
drowsy_start_time = None  # Global timer to track continuous drowsiness

# EAR calculation
def eye_aspect_ratio(eye):
    A = distance.euclidean(eye[1], eye[5])
    B = distance.euclidean(eye[2], eye[4])
    C = distance.euclidean(eye[0], eye[3])
    ear = (A + B) / (2.0 * C)
    return ear

# Correct image rotation based on EXIF
def correct_image_rotation(image_bytes):
    try:
        image = Image.open(io.BytesIO(image_bytes))

        for orientation in ExifTags.TAGS.keys():
            if ExifTags.TAGS[orientation] == 'Orientation':
                break

        exif = image._getexif()
        if exif is not None and orientation in exif:
            if exif[orientation] == 3:
                image = image.rotate(180, expand=True)
            elif exif[orientation] == 6:
                image = image.rotate(270, expand=True)
            elif exif[orientation] == 8:
                image = image.rotate(90, expand=True)

        return cv2.cvtColor(np.array(image), cv2.COLOR_RGB2BGR)

    except Exception as e:
        print(f"Rotation correction failed: {e}")
        return None

@app.route('/')
def index():
    return "Flask server for Drowsiness Detection is running!"

@app.route('/detect_drowsiness', methods=['POST'])
def detect_drowsiness():
    global drowsy_start_time

    try:
        data = request.get_json()
        if not data or "image" not in data:
            return jsonify({"error": "No image provided", "drowsy": False}), 400

        img_data = data["image"]
        print("Received image for drowsiness detection.")

        if "," in img_data:
            img_data = img_data.split(",")[1]

        image_bytes = base64.b64decode(img_data)
        frame = correct_image_rotation(image_bytes)

        if frame is None:
            return jsonify({"error": "Image decoding or rotation failed", "drowsy": False}), 400

        if frame.shape[0] < 480 or frame.shape[1] < 640:
            frame = cv2.resize(frame, (640, 480))
            print("Resized image to 640x480.")

        cv2.imwrite("received_frame.jpg", frame)
        print(f"Saved image: received_frame.jpg | Shape: {frame.shape}")

        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        faces = detector(gray)

        if not faces:
            print("No face detected.")
            drowsy_start_time = None
            return jsonify({"error": "No face detected", "drowsy": False})

        for face in faces:
            shape = predictor(gray, face)
            shape_np = np.array([[p.x, p.y] for p in shape.parts()])

            left_eye = shape_np[LEFT_EYE]
            right_eye = shape_np[RIGHT_EYE]

            left_ear = eye_aspect_ratio(left_eye)
            right_ear = eye_aspect_ratio(right_eye)
            ear = (left_ear + right_ear) / 2.0

            print(f"EAR: {ear:.3f}")

            if ear < 0.25:
                if drowsy_start_time is None:
                    drowsy_start_time = datetime.now()
                    print("Started drowsiness timer.")
                else:
                    elapsed = (datetime.now() - drowsy_start_time).total_seconds()
                    print(f"Drowsy for {elapsed:.2f} seconds")
                    if elapsed >= DROWSY_THRESHOLD_SECONDS:
                        return jsonify({"drowsy": True})
            else:
                if drowsy_start_time is not None:
                    print("Drowsiness ended. Timer reset.")
                drowsy_start_time = None

            return jsonify({"drowsy": False})

        print("Face found but landmarks not detected properly.")
        return jsonify({"error": "Face found but landmarks not detected", "drowsy": False})

    except Exception as e:
        print(f"Exception occurred: {str(e)}")
        return jsonify({"error": str(e), "drowsy": False})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
