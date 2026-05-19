import argparse
import json
import os
import time
from collections import Counter, deque
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np
import torch
from PIL import Image, ImageDraw, ImageFont

from model import build_ksl_model_v6

INPUT_DIM = 332
LANDMARK_DIM = 282
MAX_LEN = 30
CONFIDENCE_THRESHOLD = 0.80
PREDICTION_SMOOTHING = 5
STABLE_MIN_COUNT = 4

BASE_DIR = Path(__file__).resolve().parent / "models"
DEFAULT_MODEL_DIR = "v6_24words_tcn_2"

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
        return "?됱꽌臾?, 0.0, None

    neutral_gap = float(np.median(neutral_gaps))
    eyebrow_delta = current_gap - neutral_gap

    if eyebrow_delta >= QUESTION_EYEBROW_DELTA_THRESHOLD:
        return "?섎Ц臾?, eyebrow_delta, neutral_gap

    return "?됱꽌臾?, eyebrow_delta, neutral_gap

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
    parser = argparse.ArgumentParser(description="Realtime webcam inference for V6 PyTorch models.")
    parser.add_argument("--model-dir", default=DEFAULT_MODEL_DIR, help="Model directory under Transformer_v5_Impl/models")
    return parser.parse_args()

def main():
    args = parse_args()
    model_dir = resolve_from_base(args.model_dir)

    model_path = model_dir / "best_sign_model_v6.pt"
    config_path = model_dir / "train_config_v6.json"
    label_map_path = model_dir / "label_map_v6.json"

    if not model_path.exists() or not config_path.exists() or not label_map_path.exists():
        print(f"Model files not found in {model_dir}.")
        return

    with open(config_path, "r", encoding="utf-8") as f:
        config = json.load(f)

    with open(label_map_path, "r", encoding="utf-8") as f:
        label_map_raw = json.load(f)
        label_map = {int(k): v for k, v in label_map_raw.items()}

    model_type = config.get("model_type", "tcn")
    normalize_mode = config["stats"].get("normalize_mode", "shoulder")
    num_classes = len(label_map)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = build_ksl_model_v6(model_type=model_type, num_classes=num_classes)
    model.load_state_dict(torch.load(model_path, map_location=device))
    model.to(device)
    model.eval()

    print(f"Loaded {model_type} model from {model_dir}")
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

    # 理쒖쥌 ?뺤젙???⑥뼱? ??꾩뒪?ы봽 (2珥??좎???
    locked_action = "none"
    locked_time = 0.0
    last_logged_action = None

    missing_hand_frames = 0
    sentence_buffer = [] # 臾몄옣 ?꾩쟻??踰꾪띁

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
                if current_sentence_type == "?섎Ц臾?:
                    sentence_is_question = True

            # ?먯씠 15?꾨젅????0.5珥? ?댁긽 ?곗냽?쇰줈 ??蹂댁씪 ?뚮쭔 珥덇린??
            if missing_hand_frames > 15:
                # ?먯씠 ?щ씪吏硫?吏湲덇퉴吏 紐⑥? ?⑥뼱?ㅼ쓣 臾몄옣?쇰줈 異쒕젰
                if sentence_buffer:
                    final_type = "?섎Ц臾? if sentence_is_question else "?됱꽌臾?
                    print(f"\n??=== 理쒖쥌 臾몄옣: {' '.join(sentence_buffer)} ({final_type}) === ??n")
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
                # ?먯씠 ?좉퉸 ??蹂댁뿬??鍮?醫뚰몴(0)濡?梨꾩썙???먯쓽 ?먮쫫???좎???
                keypoints, last_pose, last_face = extract_keypoints(results, last_pose, last_face, normalize_mode)
                frame_window.append(keypoints)

            # 30?꾨젅?꾩쓣 ??湲곕떎由ъ? ?딄퀬 15?꾨젅??0.5珥?留?紐⑥뿬??諛붾줈 蹂닿컙(upsample)?섏뿬 異붾줎 ?쒖옉
            if len(frame_window) >= 15:
                input_array = pad_sequence(frame_window)
                input_tensor = torch.tensor(input_array, dtype=torch.float32).unsqueeze(0).to(device)

                with torch.no_grad():
                    outputs = model(input_tensor, return_probs=True)
                    probs = outputs[0].cpu().numpy()

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

                        # none???꾨땶 ?덈줈???⑥뼱媛 ?뺤젙?섏뿀????
                        if current_action != "none":
                            if current_action != last_logged_action:
                                print(f"?몛 Detected: {current_action} (Confidence: {current_confidence:.3f})")
                                locked_action = current_action
                                locked_time = time.time()
                                last_logged_action = current_action

                                # 臾몄옣 踰꾪띁???⑥뼱 異붽? (以묐났 諛⑹?)
                                if not sentence_buffer or sentence_buffer[-1] != current_action:
                                    sentence_buffer.append(current_action)
                        else:
                            last_logged_action = None

            # 2珥덇? 吏?섎㈃ ?꾩썙二쇰뜕 ?⑥뼱瑜?吏?
            if time.time() - locked_time > 2.0:
                locked_action = "none"

            mp_drawing.draw_landmarks(frame, results.pose_landmarks, mp_holistic.POSE_CONNECTIONS)
            mp_drawing.draw_landmarks(frame, results.left_hand_landmarks, mp_holistic.HAND_CONNECTIONS)
            mp_drawing.draw_landmarks(frame, results.right_hand_landmarks, mp_holistic.HAND_CONNECTIONS)

            display_frame = cv2.flip(frame, 1)
            display_frame = draw_text(
                display_frame,
                [
                    f"Model: {model_type.upper()} ({normalize_mode})",
                    f"Live Pred: {current_action} ({current_confidence:.2f})",
                    f"Result: {locked_action if locked_action != 'none' else ''}",
                    f"Frames: {len(frame_window)}/{MAX_LEN}",
                    "Quit: q",
                ],
            )

            cv2.imshow("V6 Real-time Inference", display_frame)

            if cv2.waitKey(1) & 0xFF == ord("q"):
                break

    cap.release()
    cv2.destroyAllWindows()

if __name__ == "__main__":
    main()
