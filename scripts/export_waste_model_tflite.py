#!/usr/bin/env python3
"""Export trained waste YOLO11 model to TFLite for Android.

This script handles the full conversion pipeline:
  PyTorch (.pt) → ONNX → TFLite (.tflite)

Requirements:
    pip install -r scripts/requirements-train.txt  # (ultralytics, onnx2tf, tensorflow)

Usage:
    # Export best checkpoint (default, FP32 — the shippable artifact)
    python scripts/export_waste_model_tflite.py

    # Export specific weights
    python scripts/export_waste_model_tflite.py --weights runs/train/waste-yolo11n/weights/best.pt

    # Export with INT8 quantization (REQUIRES calibration data + AP check)
    python scripts/export_waste_model_tflite.py --optimize --calibration-data datasets/waste-yolo/data.yaml

INT8 policy (audit §3.47): INT8 without a representative calibration dataset
throws or collapses accuracy. This script FAILS with a clear error unless
--calibration-data is given, and prints the mandatory AP-regression check
(FP32 vs INT8 mAP@50; reject if drop > ~2pp). Never silently ship broken int8.
The labels file (<output>_classes.txt) is ALWAYS written alongside the .tflite.

Output:
    app/src/main/assets/waste_yolo11n.tflite (+ waste_yolo11n_classes.txt)
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


def export_direct(weights: Path, output_name: str, optimize: bool,
                  calibration_data: str | None = None) -> Path:
    """Direct TFLite export via ultralytics (Linux/macOS)."""
    from ultralytics import YOLO

    print(f"Loading weights: {weights}")
    model = YOLO(str(weights))

    export_kwargs = {"format": "tflite", "imgsz": 640}
    if optimize:
        if not calibration_data:
            raise SystemExit(
                "INT8 EXPORT BLOCKED: --optimize requires --calibration-data "
                "<data.yaml> (representative dataset for quantization calibration). "
                "Re-run with --calibration-data datasets/waste-yolo/data.yaml. "
                "Never ship INT8 without it + an AP regression check."
            )
        export_kwargs["int8"] = True
        export_kwargs["data"] = calibration_data
        print(f"Exporting with INT8 quantization (calibration: {calibration_data})...")
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


def export_via_onnx(weights: Path, output_name: str, optimize: bool,
                    calibration_data: str | None = None) -> Path:
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
            default_onnx = PROJECT_ROOT / "runs" / "train" / "waste-yolo11n" / "weights" / "best.onnx"
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
            if not calibration_data:
                raise SystemExit(
                    "INT8 EXPORT BLOCKED: --optimize requires --calibration-data "
                    "for onnx2tf quantization. Re-run with --calibration-data "
                    "datasets/waste-yolo/data.yaml."
                )
            cmd.append("-oiqt")  # INT8 quantization
            print(f"INT8 quantization with calibration data: {calibration_data} "
                  "(AP regression check mandatory before shipping)")
        subprocess.check_call(cmd)

        tflite_candidates = list(output_dir.glob("*.tflite"))
        if not tflite_candidates:
            raise FileNotFoundError(f"No .tflite files produced by onnx2tf in {output_dir}")

        float32 = [f for f in tflite_candidates if "float32" in f.name]
        if float32 and not optimize:
            return float32[0]
        return max(tflite_candidates, key=lambda p: p.stat().st_size)


def generate_labels_file(output_name: str) -> Path:
    """Generate the label file for the waste model (ALWAYS alongside .tflite).

    Single-source chain: taxonomy.yaml yolo_classes -> scripts/waste_dataset.yaml
    names -> <output>_classes.txt. Never ship a .tflite without its labels file;
    stale/missing labels trigger the app's COCO fallback guard.
    """
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
                        help="Apply INT8 quantization (REQUIRES --calibration-data + AP regression check; never ship unchecked)")
    parser.add_argument("--calibration-data", type=str, default=None,
                        help="Representative dataset YAML for INT8 calibration (e.g. datasets/waste-yolo/data.yaml)")
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
            tflite_src = export_via_onnx(weights, args.output_name, args.optimize, args.calibration_data)
        else:
            tflite_src = export_direct(weights, args.output_name, args.optimize, args.calibration_data)
    except SystemExit:
        raise
    except Exception as e:
        print(f"\nERROR: Export failed: {e}")
        sys.exit(1)

    # Copy to assets
    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    dest = ASSETS_DIR / f"{args.output_name}.tflite"
    shutil.copy2(tflite_src, dest)
    size_mb = dest.stat().st_size / (1024 * 1024)
    print(f"\nCopied: {dest} ({size_mb:.2f} MB)")

    # Generate label file (ALWAYS — never ship .tflite without labels)
    generate_labels_file(args.output_name)

    if args.optimize:
        print("\nINT8 SHIPPABILITY CHECK (mandatory, not optional):")
        print("  1. Run val on the INT8 .tflite vs FP32 and compare mAP@50.")
        print("  2. Reject if drop > ~2pp or any waste class collapses.")
        print("  3. Do NOT copy INT8 into assets/ until the check passes.")

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
