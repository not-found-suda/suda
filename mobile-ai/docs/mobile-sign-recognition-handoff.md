# Mobile Sign Recognition Handoff

Last updated: 2026-05-11

This document summarizes the current sign-recognition direction, model choices,
Android integration state, and known risks so another Codex session can continue
without replaying the full experiment history.

## Product Direction

The desired product behavior is real-time on-device Korean sign recognition.

The preferred recognition direction is word spotting, not fixed sentence
classification:

- Word spotting can emit ordered words such as `가다`, `방법`, `공항`, `불량`.
- Fixed sentence intent classification is more stable on the tiny MVP sentence
set, but it only chooses among memorized sentence labels.
- The app should show word spotting as the main technical result.
- Sentence intent classification can remain a fallback or comparison baseline.

## Current Best AI Result

Best word spotting experiment:

```text
model: sen_word_variant_spotter_bg3_wordwin8.pt
validation files: 88
sequence_acc: 0.7500
wer: 0.1224
recommended scan thresholds:
  score_threshold=0.99
  margin_threshold=0.95
  max_detections=8
  canonical_suffixes=__SEN,__WORD
```

Mobile TFLite export for this word spotter:

```text
mobile/app/src/main/assets/models/sen_word_variant_spotter_bg3_wordwin8_fixed30.tflite
mobile/app/src/main/assets/models/label_map_sen_word_variant_spotter_bg3_wordwin8.json
```

Model shape:

```text
input:  [1, 30, 332]
output: [1, 43]
```

Output labels are domain-specific:

```text
<background>, 가능__SEN, 가능__WORD, 가다__SEN, 가다__WORD, ...
```

The Android app merges `__SEN` and `__WORD` labels after softmax so the UI shows
canonical words such as `가다`, `방법`, and `빨리`.

The 332-dim input is MediaPipe landmark-derived pose/hand/face features. Each
window is resampled to 30 frames before TFLite inference.

For the most recent full status, see:

```text
mobile-ai/docs/current_status_2026-05-11.md
```

## Why Word Spotting Was Chosen

Experiments showed that isolated WORD signs and sentence-internal signs are not
the same distribution. For example, isolated `가다` and sentence-internal `가다`
can look noticeably different.

Sentence videos include:

```text
preparation -> word A -> transition -> word B -> ending
```

The difficult part is not only the vocabulary. The difficult part is separating
real words from transition/preparation/ending motion.

The scan-matched `sen_window_spotter_bg3` manifest improved results because it
trained the model on the same kind of sliding windows used at inference time,
including background windows.

## Environment Notes

Feature extraction on the server worked best in the legacy environment:

```text
conda env: mobile-ai-legacy
Python: 3.10
MediaPipe: 0.10.14
backend: mp.solutions.holistic
```

Training/export was done in:

```text
conda env: mobile-ai
Python: 3.11
PyTorch / TensorFlow / TFLite
```

Important: the newer `mobile-ai` environment could train/export, but it could
not reliably run `mp.solutions.holistic` extraction. Use `mobile-ai-legacy` for
server-side feature extraction.

## Repro Commands

Build the scan-matched word spotter manifest:

```bash
python mobile-ai/src/build_sen_window_spotter_manifest.py \
  --manifest ~/Son/mobile-ai/data/mvp_v3_24_sen_rich_manifest.csv \
  --feature-root ~/Son/mvp-v3-24-rich/features_332 \
  --output-root ~/Son/mvp-v3-24-rich/sen_window_spotter_bg3 \
  --val-reals 13,14,15,16 \
  --window-frames 8,12,16,20,24,32,40,52,64 \
  --stride 4 \
  --positive-min-coverage 0.60 \
  --positive-min-purity 0.25 \
  --negative-ratio 3 \
  --max-positive-per-segment 8 \
  --max-negatives-per-video 64 \
  --overwrite
```

Train the PyTorch word spotter:

```bash
python mobile-ai/src/train_sequence_classifier.py \
  --manifest ~/Son/mvp-v3-24-rich/sen_window_spotter_bg3/sen_window_spotter_manifest.csv \
  --output ~/Son/mobile-ai/artifacts/sen_window_spotter_bg3.pt \
  --epochs 40 \
  --batch-size 8
```

Scan/evaluate:

```bash
python mobile-ai/src/scan_sen_with_segment_classifier.py \
  --model ~/Son/mobile-ai/artifacts/sen_window_spotter_bg3.pt \
  --manifest ~/Son/mobile-ai/data/mvp_v3_24_sen_rich_manifest.csv \
  --feature-root ~/Son/mvp-v3-24-rich/features_332 \
  --split val \
  --window-frames 8,12,16,20,24,32,40,52,64 \
  --stride 4 \
  --score-threshold 0.99 \
  --margin-threshold 0.95 \
  --max-detections 8
```

Train/export the mobile TFLite version:

```bash
python mobile-ai/src/train_tflite_intent_classifier.py \
  --manifest ~/Son/mvp-v3-24-rich/sen_window_spotter_bg3/sen_window_spotter_manifest.csv \
  --output ~/Son/final_models/sen_window_spotter_bg3_fixed30.tflite \
  --label-map ~/Son/final_models/label_map_sen_window_spotter_bg3.json \
  --keras-output ~/Son/final_models/sen_window_spotter_bg3_fixed30.keras \
  --epochs 80 \
  --batch-size 8
```

Observed TFLite validation result:

```text
files=6048
top1_acc=0.9869
top3_acc=0.9993
input_shape=[1, 30, 332]
output_shape=[1, 22]
```

Note: this TFLite validation is window-classification accuracy, not final
sequence WER. The final word-spotting quality should still be judged with the
sliding-window decoder.

## Android Integration State

Main Android files involved:

```text
mobile/app/src/main/java/com/ssafy/mobile/feature/conversation/presentation/ConversationRoute.kt
mobile/app/src/main/java/com/ssafy/mobile/feature/conversation/presentation/ConversationViewModel.kt
mobile/app/src/main/java/com/ssafy/mobile/feature/sign/presentation/CameraAnalysisPipeline.kt
mobile/app/src/main/java/com/ssafy/mobile/feature/sign/presentation/SignRecognitionScreen.kt
mobile/app/src/main/java/com/ssafy/mobile/core/vision/wordspotting/WordSpottingScanner.kt
```

Current mobile recognition flow:

```text
CameraX frame
-> bitmap upright/mirror handling
-> 16:9 center crop before MediaPipe
-> MediaPipe holistic landmarks
-> 332-dim feature frame
-> rolling/sliding word spotting windows
-> TFLite sen_window_spotter_bg3_fixed30
-> word/background scores
-> UI gloss chips
```

ONNX was abandoned because ONNX Runtime native libraries triggered Android
16KB page-size compatibility warnings/errors. TFLite is the safer mobile path.

## Camera / Aspect Ratio Decisions

Training videos are landscape 16:9. Mobile camera use is often portrait, which
changed the landmark distribution and caused major prediction drift.

Important app-side fixes:

- `Preview` and `ImageAnalysis` request 16:9 resolution selection.
- `PreviewView` uses `ImplementationMode.COMPATIBLE` so it stays clipped inside
  the Compose layout instead of bleeding behind UI.
- The final bitmap sent to MediaPipe is center-cropped to 16:9 in
  `CameraAnalysisPipeline.kt`.
- The conversation screen shows the camera in a top 16:9 region.
- Fullscreen mode is a landscape-only focus mode: left 16:9 camera, right status
  panel, no bottom navigation.

This does not make mobile input identical to server training. Remaining domain
gap sources:

- user distance from camera,
- front-camera mirroring,
- phone lens distortion,
- lighting/background,
- hands leaving the 16:9 crop,
- MediaPipe jitter in real-time mobile frames.

## Current Conversation UI Behavior

Normal conversation mode:

- Header and mode selector remain visible.
- Camera preview is constrained to a 16:9 region.
- Status and recognized gloss chips are shown below the camera.
- Bottom navigation remains available.

Fullscreen mode:

- Enter using the `전체` button when the session is active.
- Activity orientation is forced to landscape only while fullscreen is active.
- Left side: 16:9 camera.
- Right side: real-time sign status and recognized gloss chips.
- Only the session `종료` control should remain visible.
- On fullscreen exit or session stop, orientation is forced back to portrait.

## Manual Test Plan

Basic app test:

1. Open the app.
2. Go to `소통`.
3. Select `기기`.
4. Press `대화 시작하기`.
5. Press `전체`.
6. Confirm the screen rotates to landscape.
7. Confirm the left camera area remains 16:9.
8. Confirm the right status panel shows sign status and recognized words.
9. Press `종료`.
10. Confirm the app returns to portrait.

Recognition smoke test:

- Do nothing: no random glosses should continuously appear.
- Put hands in frame: status should move toward sign recognition.
- Try easy signs:
  - `가다`
  - `방법`
  - `빨리`
  - `가능`
  - `공항`
  - `에어컨`
  - `불량`
  - `없다`

Short phrase tests:

```text
가다 방법
공항 가다
에어컨 불량
빨리 가능
불가능 불량
```

Expected behavior is not perfect accuracy yet. The immediate goal is to confirm
that the 16:9 mobile pipeline behaves closer to the server/video pipeline and
does not fire constantly while idle.

## Known Risks / Next Work

### Vocabulary Expansion Experiment

The next planned AI experiment is to expand the word spotter beyond the current
MVP labels.

Recommended order:

```text
SEN segment label frequency
-> choose support-safe top 50 labels
-> rebuild sen_window_spotter manifest
-> train
-> scan WER comparison
-> if quality is acceptable, repeat with top 100 labels
```

Helper script added:

```text
mobile-ai/src/select_sen_top_labels.py
```

Top 50 command:

```bash
python mobile-ai/src/select_sen_top_labels.py \
  --manifest ~/Son/mobile-ai/data/mvp_v3_24_sen_rich_manifest.csv \
  --feature-root ~/Son/mvp-v3-24-rich/features_332 \
  --val-reals 13,14,15,16 \
  --top-k 50 \
  --min-train-segments 4 \
  --min-val-segments 1 \
  --output ~/Son/logs/sen_top50_labels.tsv \
  --labels-output ~/Son/logs/sen_top50_labels.txt
```

Use the generated label CSV when rebuilding the window spotter manifest:

```bash
LABELS=$(cat ~/Son/logs/sen_top50_labels.txt)

python mobile-ai/src/build_sen_window_spotter_manifest.py \
  --manifest ~/Son/mobile-ai/data/mvp_v3_24_sen_rich_manifest.csv \
  --feature-root ~/Son/mvp-v3-24-rich/features_332 \
  --output-root ~/Son/mvp-v3-24-rich/sen_window_spotter_top50_bg3 \
  --labels "$LABELS" \
  --val-reals 13,14,15,16 \
  --window-frames 8,12,16,20,24,32,40,52,64 \
  --stride 4 \
  --positive-min-coverage 0.60 \
  --positive-min-purity 0.25 \
  --negative-ratio 3 \
  --max-positive-per-segment 8 \
  --max-negatives-per-video 64 \
  --overwrite
```

Train and scan:

```bash
python mobile-ai/src/train_sequence_classifier.py \
  --manifest ~/Son/mvp-v3-24-rich/sen_window_spotter_top50_bg3/sen_window_spotter_manifest.csv \
  --output ~/Son/mobile-ai/artifacts/sen_window_spotter_top50_bg3.pt \
  --epochs 40 \
  --batch-size 8

python mobile-ai/src/scan_sen_with_segment_classifier.py \
  --model ~/Son/mobile-ai/artifacts/sen_window_spotter_top50_bg3.pt \
  --manifest ~/Son/mobile-ai/data/mvp_v3_24_sen_rich_manifest.csv \
  --feature-root ~/Son/mvp-v3-24-rich/features_332 \
  --split val \
  --window-frames 8,12,16,20,24,32,40,52,64 \
  --stride 4 \
  --score-threshold 0.99 \
  --margin-threshold 0.95 \
  --max-detections 8 \
  > ~/Son/logs/scan_sen_window_spotter_top50_bg3_val.log

grep "summary" ~/Son/logs/scan_sen_window_spotter_top50_bg3_val.log
tail -n 30 ~/Son/logs/scan_sen_window_spotter_top50_bg3_val.log
```

For top 100, repeat the same flow with `--top-k 100`,
`sen_top100_labels.*`, `sen_window_spotter_top100_bg3`, and
`sen_window_spotter_top100_bg3.pt`.

Observed first top50 attempt:

```text
labels: 40
window val_acc: about 0.97
scan sequence_acc: 0.0341
scan WER: 1.0051
main failure: many high-confidence insertions
examples: 길, 사용, 지하철, 목적, 내리다, 3, 서울대학교
```

This means direct frequency-based top50 expansion is too aggressive. The next
step is cumulative batch expansion from the stable baseline labels.

Generate batch files and a runnable experiment script:

```bash
python mobile-ai/src/make_label_expansion_batches.py \
  --ranked-labels ~/Son/logs/sen_top50_labels.tsv \
  --output-dir ~/Son/logs/label_expansion_batches \
  --batch-size 5 \
  --max-new-labels 20
```

Run the generated batch experiment:

```bash
bash ~/Son/logs/label_expansion_batches/run_batches.sh
cat ~/Son/logs/label_expansion_batch_summary.tsv
```

Interpretation:

```text
batch_05 ok, batch_10 bad -> suspect labels 6-10
batch_05 already bad -> first 5 new labels are too risky
all batches bad -> expansion needs stronger background/transition training
```

### WORD/SEN Domain Variant Experiment

The next promising experiment is to use isolated WORD data without forcing it
into the same class as sentence-internal SEN signs.

Internal training labels:

```text
가다__SEN
가다__WORD
방법__SEN
방법__WORD
...
```

Canonical decoded labels:

```text
가다__SEN  -> 가다
가다__WORD -> 가다
```

Build a variant manifest from the stable SEN window manifest plus matching WORD
clips:

```bash
python mobile-ai/src/build_sen_word_variant_spotter_manifest.py \
  --sen-window-manifest ~/Son/mvp-v3-24-rich/sen_window_spotter_bg3/sen_window_spotter_manifest.csv \
  --word-manifest ~/Son/mobile-ai/data/full_word_morpheme_manifest.csv \
  --word-feature-root ~/Son/full-word/features_332 \
  --output-root ~/Son/mvp-v3-24-rich/sen_word_variant_spotter_bg3 \
  --max-word-per-label 12 \
  --overwrite
```

Train:

```bash
python mobile-ai/src/train_sequence_classifier.py \
  --manifest ~/Son/mvp-v3-24-rich/sen_word_variant_spotter_bg3/sen_word_variant_spotter_manifest.csv \
  --output ~/Son/mobile-ai/artifacts/sen_word_variant_spotter_bg3.pt \
  --epochs 40 \
  --batch-size 8
```

Scan with canonical variant merging:

```bash
python mobile-ai/src/scan_sen_with_segment_classifier.py \
  --model ~/Son/mobile-ai/artifacts/sen_word_variant_spotter_bg3.pt \
  --manifest ~/Son/mobile-ai/data/mvp_v3_24_sen_rich_manifest.csv \
  --feature-root ~/Son/mvp-v3-24-rich/features_332 \
  --split val \
  --window-frames 8,12,16,20,24,32,40,52,64 \
  --stride 4 \
  --score-threshold 0.99 \
  --margin-threshold 0.95 \
  --max-detections 8 \
  --canonical-suffixes __SEN,__WORD \
  > ~/Son/logs/scan_sen_word_variant_spotter_bg3_val.log

grep "summary" ~/Son/logs/scan_sen_word_variant_spotter_bg3_val.log
tail -n 30 ~/Son/logs/scan_sen_word_variant_spotter_bg3_val.log
```

This tests whether isolated WORD examples help the model recognize the same
canonical word while preserving the fact that isolated and sentence-internal
forms can differ.

1. Verify front-camera mirror handling.
   - If user signs look consistently swapped or wrong, test toggling
     `DEFAULT_MIRROR_ANALYSIS_INPUT`.

2. Tune real-time decoder thresholds.
   - Server best scan used `score_threshold=0.99`, `margin_threshold=0.95`.
   - Mobile live frames may require slightly lower thresholds, but lowering too
     much can cause idle false positives.

3. Collect user mobile samples.
   - The biggest practical improvement would be a small user-shot dataset in the
     same mobile camera setup.
   - Even 5 target phrases x 5 recordings can reveal whether the issue is
     mirroring, distance, or model domain gap.

4. Keep word spotting as the main story.
   - Fixed sentence classification looks better numerically, but it is limited
     to the memorized 24 sentence classes.
   - Word spotting is the credible path toward freer composition.

5. Avoid reverting unrelated mobile UI/model changes.
   - Several UI experiments were tried and rejected.
   - The current accepted direction is normal portrait mode plus optional
     landscape fullscreen focus mode with a right-side status panel.

## Related Detailed Experiment Doc

For the full AI-side experiment log and earlier model comparisons, see:

```text
mobile-ai/docs/final_experiment_summary_2026-05-10.md
```
