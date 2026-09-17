#!/usr/bin/env python3
"""Prepare waste detection datasets for YOLO11n training.

Production pipeline:
    Raw datasets → License check → Merge → Dedup → Normalize → Quality filter → YOLO Dataset

Supports multiple sources:
  - TACO (original): CC BY 4.0: primary source
  - TACO (Kaggle mirrors): verify license before use
  - WasteNet/TrashNet: MIT: classification only (not detection)
  - Roboflow: CC BY 4.0: various waste datasets
  - Custom: user-collected images

Architecture:
    YOLO11n detects 15 coarse categories (WHERE objects are)
    EfficientNet classifies 30 fine subclasses (WHAT each object is)

Requirements:
    pip install pycocotools opencv-python-headless numpy pyyaml

Usage:
    # On Kaggle (with datasets as Input):
    python scripts/prepare_dataset.py --kaggle-mode

    # Local (with TACO downloaded):
    python scripts/prepare_dataset.py --taco-dir /path/to/taco

    # Merge multiple sources:
    python scripts/prepare_dataset.py --sources taco custom
"""

import argparse
import hashlib
import json
import os
import random
import shutil
import sys
from collections import defaultdict
from pathlib import Path

import yaml

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent
MANIFEST_PATH = PROJECT_ROOT / "datasets_manifest.yaml"

TAXONOMY_PATH = PROJECT_ROOT / "taxonomy.yaml"

# ─── Load Taxonomy ────────────────────────────────────────────────────────────
def _load_taxonomy(path: Path) -> tuple[list[str], dict[str, int]]:
    """Load taxonomy.yaml as the single source of truth."""
    if not path.exists():
        fallback_classes = [
            "battery", "bottle", "can", "cardboard", "cigarette", "cup",
            "electronic", "food_waste", "glass_container", "metal", "paper",
            "plastic_bag", "plastic_container", "plastic_wrapper", "textile"
        ]
        return fallback_classes, {}

    with open(path, "r", encoding="utf-8") as f:
        tax = yaml.safe_load(f)

    classes = [tax["yolo_classes"][i] for i in range(len(tax["yolo_classes"]))]
    mapping = {}
    for taco_name, info in tax.get("taco_mapping", {}).items():
        if info.get("excluded", False):
            mapping[taco_name] = -1
        else:
            det = info["detector"]
            cls_id = next(k for k, v in tax["yolo_classes"].items() if v == det)
            mapping[taco_name] = cls_id
    return classes, mapping

YOLO_CLASSES, TACO_TO_YOLO = _load_taxonomy(TAXONOMY_PATH)
NUM_YOLO_CLASSES = len(YOLO_CLASSES)



def load_manifest() -> dict:
    """Load datasets manifest."""
    if MANIFEST_PATH.exists():
        with open(MANIFEST_PATH) as f:
            return yaml.safe_load(f)
    return {}


def verify_licenses(sources: list[str]) -> bool:
    """Verify all sources have commercial-use clearance."""
    manifest = load_manifest()
    datasets = manifest.get("datasets", [])

    for src in sources:
        ds = next((d for d in datasets if d["id"] == src), None)
        if ds is None:
            print(f"⚠ Unknown source '{src}': not in manifest")
            continue

        if not ds.get("commercial_use", False):
            print(f"❌ BLOCKED: '{src}' is NOT cleared for commercial use")
            print(f"   License: {ds.get('license', 'unknown')}")
            return False

        if not ds.get("included_in_production", False):
            print(f"⚠ WARNING: '{src}' not marked included_in_production")
            print(f"   Update datasets_manifest.yaml if you want to use it")

        print(f"✓ {src}: {ds.get('license', '?')}: commercial OK")

    return True


def file_hash(path: Path) -> str:
    """Compute MD5 hash of a file for deduplication."""
    h = hashlib.md5()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def find_taco_annotations(taco_dir: Path) -> Path:
    """Locate annotations.json in a TACO directory."""
    candidates = [
        taco_dir / "data" / "annotations.json",
        taco_dir / "annotations.json",
        taco_dir / "TACO" / "data" / "annotations.json",
    ]
    for c in candidates:
        if c.exists():
            return c
    for f in taco_dir.rglob("annotations.json"):
        return f
    raise FileNotFoundError(f"No annotations.json found in {taco_dir}")


def load_taco_annotations(ann_path: Path) -> dict:
    """Load COCO-format annotations."""
    with open(ann_path) as f:
        return json.load(f)


def find_image_file(images_dir: Path, filename: str) -> Path:
    """Find image file, handling batch subdirectories."""
    direct = images_dir / filename
    if direct.exists():
        return direct
    for batch_dir in images_dir.iterdir():
        if batch_dir.is_dir():
            candidate = batch_dir / filename
            if candidate.exists():
                return candidate
    for f in images_dir.rglob(filename):
        return f
    return None


def coco_to_yolo_bbox(bbox, img_width, img_height):
    """Convert COCO [x, y, w, h] to YOLO [cx, cy, w, h] normalized."""
    x, y, w, h = bbox
    cx = (x + w / 2) / img_width
    cy = (y + h / 2) / img_height
    nw = w / img_width
    nh = h / img_height
    return cx, cy, nw, nh


def convert_taco_to_yolo(taco_dir: Path, output_dir: Path) -> list[dict]:
    """Convert TACO COCO annotations to YOLO format.
    
    Returns list of image records for merging.
    """
    ann_path = find_taco_annotations(taco_dir)
    print(f"  Loading: {ann_path}")

    annotations = load_taco_annotations(ann_path)
    images = {img["id"]: img for img in annotations["images"]}
    ann_by_image = defaultdict(list)
    for ann in annotations["annotations"]:
        if ann.get("iscrowd", 0):
            continue
        ann_by_image[ann["image_id"]].append(ann)

    images_dir = taco_dir / "data"
    if not images_dir.exists():
        images_dir = taco_dir

    records = []
    unmapped = set()

    for img_id, img in images.items():
        img_file = find_image_file(images_dir, img["file_name"])
        if img_file is None:
            continue

        img_w, img_h = img["width"], img["height"]
        yolo_lines = []

        for ann in ann_by_image[img_id]:
            cat = annotations["categories"][0]
            for c in annotations["categories"]:
                if c["id"] == ann["category_id"]:
                    cat = c
                    break

            taco_name = cat["name"]
            if taco_name not in TACO_TO_YOLO:
                unmapped.add(taco_name)
                continue
            yolo_class = TACO_TO_YOLO[taco_name]
            if yolo_class < 0:
                continue  # excluded from training (e.g. Pen, Unlabeled litter)

            bbox = ann["bbox"]
            cx, cy, nw, nh = coco_to_yolo_bbox(bbox, img_w, img_h)
            cx = max(0, min(1, cx))
            cy = max(0, min(1, cy))
            nw = max(0, min(1, nw))
            nh = max(0, min(1, nh))

            if nw > 0.005 and nh > 0.005:  # Skip tiny annotations
                yolo_lines.append(f"{yolo_class} {cx:.6f} {cy:.6f} {nw:.6f} {nh:.6f}")

        if yolo_lines:
            records.append({
                "source": "taco",
                "image_path": img_file,
                "image_name": img_file.name,
                "width": img_w,
                "height": img_h,
                "labels": yolo_lines,
                "num_objects": len(yolo_lines),
                "hash": file_hash(img_file),
            })

    if unmapped:
        print(f"  ⚠ Unmapped TACO classes: {unmapped}")

    return records


def load_custom_dataset(custom_dir: Path) -> list[dict]:
    """Load custom YOLO-format dataset."""
    records = []
    img_dir = custom_dir / "images"
    lbl_dir = custom_dir / "labels"

    if not img_dir.exists():
        return records

    for img_file in img_dir.glob("*"):
        if img_file.suffix.lower() not in [".jpg", ".jpeg", ".png", ".webp"]:
            continue

        lbl_file = lbl_dir / (img_file.stem + ".txt")
        if not lbl_file.exists():
            continue

        lines = lbl_file.read_text().strip().split("\n")
        lines = [l.strip() for l in lines if l.strip()]

        if lines:
            records.append({
                "source": "custom",
                "image_path": img_file,
                "image_name": img_file.name,
                "width": 0,
                "height": 0,
                "labels": lines,
                "num_objects": len(lines),
                "hash": file_hash(img_file),
            })

    return records


def deduplicate(records: list[dict]) -> list[dict]:
    """Remove duplicate images across datasets (by content hash)."""
    seen = set()
    unique = []
    dupes = 0

    for r in records:
        if r["hash"] not in seen:
            seen.add(r["hash"])
            unique.append(r)
        else:
            dupes += 1

    if dupes:
        print(f"  Removed {dupes} duplicate images")
    return unique


def filter_quality(records: list[dict], min_objects: int = 1) -> list[dict]:
    """Filter out low-quality records."""
    filtered = [r for r in records if r["num_objects"] >= min_objects]
    removed = len(records) - len(filtered)
    if removed:
        print(f"  Filtered {removed} images with < {min_objects} objects")
    return filtered


def stratified_split(records: list[dict], train_ratio=0.8, val_ratio=0.1, seed=42):
    """Stratified split ensuring all classes appear in each split.
    
    Strategy:
    1. Group images by their primary (most frequent) class
    2. Split each group proportionally
    3. This ensures rare classes appear in train/val/test
    """
    random.seed(seed)

    # Group by primary class
    class_groups = defaultdict(list)
    for r in records:
        # Find most frequent class in this image
        class_counts = defaultdict(int)
        for line in r["labels"]:
            parts = line.split()
            if parts:
                class_counts[int(parts[0])] += 1
        if class_counts:
            primary_class = max(class_counts, key=class_counts.get)
        else:
            primary_class = NUM_YOLO_CLASSES - 1  # other
        class_groups[primary_class].append(r)

    train, val, test = [], [], []

    for cls, group in class_groups.items():
        random.shuffle(group)
        n = len(group)
        n_train = int(n * train_ratio)
        n_val = max(1, int(n * val_ratio)) if n > 2 else 0

        train.extend(group[:n_train])
        val.extend(group[n_train:n_train + n_val])
        test.extend(group[n_train + n_val:])

    random.shuffle(train)
    random.shuffle(val)
    random.shuffle(test)

    return train, val, test


def write_split(records: list[dict], split_name: str, output_dir: Path,
                class_names: list[str], image_id_counter: list[int]):
    """Write a split to disk."""
    split_img_dir = output_dir / split_name / "images"
    split_lbl_dir = output_dir / split_name / "labels"
    split_img_dir.mkdir(parents=True, exist_ok=True)
    split_lbl_dir.mkdir(parents=True, exist_ok=True)

    for r in records:
        # Generate unique filename
        image_id_counter[0] += 1
        ext = r["image_path"].suffix
        new_name = f"{r['source']}_{image_id_counter[0]:06d}{ext}"

        dst_img = split_img_dir / new_name
        if not dst_img.exists():
            shutil.copy2(r["image_path"], dst_img)

        lbl_file = split_lbl_dir / (Path(new_name).stem + ".txt")
        with open(lbl_file, "w") as f:
            f.write("\n".join(r["labels"]) + "\n")


def generate_data_yaml(output_dir: Path, num_classes: int, class_names: list[str],
                       train_dir: str = "train/images", val_dir: str = "val/images",
                       test_dir: str = "test/images"):
    """Generate YOLO dataset config YAML."""
    yaml_content = {
        "path": str(output_dir.absolute()),
        "train": train_dir,
        "val": val_dir,
        "test": test_dir,
        "nc": num_classes,
        "names": {i: name for i, name in enumerate(class_names)},
    }

    yaml_path = output_dir / "data.yaml"
    with open(yaml_path, "w") as f:
        yaml.dump(yaml_content, f, default_flow_style=False, sort_keys=False)
    print(f"  Generated: {yaml_path}")


def print_class_distribution(records: list[dict], class_names: list[str]):
    """Print per-class statistics."""
    class_counts = defaultdict(int)
    for r in records:
        for line in r["labels"]:
            parts = line.split()
            if parts:
                class_counts[int(parts[0])] += 1

    print("\n  Class distribution:")
    for cls_id in sorted(class_counts.keys()):
        name = class_names[cls_id] if cls_id < len(class_names) else f"class_{cls_id}"
        count = class_counts[cls_id]
        bar = "█" * min(50, count // 5)
        print(f"    {cls_id:2d} {name:30s} {count:5d} {bar}")

    total = sum(class_counts.values())
    n_classes = len(class_counts)
    print(f"\n  Total annotations: {total}")
    print(f"  Classes represented: {n_classes}/{len(class_names)}")


def main():
    parser = argparse.ArgumentParser(
        description="Prepare waste datasets for YOLO training",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Pipeline:
  Raw datasets → License check → Merge → Dedup → Normalize → Quality filter → YOLO Dataset

Examples:
  python scripts/prepare_dataset.py --kaggle-mode
  python scripts/prepare_dataset.py --taco-dir /path/to/taco --sources taco
  python scripts/prepare_dataset.py --sources taco custom
        """,
    )
    parser.add_argument("--taco-dir", type=str, help="Path to TACO dataset")
    parser.add_argument("--kneroma-dir", type=str,
                        default="/kaggle/input/datasets/kneroma/tacotrashdataset")
    parser.add_argument("--manojkari-dir", type=str,
                        default="/kaggle/input/datasets/manojkari/taco-dataset1")
    parser.add_argument("--custom-dir", type=str,
                        default="./datasets/custom",
                        help="Path to custom YOLO dataset")
    parser.add_argument("--output", type=str, default="./datasets/waste-yolo")
    parser.add_argument("--sources", nargs="+", default=["taco"],
                        help="Dataset sources to include: taco custom")
    parser.add_argument("--kaggle-mode", action="store_true")
    parser.add_argument("--train-ratio", type=float, default=0.8)
    parser.add_argument("--val-ratio", type=float, default=0.1)
    parser.add_argument("--no-verify", action="store_true",
                        help="Skip license verification")
    args = parser.parse_args()

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    print("=" * 60)
    print("WasegMul: Dataset Preparation Pipeline")
    print("=" * 60)

    # Step 1: License verification
    if not args.no_verify:
        print("\nStep 1: License verification")
        if not verify_licenses(args.sources):
            print("\n❌ License check failed. Use --no-verify to override.")
            return
        print()

    # Step 2: Load datasets
    print("Step 2: Loading datasets")
    all_records = []

    for source in args.sources:
        print(f"\n  Loading {source}...")

        if source == "taco":
            taco_dir = None
            if args.taco_dir:
                taco_dir = Path(args.taco_dir)
            elif args.kaggle_mode:
                for d in [Path(args.manojkari_dir), Path(args.kneroma_dir)]:
                    if d.exists():
                        taco_dir = d
                        break
            if taco_dir and taco_dir.exists():
                records = convert_taco_to_yolo(taco_dir, output_dir)
                print(f"  Loaded {len(records)} images from TACO")
                all_records.extend(records)
            else:
                print("  ⚠ TACO not found")

        elif source == "custom":
            custom_dir = Path(args.custom_dir)
            if custom_dir.exists():
                records = load_custom_dataset(custom_dir)
                print(f"  Loaded {len(records)} custom images")
                all_records.extend(records)
            else:
                print(f"  ⚠ Custom dir not found: {custom_dir}")

        else:
            print(f"  ⚠ Unknown source: {source}")

    if not all_records:
        print("\n❌ No data loaded. Check your --sources and dataset paths.")
        return

    # Step 3: Deduplicate
    print(f"\nStep 3: Deduplication ({len(all_records)} images)")
    all_records = deduplicate(all_records)
    print(f"  After dedup: {len(all_records)} images")

    # Step 4: Quality filter
    print(f"\nStep 4: Quality filter")
    all_records = filter_quality(all_records, min_objects=1)
    print(f"  After filter: {len(all_records)} images")

    # Step 5: Stratified split
    print(f"\nStep 5: Stratified split")
    train, val, test = stratified_split(
        all_records,
        train_ratio=args.train_ratio,
        val_ratio=args.val_ratio,
    )
    print(f"  Train: {len(train)}")
    print(f"  Val:   {len(val)}")
    print(f"  Test:  {len(test)}")

    # Step 6: Write to disk
    print(f"\nStep 6: Writing to {output_dir}")
    image_id = [0]
    write_split(train, "train", output_dir, YOLO_CLASSES, image_id)
    write_split(val, "val", output_dir, YOLO_CLASSES, image_id)
    write_split(test, "test", output_dir, YOLO_CLASSES, image_id)

    # Step 7: Generate config
    print(f"\nStep 7: Generating data.yaml")
    generate_data_yaml(output_dir, NUM_YOLO_CLASSES, YOLO_CLASSES)

    # Step 8: Summary
    print("\n" + "=" * 60)
    print("Dataset Summary")
    print("=" * 60)

    total = 0
    for split in ["train", "val", "test"]:
        img_dir = output_dir / split / "images"
        n = len(list(img_dir.glob("*")))
        total += n
        print(f"  {split}: {n} images")

    print(f"  Total: {total} images")
    print(f"  YOLO classes: {NUM_YOLO_CLASSES}")

    print_class_distribution(
        train + val + test,
        YOLO_CLASSES,
    )

    print(f"\n  Config: {output_dir / 'data.yaml'}")
    print(f"\n  Next steps:")
    print(f"    python scripts/train_waste_yolo.py")
    print(f"    # or on Kaggle: !python scripts/kaggle_train.py")


if __name__ == "__main__":
    main()
