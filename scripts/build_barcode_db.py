#!/usr/bin/env python3
"""
build_barcode_db.py - Build offline barcode packaging database for WasegMul.

Generates `wasegmul_barcode_offline.db` with the exact schema needed by Room's
`barcode_products` table. Supports:
  1. `--sample` / `--dry-run`: 100+ curated top FMCG products with realistic EAN-13 barcodes
  2. `--mode duckdb`: Query Open Food Facts Parquet dataset from Hugging Face or local file
  3. `--mode api`: Query live Open Food Facts API v2/v3
  4. `--mode jsonl`: Parse a local Open Food Facts JSONL data dump

Usage:
  python scripts/build_barcode_db.py --sample
  python scripts/build_barcode_db.py --output app/src/main/assets/database/wasegmul_barcode_offline.db
  python scripts/build_barcode_db.py --mode duckdb --limit 5000
  python scripts/build_barcode_db.py --mode api --limit 200
"""

import argparse
import json
import os
import sqlite3
import sys
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

# ─────────────────────────────────────────────────────────────────────────────
# Material & Shape Mapping — SINGLE SOURCE: taxonomy.yaml
# ─────────────────────────────────────────────────────────────────────────────
# Canonical truth lives in taxonomy.yaml (barcode_materials.materials +
# shapes_fallback). The dicts below are a staged copy for offline/Kaggle runs
# without the repo file. On import, _load_off_maps_from_taxonomy() overrides
# them from taxonomy.yaml when present and warns on drift.
# Drift test note: add a CI/unit check comparing these dicts against
# taxonomy.yaml (fail on divergence) — see scripts/requirements-train.txt.
# Synchronized with PackagingWasteMapper.kt (shared KMP) — all three must agree.
# ─────────────────────────────────────────────────────────────────────────────

SPECIAL_HUMAN_NAMES = {
    "pet-1": "PET",
    "pet": "PET",
    "pete": "PET",
    "rpet": "rPET",
    "hdpe-2": "HDPE",
    "hdpe": "HDPE",
    "pe-hd": "HDPE",
    "pvc-3": "PVC",
    "pvc": "PVC",
    "ldpe-4": "LDPE",
    "ldpe": "LDPE",
    "pe-ld": "LDPE",
    "pp-5": "PP",
    "pp": "PP",
    "ps-6": "PS",
    "ps": "PS",
    "eps": "EPS",
    "expanded-polystyrene": "Expanded Polystyrene",
    "other-plastics-7": "Other Plastics",
    "other-plastic-7": "Other Plastics",
    "o-7": "Other Plastics",
    "pla": "PLA",
    "tetra-pak": "Tetra Pak",
    "tetrapak": "Tetra Pak",
    "c-pap": "Paper Composite",
    "alu": "Aluminium",
    "tin-plate": "Tinplate",
}

MATERIAL_MAPPING: Dict[str, Tuple[str, str]] = {
    # Plastics & Polymers (Resin Codes 1-7)
    "en:pet-1": ("Recyclable", "Plastic"),
    "en:pet": ("Recyclable", "Plastic"),
    "en:pete": ("Recyclable", "Plastic"),
    "en:rpet": ("Recyclable", "Plastic"),
    "en:hdpe-2": ("Recyclable", "Plastic"),
    "en:hdpe": ("Recyclable", "Plastic"),
    "en:pe-hd": ("Recyclable", "Plastic"),
    "en:pvc-3": ("Trash", "Plastic"),
    "en:pvc": ("Trash", "Plastic"),
    "en:ldpe-4": ("Recyclable", "Plastic"),
    "en:ldpe": ("Recyclable", "Plastic"),
    "en:pe-ld": ("Recyclable", "Plastic"),
    "en:pp-5": ("Recyclable", "Plastic"),
    "en:pp": ("Recyclable", "Plastic"),
    "en:ps-6": ("Trash", "Plastic"),
    "en:ps": ("Trash", "Plastic"),
    "en:expanded-polystyrene": ("Trash", "Plastic"),
    "en:eps": ("Trash", "Plastic"),
    "en:other-plastics-7": ("Trash", "Plastic"),
    "en:other-plastic-7": ("Trash", "Plastic"),
    "en:o-7": ("Trash", "Plastic"),
    "en:plastic": ("Recyclable", "Plastic"),
    "en:bioplastic": ("Organic", "Organic"),
    "en:pla": ("Organic", "Organic"),
    "en:biodegradable-plastic": ("Organic", "Organic"),

    # Glass
    "en:glass": ("Recyclable", "Glass"),
    "en:clear-glass": ("Recyclable", "Glass"),
    "en:green-glass": ("Recyclable", "Glass"),
    "en:brown-glass": ("Recyclable", "Glass"),
    "en:amber-glass": ("Recyclable", "Glass"),
    "en:coloured-glass": ("Recyclable", "Glass"),
    "en:colorless-glass": ("Recyclable", "Glass"),

    # Metals
    "en:aluminium": ("Recyclable", "Metal"),
    "en:aluminum": ("Recyclable", "Metal"),
    "en:alu": ("Recyclable", "Metal"),
    "en:steel": ("Recyclable", "Metal"),
    "en:tin": ("Recyclable", "Metal"),
    "en:tinplate": ("Recyclable", "Metal"),
    "en:tin-plate": ("Recyclable", "Metal"),
    "en:metal": ("Recyclable", "Metal"),
    "en:iron": ("Recyclable", "Metal"),

    # Paper & Cardboard
    "en:paper": ("Recyclable", "Paper"),
    "en:cardboard": ("Recyclable", "Cardboard"),
    "en:corrugated-cardboard": ("Recyclable", "Cardboard"),
    "en:kraft-paper": ("Recyclable", "Paper"),
    "en:paperboard": ("Recyclable", "Cardboard"),
    "en:carton": ("Recyclable", "Cardboard"),

    # Composites & Multilayer
    "en:tetra-pak": ("Recyclable", "Cardboard"),
    "en:tetrapak": ("Recyclable", "Cardboard"),
    "en:composite-material": ("Trash", "Miscellaneous Trash"),
    "en:composite": ("Trash", "Miscellaneous Trash"),
    "en:c-pap": ("Trash", "Miscellaneous Trash"),
    "en:multilayer": ("Trash", "Plastic"),

    # Natural & Organic Materials
    "en:wood": ("Organic", "Organic"),
    "en:cork": ("Organic", "Organic"),
    "en:bamboo": ("Organic", "Organic"),
    "en:cotton": ("Trash", "Textile Trash"),
    "en:jute": ("Organic", "Organic"),
    "en:ceramic": ("Trash", "Miscellaneous Trash"),
    # NOTE: no en:battery / en:electronic(s) keys — a battery/device is a PRODUCT,
    # not an OFF packaging-material tag. taxonomy.yaml and PackagingWasteMapper.kt
    # agree (neither carries them); keeping them here caused OFF-map drift.
}

SHAPE_FALLBACK: Dict[str, Tuple[str, str]] = {
    "en:bottle": ("Recyclable", "Plastic"),
    "en:can": ("Recyclable", "Metal"),
    "en:box": ("Recyclable", "Cardboard"),
    "en:jar": ("Recyclable", "Glass"),
    "en:carton": ("Recyclable", "Cardboard"),
    "en:tub": ("Recyclable", "Plastic"),
    "en:pot": ("Recyclable", "Plastic"),
    "en:tray": ("Recyclable", "Plastic"),
    "en:bag": ("Recyclable", "Plastic"),
    "en:pouch": ("Trash", "Plastic"),
    "en:wrapper": ("Trash", "Plastic"),
    "en:film": ("Trash", "Plastic"),
    "en:sachet": ("Trash", "Miscellaneous Trash"),
    "en:packet": ("Trash", "Miscellaneous Trash"),
    "en:cup": ("Trash", "Plastic"),
    "en:lid": ("Recyclable", "Plastic"),
    "en:cap": ("Recyclable", "Plastic"),
    "en:cork": ("Organic", "Organic"),
    "en:stopper": ("Organic", "Organic"),
    "en:aerosol": ("Recyclable", "Metal"),
    "en:tin": ("Recyclable", "Metal"),
    "en:envelope": ("Recyclable", "Paper"),
    "en:sheet": ("Recyclable", "Paper"),
    "en:clamshell": ("Recyclable", "Plastic"),
    "en:blister-pack": ("Trash", "Miscellaneous Trash"),
    "en:tube": ("Trash", "Plastic"),
    "en:punnet": ("Recyclable", "Plastic"),
    "en:barrel": ("Recyclable", "Metal"),
    "en:drum": ("Recyclable", "Metal"),
    "en:keg": ("Recyclable", "Metal"),
    "en:flagon": ("Recyclable", "Glass"),
    "en:vial": ("Recyclable", "Glass"),
    "en:ampoule": ("Recyclable", "Glass"),
}


def _load_off_maps_from_taxonomy() -> None:
    """Override MATERIAL_MAPPING/SHAPE_FALLBACK from taxonomy.yaml when present.

    Single-source rule: taxonomy.yaml wins. If the staged dicts above drift
    from the yaml, a warning is printed (CI should fail on drift — see header
    note). Never silently train/ship on a forked mapping.
    """
    global MATERIAL_MAPPING, SHAPE_FALLBACK
    candidates = [
        Path(__file__).resolve().parent.parent / "taxonomy.yaml",
        Path(__file__).resolve().parent / "taxonomy.yaml",
        Path("taxonomy.yaml"),
    ]
    tax_path = next((p for p in candidates if p.exists()), None)
    if tax_path is None:
        print("NOTE: taxonomy.yaml not found — using staged OFF maps (verify drift before shipping).")
        return
    try:
        import yaml
    except ImportError:
        print("NOTE: pyyaml missing — cannot load taxonomy.yaml OFF maps; using staged copy.")
        return
    with open(tax_path, encoding="utf-8") as f:
        tax = yaml.safe_load(f) or {}
    mats = ((tax.get("barcode_materials") or {}).get("materials")) or {}
    shapes = ((tax.get("barcode_materials") or {}).get("shapes_fallback")) or {}
    if not mats and not shapes:
        print(f"NOTE: {tax_path} has no barcode_materials — using staged OFF maps.")
        return
    yaml_mats = {k: (v["category"], v["subclass"]) for k, v in mats.items()
                 if isinstance(v, dict) and "category" in v and "subclass" in v}
    yaml_shapes = {k: (v["category"], v["subclass"]) for k, v in shapes.items()
                   if isinstance(v, dict) and "category" in v and "subclass" in v}
    drift_m = set(yaml_mats) ^ set(MATERIAL_MAPPING)
    drift_s = set(yaml_shapes) ^ set(SHAPE_FALLBACK)
    if drift_m or drift_s:
        print(f"WARNING: OFF map drift vs {tax_path}: "
              f"{len(drift_m)} material keys, {len(drift_s)} shape keys differ. "
              f"taxonomy.yaml wins for this run; update the staged copy + Kotlin mapper.")
    if yaml_mats:
        MATERIAL_MAPPING = yaml_mats
    if yaml_shapes:
        SHAPE_FALLBACK = yaml_shapes


_load_off_maps_from_taxonomy()


def compute_ean13_check_digit(digits12: str) -> str:
    """Calculates standard GS1 Modulo-10 check digit for a 12-digit base."""
    digits = [int(c) for c in digits12[:12]]
    total = sum(d if i % 2 == 0 else d * 3 for i, d in enumerate(digits))
    return str((10 - (total % 10)) % 10)


def is_valid_ean13(code: str) -> bool:
    """Validates an EAN-13 barcode string using GS1 Modulo-10 checksum."""
    if len(code) != 13 or not code.isdigit():
        return False
    return code[12] == compute_ean13_check_digit(code[:12])


def normalize_ean13(code: str) -> Optional[str]:
    """Normalize to a VALID EAN-13, or return None (caller must skip + log).

    Policy (audit §3.47 — never pad/truncate into an invalid checksum):
    - Strip non-digits.
    - 13 digits: return as-is IFF checksum valid, else None.
    - 12 digits: treat as UPC-A; convert by prepending '0' (GS1 rule; leading
      zero preserves the Modulo-10 check) and return IFF the result validates,
      else None.
    - Anything else (EAN-8, GTIN-14, junk): None — caller logs + skips.
      (EAN-8→EAN-13 zero-expansion is intentionally NOT guessed here.)
    """
    clean = "".join(c for c in str(code) if c.isdigit())
    if len(clean) == 13:
        return clean if is_valid_ean13(clean) else None
    if len(clean) == 12:
        candidate = "0" + clean
        return candidate if is_valid_ean13(candidate) else None
    return None


def humanize_tag(tag: Optional[str]) -> str:
    """Converts a taxonomy tag (e.g. 'en:pet-1') to a readable title (e.g. 'PET')."""
    if not tag:
        return ""
    raw = tag.split(":")[-1].strip().lower()
    if raw in SPECIAL_HUMAN_NAMES:
        return SPECIAL_HUMAN_NAMES[raw]
    words = [w.capitalize() for w in raw.replace("-", " ").replace("_", " ").split()]
    return " ".join(words)


def map_disposal_action(recycling_tag: Optional[str], fallback_category: str) -> str:
    """Maps recycling taxonomy tag to Recycle, Discard, or Compost.

    Order matters: check discard-negations FIRST — 'do-not-recycle' contains
    'recycle' and must map to Discard. Word-boundary matching avoids 'bin'
    firing inside 'combine'.
    """
    import re

    def _has(word: str, text: str) -> bool:
        return re.search(r"\b" + re.escape(word) + r"\b", text) is not None

    if not recycling_tag:
        if fallback_category == "Recyclable":
            return "Recycle"
        elif fallback_category == "Organic":
            return "Compost"
        else:
            return "Discard"
    norm = recycling_tag.split(":")[-1].strip().lower().replace("-", " ").replace("_", " ")
    # Discard-negations first (do-not-recycle, non recyclable, not recyclable)
    if ("discard" in norm or "trash" in norm or "incinerat" in norm
            or "do not recycle" in norm or "do-not-recycle" in recycling_tag.lower()
            or "non recyclable" in norm or "not recyclable" in norm
            or _has("bin", norm)):
        return "Discard"
    if "compost" in norm or "biodegrad" in norm:
        return "Compost"
    if "recycle" in norm or "reuse" in norm or "re use" in norm:
        return "Recycle"
    if fallback_category == "Recyclable":
        return "Recycle"
    elif fallback_category == "Organic":
        return "Compost"
    return "Discard"


def get_material_mapping(tag: Optional[str]) -> Optional[Tuple[str, str]]:
    if not tag:
        return None
    clean = tag.strip().lower()
    with_en = clean if ":" in clean else f"en:{clean}"
    without_en = clean.split(":")[-1]
    return MATERIAL_MAPPING.get(with_en) or MATERIAL_MAPPING.get(clean) or MATERIAL_MAPPING.get(without_en)


def get_shape_fallback(tag: Optional[str]) -> Optional[Tuple[str, str]]:
    if not tag:
        return None
    clean = tag.strip().lower()
    with_en = clean if ":" in clean else f"en:{clean}"
    without_en = clean.split(":")[-1]
    return SHAPE_FALLBACK.get(with_en) or SHAPE_FALLBACK.get(clean) or SHAPE_FALLBACK.get(without_en)


# ─────────────────────────────────────────────────────────────────────────────
# Database Schema Creation & Maintenance
# ─────────────────────────────────────────────────────────────────────────────

SCHEMA_SQL = """
CREATE TABLE IF NOT EXISTS barcode_products (
    barcode TEXT NOT NULL PRIMARY KEY,
    productName TEXT,
    brand TEXT,
    category TEXT NOT NULL,
    subclass TEXT NOT NULL,
    materials TEXT,
    componentsJson TEXT,
    weightGrams REAL,
    ecoscore TEXT,
    source TEXT NOT NULL,
    packagingsComplete INTEGER NOT NULL DEFAULT 0,
    lastAccessed INTEGER NOT NULL DEFAULT 0,
    cachedAt INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS index_barcode_products_category ON barcode_products (category);
CREATE INDEX IF NOT EXISTS index_barcode_products_lastAccessed ON barcode_products (lastAccessed);
"""


def init_db(db_path: Path) -> sqlite3.Connection:
    """Creates directory if needed, opens SQLite db, and creates the exact Room schema."""
    db_path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(db_path))
    conn.execute("PRAGMA foreign_keys = ON;")
    conn.executescript(SCHEMA_SQL)
    conn.commit()
    return conn


def insert_product(conn: sqlite3.Connection, item: Dict[str, Any]) -> bool:
    """Inserts or replaces a product record; validates barcode at insert.

    Returns True on insert, False when rejected (invalid barcode). Invalid
    codes are NEVER padded/truncated into plausible-looking rows — they are
    rejected with a logged warning so checksum corruption cannot ship.
    """
    barcode = item.get("barcode", "")
    if not is_valid_ean13(str(barcode)):
        print(f"  SKIP invalid barcode (checksum/length): {barcode!r} "
              f"product={item.get('productName')!r}")
        return False
    conn.execute(
        """
        INSERT OR REPLACE INTO barcode_products (
            barcode, productName, brand, category, subclass,
            materials, componentsJson, weightGrams, ecoscore,
            source, packagingsComplete, lastAccessed, cachedAt
        ) VALUES (
            ?, ?, ?, ?, ?,
            ?, ?, ?, ?,
            ?, ?, ?, ?
        )
        """,
        (
            item["barcode"],
            item.get("productName"),
            item.get("brand"),
            item["category"],
            item["subclass"],
            item.get("materials"),
            item.get("componentsJson"),
            item.get("weightGrams"),
            item.get("ecoscore"),
            item.get("source", "preloaded"),
            1 if item.get("packagingsComplete", True) else 0,
            item.get("lastAccessed", 0),
            item.get("cachedAt", 0),
        ),
    )
    return True


def optimize_db(conn: sqlite3.Connection) -> None:
    """Runs PRAGMA optimize and VACUUM to prepare database for production deployment."""
    conn.commit()
    conn.execute("PRAGMA optimize;")
    conn.commit()
    conn.execute("VACUUM;")
    conn.commit()


# ─────────────────────────────────────────────────────────────────────────────
# Packaging Component Builder Helper
# ─────────────────────────────────────────────────────────────────────────────

def make_component(
    shape_id: str,
    material_id: str,
    weight_g: Optional[float] = None,
    units: int = 1,
    recycling_id: str = "en:recycle",
) -> Dict[str, Any]:
    """Builds a packaging component DTO dictionary matching OffPackagingComponentDto."""
    shape_name = humanize_tag(shape_id)
    mat_name = humanize_tag(material_id)
    rec_name = humanize_tag(recycling_id)

    res: Dict[str, Any] = {
        "shape": {"id": shape_id, "lc_name": shape_name},
        "material": {"id": material_id, "lc_name": mat_name},
        "recycling": {"id": recycling_id, "lc_name": rec_name},
        "number_of_units": units,
    }
    if weight_g is not None:
        res["weight_measured"] = float(weight_g)
    return res


# ─────────────────────────────────────────────────────────────────────────────
# 100+ Curated FMCG Products for Offline Pre-loading
# ─────────────────────────────────────────────────────────────────────────────

def get_curated_fmcg_products() -> List[Dict[str, Any]]:
    """Returns over 100 realistic top FMCG products with accurate barcodes and packaging components."""
    products_raw = [
        # 1. Carbonated Beverages & Soft Drinks
        {
            "barcode": "5449000000996",
            "name": "Coca-Cola Original Taste 500ml",
            "brand": "Coca-Cola",
            "ecoscore": "d",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=24.0),
                make_component("en:cap", "en:hdpe-2", weight_g=2.5),
                make_component("en:film", "en:pp-5", weight_g=0.5),
            ],
        },
        {
            "barcode": "5449000014528",
            "name": "Coca-Cola Original 330ml Can",
            "brand": "Coca-Cola",
            "ecoscore": "d",
            "components": [
                make_component("en:can", "en:aluminium", weight_g=14.5),
            ],
        },
        {
            "barcode": "5449000131805",
            "name": "Coca-Cola Zero Sugar 500ml",
            "brand": "Coca-Cola",
            "ecoscore": "b",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=24.0),
                make_component("en:cap", "en:hdpe-2", weight_g=2.5),
            ],
        },
        {
            "barcode": "5449000017888",
            "name": "Diet Coke 330ml Can",
            "brand": "Coca-Cola",
            "ecoscore": "b",
            "components": [
                make_component("en:can", "en:aluminium", weight_g=14.5),
            ],
        },
        {
            "barcode": "4060800104618",
            "name": "Pepsi Cola Regular 500ml",
            "brand": "Pepsi",
            "ecoscore": "d",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=24.0),
                make_component("en:cap", "en:hdpe-2", weight_g=2.5),
            ],
        },
        {
            "barcode": "4060800001030",
            "name": "Pepsi Cola 330ml Can",
            "brand": "Pepsi",
            "ecoscore": "d",
            "components": [
                make_component("en:can", "en:aluminium", weight_g=14.5),
            ],
        },
        {
            "barcode": "4060800129994",
            "name": "Pepsi Max No Sugar 330ml Can",
            "brand": "Pepsi",
            "ecoscore": "b",
            "components": [
                make_component("en:can", "en:aluminium", weight_g=14.5),
            ],
        },
        {
            "barcode": "5449000012203",
            "name": "Sprite Lemon-Lime 500ml",
            "brand": "Sprite",
            "ecoscore": "d",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=23.0),
                make_component("en:cap", "en:hdpe-2", weight_g=2.5),
            ],
        },
        {
            "barcode": "5449000011527",
            "name": "Sprite Lemon-Lime 330ml Can",
            "brand": "Sprite",
            "ecoscore": "d",
            "components": [
                make_component("en:can", "en:aluminium", weight_g=14.5),
            ],
        },
        {
            "barcode": "5449000011404",
            "name": "Fanta Orange 500ml",
            "brand": "Fanta",
            "ecoscore": "d",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=24.0),
                make_component("en:cap", "en:hdpe-2", weight_g=2.5),
            ],
        },
        {
            "barcode": "5449000011534",
            "name": "Fanta Orange 330ml Can",
            "brand": "Fanta",
            "ecoscore": "d",
            "components": [
                make_component("en:can", "en:aluminium", weight_g=14.5),
            ],
        },
        {
            "barcode": "4060800007872",
            "name": "7UP Lemon & Lime 330ml Can",
            "brand": "7UP",
            "ecoscore": "d",
            "components": [
                make_component("en:can", "en:aluminium", weight_g=14.5),
            ],
        },
        {
            "barcode": "4060800164322",
            "name": "Mountain Dew Citrus 330ml Can",
            "brand": "Mountain Dew",
            "ecoscore": "d",
            "components": [
                make_component("en:can", "en:aluminium", weight_g=14.5),
            ],
        },
        {
            "barcode": "5010106113127",
            "name": "Dr Pepper 330ml Can",
            "brand": "Dr Pepper",
            "ecoscore": "d",
            "components": [
                make_component("en:can", "en:aluminium", weight_g=14.5),
            ],
        },
        {
            "barcode": "9002490100070",
            "name": "Red Bull Energy Drink 250ml Can",
            "brand": "Red Bull",
            "ecoscore": "c",
            "components": [
                make_component("en:can", "en:aluminium", weight_g=12.0),
            ],
        },
        {
            "barcode": "9002490205935",
            "name": "Red Bull Sugarfree 250ml Can",
            "brand": "Red Bull",
            "ecoscore": "b",
            "components": [
                make_component("en:can", "en:aluminium", weight_g=12.0),
            ],
        },
        {
            "barcode": "5060166692246",
            "name": "Monster Energy Original 500ml Can",
            "brand": "Monster Energy",
            "ecoscore": "d",
            "components": [
                make_component("en:can", "en:aluminium", weight_g=21.0),
            ],
        },
        {
            "barcode": "5449000005274",
            "name": "Schweppes Indian Tonic Water 1L",
            "brand": "Schweppes",
            "ecoscore": "d",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=36.0),
                make_component("en:cap", "en:hdpe-2", weight_g=3.0),
            ],
        },
        {
            "barcode": "5449000016621",
            "name": "Schweppes Ginger Ale 330ml Can",
            "brand": "Schweppes",
            "ecoscore": "d",
            "components": [
                make_component("en:can", "en:aluminium", weight_g=14.5),
            ],
        },
        {
            "barcode": "8901764031205",
            "name": "Thums Up Charged Cola 250ml Can",
            "brand": "Thums Up",
            "ecoscore": "d",
            "components": [
                make_component("en:can", "en:aluminium", weight_g=12.5),
            ],
        },
        {
            "barcode": "8901764031229",
            "name": "Thums Up Cola 500ml PET Bottle",
            "brand": "Thums Up",
            "ecoscore": "d",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=24.0),
                make_component("en:cap", "en:hdpe-2", weight_g=2.5),
            ],
        },
        {
            "barcode": "8901764021206",
            "name": "Limca Lemon Drink 500ml PET Bottle",
            "brand": "Limca",
            "ecoscore": "d",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=24.0),
                make_component("en:cap", "en:hdpe-2", weight_g=2.5),
            ],
        },

        # 2. Water (Still, Sparkling & Mineral)
        {
            "barcode": "3068320114452",
            "name": "Evian Natural Mineral Water 500ml",
            "brand": "Evian",
            "ecoscore": "b",
            "components": [
                make_component("en:bottle", "en:rpet", weight_g=19.0),
                make_component("en:cap", "en:hdpe-2", weight_g=2.0),
            ],
        },
        {
            "barcode": "3068320055007",
            "name": "Evian Natural Mineral Water 1.5L",
            "brand": "Evian",
            "ecoscore": "b",
            "components": [
                make_component("en:bottle", "en:rpet", weight_g=28.0),
                make_component("en:cap", "en:hdpe-2", weight_g=2.5),
            ],
        },
        {
            "barcode": "3057640100378",
            "name": "Volvic Natural Mineral Water 1.5L",
            "brand": "Volvic",
            "ecoscore": "b",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=30.0),
                make_component("en:cap", "en:hdpe-2", weight_g=2.5),
            ],
        },
        {
            "barcode": "7613034450375",
            "name": "Perrier Sparkling Mineral Water 330ml Glass Bottle",
            "brand": "Perrier",
            "ecoscore": "c",
            "components": [
                make_component("en:bottle", "en:clear-glass", weight_g=240.0),
                make_component("en:cap", "en:tinplate", weight_g=2.0),
            ],
        },
        {
            "barcode": "7613035384723",
            "name": "Perrier Sparkling Mineral Water 330ml Can",
            "brand": "Perrier",
            "ecoscore": "b",
            "components": [
                make_component("en:can", "en:aluminium", weight_g=13.0),
            ],
        },
        {
            "barcode": "8002270014022",
            "name": "San Pellegrino Sparkling Mineral Water 750ml Glass Bottle",
            "brand": "San Pellegrino",
            "ecoscore": "c",
            "components": [
                make_component("en:bottle", "en:green-glass", weight_g=420.0),
                make_component("en:cap", "en:tinplate", weight_g=2.5),
            ],
        },
        {
            "barcode": "8902080000403",
            "name": "Aquafina Packaged Drinking Water 500ml",
            "brand": "Aquafina",
            "ecoscore": "b",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=15.0),
                make_component("en:cap", "en:hdpe-2", weight_g=2.0),
            ],
        },
        {
            "barcode": "8906017290048",
            "name": "Bisleri Packaged Drinking Water 500ml",
            "brand": "Bisleri",
            "ecoscore": "b",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=14.0),
                make_component("en:cap", "en:hdpe-2", weight_g=2.0),
            ],
        },
        {
            "barcode": "8906017290024",
            "name": "Bisleri Mineral Water 1L",
            "brand": "Bisleri",
            "ecoscore": "b",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=21.0),
                make_component("en:cap", "en:hdpe-2", weight_g=2.0),
            ],
        },
        {
            "barcode": "8901764012211",
            "name": "Kinley Water 1L",
            "brand": "Kinley",
            "ecoscore": "b",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=22.0),
                make_component("en:cap", "en:hdpe-2", weight_g=2.5),
            ],
        },
        {
            "barcode": "0049000037112",
            "name": "Dasani Purified Water 500ml",
            "brand": "Dasani",
            "ecoscore": "b",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=16.0),
                make_component("en:cap", "en:hdpe-2", weight_g=2.0),
            ],
        },
        {
            "barcode": "5000157074409",
            "name": "Buxton Natural Mineral Water 500ml",
            "brand": "Buxton",
            "ecoscore": "b",
            "components": [
                make_component("en:bottle", "en:rpet", weight_g=16.0),
                make_component("en:cap", "en:hdpe-2", weight_g=2.0),
            ],
        },
        {
            "barcode": "7613036979140",
            "name": "Vittel Natural Mineral Water 750ml",
            "brand": "Vittel",
            "ecoscore": "b",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=22.0),
                make_component("en:cap", "en:hdpe-2", weight_g=2.0),
            ],
        },

        # 3. Milks, Juices, Dairy & Plant-Based Drinks
        {
            "barcode": "3502110009440",
            "name": "Tropicana Pure Premium 100% Orange Juice 1L",
            "brand": "Tropicana",
            "ecoscore": "c",
            "components": [
                make_component("en:carton", "en:tetra-pak", weight_g=32.0),
                make_component("en:cap", "en:hdpe-2", weight_g=3.0),
            ],
        },
        {
            "barcode": "5038862142273",
            "name": "Innocent Orange Juice with Bits 900ml",
            "brand": "Innocent",
            "ecoscore": "c",
            "components": [
                make_component("en:bottle", "en:rpet", weight_g=35.0),
                make_component("en:cap", "en:hdpe-2", weight_g=3.0),
            ],
        },
        {
            "barcode": "5038862217643",
            "name": "Innocent Super Smoothie Recharge 360ml",
            "brand": "Innocent",
            "ecoscore": "c",
            "components": [
                make_component("en:bottle", "en:rpet", weight_g=24.0),
                make_component("en:cap", "en:hdpe-2", weight_g=2.5),
            ],
        },
        {
            "barcode": "8901764041211",
            "name": "Minute Maid Pulpy Orange 1L",
            "brand": "Minute Maid",
            "ecoscore": "c",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=32.0),
                make_component("en:cap", "en:hdpe-2", weight_g=3.0),
            ],
        },
        {
            "barcode": "7394376616037",
            "name": "Oatly The Original Oat Drink Barista Edition 1L",
            "brand": "Oatly",
            "ecoscore": "a",
            "components": [
                make_component("en:carton", "en:tetra-pak", weight_g=33.0),
                make_component("en:cap", "en:hdpe-2", weight_g=3.5),
            ],
        },
        {
            "barcode": "5411188110835",
            "name": "Alpro Soya Milk Original 1L",
            "brand": "Alpro",
            "ecoscore": "a",
            "components": [
                make_component("en:carton", "en:tetra-pak", weight_g=32.0),
                make_component("en:cap", "en:hdpe-2", weight_g=3.0),
            ],
        },
        {
            "barcode": "5411188115472",
            "name": "Alpro Almond Unsweetened 1L",
            "brand": "Alpro",
            "ecoscore": "a",
            "components": [
                make_component("en:carton", "en:tetra-pak", weight_g=32.0),
                make_component("en:cap", "en:hdpe-2", weight_g=3.0),
            ],
        },
        {
            "barcode": "3428274020023",
            "name": "Lactel UHT Whole Milk 1L",
            "brand": "Lactel",
            "ecoscore": "d",
            "components": [
                make_component("en:carton", "en:tetra-pak", weight_g=30.0),
            ],
        },
        {
            "barcode": "8901262150026",
            "name": "Amul Taaza Homogenised Toned Milk 1L",
            "brand": "Amul",
            "ecoscore": "c",
            "components": [
                make_component("en:carton", "en:tetra-pak", weight_g=31.0),
            ],
        },
        {
            "barcode": "8901262151016",
            "name": "Amul Gold Full Cream Milk 1L",
            "brand": "Amul",
            "ecoscore": "c",
            "components": [
                make_component("en:carton", "en:tetra-pak", weight_g=31.0),
            ],
        },
        {
            "barcode": "8901262010047",
            "name": "Amul Pasteurized Butter 500g",
            "brand": "Amul",
            "ecoscore": "d",
            "components": [
                make_component("en:box", "en:cardboard", weight_g=18.0),
                make_component("en:wrapper", "en:paper", weight_g=3.0),
            ],
        },
        {
            "barcode": "3033490004523",
            "name": "Danone Activia Strawberry Yogurt 4x125g",
            "brand": "Danone Activia",
            "ecoscore": "d",
            "components": [
                make_component("en:pot", "en:ps-6", weight_g=20.0, recycling_id="en:discard"),
                make_component("en:lid", "en:aluminium", weight_g=2.0),
                make_component("en:sleeve", "en:cardboard", weight_g=8.0),
            ],
        },
        {
            "barcode": "9340784000306",
            "name": "Chobani Greek Yogurt Plain 450g",
            "brand": "Chobani",
            "ecoscore": "c",
            "components": [
                make_component("en:tub", "en:pp-5", weight_g=22.0),
                make_component("en:lid", "en:pp-5", weight_g=5.0),
            ],
        },
        {
            "barcode": "8906032150013",
            "name": "Yakult Probiotic Drink 5x65ml",
            "brand": "Yakult",
            "ecoscore": "c",
            "components": [
                make_component("en:bottle", "en:ps-6", weight_g=15.0, recycling_id="en:discard"),
                make_component("en:lid", "en:aluminium", weight_g=2.0),
                make_component("en:wrapper", "en:ldpe-4", weight_g=4.0),
            ],
        },
        {
            "barcode": "8712100880316",
            "name": "Blue Band Margarine 500g Tub",
            "brand": "Blue Band",
            "ecoscore": "c",
            "components": [
                make_component("en:tub", "en:pp-5", weight_g=24.0),
                make_component("en:lid", "en:pp-5", weight_g=8.0),
            ],
        },

        # 4. Spreads, Sauces, Oils & Condiments
        {
            "barcode": "3017620422003",
            "name": "Nutella Hazelnut Spread with Cocoa 400g",
            "brand": "Nutella",
            "ecoscore": "d",
            "components": [
                make_component("en:jar", "en:clear-glass", weight_g=220.0),
                make_component("en:lid", "en:pp-5", weight_g=12.0),
                make_component("en:seal", "en:aluminium", weight_g=1.0),
            ],
        },
        {
            "barcode": "3017620425035",
            "name": "Nutella Hazelnut Spread with Cocoa 750g",
            "brand": "Nutella",
            "ecoscore": "d",
            "components": [
                make_component("en:jar", "en:clear-glass", weight_g=340.0),
                make_component("en:lid", "en:pp-5", weight_g=16.0),
            ],
        },
        {
            "barcode": "8715700421872",
            "name": "Heinz Tomato Ketchup 500ml Top Down Bottle",
            "brand": "Heinz",
            "ecoscore": "c",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=35.0),
                make_component("en:cap", "en:pp-5", weight_g=8.0),
            ],
        },
        {
            "barcode": "8715700016016",
            "name": "Heinz Tomato Ketchup 342g Glass Bottle",
            "brand": "Heinz",
            "ecoscore": "c",
            "components": [
                make_component("en:bottle", "en:clear-glass", weight_g=230.0),
                make_component("en:cap", "en:tinplate", weight_g=3.0),
            ],
        },
        {
            "barcode": "8715700422404",
            "name": "Heinz Yellow Mustard 400ml Squeeze Bottle",
            "brand": "Heinz",
            "ecoscore": "c",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=32.0),
                make_component("en:cap", "en:pp-5", weight_g=8.0),
            ],
        },
        {
            "barcode": "8712566008605",
            "name": "Hellmann's Real Mayonnaise 430ml Squeeze",
            "brand": "Hellmann's",
            "ecoscore": "d",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=32.0),
                make_component("en:cap", "en:pp-5", weight_g=8.0),
            ],
        },
        {
            "barcode": "8714100615438",
            "name": "Hellmann's Real Mayonnaise 400g Jar",
            "brand": "Hellmann's",
            "ecoscore": "d",
            "components": [
                make_component("en:jar", "en:clear-glass", weight_g=210.0),
                make_component("en:lid", "en:tinplate", weight_g=10.0),
            ],
        },
        {
            "barcode": "0037600105315",
            "name": "Skippy Creamy Peanut Butter 462g",
            "brand": "Skippy",
            "ecoscore": "c",
            "components": [
                make_component("en:jar", "en:pet-1", weight_g=38.0),
                make_component("en:lid", "en:pp-5", weight_g=9.0),
            ],
        },
        {
            "barcode": "3036810202652",
            "name": "Maille Traditional Dijon Mustard 215g",
            "brand": "Maille",
            "ecoscore": "c",
            "components": [
                make_component("en:jar", "en:clear-glass", weight_g=140.0),
                make_component("en:lid", "en:tinplate", weight_g=7.0),
            ],
        },
        {
            "barcode": "0011210000021",
            "name": "Tabasco Brand Pepper Sauce 60ml",
            "brand": "Tabasco",
            "ecoscore": "c",
            "components": [
                make_component("en:bottle", "en:clear-glass", weight_g=85.0),
                make_component("en:cap", "en:pp-5", weight_g=2.0),
            ],
        },
        {
            "barcode": "8715035110106",
            "name": "Kikkoman Naturally Brewed Soy Sauce 150ml Dispenser",
            "brand": "Kikkoman",
            "ecoscore": "b",
            "components": [
                make_component("en:bottle", "en:clear-glass", weight_g=160.0),
                make_component("en:cap", "en:pp-5", weight_g=10.0),
            ],
        },
        {
            "barcode": "8076809513722",
            "name": "Barilla Pesto alla Genovese 190g",
            "brand": "Barilla",
            "ecoscore": "d",
            "components": [
                make_component("en:jar", "en:clear-glass", weight_g=135.0),
                make_component("en:lid", "en:tinplate", weight_g=8.0),
            ],
        },
        {
            "barcode": "8002210113204",
            "name": "Filippo Berio Extra Virgin Olive Oil 500ml",
            "brand": "Filippo Berio",
            "ecoscore": "c",
            "components": [
                make_component("en:bottle", "en:green-glass", weight_g=350.0),
                make_component("en:cap", "en:aluminium", weight_g=4.0),
            ],
        },
        {
            "barcode": "5000111044457",
            "name": "Marmite Yeast Extract 250g Jar",
            "brand": "Marmite",
            "ecoscore": "b",
            "components": [
                make_component("en:jar", "en:clear-glass", weight_g=180.0),
                make_component("en:lid", "en:pp-5", weight_g=8.0),
            ],
        },
        {
            "barcode": "8005110170203",
            "name": "Mutti Polpa Chopped Tomatoes 400g",
            "brand": "Mutti",
            "ecoscore": "a",
            "components": [
                make_component("en:can", "en:tinplate", weight_g=45.0),
            ],
        },

        # 5. Instant Noodles, Pasta & Grains
        {
            "barcode": "8901058852356",
            "name": "Maggi 2-Minute Noodles Masala 70g",
            "brand": "Maggi",
            "ecoscore": "c",
            "components": [
                make_component("en:wrapper", "en:pp-5", weight_g=3.2, recycling_id="en:discard"),
                make_component("en:sachet", "en:multilayer", weight_g=0.8, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "8901058852424",
            "name": "Maggi 2-Minute Noodles Masala 4-Pack 280g",
            "brand": "Maggi",
            "ecoscore": "c",
            "components": [
                make_component("en:bag", "en:ldpe-4", weight_g=8.0, recycling_id="en:discard"),
                make_component("en:wrapper", "en:pp-5", weight_g=12.0, units=4, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "089686010724",
            "name": "Indomie Mi Goreng Instant Noodles 85g",
            "brand": "Indomie",
            "ecoscore": "d",
            "components": [
                make_component("en:wrapper", "en:pp-5", weight_g=3.5, recycling_id="en:discard"),
                make_component("en:sachet", "en:multilayer", weight_g=1.5, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "070662030012",
            "name": "Nissin Cup Noodles Chicken Flavor 64g",
            "brand": "Nissin",
            "ecoscore": "d",
            "components": [
                make_component("en:cup", "en:eps", weight_g=10.0, recycling_id="en:discard"),
                make_component("en:lid", "en:composite-material", weight_g=2.0, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "8801043014854",
            "name": "Nongshim Shin Ramyun Gourmet Spicy Soup 120g",
            "brand": "Nongshim",
            "ecoscore": "d",
            "components": [
                make_component("en:wrapper", "en:pp-5", weight_g=4.0, recycling_id="en:discard"),
                make_component("en:sachet", "en:multilayer", weight_g=1.0, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "8801043021487",
            "name": "Nongshim Shin Ramyun Noodle Cup 68g",
            "brand": "Nongshim",
            "ecoscore": "d",
            "components": [
                make_component("en:cup", "en:composite-material", weight_g=12.0, recycling_id="en:discard"),
                make_component("en:lid", "en:aluminium", weight_g=2.0),
            ],
        },
        {
            "barcode": "8076809511117",
            "name": "Barilla Spaghetti No.5 500g",
            "brand": "Barilla",
            "ecoscore": "b",
            "components": [
                make_component("en:box", "en:cardboard", weight_g=28.0),
            ],
        },
        {
            "barcode": "8076809511735",
            "name": "Barilla Penne Rigate No.73 500g",
            "brand": "Barilla",
            "ecoscore": "b",
            "components": [
                make_component("en:box", "en:cardboard", weight_g=28.0),
            ],
        },
        {
            "barcode": "8901491101830",
            "name": "Quaker White Oats 500g",
            "brand": "Quaker",
            "ecoscore": "a",
            "components": [
                make_component("en:carton", "en:cardboard", weight_g=45.0),
                make_component("en:lid", "en:ldpe-4", weight_g=8.0),
            ],
        },
        {
            "barcode": "5410126006957",
            "name": "Uncle Ben's Long Grain Microwave Rice 250g",
            "brand": "Uncle Ben's",
            "ecoscore": "c",
            "components": [
                make_component("en:pouch", "en:multilayer", weight_g=8.0, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "8001250120112",
            "name": "De Cecco Penne Rigate No.41 500g",
            "brand": "De Cecco",
            "ecoscore": "b",
            "components": [
                make_component("en:bag", "en:pp-5", weight_g=6.5),
            ],
        },

        # 6. Snacks, Chips, Cookies & Confectionery
        {
            "barcode": "8901491101847",
            "name": "Lay's Classic Potato Chips 50g",
            "brand": "Lay's",
            "ecoscore": "c",
            "components": [
                make_component("en:bag", "en:multilayer", weight_g=3.5, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "0028400043809",
            "name": "Lay's Classic Potato Chips 150g",
            "brand": "Lay's",
            "ecoscore": "c",
            "components": [
                make_component("en:bag", "en:multilayer", weight_g=7.5, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "8710398601811",
            "name": "Doritos Nacho Cheese Tortilla Chips 180g",
            "brand": "Doritos",
            "ecoscore": "d",
            "components": [
                make_component("en:bag", "en:multilayer", weight_g=8.5, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "0028400039987",
            "name": "Cheetos Crunchy Cheese Flavored Snacks 150g",
            "brand": "Cheetos",
            "ecoscore": "d",
            "components": [
                make_component("en:bag", "en:multilayer", weight_g=7.0, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "5053990101574",
            "name": "Pringles Original Potato Crisps 165g",
            "brand": "Pringles",
            "ecoscore": "d",
            "components": [
                make_component("en:can", "en:composite-material", weight_g=28.0, recycling_id="en:discard"),
                make_component("en:lid", "en:pp-5", weight_g=5.0),
                make_component("en:seal", "en:aluminium", weight_g=1.0),
            ],
        },
        {
            "barcode": "5053990101604",
            "name": "Pringles Sour Cream & Onion 165g",
            "brand": "Pringles",
            "ecoscore": "d",
            "components": [
                make_component("en:can", "en:composite-material", weight_g=28.0, recycling_id="en:discard"),
                make_component("en:lid", "en:pp-5", weight_g=5.0),
            ],
        },
        {
            "barcode": "8901719101038",
            "name": "Parle-G Original Gluco Biscuits 250g",
            "brand": "Parle",
            "ecoscore": "c",
            "components": [
                make_component("en:wrapper", "en:pp-5", weight_g=4.2, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "8901063012721",
            "name": "Britannia Good Day Butter Cookies 200g",
            "brand": "Britannia",
            "ecoscore": "d",
            "components": [
                make_component("en:tray", "en:pp-5", weight_g=6.0),
                make_component("en:wrapper", "en:pp-5", weight_g=4.5, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "8901063012806",
            "name": "Britannia Bourbon Chocolate Cream Biscuits 150g",
            "brand": "Britannia",
            "ecoscore": "d",
            "components": [
                make_component("en:wrapper", "en:pp-5", weight_g=4.0, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "7622210449283",
            "name": "Oreo Original Sandwich Cookies 154g",
            "brand": "Oreo",
            "ecoscore": "d",
            "components": [
                make_component("en:wrapper", "en:pp-5", weight_g=5.2, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "5000168001007",
            "name": "McVitie's Original Digestives 400g",
            "brand": "McVitie's",
            "ecoscore": "c",
            "components": [
                make_component("en:wrapper", "en:pp-5", weight_g=5.0, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "7613035221776",
            "name": "KitKat 4 Finger Milk Chocolate Bar 41.5g",
            "brand": "KitKat",
            "ecoscore": "d",
            "components": [
                make_component("en:wrapper", "en:paper", weight_g=1.8),
            ],
        },
        {
            "barcode": "5000159461122",
            "name": "Snickers Single Chocolate Bar 50g",
            "brand": "Snickers",
            "ecoscore": "d",
            "components": [
                make_component("en:wrapper", "en:pp-5", weight_g=1.5, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "5000159459228",
            "name": "Mars Single Chocolate Bar 51g",
            "brand": "Mars",
            "ecoscore": "d",
            "components": [
                make_component("en:wrapper", "en:pp-5", weight_g=1.5, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "5000159462617",
            "name": "Twix Twin Chocolate Bar 50g",
            "brand": "Twix",
            "ecoscore": "d",
            "components": [
                make_component("en:wrapper", "en:pp-5", weight_g=1.5, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "7622210286192",
            "name": "Cadbury Dairy Milk Chocolate Bar 110g",
            "brand": "Cadbury",
            "ecoscore": "d",
            "components": [
                make_component("en:wrapper", "en:pp-5", weight_g=2.5, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "3046920022606",
            "name": "Lindt Excellence 70% Cocoa Dark Chocolate 100g",
            "brand": "Lindt",
            "ecoscore": "c",
            "components": [
                make_component("en:box", "en:cardboard", weight_g=12.0),
                make_component("en:wrapper", "en:aluminium", weight_g=2.0),
            ],
        },
        {
            "barcode": "8000500003787",
            "name": "Ferrero Rocher 16 Pieces 200g",
            "brand": "Ferrero Rocher",
            "ecoscore": "e",
            "components": [
                make_component("en:box", "en:ps-6", weight_g=45.0, recycling_id="en:discard"),
                make_component("en:wrapper", "en:aluminium", weight_g=8.0),
                make_component("en:cup", "en:paper", weight_g=4.0),
            ],
        },
        {
            "barcode": "4001686301524",
            "name": "Haribo Goldbears Gummy Candy 200g",
            "brand": "Haribo",
            "ecoscore": "d",
            "components": [
                make_component("en:bag", "en:pp-5", weight_g=3.8, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "5000159407236",
            "name": "M&M's Peanut Chocolate Candies 250g",
            "brand": "M&M's",
            "ecoscore": "d",
            "components": [
                make_component("en:pouch", "en:multilayer", weight_g=6.0, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "7622210204424",
            "name": "Milka Alpine Milk Chocolate 100g",
            "brand": "Milka",
            "ecoscore": "d",
            "components": [
                make_component("en:wrapper", "en:pp-5", weight_g=2.2, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "8000500023976",
            "name": "Kinder Bueno Chocolate Wafer Bar 43g",
            "brand": "Kinder",
            "ecoscore": "e",
            "components": [
                make_component("en:wrapper", "en:pp-5", weight_g=2.0, recycling_id="en:discard"),
            ],
        },

        # 7. Canned Foods & Preserved Goods
        {
            "barcode": "5000157024671",
            "name": "Heinz Baked Beanz in Tomato Sauce 415g",
            "brand": "Heinz",
            "ecoscore": "b",
            "components": [
                make_component("en:can", "en:tinplate", weight_g=48.0),
            ],
        },
        {
            "barcode": "0051000012517",
            "name": "Campbell's Condensed Tomato Soup 298g",
            "brand": "Campbell's",
            "ecoscore": "c",
            "components": [
                make_component("en:can", "en:steel", weight_g=42.0),
                make_component("en:label", "en:paper", weight_g=2.0),
            ],
        },
        {
            "barcode": "0051000012616",
            "name": "Campbell's Cream of Mushroom Soup 298g",
            "brand": "Campbell's",
            "ecoscore": "c",
            "components": [
                make_component("en:can", "en:steel", weight_g=42.0),
            ],
        },
        {
            "barcode": "0037600124729",
            "name": "Spam Classic Luncheon Meat 340g",
            "brand": "Spam",
            "ecoscore": "d",
            "components": [
                make_component("en:can", "en:tinplate", weight_g=38.0),
            ],
        },
        {
            "barcode": "5010061001408",
            "name": "Del Monte Golden Sweet Corn Whole Kernel 400g",
            "brand": "Del Monte",
            "ecoscore": "a",
            "components": [
                make_component("en:can", "en:tinplate", weight_g=45.0),
            ],
        },
        {
            "barcode": "5000171050511",
            "name": "John West Tuna Chunks in Brine 145g",
            "brand": "John West",
            "ecoscore": "c",
            "components": [
                make_component("en:can", "en:steel", weight_g=24.0),
            ],
        },
        {
            "barcode": "3083680085304",
            "name": "Bonduelle Extra Fine Green Peas 400g",
            "brand": "Bonduelle",
            "ecoscore": "a",
            "components": [
                make_component("en:can", "en:tinplate", weight_g=46.0),
            ],
        },
        {
            "barcode": "5000157041005",
            "name": "Heinz Classic Tomato Soup 400g Can",
            "brand": "Heinz",
            "ecoscore": "b",
            "components": [
                make_component("en:can", "en:tinplate", weight_g=46.0),
            ],
        },

        # 8. Coffee & Tea
        {
            "barcode": "7613035634507",
            "name": "Nescafé Classic Instant Coffee 200g",
            "brand": "Nescafé",
            "ecoscore": "b",
            "components": [
                make_component("en:jar", "en:clear-glass", weight_g=280.0),
                make_component("en:lid", "en:pp-5", weight_g=14.0),
                make_component("en:seal", "en:aluminium", weight_g=1.0),
            ],
        },
        {
            "barcode": "7613034947479",
            "name": "Nescafé Gold Blend Instant Coffee 200g",
            "brand": "Nescafé",
            "ecoscore": "b",
            "components": [
                make_component("en:jar", "en:clear-glass", weight_g=290.0),
                make_component("en:lid", "en:pp-5", weight_g=15.0),
            ],
        },
        {
            "barcode": "8000070014702",
            "name": "Lavazza Qualità Rossa Ground Coffee 250g",
            "brand": "Lavazza",
            "ecoscore": "b",
            "components": [
                make_component("en:pouch", "en:composite-material", weight_g=8.0, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "8003753900490",
            "name": "Illy Espresso Classico Ground Coffee 250g Tin",
            "brand": "Illy",
            "ecoscore": "b",
            "components": [
                make_component("en:can", "en:tinplate", weight_g=85.0),
                make_component("en:lid", "en:pp-5", weight_g=8.0),
            ],
        },
        {
            "barcode": "8714100773664",
            "name": "Lipton Yellow Label Black Tea 100 Tea Bags 200g",
            "brand": "Lipton",
            "ecoscore": "b",
            "components": [
                make_component("en:box", "en:cardboard", weight_g=32.0),
            ],
        },
        {
            "barcode": "5055953901114",
            "name": "Twinings English Breakfast Tea 50 Bags 125g",
            "brand": "Twinings",
            "ecoscore": "b",
            "components": [
                make_component("en:box", "en:cardboard", weight_g=25.0),
            ],
        },
        {
            "barcode": "5711953043818",
            "name": "Starbucks Frappuccino Coffee Drink 250ml",
            "brand": "Starbucks",
            "ecoscore": "c",
            "components": [
                make_component("en:bottle", "en:clear-glass", weight_g=175.0),
                make_component("en:cap", "en:tinplate", weight_g=3.0),
            ],
        },
        {
            "barcode": "8711000514658",
            "name": "Douwe Egberts Pure Gold Instant Coffee 190g Jar",
            "brand": "Douwe Egberts",
            "ecoscore": "b",
            "components": [
                make_component("en:jar", "en:clear-glass", weight_g=270.0),
                make_component("en:lid", "en:clear-glass", weight_g=40.0),
            ],
        },

        # 9. Personal Care, Shampoo & Toiletries
        {
            "barcode": "4015600850937",
            "name": "Head & Shoulders Classic Clean Anti-Dandruff Shampoo 400ml",
            "brand": "Head & Shoulders",
            "ecoscore": "d",
            "components": [
                make_component("en:bottle", "en:hdpe-2", weight_g=38.0),
                make_component("en:cap", "en:pp-5", weight_g=6.0),
            ],
        },
        {
            "barcode": "8001090224156",
            "name": "Pantene Pro-V Repair & Protect Shampoo 400ml",
            "brand": "Pantene",
            "ecoscore": "d",
            "components": [
                make_component("en:bottle", "en:hdpe-2", weight_g=38.0),
                make_component("en:cap", "en:pp-5", weight_g=6.0),
            ],
        },
        {
            "barcode": "8712561580212",
            "name": "Dove Deeply Nourishing Body Wash 500ml",
            "brand": "Dove",
            "ecoscore": "c",
            "components": [
                make_component("en:bottle", "en:hdpe-2", weight_g=42.0),
                make_component("en:cap", "en:pp-5", weight_g=7.0),
            ],
        },
        {
            "barcode": "4005808125012",
            "name": "Nivea Creme Blue Tin 150ml",
            "brand": "Nivea",
            "ecoscore": "b",
            "components": [
                make_component("en:tin", "en:aluminium", weight_g=18.0),
            ],
        },
        {
            "barcode": "4005808890507",
            "name": "Nivea Soft Moisturizing Cream 200ml",
            "brand": "Nivea",
            "ecoscore": "c",
            "components": [
                make_component("en:tub", "en:pp-5", weight_g=22.0),
                make_component("en:lid", "en:pp-5", weight_g=8.0),
            ],
        },
        {
            "barcode": "8718951280381",
            "name": "Colgate Total Original Toothpaste 100ml",
            "brand": "Colgate",
            "ecoscore": "b",
            "components": [
                make_component("en:tube", "en:hdpe-2", weight_g=14.0),
                make_component("en:cap", "en:pp-5", weight_g=4.0),
                make_component("en:box", "en:cardboard", weight_g=12.0),
            ],
        },
        {
            "barcode": "8001090124807",
            "name": "Oral-B Pro-Expert Deep Clean Toothpaste 75ml",
            "brand": "Oral-B",
            "ecoscore": "c",
            "components": [
                make_component("en:tube", "en:multilayer", weight_g=15.0, recycling_id="en:discard"),
                make_component("en:box", "en:cardboard", weight_g=10.0),
            ],
        },
        {
            "barcode": "3574660447385",
            "name": "Listerine Cool Mint Mouthwash 500ml",
            "brand": "Listerine",
            "ecoscore": "c",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=32.0),
                make_component("en:cap", "en:hdpe-2", weight_g=6.0),
            ],
        },
        {
            "barcode": "7702018042456",
            "name": "Gillette Series Protection Shaving Foam 250ml",
            "brand": "Gillette",
            "ecoscore": "c",
            "components": [
                make_component("en:aerosol", "en:aluminium", weight_g=42.0),
                make_component("en:cap", "en:pp-5", weight_g=4.0),
            ],
        },
        {
            "barcode": "8717644338902",
            "name": "Rexona Men Cobalt Dry Antiperspirant 150ml",
            "brand": "Rexona",
            "ecoscore": "c",
            "components": [
                make_component("en:aerosol", "en:aluminium", weight_g=28.0),
                make_component("en:cap", "en:pp-5", weight_g=3.0),
            ],
        },
        {
            "barcode": "8901396388401",
            "name": "Dettol Liquid Handwash Original with Pump 250ml",
            "brand": "Dettol",
            "ecoscore": "c",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=22.0),
                make_component("en:cap", "en:pp-5", weight_g=12.0, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "4005900135766",
            "name": "Eucerin pH5 Shower Oil 400ml",
            "brand": "Eucerin",
            "ecoscore": "c",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=34.0),
                make_component("en:cap", "en:pp-5", weight_g=6.0),
            ],
        },

        # 10. Household Cleaning & Laundry
        {
            "barcode": "8001841369792",
            "name": "Fairy Original Dishwashing Liquid 433ml",
            "brand": "Fairy",
            "ecoscore": "b",
            "components": [
                make_component("en:bottle", "en:rpet", weight_g=26.0),
                make_component("en:cap", "en:pp-5", weight_g=5.0),
            ],
        },
        {
            "barcode": "0037000103756",
            "name": "Dawn Ultra Dishwashing Liquid Original 473ml",
            "brand": "Dawn",
            "ecoscore": "b",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=28.0),
                make_component("en:cap", "en:pp-5", weight_g=5.0),
            ],
        },
        {
            "barcode": "8001841893327",
            "name": "Ariel Original All-in-1 PODS Washing Capsules 30 Pods",
            "brand": "Ariel",
            "ecoscore": "b",
            "components": [
                make_component("en:box", "en:cardboard", weight_g=65.0),
            ],
        },
        {
            "barcode": "8001090886613",
            "name": "Ariel Liquid Detergent Original 1.3L",
            "brand": "Ariel",
            "ecoscore": "c",
            "components": [
                make_component("en:bottle", "en:hdpe-2", weight_g=75.0),
                make_component("en:cap", "en:pp-5", weight_g=12.0),
            ],
        },
        {
            "barcode": "4015000969567",
            "name": "Persil Universal 4in1 DISCS Laundry Detergent",
            "brand": "Persil",
            "ecoscore": "c",
            "components": [
                make_component("en:box", "en:pp-5", weight_g=55.0),
            ],
        },
        {
            "barcode": "8901396340515",
            "name": "Harpic Power Plus Toilet Cleaner Original 750ml",
            "brand": "Harpic",
            "ecoscore": "d",
            "components": [
                make_component("en:bottle", "en:hdpe-2", weight_g=48.0),
                make_component("en:cap", "en:pp-5", weight_g=9.0),
            ],
        },
        {
            "barcode": "8717163625446",
            "name": "Cif Cream Surface Cleaner Lemon 500ml",
            "brand": "Cif",
            "ecoscore": "c",
            "components": [
                make_component("en:bottle", "en:hdpe-2", weight_g=36.0),
                make_component("en:cap", "en:pp-5", weight_g=6.0),
            ],
        },
        {
            "barcode": "5000204702110",
            "name": "Mr Muscle Multi-Task Trigger Surface Cleaner 500ml",
            "brand": "Mr Muscle",
            "ecoscore": "c",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=30.0),
                make_component("en:cap", "en:pp-5", weight_g=18.0, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "8001090022359",
            "name": "Febreze Air Mist Blossom & Breeze Air Freshener 300ml",
            "brand": "Febreze",
            "ecoscore": "c",
            "components": [
                make_component("en:aerosol", "en:aluminium", weight_g=36.0),
                make_component("en:cap", "en:pp-5", weight_g=8.0),
            ],
        },
        {
            "barcode": "5410076410422",
            "name": "Lenor Spring Awakening Fabric Conditioner 1L",
            "brand": "Lenor",
            "ecoscore": "c",
            "components": [
                make_component("en:bottle", "en:pet-1", weight_g=40.0),
                make_component("en:cap", "en:pp-5", weight_g=8.0),
            ],
        },

        # 11. Beers, Ciders & Wines
        {
            "barcode": "8712000025114",
            "name": "Heineken Premium Lager Beer 330ml Glass Bottle",
            "brand": "Heineken",
            "ecoscore": "d",
            "components": [
                make_component("en:bottle", "en:green-glass", weight_g=210.0),
                make_component("en:cap", "en:steel", weight_g=2.0),
            ],
        },
        {
            "barcode": "8712000025091",
            "name": "Heineken Premium Lager Beer 330ml Can",
            "brand": "Heineken",
            "ecoscore": "c",
            "components": [
                make_component("en:can", "en:aluminium", weight_g=14.0),
            ],
        },
        {
            "barcode": "7501064191310",
            "name": "Corona Extra Premium Mexican Beer 355ml Bottle",
            "brand": "Corona",
            "ecoscore": "d",
            "components": [
                make_component("en:bottle", "en:clear-glass", weight_g=230.0),
                make_component("en:cap", "en:steel", weight_g=2.0),
            ],
        },
        {
            "barcode": "5000213010534",
            "name": "Guinness Draught Stout 440ml Can",
            "brand": "Guinness",
            "ecoscore": "c",
            "components": [
                make_component("en:can", "en:aluminium", weight_g=16.0),
                make_component("en:lid", "en:pp-5", weight_g=3.0),
            ],
        },
        {
            "barcode": "5410228141471",
            "name": "Stella Artois Belgian Lager 330ml Glass Bottle",
            "brand": "Stella Artois",
            "ecoscore": "d",
            "components": [
                make_component("en:bottle", "en:green-glass", weight_g=205.0),
                make_component("en:cap", "en:steel", weight_g=2.0),
            ],
        },
        {
            "barcode": "0018200007715",
            "name": "Budweiser King of Beers 355ml Can",
            "brand": "Budweiser",
            "ecoscore": "d",
            "components": [
                make_component("en:can", "en:aluminium", weight_g=14.5),
            ],
        },
        {
            "barcode": "5740700900236",
            "name": "Carlsberg Danish Pilsner 500ml Can",
            "brand": "Carlsberg",
            "ecoscore": "c",
            "components": [
                make_component("en:can", "en:aluminium", weight_g=18.0),
            ],
        },
        {
            "barcode": "9322214006129",
            "name": "Yellow Tail Shiraz Red Wine 750ml",
            "brand": "Yellow Tail",
            "ecoscore": "d",
            "components": [
                make_component("en:bottle", "en:green-glass", weight_g=450.0),
                make_component("en:cap", "en:aluminium", weight_g=4.0),
            ],
        },
        {
            "barcode": "5010103917889",
            "name": "Strongbow Original Apple Cider 440ml Can",
            "brand": "Strongbow",
            "ecoscore": "c",
            "components": [
                make_component("en:can", "en:aluminium", weight_g=15.0),
            ],
        },

        # 12. Baby Food & Pet Food
        {
            "barcode": "0015000070024",
            "name": "Gerber Pureed Apple Baby Food 113g",
            "brand": "Gerber",
            "ecoscore": "a",
            "components": [
                make_component("en:jar", "en:clear-glass", weight_g=88.0),
                make_component("en:lid", "en:tinplate", weight_g=6.0),
            ],
        },
        {
            "barcode": "5000159418201",
            "name": "Pedigree Dog Food with Chicken & Rice 400g Tin",
            "brand": "Pedigree",
            "ecoscore": "c",
            "components": [
                make_component("en:can", "en:steel", weight_g=45.0),
            ],
        },
        {
            "barcode": "4008429085802",
            "name": "Whiskas Cat Food Chicken in Gravy 85g Pouch",
            "brand": "Whiskas",
            "ecoscore": "d",
            "components": [
                make_component("en:pouch", "en:multilayer", weight_g=3.5, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "7613035308606",
            "name": "Felix As Good As It Looks Cat Food 100g Pouch",
            "brand": "Purina Felix",
            "ecoscore": "d",
            "components": [
                make_component("en:pouch", "en:multilayer", weight_g=4.0, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "5900951016554",
            "name": "Sheba Fine Flakes in Jelly 85g Pouch",
            "brand": "Sheba",
            "ecoscore": "d",
            "components": [
                make_component("en:pouch", "en:multilayer", weight_g=3.8, recycling_id="en:discard"),
            ],
        },

        # 13. Electronics, Batteries & Hardware (E-Waste Domain)
        {
            "barcode": "5000394077058",
            "name": "Duracell Plus AA Alkaline Batteries 4-Pack",
            "brand": "Duracell",
            "ecoscore": "e",
            "components": [
                make_component("en:box", "en:battery", weight_g=96.0, recycling_id="en:recycle"),
                make_component("en:sheet", "en:cardboard", weight_g=8.0),
                make_component("en:clamshell", "en:pet-1", weight_g=4.0),
            ],
        },
        {
            "barcode": "7638900012354",
            "name": "Energizer Max AAA Alkaline Batteries 4-Pack",
            "brand": "Energizer",
            "ecoscore": "e",
            "components": [
                make_component("en:box", "en:battery", weight_g=48.0, recycling_id="en:recycle"),
                make_component("en:sheet", "en:cardboard", weight_g=6.0),
                make_component("en:clamshell", "en:pet-1", weight_g=3.0),
            ],
        },
        {
            "barcode": "5410853036495",
            "name": "Panasonic CR2032 3V Lithium Coin Cell 2-Pack",
            "brand": "Panasonic",
            "ecoscore": "e",
            "components": [
                make_component("en:box", "en:battery", weight_g=6.0, recycling_id="en:recycle"),
                make_component("en:blister-pack", "en:composite-material", weight_g=5.0, recycling_id="en:discard"),
            ],
        },
        {
            "barcode": "4008496674688",
            "name": "Varta Longlife Power 9V Alkaline Battery",
            "brand": "Varta",
            "ecoscore": "e",
            "components": [
                make_component("en:box", "en:battery", weight_g=46.0, recycling_id="en:recycle"),
                make_component("en:blister-pack", "en:cardboard", weight_g=6.0),
            ],
        },
        {
            "barcode": "4902580131456",
            "name": "Maxell LR44 Micro Alkaline Coin Cell 1.5V 2-Pack",
            "brand": "Maxell",
            "ecoscore": "e",
            "components": [
                make_component("en:box", "en:battery", weight_g=4.0, recycling_id="en:recycle"),
                make_component("en:blister-pack", "en:composite-material", weight_g=4.0, recycling_id="en:discard"),
            ],
        },

        # 14. Organic & Certified Compostable Products
        {
            "barcode": "5055953901459",
            "name": "Twinings Pure Peppermint Herbal Tea 20 Bags 40g",
            "brand": "Twinings",
            "ecoscore": "a",
            "components": [
                make_component("en:box", "en:paperboard", weight_g=14.0),
                make_component("en:sachet", "en:pla", weight_g=8.0, recycling_id="en:compost"),
            ],
        },
        {
            "barcode": "5060229011701",
            "name": "Pukka Herbs Organic Three Ginger 20 Tea Bags",
            "brand": "Pukka Herbs",
            "ecoscore": "a",
            "components": [
                make_component("en:box", "en:cardboard", weight_g=18.0),
                make_component("en:envelope", "en:paper", weight_g=10.0),
                make_component("en:sachet", "en:pla", weight_g=8.0, recycling_id="en:compost"),
            ],
        },
        {
            "barcode": "7090001140019",
            "name": "BioBag 100% Certified Compostable Waste Bags 10L",
            "brand": "BioBag",
            "ecoscore": "a",
            "components": [
                make_component("en:bag", "en:biodegradable-plastic", weight_g=80.0, recycling_id="en:compost"),
                make_component("en:sleeve", "en:paper", weight_g=6.0),
            ],
        },
        {
            "barcode": "4006381333634",
            "name": "Stabilo Green Colors FSC Certified Wood Pencils",
            "brand": "Stabilo",
            "ecoscore": "a",
            "components": [
                make_component("en:box", "en:cardboard", weight_g=22.0),
                make_component("en:envelope", "en:wood", weight_g=85.0, recycling_id="en:compost"),
            ],
        },
    ]

    processed = []
    now_ms = int(time.time() * 1000)
    for raw in products_raw:
        barcode = normalize_ean13(raw["barcode"])
        if barcode is None:
            print(f"  SKIP curated product with invalid barcode: {raw.get('barcode')!r} "
                  f"product={raw.get('name')!r} (fix the source EAN-13, do not pad/truncate)")
            continue
        components = raw.get("components", [])

        # Determine primary component (heaviest, or first)
        primary = None
        max_wt = -1.0
        for comp in components:
            wt = comp.get("weight_measured") or comp.get("weight") or 0.0
            if wt > max_wt:
                max_wt = wt
                primary = comp

        if not primary and components:
            primary = components[0]

        # Resolve category & subclass
        category = "Recyclable"
        subclass = "Plastic"
        if primary:
            mat_tag = primary.get("material", {}).get("id")
            shape_tag = primary.get("shape", {}).get("id")
            mapping = get_material_mapping(mat_tag) or get_shape_fallback(shape_tag)
            if mapping:
                category, subclass = mapping
            else:
                category, subclass = ("Trash", "Miscellaneous Trash")

        # Extract materials list
        mat_names = []
        for comp in components:
            m = comp.get("material", {}).get("lc_name")
            if m and m not in mat_names:
                mat_names.append(m)

        materials_str = ", ".join(mat_names) if mat_names else subclass
        total_weight = sum(
            c.get("weight_measured") or c.get("weight") or 0.0 for c in components
        )

        item = {
            "barcode": barcode,
            "productName": raw.get("name"),
            "brand": raw.get("brand"),
            "category": category,
            "subclass": subclass,
            "materials": materials_str,
            "componentsJson": json.dumps(components),
            "weightGrams": round(total_weight, 2) if total_weight > 0 else None,
            "ecoscore": raw.get("ecoscore"),
            "source": "preloaded",
            "packagingsComplete": 1,
            "lastAccessed": 0,
            "cachedAt": now_ms,
        }
        processed.append(item)

    return processed


# ─────────────────────────────────────────────────────────────────────────────
# DuckDB Parquet Ingestion Mode
# ─────────────────────────────────────────────────────────────────────────────

def build_from_duckdb(
    output_path: Path,
    input_source: Optional[str] = None,
    limit: int = 1000,
    verbose: bool = False,
) -> int:
    """Queries Open Food Facts Parquet dataset via DuckDB and generates SQLite database."""
    try:
        import duckdb
    except ImportError:
        print("ERROR: DuckDB is not installed in the current Python environment.")
        print("Install it with: pip install duckdb")
        print("Falling back to sample mode...")
        return build_sample_db(output_path, verbose=verbose)

    source = input_source or "hf://datasets/openfoodfacts/product-database/data/en.openfoodfacts.org.products.parquet"
    print(f"Connecting to DuckDB and querying Parquet source: {source}")

    con = duckdb.connect()
    # Query products with code and packaging tags
    query = f"""
        SELECT
            code,
            product_name,
            brands,
            packaging_materials_tags,
            packaging_shapes_tags,
            packaging_recycling_tags,
            ecoscore_grade
        FROM read_parquet('{source}')
        WHERE code IS NOT NULL
          AND length(code) >= 8
          AND (packaging_materials_tags IS NOT NULL OR packaging_shapes_tags IS NOT NULL)
        LIMIT {limit}
    """

    try:
        cursor = con.execute(query)
    except Exception as e:
        print(f"DuckDB query error: {e}")
        print("Falling back to sample mode...")
        return build_sample_db(output_path, verbose=verbose)

    conn = init_db(output_path)
    count = 0
    now_ms = int(time.time() * 1000)

    for row in cursor.fetchall():
        raw_code = str(row[0]).strip()
        barcode = normalize_ean13(raw_code)
        if barcode is None:
            if verbose:
                print(f"  SKIP invalid barcode from DuckDB: {raw_code!r}")
            continue
        product_name = row[1]
        brand = row[2]
        mat_tags = row[3] if isinstance(row[3], list) else []
        shape_tags = row[4] if isinstance(row[4], list) else []
        rec_tags = row[5] if isinstance(row[5], list) else []
        ecoscore = row[6]

        primary_mat = mat_tags[0] if mat_tags else None
        primary_shape = shape_tags[0] if shape_tags else None
        mapping = get_material_mapping(primary_mat) or get_shape_fallback(primary_shape)
        category, subclass = mapping if mapping else ("Trash", "Miscellaneous Trash")

        components = []
        max_len = max(len(mat_tags), len(shape_tags), 1)
        for i in range(max_len):
            m_tag = mat_tags[i] if i < len(mat_tags) else primary_mat
            s_tag = shape_tags[i] if i < len(shape_tags) else primary_shape
            r_tag = rec_tags[i] if i < len(rec_tags) else "en:recycle"
            components.append(
                make_component(
                    s_tag or "en:container",
                    m_tag or "en:plastic",
                    weight_g=None,
                    recycling_id=r_tag or "en:recycle",
                )
            )

        mat_names = [humanize_tag(m) for m in mat_tags if m]
        materials_str = ", ".join(mat_names) if mat_names else subclass

        item = {
            "barcode": barcode,
            "productName": product_name,
            "brand": brand,
            "category": category,
            "subclass": subclass,
            "materials": materials_str,
            "componentsJson": json.dumps(components),
            "weightGrams": None,
            "ecoscore": ecoscore,
            "source": "preloaded",
            "packagingsComplete": 1,
            "lastAccessed": 0,
            "cachedAt": now_ms,
        }
        if insert_product(conn, item):
            count += 1

    optimize_db(conn)
    conn.close()
    print(f"Successfully imported {count} products via DuckDB into {output_path}")
    return count


# ─────────────────────────────────────────────────────────────────────────────
# Open Food Facts API Ingestion Mode
# ─────────────────────────────────────────────────────────────────────────────

def build_from_api(
    output_path: Path,
    limit: int = 100,
    verbose: bool = False,
) -> int:
    """Fetches real products from Open Food Facts search API and stores in SQLite."""
    import urllib.parse
    import urllib.request

    print(f"Fetching up to {limit} products from Open Food Facts API v2...")
    conn = init_db(output_path)
    count = 0
    now_ms = int(time.time() * 1000)
    page_size = min(limit, 50)
    page = 1

    while count < limit:
        url = (
            f"https://world.openfoodfacts.org/api/v2/search?"
            f"fields=code,product_name,brands,packaging_materials_tags,packaging_shapes_tags,packaging_recycling_tags,ecoscore_grade,packagings_complete&"
            f"page_size={page_size}&page={page}"
        )
        if verbose:
            print(f"Requesting: {url}")
        req = urllib.request.Request(url, headers={"User-Agent": "WasegMul - Android Waste Segregator"})
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                data = json.loads(resp.read().decode("utf-8"))
        except Exception as e:
            print(f"API request failed: {e}")
            break

        products = data.get("products", [])
        if not products:
            break

        for p in products:
            raw_code = p.get("code")
            if not raw_code:
                continue
            barcode = normalize_ean13(raw_code)
            if barcode is None:
                if verbose:
                    print(f"  SKIP invalid barcode from API: {raw_code!r}")
                continue

            mat_tags = p.get("packaging_materials_tags", [])
            shape_tags = p.get("packaging_shapes_tags", [])
            rec_tags = p.get("packaging_recycling_tags", [])
            primary_mat = mat_tags[0] if mat_tags else None
            primary_shape = shape_tags[0] if shape_tags else None
            mapping = get_material_mapping(primary_mat) or get_shape_fallback(primary_shape)
            category, subclass = mapping if mapping else ("Trash", "Miscellaneous Trash")

            components = []
            max_len = max(len(mat_tags), len(shape_tags), 1)
            for i in range(max_len):
                m_tag = mat_tags[i] if i < len(mat_tags) else primary_mat
                s_tag = shape_tags[i] if i < len(shape_tags) else primary_shape
                r_tag = rec_tags[i] if i < len(rec_tags) else "en:recycle"
                components.append(
                    make_component(
                        s_tag or "en:container",
                        m_tag or "en:plastic",
                        weight_g=None,
                        recycling_id=r_tag or "en:recycle",
                    )
                )

            item = {
                "barcode": barcode,
                "productName": p.get("product_name"),
                "brand": p.get("brands"),
                "category": category,
                "subclass": subclass,
                "materials": ", ".join(humanize_tag(m) for m in mat_tags if m) or subclass,
                "componentsJson": json.dumps(components),
                "weightGrams": None,
                "ecoscore": p.get("ecoscore_grade"),
                "source": "preloaded",
                "packagingsComplete": p.get("packagings_complete", 1),
                "lastAccessed": 0,
                "cachedAt": now_ms,
            }
            if insert_product(conn, item):
                count += 1
            if count >= limit:
                break

        page += 1

    optimize_db(conn)
    conn.close()
    print(f"Successfully fetched {count} products via OFF API into {output_path}")
    return count


# ─────────────────────────────────────────────────────────────────────────────
# JSONL File Ingestion Mode
# ─────────────────────────────────────────────────────────────────────────────

def build_from_jsonl(
    output_path: Path,
    input_file: Path,
    limit: Optional[int] = None,
    verbose: bool = False,
) -> int:
    """Parses local Open Food Facts JSONL export file into SQLite."""
    import gzip

    print(f"Parsing Open Food Facts JSONL from {input_file}...")
    conn = init_db(output_path)
    count = 0
    now_ms = int(time.time() * 1000)

    is_gz = str(input_file).endswith(".gz")
    opener = gzip.open(input_file, "rt", encoding="utf-8") if is_gz else open(input_file, "r", encoding="utf-8")

    with opener as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                p = json.loads(line)
            except json.JSONDecodeError:
                continue

            raw_code = p.get("code")
            if not raw_code:
                continue

            barcode_jsonl = normalize_ean13(raw_code)
            if barcode_jsonl is None:
                if verbose:
                    print(f"  SKIP invalid barcode from JSONL: {raw_code!r}")
                continue

            mat_tags = p.get("packaging_materials_tags", [])
            shape_tags = p.get("packaging_shapes_tags", [])
            primary_mat = mat_tags[0] if mat_tags else None
            primary_shape = shape_tags[0] if shape_tags else None
            mapping = get_material_mapping(primary_mat) or get_shape_fallback(primary_shape)
            category, subclass = mapping if mapping else ("Trash", "Miscellaneous Trash")

            components = []
            max_len = max(len(mat_tags), len(shape_tags), 1)
            for i in range(max_len):
                m_tag = mat_tags[i] if i < len(mat_tags) else primary_mat
                s_tag = shape_tags[i] if i < len(shape_tags) else primary_shape
                components.append(
                    make_component(
                        s_tag or "en:container",
                        m_tag or "en:plastic",
                        weight_g=None,
                    )
                )

            item = {
                "barcode": barcode_jsonl,
                "productName": p.get("product_name"),
                "brand": p.get("brands"),
                "category": category,
                "subclass": subclass,
                "materials": ", ".join(humanize_tag(m) for m in mat_tags if m) or subclass,
                "componentsJson": json.dumps(components),
                "weightGrams": None,
                "ecoscore": p.get("ecoscore_grade"),
                "source": "preloaded",
                "packagingsComplete": 1,
                "lastAccessed": 0,
                "cachedAt": now_ms,
            }
            if insert_product(conn, item):
                count += 1
            if limit and count >= limit:
                break

    optimize_db(conn)
    conn.close()
    print(f"Successfully loaded {count} products from JSONL into {output_path}")
    return count


# ─────────────────────────────────────────────────────────────────────────────
# Sample / Curated Mode (Default)
# ─────────────────────────────────────────────────────────────────────────────

def build_sample_db(output_path: Path, verbose: bool = False) -> int:
    """Builds pre-loaded SQLite database with 100+ top FMCG products."""
    print(f"Generating curated sample database with 100+ top FMCG products...")
    conn = init_db(output_path)
    products = get_curated_fmcg_products()

    category_counts: Dict[str, int] = {}
    subclass_counts: Dict[str, int] = {}

    for item in products:
        if not insert_product(conn, item):
            continue  # already logged inside insert_product
        category_counts[item["category"]] = category_counts.get(item["category"], 0) + 1
        subclass_counts[item["subclass"]] = subclass_counts.get(item["subclass"], 0) + 1
        if verbose:
            print(f"  [{item['barcode']}] {item['productName']} -> {item['category']} / {item['subclass']}")

    optimize_db(conn)
    conn.close()

    file_size_kb = round(os.path.getsize(output_path) / 1024, 1)
    print(f"Database successfully generated at: {output_path} ({file_size_kb} KB)")
    inserted = sum(category_counts.values())
    print(f"Total products inserted: {inserted} (curated candidates: {len(products)})")
    print("Breakdown by Category:")
    for cat, cnt in sorted(category_counts.items()):
        print(f"  - {cat}: {cnt}")
    print("Breakdown by Subclass:")
    for sub, cnt in sorted(subclass_counts.items(), key=lambda x: -x[1]):
        print(f"  - {sub}: {cnt}")

    return inserted


# ─────────────────────────────────────────────────────────────────────────────
# CLI Entry Point
# ─────────────────────────────────────────────────────────────────────────────

def main() -> None:
    root_dir = Path(__file__).resolve().parent.parent
    default_output = root_dir / "app" / "src" / "main" / "assets" / "database" / "wasegmul_barcode_offline.db"

    parser = argparse.ArgumentParser(
        description="Build offline barcode packaging database for WasegMul",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "-o", "--output",
        type=Path,
        default=default_output,
        help="Target SQLite database output file path",
    )
    parser.add_argument(
        "--mode",
        choices=["sample", "duckdb", "api", "jsonl"],
        default="sample",
        help="ETL source mode to build database from",
    )
    parser.add_argument(
        "--sample", "--dry-run",
        action="store_true",
        help="Convenience alias to force sample mode (100+ curated products)",
    )
    parser.add_argument(
        "--input",
        type=str,
        default=None,
        help="Input source (file path for JSONL or local Parquet file path for DuckDB)",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=1000,
        help="Maximum products to import in duckdb, api, or jsonl modes",
    )
    parser.add_argument(
        "-v", "--verbose",
        action="store_true",
        help="Enable detailed verbose output during ETL",
    )

    args = parser.parse_args()

    mode = "sample" if args.sample else args.mode

    if mode == "sample":
        build_sample_db(args.output, verbose=args.verbose)
    elif mode == "duckdb":
        build_from_duckdb(args.output, input_source=args.input, limit=args.limit, verbose=args.verbose)
    elif mode == "api":
        build_from_api(args.output, limit=args.limit, verbose=args.verbose)
    elif mode == "jsonl":
        if not args.input:
            print("ERROR: --input file path must be specified when using --mode jsonl")
            sys.exit(1)
        build_from_jsonl(args.output, Path(args.input), limit=args.limit, verbose=args.verbose)


if __name__ == "__main__":
    main()
