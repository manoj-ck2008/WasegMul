#!/usr/bin/env python3
"""Train a waste-specific YOLO11n model for WasegMul.

YOLO11n advantages over YOLOv8n:
  - Faster inference
  - Better AP (mAP@50: 39.5 vs 37.3 on COCO)
  - Native TFLite export via ultralytics
  - Actively maintained by Ultralytics

License: AGPL-3.0 (Ultralytics)
  - Free for personal/research use
  - Requires Enterprise License for closed-source commercial use
  - Alternative: use YOLOX-S (Apache 2.0) for commercial freedom

Requirements:
    pip install -r scripts/requirements-train.txt
    # (pins torch, ultralytics, onnx2tf, tensorflow, opencv-headless,
    #  pyyaml, pycocotools; see that file for versions + PYTHONHASHSEED note)

Determinism:
    - Set PYTHONHASHSEED=0 in your shell for reproducible splits/hashing.
    - This script forces seed=42 for ultralytics + random/numpy/torch seeds,
      and sets torch.backends.cudnn.benchmark=False (no autotuner nondeterminism).
      TF32 on Ampere+ may still add tiny numeric variance; that is expected.
    - DataLoader workers are seeded via torch (workers>0 still deterministic
      given the same seed; use --workers 0 for strictest reproducibility).

Kaggle quota (free tier):
    - Defaults are 300 epochs / patience 50, but Kaggle free sessions time out
      (~9h) and may not finish 300 epochs on T4. Early stopping (patience)
      usually stops sooner; use --epochs 50 --patience 15 for a quick
      quota-safe smoke run, and --resume <last.pt> to continue across sessions.

Usage:
    # Step 1: Prepare dataset
    python scripts/prepare_dataset.py

    # Step 2: Train (requires dataset in datasets/waste-yolo/)
    python scripts/train_waste_yolo.py --epochs 100 --batch 16

    # Step 3: Export to TFLite
    python scripts/export_waste_model_tflite.py

Output:
    runs/train/waste-yolo11n/weights/best.pt   ← best PyTorch weights
    app/src/main/assets/waste_yolo11n.tflite   ← TFLite for Android
"""

import argparse
import os
import random
import subprocess
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent
DATASET_CONFIG = SCRIPT_DIR / "waste_dataset.yaml"
ASSETS_DIR = PROJECT_ROOT / "app" / "src" / "main" / "assets"


def check_dependencies():
    """Verify required packages are installed."""
    missing = []
    try:
        import torch
        print(f"PyTorch: {torch.__version__}")
        print(f"CUDA available: {torch.cuda.is_available()}")
        if torch.cuda.is_available():
            print(f"GPU: {torch.cuda.get_device_name(0)}")
    except ImportError:
        missing.append("torch")

    try:
        import ultralytics
        print(f"Ultralytics: {ultralytics.__version__}")
    except ImportError:
        missing.append("ultralytics")

    if missing:
        print(f"\nMissing packages: {', '.join(missing)}")
        print(f"Install: pip install {' '.join(missing)}")
        return False
    return True


def train_yolo11n(args):
    """Train YOLO11n on waste dataset.
    
    YOLO11n advantages:
    - Faster inference than YOLOv8n
    - Better accuracy (mAP@50: 39.5 vs 37.3 on COCO)
    - Native TFLite export
    - Actively maintained
    """
    import torch
    # Deterministic flags (see module docstring). TF32 stays enabled where
    # beneficial; benchmark=False avoids autotuner nondeterminism.
    random.seed(args.seed)
    try:
        import numpy as np
        np.random.seed(args.seed)
    except ImportError:
        pass
    torch.manual_seed(args.seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(args.seed)
        torch.backends.cudnn.benchmark = False
        torch.backends.cudnn.deterministic = False  # keep perf; benchmark=False is the documented guarantee
    if os.environ.get("PYTHONHASHSEED") != "0":
        print("NOTE: for fully reproducible splits set PYTHONHASHSEED=0 in your shell.")
    from ultralytics import YOLO

    print("=" * 60)
    print("Training YOLO11n for waste classification")
    print("=" * 60)
    if args.resume:
        print(f"Resuming from checkpoint: {args.resume}")

    # YOLO11n weights will be auto-downloaded by ultralytics
    model = YOLO(args.resume if args.resume else "yolo11n.pt")

    results = model.train(
        data=str(DATASET_CONFIG),
        epochs=args.epochs,
        imgsz=args.img_size,
        batch=args.batch,
        name="waste-yolo11n",
        patience=args.patience,
        save=True,
        save_period=25,
        plots=True,
        device=args.device,
        workers=args.workers,
        lr0=args.lr,
        lrf=0.01,
        momentum=0.937,
        weight_decay=0.0005,
        warmup_epochs=5.0,
        warmup_momentum=0.8,
        warmup_bias_lr=0.1,
        close_mosaic=15,
        amp=True,
        cache=args.cache,
        exist_ok=True,
        pretrained=True,
        optimizer="AdamW",
        verbose=True,
        seed=args.seed,
        cos_lr=True,
        label_smoothing=0.1,
        dropout=0.1,
    )

    return results


def export_to_tflite(weights_path: Path, output_name: str = "waste_yolo11n"):
    """Export trained weights to TFLite (float32) + always write labels file."""
    from ultralytics import YOLO

    print(f"\nExporting {weights_path} to TFLite...")
    model = YOLO(str(weights_path))

    export_path = model.export(format="tflite", imgsz=640)
    tflite_src = Path(export_path)

    if tflite_src.is_dir():
        candidates = list(tflite_src.glob("*.tflite"))
        if not candidates:
            raise FileNotFoundError(f"No .tflite files in {tflite_src}")
        tflite_src = max(candidates, key=lambda p: p.stat().st_size)

    dest = ASSETS_DIR / f"{output_name}.tflite"
    import shutil
    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    shutil.copy2(tflite_src, dest)
    size_mb = dest.stat().st_size / (1024 * 1024)
    print(f"Exported: {dest} ({size_mb:.2f} MB)")
    # Always write labels alongside the .tflite so the app never falls back
    # to stale/COCO labels. Single source: scripts/waste_dataset.yaml names.
    write_labels_file(output_name)
    return dest


def write_labels_file(output_name: str = "waste_yolo11n") -> Path:
    """Write <output_name>_classes.txt next to the exported TFLite.

    Single source: scripts/waste_dataset.yaml `names` (mirrors
    taxonomy.yaml yolo_classes). Never ship a .tflite without its labels.
    """
    import yaml
    config_path = SCRIPT_DIR / "waste_dataset.yaml"
    with open(config_path, encoding="utf-8") as f:
        config = yaml.safe_load(f)
    labels = list(config["names"].values())
    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    labels_file = ASSETS_DIR / f"{output_name}_classes.txt"
    with open(labels_file, "w", encoding="utf-8") as f:
        f.write("\n".join(labels) + "\n")
    print(f"Wrote labels: {labels_file} ({len(labels)} classes)")
    return labels_file


def main():
    parser = argparse.ArgumentParser(description="Train waste YOLO model")
    parser.add_argument("--epochs", type=int, default=300, help="Training epochs (default: 300, early stops via patience; Kaggle free quota: use --epochs 50 for smoke runs)")
    parser.add_argument("--batch", type=int, default=-1, help="Batch size (-1 = auto, uses max VRAM)")
    parser.add_argument("--img-size", type=int, default=640, help="Image size")
    parser.add_argument("--patience", type=int, default=50, help="Early stopping patience (Kaggle quota: --patience 15 for smoke runs)")
    parser.add_argument("--device", default="auto", help="Device (auto/cpu/cuda/0)")
    parser.add_argument("--workers", type=int, default=8, help="DataLoader workers (seeded; use 0 for strictest reproducibility)")
    parser.add_argument("--lr", type=float, default=0.001, help="Initial learning rate")
    parser.add_argument("--cache", action="store_true", help="Cache images in memory (OPT-IN: can OOM on T4/laptops; default off)")
    parser.add_argument("--resume", type=str, default=os.environ.get("WASEGMUL_RESUME", ""),
                        help="Resume from checkpoint .pt (or WASEGMUL_RESUME env). Enables Kaggle session-to-session continuation.")
    parser.add_argument("--seed", type=int, default=int(os.environ.get("PYTHON_SEED", "42")), help="Random seed (or PYTHON_SEED env)")
    parser.add_argument("--export-only", type=str, help="Skip training, export this .pt to TFLite")
    parser.add_argument("--output-name", type=str, default="waste_yolo11n",
                        help="Output filename (without .tflite)")
    args = parser.parse_args()

    # Export-only mode (still writes labels file via export_to_tflite)
    if args.export_only:
        export_to_tflite(Path(args.export_only), args.output_name)
        return

    if not DATASET_CONFIG.exists():
        print(f"Dataset config not found: {DATASET_CONFIG}")
        print("Run: python scripts/prepare_dataset.py")
        sys.exit(1)

    if not check_dependencies():
        sys.exit(1)

    results = train_yolo11n(args)

    # Auto-export best weights to TFLite
    best_weights = PROJECT_ROOT / "runs" / "train" / "waste-yolo11n" / "weights" / "best.pt"
    if best_weights.exists():
        export_to_tflite(best_weights, args.output_name)
        print("\n✓ Training complete. Model exported to assets/")
    else:
        print(f"\n⚠ Best weights not found at {best_weights}")
        print("  Export manually: python scripts/export_waste_model_tflite.py")


if __name__ == "__main__":
    main()
