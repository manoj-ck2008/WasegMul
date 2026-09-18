#!/usr/bin/env python3
"""Export STOCK COCO YOLOv8n to TFLite (generic fallback — NOT the waste model).

WARNING: this script downloads stock yolov8n.pt (COCO-80, Ultralytics) and
exports it. The output is a GENERIC object detector (person/car/bottle/...),
NOT the WasegMul 15-class waste detector. The app's YoloDetector guards
against mislabeling (COCO-0 person is never reported as waste-0 battery),
but COCO output must never be presented as waste classes.

For the real waste model, train + export via:
    python scripts/train_waste_yolo.py            # -> waste_yolo11n.tflite
    python scripts/export_waste_model_tflite.py   # -> waste_yolo11n.tflite

Output filename keeps the app's COCO-fallback contract:
    app/src/main/assets/yolov8n.tflite  (COCO-80; pair ONLY with COCO-80 labels)

Requirements:
    pip install -r scripts/requirements-train.txt  # (ultralytics, onnx2tf)

Usage:
    python scripts/export_yolo_tflite.py
    python scripts/export_yolo_tflite.py --output-name yolov8n  # default; COCO contract

On Linux/macOS: exports directly via ultralytics.
On Windows: exports to ONNX first, then converts via onnx2tf.
"""
import platform
import subprocess
import sys
import shutil
import tempfile
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ASSETS_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets"
MODEL_FILENAME = "yolov8n.tflite"  # COCO-80 fallback contract with YoloDetector.kt; do NOT rename without updating app
IS_WINDOWS = platform.system() == "Windows"


def ensure_package(name: str) -> None:
    try:
        __import__(name)
    except ImportError:
        print(f"Installing {name}...")
        subprocess.check_call([sys.executable, "-m", "pip", "install", name])


def export_direct() -> Path:
    """Direct TFLite export via ultralytics (Linux/macOS only)."""
    from ultralytics import YOLO

    print("Downloading YOLOv8n weights...")
    model = YOLO("yolov8n.pt")

    print("Exporting to TFLite (float32)...")
    export_path = model.export(format="tflite", imgsz=640)

    tflite_src = Path(export_path)
    if tflite_src.is_dir():
        candidates = list(tflite_src.glob("*.tflite"))
        if not candidates:
            raise FileNotFoundError(f"No .tflite files found in {tflite_src}")
        tflite_src = max(candidates, key=lambda p: p.stat().st_size)

    return tflite_src


def export_via_onnx() -> Path:
    """ONNX → TFLite conversion (works on Windows)."""
    ensure_package("ultralytics")
    ensure_package("onnx2tf")

    from ultralytics import YOLO

    print("Downloading YOLOv8n weights...")
    model = YOLO("yolov8n.pt")

    with tempfile.TemporaryDirectory() as tmpdir:
        tmpdir = Path(tmpdir)

        print("Exporting to ONNX...")
        onnx_path = model.export(format="onnx", imgsz=640)
        onnx_file = Path(onnx_path)
        if not onnx_file.exists():
            onnx_file = tmpdir / "yolov8n.onnx"
            shutil.copy2(Path("yolov8n.onnx"), onnx_file)

        print("Converting ONNX → TFLite via onnx2tf...")
        output_dir = tmpdir / "tflite_output"
        subprocess.check_call([
            sys.executable, "-m", "onnx2tf",
            "-i", str(onnx_file),
            "-o", str(output_dir),
            "-osd", "-cotof",
        ])

        tflite_candidates = list(output_dir.glob("*.tflite"))
        if not tflite_candidates:
            raise FileNotFoundError(f"No .tflite files produced by onnx2tf in {output_dir}")

        # Prefer float32 over float16
        float32 = [f for f in tflite_candidates if "float32" in f.name]
        if float32:
            return float32[0]
        return max(tflite_candidates, key=lambda p: p.stat().st_size)


def main() -> None:
    import argparse
    parser = argparse.ArgumentParser(description="Export STOCK COCO YOLOv8n (NOT waste model)")
    parser.add_argument("--output-name", default="yolov8n",
                        help="Output stem (default yolov8n = app COCO-fallback contract)")
    cli = parser.parse_args()
    model_filename = f"{cli.output_name}.tflite"
    print("WARNING: exporting STOCK COCO-80 weights (generic detector).")
    print("This is NOT the 15-class WasegMul waste model. For waste, use train_waste_yolo.py.")
    try:
        if IS_WINDOWS:
            tflite_src = export_via_onnx()
        else:
            tflite_src = export_direct()
    except Exception as e:
        print(f"ERROR: Export failed: {e}")
        sys.exit(1)

    dest = ASSETS_DIR / model_filename
    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    shutil.copy2(tflite_src, dest)
    size_mb = dest.stat().st_size / (1024 * 1024)
    print(f"Copied {tflite_src.name} -> {dest} ({size_mb:.2f} MB)")
    print("Done! Build the Android app to use the YOLO model.")


if __name__ == "__main__":
    main()
