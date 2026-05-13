# CTC ONNX Runtime Switch

작성일: 2026-05-12

## 요약

현재 앱은 두 가지 수어 인식 런타임을 같이 보유한다.

- `CTC_ONNX`: 664-dim `[x, dx]` feature를 입력으로 받는 CTC sequence 모델
- `TFLITE`: 기존 30-frame window 기반 TFLite word spotting 모델

기본값은 `CTC_ONNX`다. ONNX가 앱에서 불안정하거나 Android 16KB native library 이슈가 커지면, 설정 한 줄로 기존 TFLite 경로로 되돌릴 수 있다.

## 모델 파일

현재 앱에 포함된 CTC ONNX 파일:

```text
mobile/app/src/main/assets/models/ctc_sen_delta664_useraug.onnx
mobile/app/src/main/assets/models/ctc_sen_vocab.json
```

기존 TFLite 모델은 그대로 유지한다.

```text
mobile/app/src/main/assets/models/sen_word_variant_spotter_bg3_wordwin8_fixed30.tflite
mobile/app/src/main/assets/models/label_map_sen_word_variant_spotter_bg3_wordwin8.json
```

## 런타임 전환

전환 위치:

```text
mobile/app/src/main/java/com/ssafy/mobile/core/vision/inference/SignInferenceRuntimeConfig.kt
```

ONNX CTC 사용:

```kotlin
val primaryMode: SignInferenceRuntimeMode = SignInferenceRuntimeMode.CTC_ONNX
```

기존 TFLite 사용:

```kotlin
val primaryMode: SignInferenceRuntimeMode = SignInferenceRuntimeMode.TFLITE
```

디버그 영상 리플레이는 기본적으로 `primaryMode`를 따라간다.

```kotlin
val debugReplayMode: DebugReplayInferenceMode = DebugReplayInferenceMode.MATCH_PRIMARY
```

필요하면 디버그 리플레이만 강제로 고정할 수 있다.

```kotlin
val debugReplayMode: DebugReplayInferenceMode = DebugReplayInferenceMode.CTC_ONNX
val debugReplayMode: DebugReplayInferenceMode = DebugReplayInferenceMode.TFLITE_WORD_SPOTTING
```

## 코드 역할

```text
SignInferenceRuntimeConfig.kt
```

앱에서 사용할 primary inference runtime과 debug replay runtime을 정한다.

```text
CtcOnnxSignInferenceAdapter.kt
```

ONNX Runtime으로 CTC 모델을 실행한다. `LandmarkFeatureFrame` 목록을 664-dim `[x, dx]` feature로 변환하고, CTC greedy decoding으로 단어열을 만든다.

```text
SignInferenceAdapterFactory.kt
```

설정값과 asset 존재 여부에 따라 ONNX CTC 또는 TFLite adapter를 생성한다.

```text
RealSignRecognitionEngine.kt
```

실시간 카메라 입력에서 손이 보이는 구간을 segment로 모은 뒤, CTC adapter가 지원되면 전체 segment를 한 번에 decoding한다.

```text
WholeVideoIntentClassifier.kt
```

디버그 영상 업로드/리플레이에서 ONNX CTC 또는 TFLite word spotting 중 하나를 실행한다. ONNX 모드에서는 결과가 `CTC: ...` 형태로 표시된다.

```text
ReplayWindowScanner.kt
```

고정 window exhaustive replay는 구조상 TFLite 전용이다. 이 경로는 `[1, 30, 332]` fixed-window classifier를 전제로 한다.

## CTC 출력 조건

현재 CTC 경로에는 별도의 confidence threshold가 없다.

CTC가 non-blank token을 하나라도 decode하면 utterance를 출력하고, 전부 blank이면 출력하지 않는다. 지금 적용된 segment guard는 다음과 같다.

```text
min segment frames: 8
min hand frames: 6
min segment duration: 250 ms
```

CTC 결과의 confidence는 출력된 token frame들의 평균 softmax 값이다. 기존 TFLite의 class confidence와 의미가 완전히 같지는 않다.

기존 TFLite threshold는 여전히 남아 있지만, ONNX full-segment CTC 경로에는 적용되지 않는다.

```text
SignModelContract.CONFIDENCE_THRESHOLD = 0.75f
SignModelContract.MARGIN_THRESHOLD = 0.08f
```

## 현재 검증된 상태

빌드/테스트:

```powershell
cd C:\Users\SSAFY\Desktop\Project\S14P31A404\mobile
$env:GRADLE_USER_HOME='C:\Users\SSAFY\Desktop\Project\S14P31A404\mobile\.gradle-user-home'
.\gradlew.bat :app:ktlintCheck :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest --tests com.ssafy.mobile.core.vision.inference.SignInferenceAssetStateTest
.\gradlew.bat :app:installDebug
```

ADB logcat에서 확인된 ONNX 사용 로그:

```text
SignPipeline: Inference adapter. name=CtcOnnxSignInferenceAdapter, supportsFullSegmentInference=true
```

APK 내부에 포함된 ONNX 관련 파일:

```text
assets/models/ctc_sen_delta664_useraug.onnx
assets/models/ctc_sen_vocab.json
lib/arm64-v8a/libonnxruntime.so
lib/arm64-v8a/libonnxruntime4j_jni.so
```

## 디버그 리플레이 확인

앱에서 디버그 영상을 올렸을 때 ONNX CTC가 사용되는지 보려면 logcat에서 다음 패턴을 확인한다.

```powershell
adb logcat -d | Select-String -Pattern "Inference adapter|Whole-video replay inference mode|CTC ONNX|TFLite sign model"
```

ONNX CTC를 쓰는 경우 기대 로그:

```text
Inference adapter. name=CtcOnnxSignInferenceAdapter, supportsFullSegmentInference=true
Whole-video replay inference mode=CTC_ONNX
```

TFLite 리플레이를 쓰는 경우 기대 로그:

```text
Whole-video replay inference mode=TFLITE_WORD_SPOTTING
```

## 실험 결과 해석

현재 앱에 넣은 ONNX 모델은 `NIA + 순우 0512 idx1/2 adaptation + 약한 증강` 계열이다.

서버 평가에서 순우 19개 앱 feature 기준:

```text
sequence_acc = 0.8947
wer = 0.0435
```

현원 데이터는 zero-shot 및 few-shot 결과가 안정적이지 않았다. 현원 영상은 프레임 수가 짧고 동작 속도가 빠른 편이라, 현재 모델의 사용자 adaptation 범위를 벗어나는 케이스로 보는 것이 안전하다.

따라서 현재 앱 테스트의 우선순위는 다음과 같다.

1. 순우 스타일 영상이 앱 실시간 입력에서도 잘 나오는지 확인
2. 디버그 리플레이가 ONNX CTC 경로를 타는지 확인
3. 현원처럼 빠른 사용자에 대해서는 촬영 가이드, 속도 증강, 별도 adaptation 전략을 분리해서 검증

## 알려진 이슈

Android 16KB page-size 호환성 경고가 뜰 수 있다.

경고 대상에는 ONNX Runtime native library가 포함된다.

```text
lib/arm64-v8a/libonnxruntime.so
lib/arm64-v8a/libonnxruntime4j_jni.so
```

이 때문에 production 방향은 아직 확정하지 않는다. ONNX 경로가 인식 성능 측면에서 유리하더라도, 배포 안정성까지 확인되기 전까지는 TFLite fallback을 유지한다.

## 다음 작업 후보

- CTC confidence gate 추가
- 너무 짧거나 너무 빠른 segment를 걸러내는 후처리 추가
- ONNX Runtime 16KB 호환성 해결 가능 여부 확인
- 필요하면 CTC 모델을 TFLite로 다시 export하는 경로 검토
- 사용자별 few-shot adaptation 데이터 수집 기준 정리
