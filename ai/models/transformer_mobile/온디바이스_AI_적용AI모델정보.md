# 온디바이스 AI 적용 AI 모델 정보

## 1. 최종 데모 모델 요약

모바일 온디바이스 추론용 최종 모델은 수어 단어 7개를 분류하는 Transformer 기반 모델이다.

최종 인식 단어와 라벨 순서는 다음과 같다.

| index | label |
| ---: | --- |
| 0 | 기차 |
| 1 | 장난감 |
| 2 | 놀다 |
| 3 | 가다 |
| 4 | 일어나다 |
| 5 | 병원 |
| 6 | 조심 |

현재 7개 단어만으로 구성할 수 있는 문장 예시는 다음과 같다. 이 문장들은 모델 학습에 직접 사용된 문장 데이터가 아니라, 인식된 단어들을 조합해 데모에서 보여줄 수 있는 테스트 문장 예시다.

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

모바일에 전달할 주요 파일은 다음과 같다.

| 파일 | 용도 |
| --- | --- |
| `best_sign_model_v5_1_float16.tflite` | 모바일 권장 모델. float16 경량화 TFLite |
| `best_sign_model_v5_1_float32.tflite` | 정확도 비교용 float32 TFLite |
| `label_map_v5_1.json` | 모델 출력 index와 단어 라벨 매핑 |
| `train_config_v5_1.json` | 학습 설정 기록용 |

파일 크기:

| 모델 | 크기 |
| --- | ---: |
| float32 TFLite | 약 2.51 MB |
| float16 TFLite | 약 1.27 MB |

갤럭시 S26 Ultra에서는 `float16` 모델부터 테스트하고, 실제 프로젝트 시연 기기인 갤럭시 Flip3에서도 동일 모델을 우선 테스트한다. Flip3에서 발열, 지연, 프레임 드랍, 정확도 저하가 있으면 `float32`와 `float16`을 모두 비교한다.

## 2. 모델 입출력 형식

TFLite 모델 입력은 고정 크기 float32 텐서다.

```text
input shape: [1, 30, 332]
input dtype: float32
output shape: [1, 7]
output dtype: float32
```

입력 의미:

```text
1개 샘플
30프레임
프레임당 332차원 MediaPipe 기반 feature
```

출력 의미:

```text
7개 클래스에 대한 확률값
argmax(output[0]) = 예측 단어 index
```

변환 시 모델 내부에 softmax를 포함했으므로, 일반적으로 TFLite 출력은 확률값으로 사용할 수 있다. 다만 모바일 구현에서 출력 합이 1에 가깝지 않다면 softmax를 한 번 적용해도 된다.

## 3. MediaPipe 좌표 추출 방식

학습, PC 실시간 추론, TFLite 테스트는 모두 같은 332차원 feature 구조를 사용했다.

프레임 1개의 feature 구성:

```text
left hand: 21개 landmark x 3차원(x, y, z) = 63
right hand: 21개 landmark x 3차원(x, y, z) = 63
face: 45개 landmark x 3차원(x, y, z) = 135
pose: 7개 landmark x 3차원(x, y, z) = 21
hand-face distances: 50
총합: 332
```

최종 feature 순서:

```text
[left_hand_63, right_hand_63, face_135, pose_21, hand_face_distance_50]
```

사용한 MediaPipe face landmark index:

```text
BASE_FACE_IDXS = [
  70, 63, 105, 66,
  336, 296, 334, 293,
  33, 160, 158, 133, 153, 144,
  362, 385, 387, 263, 373, 380,
  61, 146, 91, 181, 84, 17, 314, 405, 321, 375
]

FACEPLUS_IDXS = [
  1, 4,
  152,
  234, 454,
  172, 397,
  148, 377,
  13, 14,
  78, 308,
  82, 312
]

FACE_IDXS = BASE_FACE_IDXS + FACEPLUS_IDXS
```

사용한 pose landmark index:

```text
POSE_IDXS = [0, 7, 8, 11, 12, 13, 14]
```

손끝과 얼굴 기준점 거리 feature:

```text
HAND_TIP_IDXS = [4, 8, 12, 16, 20]
FACE_ANCHOR_IDXS = [1, 13, 152, 234, 454]
```

각 손마다 손끝 5개와 얼굴 anchor 5개의 거리 25개를 계산한다. 양손을 사용하므로 총 50차원이다.

```text
left hand distances: 5 tips x 5 anchors = 25
right hand distances: 5 tips x 5 anchors = 25
total distances = 50
```

손이 검출되지 않으면 해당 손 좌표와 거리 feature는 0으로 채운다. 얼굴 또는 pose가 일시적으로 검출되지 않으면 직전 검출값을 재사용하고, 직전값도 없으면 0으로 채운다.

## 4. 전처리 및 정규화

모델에 넣기 전에 각 프레임 feature를 어깨 기준으로 정규화한다.

정규화 기준:

```text
left shoulder index in 94-landmark array = 90
right shoulder index in 94-landmark array = 91
shoulder_center = (left_shoulder + right_shoulder) / 2
shoulder_width = distance(left_shoulder, right_shoulder)
```

정규화 방식:

```text
normalized_landmarks = (landmarks - shoulder_center) / shoulder_width
normalized_distances = distances / shoulder_width
```

`shoulder_width`가 너무 작으면 1.0으로 처리한다.

중요: 모바일에서도 학습/PC 추론과 동일하게 어깨 중심과 어깨 너비 기준 정규화를 해야 한다. 이 정규화를 빼면 모델 입력 분포가 달라져 정확도가 크게 떨어질 수 있다.

## 5. 시퀀스 길이 처리

모델은 항상 30프레임 입력을 받는다.

실시간 추론에서는 손이 보이는 동안 프레임 feature를 누적하고, 최소 15프레임 이상 모이면 예측을 시작한다.

프레임 수 처리 규칙:

```text
frames > 30:
  전체 구간에서 30개 index를 균등 샘플링

frames == 30:
  그대로 사용

15 <= frames < 30:
  마지막 프레임을 반복 padding하여 30프레임으로 맞춤

frames < 15:
  선형 보간으로 30프레임으로 upsampling
```

PC 코드에서는 최근 30프레임 window를 유지한다.

```text
frame_window = deque(maxlen=30)
```

모바일에서도 같은 방식으로 최근 30프레임을 유지하면 된다.

## 6. 실시간 추론 로직

PC 기준 TFLite 추론 코드는 다음 파일에 구현되어 있다.

```text
inference_realtime_tflite_v5.py
```

핵심 흐름:

```text
1. 카메라 프레임 획득
2. MediaPipe Holistic 또는 동등한 landmark 추출
3. 손/얼굴/pose 좌표를 332차원 feature로 변환
4. 어깨 기준 정규화
5. 최근 feature를 frame_window에 누적
6. 최소 15프레임 이상이면 [1, 30, 332] 입력 생성
7. TFLite Interpreter로 추론
8. Top-1 confidence와 Top-1/Top-2 margin 계산
9. threshold와 smoothing 조건을 만족하면 단어 확정
```

현재 PC 테스트 기준 threshold:

```text
confidence threshold = 0.75
margin threshold = 0.08
prediction smoothing window = 6
stable minimum count = 4
minimum frames for prediction = 15
```

확정 조건:

```text
top1_confidence >= 0.75
top1_confidence - top2_confidence >= 0.08
최근 6개 예측 중 같은 단어가 최소 4회 이상 등장
```

이 smoothing을 넣은 이유는 손이 이동 중인 중간 프레임에서 다른 단어가 잠깐 튀는 현상을 줄이기 위해서다.

## 7. 평서문/의문문 판별용 눈썹 정보

단어 모델 자체는 7개 단어 분류 모델이다. 평서문/의문문 판별은 별도 학습 모델이 아니라, MediaPipe face landmark 기반 rule(수학적 계산)로 처리한다.

사용 index:

```text
LEFT_EYEBROW_IDXS = [70, 63, 105, 66]
RIGHT_EYEBROW_IDXS = [336, 296, 334, 293]
LEFT_EYE_CENTER_IDXS = [33, 133]
RIGHT_EYE_CENTER_IDXS = [362, 263]
```

계산 방식:

```text
eye_distance = distance(left_eye_center, right_eye_center)
eyebrow_gap = (eye_y - eyebrow_y) / eye_distance
```

손이 보이지 않는 중립 상태에서 eyebrow gap을 모아 neutral baseline을 만들고, 현재 gap이 baseline보다 충분히 크면 의문문으로 판단한다.

현재 기준:

```text
neutral face window = 45
minimum neutral samples = 15
question eyebrow delta threshold = 0.055
```

안경 착용 시에도 MediaPipe가 눈썹 자체가 아니라 얼굴 mesh의 해당 landmark를 추정한다. 즉 안경 테두리를 직접 feature로 쓰는 것은 아니다. 다만 조명, 안경 반사, 머리카락에 따라 landmark 품질이 흔들릴 수 있다.

## 8. 모델 학습 정보

모델 구조:

```text
input_dim = 332
d_model = 128
num_heads = 8
num_layers = 3
dim_feedforward = 512
dropout = 0.1
classifier output = 7 classes
```

모델은 입력 projection 후 positional encoding을 더하고 Transformer Encoder를 통과한다. 이후 valid frame pooling을 거쳐 classifier로 7개 단어를 분류한다.

학습 데이터 구성:

```text
AI Hub 기반 npy 데이터
팀원 추가 촬영 데이터
최종 데모 안정화를 위해 오다, 자다, 아프다 제외
가다: 가다4 variant만 사용
일어나다: 일어나다2 variant만 사용
```

학습 중 사용한 주요 기법:

```text
1. shoulder normalization
2. fixed 30-frame sequence
3. stratified train/validation split
4. 부족한 train class에만 안전한 virtual augmentation 적용
5. best model numbering 저장
6. 별도 holdout_test_v5 평가
```

사용한 augmentation:

```text
Gaussian noise: std=0.01
temporal scale: 0.9 ~ 1.1
temporal shift: -2 ~ +2 frames
```

좌우 flip은 사용하지 않았다. 수어에서 좌우 방향이 의미에 영향을 줄 수 있기 때문이다.

최종 holdout 평가 결과:

```text
holdout 대상: 가다, 일어나다, 조심
accuracy: 100.00% (15/15)
가다: 5/5
일어나다: 5/5
조심: 5/5
wrong predictions: none
```

학습 로그 기준:

```text
Best F1: 1.0000
Val Acc@BestF1: 100.00%
Best Train Acc: 100.00%
```

## 9. TFLite 변환 및 양자화 정보

변환 흐름:

```text
PyTorch .pt
→ ONNX
→ TensorFlow SavedModel
→ TFLite
```

생성한 TFLite 모델:

```text
float32 TFLite: 양자화 없음, 정확도 비교용
float16 TFLite: float16 양자화/경량화 모델, 모바일 우선 적용 후보
```

현재 권장 사용 순서:

```text
1. Galaxy S26 Ultra에서 float16 테스트
2. Galaxy Flip3에서 float16 테스트
3. Flip3에서 정확도/속도 문제가 있으면 float32와 비교
4. float16이 충분히 안정적이면 모바일에는 float16 모델 적용
```

int8 양자화는 현재 적용하지 않았다. int8은 representative dataset이 필요하고, 단어 간 경계가 민감한 모델에서 정확도 손실이 커질 수 있어 데모 안정성 기준에서는 제외했다.

## 10. Galaxy S26 Ultra / Galaxy Flip3 적용 시 주의사항

S26 Ultra는 성능 여유가 크므로 float16 모델에서 실시간 추론이 가능할 것으로 예상한다. Flip3는 실제 시연 기기이므로 다음 항목을 반드시 확인한다.

**Flip3 테스트 체크리스트:**

```text
1. 카메라 프레임이 안정적으로 들어오는지
2. MediaPipe landmark 추출 FPS가 충분한지
3. 손이 화면 밖으로 나가지 않는지
4. 상반신, 얼굴, 양손이 모두 화면에 들어오는지
5. 조명 반사와 역광이 심하지 않은지
6. float16 모델에서 단어가 튀면 float32 모델과 비교
7. 예측이 너무 자주 바뀌면 smoothing count 또는 confidence threshold 조정
```

**모바일에서 권장 카메라 가이드:**

```text
상반신 전체가 보이게 촬영
얼굴과 양손이 동시에 보여야 함
손 시작 전에는 손을 잠시 내리거나 중립 위치에 둠
동작이 끝나면 마지막 자세를 짧게 유지
카메라와 너무 가까우면 손/어깨 기준 정규화가 흔들릴 수 있음
```

## 11. 모바일 구현 시 핵심 의사코드

```text
load TFLite model
load label_map

frameWindow = Queue(maxSize=30)
predictionWindow = Queue(maxSize=6)
neutralEyebrowWindow = Queue(maxSize=45)

for each camera frame:
  landmarks = runMediaPipe(frame)

  if no hand detected:
    update neutral eyebrow baseline if face exists
    clear frameWindow
    clear predictionWindow
    continue

  feature332 = extractFeature332(landmarks)
  normalized = normalizeByShoulder(feature332)
  frameWindow.push(normalized)

  if frameWindow.size < 15:
    continue

  input = makeFixed30Frames(frameWindow)
  output = tflite.run(input[1, 30, 332])

  top1, top2 = getTop2(output)
  confidence = output[top1]
  margin = output[top1] - output[top2]

  if confidence >= 0.75 and margin >= 0.08:
    predictionWindow.push(label[top1])
  else:
    predictionWindow.clear()

  if mostCommon(predictionWindow).count >= 4:
    emit detected word
```

## 12. 모바일 담당자에게 전달할 최소 패키지

최소 전달 파일:

```text
best_sign_model_v5_1_float16.tflite
best_sign_model_v5_1_float32.tflite
label_map_v5_1.json
온디바이스_AI_적용AI모델정보.md
```

참고용으로 함께 전달하면 좋은 파일:

```text
inference_realtime_tflite_v5.py
inference_realtime_transformer_v5.py
train_config_v5_1.json
```

모바일 구현에서 가장 중요한 것은 TFLite 모델 파일보다도 **학습 때와 완전히 같은 332차원 feature 추출 순서와 shoulder normalization을 맞추는 것**이다. 이 부분이 다르면 모델은 정상이어도 모바일 추론 결과가 크게 달라질 수 있다.

## 13. Android Holistic Landmarker `.task` 사용 시 구현 기준

모바일에서는 `holistic_landmarker.task`를 사용해 실시간으로 사용자의 landmark를 추출한 뒤, 그중 학습에 사용한 landmark index만 선택해서 TFLite 모델 입력을 만든다. 이 방식은 가능하며, 현재 모델 적용 방향과도 맞다.

모바일 처리 흐름:

```text
1. CameraX 등으로 카메라 프레임 획득
2. holistic_landmarker.task로 face / pose / left hand / right hand landmark 추출
3. 학습에 사용한 index만 선택
4. 프레임당 332차원 feature 생성
5. 어깨 기준 normalization 적용
6. 최근 30프레임을 [1, 30, 332] 배열로 구성
7. ByteBuffer(float32)에 순서대로 putFloat
8. TFLite Interpreter 실행
9. output[1, 7]에서 top-k / confidence / margin 계산
```

주의: 모바일 MediaPipe Tasks API의 객체명은 Python `mp.solutions.holistic`과 다를 수 있다. 하지만 landmark index 체계가 동일하다면 아래 값만 정확히 가져오면 된다.

```text
faceLandmarks[index].x / y / z
poseLandmarks[index].x / y / z
leftHandLandmarks[0..20].x / y / z
rightHandLandmarks[0..20].x / y / z
```

모바일 담당자는 `.task` 결과에서 pose landmark가 Python Holistic과 동일한 33개 pose index 체계를 사용하는지 먼저 확인해야 한다. 우리가 사용하는 pose index는 다음 7개다.

```text
POSE_IDXS = [0, 7, 8, 11, 12, 13, 14]
```

## 14. ByteBuffer 입력 규격

TFLite 모델 입력은 float32이다. float16 TFLite 모델을 사용하더라도 입력 dtype은 float32다.

```text
input shape = [1, 30, 332]
input dtype = float32
required byte size = 1 * 30 * 332 * 4 = 39,840 bytes
```

Android/Kotlin 예시:

```kotlin
val inputBuffer = ByteBuffer.allocateDirect(1 * 30 * 332 * 4)
inputBuffer.order(ByteOrder.nativeOrder())

for (t in 0 until 30) {
    for (i in 0 until 332) {
        inputBuffer.putFloat(sequence[t][i])
    }
}
inputBuffer.rewind()

val output = Array(1) { FloatArray(7) }
interpreter.run(inputBuffer, output)
```

중요한 점은 `sequence[t][i]`의 `i` 순서가 반드시 아래 순서와 같아야 한다는 것이다.

```text
0..62      left hand 21 x xyz
63..125    right hand 21 x xyz
126..260   selected face 45 x xyz
261..281   selected pose 7 x xyz
282..331   hand-face distances 50
```

ByteBuffer에는 위 순서대로 30프레임을 연속으로 넣는다.

```text
frame0 feature 332개
frame1 feature 332개
...
frame29 feature 332개
```

## 15. 모바일 feature 생성 의사코드

```kotlin
fun buildFeature332(result: HolisticResult): FloatArray {
    val feature = FloatArray(332)
    var offset = 0

    // 1. left hand: 63
    offset = putHand(feature, offset, result.leftHandLandmarks)

    // 2. right hand: 63
    offset = putHand(feature, offset, result.rightHandLandmarks)

    // 3. selected face: 135
    offset = putSelectedLandmarks(feature, offset, result.faceLandmarks, FACE_IDXS)

    // 4. selected pose: 21
    offset = putSelectedLandmarks(feature, offset, result.poseLandmarks, POSE_IDXS)

    // 5. hand-face distances: 50
    offset = putHandFaceDistances(
        feature,
        offset,
        result.leftHandLandmarks,
        result.rightHandLandmarks,
        result.faceLandmarks
    )

    return normalizeByShoulder(feature)
}
```

손이 검출되지 않은 경우:

```text
left hand 미검출 → left hand 63차원 0, left hand distance 25차원 0
right hand 미검출 → right hand 63차원 0, right hand distance 25차원 0
```

얼굴 또는 pose가 일시적으로 검출되지 않은 경우:

```text
가능하면 직전 frame의 face/pose selected feature 재사용
직전값도 없으면 0으로 채움
```

PC 추론 코드도 이 방식으로 동작한다. 모바일에서 매 프레임 face/pose 미검출을 무조건 0으로 처리하면 순간적인 입력 분포 변화가 커질 수 있으므로, 직전값 재사용을 권장한다.

## 16. 모바일 구현 시 validation 체크리스트

모바일에서 처음 연결할 때는 모델 정확도보다 입력 feature가 PC와 같은지 먼저 확인해야 한다.

확인 항목:

```text
1. TFLite input shape가 [1, 30, 332]인지
2. label_map index 순서가 모델 출력과 같은지
3. 332차원 feature 순서가 PC 코드와 같은지
4. x/y/z 좌표를 모두 float32로 넣는지
5. 손 미검출 시 해당 hand feature를 0으로 채우는지
6. face/pose 미검출 시 직전값 재사용 또는 0 처리 기준이 일관적인지
7. shoulder normalization이 적용되는지
8. 최근 30프레임 순서가 시간순인지
9. ByteBuffer rewind 후 interpreter.run을 호출하는지
10. output에 softmax가 이미 적용되어 있는지 확인하는지
```

추천 디버깅 방법:

```text
1. 모바일에서 1프레임 feature332를 로그로 저장
2. 같은 자세를 PC inference 코드에서도 feature332로 저장
3. feature 앞 10개, face 시작 index, pose 시작 index, distance 시작 index를 비교
4. 값 범위가 크게 다르면 index 순서 또는 normalization 문제를 먼저 확인
```

정규화 후 landmark 값은 일반적으로 어깨 너비 기준 상대 좌표가 되므로, raw MediaPipe 좌표처럼 0~1 범위에만 머물지 않는다.

## 17. Galaxy S26 Ultra / Flip3 적용 권장값

초기 모바일 적용값은 PC와 동일하게 시작한다.

```text
confidence threshold = 0.75
margin threshold = 0.08
smoothing window = 6
stable minimum count = 4
minimum frames = 15
```

Galaxy S26 Ultra:

```text
float16 모델 우선 테스트
MediaPipe + TFLite 전체 FPS 확인
성능 여유가 있으면 threshold는 PC와 동일하게 유지
```

Galaxy Flip3:

```text
실제 시연 기기이므로 반드시 별도 테스트
발열 또는 프레임 드랍이 있으면 카메라 해상도/FPS를 낮춤
float16에서 정확도가 흔들리면 float32도 비교
예측이 너무 자주 튀면 stable minimum count를 4에서 5로 올리는 것을 고려
반응이 너무 느리면 stable minimum count를 3으로 낮추는 것을 고려
```

데모 안정성 기준에서는 빠른 반응보다 잘못된 단어가 출력되지 않는 것이 더 중요하다. Flip3에서 순간 오인식이 보이면 threshold를 낮추기보다 smoothing을 조금 강화하는 편이 안전하다.
