# Sign Word Spotter Mobile Feature Investigation

작성일: 2026-05-11

## 배경

수어 단어 감지 모델을 `sen_word_variant_spotter_bg3_allviews_win16_fixed30.tflite`로 교체한 뒤, 서버 검증에서는 성능이 괜찮아 보였지만 모바일 앱 리플레이에서는 `WS: -`가 나오는 문제가 있었다.

초기 모바일 로그에서는 `가다`가 후보로 나오더라도 score/margin이 현재 앱 threshold를 통과하지 못했다.

```text
WordSpotting raw frames=37 windows=17
rawTop=가다@16-32 s=0.815 m=0.671 2=<background>:0.144 ...
accepted=-
```

당시 앱 threshold:

```text
score >= 0.992
margin >= 0.95
```

## 사용한 주요 모델/데이터

- PyTorch 모델:
  - `~/Son/mobile-ai/artifacts/sen_word_variant_spotter_bg3_allviews_win16.pt`
- TFLite 모델:
  - `~/Son/final_models/sen_word_variant_spotter_bg3_allviews_win16_fixed30.tflite`
  - 앱 asset: `mobile/app/src/main/assets/models/sen_word_variant_spotter_bg3_allviews_win16_fixed30.tflite`
- Label map:
  - `~/Son/final_models/label_map_sen_word_variant_spotter_bg3_allviews_win16.json`
  - 앱 asset: `mobile/app/src/main/assets/models/label_map_sen_word_variant_spotter_bg3_allviews_win16.json`
- 기존 서버 feature:
  - `~/Son/full-word/features_332`
- 새 tasks backend feature 후보:
  - `~/Son/full-word/features_332_tasks`

## 확인한 사실

### 1. 모바일 TFLite 실행 자체는 정상

앱에서 리플레이 후 실제 모바일 feature CSV를 저장하도록 디버그 기능을 추가했다.

저장 위치:

```text
/sdcard/Download/S14P31A404-debug/
```

예시:

```text
Download/S14P31A404-debug/sign_replay_features_8678_1778480149921.csv
```

이 CSV를 서버로 옮긴 뒤 같은 TFLite 모델에 넣었더니 모바일 로그와 동일한 결과가 재현됐다.

```text
016-032 가다:0.815 | <background>:0.144 | 불가능:0.022
004-020 빨리:0.811 | 불량:0.116 | 얼마:0.067
```

따라서 다음은 정상으로 판단했다.

- 앱의 TFLite 모델 로딩
- 앱의 label map 해석
- 앱의 TFLite inference
- 서버에서 TFLite를 호출하는 방식

### 2. 서버 PyTorch 결과와 모바일 TFLite 결과는 직접 비교하면 안 됨

서버에서 처음 비교했던 `가다:0.999` 결과는 PyTorch GRU 모델 기준이었다.

```text
server feature + PyTorch GRU => 가다:0.999
mobile feature + TFLite Conv1D => 가다:0.815
```

서로 다른 모델이므로 score 분포를 직접 비교하면 안 된다.

### 3. 기존 서버 `solutions` feature는 모바일 feature와 많이 다름

같은 TFLite 모델로 `가다_right1`을 비교했을 때:

```text
solutions feature:
000-016 얼마:0.999
012-028 기차:0.952 | 가다:0.028

mobile CSV feature:
016-032 가다:0.815 | <background>:0.144
```

즉 기존 Python MediaPipe `solutions` backend로 만든 feature는 모바일 앱에서 실제 생성되는 feature와 분포 차이가 크다.

### 4. 서버 `tasks` backend는 `solutions`보다 모바일에 가까움

서버에서 `holistic_landmarker.task`를 사용한 `tasks` backend 추출을 시도했다. 처음에는 다음 에러가 발생했다.

```text
libGLESv2.so.2: cannot open shared object file
```

해결:

```bash
conda install -c conda-forge -y libglvnd libegl libgles
export LD_LIBRARY_PATH="$CONDA_PREFIX/lib:$LD_LIBRARY_PATH"
```

이후 `tasks` backend 추출 성공.

4개 테스트 영상 비교 결과:

```text
== 가다_right1 ==
solutions: 얼마/기차로 강하게 틀어짐
tasks:     012-028 가다:0.648 top1
mobile:    016-032 가다:0.815 top1

== 가다_right2 ==
solutions: 기차/얼마로 강하게 틀어짐
tasks:     016-032 가다:0.806, 단 빨리:0.855도 높음

== 가다_left1 ==
tasks: background/없다 쪽

== 가다_left2 ==
tasks: 없다/background 쪽
```

해석:

- `tasks`는 `solutions`보다 모바일 feature 동작에 훨씬 가까움
- 오른손 `가다`에서 `가다`를 의미 있는 후보로 올림
- 왼손 `가다`에서는 `가다`가 강하게 뜨지 않음
- 하지만 Android 모바일 feature와 100% 동일하지는 않음

## 현재 결론

가장 직접적인 문제:

```text
학습/검증에 사용한 서버 feature 분포와 앱에서 실제 생성되는 모바일 feature 분포가 다르다.
```

앱에서 `WS: -`가 나온 직접 원인:

```text
모바일 feature 기준 TFLite score가 0.8대인데, 앱 threshold가 0.992/0.95로 너무 높다.
```

근본적인 개선 방향:

```text
서버 feature를 solutions가 아니라 tasks backend로 다시 추출하고,
그 feature로 TFLite를 재학습한 뒤,
모바일 CSV 기준으로 threshold를 다시 잡는다.
```

## 진행 중인 작업

`mvp_21_word_allviews_manifest_valid.csv` 기준 WORD allviews feature를 `tasks` backend로 재추출 중이다.

출력 폴더:

```text
~/Son/full-word/features_332_tasks
```

8분할 병렬 실행 중이며, 리소스 상태는 양호했다.

관측 속도:

```text
134 -> 179 files / 1 minute
약 45 files/min
```

예상 총 소요:

```text
약 50~65분
```

진행률 확인 스크립트:

```bash
cd ~/Son

python - <<'PY'
import csv
from pathlib import Path
from collections import Counter

manifest = Path("mobile-ai/data/mvp_21_word_allviews_manifest_valid.csv")
feature_root = Path("full-word/features_332_tasks")
feature_stems = {p.stem for p in feature_root.rglob("*.npy")}

done = []
missing = []

with manifest.open("r", encoding="utf-8-sig", newline="") as f:
    rows = list(csv.DictReader(f))

for r in rows:
    stem = Path(r["video_name"]).stem
    if stem in feature_stems:
        done.append(r)
    else:
        missing.append(r)

print(f"done={len(done)}/{len(rows)}")
print(f"missing={len(missing)}")
print("done_by_view", Counter(Path(r["video_name"]).stem.split("_")[-1] for r in done))
print("missing_by_view", Counter(Path(r["video_name"]).stem.split("_")[-1] for r in missing))
PY
```

## 다음 단계

### 1. tasks feature 추출 완료 확인

`~/Son/full-word/features_332_tasks`가 manifest 기준으로 모두 채워졌는지 확인한다.

### 2. 새 manifest 생성

```bash
conda activate mobile-ai
cd ~/Son

python mobile-ai/src/build_sen_word_variant_spotter_manifest.py \
  --sen-window-manifest ~/Son/mvp-v3-24-rich/sen_window_spotter_bg3_win16/sen_window_spotter_manifest.csv \
  --word-manifest ~/Son/mobile-ai/data/mvp_21_word_allviews_manifest_valid.csv \
  --word-feature-root ~/Son/full-word/features_332_tasks \
  --output-root ~/Son/mvp-v3-24-rich/sen_word_variant_spotter_bg3_tasks_allviews_win16 \
  --max-word-per-label 60 \
  --word-window-frames 16,20,24,32,40,52,64 \
  --word-window-stride 4 \
  --word-positive-min-coverage 0.60 \
  --word-positive-min-purity 0.25 \
  --max-word-windows-per-clip 8 \
  --overwrite
```

### 3. TFLite 재학습

`mobile-ai-tf` 환경에서 GPU 사용:

```bash
conda activate mobile-ai-tf
cd ~/Son

export TF_ENABLE_ONEDNN_OPTS=0
export CUDA_DEVICE_ORDER=PCI_BUS_ID
export CUDA_VISIBLE_DEVICES=1

python mobile-ai/src/train_tflite_intent_classifier.py \
  --manifest ~/Son/mvp-v3-24-rich/sen_word_variant_spotter_bg3_tasks_allviews_win16/sen_word_variant_spotter_manifest.csv \
  --output ~/Son/final_models/sen_word_variant_spotter_bg3_tasks_allviews_win16_fixed30.tflite \
  --label-map ~/Son/final_models/label_map_sen_word_variant_spotter_bg3_tasks_allviews_win16.json \
  --keras-output ~/Son/final_models/sen_word_variant_spotter_bg3_tasks_allviews_win16_fixed30.keras \
  --epochs 80 \
  --batch-size 64
```

### 4. 모바일 CSV 기준 검증

재학습된 TFLite를 모바일 CSV feature에 넣어서 `가다_right1/right2`의 score가 기존보다 좋아지는지 확인한다.

기존 모바일 CSV 기준:

```text
가다_right1: 가다 0.815
```

새 모델에서 기대하는 방향:

```text
가다 score 상승
빨리/기차/얼마 false positive 감소
```

### 5. 앱 threshold 재보정

최종 threshold는 서버 validation이 아니라 모바일 CSV 및 앱 리플레이 기준으로 결정한다.

현재 후보 시작점:

```text
score_threshold: 0.70 ~ 0.80
margin_threshold: 0.50 ~ 0.60
```

단, false positive를 함께 확인해야 한다.

## 앱에 추가된 디버그 기능

### 분석 프레임 MP4 녹화

디버그 화면에서 분석 프레임을 MP4로 저장할 수 있다.

저장 위치:

```text
Movies/S14P31A404-debug/
```

### 리플레이 feature CSV 저장

리플레이가 끝나면 앱이 실제 TFLite에 들어가는 feature sequence를 CSV로 저장한다.

저장 위치:

```text
Download/S14P31A404-debug/
```

로그 예시:

```text
SignReplay D FEATURE_CSV saved=Download/S14P31A404-debug/sign_replay_features_8678_1778480149921.csv
```

이 CSV는 특정 기기에서 실제 앱이 생성한 feature이므로, 모바일 동작 재현의 기준 데이터로 사용한다.

