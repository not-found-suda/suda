# Final Sign Recognition Experiment Summary - 2026-05-10

## Goal

The final product needs an on-device sign recognition experience that is stable
enough for demonstration, while also showing a credible path toward free word
composition.

Two recognition directions were evaluated:

1. Fixed sentence intent classification.
2. Word spotting from sentence-internal segments.

The desired final direction is word spotting:

- Use word spotting as the primary recognition goal, because it can extract
  sentence-internal words and can eventually support freer composition.
- Keep the sentence intent classifier as a stability baseline and fallback, not
  as the main technical goal.

## Data And Feature Setup

Feature vectors use the Android-compatible 332-dimensional landmark format.

Model input for the fixed sentence classifier:

```text
[1, 30, 332] -> [1, 24]
```

The 332 dimensions come from MediaPipe landmark-derived pose/hand/face features.
The fixed sequence length of 30 is produced by resampling the detected video or
sign segment.

Feature extraction is environment-sensitive:

```text
Feature extraction env: mobile-ai-legacy
Python: 3.10
MediaPipe: 0.10.14
Backend: mp.solutions.holistic
```

Training and evaluation are done in:

```text
Training env: mobile-ai
Python: 3.11
PyTorch/TensorFlow/TFLite tools
```

This split matters. The newer `mobile-ai` environment could not reliably extract
features on the server:

- MediaPipe Tasks backend hit headless server GL issues.
- `mp.solutions.holistic` was unavailable in the newer MediaPipe package.
- The legacy environment worked and was used for feature extraction.

## Fixed Sentence Intent Classifier

The fixed intent classifier was trained over 24 selected sentence labels.

Representative server-side PyTorch result:

```text
files=88
top1_acc=0.9886
top3_acc=0.9886
```

TFLite/mobile-oriented retraining result:

```text
files=88
top1_acc=0.9659
top3_acc=0.9773
input_shape=[1, 30, 332]
output_shape=[1, 24]
```

This model is a useful fallback and comparison baseline because it is stable,
small, and TFLite-compatible. It does not solve the desired free-composition
goal because it only chooses among fixed sentence labels.

ONNX was rejected for the final app because ONNX Runtime native libraries caused
Android 16KB page-size compatibility issues. TFLite avoids that native runtime
risk.

Final TFLite assets:

```text
intent_classifier_mvp_v3_24_fixed30.tflite
label_map_mvp_v3_24_intent_tflite.json
```

## Why Free Word Composition Is Hard

Initial experiments showed that isolated WORD signs and the same labels inside
SEN sentence videos do not always match well.

For example, isolated `가다` and sentence-internal `가다` often differed enough
that nearest-neighbor matching against isolated WORD examples failed. This means
the task is not just "recognize isolated words and concatenate them."

Sentence signing contains:

```text
preparation -> word A -> transition -> word B -> ending
```

The transition/preparation/ending motion can look like a real sign to a model.
The main failure mode in free composition is therefore boundary and transition
handling.

## Word/Sentence Coverage

Observed vocabulary overlap:

```text
WORD unique: 2963
SEN unique: 449
COMMON unique: 85
WORD only: 2878
SEN only: 364

WORD labels covered by SEN: 0.0287
SEN labels covered by WORD: 0.1893

SEN token/video-count total: 506715
SEN token/video-count covered by WORD: 98170
SEN frequency covered by WORD: 0.1937
```

This means isolated WORD data is large, but only partially overlaps with
sentence vocabulary. It is useful, but not enough to solve sentence-internal word
spotting by itself.

## Segment Word Classifier Experiments

A sentence-internal segment classifier was trained using labeled SEN morpheme
segments. The goal was to classify cropped sentence segments into target words or
`<background>`.

Strong segment-level validation can be misleading because the true challenge is
scanning a full sentence and selecting the correct sequence.

### bg5 Baseline

The `bg5` model added background samples at a negative ratio of 5.

Segment-level performance was high, but full sentence scanning was harder:

```text
scan files=88
sequence_acc=0.4091
wer=0.3214
```

### Mined Hard Negatives

Hard negatives were mined by scanning training SEN videos and collecting
high-confidence false-positive windows that did not overlap labeled target
segments.

Model:

```text
sen_segment_word_classifier_mvp_v3_24_bg5_mined.pt
```

Segment eval:

```text
files=987
top1_acc=0.9747
top3_acc=0.9980
```

Initial scan result:

```text
files=88
sequence_acc=0.4091
wer=0.3163
```

This became the best base word-spotting model.

### WORD Unknown Negatives

Additional isolated WORD features were extracted using the legacy MediaPipe
environment. The goal was to use non-target isolated WORD signs as
`<background>`/unknown negatives.

Feature extraction completed for:

```text
845 WORD videos
```

Only 57 WORD unknown negative rows were ultimately added because of manifest and
target-label filtering.

Result:

```text
files=88
sequence_acc=0.3409
wer=0.3673
```

This worsened performance. It reduced some false positives but pushed real SEN
words toward background, increasing deletion errors.

Conclusion:

```text
Isolated WORD unknown negatives are not the right main fix.
SEN-internal transition/background examples matter more.
```

### Strong Transition Background

A stronger background ratio was tested to force SEN non-word regions into
`<background>`.

Result:

```text
files=88
sequence_acc=0.2386
wer=0.3980
```

This also worsened performance. The negative pressure was too strong and caused
many real words to be missed.

## Decoder Threshold Tuning

Before the window-supervised experiment, the best improvement came from tuning
the scan decoder rather than retraining.

Best model:

```text
sen_segment_word_classifier_mvp_v3_24_bg5_mined.pt
```

Best settings:

```text
score_threshold=0.95
margin_threshold=0.95
max_detections=8
window_frames=8,12,16,20,24,32,40,52,64
stride=4
```

Best scan result:

```text
files=88
sequence_acc=0.4545
wer=0.2857
```

Fine grid search showed that score threshold had little effect in the
0.93-0.96 range, while margin threshold was the key control. `margin=0.95`
was consistently best.

Remaining errors were mostly deletion errors:

```text
delete 맞다: 11
delete 가다: 7
delete 명동: 5
delete 빨리: 5
```

This means the tuned decoder reduced excessive insertions, but short or
ambiguous words were still missed.

## Window-Supervised Word Spotter

The major improvement came from matching the training distribution to the
inference distribution.

Previous segment models were trained on exact annotation crops:

```text
train: true segment start~end crop
infer: sliding windows of 8,12,16,... frames
```

This caused a distribution mismatch. At inference time, many windows include
partial words, transition motion, or extra context that the model did not see
during training.

A new manifest builder was added:

```text
mobile-ai/src/build_sen_window_spotter_manifest.py
```

It builds training samples using the same sliding-window style used by scan
inference:

```text
window overlaps one true word enough -> label = that word
window avoids all true words          -> label = <background>
ambiguous partial-overlap window      -> skip
```

Manifest command:

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

Generated manifest:

```text
rows=25761
split {'train': 19713, 'val': 6048}
kind {'SEN_WIN': 6680, 'SEN_WIN_NEG': 19081}
labels=22
```

Important label counts:

```text
<background> 19081
가다 2112
방법 640
맞다 384
불량 384
원하다 384
불가능 384
빨리 384
```

Model:

```text
sen_window_spotter_bg3.pt
```

Initial scan result with `score=0.95`, `margin=0.95`:

```text
files=88
sequence_acc=0.6364
wer=0.2041
```

This already improved substantially over the previous best:

```text
previous best: sequence_acc=0.4545, wer=0.2857
window model:  sequence_acc=0.6364, wer=0.2041
```

A threshold grid search then found the best scan setting:

```text
score_threshold=0.99
margin_threshold=0.95
max_detections=8
window_frames=8,12,16,20,24,32,40,52,64
stride=4
```

Best scan result:

```text
files=88
sequence_acc=0.7045
wer=0.1531
```

This is now the best word-spotting/free-composition result.

The main takeaway:

```text
Training the word spotter with sliding-window samples that match inference
improves word spotting much more than adding isolated WORD unknown negatives or
stronger random transition background.
```

## Current Best Model Choices

### Primary Word Spotting Result

Use:

```text
sen_window_spotter_bg3.pt
score_threshold=0.99
margin_threshold=0.95
max_detections=8
```

Reason:

- Best observed word-spotting scan performance.
- Directly matches the desired final behavior: extracting ordered words from a
  signed utterance.
- Uses training samples that match the sliding-window inference distribution.

Validation result:

```text
files=88
sequence_acc=0.7045
wer=0.1531
```

### Sentence Fallback Result

Use:

```text
intent_classifier_mvp_v3_24_fixed30.tflite
```

Reason:

- TFLite-compatible.
- Small enough for on-device use.
- High validation accuracy on the selected 24 sentence intents.
- Useful as fallback/comparison, but not the main technical goal.

## App Integration Notes

The app currently uses whole-video/whole-segment classification for the stable
intent classifier:

```text
camera/video frames
-> MediaPipe landmarks
-> 332-dim features
-> resample to 30 frames
-> TFLite intent classifier
-> one of 24 sentence labels
```

For real-time use, the app should avoid emitting predictions while idle. It
should:

1. Accumulate frames while hands are visible.
2. Wait for a short no-hands period to mark segment end.
3. Classify the completed segment once.

This is more stable than rolling-window sentence classification, but it is not
fully streaming. It is closer to short utterance recognition.

For the desired final direction, the app should prioritize a word spotting
pipeline:

```text
camera/video frames
-> MediaPipe landmarks
-> 332-dim features
-> sliding windows over the utterance
-> fixed-length resampling per window
-> word/background classifier
-> NMS + threshold decoder
-> ordered word sequence
```

The sentence intent classifier can still be displayed as a fallback/comparison:

```text
primary result: word spotting sequence
fallback/comparison: sentence intent classifier
```

This aligns the product with the free-composition goal while retaining a stable
fallback for demonstrations.

## Final Interpretation

Fixed sentence recognition is currently stronger numerically for the selected
24 intents, but it is not the target behavior. The target behavior is extracting
words from a signed utterance.

Free word composition is partially working but remains limited by:

- sentence-internal transition motion,
- start/end boundary detection,
- short word deletion,
- differences between isolated WORD signs and SEN-internal signs.

The most important finding is:

```text
The bottleneck is not just label vocabulary size.
The bottleneck is modeling non-word transition regions inside sentences.
```

Best reported word-spotting/free-composition result so far:

```text
sequence_acc=0.7045
wer=0.1531
```

This should be presented as the main technical direction, with the fixed
sentence classifier retained as a fallback and baseline.

## Mobile App Handoff

For the current Android integration state, 16:9 camera handling, fullscreen
landscape UI behavior, and manual app test plan, see:

```text
mobile-ai/docs/mobile-sign-recognition-handoff.md
```
