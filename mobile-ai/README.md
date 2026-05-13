# Mobile AI Continuous Sign Recognition

This workspace is for the on-device continuous sign-recognition pipeline.
It is intentionally separate from the existing 30-frame isolated-word model.

See `docs/sequence_labeling_plan.md` for the current agreed training plan,
vocabulary, split policy, augmentation policy, and evaluation criteria.

See `docs/sequence_labeling_baseline_2026-05-09.md` for the first
REAL01~07 train / REAL08 validation baseline result and reproduction commands.

## Goal

Build a model that extracts a word sequence from continuous sign input.

Current target:

```text
Input:  [T, 332] landmark features
Label:  frame-level blank/word ids from AI Hub start/end timestamps
Model:  sequence labeling baseline
Output: blank / word / blank / word ...
```

The existing Android feature contract is preserved:

```text
hands:               42 landmarks x 3 = 126
selected face:       45 landmarks x 3 = 135
selected pose:        7 landmarks x 3 = 21
hand-face distances: 50
total:                              332
```

## Data Format

Continuous samples are stored as a pair of files with the same stem.

```text
mobile-ai/data/continuous_v1/
  train/
    sample_0001.npy
    sample_0001.json
  holdout/
    sample_1001.npy
    sample_1001.json
```

The `.npy` file contains normalized features:

```text
shape: [T, 332]
dtype: float32
```

The `.json` file contains the word sequence label:

```json
{
  "labels": ["장난감", "조심"],
  "fps": 30.0,
  "frames": 96,
  "feature_dim": 332,
  "schema": "continuous_v1"
}
```

## First Collection Pass

Start small. The first dataset is only for proving the CTC pipeline.

```text
holdout: 10-15 clips
train:   30-50 clips
```

Suggested two-word phrases:

```text
장난감 조심
기차 가다
병원 가다
놀다 조심
일어나다 가다
장난감 놀다
기차 조심
병원 조심
```

## Collect A Clip

Install dependencies in your preferred Python environment, then run:

```powershell
pip install -r mobile-ai/requirements.txt
python mobile-ai/src/collect_continuous.py --split holdout --labels "장난감 조심"
python mobile-ai/src/collect_continuous.py --split train --labels "장난감 조심"
```

Controls:

```text
s or space: start/stop recording
q or esc: quit without saving while idle
```

The script saves the next available `sample_XXXX.npy/json` pair under the
requested split.

## Extract 332 Features From Videos

For app-compatible training, prefer extracting `[T, 332]` from the original
videos instead of using older `[T, 345]` npy files.

The extractor uses MediaPipe Tasks API and needs the same task model used by
Android:

```text
mobile/app/src/main/assets/models/holistic_landmarker.task
```

See `mobile/README.md` for the download URL. You can also pass another path
with `--task-path`.

Expected local input layout:

```text
some-video-root/
  기차/
    NIA_....mp4
  장난감/
    NIA_....mp4
  none/
    NIA_....mp4
```

Run a small smoke test first:

```powershell
python mobile-ai/src/extract_videos_332.py `
  --source-root C:\path\to\aihub-videos `
  --max-files-per-label 2
```

Full MVP extraction:

```powershell
python mobile-ai/src/extract_videos_332.py `
  --source-root C:\path\to\aihub-videos
```

Outputs:

```text
mobile-ai/data/isolated_332/
  기차/
    NIA_....npy
    NIA_....json
```

## Sequence Labeling From AI Hub Start/End

AI Hub `*_morpheme.zip` files include word-level timestamps:

```json
[
  {"start": 1.276, "end": 1.84, "labels": ["명동"]},
  {"start": 1.962, "end": 2.76, "labels": ["가다"]}
]
```

For sequence labeling, those timestamps become frame labels:

```text
blank blank 명동 명동 blank 가다 가다 blank
```

Build a child-word manifest from every downloaded morpheme zip:

```powershell
python mobile-ai/src/build_morpheme_manifest.py `
  --morpheme-root C:\Users\son\Downloads\004.수어영상 `
  --output mobile-ai/data/child_morpheme_manifest.csv `
  --only-targets
```

After downloading matching mp4 files, extract features only for rows in that manifest:

```powershell
python mobile-ai/src/extract_manifest_videos_332.py `
  --manifest mobile-ai/data/child_morpheme_manifest.csv `
  --source-root C:\path\to\downloaded-videos `
  --kind WORD `
  --max-files 20
```

Create frame-level labels:

```powershell
python mobile-ai/src/prepare_sequence_labels.py `
  --manifest mobile-ai/data/child_morpheme_manifest.csv `
  --feature-root mobile-ai/data/sequence_332
```

Train the baseline:

```powershell
python mobile-ai/src/train_sequence_labeler.py `
  --dataset-manifest mobile-ai/data/sequence_labels_child/dataset_manifest.csv `
  --vocab mobile-ai/data/sequence_labels_child/vocab.json
```

Decode a frame-level prediction:

```powershell
python mobile-ai/src/decode_sequence_labels.py `
  --predictions path\to\predictions.npy `
  --vocab mobile-ai/data/sequence_labels_child/vocab.json
```

## Planned Steps

1. Build child-word morpheme manifests.
2. Extract selected mp4 files to `[T, 332]`.
3. Convert start/end timestamps to frame-level labels.
4. Train a sequence labeling baseline in Python.
5. Evaluate repeated-word collapse and false positives.
6. Export to TFLite.
7. Add Kotlin sequence-label decoder.
8. Connect decoded words to the existing TTS flow.

## Build A Sentence Label Manifest

AI Hub `REAL/SEN/*_morpheme.zip` files contain sentence video names and
word-level timestamps. Convert them to a compact CSV first:

```powershell
python mobile-ai/src/build_sentence_manifest.py `
  --morpheme-zip C:\Users\son\Downloads\004.수어영상\1.Training\라벨링데이터\REAL\SEN\01_real_sen_morpheme.zip.part0 `
  --only-targets
```

Output:

```text
mobile-ai/data/sentence_manifest.csv
```

The CSV includes `video_name`, `labels`, `contains_target`, and `segments_json`.
