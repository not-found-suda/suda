# Sequence Labeling Plan

Baseline record:

```text
docs/sequence_labeling_baseline_2026-05-09.md
```

## Purpose

The current 30-frame isolated-word classifier is not enough for continuous sign translation.
The next model should predict frame-level labels from a continuous landmark sequence:

```text
input:  [T, 332]
output: [T, class_count]
labels: <blank> / word
```

Post-processing collapses frame predictions into a word sequence:

```text
<blank> <blank> 병원 병원 <blank> 주차 주차 <blank>
=> 병원 주차
```

This directly addresses the current app problem where preparation, transition, and idle frames are forced into one of the known words.

## Feature Contract

Use the Android-compatible 332-dimensional feature vector.

```text
left hand:           21 * 3 = 63
right hand:          21 * 3 = 63
selected face:       45 * 3 = 135
selected pose:        7 * 3 = 21
hand-face distances:          50
total:                       332
```

The same MediaPipe Holistic Tasks model should be used for extraction:

```text
mobile/app/src/main/assets/models/holistic_landmarker.task
```

Missing hands are represented as zeros. Do not add artificial noise to zero-valued missing-hand features.

## Data Sources

Use AI Hub `004.수어영상`.

Relevant files:

```text
morpheme:
  REAL/WORD/*_morpheme.zip
  REAL/SEN/*_morpheme.zip

video:
  REAL/WORD/*_word_video.zip
  REAL/SEN/*_sen_video.zip
```

Morpheme JSON contains:

```text
metaData.name
metaData.duration
data[].start
data[].end
data[].attributes[].name
```

The `start/end` values define the actual sign morpheme interval. Frames outside those intervals should be labeled `<blank>`.

## Model Choice

Use sequence labeling first.

Reason:

```text
AI Hub has start/end timestamps.
Therefore we can directly create frame-level labels.
CTC remains useful later for data that only has sentence-level word sequences.
```

WORD and SEN should both be used:

```text
WORD:
  learns individual word movement well
  important for child-service vocabulary coverage

SEN:
  learns blank, transition, and continuous sentence context
```

WORD must not be treated as "the whole video is the word".
Use its morpheme start/end:

```text
blank blank 장난감 장난감 장난감 blank
```

## MVP Vocabulary

Initial child-service vocabulary:

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

Confirmed WORD ids from `01_real_word_morpheme`:

```text
엄마      NIA_SL_WORD1528
아기      NIA_SL_WORD1189
밥        NIA_SL_WORD1534
우유      NIA_SL_WORD0815
자다      NIA_SL_WORD1377
아프다    NIA_SL_WORD1152
병원      NIA_SL_WORD1496
놀다      NIA_SL_WORD1515
장난감    NIA_SL_WORD2158
좋다      NIA_SL_WORD0738
싫다      NIA_SL_WORD1278
조심      NIA_SL_WORD1186
배고프다  NIA_SL_WORD0953
졸리다    NIA_SL_WORD1244
행복      NIA_SL_WORD1169
화나다    NIA_SL_WORD1236
```

## Viewpoint Policy

Start with `F` videos only.

Reason:

```text
The mobile app camera is expected to be used mostly from the front.
F-only makes early experiments easier to interpret.
L/R/U/D can be added later for robustness experiments.
```

Do not use left-right flip augmentation initially because sign meaning can change.

## Train/Validation Split

For the first mini experiment:

```text
train: REAL01 ~ REAL06
val:   REAL07 ~ REAL08
view:  F only
```

This gives a signer/session holdout style validation before downloading the official validation split.

The official validation files can be used later for final evaluation.

## Augmentation Policy

Run a no-augmentation baseline first.
Then enable augmentations one by one.

Approved augmentations:

```text
speed perturbation:
  range 0.9 ~ 1.1
  resample both features and frame labels

random frame drop:
  drop rate <= 5%
  remove the same frame indices from features and labels

weak gaussian noise:
  std 0.002 ~ 0.005
  apply only to nonzero feature values
  preserve zero-valued missing hands

temporal blank crop:
  crop only leading/trailing blank regions
  never crop labeled word frames
```

Do not use initially:

```text
left-right flip
strong rotation or scale jitter
noise on missing-hand zeros
intentional hand dropout
```

Suggested experiment order:

```text
A: no augmentation
B: speed only
C: speed + weak noise
D: speed + weak noise + frame drop
E: speed + weak noise + frame drop + blank crop
```

## Evaluation

Primary evaluation should be based on the final word sequence, not frame accuracy alone.

Priority:

```text
1. collapsed word sequence accuracy
2. WER / edit distance
3. non-blank macro F1
4. frame accuracy
```

Additional app-facing metrics:

```text
blank false positive rate
duplicate word rate after collapse
latency from word end to emitted word
```

Frame accuracy can be misleading because `<blank>` frames are common.

## Current Smoke Test Result

Completed successfully with four SEN videos:

```text
NIA_SL_SEN0189_REAL01_F.mp4  좋다
NIA_SL_SEN0276_REAL01_F.mp4  병원 주차
NIA_SL_SEN0290_REAL01_F.mp4  배 아프다
NIA_SL_SEN0414_REAL01_F.mp4  명동 가다
```

Feature extraction result:

```text
좋다       (86, 332)
병원 주차  (175, 332)
배 아프다  (144, 332)
명동 가다  (126, 332)
```

Frame-label conversion result:

```text
좋다:
  <blank>: 57, 좋다: 29

병원 주차:
  <blank>: 138, 병원: 13, 주차: 24

배 아프다:
  <blank>: 99, 배: 10, 아프다: 35

명동 가다:
  <blank>: 88, 명동: 19, 가다: 19
```

Tiny training smoke test also ran successfully:

```text
epoch=1 train_loss=1.5277 train_acc=0.4463
epoch=2 train_loss=0.8187 train_acc=0.7194
epoch=3 train_loss=0.6830 train_acc=0.7702
```

This confirms the end-to-end path:

```text
mp4 -> [T, 332] -> frame labels -> sequence labeler training
```

## Server Download Plan

AI Hub dataset:

```text
datasetkey: 103
```

Already downloaded:

```text
SEN 01:  filekey 39625
WORD 01: filekey 39546
```

Planned first expansion:

```text
WORD 02~08:
  39547,39548,39549,39550,39551,39552,39553

SEN 02~08:
  39626,39627,39628,39629,39630,39631,39632
```

Use:

```bash
source ~/Son/son/aihub.env
```

The API key should stay in `~/Son/son/aihub.env` with restricted permissions:

```bash
chmod 600 ~/Son/son/aihub.env
```

## Next Steps

1. Finish downloading WORD/SEN 02~08 on the server.
2. Extract only `F` videos for the MVP vocabulary.
3. Use `REAL01~REAL06` as train and `REAL07~REAL08` as validation.
4. Extract `[T, 332]` features.
5. Generate frame-level labels from morpheme start/end.
6. Train a no-augmentation baseline.
7. Add evaluation for collapsed word sequence accuracy and WER.
8. Add augmentation experiments one at a time.
