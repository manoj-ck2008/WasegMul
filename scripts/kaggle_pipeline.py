#!/usr/bin/env python3
"""
WasegMul Kaggle Cloud Training Pipeline Orchestrator.

Automates the complete lifecycle of remote model training on Kaggle GPU clusters:
  1. Stages training scripts, taxonomy configurations, and kernel metadata.
  2. Pushes the job to Kaggle via Kaggle CLI with GPU acceleration enabled.
  3. Monitors execution telemetry, loss curves, and epoch status.
  4. Automatically downloads trained weights, evaluation graphs, and exported TFLite models.
  5. Syncs ready-to-use models into app/src/main/assets/.

Requirements:
  - Kaggle API token configured (~/.kaggle/access_token or KAGGLE_API_TOKEN env)
  - Python 3.8+ with kaggle, pyyaml

Usage:
  # Check status of ongoing Kaggle training
  python scripts/kaggle_pipeline.py status

  # Push and launch training on Kaggle GPU
  python scripts/kaggle_pipeline.py push

  # Download trained artifacts and export to Android assets
  python scripts/kaggle_pipeline.py output --sync-assets

  # List accessible Kaggle datasets
  python scripts/kaggle_pipeline.py datasets
"""

import argparse
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Optional, List

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent
STAGING_DIR = PROJECT_ROOT / ".kaggle_staging"
OUTPUT_DIR = PROJECT_ROOT / "runs" / "kaggle_output"
ASSETS_DIR = PROJECT_ROOT / "app" / "src" / "main" / "assets"
TAXONOMY_PATH = PROJECT_ROOT / "taxonomy.yaml"
TRAIN_SCRIPT = SCRIPT_DIR / "kaggle_train.py"

DEFAULT_USERNAME = "manojkari"
DEFAULT_SLUG = "wasegmul-yolo11n-waste-training"
DEFAULT_TITLE = "WasegMul YOLO11n Waste Training"
DEFAULT_DATASETS = ["manojkari/taco-dataset1"]


def run_kaggle_cmd(args: List[str]) -> subprocess.CompletedProcess:
    """Execute Kaggle CLI command directly or through python module fallback."""
    env = os.environ.copy()
    scripts_dir = str(Path(sys.executable).parent / "Scripts")
    if scripts_dir not in env.get("PATH", ""):
        env["PATH"] = f"{env.get('PATH', '')};{scripts_dir}"

    # Try calling 'kaggle' directly
    try:
        return subprocess.run(["kaggle"] + args, check=True, text=True, capture_output=True, env=env)
    except (FileNotFoundError, subprocess.CalledProcessError):
        # Fallback to python -c invocation using kaggle.cli
        python_cmd = [
            sys.executable,
            "-c",
            "import kaggle.cli, sys; sys.argv=['kaggle'] + sys.argv[1:]; kaggle.cli.main()"
        ] + args
        return subprocess.run(python_cmd, check=True, text=True, capture_output=True, env=env)


def cmd_datasets(args: argparse.Namespace) -> None:
    """List accessible Kaggle datasets."""
    print("Listing your Kaggle datasets...")
    try:
        proc = run_kaggle_cmd(["datasets", "list", "--mine"])
        print(proc.stdout)
    except subprocess.CalledProcessError as e:
        print(f"Error querying datasets: {e.stderr or e.stdout}")
        sys.exit(1)


def prepare_staging(username: str, slug: str, title: str, datasets: List[str]) -> Path:
    """Create staging directory with code, taxonomy, and kernel-metadata.json."""
    if STAGING_DIR.exists():
        shutil.rmtree(STAGING_DIR)
    STAGING_DIR.mkdir(parents=True, exist_ok=True)

    if not TRAIN_SCRIPT.exists():
        raise FileNotFoundError(f"Training script not found: {TRAIN_SCRIPT}")
    shutil.copy2(TRAIN_SCRIPT, STAGING_DIR / "kaggle_train.py")

    if TAXONOMY_PATH.exists():
        shutil.copy2(TAXONOMY_PATH, STAGING_DIR / "taxonomy.yaml")

    metadata = {
        "id": f"{username}/{slug}",
        "title": title,
        "code_file": "kaggle_train.py",
        "language": "python",
        "kernel_type": "script",
        "is_private": "true",
        "enable_gpu": "true",
        "enable_tpu": "false",
        "enable_internet": "true",
        "dataset_sources": datasets,
        "competition_sources": [],
        "kernel_sources": []
    }

    metadata_path = STAGING_DIR / "kernel-metadata.json"
    with open(metadata_path, "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2)

    return STAGING_DIR


def cmd_push(args: argparse.Namespace) -> None:
    """Stage files and push kernel to Kaggle."""
    kernel_id = f"{args.username}/{args.slug}"
    print(f"Preparing staging environment for {kernel_id}...")
    prepare_staging(args.username, args.slug, args.title, args.datasets)

    print(f"Pushing kernel to Kaggle (GPU enabled, attached datasets: {', '.join(args.datasets)})...")
    try:
        proc = run_kaggle_cmd(["kernels", "push", "-p", str(STAGING_DIR)])
        print(proc.stdout)
        print(f"\nKernel successfully dispatched to Kaggle!")
        print(f"Track status with: python scripts/kaggle_pipeline.py status --slug {args.slug}")
    except subprocess.CalledProcessError as e:
        print(f"Failed to push kernel: {e.stderr or e.stdout}")
        sys.exit(1)


def cmd_status(args: argparse.Namespace) -> None:
    """Check kernel execution status."""
    kernel_id = f"{args.username}/{args.slug}"
    print(f"Checking status for {kernel_id}...")
    try:
        proc = run_kaggle_cmd(["kernels", "status", kernel_id])
        print(proc.stdout)
    except subprocess.CalledProcessError as e:
        print(f"Failed to get kernel status: {e.stderr or e.stdout}")
        sys.exit(1)


def cmd_output(args: argparse.Namespace) -> None:
    """Download output artifacts from completed Kaggle run."""
    kernel_id = f"{args.username}/{args.slug}"
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    print(f"Downloading artifacts for {kernel_id} to {OUTPUT_DIR}...")
    try:
        proc = run_kaggle_cmd(["kernels", "output", kernel_id, "-p", str(OUTPUT_DIR)])
        print(proc.stdout)
        print("Download complete.")

        # Check for TFLite files
        tflite_files = list(OUTPUT_DIR.glob("**/*.tflite"))
        if tflite_files:
            print(f"Found {len(tflite_files)} exported TFLite models:")
            for tf in tflite_files:
                print(f"  - {tf.name} ({tf.stat().st_size / (1024 * 1024):.2f} MB)")

            if args.sync_assets:
                ASSETS_DIR.mkdir(parents=True, exist_ok=True)
                for tf in tflite_files:
                    dest = ASSETS_DIR / tf.name
                    shutil.copy2(tf, dest)
                    print(f"  Synchronized -> {dest}")

                # Copy labels if available
                label_files = list(OUTPUT_DIR.glob("**/*_classes.txt"))
                for lf in label_files:
                    dest = ASSETS_DIR / lf.name
                    shutil.copy2(lf, dest)
                    print(f"  Synchronized labels -> {dest}")
        else:
            print("No .tflite files found in kernel output yet.")
    except subprocess.CalledProcessError as e:
        print(f"Failed to download outputs: {e.stderr or e.stdout}")
        sys.exit(1)


def main() -> None:
    parser = argparse.ArgumentParser(description="WasegMul Kaggle Training Pipeline")
    subparsers = parser.add_subparsers(dest="subcommand", required=True)

    # Subcommand: datasets
    p_datasets = subparsers.add_parser("datasets", help="List user's Kaggle datasets")
    p_datasets.set_defaults(func=cmd_datasets)

    # Subcommand: push
    p_push = subparsers.add_parser("push", help="Stage and push training job to Kaggle")
    p_push.add_argument("--username", default=DEFAULT_USERNAME, help="Kaggle username")
    p_push.add_argument("--slug", default=DEFAULT_SLUG, help="Kernel URL slug")
    p_push.add_argument("--title", default=DEFAULT_TITLE, help="Kernel title")
    p_push.add_argument("--datasets", nargs="+", default=DEFAULT_DATASETS, help="Dataset slugs")
    p_push.set_defaults(func=cmd_push)

    # Subcommand: status
    p_status = subparsers.add_parser("status", help="Check status of running Kaggle kernel")
    p_status.add_argument("--username", default=DEFAULT_USERNAME, help="Kaggle username")
    p_status.add_argument("--slug", default=DEFAULT_SLUG, help="Kernel URL slug")
    p_status.set_defaults(func=cmd_status)

    # Subcommand: output
    p_output = subparsers.add_parser("output", help="Download trained outputs from Kaggle")
    p_output.add_argument("--username", default=DEFAULT_USERNAME, help="Kaggle username")
    p_output.add_argument("--slug", default=DEFAULT_SLUG, help="Kernel URL slug")
    p_output.add_argument("--sync-assets", action="store_true", help="Copy TFLite files to app assets")
    p_output.set_defaults(func=cmd_output)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
