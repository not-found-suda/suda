# Current Sign Recognition Status - 2026-05-11

이 문서는 현재 수어 인식 실험과 모바일 적용 상태를 다음 작업자가 바로
이어받을 수 있도록 정리한 최신 상태 문서이다.

## 결론

현재 MVP 방향은 고정 문장 분류가 아니라 sentence 내부 word spotting이다.

현재 best 서버 모델:

```text
model: /home/j-k14a404/Son/mobile-ai/artifacts/sen_word_variant_spotter_bg3_wordwin8.pt
validation files: 88
sequence_acc: 0.7500
wer: 0.1224
scan thresholds:
  score_threshold=0.99
  margin_threshold=0.95
  max_detections=8
canonical_suffixes:
  __SEN,__WORD
```

현재 모바일 적용 모델:

```text
mobile/app/src/main/assets/models/sen_word_variant_spotter_bg3_wordwin8_fixed30.tflite
mobile/app/src/main/assets/models/label_map_sen_word_variant_spotter_bg3_wordwin8.json
```

모바일 모델 shape:

```text
input_shape=[1, 30, 332]
output_shape=[1, 43]
```

TFLite window classification 검증:

```text
files=6048
top1_acc=0.9724
top3_acc=0.9983
```

주의: TFLite의 `top1_acc`는 window classification 정확도이고, 최종 품질은
sliding scan의 WER로 판단해야 한다. 현재 최종 서버 기준 best는 PyTorch
scan 결과 `WER=0.1224`이다.

## 왜 이 모델이 best인가

초기 best word spotter는 sentence 내부 window만 학습한 모델이었다.

```text
sen_window_spotter_bg3
sequence_acc=0.7045
wer=0.1531
```

그 뒤 isolated WORD 데이터와 sentence 내부 SEN 데이터를 같은 label로 억지로
섞지 않고, domain suffix로 분리해서 학습했다.

```text
가다__SEN
가다__WORD
방법__SEN
방법__WORD
...
```

추론 때만 suffix를 제거해서 canonical word로 합친다.

```text
가다__SEN + 가다__WORD -> 가다
방법__SEN + 방법__WORD -> 방법
```

실험 결과:

```text
sen_window_spotter_bg3
  sequence_acc=0.7045
  wer=0.1531

sen_word_variant_spotter_bg3
  sequence_acc=0.7386
  wer=0.1429

sen_word_variant_spotter_bg3_wordwin4
  sequence_acc=0.7386
  wer=0.1378

sen_word_variant_spotter_bg3_wordwin8
  sequence_acc=0.7500
  wer=0.1224
```

결론:

```text
SEN 문장 내부 window + WORD 단독 window를 domain label로 분리 학습하고,
추론 때 canonical word로 합치는 방식이 현재 가장 좋다.
```

## 재현 명령

### 1. WORD/SEN variant wordwin8 manifest 생성

```bash
cd ~/Son
conda activate mobile-ai

python mobile-ai/src/build_sen_word_variant_spotter_manifest.py \
  --sen-window-manifest ~/Son/mvp-v3-24-rich/sen_window_spotter_bg3/sen_window_spotter_manifest.csv \
  --word-manifest ~/Son/mobile-ai/data/full_word_morpheme_manifest.csv \
  --word-feature-root ~/Son/full-word/features_332 \
  --output-root ~/Son/mvp-v3-24-rich/sen_word_variant_spotter_bg3_wordwin8 \
  --max-word-per-label 12 \
  --word-window-frames 8,12,16,20,24,32,40,52,64 \
  --word-window-stride 4 \
  --word-positive-min-coverage 0.60 \
  --word-positive-min-purity 0.25 \
  --max-word-windows-per-clip 8 \
  --overwrite
```

Manifest 결과:

```text
rows=27777
split {'train': 21729, 'val': 6048}
kind {'SEN_WIN_DOMAIN': 6680, 'SEN_WIN_NEG': 19081, 'WORD_DOMAIN': 2016}
labels 43
```

### 2. PyTorch 모델 학습

```bash
python mobile-ai/src/train_sequence_classifier.py \
  --manifest ~/Son/mvp-v3-24-rich/sen_word_variant_spotter_bg3_wordwin8/sen_word_variant_spotter_manifest.csv \
  --output ~/Son/mobile-ai/artifacts/sen_word_variant_spotter_bg3_wordwin8.pt \
  --epochs 40 \
  --batch-size 8
```

### 3. Sliding scan 평가

```bash
python mobile-ai/src/scan_sen_with_segment_classifier.py \
  --model ~/Son/mobile-ai/artifacts/sen_word_variant_spotter_bg3_wordwin8.pt \
  --manifest ~/Son/mobile-ai/data/mvp_v3_24_sen_rich_manifest.csv \
  --feature-root ~/Son/mvp-v3-24-rich/features_332 \
  --split val \
  --window-frames 8,12,16,20,24,32,40,52,64 \
  --stride 4 \
  --score-threshold 0.99 \
  --margin-threshold 0.95 \
  --max-detections 8 \
  --canonical-suffixes __SEN,__WORD
```

결과:

```text
summary files=88 sequence_acc=0.7500 wer=0.1224
```

주요 남은 오류:

```text
insert  <extra> 가다    5
delete  가다    <empty> 5
insert  <extra> 기차    3
insert  <extra> 원하다  2
delete  빨리    <empty> 2
```

### 4. TFLite export

```bash
python mobile-ai/src/train_tflite_intent_classifier.py \
  --manifest ~/Son/mvp-v3-24-rich/sen_word_variant_spotter_bg3_wordwin8/sen_word_variant_spotter_manifest.csv \
  --output ~/Son/final_models/sen_word_variant_spotter_bg3_wordwin8_fixed30.tflite \
  --label-map ~/Son/final_models/label_map_sen_word_variant_spotter_bg3_wordwin8.json \
  --keras-output ~/Son/final_models/sen_word_variant_spotter_bg3_wordwin8_fixed30.keras \
  --epochs 80 \
  --batch-size 8
```

Export 결과:

```text
tflite=/home/j-k14a404/Son/final_models/sen_word_variant_spotter_bg3_wordwin8_fixed30.tflite
label_map=/home/j-k14a404/Son/final_models/label_map_sen_word_variant_spotter_bg3_wordwin8.json
labels=43
input_shape=[1, 30, 332]
output_shape=[1, 43]
```

## 모바일 적용 상태

현재 Android 앱은 새 wordwin8 TFLite 모델을 기본 word spotter로 사용한다.

관련 파일:

```text
mobile/app/src/main/java/com/ssafy/mobile/core/vision/wordspotting/WordSpottingScanner.kt
mobile/app/src/main/assets/models/sen_word_variant_spotter_bg3_wordwin8_fixed30.tflite
mobile/app/src/main/assets/models/label_map_sen_word_variant_spotter_bg3_wordwin8.json
```

앱에서 수행하는 canonical merge:

```text
softmax(logits)
-> index별 확률을 label로 매핑
-> label suffix 제거
-> 같은 canonical gloss 확률 합산
-> threshold / margin / NMS / adjacent duplicate collapse
```

예:

```text
가다__SEN: 0.70
가다__WORD: 0.20
<background>: 0.05

canonical 가다: 0.90
```

빌드 확인:

```text
./gradlew.bat assembleDebug
BUILD SUCCESSFUL
```

실기기 설치:

```powershell
cd C:\Users\son\Desktop\project\S14P31A404\mobile
.\gradlew.bat installDebug
```

## 라벨 확장 실험 결과

많이 등장한 SEN label을 top-N으로 한꺼번에 추가하는 실험은 실패 방향이었다.

결과:

```text
batch_05
new_labels=지하철,사용,내리다,곳,목적
sequence_acc=0.3977
wer=0.3878

batch_10
new_labels=지하철,사용,내리다,곳,목적,3,길,무엇,서울대학교,고장
sequence_acc=0.2841
wer=0.7245

batch_15
new_labels=지하철,사용,내리다,곳,목적,3,길,무엇,서울대학교,고장,공기청정기,난방,안되다,잃어버리다,접근
sequence_acc=0.1364
wer=0.7806
```

주요 실패 원인:

```text
새 label이 background/transition 구간에서 높은 confidence로 insert됨
window classification val_acc는 높지만 full scan WER가 무너짐
빈도순 label 확장은 word spotting 품질을 보장하지 않음
```

결론:

```text
라벨 확장은 top-N 일괄 추가가 아니라 greedy candidate 검증으로 해야 한다.
```

추천 방식:

```text
1. 후보 label 1개 추가
2. wordwin8 구조로 manifest 생성
3. 학습
4. scan WER 확인
5. insert 폭증 없으면 채택
6. 나쁘면 제외
7. 다음 label 테스트
```

## 카메라와 입력 비율

학습 데이터는 landscape 16:9 영상 기반이다. 모바일 세로 카메라를 그대로 쓰면
랜드마크 좌표 분포가 크게 바뀌고, 서버에서 확인한 portrait crop drift에서도
예측이 크게 달라졌다.

현재 앱 쪽 대응:

```text
CameraX Preview/ImageAnalysis는 16:9를 요청
MediaPipe 입력 전 bitmap을 16:9 center crop
UI에서도 카메라 영역을 16:9 기준으로 유지
```

이것은 완전한 일반화 해결책은 아니다. 실제 모바일 사용에서는 여전히 아래
요인이 성능을 흔든다.

```text
전면 카메라 좌우반전
사용자와 카메라 거리
손이 16:9 crop 밖으로 나가는 문제
조명/배경
MediaPipe landmark jitter
```

## 다음 작업

우선순위:

```text
1. 실기기에서 새 wordwin8 모델 테스트
2. 앱 표시가 가다__SEN이 아니라 가다로 나오는지 확인
3. 영상 업로드 테스트와 실시간 카메라 테스트를 분리해서 기록
4. 모바일 카메라 환경에서 실패 샘플 수집
5. 라벨 확장은 greedy 방식으로 별도 진행
6. 필요하면 feature-level augmentation 도입
```

실기기 확인 포인트:

```text
아무 동작 없을 때 임의 단어가 뜨지 않는가
가다/방법/빨리/가능 등 known label이 표시되는가
결과가 suffix 없이 canonical word로 표시되는가
카메라 16:9 crop에서 손이 잘리지 않는가
```

현재 상태를 한 줄로 정리하면:

```text
word spotting 연구 성능은 WER 0.1224까지 개선되었고,
해당 모델은 TFLite로 export되어 Android 앱에 연결된 상태다.
남은 핵심은 실제 모바일 카메라 환경 일반화 검증이다.
```
