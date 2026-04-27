import torch
import torch.nn as nn
import math

class PositionalEncoding(nn.Module):
    def __init__(self, d_model, max_len=5000):
        super(PositionalEncoding, self).__init__()
        
        # 1. 시퀀스 내의 위치(순서)를 표현할 행렬 생성
        pe = torch.zeros(max_len, d_model)
        position = torch.arange(0, max_len, dtype=torch.float).unsqueeze(1)
        
        # 2. 주파수 스케일링을 위한 분모 계산
        div_term = torch.exp(torch.arange(0, d_model, 2).float() * (-math.log(10000.0) / d_model))
        
        # 3. 짝수 인덱스에는 Sin, 홀수 인덱스에는 Cos 함수 적용
        pe[:, 0::2] = torch.sin(position * div_term)
        pe[:, 1::2] = torch.cos(position * div_term)
        
        # pe shape: (1, max_len, d_model)
        pe = pe.unsqueeze(0)
        self.register_buffer('pe', pe)

    def forward(self, x):
        # x shape: (batch_size, seq_len, d_model)
        seq_len = x.size(1)
        # 입력 x에 Positional Encoding 값을 더해줌
        x = x + self.pe[:, :seq_len, :]
        return x

class KSLTransformer(nn.Module):
    def __init__(self, input_dim=345, num_classes=110, d_model=128, num_heads=4, num_layers=2, dim_feedforward=256, dropout=0.1):
        super(KSLTransformer, self).__init__()
        
        # 1. 입력 차원(345)을 모델 내부 차원(128)으로 축소/확장 (Linear Projection)
        self.input_projection = nn.Linear(input_dim, d_model)
        
        # 2. 시간에 대한 순서 정보를 부여하는 Positional Encoding
        self.pos_encoder = PositionalEncoding(d_model)
        
        # 3. Transformer Encoder 블록 생성
        # 모바일 환경을 고려하여 layer 수와 차원을 최대한 얇게 유지
        encoder_layer = nn.TransformerEncoderLayer(
            d_model=d_model, 
            nhead=num_heads, 
            dim_feedforward=dim_feedforward, 
            dropout=dropout,
            batch_first=True # (batch_size, seq_len, d_model) 형태 사용
        )
        self.transformer_encoder = nn.TransformerEncoder(encoder_layer, num_layers=num_layers)
        
        # 4. 분류기 (Classifier)
        # 30프레임의 시퀀스 정보를 하나로 압축한 뒤(GAP), 110개의 단어로 분류
        self.classifier = nn.Sequential(
            nn.Linear(d_model, d_model // 2),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(d_model // 2, num_classes)
        )

    def forward(self, x):
        # x shape: (batch_size, seq_len(30), input_dim(345))
        
        # Step 1. 임베딩 및 위치 정보 추가
        x = self.input_projection(x)  # (batch_size, 30, 128)
        x = self.pos_encoder(x)       # (batch_size, 30, 128)
        
        # Step 2. Transformer 인코더 통과
        # Self-Attention을 통해 각 프레임 간의 움직임 연관성을 파악
        encoded = self.transformer_encoder(x) # (batch_size, 30, 128)
        
        # Step 3. Global Average Pooling (시간 축(30 프레임)을 평균내어 하나의 벡터로 압축)
        # (batch_size, 30, 128) -> (batch_size, 128)
        pooled = encoded.mean(dim=1) 
        
        # Step 4. 최종 분류
        # (batch_size, 110)
        output = self.classifier(pooled)
        
        return output
