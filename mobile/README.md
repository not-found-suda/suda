# Mobile

이 앱은 카메라 프레임에서 MediaPipe Holistic landmark를 추출하고, 앱에 포함된
TFLite 모델로 한국어 수어 단어를 온디바이스에서 인식합니다.

## 필수 외부 파일

`holistic_landmarker.task`는 용량이 커서 Git에 포함하지 않습니다. 새 PC에서
앱을 실행하려면 아래 파일을 직접 다운로드해서 넣어야 합니다.

```text
mobile/app/src/main/assets/models/holistic_landmarker.task
```

다운로드 링크:

```text
https://storage.googleapis.com/mediapipe-models/holistic_landmarker/holistic_landmarker/float16/1/holistic_landmarker.task
```

이 파일이 없으면 카메라 landmark 추출이 동작하지 않습니다.

## Git에 포함되는 모델

현재 앱에서 사용하는 주요 TFLite 모델은 Git에 포함합니다.

```text
mobile/app/src/main/assets/models/sen_word_variant_spotter_bg3_wordwin8_fixed30.tflite
mobile/app/src/main/assets/models/label_map_sen_word_variant_spotter_bg3_wordwin8.json
```

위 모델은 word spotting용입니다. 입력은 `[1, 30, 332]`, 출력은
`__SEN`/`__WORD` domain label 후보 확률입니다. 앱에서는 `가다__SEN`과
`가다__WORD`를 `가다`로 합쳐서 표시합니다.

비교/디버그용 전체 문장 분류 모델도 포함되어 있습니다.

```text
mobile/app/src/main/assets/models/intent_classifier_mvp_v3_24_fixed30.tflite
mobile/app/src/main/assets/models/label_map_mvp_v3_24_intent_tflite.json
```

ONNX 모델은 Android 16KB page-size 호환성 문제 때문에 사용하지 않습니다.

## 실행

Android Studio에서 `mobile` 모듈을 열거나, 루트에서 아래 명령을 실행합니다.

```bash
cd mobile
./gradlew assembleDebug
```

실기기 설치:

```bash
./gradlew installDebug
```

Android 디바이스가 인식되지 않으면 USB 디버깅과 `adb devices`를 먼저 확인하세요.

## 앱에서 확인하는 방법

1. 앱 실행
2. `소통` 탭 이동
3. `기기` 모드 선택
4. `대화 시작하기`
5. 카메라에 손을 보이게 두고 수어 동작

현재 안정적으로 테스트할 수 있는 단어/구문:

```text
가다
방법
빨리
가능
공항
에어컨
불량
없다

가다 방법
공항 가다
에어컨 불량
빨리 가능
불가능 불량
```

인식 결과는 실시간 상태 영역과 gloss chip으로 표시됩니다. 아무 동작도 하지
않을 때는 임의 단어가 계속 뜨지 않는 것이 정상입니다.

## 카메라 비율

학습 데이터는 16:9 landscape 영상입니다. 모바일 세로 카메라와 분포 차이가
커서, 앱은 분석 입력을 16:9 center crop으로 맞춥니다.

관련 파일:

```text
mobile/app/src/main/java/com/ssafy/mobile/feature/sign/presentation/CameraAnalysisPipeline.kt
mobile/app/src/main/java/com/ssafy/mobile/feature/sign/presentation/SignRecognitionScreen.kt
mobile/app/src/main/java/com/ssafy/mobile/feature/conversation/presentation/ConversationRoute.kt
```

## AI 실험 문서

다른 사람이 현재 연구 흐름을 이어가려면 먼저 아래 문서를 읽는 것이 좋습니다.

```text
mobile-ai/docs/mobile-sign-recognition-handoff.md
mobile-ai/docs/final_experiment_summary_2026-05-10.md
```

요약:

```text
현재 앱 MVP: sen_word_variant_spotter_bg3_wordwin8_fixed30.tflite
현재 연구 방향: sentence 내부 word spotting
주요 이슈: WORD 단독 수어와 SEN 문장 속 수어의 도메인 차이
다음 실험: 라벨 확장, WORD/SEN variant 학습, WORD sliding positive
```
