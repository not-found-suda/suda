import os
import cv2
import boto3
import numpy as np
import mediapipe as mp
from dotenv import load_dotenv
import concurrent.futures

# MediaPipe Holistic 초기화 (포즈 + 양손)
mp_holistic = mp.solutions.holistic

# 입술 윤곽선(Outer+Inner) 랜드마크 인덱스 (총 40개)
MOUTH_INDICES = [
    61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 185, 40, 39, 37, 0, 267, 269, 270, 409, # Outer
    78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308, 191, 80, 81, 82, 13, 312, 311, 310, 415  # Inner
]

def extract_landmarks_from_video(video_path):
    """
    비디오에서 프레임별로 MediaPipe 랜드마크를 추출하여 NumPy 배열로 반환합니다.
    추출 범위 (총 115개 랜드마크):
    - Pose: 33개
    - Left Hand: 21개
    - Right Hand: 21개
    - Mouth: 40개 (얼굴 표정 중 입모양만 핀셋 추출)
    결과 차원: (프레임수, 115 * 3) = (프레임수, 345)
    """
    cap = cv2.VideoCapture(video_path)
    video_data = []
    
    # Forward Fill을 위한 이전 프레임 좌표 저장 변수 초기화
    last_pose = np.zeros(33 * 3)
    last_lh = np.zeros(21 * 3)
    last_rh = np.zeros(21 * 3)
    last_mouth = np.zeros(40 * 3)
    
    with mp_holistic.Holistic(min_detection_confidence=0.5, min_tracking_confidence=0.5) as holistic:
        while cap.isOpened():
            ret, frame = cap.read()
            if not ret:
                break
                
            # BGR을 RGB로 변환
            image = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            image.flags.writeable = False
            results = holistic.process(image)
            
            # 1. Pose (33개 랜드마크)
            if results.pose_landmarks:
                pose = np.array([[res.x, res.y, res.z] for res in results.pose_landmarks.landmark]).flatten()
                last_pose = pose
            else:
                pose = last_pose  # Forward Fill (이전 값 유지)
                
            # 2. Left Hand (21개 랜드마크)
            if results.left_hand_landmarks:
                lh = np.array([[res.x, res.y, res.z] for res in results.left_hand_landmarks.landmark]).flatten()
                last_lh = lh
            else:
                lh = last_lh  # Forward Fill
                
            # 3. Right Hand (21개 랜드마크)
            if results.right_hand_landmarks:
                rh = np.array([[res.x, res.y, res.z] for res in results.right_hand_landmarks.landmark]).flatten()
                last_rh = rh
            else:
                rh = last_rh  # Forward Fill
                
            # 4. Mouth / Lips (40개 랜드마크 - Face Mesh에서 핀셋 추출)
            if results.face_landmarks:
                mouth = np.array([[results.face_landmarks.landmark[i].x, 
                                   results.face_landmarks.landmark[i].y, 
                                   results.face_landmarks.landmark[i].z] for i in MOUTH_INDICES]).flatten()
                last_mouth = mouth
            else:
                mouth = last_mouth  # Forward Fill
                
            # 하나의 프레임 데이터로 결합 (33*3 + 21*3 + 21*3 + 40*3 = 345 차원)
            frame_data = np.concatenate([pose, lh, rh, mouth])
            video_data.append(frame_data)
            
    cap.release()
    return np.array(video_data) # Shape: (frames, 345)

def process_single_video(s3_key):
    """
    각 프로세스(워커)가 독립적으로 실행할 단일 영상 처리 로직
    """
    # 워커 내부에서 S3 클라이언트를 독립적으로 생성 (멀티프로세싱 충돌 방지)
    load_dotenv()
    AWS_ACCESS_KEY = os.getenv('AWS_ACCESS_KEY')
    AWS_SECRET_KEY = os.getenv('AWS_SECRET_KEY')
    BUCKET_NAME = os.getenv('S3_BUCKET_NAME')
    
    s3_client = boto3.client(
        's3',
        aws_access_key_id=AWS_ACCESS_KEY,
        aws_secret_access_key=AWS_SECRET_KEY
    )
    
    npy_s3_key = s3_key.replace('.mp4', '.npy')
    
    # 1. 이미 .npy 파일이 있는지 확인 (이어하기 기능)
    try:
        s3_client.head_object(Bucket=BUCKET_NAME, Key=npy_s3_key)
        return f"⏩ 이미 처리된 파일입니다 (스킵): {npy_s3_key}"
    except:
        pass # 파일이 없으므로 처리 진행
    
    # 병렬 처리 중 로컬 파일 이름이 겹치지 않도록 고유한 이름 사용
    local_id = os.path.basename(s3_key).replace('.mp4', '')
    local_mp4 = f"temp_video_{local_id}.mp4"
    local_npy = f"temp_data_{local_id}.npy"
    
    try:
        # 2. 다운로드
        s3_client.download_file(BUCKET_NAME, s3_key, local_mp4)
        
        # 3. MediaPipe 추출
        npy_data = extract_landmarks_from_video(local_mp4)
        
        # 4. 저장 및 업로드
        np.save(local_npy, npy_data)
        s3_client.upload_file(local_npy, BUCKET_NAME, npy_s3_key)
        
        return f"✅ 변환 완료: {npy_s3_key}"
    except Exception as e:
        return f"❌ 에러 발생 ({s3_key}): {e}"
    finally:
        # 5. 로컬 찌꺼기 삭제
        if os.path.exists(local_mp4):
            os.remove(local_mp4)
        if os.path.exists(local_npy):
            os.remove(local_npy)

def process_s3_videos_multiprocess():
    print("🚀 S3 원본 영상(.mp4) -> MediaPipe 좌표(.npy) [멀티프로세싱 병렬 변환] 작업을 시작합니다...")
    
    # 1. S3 환경변수 로드
    load_dotenv()
    AWS_ACCESS_KEY = os.getenv('AWS_ACCESS_KEY')
    AWS_SECRET_KEY = os.getenv('AWS_SECRET_KEY')
    BUCKET_NAME = os.getenv('S3_BUCKET_NAME')

    s3_client = boto3.client(
        's3',
        aws_access_key_id=AWS_ACCESS_KEY,
        aws_secret_access_key=AWS_SECRET_KEY
    )
    
    # 2. S3에서 모든 .mp4 파일 목록 수집
    print("📂 S3에서 대상 영상 목록을 불러오는 중...")
    paginator = s3_client.get_paginator('list_objects_v2')
    pages = paginator.paginate(Bucket=BUCKET_NAME, Prefix='raw_videos/')
    
    target_keys = []
    for page in pages:
        if 'Contents' in page:
            for obj in page['Contents']:
                if obj['Key'].endswith('.mp4'):
                    target_keys.append(obj['Key'])
                    
    print(f"총 {len(target_keys)}개의 영상이 대기열에 등록되었습니다.")
    
    # 3. 멀티프로세싱 풀(Pool) 가동 (CPU 코어 최대치 활용)
    # V100 서버의 코어 수에 맞게 워커 수를 늘려줍니다. (여기서는 최대 16개 동시 작업)
    MAX_WORKERS = 16
    
    with concurrent.futures.ProcessPoolExecutor(max_workers=MAX_WORKERS) as executor:
        # 여러 개의 프로세스가 영상들을 나누어서 동시에 처리합니다.
        futures = {executor.submit(process_single_video, key): key for key in target_keys}
        
        for future in concurrent.futures.as_completed(futures):
            # 완료되는 순서대로 결과 출력
            print(future.result())

if __name__ == "__main__":
    process_s3_videos_multiprocess()
