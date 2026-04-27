# 모바일-AI 모델 인터페이스 및 전달물 명세서 (Delivery Spec)

본 문서는 AI 파트에서 학습한 수어 인식 모델(Transformer)을 모바일 파트(Android)에 연동하기 위해 필수적으로 전달해야 하는 **항목 목록(10가지 핵심 요구사항)**과 **입출력/전처리 계약(Contract)**을 정의합니다. 모바일 팀원은 이 명세서와 샘플 데이터를 바탕으로 TFLite 추론 코드를 구현해야 합니다.

---

## 📦 1. AI -> 모바일 전달물 목록 (10가지 필수 항목 체크리스트)

AI 파트에서 학습 및 검증이 완료된 후, 모바일 파트로 아래 항목들을 압축하여 전달합니다. 아직 학습이 완료되지 않은 항목(1, 2, 5, 6번)은 어떤 형태로 전달될지 명시합니다.

1. **`sign_model.tflite`**: 학습이 완료된 초경량 수어 인식 Transformer 모델 파일 (FP16 양자화 적용 예정). *[학습 완료 후 전달]*
2. **`label_map.json`**: 모델의 출력 인덱스(0~109)와 실제 한글 단어를 매핑한 파일 (예: `{"0": "사과", "1": "바나나", ...}`). *[학습 완료 후 전달]*
3. **`scaler.json` 또는 정규화 Config**: 본 문서 **[3. Preprocess Specification]**에 명시된 알고리즘 기반 정규화 규칙으로 대체합니다.
4. **Input/Output Spec 문서**: 현재 보고 계신 이 문서가 스펙 계약서 역할을 수행합니다.
5. **샘플 입력 (`sample_features.json`)**: 모바일에서 전처리가 완벽하게 되었는지 검증하기 위한 더미 입력 데이터 (Shape: `[1, 30, 345]`). *[추후 전달]*
6. **샘플 출력 결과**: 위 샘플 입력을 넣었을 때 나와야 하는 정답 (예: `expected: top1="엄마", confidence >= 0.85`). *[추후 전달]*
7. **학습 시 사용한 MediaPipe 버전 및 Landmark 종류**: 
   - 버전: `MediaPipe Python v0.10.14`
   - 종류: `Holistic 모델` (자세 33개, 왼손 21개, 오른손 21개, Face Mesh 입술 40개)
8. **권장 Sequence Length / FPS**: 본 문서 **[2. Input Specification]** 참조 (30프레임, 30FPS).
9. **Confidence Threshold**: 본 문서 **[4. Output Specification]** 참조 (0.85 이상).
10. **None/Unknown 처리 정책**: 본 문서 **[4. Output Specification]** 참조.

---

## 📝 2. Input Specification (입력 스펙)

모바일 앱의 MediaPipe에서 뽑아낸 좌표를 모델에 넣을 때 **반드시 지켜야 하는 배열의 형태와 순서**입니다.

- **Input Tensor Shape**: `[1, 30, 345]` (배치사이즈 1, 30프레임, 345차원)
- **Sequence Length**: 최근 **30프레임** (1초 분량, 30FPS 기준)
- **Feature 구성 (1프레임당 345개 값)**:
  - 1. **Pose (33개)**: 인덱스 0 ~ 98 `(33 * 3)`
  - 2. **Left Hand (21개)**: 인덱스 99 ~ 161 `(21 * 3)`
  - 3. **Right Hand (21개)**: 인덱스 162 ~ 224 `(21 * 3)`
  - 4. **Mouth/Lips (40개)**: 인덱스 225 ~ 344 `(40 * 3)` (Face Mesh의 특정 인덱스 40개. `mobile_ai_spec.md` 참조)
- **Landmark 기준 및 결측치 처리**:
  - `(x, y, z)` 좌표를 순서대로 Flatten 합니다.
  - 좌표는 이미지 정규화(Image-normalized, 0.0 ~ 1.0)된 원본 MediaPipe 좌표를 사용합니다. Visibility 값은 넣지 않습니다.
  - 🚨 **손이나 얼굴이 Detection 되지 않았을 때:** 절대 `0.0`으로 채우지 마세요(Zero-padding 금지). 직전 프레임에서 마지막으로 관측된 좌표값을 그대로 복사해서 유지하는 **Forward Fill** 방식을 적용해야 합니다.

---

## ⚙️ 3. Preprocess Specification (전처리 스펙)

모바일 기기와 카메라 거리에 상관없이 AI가 동일하게 인식하도록 만드는 핵심 정규화(Normalization) 과정입니다. **모바일 앱 단에서 모델 입력 직전에 반드시 수행해야 합니다.**

- **정규화 방식 (알고리즘 연산)**:
  - **1단계 (Translation)**: 매 프레임마다 '좌우 어깨 좌표의 중간점(Shoulder Center)'을 구한 뒤, 115개의 모든 좌표에서 이 중심점 좌표를 빼줍니다. (어깨 중심이 `0,0,0`이 됨)
  - **2단계 (Scale)**: 매 프레임마다 '왼쪽 어깨와 오른쪽 어깨 사이의 픽셀 거리(Shoulder Width)'를 구합니다. 1단계에서 얻은 모든 좌표를 이 거리값으로 나누어 줍니다.
  - *예외 처리*: 어깨가 화면에 잡히지 않아 Width 계산이 불가능할 경우, 직전 프레임의 Width를 쓰거나 정규화를 생략하고 원본을 넣습니다.

- **프레임 큐(Queue) 처리**:
  - 카메라에서 30FPS로 이미지를 받아 FIFO Queue(길이 30)에 계속 쌓습니다.
  - 매 프레임이 들어올 때마다 Queue의 데이터를 `[1, 30, 345]` 로 만들어서 TFLite 모델에 추론(Inference)을 요청합니다. (Sliding Window 방식)

---

## 🎯 4. Output Specification (출력 스펙)

모델이 뱉어내는 결과를 모바일 앱에서 어떻게 해석하고 UI에 뿌려줄지에 대한 규칙입니다.

- **Output Tensor Shape**: `[1, 110]` (110개의 수어 단어 클래스)
- **출력 의미 (Output Meaning)**:
  - 모델의 최종 레이어에 `Softmax`가 적용되지 않고 **Logits** 상태로 나올 수 있습니다. (앱 단에서 Softmax 연산을 거쳐 확률(Probability 0.0~1.0)로 변환 필요)
- **판정 기준 (Inference Logic)**:
  - **Confidence Threshold**: Softmax 결과 중 Top-1의 확률이 **`0.85` (85%) 이상**일 때만 유효한 수어 동작(Prediction)으로 인정합니다.
  - **Smoothing / Voting**: 프레임마다 결과가 튀는 것을 방지하기 위해, 모바일 앱 단에서 **최근 5번의 추론 중 3번 이상 같은 단어**가 0.85 이상으로 나왔을 때 최종적으로 "단어가 발화(Utterance)되었다"고 확정(UI 표출)하는 로직을 권장합니다.
  - **None / Unknown 처리**: 확률이 `0.85` 미만이거나 아무런 동작을 하지 않을 때는 화면에 텍스트를 띄우지 않습니다. (추후 모델 학습 시 '정지 동작'을 의미하는 `0: "none"` 클래스를 추가할 수도 있습니다.)
