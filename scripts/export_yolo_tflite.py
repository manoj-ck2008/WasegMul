#!/usr/bin/env python3
"""Export YOLOv8n to TFLite for WasegMul Android app.

Requirements:
    pip install ultralytics

Usage (Linux/macOS/WSL only - TFLite export not supported on Windows):
    python scripts/export_yolo_tflite.py

Output:
    app/src/main/assets/yolov8n.tflite
"""
import subprocess
import sys
import shutil
from pathlib import Path

ASSETS_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets"
MODEL_FILENAME = "yolov8n.tflite"


def main() -> None:
    try:
        from ultralytics import YOLO
    except ImportError:
        print("Installing ultralytics...")
        subprocess.check_call([sys.executable, "-m", "pip", "install", "ultralytics"])
        from ultralytics import YOLO

    print("Downloading YOLOv8n weights...")
    model = YOLO("yolov8n.pt")

    print("Exporting to TFLite (float16)...")
    export_path = model.export(format="tflite", imgsz=640)

    tflite_src = Path(export_path)
    if tflite_src.is_dir():
        candidates = list(tflite_src.glob("*.tflite"))
        if not candidates:
            print(f"ERROR: No .tflite files found in {tflite_src}")
            sys.exit(1)
        tflite_src = max(candidates, key=lambda p: p.stat().st_size)

    dest = ASSETS_DIR / MODEL_FILENAME
    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    shutil.copy2(tflite_src, dest)
    size_mb = dest.stat().st_size / (1024 * 1024)
    print(f"Copied {tflite_src.name} -> {dest} ({size_mb:.2f} MB)")
    print("Done! Build the Android app to use the YOLO model.")


if __name__ == "__main__":
    main()
