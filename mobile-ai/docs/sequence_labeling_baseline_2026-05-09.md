# Sequence Labeling Baseline - 2026-05-09

## Summary

This document records the current continuous sign-recognition baseline.

The main goal was to replace the 30-frame isolated-word classifier with a
frame-level sequence labeling model:

```text
input:  [T, 332]
output: [T, blank + child vocabulary]
decode: frame ids -> collapsed word sequence
```

The end-to-end pipeline is now verified:

```text
AI Hub mp4
-> MediaPipe landmarks
-> Android-compatible 332 features
-> AI Hub start/end frame labels
-> BiGRU sequence labeler
-> collapsed word output
```

## Important Caveat

The server could not run the same `holistic_landmarker.task` backend used by
Android. The Tasks API repeatedly failed in the headless server GL runtime.

The working extraction backend is:

```text
mp.solutions.holistic
mediapipe==0.10.14
```

Therefore, this baseline is useful for model/pipeline validation, but final app
compatibility still needs to be checked against Android `holistic_landmarker.task`
features.

## Feature Format

The model uses the current Android 332-dimensional feature contract:

```text
left hand:           21 * 3 = 63
right hand:          21 * 3 = 63
selected face:       45 * 3 = 135
selected pose:        7 * 3 = 21
hand-face distances:          50
total:                       332
```

Missing hand values remain zero. No noise or synthetic hand dropout was applied.

## Vocabulary

The baseline uses `<blank>` plus the child-service MVP vocabulary:

```text
엄마
아기
밥
우유
자다
아프다
병원
놀다
장난감
좋다
싫다
조심
배고프다
졸리다
행복
화나다
```

Additional AI Hub WORD ids were included when they canonicalize to the same
MVP labels. Examples observed in the baseline include:

```text
WORD1191 -> 좋다
WORD1197 -> 배고프다
WORD1243 -> 우유
WORD1378 -> 자다
WORD1385 -> 싫다
WORD1544 -> 자다
WORD2005 -> 아기
```

## Data Used

Dataset:

```text
AI Hub 004.수어영상
Training / REAL / SEN
Training / REAL / WORD
view: F only
```

Downloaded video zip coverage:

```text
SEN video 01~16
WORD video 01~16
```

This covers `REAL01~REAL08`.

Extracted mini dataset:

```text
features: 414 npy files
labels:   414 matched frame-label files
```

Train/validation split:

```text
train: REAL01~REAL07
val:   REAL08
```

Split counts:

```text
train rows: 362
  SEN:  203
  WORD: 159

val rows: 52
  SEN:  29
  WORD: 23
```

## Class Distribution

The dataset is highly imbalanced toward `병원`.

Train distribution:

```text
병원      161
좋다       28
아프다     21
화나다     21
자다       20
조심       14
배고프다   14
아기       14
싫다       14
우유       13
행복        7
졸리다      7
놀다        7
엄마        7
밥          7
장난감      7
```

Validation distribution:

```text
병원       23
좋다        4
아프다      3
화나다      3
자다        3
조심        2
우유        2
배고프다    2
아기        2
싫다        2
행복        1
졸리다      1
놀다        1
엄마        1
밥          1
장난감      1
```

Because `병원` is overrepresented, overall accuracy can look better than the
minority-word behavior.

## Extraction Environments

Feature extraction environment:

```text
conda env: mobile-ai-legacy
python:    3.10
mediapipe: 0.10.14
backend:   mp.solutions.holistic
```

Training environment:

```text
conda env: mobile-ai
python:    3.11
torch:     2.10.0+cu128
CUDA:      available
GPU:       Tesla V100S-PCIE-32GB
```

PyTorch initially failed to use CUDA because `torch 2.11.0+cu130` required a
newer driver than the server had. Reinstalling a CUDA 12.8 build fixed it:

```text
torch 2.10.0+cu128
torch cuda build 12.8
cuda available True
```

## Model

Baseline model:

```text
architecture: BiGRU sequence labeler
input_dim:    332
hidden_size:  128
layers:       2
loss:         CrossEntropyLoss(ignore_index=-100)
decoder:      collapse repeated non-blank runs
min_run:      10 for reported results
```

Saved artifact:

```text
~/Son/mobile-ai/artifacts/sequence_labeler_01_08_real08_val.pt
```

## Main Result

Validation set:

```text
REAL08 only
files: 52
```

Overall:

```text
sequence_acc: 0.9423
WER:          0.0577
frame_acc:    0.9323
```

SEN only:

```text
files:        29
sequence_acc: 1.0000
WER:          0.0000
frame_acc:    0.9510
```

WORD only:

```text
files:        23
sequence_acc: 0.8696
WER:          0.1304
frame_acc:    0.8979
```

## Observed Errors

Validation errors were concentrated in isolated WORD clips:

```text
NIA_SL_WORD0738_REAL08_F.mp4
ref=좋다
pred=졸리다 좋다

NIA_SL_WORD1528_REAL08_F.mp4
ref=엄마
pred=좋다

NIA_SL_WORD1534_REAL08_F.mp4
ref=밥
pred=졸리다
```

Interpretation:

```text
SEN context is currently easy, especially because many SEN samples are 병원.
WORD-only clips still show confusion between short/visually similar motions or
preparation/ending postures.
```

## Reproduction Commands

Create labels from extracted features:

```bash
cd ~/Son

python mobile-ai/src/prepare_sequence_labels.py \
  --manifest mobile-ai/data/child_morpheme_manifest.csv \
  --feature-root ~/Son/mini-f-only/features_332 \
  --label-manifest mobile-ai/config/label_manifest_child_mvp.json \
  --output-root ~/Son/mini-f-only/sequence_labels_01_08 \
  --overwrite
```

Create REAL08 validation split:

```bash
python - <<'PY'
import csv
from pathlib import Path

src = Path.home() / "Son" / "mini-f-only" / "sequence_labels_01_08" / "dataset_manifest.csv"
out = Path.home() / "Son" / "mini-f-only" / "sequence_labels_01_08_real08_val.csv"

rows = list(csv.DictReader(open(src, encoding="utf-8-sig")))
fields = rows[0].keys()

for row in rows:
    row["split"] = "val" if "_REAL08_" in row["video_name"] else "train"

with open(out, "w", encoding="utf-8-sig", newline="") as file:
    writer = csv.DictWriter(file, fieldnames=fields)
    writer.writeheader()
    writer.writerows(rows)

print("train", sum(row["split"] == "train" for row in rows))
print("val", sum(row["split"] == "val" for row in rows))
PY
```

Train:

```bash
python mobile-ai/src/train_sequence_labeler.py \
  --dataset-manifest ~/Son/mini-f-only/sequence_labels_01_08_real08_val.csv \
  --vocab ~/Son/mini-f-only/sequence_labels_01_08/vocab.json \
  --output ~/Son/mobile-ai/artifacts/sequence_labeler_01_08_real08_val.pt \
  --epochs 30 \
  --batch-size 8
```

Evaluate all validation samples:

```bash
python mobile-ai/src/predict_sequence_labeler.py \
  --model ~/Son/mobile-ai/artifacts/sequence_labeler_01_08_real08_val.pt \
  --dataset-manifest ~/Son/mini-f-only/sequence_labels_01_08_real08_val.csv \
  --vocab ~/Son/mini-f-only/sequence_labels_01_08/vocab.json \
  --max-files 0 \
  --min-run 10 \
  --split val
```

Evaluate SEN and WORD separately:

```bash
python mobile-ai/src/predict_sequence_labeler.py \
  --model ~/Son/mobile-ai/artifacts/sequence_labeler_01_08_real08_val.pt \
  --dataset-manifest ~/Son/mini-f-only/sequence_labels_01_08_real08_val.csv \
  --vocab ~/Son/mini-f-only/sequence_labels_01_08/vocab.json \
  --max-files 0 \
  --min-run 10 \
  --split val \
  --kind SEN

python mobile-ai/src/predict_sequence_labeler.py \
  --model ~/Son/mobile-ai/artifacts/sequence_labeler_01_08_real08_val.pt \
  --dataset-manifest ~/Son/mini-f-only/sequence_labels_01_08_real08_val.csv \
  --vocab ~/Son/mini-f-only/sequence_labels_01_08/vocab.json \
  --max-files 0 \
  --min-run 10 \
  --split val \
  --kind WORD
```

## Next Steps

1. Add run-level confidence thresholding to the decoder.
2. Add per-label recall/precision and balanced accuracy reporting.
3. Document and implement OOV segment handling as `ignore_index=-100`.
4. When `REAL09~REAL16` downloads complete, run a larger split:

```text
train: REAL01~REAL12
val:   REAL13~REAL16
```

5. Compare `mp.solutions.holistic` features against Android
   `holistic_landmarker.task` features on a small shared video set before app
   integration.
