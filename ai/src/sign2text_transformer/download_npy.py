import os
import boto3
import concurrent.futures
from dotenv import load_dotenv

load_dotenv()
AWS_ACCESS_KEY = os.getenv('AWS_ACCESS_KEY')
AWS_SECRET_KEY = os.getenv('AWS_SECRET_KEY')
BUCKET_NAME = os.getenv('S3_BUCKET_NAME')
TARGET_DIR = './s3_downloaded_data'

def download_single_file(s3_key):
    s3_client = boto3.client(
        's3',
        aws_access_key_id=AWS_ACCESS_KEY,
        aws_secret_access_key=AWS_SECRET_KEY
    )
    
    # s3_key 예시: 'raw_videos/가족/영상1.npy'
    # 로컬 경로 생성: './s3_downloaded_data/가족/영상1.npy'
    relative_path = s3_key.replace('raw_videos/', '')
    local_path = os.path.join(TARGET_DIR, relative_path)
    
    os.makedirs(os.path.dirname(local_path), exist_ok=True)
    
    if not os.path.exists(local_path):
        s3_client.download_file(BUCKET_NAME, s3_key, local_path)
        return f"📥 다운로드 완료: {local_path}"
    return f"⏩ 이미 존재함 (스킵): {local_path}"

def download_all_npy():
    s3_client = boto3.client(
        's3',
        aws_access_key_id=AWS_ACCESS_KEY,
        aws_secret_access_key=AWS_SECRET_KEY
    )
    
    print("🔍 S3에서 .npy 파일 목록을 가져오는 중...")
    paginator = s3_client.get_paginator('list_objects_v2')
    pages = paginator.paginate(Bucket=BUCKET_NAME, Prefix='raw_videos/')
    
    npy_keys = []
    for page in pages:
        if 'Contents' in page:
            for obj in page['Contents']:
                if obj['Key'].endswith('.npy'):
                    npy_keys.append(obj['Key'])
                    
    print(f"총 {len(npy_keys)}개의 .npy 파일을 다운로드합니다...")
    
    # 병렬 다운로드 (빠르게 처리)
    with concurrent.futures.ThreadPoolExecutor(max_workers=16) as executor:
        for result in executor.map(download_single_file, npy_keys):
            pass # 진행 상황을 보려면 print(result) 주석 해제
            
    print("✅ 모든 .npy 파일 다운로드 완료! 이제 train.py를 실행하세요.")

if __name__ == "__main__":
    download_all_npy()
