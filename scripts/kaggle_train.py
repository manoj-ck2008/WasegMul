#!/usr/bin/env python3
"""
╔══════════════════════════════════════════════════════════════════════════════╗
║  WasegMul: Kaggle Training Pipeline                                        ║
║  Waste-Specific YOLO11n for On-Device Detection                            ║
║                                                                            ║
║  Two-stage architecture:                                                   ║
║    Stage 1: YOLO11n  → detects WHERE objects are (15 visual classes)       ║
║    Stage 2: EfficientNet → classifies WHAT each crop is (30 subclasses)    ║
║                                                                            ║
║  Taxonomy is defined in taxonomy.yaml (single source of truth).            ║
║  Both this script and the Android app read from the same taxonomy.         ║
║  To add new datasets or classes, update ONLY taxonomy.yaml.                ║
║                                                                            ║
║  Setup:                                                                    ║
║    1. Add Input datasets:                                                  ║
║       - /kaggle/input/datasets/kneroma/tacotrashdataset                    ║
║       - /kaggle/input/datasets/manojkari/taco-dataset1                     ║
║    2. Enable GPU: Settings → Accelerator → GPU T4 x2                       ║
║    3. Run all cells                                                        ║
╚══════════════════════════════════════════════════════════════════════════════╝
"""

# ═══════════════════════════════════════════════════════════════════════════════
# SECTION 1: INSTALL DEPENDENCIES
# ═══════════════════════════════════════════════════════════════════════════════

import subprocess
import sys
from typing import Optional, List, Dict


def _install(package: str) -> None:
    """Install a pip package silently."""
    subprocess.check_call([sys.executable, "-m", "pip", "install", "-q", package])


def _ensure_dependencies() -> None:
    print("Installing dependencies...")
    for _pkg in ["ultralytics", "pyyaml", "opencv-python-headless"]:
        try:
            __import__(_pkg.replace("-", "_").split("[")[0])
        except ImportError:
            _install(_pkg)
    print("Done.\n")


if __name__ == "__main__" or "ipykernel" in sys.modules:
    _ensure_dependencies()


# ═══════════════════════════════════════════════════════════════════════════════
# SECTION 2: CONFIGURATION
#
# All taxonomy definitions are loaded from taxonomy.yaml (single source of truth).
# Both this training script AND the Android app read from the same taxonomy.
# To add new datasets or classes, update ONLY taxonomy.yaml.
#
# Design principles:
#   YOLO classes group objects by VISUAL APPEARANCE (shape, texture, silhouette)
#   EfficientNet classes group objects by SEMANTIC MEANING (material, function)
#   Ambiguous categories are EXCLUDED rather than forced into wrong classes
#
# ═══════════════════════════════════════════════════════════════════════════════

import os
import yaml
from pathlib import Path

# ─── Paths ────────────────────────────────────────────────────────────────────
# Overrides via env (keep defaults for Kaggle; document local reruns):
#   WASEGMUL_KAGGLE_INPUT, WASEGMUL_OUTPUT_DIR, WASEGMUL_DATASET_DIR,
#   WASEGMUL_RUNS_DIR, WASEGMUL_TAXONOMY, WASEGMUL_EPOCHS, WASEGMUL_PATIENCE,
#   WASEGMUL_BATCH, WASEGMUL_CACHE (0/1, default 0: opt-in, OOM risk on T4),
#   WASEGMUL_RESUME (.pt checkpoint for session-to-session resume),
#   WASEGMUL_SEED, PYTHONHASHSEED=0 (shell-level determinism).
# Local rerun example:
#   PYTHONHASHSEED=0 WASEGMUL_EPOCHS=50 WASEGMUL_PATIENCE=15 python scripts/kaggle_train.py

KAGGLE_INPUT = Path(os.environ.get("WASEGMUL_KAGGLE_INPUT", "/kaggle/input"))
OUTPUT_DIR = Path(os.environ.get("WASEGMUL_OUTPUT_DIR", "./waste-yolo-output"))
DATASET_DIR = Path(os.environ.get("WASEGMUL_DATASET_DIR", "./datasets/waste-yolo"))
RUNS_DIR = Path(os.environ.get("WASEGMUL_RUNS_DIR", "./runs"))
TACO_DIR: Optional[Path] = None  # Auto-detected in Section 4
TAXONOMY_PATH = Path(os.environ.get("WASEGMUL_TAXONOMY", str(Path(__file__).resolve().parent.parent / "taxonomy.yaml")))

# ─── Load Taxonomy ────────────────────────────────────────────────────────────

def _load_taxonomy(path: Path) -> dict:
    """Load taxonomy.yaml — the SINGLE source of truth.

    No built-in fallback dictionary: silently training on a forked taxonomy
    is worse than failing. If taxonomy.yaml is missing (e.g. Kaggle kernel
    without the file attached), this raises a clear error telling the user
    to attach/copy taxonomy.yaml. See also: Other plastic include-vs-exclude
    and detection_to_subclass rationale are documented in taxonomy.yaml.
    """
    candidates = [
        path,
        Path("taxonomy.yaml"),
        Path("./taxonomy.yaml"),
        Path("/kaggle/working/taxonomy.yaml"),
        Path(__file__).resolve().parent / "taxonomy.yaml",
        Path(__file__).resolve().parent.parent / "taxonomy.yaml",
    ]
    for candidate in candidates:
        if candidate.exists():
            with open(candidate, "r", encoding="utf-8") as f:
                return yaml.safe_load(f)

    if KAGGLE_INPUT.exists():
        for candidate in KAGGLE_INPUT.rglob("taxonomy.yaml"):
            with open(candidate, "r", encoding="utf-8") as f:
                return yaml.safe_load(f)

    raise FileNotFoundError(
        "taxonomy.yaml not found. taxonomy.yaml is the single source of truth — "
        "the training script no longer ships a built-in fallback dictionary "
        "(it drifted: omitted blister/foam/straws/rings and disagreed on "
        "Other-plastic include-vs-exclude). Fix: copy taxonomy.yaml next to "
        "kaggle_train.py (the Kaggle pipeline stages it automatically), or set "
        "WASEGMUL_TAXONOMY=/path/to/taxonomy.yaml."
    )

_taxonomy = _load_taxonomy(TAXONOMY_PATH)

# ─── YOLO Detection Classes ──────────────────────────────────────────────────

YOLO_CLASSES: list[str] = [
    _taxonomy["yolo_classes"][i]
    for i in range(len(_taxonomy["yolo_classes"]))
]
NUM_CLASSES: int = len(YOLO_CLASSES)

# ─── TACO → YOLO Mapping ──────────────────────────────────────────────────────
# Maps every TACO category name to its YOLO class index.
# Excluded categories are mapped to -1.

TACO_TO_YOLO: dict[str, int] = {}
EXCLUDED_CATEGORIES: set[str] = set()

for _taco_name, _info in _taxonomy["taco_mapping"].items():
    if _info.get("excluded", False):
        TACO_TO_YOLO[_taco_name] = -1
        EXCLUDED_CATEGORIES.add(_taco_name)
    else:
        _det = _info["detector"]
        _cls_id = next(k for k, v in _taxonomy["yolo_classes"].items() if v == _det)
        TACO_TO_YOLO[_taco_name] = _cls_id

# ─── EfficientNet Classification Classes ──────────────────────────────────────

EFFICIENTNET_CLASSES: list[str] = [
    _taxonomy["efficientnet_classes"][i]
    for i in range(len(_taxonomy["efficientnet_classes"]))
]

# ─── Training Hyperparameters ────────────────────────────────────────────────
# Quota note (Kaggle free tier): 300 epochs / patience 50 rarely finishes in
# one ~9h T4 session. Early stopping usually exits sooner; for smoke runs set
# WASEGMUL_EPOCHS=50 WASEGMUL_PATIENCE=15, and resume across sessions with
# WASEGMUL_RESUME=<last.pt>. cache= is OPT-IN (OOM risk on T4 — see below).

def _env_int(name: str, default: int) -> int:
    try:
        return int(os.environ.get(name, str(default)))
    except ValueError:
        return default


CONFIG: dict = {
    # Model
    "model": os.environ.get("WASEGMUL_MODEL", "yolo11n.pt"),
    "resume": os.environ.get("WASEGMUL_RESUME", ""),
    "epochs": _env_int("WASEGMUL_EPOCHS", 300),
    "batch": _env_int("WASEGMUL_BATCH", -1) if "WASEGMUL_BATCH" in os.environ else -1,
    "img_size": 640,
    "patience": _env_int("WASEGMUL_PATIENCE", 50),           # Early stopping patience

    # Optimizer
    "lr0": 0.001,
    "lrf": 0.01,
    "momentum": 0.937,
    "weight_decay": 0.0005,
    "warmup_epochs": 5.0,
    "warmup_momentum": 0.8,
    "warmup_bias_lr": 0.1,

    # Augmentation (Mobile Camera Tuned)
    "close_mosaic": 15,
    "dropout": 0.1,
    "degrees": 10.0,
    "translate": 0.1,
    "scale": 0.5,
    "shear": 2.0,
    "perspective": 0.0005,
    "flipud": 0.2,
    "fliplr": 0.5,
    "mosaic": 1.0,
    "mixup": 0.15,
    "copy_paste": 0.1,

    # System
    "device": 0,
    "workers": 2,
    "amp": True,
    "seed": _env_int("WASEGMUL_SEED", 42),
    # cache=True pins decoded images in RAM — OOM-kills T4/laptops on TACO.
    # OPT-IN only: set WASEGMUL_CACHE=1 if you have headroom, else leave off.
    "cache": os.environ.get("WASEGMUL_CACHE", "0") == "1",

    # Dataset split
    "train_ratio": 0.8,
    "val_ratio": 0.1,
}

# ─── Taxonomy Summary ─────────────────────────────────────────────────────────
print(f"Taxonomy loaded: {TAXONOMY_PATH}")
print(f"  YOLO classes:        {NUM_CLASSES}")
print(f"  EfficientNet classes: {len(EFFICIENTNET_CLASSES)}")
print(f"  TACO categories:     {len(TACO_TO_YOLO)}")
print(f"  Excluded:            {len(EXCLUDED_CATEGORIES)}")
print()


# ═══════════════════════════════════════════════════════════════════════════════
# SECTION 3: ENVIRONMENT CHECK
# ═══════════════════════════════════════════════════════════════════════════════

import torch

# Determinism: PYTHONHASHSEED=0 must be set in the shell (documented in
# requirements-train.txt). Workers inherit torch seeds; cudnn.benchmark stays
# False so convolution autotuning cannot introduce run-to-run variance.
if os.environ.get("PYTHONHASHSEED") != "0":
    print("NOTE: set PYTHONHASHSEED=0 in the environment for fully reproducible splits.")
import random as _random
_random.seed(CONFIG["seed"])
try:
    import numpy as _np
    _np.random.seed(CONFIG["seed"])
except ImportError:
    pass
torch.manual_seed(CONFIG["seed"])
if torch.cuda.is_available():
    torch.cuda.manual_seed_all(CONFIG["seed"])

print("=" * 60)
print("Environment")
print("=" * 60)
print(f"  PyTorch:    {torch.__version__}")
print(f"  CUDA:       {torch.cuda.is_available()}")

if torch.cuda.is_available():
    gpu_count = torch.cuda.device_count()
    gpu_name = torch.cuda.get_device_name(0)
    vram_gb = torch.cuda.get_device_properties(0).total_memory / 1e9
    cap = torch.cuda.get_device_capability(0)
    capability = cap[0] * 10 + cap[1]

    print(f"  GPU(s):     {gpu_count}x {gpu_name}")
    print(f"  VRAM:       {vram_gb:.1f} GB per device")
    print(f"  Compute:    sm_{capability}")

    # Modern accelerator hardware optimizations (Ampere sm_80, Ada sm_89, Hopper H100 sm_90, Blackwell sm_100+)
    if capability >= 80:
        torch.backends.cuda.matmul.allow_tf32 = True
        torch.backends.cudnn.allow_tf32 = True
        print("  Accelerations: TensorFloat-32 (TF32) enabled for matmul and cuDNN")

    # Deterministic dataloader behavior: benchmark=False (no autotuner
    # nondeterminism). TF32 may still add tiny numeric variance on Ampere+;
    # that is expected and documented, not a bug.
    torch.backends.cudnn.benchmark = False

    # Check CUDA compute capability compatibility
    if capability < 70:
        print(f"\n  WARNING: GPU sm_{capability} incompatible with PyTorch {torch.__version__}")
        print(f"  PyTorch 2.10+ requires sm_70+ (T4, P100, A100, H100, L4, Blackwell, etc.)")
        print(f"  Falling back to CPU...")
        CONFIG["device"] = "cpu"
        CONFIG["batch"] = 8
    elif vram_gb >= 60:
        # Ultra-capacity accelerators (NVIDIA H100 80GB, Blackwell B200 192GB, A100 80GB)
        CONFIG["device"] = [i for i in range(gpu_count)] if gpu_count > 1 else 0
        CONFIG["batch"] = 64 * max(1, gpu_count)
        CONFIG["workers"] = min(8, os.cpu_count() or 4)
        CONFIG["amp"] = True
        print(f"  High-Capacity Profile (H100/Blackwell/A100): Batch {CONFIG['batch']}, Workers {CONFIG['workers']}, AMP FP16/BF16")
    elif vram_gb >= 24:
        # High VRAM accelerators (RTX 4090/3090, A10G, L4)
        CONFIG["device"] = [i for i in range(gpu_count)] if gpu_count > 1 else 0
        CONFIG["batch"] = 32 * max(1, gpu_count)
        CONFIG["workers"] = min(6, os.cpu_count() or 4)
        print(f"  Workstation Profile: Batch {CONFIG['batch']}, Workers {CONFIG['workers']}")
    elif gpu_count > 1:
        # Multi-GPU (e.g. Kaggle dual T4 x 2)
        CONFIG["device"] = [i for i in range(gpu_count)]
        CONFIG["batch"] = 16 * gpu_count
        CONFIG["workers"] = 2
        print(f"  Multi-GPU Profile: {gpu_count} devices -> Batch {CONFIG['batch']}")
    else:
        # Single standard GPU (e.g. Kaggle T4, P100)
        CONFIG["device"] = 0
        CONFIG["batch"] = 16
        CONFIG["workers"] = 2
        print(f"  Standard Profile: Single device -> Batch {CONFIG['batch']}")
else:
    print("\n  WARNING: No GPU detected!")
    print("  Enable GPU: Settings -> Accelerator -> GPU")
    CONFIG["device"] = "cpu"
    CONFIG["batch"] = 8

try:
    import ultralytics
    print(f"  Ultralytics: {ultralytics.__version__}")
except Exception:
    print("  Ultralytics: not installed (will install)")

print()


# ═══════════════════════════════════════════════════════════════════════════════
# SECTION 4: LOCATE DATASETS
# ═══════════════════════════════════════════════════════════════════════════════

import json
from collections import defaultdict
from typing import Optional

print("=" * 60)
print("Locating Datasets")
print("=" * 60)


def _find_dataset(pattern: str) -> Optional[Path]:
    """Search Kaggle input directories for a file matching the pattern."""
    for p in KAGGLE_INPUT.rglob(pattern):
        return p.parent
    return None


def _find_taco_dir() -> Optional[Path]:
    """Locate the best TACO dataset directory on Kaggle.

    Priority: manojkari (cleaner) > kneroma > any fallback.
    Returns the directory containing annotations.json.
    """
    # Try manojkari first
    manojkari = _find_dataset("annotations.json")
    if manojkari and "manojkari" in str(manojkari).lower():
        print(f"  Found manojkari: {manojkari}")
        return manojkari

    # Try kneroma
    for p in KAGGLE_INPUT.rglob("*.json"):
        if "kneroma" in str(p).lower() and "annotations" in p.name:
            print(f"  Found kneroma: {p.parent}")
            return p.parent

    # Fallback: any annotations.json
    for p in KAGGLE_INPUT.rglob("annotations.json"):
        print(f"  Found dataset: {p.parent}")
        return p.parent

    return None


TACO_DIR = _find_taco_dir()
if TACO_DIR is None:
    print("  No TACO datasets found!")
    print("  Add datasets as Input:")
    print("    - /kaggle/input/datasets/kneroma/tacotrashdataset")
    print("    - /kaggle/input/datasets/manojkari/taco-dataset1")
else:
    print(f"  Using: {TACO_DIR}")

print()


# ═══════════════════════════════════════════════════════════════════════════════
# SECTION 5: DATASET PREPARATION
#
# Pipeline: Load → Dedup → Filter → Stratified Split → Write → Validate
#
# ═══════════════════════════════════════════════════════════════════════════════

import hashlib
import random
import shutil

print("=" * 60)
print("Preparing Dataset")
print("=" * 60)


def _file_hash(path: Path) -> str:
    """Compute MD5 hash of a file for deduplication."""
    h = hashlib.md5()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def _find_image(images_dir: Path, filename: str) -> Optional[Path]:
    """Locate an image file within TACO's batch directory structure.

    TACO stores images in data/batch_N/ subdirectories.
    """
    # Direct match
    direct = images_dir / filename
    if direct.exists():
        return direct

    # Check batch subdirectories
    if images_dir.exists():
        for d in images_dir.iterdir():
            if d.is_dir():
                candidate = d / filename
                if candidate.exists():
                    return candidate

    # Recursive fallback
    for f in images_dir.rglob(filename):
        return f

    return None


def _coco_to_yolo(bbox: list[float], img_w: int, img_h: int) -> tuple[float, float, float, float]:
    """Convert COCO [x, y, w, h] to YOLO [cx, cy, w, h] normalized to [0, 1]."""
    x, y, bw, bh = bbox
    return (
        max(0.0, min(1.0, (x + bw / 2) / img_w)),
        max(0.0, min(1.0, (y + bh / 2) / img_h)),
        max(0.0, min(1.0, bw / img_w)),
        max(0.0, min(1.0, bh / img_h)),
    )


def _load_taco(taco_dir: Path) -> list[dict]:
    """Load TACO COCO annotations and convert to YOLO format records.

    Returns a list of dicts, each containing:
        src, path, name, lines (YOLO format), n_obj, hash
    """
    # Locate annotations.json
    ann_file: Optional[Path] = None
    for candidate in [
        taco_dir / "data" / "annotations.json",
        taco_dir / "annotations.json",
        taco_dir / "TACO" / "data" / "annotations.json",
    ]:
        if candidate.exists():
            ann_file = candidate
            break
    if ann_file is None:
        for f in taco_dir.rglob("annotations.json"):
            ann_file = f
            break

    if ann_file is None:
        print("  No annotations.json found")
        return []

    print(f"  Annotations: {ann_file}")

    with open(ann_file) as f:
        data = json.load(f)

    images = {img["id"]: img for img in data["images"]}
    cats = {cat["id"]: cat["name"] for cat in data["categories"]}

    ann_by_img: dict[int, list] = defaultdict(list)
    for ann in data["annotations"]:
        if not ann.get("iscrowd", 0):
            ann_by_img[ann["image_id"]].append(ann)

    # Resolve the images directory
    images_dir = taco_dir / "data"
    if not images_dir.exists():
        images_dir = taco_dir

    records: list[dict] = []
    unmapped_categories: set[str] = set()
    excluded_count: int = 0
    tiny_count: int = 0

    for img_id, img in images.items():
        img_file = _find_image(images_dir, img["file_name"])
        if img_file is None:
            continue

        iw, ih = img["width"], img["height"]
        lines: list[str] = []

        for ann in ann_by_img[img_id]:
            cat_name = cats.get(ann["category_id"], "")

            # Skip excluded categories (e.g., Unlabeled litter)
            if cat_name in EXCLUDED_CATEGORIES:
                excluded_count += 1
                continue

            # Map to YOLO class
            cls = TACO_TO_YOLO.get(cat_name, -1)
            if cls == -1:
                unmapped_categories.add(cat_name)
                continue

            cx, cy, nw, nh = _coco_to_yolo(ann["bbox"], iw, ih)

            # Filter out annotations that are too small to be meaningful
            if nw < 0.005 or nh < 0.005:
                tiny_count += 1
                continue

            lines.append(f"{cls} {cx:.6f} {cy:.6f} {nw:.6f} {nh:.6f}")

        if lines:
            records.append({
                "src": "taco",
                "path": img_file,
                "name": img_file.name,
                "lines": lines,
                "n_obj": len(lines),
                "hash": _file_hash(img_file),
            })

    # Report skipped annotations
    if unmapped_categories:
        print(f"  WARNING: Unmapped categories (falling to other): {unmapped_categories}")
    if excluded_count:
        print(f"  Excluded {excluded_count} annotations (ambiguous categories)")
    if tiny_count:
        print(f"  Skipped {tiny_count} annotations (too small: <0.5% of image)")

    print(f"  Loaded {len(records)} images from TACO")
    return records


def _deduplicate(records: list[dict]) -> list[dict]:
    """Remove duplicates: MD5 exact always; pHash near-dedup when available.

    LIMITATION (documented): MD5 alone misses burst-frame near-duplicates.
    If Pillow+imagehash are installed, a pHash stage (Hamming<=5) runs;
    otherwise a filename-group + resolution fallback runs and logs its drops.
    Install with: pip install pillow imagehash
    """
    seen: set[str] = set()
    unique: list[dict] = []
    dupes = 0

    for r in records:
        if r["hash"] not in seen:
            seen.add(r["hash"])
            unique.append(r)
        else:
            dupes += 1

    if dupes:
        print(f"  Removed {dupes} duplicate images (MD5 exact)")

    try:
        from PIL import Image
        import imagehash
        _has_phash = True
    except ImportError:
        _has_phash = False

    if _has_phash:
        seen_ph: list = []
        kept: list[dict] = []
        near = 0
        for r in unique:
            try:
                with Image.open(r["path"]) as im:
                    ph = imagehash.phash(im)
            except Exception:
                kept.append(r)
                continue
            if any((ph - h) <= 5 for h in seen_ph):
                near += 1
                continue
            seen_ph.append(ph)
            kept.append(r)
        if near:
            print(f"  Removed {near} near-duplicate images (pHash Hamming<=5)")
        return kept

    print("  NOTE: Pillow/imagehash absent — filename-group + resolution fallback "
          "for near-dedup (MD5 exact already applied).")
    import re
    seen_groups: set = set()
    kept = []
    grouped = 0
    for r in unique:
        stem = Path(r.get("name", "")).stem
        prefix = re.sub(r"[_-]?\d+$", "", stem).lower()[:32]
        key = (prefix, r.get("path", ""))
        # Resolution proxy unavailable here (no w/h stored) — group by prefix only.
        if prefix and key in seen_groups:
            grouped += 1
            continue
        seen_groups.add(key)
        kept.append(r)
    if grouped:
        print(f"  Removed {grouped} suspected burst-frame dupes (filename-group fallback)")
    return kept


def _stratified_split(
    records: list[dict],
    train_r: float = 0.8,
    val_r: float = 0.1,
    seed: int = 42,
    allow_rare: bool = False,
) -> tuple[list[dict], list[dict], list[dict]]:
    """Stratified split ensuring every class appears in all splits.

    Minimum-val enforcement: classes with <3 images cannot cover
    train/val/test. FAILS loudly unless WASEGMUL_ALLOW_RARE=1, in which case
    rare singles are redistributed into train and logged (never silent).
    """
    random.seed(seed)

    # Group by primary class (most frequent class in each image)
    groups: dict[int, list[dict]] = defaultdict(list)
    for r in records:
        counts: dict[int, int] = defaultdict(int)
        for line in r["lines"]:
            cls_id = int(line.split()[0])
            counts[cls_id] += 1
        primary = max(counts, key=counts.get) if counts else NUM_CLASSES - 1
        groups[primary].append(r)

    rare = {c: len(g) for c, g in groups.items() if len(g) < 3}
    if rare:
        detail = ", ".join(f"{YOLO_CLASSES[c]} (n={n})" for c, n in sorted(rare.items()))
        if not allow_rare:
            raise SystemExit(
                f"STRATIFIED SPLIT BLOCKED: {detail} have <3 images. Collect more "
                f"images or set WASEGMUL_ALLOW_RARE=1 to redistribute rare singles "
                f"into train (logged, not silent)."
            )
        print(f"  WARNING (WASEGMUL_ALLOW_RARE=1): rare groups into train: {detail}")

    train: list[dict] = []
    val: list[dict] = []
    test: list[dict] = []

    for cls_id, group in groups.items():
        random.shuffle(group)
        n = len(group)
        if n < 3 and allow_rare:
            train.extend(group)
            continue
        n_train = int(n * train_r)
        n_val = max(1, int(n * val_r)) if n > 2 else 0

        train.extend(group[:n_train])
        val.extend(group[n_train:n_train + n_val])
        test.extend(group[n_train + n_val:])

    random.shuffle(train)
    random.shuffle(val)
    random.shuffle(test)
    return train, val, test


def _write_split(
    records: list[dict],
    split_name: str,
    output_dir: Path,
    id_counter: list[int],
) -> None:
    """Write a dataset split (images + labels) to disk."""
    img_dir = output_dir / split_name / "images"
    lbl_dir = output_dir / split_name / "labels"
    img_dir.mkdir(parents=True, exist_ok=True)
    lbl_dir.mkdir(parents=True, exist_ok=True)

    for r in records:
        id_counter[0] += 1
        ext = r["path"].suffix
        new_name = f"{r['src']}_{id_counter[0]:06d}{ext}"

        shutil.copy2(r["path"], img_dir / new_name)
        with open(lbl_dir / (Path(new_name).stem + ".txt"), "w") as f:
            f.write("\n".join(r["lines"]) + "\n")


def _compute_class_distribution(records: list[dict]) -> dict[int, int]:
    """Count annotations per class across all records."""
    counts: dict[int, int] = defaultdict(int)
    for r in records:
        for line in r["lines"]:
            cls_id = int(line.split()[0])
            counts[cls_id] += 1
    return dict(counts)


def _verify_split_quality(
    train_records: list[dict],
    val_records: list[dict],
    test_records: list[dict],
) -> None:
    """Verify dataset quality: check class balance, rare classes, split coverage.

    Raises warnings for:
      - Classes with fewer than 50 total annotations
      - Classes that are completely empty (0 annotations)
      - Classes missing from any split (train/val/test)
    """
    all_records = train_records + val_records + test_records
    total_dist = _compute_class_distribution(all_records)
    train_dist = _compute_class_distribution(train_records)
    val_dist = _compute_class_distribution(val_records)
    test_dist = _compute_class_distribution(test_records)

    print("\n  --- Dataset Quality Report ---")
    print(f"  {'Class':20s} {'Total':>6s} {'Train':>6s} {'Val':>5s} {'Test':>5s} {'Status'}")
    print("  " + "-" * 55)

    issues: list[str] = []

    for cls_id in range(NUM_CLASSES):
        name = YOLO_CLASSES[cls_id]
        total = total_dist.get(cls_id, 0)
        tr = train_dist.get(cls_id, 0)
        vl = val_dist.get(cls_id, 0)
        ts = test_dist.get(cls_id, 0)

        # Check for empty classes
        if total == 0:
            status = "EMPTY"
            issues.append(f"  CRITICAL: '{name}' has 0 annotations")
        elif total < 50:
            status = "RARE"
            issues.append(f"  WARNING: '{name}' has only {total} annotations (< 50)")
        else:
            status = "OK"

        # Check split coverage (only if class has annotations)
        if total > 0:
            missing_splits = []
            if tr == 0:
                missing_splits.append("train")
            if vl == 0:
                missing_splits.append("val")
            if ts == 0:
                missing_splits.append("test")
            if missing_splits:
                status = f"MISSING from {', '.join(missing_splits)}"
                issues.append(f"  WARNING: '{name}' not in {', '.join(missing_splits)}")

        bar = "#" * min(30, total // 10)
        print(f"  {name:20s} {total:6d} {tr:6d} {vl:5d} {ts:5d}  {status}  {bar}")

    print()
    if issues:
        print("  ISSUES FOUND:")
        for issue in issues:
            print(issue)
    else:
        print("  All classes OK (>= 50 annotations, present in all splits)")


def _generate_data_yaml(output_dir: Path) -> None:
    """Generate YOLO dataset configuration YAML."""
    import yaml

    data_yaml = {
        "path": str(output_dir.absolute()),
        "train": "train/images",
        "val": "val/images",
        "test": "test/images",
        "nc": NUM_CLASSES,
        "names": {i: name for i, name in enumerate(YOLO_CLASSES)},
    }

    yaml_path = output_dir / "data.yaml"
    with open(yaml_path, "w") as f:
        yaml.dump(data_yaml, f, default_flow_style=False, sort_keys=False)
    print(f"  Generated: {yaml_path}")


# ─── Execute Preparation ──────────────────────────────────────────────────────

DATASET_DIR.mkdir(parents=True, exist_ok=True)

all_records: list[dict] = []
if TACO_DIR is not None:
    all_records = _load_taco(TACO_DIR)

if not all_records:
    print("  No data loaded. Check dataset paths.")
else:
    print(f"\n  Total raw: {len(all_records)}")

    all_records = _deduplicate(all_records)
    print(f"  After dedup: {len(all_records)}")

    # Filter images with zero objects (should not happen, but defensive)
    all_records = [r for r in all_records if r["n_obj"] >= 1]
    print(f"  After filter: {len(all_records)}")

    # Stratified split (rare-class policy via WASEGMUL_ALLOW_RARE=1 to redistribute instead of fail)
    train, val, test = _stratified_split(
        all_records,
        train_r=CONFIG["train_ratio"],
        val_r=CONFIG["val_ratio"],
        seed=CONFIG["seed"],
        allow_rare=os.environ.get("WASEGMUL_ALLOW_RARE", "0") == "1",
    )
    print(f"  Split: {len(train)} train / {len(val)} val / {len(test)} test")

    # Write to disk
    img_id = [0]
    for split_name, split_records in [("train", train), ("val", val), ("test", test)]:
        _write_split(split_records, split_name, DATASET_DIR, img_id)

    # Generate config
    _generate_data_yaml(DATASET_DIR)

    # Quality verification
    _verify_split_quality(train, val, test)

    # Class distribution summary
    dist = _compute_class_distribution(all_records)
    total_annots = sum(dist.values())
    print(f"\n  Total annotations: {total_annots}")
    print(f"  Classes with data: {len(dist)}/{NUM_CLASSES}")

print()


# ═══════════════════════════════════════════════════════════════════════════════
# SECTION 6: TRAINING
# ═══════════════════════════════════════════════════════════════════════════════

print("=" * 60)
print("Training YOLO11n")
print("=" * 60)
print(f"  Model:     {CONFIG['model']}")
print(f"  Epochs:    {CONFIG['epochs']}")
print(f"  Batch:     {CONFIG['batch']}")
print(f"  Image size: {CONFIG['img_size']}")
print(f"  Device:    {CONFIG['device']}")
print(f"  Patience:  {CONFIG['patience']}")
print(f"  Classes:   {NUM_CLASSES} (visual detection taxonomy)")
print()

from ultralytics import YOLO

_resume = CONFIG.get("resume") or CONFIG["model"]
if CONFIG.get("resume"):
    print(f"  Resuming from checkpoint: {_resume}")
model = YOLO(_resume)

results = model.train(
    data=str(DATASET_DIR / "data.yaml"),
    epochs=CONFIG["epochs"],
    imgsz=CONFIG["img_size"],
    batch=CONFIG["batch"],
    name="waste-yolo11n",
    patience=CONFIG["patience"],
    save=True,
    save_period=25,
    plots=True,
    device=CONFIG["device"],
    workers=CONFIG["workers"],
    lr0=CONFIG["lr0"],
    lrf=CONFIG["lrf"],
    momentum=CONFIG["momentum"],
    weight_decay=CONFIG["weight_decay"],
    warmup_epochs=CONFIG["warmup_epochs"],
    warmup_momentum=CONFIG["warmup_momentum"],
    warmup_bias_lr=CONFIG["warmup_bias_lr"],
    close_mosaic=CONFIG["close_mosaic"],
    amp=CONFIG["amp"],
    cache=CONFIG["cache"],  # OPT-IN via WASEGMUL_CACHE=1; True OOMs T4 on TACO
    exist_ok=True,
    pretrained=True,
    optimizer="AdamW",
    verbose=True,
    seed=CONFIG["seed"],
    cos_lr=True,
    dropout=CONFIG["dropout"],
    degrees=CONFIG["degrees"],
    translate=CONFIG["translate"],
    scale=CONFIG["scale"],
    shear=CONFIG["shear"],
    perspective=CONFIG["perspective"],
    flipud=CONFIG["flipud"],
    fliplr=CONFIG["fliplr"],
    mosaic=CONFIG["mosaic"],
    mixup=CONFIG["mixup"],
    copy_paste=CONFIG["copy_paste"],
)

print("\nTraining complete!")
print()


# ═══════════════════════════════════════════════════════════════════════════════
# SECTION 7: VALIDATION + PLOTS
# ═══════════════════════════════════════════════════════════════════════════════

print("=" * 60)
print("Validation")
print("=" * 60)

best_weights = RUNS_DIR / "train" / "waste-yolo11n" / "weights" / "best.pt"

if best_weights.exists():
    model = YOLO(str(best_weights))

    # Validate on val set: this generates confusion_matrix, PR curve, etc.
    print("\nRunning validation (generating plots)...")
    metrics = model.val(
        data=str(DATASET_DIR / "data.yaml"),
        imgsz=CONFIG["img_size"],
        batch=CONFIG["batch"],
        device=CONFIG["device"],
        workers=CONFIG["workers"],
        verbose=True,
        plots=True,
        save_json=True,
        save_hybrid=False,
        conf=0.25,
        iou=0.6,
        max_det=300,
    )

    print(f"\n  mAP@50:    {metrics.box.map50:.4f}")
    print(f"  mAP@50-95: {metrics.box.map:.4f}")
    print(f"  Precision: {metrics.box.mp:.4f}")
    print(f"  Recall:    {metrics.box.mr:.4f}")

    # Per-class AP with worst-5 highlighting
    if hasattr(metrics.box, "ap50") and metrics.box.ap50 is not None:
        per_class_ap = []
        for i, ap_val in enumerate(metrics.box.ap50):
            if i < NUM_CLASSES:
                per_class_ap.append((YOLO_CLASSES[i], float(ap_val), i))

        # Sort by AP ascending to find worst performers
        per_class_ap.sort(key=lambda x: x[1])
        worst_5 = per_class_ap[:5]

        print("\n  Per-class AP@50 (sorted worst to best):")
        for name, ap_val, _ in per_class_ap:
            bar = "#" * int(ap_val * 30)
            print(f"    {name:20s} {ap_val:.4f}  {bar}")

        print("\n  Worst 5 classes (priority for data collection):")
        for name, ap_val, cls_id in worst_5:
            print(f"    {cls_id:2d} {name:20s} {ap_val:.4f}")

    # Copy plots to output directory
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    runs_plots = RUNS_DIR / "train" / "waste-yolo11n"
    plot_files = [
        "confusion_matrix.png",
        "confusion_matrix_normalized.png",
        "PR_curve.png",
        "P_curve.png",
        "R_curve.png",
        "F1_curve.png",
        "results.png",
        "labels.jpg",
        "labels_correlogram.jpg",
    ]
    print("\n  Copying plots to output:")
    for plot_name in plot_files:
        src = runs_plots / plot_name
        if src.exists():
            shutil.copy2(src, OUTPUT_DIR / plot_name)
            print(f"    {plot_name}")
        else:
            # Some plots may be in a subdirectory
            for found in runs_plots.rglob(plot_name):
                shutil.copy2(found, OUTPUT_DIR / plot_name)
                print(f"    {plot_name}")
                break
else:
    print(f"  Best weights not found at {best_weights}")

print()


# ═══════════════════════════════════════════════════════════════════════════════
# SECTION 8: EXPORT (TFLite, ONNX, TorchScript)
# ═══════════════════════════════════════════════════════════════════════════════

print("=" * 60)
print("Exporting Model")
print("=" * 60)

OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

if best_weights.exists():
    model = YOLO(str(best_weights))

    # ── TFLite Float32 ────────────────────────────────────────────────────────
    print("\n  [1/5] TFLite Float32...")
    try:
        tflite_fp32 = model.export(format="tflite", imgsz=640)
        print(f"    -> {tflite_fp32}")
    except Exception as e:
        print(f"    FAILED: {e}")
        tflite_fp32 = None

    # ── TFLite INT8 ───────────────────────────────────────────────────────────
    # NEVER silently ship broken int8: requires a calibration dataset (data=)
    # and a manual AP regression check. If calibration/export fails, this
    # FAILS with a clear error and leaves tflite_int8=None (FP32 remains the
    # shippable artifact). Before shipping any *_int8.tflite, compare val
    # mAP@50 FP32 vs INT8; reject if drop > ~2pp or any class collapses.
    print("\n  [2/5] TFLite INT8 quantized (requires calibration data)...")
    try:
        tflite_int8 = model.export(
            format="tflite",
            imgsz=640,
            int8=True,
            data=str(DATASET_DIR / "data.yaml"),
        )
        print(f"    -> {tflite_int8}")
        print("    WARNING: INT8 artifact is NOT shippable until you run val on it "
              "and confirm AP regression is acceptable. See note above.")
    except Exception as e:
        print(f"    INT8 EXPORT BLOCKED (not silent): {e}")
        print("    FP32 remains the shippable artifact. Provide a representative "
              "calibration dataset via data= and re-run; do not ship INT8 without it.")
        tflite_int8 = None

    # ── ONNX ──────────────────────────────────────────────────────────────────
    print("\n  [3/5] ONNX...")
    try:
        onnx_path = model.export(format="onnx", imgsz=640, simplify=True)
        print(f"    -> {onnx_path}")
    except Exception as e:
        print(f"    FAILED: {e}")
        onnx_path = None

    # ── TorchScript ───────────────────────────────────────────────────────────
    print("\n  [4/5] TorchScript...")
    try:
        ts_path = model.export(format="torchscript", imgsz=640)
        print(f"    -> {ts_path}")
    except Exception as e:
        print(f"    FAILED: {e}")
        ts_path = None

    # ── Copy outputs ──────────────────────────────────────────────────────────
    print("\n  [5/5] Copying to output directory...")

    # Best weights
    shutil.copy2(best_weights, OUTPUT_DIR / "waste_yolo11n.pt")

    # Label file
    with open(OUTPUT_DIR / "waste_yolo11n_classes.txt", "w") as f:
        f.write("\n".join(YOLO_CLASSES) + "\n")

    # Copy TFLite files
    import glob as glob_mod
    for export_path in [tflite_fp32, tflite_int8]:
        if export_path is None:
            continue
        if os.path.isdir(str(export_path)):
            for tf_file in glob_mod.glob(str(export_path) + "/*.tflite"):
                shutil.copy2(tf_file, OUTPUT_DIR / Path(tf_file).name)
                print(f"    {Path(tf_file).name}")
        else:
            shutil.copy2(str(export_path), OUTPUT_DIR / Path(export_path).name)
            print(f"    {Path(export_path).name}")

    # Copy ONNX
    if onnx_path and os.path.exists(str(onnx_path)):
        shutil.copy2(str(onnx_path), OUTPUT_DIR / "waste_yolo11n.onnx")
        print(f"    waste_yolo11n.onnx")

    # Copy TorchScript
    if ts_path and os.path.exists(str(ts_path)):
        ts_src = Path(ts_path)
        if ts_src.is_dir():
            for ts_file in ts_src.glob("*.torchscript"):
                shutil.copy2(ts_file, OUTPUT_DIR / ts_file.name)
                print(f"    {ts_file.name}")
        else:
            shutil.copy2(ts_src, OUTPUT_DIR / ts_src.name)
            print(f"    {ts_src.name}")

    print(f"\n  Labels: waste_yolo11n_classes.txt ({NUM_CLASSES} classes)")
else:
    print("  Skipped (no best weights)")

print()


# ═══════════════════════════════════════════════════════════════════════════════
# SECTION 9: RESULTS SUMMARY
# ═══════════════════════════════════════════════════════════════════════════════

print("=" * 60)
print("Results Summary")
print("=" * 60)

import csv

results_csv = RUNS_DIR / "train" / "waste-yolo11n" / "results.csv"
if results_csv.exists():
    with open(results_csv) as f:
        reader = csv.DictReader(f)
        rows = list(reader)
        if rows:
            best_map50 = max(float(r.get("metrics/mAP50(B)", 0)) for r in rows)
            best_map = max(float(r.get("metrics/mAP50-95(B)", 0)) for r in rows)
            print(f"\n  Epochs trained:  {len(rows)}/{CONFIG['epochs']}")
            print(f"  Best mAP@50:     {best_map50:.4f}")
            print(f"  Best mAP@50-95:  {best_map:.4f}")
            print(f"  Final box loss:  {rows[-1].get('train/box_loss', 'N/A')}")
            print(f"  Final cls loss:  {rows[-1].get('train/cls_loss', 'N/A')}")

print(f"\n  Output files:")
print(f"    {OUTPUT_DIR / 'waste_yolo11n.pt'}          (PyTorch weights)")
print(f"    {OUTPUT_DIR / 'waste_yolo11n.onnx'}        (ONNX)")
print(f"    {OUTPUT_DIR / 'waste_yolo11n.tflite'}      (TFLite FP32)")
print(f"    {OUTPUT_DIR / 'waste_yolo11n_int8.tflite'} (TFLite INT8)")
print(f"    {OUTPUT_DIR / 'waste_yolo11n_classes.txt'} ({NUM_CLASSES} classes)")
print(f"    {OUTPUT_DIR / 'confusion_matrix.png'}")
print(f"    {OUTPUT_DIR / 'PR_curve.png'}")
print(f"    {OUTPUT_DIR / 'P_curve.png'}")
print(f"    {OUTPUT_DIR / 'R_curve.png'}")
print(f"    {OUTPUT_DIR / 'F1_curve.png'}")
print(f"    {OUTPUT_DIR / 'results.png'}")

print(f"\n  Android integration:")
print(f"    1. Copy TFLite files to app/src/main/assets/")
print(f"    2. Update YoloDetector.kt:")
print(f"       private const val MODEL_FILENAME = \"waste_yolo11n.tflite\"")
print(f"       private const val LABELS_FILENAME = \"waste_yolo11n_classes.txt\"")
print(f"    3. Build: ./gradlew assembleDebug")

print(f"\n  Kaggle download:")
print(f"    Output dir: {OUTPUT_DIR}/")
print(f"    Best weights: {best_weights}")

print()
print("=" * 60)
print("Done!")
print("=" * 60)
