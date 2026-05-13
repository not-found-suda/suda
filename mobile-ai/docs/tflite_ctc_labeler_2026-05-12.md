# TFLite CTC Labeler Notes - 2026-05-12

## Context

The current best sequence model is a PyTorch BiGRU CTC model exported to ONNX for Android.

That path works for inference, but ONNX Runtime adds native libraries and triggered Android
16KB page-size compatibility warnings. Because of that, we also started a TFLite-native CTC
experiment.

## Why The Direct PyTorch To TFLite Path Was Stopped

The attempted path was:

```text
PyTorch BiGRU CTC
-> ONNX
-> onnx2tf SavedModel
-> TFLite
```

The dynamic onnx2tf conversion produced invalid shapes:

```text
input  features [1, 664, 1]
output Identity [1, 25, 25]
```

The fixed-shape conversion with `-ois features:1,192,664 lengths:1` failed on the first GRU:

```text
wa/encoder/GRU
tf.split(ftW, num_or_size_splits=3)
split_dim 0 size = 664
```

Even with `-kat features`, onnx2tf still tried to split the feature dimension instead of the
`3 * hidden_size` gate dimension. So this conversion path is not considered usable for the
current BiGRU model.

## Why The Previous TFLite Models Worked

The previous mobile TFLite models were not converted from PyTorch. They were trained directly
as TensorFlow/Keras models, then exported with:

```python
tf.lite.TFLiteConverter.from_keras_model(model)
```

That is why they were much easier to deploy. The model structure was also mobile-friendly:

```text
fixed 30-frame input
Conv1D / recurrent classifier
single class output
```

The current CTC model is different:

```text
variable or fixed long sequence input
BiGRU encoder
per-frame vocab logits
CTC greedy decode
```

## New TFLite-Native CTC Scripts

Added scripts:

```text
mobile-ai/src/train_tflite_ctc_labeler.py
mobile-ai/src/predict_tflite_ctc_labeler.py
```

This path trains a fixed-length TCN/Conv1D CTC model directly in Keras and exports TFLite.

It uses the same CTC manifest/vocab format as the PyTorch CTC labeler.

Default output shape:

```text
input  [1, 192, input_dim]
output [1, 192, vocab_size]
```

For the current delta664 data:

```text
input  [1, 192, 664]
output [1, 192, 25]
```

## Recommended Environment

Use the old TensorFlow environment used by previous TFLite work:

```bash
conda activate mobile-ai-tf
```

The newer `mobile-ai-tflite` environment hit cuDNN handle issues on the server.

`mobile-ai-tf` was able to load cuDNN:

```text
Loaded cuDNN version 8907
Compiled cluster using XLA
```

However, TensorFlow may rewrite `LayerNormalization` to a oneDNN/MKL op that has no GPU kernel:

```text
No registered '_MklLayerNorm' OpKernel for 'GPU'
```

The training script now disables oneDNN by default before importing TensorFlow:

```python
os.environ.setdefault("TF_ENABLE_ONEDNN_OPTS", "0")
```

It is still safest to launch with one GPU only:

```bash
export CUDA_VISIBLE_DEVICES=1
export TF_ENABLE_ONEDNN_OPTS=0
```

## Training Command

```bash
cd ~/Son
conda activate mobile-ai-tf

export CUDA_VISIBLE_DEVICES=1
export TF_ENABLE_ONEDNN_OPTS=0

python mobile-ai/src/train_tflite_ctc_labeler.py \
  --dataset-manifest ~/Son/mvp-v3-24-rich/sequence_labels_real_holdout_0512_adapt_idx12_augweak_delta664/dataset_manifest.csv \
  --vocab ~/Son/mvp-v3-24-rich/sequence_labels_real_holdout/vocab.json \
  --output ~/Son/mobile-ai/artifacts/ctc_sen_delta664_tcn_ctc_0512_useraug.tflite \
  --label-map ~/Son/mobile-ai/artifacts/ctc_sen_delta664_tcn_ctc_0512_useraug.json \
  --keras-output ~/Son/mobile-ai/artifacts/ctc_sen_delta664_tcn_ctc_0512_useraug.keras \
  --sequence-length 192 \
  --epochs 80 \
  --batch-size 8 \
  --units 192 \
  --layers 6 \
  --noise-std 0.01 \
  --time-jitter 2
```

## Evaluation Commands

NIA validation:

```bash
python mobile-ai/src/predict_tflite_ctc_labeler.py \
  --model ~/Son/mobile-ai/artifacts/ctc_sen_delta664_tcn_ctc_0512_useraug.tflite \
  --dataset-manifest ~/Son/mvp-v3-24-rich/sequence_labels_real_holdout_delta664/dataset_manifest.csv \
  --label-map ~/Son/mobile-ai/artifacts/ctc_sen_delta664_tcn_ctc_0512_useraug.json \
  --split val \
  --max-files 0 \
  --report-labels
```

Soonwoo app feature replay:

```bash
python mobile-ai/src/predict_tflite_ctc_labeler.py \
  --model ~/Son/mobile-ai/artifacts/ctc_sen_delta664_tcn_ctc_0512_useraug.tflite \
  --dataset-manifest ~/Son/mobile_debug/0512/app_ctc_manifest_all19_delta664/dataset_manifest.csv \
  --label-map ~/Son/mobile-ai/artifacts/ctc_sen_delta664_tcn_ctc_0512_useraug.json \
  --split val \
  --kind USER_0512_SOONWOO \
  --max-files 0 \
  --report-labels
```

## Decision Rule

Keep the ONNX CTC app path as the current working path.

Treat the new TFLite CTC model as an experimental backup until it reaches roughly:

```text
NIA WER <= 0.10
Soonwoo app-feature WER <= 0.10
```

If the TFLite CTC model cannot approach that range, keep:

```text
CTC_ONNX for sequence inference
TFLITE word spotter as fallback
```

## Asset Note

`mobile/app/src/main/assets/models/*.onnx` is ignored by Git in this repository because of the
Android 16KB warning. The ONNX model must be copied locally for debug/install builds that use
`SignInferenceRuntimeMode.CTC_ONNX`.
