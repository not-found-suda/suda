import os
import json
import torch
import torch.nn as nn
import torch.optim as optim
from torch.optim.lr_scheduler import ReduceLROnPlateau
from dataset import create_dataloaders
from model import KSLTransformer

# 하이퍼파라미터 설정
DATA_DIR = './s3_downloaded_data' # S3에서 다운받은 .npy 파일들이 있는 최상위 폴더 (단어별 하위 폴더 존재)
MAX_CLASSES = 30 # 일단 모바일 테스트용으로 30개 단어만 먼저 학습 (전체 학습 시 None으로 변경)
BATCH_SIZE = 64
MAX_LEN = 30
EPOCHS = 200 # Early Stopping을 믿고 넉넉하게 200으로 상향
PATIENCE = 15 # 15번 연속 성능 향상이 없으면 조기 종료
LEARNING_RATE = 1e-3
DEVICE = torch.device('cuda' if torch.cuda.is_available() else 'cpu')

def train_model():
    print(f"🚀 학습을 시작합니다. 사용 장치: {DEVICE}")
    
    # 1. 데이터 로더 및 라벨 매핑 생성
    train_loader, val_loader, class_to_idx = create_dataloaders(DATA_DIR, batch_size=BATCH_SIZE, max_len=MAX_LEN, max_classes=MAX_CLASSES)
    num_classes = len(class_to_idx)
    print(f"✅ 총 {num_classes}개의 단어 클래스를 로드했습니다.")
    
    # 모바일 팀 전달용 label_map.json 저장 (인덱스 -> 단어)
    idx_to_class = {v: k for k, v in class_to_idx.items()}
    with open('label_map.json', 'w', encoding='utf-8') as f:
        json.dump(idx_to_class, f, ensure_ascii=False, indent=4)
    print("✅ label_map.json 파일이 생성되었습니다.")
    
    # 2. 모델 초기화
    # 모바일용 초경량 세팅 (d_model=128, num_layers=2)
    model = KSLTransformer(input_dim=345, num_classes=num_classes, d_model=128, num_heads=4, num_layers=2).to(DEVICE)
    
    # 3. 손실 함수 및 최적화 기법 설정
    criterion = nn.CrossEntropyLoss()
    optimizer = optim.AdamW(model.parameters(), lr=LEARNING_RATE, weight_decay=1e-4)
    # Validation Accuracy가 정체되면 학습률을 절반(0.5)으로 깎아버리는 똑똑한 스케줄러로 변경
    scheduler = ReduceLROnPlateau(optimizer, mode='max', factor=0.5, patience=5)
    
    best_val_acc = 0.0
    patience_counter = 0 # 조기 종료 카운터
    
    # 4. 학습 루프
    for epoch in range(EPOCHS):
        model.train()
        train_loss = 0.0
        train_correct = 0
        train_total = 0
        
        for inputs, labels in train_loader:
            inputs, labels = inputs.to(DEVICE), labels.to(DEVICE)
            
            optimizer.zero_grad()
            outputs = model(inputs) # Shape: (batch_size, num_classes)
            
            loss = criterion(outputs, labels)
            loss.backward()
            optimizer.step()
            
            train_loss += loss.item() * inputs.size(0)
            _, predicted = torch.max(outputs, 1)
            train_total += labels.size(0)
            train_correct += (predicted == labels).sum().item()
            
        train_acc = 100 * train_correct / train_total
        train_loss = train_loss / train_total
        
        # 5. 검증 루프
        model.eval()
        val_loss = 0.0
        val_correct = 0
        val_total = 0
        
        with torch.no_grad():
            for inputs, labels in val_loader:
                inputs, labels = inputs.to(DEVICE), labels.to(DEVICE)
                
                outputs = model(inputs)
                loss = criterion(outputs, labels)
                
                val_loss += loss.item() * inputs.size(0)
                _, predicted = torch.max(outputs, 1)
                val_total += labels.size(0)
                val_correct += (predicted == labels).sum().item()
                
        val_acc = 100 * val_correct / val_total
        val_loss = val_loss / val_total
        
        # Learning Rate 스케줄러 업데이트 (Val Acc 기준)
        scheduler.step(val_acc)
        
        print(f"Epoch [{epoch+1}/{EPOCHS}] "
              f"Train Loss: {train_loss:.4f}, Train Acc: {train_acc:.2f}% | "
              f"Val Loss: {val_loss:.4f}, Val Acc: {val_acc:.2f}%")
        
        # 6. 최고 성능 모델 저장 (.pt) 및 Early Stopping 검사
        if val_acc > best_val_acc:
            best_val_acc = val_acc
            torch.save(model.state_dict(), 'best_sign_model.pt')
            print(f"🔥 Best Model Saved! (Val Acc: {best_val_acc:.2f}%)")
            patience_counter = 0 # 신기록 달성 시 카운터 초기화
        else:
            patience_counter += 1
            print(f"⚠️ 모델 성능 향상 없음 ({patience_counter}/{PATIENCE})")
            
        if patience_counter >= PATIENCE:
            print(f"\n🛑 Early Stopping 발동! {PATIENCE}번 연속 성능 향상이 없어 {epoch+1} 에포크에서 학습을 조기 종료합니다.")
            break

if __name__ == "__main__":
    # 데이터 폴더가 존재하는지 확인 후 실행
    if os.path.exists(DATA_DIR):
        train_model()
    else:
        print(f"❌ 데이터 폴더를 찾을 수 없습니다: {DATA_DIR}")
        print("학습을 시작하려면 S3에서 다운받은 .npy 파일들이 있는 폴더 경로를 지정해주세요.")
