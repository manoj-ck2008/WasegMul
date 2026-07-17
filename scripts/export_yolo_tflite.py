#!/usr/bin/env python3
"""Export YOLOv8n to TFLite for WasegMul Android app.

Requirements:
    pip install ultralytics onnx2tf

Usage:
    python scripts/export_yolo_tflite.py

On Linux/macOS: exports directly via ultralytics.
On Windows: exports to ONNX first, then converts via onnx2tf.

Output:
    app/src/main/assets/yolov8n.tflite
"""
import platform
import subprocess
import sys
import shutil
import tempfile
from pathlib import Path

ASSETS_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets"
MODEL_FILENAME = "yolov8n.tflite"
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
    try:
        if IS_WINDOWS:
            tflite_src = export_via_onnx()
        else:
            tflite_src = export_direct()
    except Exception as e:
        print(f"ERROR: Export failed: {e}")
        sys.exit(1)

    dest = ASSETS_DIR / MODEL_FILENAME
    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    shutil.copy2(tflite_src, dest)
    size_mb = dest.stat().st_size / (1024 * 1024)
    print(f"Copied {tflite_src.name} -> {dest} ({size_mb:.2f} MB)")
    print("Done! Build the Android app to use the YOLO model.")


if __name__ == "__main__":
    main()
