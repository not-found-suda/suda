import argparse
import json
import os
import time
from collections import Counter, deque
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np

# TensorFlow Lite 사용 (모바일 환경 모사)
try:
    import tensorflow as tf
except ImportError:
    print("TensorFlow가 설치되어 있지 않습니다. pip install tensorflow 를 실행해주세요.")
    exit(1)

from PIL import Image, ImageDraw, ImageFont

INPUT_DIM = 332
LANDMARK_DIM = 282
MAX_LEN = 30
CONFIDENCE_THRESHOLD = 0.80
PREDICTION_SMOOTHING = 7
STABLE_MIN_COUNT = 5

BASE_DIR = Path(__file__).resolve().parent / "models"
DEFAULT_MODEL_DIR = "v7_117words_tcn_2"

NEUTRAL_FACE_WINDOW = 45
MIN_NEUTRAL_FACE_SAMPLES = 15
QUESTION_EYEBROW_DELTA_THRESHOLD = 0.055

LEFT_EYEBROW_IDXS = [70, 63, 105, 66]
RIGHT_EYEBROW_IDXS = [336, 296, 334, 293]
LEFT_EYE_CENTER_IDXS = [33, 133]
RIGHT_EYE_CENTER_IDXS = [362, 263]

BASE_FACE_IDXS = [
    70, 63, 105, 66,
    336, 296, 334, 293,
    33, 160, 158, 133, 153, 144,
    362, 385, 387, 263, 373, 380,
    61, 146, 91, 181, 84, 17, 314, 405, 321, 375,
]
FACEPLUS_IDXS = [
    1, 4,
    152,
    234, 454,
    172, 397,
    148, 377,
    13, 14,
    78, 308,
    82, 312,
]
FACE_IDXS = BASE_FACE_IDXS + FACEPLUS_IDXS
POSE_IDXS = [0, 7, 8, 11, 12, 13, 14]
HAND_TIP_IDXS = [4, 8, 12, 16, 20]
FACE_ANCHOR_IDXS = [1, 13, 152, 234, 454]

LEFT_SHOULDER_IDX = 90
RIGHT_SHOULDER_IDX = 91

def resolve_from_base(path):
    path = Path(path)
    if path.is_absolute():
        return path
    return (BASE_DIR / path).resolve()

def _flatten_landmarks(landmarks, idxs):
    return np.array(
        [[landmarks[idx].x, landmarks[idx].y, landmarks[idx].z] for idx in idxs],
        dtype=np.float32,
    ).flatten()

def _hand_array(hand_landmarks):
    if not hand_landmarks:
        return np.zeros((21, 3), dtype=np.float32), False
    points = np.array([[lm.x, lm.y, lm.z] for lm in hand_landmarks.landmark], dtype=np.float32)
    return points, True

def _hand_face_distances(left_hand, left_present, right_hand, right_present, face_landmarks):
    if not face_landmarks:
        return np.zeros(len(HAND_TIP_IDXS) * 2 * len(FACE_ANCHOR_IDXS), dtype=np.float32)

    anchors = np.array(
        [
            [face_landmarks.landmark[idx].x, face_landmarks.landmark[idx].y, face_landmarks.landmark[idx].z]
            for idx in FACE_ANCHOR_IDXS
        ],
        dtype=np.float32,
    )

    features = []
    for hand_points, is_present in ((left_hand, left_present), (right_hand, right_present)):
        if not is_present:
            features.extend([0.0] * (len(HAND_TIP_IDXS) * len(FACE_ANCHOR_IDXS)))
            continue

        for tip_idx in HAND_TIP_IDXS:
            tip = hand_points[tip_idx]
            features.extend(np.linalg.norm(anchors - tip, axis=1).tolist())

    return np.array(features, dtype=np.float32)

def extract_keypoints(results, last_pose, last_face, normalize_mode):
    left_hand, left_present = _hand_array(results.left_hand_landmarks)
    right_hand, right_present = _hand_array(results.right_hand_landmarks)
    lh = left_hand.flatten()
    rh = right_hand.flatten()

    if results.face_landmarks:
        face = _flatten_landmarks(results.face_landmarks.landmark, FACE_IDXS)
        last_face = face
    else:
        face = last_face if last_face is not None else np.zeros(len(FACE_IDXS) * 3, dtype=np.float32)

    if results.pose_landmarks:
        pose = _flatten_landmarks(results.pose_landmarks.landmark, POSE_IDXS)
        last_pose = pose
    else:
        pose = last_pose if last_pose is not None else np.zeros(len(POSE_IDXS) * 3, dtype=np.float32)

    distances = _hand_face_distances(
        left_hand, left_present, right_hand, right_present, results.face_landmarks
    )

    feature = np.concatenate([lh, rh, face, pose, distances]).astype(np.float32)
    if feature.shape[0] != INPUT_DIM:
        raise ValueError(f"Unexpected feature dim: {feature.shape[0]} != {INPUT_DIM}")

    return normalize_features(feature, normalize_mode), last_pose, last_face

def normalize_features(feature, normalize_mode):
    if normalize_mode in ("none", "raw", "off"):
        return np.array(feature, dtype=np.float32)

    landmarks = feature[:LANDMARK_DIM].reshape(-1, 3)
    distances = feature[LANDMARK_DIM:]

    l_shoulder = landmarks[LEFT_SHOULDER_IDX]
    r_shoulder = landmarks[RIGHT_SHOULDER_IDX]
    shoulder_center = (l_shoulder + r_shoulder) / 2.0

    shoulder_width = np.linalg.norm(l_shoulder - r_shoulder)
    if shoulder_width < 1e-6:
        shoulder_width = 1.0

    normalized_landmarks = (landmarks - shoulder_center) / shoulder_width
    normalized_distances = distances / shoulder_width
    return np.concatenate([normalized_landmarks.flatten(), normalized_distances]).astype(np.float32)

def get_normalized_eyebrow_gap(results):
    if not results.face_landmarks:
        return None

    landmarks = results.face_landmarks.landmark
    left_eye = np.mean([[landmarks[idx].x, landmarks[idx].y] for idx in LEFT_EYE_CENTER_IDXS], axis=0)
    right_eye = np.mean([[landmarks[idx].x, landmarks[idx].y] for idx in RIGHT_EYE_CENTER_IDXS], axis=0)
    eye_distance = np.linalg.norm(left_eye - right_eye)
    if eye_distance < 1e-6:
        return None

    eyebrow_y = np.mean([landmarks[idx].y for idx in LEFT_EYEBROW_IDXS + RIGHT_EYEBROW_IDXS])
    eye_y = np.mean([landmarks[idx].y for idx in LEFT_EYE_CENTER_IDXS + RIGHT_EYE_CENTER_IDXS])
    return float((eye_y - eyebrow_y) / eye_distance)

def classify_sentence_type(current_gap, neutral_gaps):
    if current_gap is None or len(neutral_gaps) < MIN_NEUTRAL_FACE_SAMPLES:
        return "평서문", 0.0, None

    neutral_gap = float(np.median(neutral_gaps))
    eyebrow_delta = current_gap - neutral_gap

    if eyebrow_delta >= QUESTION_EYEBROW_DELTA_THRESHOLD:
        return "의문문", eyebrow_delta, neutral_gap

    return "평서문", eyebrow_delta, neutral_gap

def has_visible_hand(results):
    return results.left_hand_landmarks is not None or results.right_hand_landmarks is not None

def pad_sequence(sequence, max_len=MAX_LEN):
    frames = np.array(sequence, dtype=np.float32)
    if len(frames) == 0:
        return np.zeros((max_len, INPUT_DIM), dtype=np.float32)

    if len(frames) > max_len:
        indices = np.linspace(0, len(frames) - 1, max_len, dtype=int)
        return frames[indices]

    if len(frames) < 15:
        indices = np.linspace(0, len(frames) - 1, max_len)
        upsampled = np.zeros((max_len, INPUT_DIM), dtype=np.float32)
        for i, float_idx in enumerate(indices):
            low = int(np.floor(float_idx))
            high = int(np.ceil(float_idx))
            weight = float_idx - low
            if low == high:
                upsampled[i] = frames[low]
            else:
                upsampled[i] = frames[low] * (1 - weight) + frames[high] * weight
        return upsampled

    pad_len = max_len - len(frames)
    padding = np.repeat(frames[-1:], pad_len, axis=0)
    return np.vstack((frames, padding)).astype(np.float32)

def draw_text(frame, lines):
    image = Image.fromarray(cv2.cvtColor(frame, cv2.COLOR_BGR2RGB))
    draw = ImageDraw.Draw(image)

    try:
        font = ImageFont.truetype("malgun.ttf", 30)
    except Exception:
        font = ImageFont.load_default()

    y = 25
    for line in lines:
        draw.text((25, y), line, font=font, fill=(0, 255, 0))
        y += 38

    return cv2.cvtColor(np.array(image), cv2.COLOR_RGB2BGR)

def parse_args():
    parser = argparse.ArgumentParser(description="Realtime webcam inference for V7 TFLite models.")
    parser.add_argument("--model-dir", default=DEFAULT_MODEL_DIR, help="Model directory")
    parser.add_argument("--precision", choices=["float32", "float16"], default="float16", help="Precision of the tflite model to load")
    return parser.parse_args()

def main():
    args = parse_args()
    model_dir = resolve_from_base(args.model_dir)

    tflite_path = model_dir / f"best_sign_model_v7_{args.precision}.tflite"
    config_path = model_dir / "train_config_v7.json"
    label_map_path = model_dir / "label_map_v7.json"

    if not tflite_path.exists() or not config_path.exists() or not label_map_path.exists():
        print(f"Model files not found in {model_dir}. Please make sure you copied the .tflite files here!")
        print(f"Missing: {tflite_path}")
        return

    with open(config_path, "r", encoding="utf-8") as f:
        config = json.load(f)
    
    with open(label_map_path, "r", encoding="utf-8") as f:
        label_map_raw = json.load(f)
        label_map = {int(k): v for k, v in label_map_raw.items()}

    model_type = config.get("model_type", "tcn")
    normalize_mode = config["stats"].get("normalize_mode", "shoulder")
    num_classes = len(label_map)

    # Load TFLite Model
    print(f"Loading TFLite model from {tflite_path} ...")
    interpreter = tf.lite.Interpreter(model_path=str(tflite_path))
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print(f"Loaded {args.precision} TFLite model!")
    print(f"Normalize Mode: {normalize_mode}")
    print(f"Classes: {num_classes}")

    mp_holistic = mp.solutions.holistic
    mp_drawing = mp.solutions.drawing_utils
    cap = cv2.VideoCapture(0)

    frame_window = deque(maxlen=MAX_LEN)
    prediction_window = deque(maxlen=PREDICTION_SMOOTHING)
    
    current_action = "none"
    current_confidence = 0.0
    last_pose = None
    last_face = None

    # 최종 확정된 단어와 타임스탬프 (2초 유지용)
    locked_action = "none"
    locked_time = 0.0
    last_logged_action = None
    
    missing_hand_frames = 0
    sentence_buffer = [] # 문장 누적용 버퍼
    
    neutral_gaps = deque(maxlen=NEUTRAL_FACE_WINDOW)
    sentence_is_question = False

    with mp_holistic.Holistic(
        static_image_mode=False, min_detection_confidence=0.5, min_tracking_confidence=0.5,
    ) as holistic:
        while cap.isOpened():
            success, frame = cap.read()
            if not success:
                break

            image_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            results = holistic.process(image_rgb)

            hand_visible = has_visible_hand(results)
            eyebrow_gap = get_normalized_eyebrow_gap(results)

            if not hand_visible:
                missing_hand_frames += 1
                if eyebrow_gap is not None:
                    neutral_gaps.append(eyebrow_gap)
            else:
                missing_hand_frames = 0
                current_sentence_type, current_eyebrow_delta, current_neutral_gap = classify_sentence_type(
                    eyebrow_gap, neutral_gaps
                )
                if current_sentence_type == "의문문":
                    sentence_is_question = True

            # 손이 15프레임(약 0.5초) 이상 연속으로 안 보일 때만 초기화
            if missing_hand_frames > 15:
                # 손이 사라지면 지금까지 모은 단어들을 문장으로 출력
                if sentence_buffer:
                    final_type = "의문문" if sentence_is_question else "평서문"
                    print(f"\\n✨ === 최종 문장: {' '.join(sentence_buffer)} ({final_type}) === ✨\\n")
                    sentence_buffer.clear()
                    sentence_is_question = False

                frame_window.clear()
                prediction_window.clear()
                current_action = "none"
                current_confidence = 0.0
                last_pose = None
                last_face = None
                last_logged_action = None
            else:
                # 손이 잠깐 안 보여도 빈 좌표(0)로 채워서 큐의 흐름을 유지함
                keypoints, last_pose, last_face = extract_keypoints(results, last_pose, last_face, normalize_mode)
                frame_window.append(keypoints)

            # 22프레임(~0.73초) 이상 모여야 추론 시작 (15→22: 부분 제스처 오인식 방지)
            if len(frame_window) >= 22:
                input_array = pad_sequence(frame_window)
                input_tensor = np.expand_dims(input_array, axis=0).astype(np.float32)
                
                # TFLite Inference
                interpreter.set_tensor(input_details[0]['index'], input_tensor)
                interpreter.invoke()
                probs = interpreter.get_tensor(output_details[0]['index'])[0]
                
                max_idx = np.argmax(probs)
                predicted_label = label_map[max_idx]
                current_confidence = probs[max_idx]

                if current_confidence >= CONFIDENCE_THRESHOLD:
                    prediction_window.append(predicted_label)
                else:
                    prediction_window.clear()
                    prediction_window.append("none")

                if prediction_window:
                    candidate, count = Counter(prediction_window).most_common(1)[0]
                    if candidate == "none" or count >= STABLE_MIN_COUNT:
                        current_action = candidate

                        # none이 아닌 새로운 단어가 확정되었을 때
                        if current_action != "none":
                            if current_action != last_logged_action:
                                print(f"👉 Detected: {current_action} (Confidence: {current_confidence:.3f})")
                                locked_action = current_action
                                locked_time = time.time()
                                last_logged_action = current_action

                                # 문장 버퍼에 단어 추가 (중복 방지)
                                if not sentence_buffer or sentence_buffer[-1] != current_action:
                                    sentence_buffer.append(current_action)

                                # 감지 후 윈도우 초기화
                                # 이전 동작 프레임이 다음 추론에 섞이지 않도록 완전 리셋
                                # → 동작 후 가만히 있을 때 연속 오감지 방지
                                frame_window.clear()
                                prediction_window.clear()
                        else:
                            last_logged_action = None

            # 2초가 지나면 띄워주던 단어를 지움
            if time.time() - locked_time > 2.0:
                locked_action = "none"

            mp_drawing.draw_landmarks(frame, results.pose_landmarks, mp_holistic.POSE_CONNECTIONS)
            mp_drawing.draw_landmarks(frame, results.left_hand_landmarks, mp_holistic.HAND_CONNECTIONS)
            mp_drawing.draw_landmarks(frame, results.right_hand_landmarks, mp_holistic.HAND_CONNECTIONS)

            # 화면에 띄울 텍스트 준비
            sentence_str = ' '.join(sentence_buffer) if sentence_buffer else ""
            type_str = "의문문" if sentence_is_question else "평서문"

            display_frame = cv2.flip(frame, 1)
            display_frame = draw_text(
                display_frame,
                [
                    f"Model: TFLite V7 ({args.precision})",
                    f"Live Pred: {current_action} ({current_confidence:.2f})",
                    f"Result: {locked_action if locked_action != 'none' else ''}",
                    f"Buffer: {sentence_str} ({type_str})",
                    f"Frames: {len(frame_window)}/{MAX_LEN}",
                    "Quit: q",
                ],
            )

            cv2.imshow("V7 TFLite Real-time Inference", display_frame)

            if cv2.waitKey(1) & 0xFF == ord("q"):
                break

    cap.release()
    cv2.destroyAllWindows()

if __name__ == "__main__":
    main()
