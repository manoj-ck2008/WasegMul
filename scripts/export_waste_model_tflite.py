#!/usr/bin/env python3
"""Export trained waste YOLO11 model to TFLite for Android.

This script handles the full conversion pipeline:
  PyTorch (.pt) → ONNX → TFLite (.tflite)

Requirements:
    pip install ultralytics onnx2tf tensorflow

Usage:
    # Export best checkpoint (default)
    python scripts/export_waste_model_tflite.py

    # Export specific weights
    python scripts/export_waste_model_tflite.py --weights runs/train/waste-yolo11n/weights/best.pt

    # Export with INT8 quantization (smaller file, slight accuracy loss)
    python scripts/export_waste_model_tflite.py --optimize

Output:
    app/src/main/assets/waste_yolo11n.tflite
"""

import argparse
import platform
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent
ASSETS_DIR = PROJECT_ROOT / "app" / "src" / "main" / "assets"
DEFAULT_WEIGHTS = PROJECT_ROOT / "runs" / "train" / "waste-yolo11n" / "weights" / "best.pt"
IS_WINDOWS = platform.system() == "Windows"


def ensure_package(name: str) -> None:
    try:
        __import__(name)
    except ImportError:
        print(f"Installing {name}...")
        subprocess.check_call([sys.executable, "-m", "pip", "install", name])


def export_direct(weights: Path, output_name: str, optimize: bool) -> Path:
    """Direct TFLite export via ultralytics (Linux/macOS)."""
    from ultralytics import YOLO

    print(f"Loading weights: {weights}")
    model = YOLO(str(weights))

    export_kwargs = {"format": "tflite", "imgsz": 640}
    if optimize:
        export_kwargs["int8"] = True
        print("Exporting with INT8 quantization...")
    else:
        print("Exporting to TFLite (float32)...")

    export_path = model.export(**export_kwargs)

    tflite_src = Path(export_path)
    if tflite_src.is_dir():
        candidates = list(tflite_src.glob("*.tflite"))
        if not candidates:
            raise FileNotFoundError(f"No .tflite files found in {tflite_src}")
        # Prefer float32 over quantized
        float32 = [f for f in candidates if "float32" in f.name]
        if float32 and not optimize:
            tflite_src = float32[0]
        else:
            tflite_src = max(candidates, key=lambda p: p.stat().st_size)

    return tflite_src


def export_via_onnx(weights: Path, output_name: str, optimize: bool) -> Path:
    """ONNX → TFLite conversion (Windows-compatible)."""
    ensure_package("ultralytics")
    ensure_package("onnx2tf")

    from ultralytics import YOLO

    print(f"Loading weights: {weights}")
    model = YOLO(str(weights))

    with tempfile.TemporaryDirectory() as tmpdir:
        tmpdir = Path(tmpdir)

        print("Exporting to ONNX...")
        onnx_path = model.export(format="onnx", imgsz=640)
        onnx_file = Path(onnx_path)
        if not onnx_file.exists():
            onnx_file = tmpdir / "model.onnx"
            # Try default location
            default_onnx = PROJECT_ROOT / "runs" / "train" / "waste-yolox" / "weights" / "best.onnx"
            if default_onnx.exists():
                shutil.copy2(default_onnx, onnx_file)
            else:
                raise FileNotFoundError(f"ONNX export failed. Check ultralytics output.")

        print("Converting ONNX → TFLite via onnx2tf...")
        output_dir = tmpdir / "tflite_output"
        cmd = [
            sys.executable, "-m", "onnx2tf",
            "-i", str(onnx_file),
            "-o", str(output_dir),
            "-osd", "-cotof",
        ]
        if optimize:
            cmd.append("-oiqt")  # INT8 quantization
        subprocess.check_call(cmd)

        tflite_candidates = list(output_dir.glob("*.tflite"))
        if not tflite_candidates:
            raise FileNotFoundError(f"No .tflite files produced by onnx2tf in {output_dir}")

        float32 = [f for f in tflite_candidates if "float32" in f.name]
        if float32 and not optimize:
            return float32[0]
        return max(tflite_candidates, key=lambda p: p.stat().st_size)


def generate_labels_file(output_name: str) -> Path:
    """Generate the label file for the waste model."""
    import yaml
    config_path = SCRIPT_DIR / "waste_dataset.yaml"
    with open(config_path) as f:
        config = yaml.safe_load(f)

    labels = list(config["names"].values())
    labels_file = ASSETS_DIR / f"{output_name}_classes.txt"
    with open(labels_file, "w") as f:
        f.write("\n".join(labels) + "\n")

    print(f"Generated labels: {labels_file} ({len(labels)} classes)")
    return labels_file


def main():
    parser = argparse.ArgumentParser(description="Export waste YOLO to TFLite")
    parser.add_argument("--weights", type=str, default=str(DEFAULT_WEIGHTS),
                        help="Path to trained .pt weights")
    parser.add_argument("--output-name", type=str, default="waste_yolo11n",
                        help="Output filename (without .tflite)")
    parser.add_argument("--optimize", action="store_true",
                        help="Apply INT8 quantization (smaller file)")
    args = parser.parse_args()

    weights = Path(args.weights)
    if not weights.exists():
        print(f"Weights not found: {weights}")
        print("Train first: python scripts/train_waste_yolo.py")
        sys.exit(1)

    print(f"Source: {weights}")
    print(f"Output: {ASSETS_DIR / (args.output_name + '.tflite')}")
    print()

    try:
        if IS_WINDOWS:
            tflite_src = export_via_onnx(weights, args.output_name, args.optimize)
        else:
            tflite_src = export_direct(weights, args.output_name, args.optimize)
    except Exception as e:
        print(f"\nERROR: Export failed: {e}")
        sys.exit(1)

    # Copy to assets
    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    dest = ASSETS_DIR / f"{args.output_name}.tflite"
    shutil.copy2(tflite_src, dest)
    size_mb = dest.stat().st_size / (1024 * 1024)
    print(f"\nCopied: {dest} ({size_mb:.2f} MB)")

    # Generate label file
    generate_labels_file(args.output_name)

    # Also copy existing COCO labels as fallback
    coco_labels = ASSETS_DIR / "yolov8n_classes.txt"
    if not coco_labels.exists():
        print("\nNote: No yolov8n_classes.txt found. The waste model uses its own labels.")

    print("\n✓ Export complete!")
    print(f"  Model: {dest}")
    print(f"  Labels: {ASSETS_DIR / (args.output_name + '_classes.txt')}")
    print("\nNext steps:")
    print(f"  1. Update YoloDetector.kt MODEL_FILENAME to '{args.output_name}.tflite'")
    print(f"  2. Update YoloDetector.kt label list to use {args.output_name}_classes.txt")
    print(f"  3. Build and test: ./gradlew assembleDebug")


if __name__ == "__main__":
    main()
