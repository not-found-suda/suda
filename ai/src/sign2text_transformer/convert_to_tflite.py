import argparse
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

import numpy as np
import torch
import torch.onnx

try:
    import onnx
    try:
        import onnx.mapping
    except ImportError:
        from onnx import _mapping as mapping
        onnx.mapping = mapping
except ImportError:
    onnx = None

from model import KSLTransformerV5


SCRIPT_DIR = Path(__file__).resolve().parent
MODELS_ROOT = SCRIPT_DIR / "models"
DEFAULT_MODEL_DIR_NAMES = [
    "7words_test_great",
    "7words_test",
    "BEST_7words_val_acc_100-f1_100-2",
    "7words_demo_stable",
    "overfit_model_v5_7words_demo_stable",
]
DEFAULT_MODEL_DIR = MODELS_ROOT / DEFAULT_MODEL_DIR_NAMES[0]

INPUT_DIM = 332
SEQ_LEN = 30
D_MODEL = 128
NUM_HEADS = 8
NUM_LAYERS = 3


class SoftmaxWrapper(torch.nn.Module):
    def __init__(self, core_model):
        super().__init__()
        self.core_model = core_model

    def forward(self, x):
        # Mobile inference consumes probabilities directly.
        return self.core_model(x, lengths=None, return_probs=True)


def load_num_classes(label_map_path):
    with open(label_map_path, "r", encoding="utf-8") as f:
        label_map = json.load(f)
    if isinstance(label_map, dict) and "classes" in label_map:
        return len(label_map["classes"])
    return len(label_map)


def resolve_model_dir(model_dir=None):
    if model_dir:
        path = Path(model_dir)
        if path.is_absolute():
            return path
        if path.exists():
            return path.resolve()
        return MODELS_ROOT / path

    for dirname in DEFAULT_MODEL_DIR_NAMES:
        candidate = MODELS_ROOT / dirname
        if candidate.exists():
            return candidate

    return DEFAULT_MODEL_DIR


def find_latest_numbered_pair(model_dir):
    pairs = []

    legacy_model_path = model_dir / "best_sign_model_v5.pt"
    legacy_label_map_path = model_dir / "label_map_v5.json"
    if legacy_model_path.exists() and legacy_label_map_path.exists():
        pairs.append((0, legacy_model_path, legacy_label_map_path))

    for model_path in model_dir.glob("best_sign_model_v5_*.pt"):
        suffix = model_path.stem.replace("best_sign_model_v5_", "")
        if not suffix.isdigit():
            continue

        label_map_path = model_dir / f"label_map_v5_{suffix}.json"
        if label_map_path.exists():
            pairs.append((int(suffix), model_path, label_map_path))
            continue

        config_path = model_dir / f"train_config_v5_{suffix}.json"
        if config_path.exists():
            pairs.append((int(suffix), model_path, config_path))

    if not pairs:
        return None, None, None

    suffix, model_path, label_map_path = sorted(pairs, key=lambda item: item[0])[-1]
    return suffix, model_path, label_map_path


def load_model(model_path, num_classes):
    model = KSLTransformerV5(
        input_dim=INPUT_DIM,
        num_classes=num_classes,
        d_model=D_MODEL,
        num_heads=NUM_HEADS,
        num_layers=NUM_LAYERS,
    )
    try:
        state_dict = torch.load(model_path, map_location="cpu", weights_only=True)
    except TypeError:
        state_dict = torch.load(model_path, map_location="cpu")
    model.load_state_dict(state_dict)
    model.eval()
    return model


def export_onnx(export_model, onnx_path):
    dummy_input = torch.randn(1, SEQ_LEN, INPUT_DIM, dtype=torch.float32)

    print(f"[1/4] Export PyTorch -> ONNX: {onnx_path}")
    torch.onnx.export(
        export_model,
        dummy_input,
        onnx_path,
        input_names=["input"],
        output_names=["output"],
        # Keep batch/sequence fixed for mobile. This avoids extra transpose/reshape surprises.
        dynamic_axes=None,
        opset_version=17,
    )
    return dummy_input


def write_patched_onnx2tf_runner(runner_path):
    patch_code = """
import sys
import numpy as np
import onnx

if not hasattr(onnx, "mapping"):
    class MockMapping:
        pass
    onnx.mapping = MockMapping()

if not hasattr(onnx.mapping, "TENSOR_TYPE_TO_NP_TYPE"):
    onnx.mapping.TENSOR_TYPE_TO_NP_TYPE = {
        1: np.dtype("float32"), 2: np.dtype("uint8"), 3: np.dtype("int8"),
        4: np.dtype("uint16"), 5: np.dtype("int16"), 6: np.dtype("int32"),
        7: np.dtype("int64"), 8: np.dtype("object"), 9: np.dtype("bool"),
        10: np.dtype("float16"), 11: np.dtype("double"), 12: np.dtype("uint32"),
        13: np.dtype("uint64"), 14: np.dtype("complex64"), 15: np.dtype("complex128"),
        16: np.dtype("float32"),
    }

from onnx2tf import main

sys.argv = ["onnx2tf"] + sys.argv[1:]
main()
"""
    runner_path.write_text(patch_code, encoding="utf-8")


def convert_onnx_to_saved_model(onnx_path, saved_model_dir):
    runner_path = SCRIPT_DIR / "patched_onnx2tf_v5.py"
    write_patched_onnx2tf_runner(runner_path)

    if saved_model_dir.exists():
        shutil.rmtree(saved_model_dir)

    print(f"[2/4] Convert ONNX -> TensorFlow SavedModel: {saved_model_dir}")
    command = [
        sys.executable,
        str(runner_path),
        "-i",
        str(onnx_path),
        "-o",
        str(saved_model_dir),
        "-ois",
        f"input:1,{SEQ_LEN},{INPUT_DIM}",
        "-kt",
        "input",
    ]

    try:
        subprocess.run(command, check=True)
    finally:
        if runner_path.exists():
            runner_path.unlink()


def convert_saved_model_to_tflite(saved_model_dir, tflite_path, quantization="float32"):
    import tensorflow as tf

    print(f"[3/4] Convert SavedModel -> TFLite {quantization}: {tflite_path}")
    converter = tf.lite.TFLiteConverter.from_saved_model(str(saved_model_dir))
    if quantization == "float32":
        converter.optimizations = []
    elif quantization == "float16":
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]
    else:
        raise ValueError(f"Unsupported quantization mode: {quantization}")
    tflite_model = converter.convert()
    tflite_path.write_bytes(tflite_model)


def compare_outputs(export_model, dummy_input, onnx_path, tflite_paths):
    print("[4/4] Compare PyTorch / ONNX / TFLite outputs")
    with torch.no_grad():
        torch_output = export_model(dummy_input).cpu().numpy()

    try:
        import onnxruntime as ort

        ort_session = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
        onnx_output = ort_session.run(None, {"input": dummy_input.numpy()})[0]
        print(f"- PyTorch vs ONNX max_abs_diff: {np.max(np.abs(torch_output - onnx_output)):.8f}")
    except ImportError:
        print("- Skip ONNXRuntime check: onnxruntime is not installed.")

    try:
        import tensorflow as tf

        for label, tflite_path in tflite_paths:
            interpreter = tf.lite.Interpreter(model_path=str(tflite_path))
            interpreter.allocate_tensors()
            input_details = interpreter.get_input_details()
            output_details = interpreter.get_output_details()
            interpreter.set_tensor(input_details[0]["index"], dummy_input.numpy().astype(np.float32))
            interpreter.invoke()
            tflite_output = interpreter.get_tensor(output_details[0]["index"])
            print(
                f"- PyTorch vs TFLite {label} max_abs_diff: "
                f"{np.max(np.abs(torch_output - tflite_output)):.8f}"
            )
            print(f"  input shape: {input_details[0]['shape']}, dtype={input_details[0]['dtype']}")
            print(f"  output shape: {output_details[0]['shape']}, dtype={output_details[0]['dtype']}")
    except ImportError:
        print("- Skip TFLite check: tensorflow is not installed.")


def convert_pt_to_tflite(
    model_path,
    label_map_path,
    onnx_path,
    tflite_path,
    quantization="both",
    keep_saved_model=False,
):
    if onnx is None:
        raise RuntimeError("onnx is not installed. Install onnx before conversion.")

    model_path = Path(model_path)
    label_map_path = Path(label_map_path)
    onnx_path = Path(onnx_path)
    tflite_path = Path(tflite_path)
    saved_model_dir = tflite_path.parent / "tflite_saved_model"

    if not model_path.exists():
        raise FileNotFoundError(f"Model file not found: {model_path}")
    if not label_map_path.exists():
        raise FileNotFoundError(f"Label map file not found: {label_map_path}")

    num_classes = load_num_classes(label_map_path)
    print(f"Detected classes from label map: {num_classes}")
    print(f"Input shape: [1, {SEQ_LEN}, {INPUT_DIM}]")

    model = load_model(model_path, num_classes)
    export_model = SoftmaxWrapper(model).eval()

    onnx_path.parent.mkdir(parents=True, exist_ok=True)
    tflite_path.parent.mkdir(parents=True, exist_ok=True)

    dummy_input = export_onnx(export_model, onnx_path)
    convert_onnx_to_saved_model(onnx_path, saved_model_dir)

    generated_tflites = []
    if quantization in ("float32", "both"):
        float32_path = tflite_path if quantization == "float32" else tflite_path.with_name(f"{tflite_path.stem}_float32.tflite")
        convert_saved_model_to_tflite(saved_model_dir, float32_path, quantization="float32")
        generated_tflites.append(("float32", float32_path))

    if quantization in ("float16", "both"):
        float16_path = tflite_path if quantization == "float16" else tflite_path.with_name(f"{tflite_path.stem}_float16.tflite")
        convert_saved_model_to_tflite(saved_model_dir, float16_path, quantization="float16")
        generated_tflites.append(("float16", float16_path))

    compare_outputs(export_model, dummy_input, onnx_path, generated_tflites)

    if not keep_saved_model and saved_model_dir.exists():
        shutil.rmtree(saved_model_dir)

    print("\nConversion complete.")
    print(f"- ONNX: {onnx_path}")
    for label, generated_path in generated_tflites:
        print(f"- TFLite {label}: {generated_path} ({generated_path.stat().st_size / 1024 / 1024:.2f} MB)")
    print(f"- Label map: {label_map_path}")


def parse_args():
    parser = argparse.ArgumentParser(description="Convert V5 PyTorch model to TFLite.")
    parser.add_argument("--model-dir", default=None, help="Model directory name/path. Defaults to the stable 7-word demo model.")
    parser.add_argument("--model", default=None, help="Path to .pt model. Defaults to latest numbered V5 model.")
    parser.add_argument("--label-map", default=None, help="Path to label_map_v5_*.json. Defaults to matching latest model.")
    parser.add_argument("--onnx", default=None, help="Output .onnx path")
    parser.add_argument("--tflite", default=None, help="Output .tflite base path")
    parser.add_argument(
        "--quantization",
        choices=("float32", "float16", "both"),
        default="both",
        help="TFLite output type. Use 'both' to create float32 and float16 files.",
    )
    parser.add_argument("--keep-saved-model", action="store_true", help="Keep intermediate TensorFlow SavedModel")
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    model_dir = resolve_model_dir(args.model_dir)
    latest_suffix, latest_model_path, latest_label_map_path = find_latest_numbered_pair(model_dir)

    model_path = Path(args.model) if args.model else latest_model_path
    label_map_path = Path(args.label_map) if args.label_map else latest_label_map_path

    if model_path is None or label_map_path is None:
        model_path = model_dir / "best_sign_model_v5.pt"
        label_map_path = model_dir / "label_map_v5.json"

    output_stem = model_path.stem if model_path is not None else "best_sign_model_v5"
    onnx_path = Path(args.onnx) if args.onnx else model_dir / f"{output_stem}.onnx"
    tflite_path = Path(args.tflite) if args.tflite else model_dir / f"{output_stem}.tflite"

    convert_pt_to_tflite(
        model_path=model_path,
        label_map_path=label_map_path,
        onnx_path=onnx_path,
        tflite_path=tflite_path,
        quantization=args.quantization,
        keep_saved_model=args.keep_saved_model,
    )
