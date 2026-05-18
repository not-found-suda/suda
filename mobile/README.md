# Mobile

Android 앱은 MediaPipe Holistic landmark와 앱에 포함된 TFLite 수어 인식 모델을 사용합니다.
온디바이스 문장 변환은 Qwen LiteRT-LM 모델을 사용하며, Qwen 모델 파일은 용량 문제로 Git에 포함하지 않습니다.

## 필수 외부 파일

MediaPipe Holistic landmark 모델은 Git에 포함하지 않습니다. 로컬 실행 전에 아래 파일을 직접 내려받아 배치해야 합니다.

```text
mobile/app/src/main/assets/models/holistic_landmarker.task
```

다운로드:

```text
https://storage.googleapis.com/mediapipe-models/holistic_landmarker/holistic_landmarker/float16/1/holistic_landmarker.task
```

이 파일이 없으면 카메라 기반 landmark 추출이 동작하지 않습니다.

## 실행

루트에서 `mobile` 디렉터리로 이동한 뒤 Gradle 명령을 실행합니다.

```bash
cd mobile
./gradlew assembleDebug
```

디바이스 설치:

```bash
./gradlew installDebug
```

검증용 명령:

```bash
./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug --no-daemon
```

Android 디바이스가 인식되지 않으면 USB 디버깅 설정과 `adb devices` 결과를 먼저 확인하세요.

## 디버그 빌드에 Qwen 모델 포함

온디바이스 문장 변환용 Qwen 모델은 용량이 커서 Git에 포함하지 않습니다.
개발 PC에 모델 파일이 있으면 `mobile/local.properties`에 아래 값을 추가해 debug APK assets에 포함할 수 있습니다.

```properties
QWEN_MODEL_LOCAL_PATH=C:/path/to/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm
```

또는 기본 위치에 모델 파일을 둘 수 있습니다.

```text
mobile/local-models/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm
```

debug 빌드 시 모델 파일이 있으면 자동으로 assets에 포함됩니다. 모델 파일이 없으면 debug assets 포함은 건너뛰며,
앱에서는 마이페이지의 모델 다운로드 흐름을 통해 Qwen 모델을 준비할 수 있습니다.
