import cv2
import torch
import json
import numpy as np
import mediapipe as mp
from collections import deque
from model import KSLTransformer

# 모바일-AI 계약 스펙과 동일한 환경 세팅
MAX_LEN = 30
CONFIDENCE_THRESHOLD = 0.85

# 입술 랜드마크 인덱스 (40개)
MOUTH_INDICES = [
    61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 185, 40, 39, 37, 0, 267, 269, 270, 409, 
    78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308, 191, 80, 81, 82, 13, 312, 311, 310, 415
]

def load_label_map(path='models/label_map.json'):
    with open(path, 'r', encoding='utf-8') as f:
        # JSON 키는 무조건 문자열이므로 정수형으로 변환
        label_map = {int(k): v for k, v in json.load(f).items()}
    return label_map

def normalize_frame(frame_data):
    """
    모바일 전처리 스펙(2-1)과 완벽하게 동일한 정규화 함수
    frame_data: (345,) 1차원 배열
    """
    pts = frame_data.reshape(-1, 3)
    l_shoulder, r_shoulder = pts[11], pts[12]
    
    shoulder_center = (l_shoulder + r_shoulder) / 2.0
    pts_translated = pts - shoulder_center
    
    shoulder_width = np.linalg.norm(l_shoulder - r_shoulder)
    if shoulder_width < 1e-6:
        shoulder_width = 1.0
        
    pts_scaled = pts_translated / shoulder_width
    return pts_scaled.flatten()

def main():
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    
    # 1. 라벨 매핑 및 모델 로드
    try:
        label_map = load_label_map()
        num_classes = len(label_map)
    except FileNotFoundError:
        print("❌ label_map.json 파일이 없습니다. 먼저 train.py를 실행하세요.")
        return

    model = KSLTransformer(input_dim=345, num_classes=num_classes, d_model=128, num_heads=4, num_layers=2)
    try:
        model.load_state_dict(torch.load('models/best_sign_model.pt', map_location=device))
        model.to(device)
        model.eval()
        print("✅ 모델 로드 성공!")
    except FileNotFoundError:
        print("❌ models/best_sign_model.pt 파일이 없습니다. 먼저 모델을 학습시키세요.")
        return

    # 2. MediaPipe 초기화
    mp_holistic = mp.solutions.holistic
    cap = cv2.VideoCapture(0) # 0번 웹캠 켜기
    
    # 프레임 큐 보관용 (길이 30 고정)
    frame_queue = deque(maxlen=MAX_LEN)
    prediction_history = deque(maxlen=5) # Smoothing / Voting 용 (최근 5번)
    
    # Forward Fill 용도
    last_pose = np.zeros(33 * 3)
    last_lh = np.zeros(21 * 3)
    last_rh = np.zeros(21 * 3)
    last_mouth = np.zeros(40 * 3)

    current_action = "대기중..."
    
    with mp_holistic.Holistic(min_detection_confidence=0.5, min_tracking_confidence=0.5) as holistic:
        while cap.isOpened():
            ret, frame = cap.read()
            if not ret:
                break
                
            # 좌우 반전 (거울 모드)
            frame = cv2.flip(frame, 1)
            image = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            image.flags.writeable = False
            results = holistic.process(image)
            image.flags.writeable = True
            
            # 3. 랜드마크 추출 및 Forward Fill
            if results.pose_landmarks:
                pose = np.array([[res.x, res.y, res.z] for res in results.pose_landmarks.landmark]).flatten()
                last_pose = pose
            else:
                pose = last_pose
                
            if results.left_hand_landmarks:
                lh = np.array([[res.x, res.y, res.z] for res in results.left_hand_landmarks.landmark]).flatten()
                last_lh = lh
            else:
                lh = last_lh
                
            if results.right_hand_landmarks:
                rh = np.array([[res.x, res.y, res.z] for res in results.right_hand_landmarks.landmark]).flatten()
                last_rh = rh
            else:
                rh = last_rh
                
            if results.face_landmarks:
                mouth = np.array([[results.face_landmarks.landmark[i].x, 
                                   results.face_landmarks.landmark[i].y, 
                                   results.face_landmarks.landmark[i].z] for i in MOUTH_INDICES]).flatten()
                last_mouth = mouth
            else:
                mouth = last_mouth
                
            raw_frame_data = np.concatenate([pose, lh, rh, mouth])
            
            # 4. 정규화 및 큐에 추가
            norm_frame_data = normalize_frame(raw_frame_data)
            frame_queue.append(norm_frame_data)
            
            # 5. 큐가 30프레임 꽉 찼을 때 추론 시작
            if len(frame_queue) == MAX_LEN:
                # Numpy -> Tensor 변환 (1, 30, 345)
                input_seq = np.array(frame_queue)
                input_tensor = torch.FloatTensor(input_seq).unsqueeze(0).to(device)
                
                with torch.no_grad():
                    logits = model(input_tensor)
                    probabilities = torch.nn.functional.softmax(logits, dim=1)
                    
                    # Top-1 확률 추출
                    conf, predicted_idx = torch.max(probabilities, 1)
                    conf = conf.item()
                    predicted_idx = predicted_idx.item()
                    predicted_word = label_map[predicted_idx]
                    
                    # Confidence Threshold 통과 여부 확인
                    if conf >= CONFIDENCE_THRESHOLD:
                        prediction_history.append(predicted_word)
                    else:
                        prediction_history.append("none")
                        
                    # Voting 로직 (최근 5번 중 가장 많이 나온 단어가 3번 이상일 때만 표출)
                    if len(prediction_history) == 5:
                        most_common_word = max(set(prediction_history), key=prediction_history.count)
                        if prediction_history.count(most_common_word) >= 3 and most_common_word != "none":
                            current_action = f"{most_common_word} ({conf*100:.1f}%)"
                        else:
                            current_action = "대기중..."
            
            # 6. 화면 출력 (한국어 출력은 cv2에서 깨지므로 영어/숫자 기반 렌더링 권장, 여기선 테스트용)
            # 파이썬 OpenCV는 기본 한글 출력이 깨지므로, 실제 환경에서는 PIL ImageFont 등을 사용해야 합니다.
            # 여기서는 편의상 영문 폰트를 사용하되, 터미널 로그로 한글 정답을 찍어줍니다.
            cv2.putText(frame, f"Action: {current_action}", (10, 40), 
                        cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2, cv2.LINE_AA)
            print(f"현재 동작: {current_action}")
            
            cv2.imshow('Sign Language Real-time Inference', frame)
            
            if cv2.waitKey(10) & 0xFF == ord('q'):
                break
                
    cap.release()
    cv2.destroyAllWindows()

if __name__ == '__main__':
    main()
