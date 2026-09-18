#!/usr/bin/env python3
"""
WasegMul - Taxonomy Generator

Reads taxonomy.yaml (single source of truth) and generates:
  - docs/taxonomy.md (human-readable mapping table)

Usage:
  python scripts/generate_taxonomy_md.py
"""

import yaml
from pathlib import Path
from datetime import datetime


def main() -> None:
    root = Path(__file__).resolve().parent.parent
    yaml_path = root / "taxonomy.yaml"
    out_dir = root / "docs"
    out_path = out_dir / "taxonomy.md"

    if not yaml_path.exists():
        print(f"ERROR: {yaml_path} not found")
        return

    with open(yaml_path) as f:
        t = yaml.safe_load(f)

    yolo = t["yolo_classes"]
    effnet = t["efficientnet_classes"]
    mapping = t["taco_mapping"]
    det2sub = t["detection_to_subclass"]

    # ── Build excluded list ────────────────────────────────────────────────────
    excluded = {
        name: info["exclude_reason"]
        for name, info in mapping.items()
        if info.get("excluded", False)
    }

    # ── Build included mapping ─────────────────────────────────────────────────
    included = {
        name: (info["detector"], info.get("reason", ""))
        for name, info in mapping.items()
        if not info.get("excluded", False)
    }

    # ── Counters ───────────────────────────────────────────────────────────────
    n_yolo = len(yolo)
    n_effnet = len(effnet)
    n_taco = len(mapping)
    n_included = len(included)
    n_excluded = len(excluded)

    # ── Group TACO by detector class ───────────────────────────────────────────
    by_detector: dict[str, list[tuple[str, str]]] = {}
    for name, (detector, reason) in sorted(included.items()):
        by_detector.setdefault(detector, []).append((name, reason))

    lines: list[str] = []
    w = lines.append

    # ── Header ─────────────────────────────────────────────────────────────────
    w("# WasegMul Waste Taxonomy")
    w("")
    w(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M')}  ")
    w(f"Source: `taxonomy.yaml` (single source of truth)")
    w("")
    w("---")
    w("")
    w("## Overview")
    w("")
    w(f"```")
    w(f"TACO ({n_taco} raw classes)")
    w(f"  ├── Excluded: {n_excluded} ambiguous categories")
    w(f"  └── Mapped:   {n_included} categories")
    w("")
    w(f"YOLO ({n_yolo} visual detector classes)")
    w(f"  └── Each class groups objects that LOOK similar")
    w("")
    w(f"EfficientNet ({n_effnet} semantic subclasses)")
    w("  └── Classifies what each YOLO crop actually IS")
    w("```")
    w("")

    # ── Detection Classes ──────────────────────────────────────────────────────
    w("---")
    w("")
    w(f"## YOLO Detection Classes ({n_yolo} visual categories)")
    w("")
    w("YOLO detects WHERE objects are based on visual appearance.")
    w("")
    w("| ID | Class | Description |")
    w("|---:|-------|-------------|")
    for cid, name in yolo.items():
        # Build description from first reason of that class
        desc = ""
        if name in by_detector and by_detector[name]:
            desc = by_detector[name][0][1].split(".")[0]
        w(f"| {cid} | `{name}` | {desc} |")
    w("")

    # ── EfficientNet Classes ───────────────────────────────────────────────────
    w("---")
    w("")
    w(f"## EfficientNet Classification Classes ({n_effnet} final subclasses)")
    w("")
    w("After YOLO crops a detection, EfficientNet classifies into these subclasses.")
    w("")
    w("| ID | Class |")
    w("|---:|-------|")
    for cid, name in effnet.items():
        w(f"| {cid} | `{name}` |")
    w("")

    # ── TACO → YOLO Mapping ───────────────────────────────────────────────────
    w("---")
    w("")
    w("## TACO → YOLO Mapping ({n_included} categories)".format(n_included=n_included))
    w("")
    w("Every TACO category is mapped individually based on visual appearance.")
    w("Source: http://tacodataset.org/taxonomy")
    w("")

    for detector_name in sorted(by_detector.items(), key=lambda x: x[0]):
        detector = detector_name[0]
        items = detector_name[1]
        w(f"### `{detector}` ({len(items)} categories)")
        w("")
        w("| TACO Category | Reason |")
        w("|---------------|--------|")
        for taco_name, reason in items:
            # Truncate long reasons
            short = reason.split(".")[0] if reason else ""
            w(f"| {taco_name} | {short} |")
        w("")

    # ── Excluded Categories ────────────────────────────────────────────────────
    w("---")
    w("")
    w("## Excluded Categories ({n_excluded})".format(n_excluded=n_excluded))
    w("")
    w("These categories are excluded from training because they are genuinely ambiguous.")
    w("Better to leave them out than to introduce label noise.")
    w("")
    w("| Category | Reason |")
    w("|----------|--------|")
    for name, reason in sorted(excluded.items()):
        w(f"| {name} | {reason} |")
    w("")

    # ── Detection → Subclass Map ───────────────────────────────────────────────
    w("---")
    w("")
    w("## Detection → Subclass Mapping")
    w("")
    w("Which EfficientNet subclasses can appear in each YOLO detection.")
    w("Used for post-crop filtering and confidence boosting.")
    w("")
    w("| YOLO Class | EfficientNet Subclasses |")
    w("|------------|----------------------|")
    for det, subs in det2sub.items():
        sub_names = ", ".join(effnet[s] for s in subs if s in effnet)
        w(f"| `{det}` | {sub_names} |")
    w("")

    # ── Write ──────────────────────────────────────────────────────────────────
    out_dir.mkdir(parents=True, exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")

    print(f"Generated: {out_path}")
    print(f"  TACO categories: {n_taco}")
    print(f"  Included: {n_included}")
    print(f"  Excluded: {n_excluded}")
    print(f"  YOLO classes: {n_yolo}")
    print(f"  EfficientNet classes: {n_effnet}")


if __name__ == "__main__":
    main()
