import json
import os
from collections import deque

import cv2
import mediapipe as mp
import numpy as np
import torch
import torch.nn.functional as F

from model import SignBiLSTM

# ── 설정 ─────────────────────────────────────────────────
SEQ_LEN              = 60
CONF_THRESHOLD       = 0.70   # 신뢰도 임계값
NONE_RESET_THRESHOLD = 0.85   # NONE 감지 시 윈도우 리셋 임계값
VOTE_WINDOW          = 10     # 다수결 투표 윈도우 크기
MODEL_DIR            = os.path.join("models")
LABEL_MAP_PATH       = os.path.join("data", "processed", "label_map.json")
SCALER_MEAN_PATH     = os.path.join("data", "processed", "scaler_mean.npy")
SCALER_SCALE_PATH    = os.path.join("data", "processed", "scaler_scale.npy")


def get_latest_model_path() -> str:
    """models/ 폴더에서 sign_lstm_N.pt 중 가장 높은 N의 파일을 반환합니다."""
    files = [
        f for f in os.listdir(MODEL_DIR)
        if f.startswith("sign_lstm_") and f.endswith(".pt")
    ]
    if not files:
        raise FileNotFoundError(f"학습된 모델이 {MODEL_DIR} 안에 없습니다. 먼저 train.py를 실행하세요.")
    nums = []
    for name in files:
        stem = name[len("sign_lstm_"):-len(".pt")]
        if stem.isdigit():
            nums.append(int(stem))
    latest = max(nums)
    return os.path.join(MODEL_DIR, f"sign_lstm_{latest}.pt")

mp_holistic  = mp.solutions.holistic
mp_drawing   = mp.solutions.drawing_utils


# ── 키포인트 추출 (collect_data.py 와 동일) ──────────────
def extract_keypoints(results) -> np.ndarray:
    features = []
    if results.left_hand_landmarks:
        for lm in results.left_hand_landmarks.landmark:
            features.extend([lm.x, lm.y, lm.z])
    else:
        features.extend([0.0] * 63)

    if results.right_hand_landmarks:
        for lm in results.right_hand_landmarks.landmark:
            features.extend([lm.x, lm.y, lm.z])
    else:
        features.extend([0.0] * 63)

    pose_idxs = [0, 11, 12, 13, 14]
    if results.pose_landmarks:
        for idx in pose_idxs:
            lm = results.pose_landmarks.landmark[idx]
            features.extend([lm.x, lm.y, lm.z])
    else:
        features.extend([0.0] * 15)

    return np.array(features, dtype=np.float32)


# ── 정규화 (preprocess 와 동일한 scaler 적용) ────────────
def normalize(kp: np.ndarray, mean: np.ndarray, scale: np.ndarray) -> np.ndarray:
    return (kp - mean) / (scale + 1e-8)


# ── 화면 오버레이 ─────────────────────────────────────────
def draw_overlay(frame, pred_label: str, pred_conf: float,
                 frame_count: int, vote_counts: dict, idx_to_label: dict):
    h, w = frame.shape[:2]
    overlay = frame.copy()

    # 반투명 배경 박스
    cv2.rectangle(overlay, (0, 0), (w, 110), (0, 0, 0), -1)
    cv2.addWeighted(overlay, 0.5, frame, 0.5, 0, frame)

    # 예측 단어
    color = (0, 255, 80) if pred_conf >= CONF_THRESHOLD else (0, 140, 255)
    cv2.putText(frame, f"Sign: {pred_label}", (20, 42),
                cv2.FONT_HERSHEY_SIMPLEX, 1.2, color, 3)

    # 신뢰도
    conf_pct = int(pred_conf * 100)
    cv2.putText(frame, f"Confidence: {conf_pct}%", (20, 78),
                cv2.FONT_HERSHEY_SIMPLEX, 0.75, (255, 255, 255), 2)

    # 프레임 진행 바
    bar_w = int((frame_count / SEQ_LEN) * (w - 40))
    cv2.rectangle(frame, (20, 90), (w - 20, 105), (60, 60, 60), -1)
    cv2.rectangle(frame, (20, 90), (20 + bar_w, 105), (0, 200, 255), -1)
    cv2.putText(frame, f"{frame_count}/{SEQ_LEN}", (20, 103),
                cv2.FONT_HERSHEY_SIMPLEX, 0.45, (255, 255, 255), 1)

    # 우측 상단: 투표 현황
    for i, (idx, cnt) in enumerate(sorted(vote_counts.items())):
        lbl = idx_to_label.get(idx, str(idx))
        cv2.putText(frame, f"{lbl}: {cnt}", (w - 180, 30 + i * 22),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.55, (200, 200, 200), 1)

    cv2.putText(frame, "Q: quit", (20, h - 15),
                cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 200, 255), 1)


# ── 메인 ─────────────────────────────────────────────────
def main():
    # 레이블 맵 로드
    with open(LABEL_MAP_PATH, "r", encoding="utf-8") as f:
        label_map = json.load(f)
    idx_to_label = {v: k for k, v in label_map.items()}

    # scaler 로드
    scaler_mean  = np.load(SCALER_MEAN_PATH)
    scaler_scale = np.load(SCALER_SCALE_PATH)

    # 모델 로드 (가장 최신 번호 파일 자동 탐색)
    MODEL_PATH = get_latest_model_path()
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model  = SignBiLSTM(
        input_size=141,
        hidden_size=128,
        num_layers=2,
        num_classes=len(label_map),
        dropout=0.3,
    ).to(device)
    model.load_state_dict(torch.load(MODEL_PATH, map_location=device, weights_only=True))
    model.eval()
    print(f"모델 로드 완료: {MODEL_PATH}  |  device: {device}")

    # 슬라이딩 윈도우 + 투표 큐
    sequence    = deque(maxlen=SEQ_LEN)
    vote_queue  = deque(maxlen=VOTE_WINDOW)

    cap = cv2.VideoCapture(0)
    cap.set(cv2.CAP_PROP_FRAME_WIDTH,  1280)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)

    pred_label = "collecting..."
    pred_conf  = 0.0
    frame_idx  = 0

    with mp_holistic.Holistic(
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5,
    ) as holistic:
        while True:
            ret, frame = cap.read()
            if not ret:
                continue

            frame = cv2.flip(frame, 1)
            rgb   = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            results = holistic.process(rgb)

            # 키포인트 추출 → 정규화 → 큐에 추가
            kp = extract_keypoints(results)
            kp = normalize(kp, scaler_mean, scaler_scale)
            sequence.append(kp)
            frame_idx += 1

            # 뼈대 그리기
            mp_drawing.draw_landmarks(frame, results.left_hand_landmarks,  mp_holistic.HAND_CONNECTIONS)
            mp_drawing.draw_landmarks(frame, results.right_hand_landmarks, mp_holistic.HAND_CONNECTIONS)
            mp_drawing.draw_landmarks(frame, results.pose_landmarks,       mp_holistic.POSE_CONNECTIONS)

            # 시퀀스가 가득 찼을 때만 3프레임 간격으로 추론 수행 (Lag 방지)
            if len(sequence) == SEQ_LEN and frame_idx % 3 == 0:
                inp = torch.tensor(
                    np.expand_dims(np.array(sequence, dtype=np.float32), 0),
                    dtype=torch.float32
                ).to(device)

                with torch.no_grad():
                    probs    = F.softmax(model(inp), dim=1).cpu().numpy()[0]
                    pred_idx = int(np.argmax(probs))
                    pred_conf = float(probs[pred_idx])

                # 투표 큐에 추가
                vote_queue.append(pred_idx)

                # 다수결 투표
                vote_counts = {}
                for v in vote_queue:
                    vote_counts[v] = vote_counts.get(v, 0) + 1
                voted_idx = max(vote_counts, key=vote_counts.get)
                voted_conf = probs[voted_idx]

                if voted_conf >= CONF_THRESHOLD:
                    pred_label = idx_to_label.get(voted_idx, "?")
                    pred_conf  = voted_conf

                    # NONE 감지 → 투표큐 리셋 및 슬라이딩 윈도우 절반 유지 (블라인드 스팟 방지)
                    if pred_label == "none" and voted_conf >= NONE_RESET_THRESHOLD:
                        while len(sequence) > 30:
                            sequence.popleft()
                        vote_queue.clear()
                        pred_label = "..."
                else:
                    pred_label = "uncertain"
                    pred_conf  = voted_conf
            else:
                vote_counts = {}

            draw_overlay(frame, pred_label, pred_conf,
                         len(sequence), vote_counts, idx_to_label)

            cv2.imshow("KSL Realtime Inference", frame)
            if cv2.waitKey(1) & 0xFF in (ord("q"), ord("Q"), 27):
                break

    cap.release()
    cv2.destroyAllWindows()


if __name__ == "__main__":
    main()
