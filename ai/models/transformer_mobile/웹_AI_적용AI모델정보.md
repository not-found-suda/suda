# 웹 AI 적용 AI 모델 정보

## 1. 문서 목적

이 문서는 웹 서비스 담당자가 최종 수어 인식 AI 모델을 서비스 화면/API와 연결할 때 참고할 수 있도록 정리한 자료다. 모바일 온디바이스 구현 문서와 달리, 웹 담당자는 주로 다음 항목을 이해하면 된다.

```text
1. 모델이 어떤 단어를 출력하는지
2. 출력 index와 라벨을 어떻게 매핑하는지
3. 모바일/서버에서 받은 예측 결과를 웹 화면에 어떻게 표시하는지
4. 문장 조합과 평서문/의문문 결과를 어떻게 다루는지
5. 모델 파일과 관련 메타데이터를 어떻게 관리하는지
```

현재 최종 모델은 실시간 데모 안정성을 위해 7개 단어만 인식한다.

## 2. 최종 모델 파일 구성

현재 폴더:

```text
Transformer_v5_Impl/models/BEST_7words_model/
```

주요 파일:

| 파일 | 용도 |
| --- | --- |
| `best_sign_model_v5_1_float16.tflite` | 모바일 온디바이스 우선 적용 모델 |
| `best_sign_model_v5_1_float32.tflite` | 정확도 비교용 TFLite 모델 |
| `best_sign_model_v5_1.pt` | PyTorch 원본 모델 |
| `best_sign_model_v5_1.onnx` | 변환 중간 산출물 |
| `label_map_v5_1.json` | 모델 출력 index와 단어 라벨 매핑 |
| `train_config_v5_1.json` | 학습 설정 및 데이터 구성 기록 |
| `inference_realtime_tflite_v5.py` | PC에서 TFLite 모델을 웹캠으로 테스트한 참고 코드 |
| `inference_realtime_transformer_v5.py` | PC에서 PyTorch 모델을 웹캠으로 테스트한 참고 코드 |

웹 서비스에서 직접 모델을 실행하지 않고 모바일 앱에서 예측 결과만 전달받는 구조라면, 웹 쪽에는 `label_map_v5_1.json`과 이 문서가 특히 중요하다.

## 3. 모델이 인식하는 단어

최종 모델의 출력 클래스는 7개다.

| index | label |
| ---: | --- |
| 0 | 기차 |
| 1 | 장난감 |
| 2 | 놀다 |
| 3 | 가다 |
| 4 | 일어나다 |
| 5 | 병원 |
| 6 | 조심 |

제외된 단어:

```text
오다
자다
아프다
```

제외 이유:

```text
오다, 자다: 가다와 동작 경계가 불안정했음
아프다: holdout 테스트에서 가다로 오인식되는 문제가 있었음
```

데모 안정성을 위해 위 3개 단어는 최종 모델에서 제외했다.

## 4. 만들 수 있는 문장 예시

아래 문장들은 문장 단위로 학습된 것이 아니라, 인식된 단어 라벨을 웹 서비스에서 조합해 보여줄 수 있는 예시다.

| 번호 | 문장 예시 | 사용 단어 |
| ---: | --- | --- |
| 1 | 기차가 가다 | 기차, 가다 |
| 2 | 병원에 가다 | 병원, 가다 |
| 3 | 장난감으로 놀다 | 장난감, 놀다 |
| 4 | 장난감을 조심하다 | 장난감, 조심 |
| 5 | 기차를 조심하다 | 기차, 조심 |
| 6 | 병원에서 일어나다 | 병원, 일어나다 |
| 7 | 놀다가 일어나다 | 놀다, 일어나다 |
| 8 | 기차 타러 가다 | 기차, 가다 |
| 9 | 병원에 가서 조심하다 | 병원, 가다, 조심 |
| 10 | 장난감 가지고 놀다 | 장난감, 놀다 |

웹에서는 우선 단어 시퀀스를 그대로 보여주고, 필요하면 간단한 규칙으로 자연어 문장처럼 표시할 수 있다.

예:

```text
recognizedWords = ["병원", "가다"]
displaySentence = "병원에 가다"
```

## 5. 웹 서비스 연동 구조 권장안

현재 프로젝트 구조에서는 모델 추론은 모바일 온디바이스에서 수행하고, 웹은 결과를 받아 표시하는 구조가 가장 자연스럽다.

권장 구조:

```text
Android app
  - Camera + MediaPipe Holistic Landmarker
  - TFLite 모델 추론
  - 단어 라벨, confidence, sentenceType 생성

Backend/WebSocket/API
  - 모바일에서 예측 결과 수신
  - 세션별 단어 시퀀스 저장
  - 필요 시 문장 조합/로그 저장

Web frontend
  - 현재 인식 단어 표시
  - 누적 단어/문장 표시
  - confidence 또는 상태 UI 표시
  - 평서문/의문문 표시
```

웹이 모델을 직접 실행하는 구조도 가능하지만, 현재 실시간 landmark 추출과 카메라 접근은 모바일 쪽이 더 적합하다. 따라서 웹은 AI 추론 결과 소비자 역할로 두는 것을 권장한다.

## 6. 모바일 → 웹 전달 데이터 예시

모바일에서 한 단어가 안정적으로 확정되었을 때 웹 또는 서버로 다음과 같은 JSON을 보낼 수 있다.

```json
{
  "type": "sign_prediction",
  "sessionId": "demo-session-001",
  "timestamp": 1778115000000,
  "label": "가다",
  "labelIndex": 3,
  "confidence": 0.982,
  "margin": 0.914,
  "sentenceType": "평서문",
  "eyebrowDelta": -0.012,
  "model": {
    "name": "BEST_7words_model",
    "format": "tflite",
    "precision": "float16",
    "version": "v5_7words_demo_stable"
  }
}
```

필드 설명:

| 필드 | 설명 |
| --- | --- |
| `type` | 메시지 타입 |
| `sessionId` | 데모 또는 사용자 세션 ID |
| `timestamp` | 예측 발생 시각 |
| `label` | 예측 단어 |
| `labelIndex` | 모델 출력 index |
| `confidence` | top1 확률 |
| `margin` | top1 - top2 확률 차이 |
| `sentenceType` | `평서문` 또는 `의문문` |
| `eyebrowDelta` | 눈썹 기준 변화량 |
| `model` | 사용 모델 메타 정보 |

## 7. 웹 화면 표시 권장 방식

실시간 추론은 순간적으로 다른 단어가 잠깐 튈 수 있으므로, 웹 화면에서는 모바일에서 확정된 단어만 표시하는 것이 좋다.

권장 UI 상태:

```text
대기 중: 손이 감지되지 않음
인식 중: 손이 감지되고 frame window 누적 중
확정: smoothing 조건을 통과한 단어 표시
불확실: confidence 또는 margin이 낮음
```

표시 예시:

```text
현재 단어: 가다
신뢰도: 98.2%
문장 유형: 평서문
누적 문장: 병원 가다
```

웹에서 매 프레임의 top-k를 모두 보여주면 화면이 불안정해 보일 수 있다. 데모 화면에서는 안정적으로 확정된 단어만 크게 보여주고, confidence는 보조 정보로 작게 표시하는 것을 권장한다.

## 8. 단어 누적 및 중복 제거 규칙

모바일에서 같은 단어가 연속으로 여러 번 전송될 수 있으므로, 웹 또는 서버에서 간단한 중복 제거를 적용하면 좋다.

권장 규칙:

```text
1. 직전 단어와 같은 단어가 1~2초 안에 다시 들어오면 중복으로 처리
2. 손이 화면에서 사라졌다가 다시 나타난 뒤 같은 단어가 들어오면 새 입력으로 처리 가능
3. 사용자가 "초기화" 버튼을 누르면 누적 단어 시퀀스 삭제
```

예시:

```text
input events:
  가다, 가다, 가다, 병원, 병원

display sequence:
  가다, 병원
```

문장 표시용으로는 단어 시퀀스를 그대로 저장해두고, 화면에는 변환된 문장을 보여줄 수 있다.

## 9. 평서문/의문문 처리

현재 평서문/의문문은 단어 분류 모델이 아니라 MediaPipe face landmark 기반 rule로 판별한다.

모바일에서 다음 값을 함께 보내주는 것을 권장한다.

```text
sentenceType: "평서문" 또는 "의문문"
eyebrowDelta: 현재 눈썹 gap - 중립 눈썹 gap
```

웹에서는 이 값을 그대로 표시하면 된다.

예:

```text
문장 유형: 의문문
화면 표시: "병원 가다?"
```

현재 기준:

```text
question eyebrow delta threshold = 0.055
minimum neutral face samples = 15
neutral face window = 45
```

웹 담당자는 eyebrow landmark를 직접 계산할 필요는 없다. 모바일에서 계산된 결과를 받아 표시하는 것을 권장한다.

## 10. 모델 학습 요약

모델 구조:

```text
Transformer Encoder 기반 분류 모델
input_dim = 332
sequence_length = 30
d_model = 128
num_heads = 8
num_layers = 3
output classes = 7
```

학습 데이터:

```text
AI Hub 기반 수어 데이터
팀원 추가 촬영 데이터
MediaPipe Holistic 기반 332차원 npy feature
```

학습 구성:

```text
기차, 장난감, 놀다, 가다, 일어나다, 병원, 조심
가다: 가다4 variant만 사용
일어나다: 일어나다2 variant만 사용
오다, 자다, 아프다 제외
```

사용한 주요 기법:

```text
1. 어깨 기준 좌표 정규화
2. 30프레임 고정 길이 입력
3. stratified train/validation split
4. 부족한 train class에 virtual augmentation 적용
5. holdout_test_v5 별도 평가
```

augmentation:

```text
Gaussian noise std = 0.01
temporal scale = 0.9 ~ 1.1
temporal shift = -2 ~ +2 frames
```

좌우 flip은 사용하지 않았다. 수어에서 좌우 방향이 의미와 동작 경계에 영향을 줄 수 있기 때문이다.

## 11. 최종 평가 결과

학습 결과:

```text
Best F1: 1.0000
Val Acc@BestF1: 100.00%
Best Train Acc: 100.00%
```

Holdout 테스트:

```text
평가 파일 수: 15
정확도: 100.00% (15/15)
가다: 5/5
일어나다: 5/5
조심: 5/5
Wrong Predictions: none
```

주의: holdout에는 `기차`, `장난감`, `놀다`, `병원`이 포함되지 않았다. 이 단어들은 기존 실시간 테스트에서 안정적으로 동작했고, holdout은 주로 문제가 있었던 핵심 단어 축인 `가다`, `일어나다`, `조심` 검증용으로 사용했다.

## 12. 웹 담당자가 알아야 할 입력/출력 한계

현재 모델은 단어 단위 분류 모델이다. 즉 모델이 직접 완전한 문장을 생성하지 않는다.

모델이 하는 일:

```text
30프레임 수어 landmark sequence → 7개 단어 중 하나의 확률 분포 출력
```

모델이 하지 않는 일:

```text
문장 생성
문법 보정
조사 자동 삽입
사용자 의도 해석
```

따라서 웹에서 자연스러운 문장으로 보여주려면 간단한 규칙 또는 매핑 테이블이 필요하다.

예:

```json
{
  "병원,가다": "병원에 가다",
  "장난감,놀다": "장난감으로 놀다",
  "기차,조심": "기차를 조심하다"
}
```

데모에서는 복잡한 문장 생성보다, 인식된 단어 시퀀스를 명확하게 보여주는 것이 안정적이다.

## 13. API 설계 예시

REST 방식 예시:

```http
POST /api/sign/predictions
Content-Type: application/json
```

Request:

```json
{
  "sessionId": "demo-session-001",
  "label": "조심",
  "labelIndex": 6,
  "confidence": 0.991,
  "margin": 0.954,
  "sentenceType": "평서문",
  "timestamp": 1778115000000
}
```

Response:

```json
{
  "ok": true,
  "recognizedWords": ["병원", "가다", "조심"],
  "displaySentence": "병원에 가서 조심하다",
  "sentenceType": "평서문"
}
```

WebSocket 방식 예시:

```json
{
  "event": "SIGN_PREDICTED",
  "payload": {
    "label": "기차",
    "labelIndex": 0,
    "confidence": 0.987,
    "sentenceType": "평서문"
  }
}
```

실시간성이 중요하면 WebSocket 또는 SSE를 권장한다. 단순 데모라면 REST polling 또는 모바일에서 결과를 서버에 저장하고 웹이 조회하는 구조도 가능하다.

## 14. 서비스 화면 구성 제안

데모 화면 구성:

```text
1. 현재 인식 단어
2. 누적 단어 시퀀스
3. 자연어 변환 문장
4. 문장 유형: 평서문/의문문
5. confidence 표시
6. 모델 상태: 인식 중/대기 중
7. 초기화 버튼
```

예시:

```text
현재 인식: 조심
누적 단어: 병원 > 가다 > 조심
문장: 병원에 가서 조심하다
문장 유형: 평서문
신뢰도: 99.1%
```

데모 안정성을 위해 화면에는 확정된 단어만 표시하고, 낮은 confidence의 후보 단어는 표시하지 않는 것을 권장한다.

## 15. 전달 파일

웹 담당자에게 전달할 파일:

```text
웹_AI_적용AI모델정보.md
label_map_v5_1.json
train_config_v5_1.json
```

웹이 직접 모델 추론을 하지 않는다면 TFLite 모델 파일은 필수는 아니다. 다만 전체 산출물 보관 및 확인을 위해 아래 파일도 함께 공유할 수 있다.

```text
best_sign_model_v5_1_float16.tflite
best_sign_model_v5_1_float32.tflite
inference_realtime_tflite_v5.py
```

웹에서 모델을 직접 실행하는 경우에는 브라우저 TensorFlow.js 변환이나 서버 Python 추론 API가 추가로 필요하다. 현재 권장 구조는 모바일 온디바이스 추론 후 결과를 웹으로 전달하는 방식이다.
