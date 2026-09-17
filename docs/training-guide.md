# YOLO Training Guide

Train a custom waste-specific YOLO11n model for WasegMul.

## Why Train a Custom Model?

| Factor | YOLOv8n (legacy) | YOLO11n (current) | Custom YOLO11n |
|--------|------------------|-------------------|----------------|
| Speed | Baseline | **25% faster** | **25% faster** |
| mAP@50 | 37.3 (COCO) | **39.5 (COCO)** | **~50+ (waste)** |
| Classes | 80 (COCO) | 80 (COCO) | **30 (waste-specific)** |
| TFLite | Yes | **Native export** | **Native export** |
| License | AGPL-3.0 | AGPL-3.0 | AGPL-3.0 |

## Architecture: YOLO11n

YOLO11n is the latest generation from Ultralytics with:
- Faster inference than YOLOv8n
- Better accuracy (mAP@50: 39.5 vs 37.3 on COCO)
- Native TFLite export (no ONNX intermediate needed)
- Actively maintained

## Prerequisites

```bash
# Python 3.8+
pip install torch torchvision ultralytics

# Optional: for dataset preparation
pip install roboflow
```

## Step-by-Step Guide

### 1. Prepare the Dataset

**Option A: Roboflow (Recommended)**

```bash
# Get free API key at https://roboflow.com
# Edit scripts/prepare_dataset.py -> replace YOUR_ROBOFLOW_API_KEY

python scripts/prepare_dataset.py
```

**Option B: Kaggle Cloud Dataset Download**

```bash
# Auto-download and unpack directly via Kaggle API
python scripts/prepare_dataset.py --download-kaggle manojkari/taco-dataset1
```

**Option C: Manual Dataset**

```bash
# Create the directory structure
python scripts/prepare_dataset.py

# Manually add images and labels to:
#   datasets/waste-yolo/train/images/
#   datasets/waste-yolo/train/labels/
#   datasets/waste-yolo/val/images/
#   datasets/waste-yolo/val/labels/
```

**Label Format (YOLO):**
```
class_id center_x center_y width height
```
All coordinates normalized to `[0, 1]`.

**15-Class YOLO Visual Detection IDs (from taxonomy.yaml):**
```
 0: battery                  8: glass_container
 1: bottle                   9: metal
 2: can                     10: paper
 3: cardboard               11: plastic_bag
 4: cigarette               12: plastic_container
 5: cup                     13: plastic_wrapper
 6: electronic              14: textile
 7: food_waste
```

After YOLO crops an item, the EfficientNet classifier assigns it to one of the 30 semantic subclasses.


### 2. Train the Model

```bash
# Basic local training (YOLO11n, 100 epochs)
python scripts/train_waste_yolo.py

# Custom settings
python scripts/train_waste_yolo.py --epochs 200 --batch 32 --img-size 640

# Force CPU training (slower)
python scripts/train_waste_yolo.py --device cpu
```

**Option B: Kaggle Cloud Training (Recommended - Free GPU T4 x2 / P100)**

```bash
# Push training job directly to Kaggle GPU cluster
python scripts/kaggle_pipeline.py push

# Monitor live training telemetry and status
python scripts/kaggle_pipeline.py status

# Download best weights, plots, and sync TFLite to app assets
python scripts/kaggle_pipeline.py output --sync-assets
```

Training outputs:
- `runs/train/waste-yolo11n/weights/best.pt`: best model
- `runs/train/waste-yolo11n/weights/last.pt`: last checkpoint
- `runs/train/waste-yolo11n/results.csv`: training metrics

### 3. Export to TFLite

```bash
# Auto-export (runs after training)
python scripts/export_waste_model_tflite.py

# Or export specific weights
python scripts/export_waste_model_tflite.py --weights runs/train/waste-yolo11n/weights/best.pt

# Export with INT8 quantization (smaller, slight accuracy loss)
python scripts/export_waste_model_tflite.py --optimize
```

Output: `app/src/main/assets/waste_yolo11n.tflite`

### 4. Update Android App

After export, update `YoloDetector.kt`:

```kotlin
// Change these constants in companion object:
private const val MODEL_FILENAME = "waste_yolo11n.tflite"
private const val LABELS_FILENAME = "waste_yolo11n_classes.txt"
```

The label file is auto-generated during export.

### 5. Build and Test

```bash
./gradlew assembleDebug
```

## Training Tips

### Dataset Size
- **Minimum**: 500 images total (20 per class)
- **Good**: 3,000+ images
- **Excellent**: 10,000+ images

### Data Augmentation
Ultralytics applies augmentation automatically:
- Mosaic (4-image concatenation)
- MixUp
- Random HSV
- Random flip/rotation

### Hyperparameters
| Parameter | Default | Recommended Range |
|-----------|---------|-------------------|
| `--epochs` | 100 | 50-300 |
| `--batch` | 16 | 8-64 |
| `--img-size` | 640 | 416-832 |
| `--lr` | 0.001 | 0.0001-0.01 |

### Monitoring Training

```bash
# Watch training in real-time
tensorboard --logdir runs/train/waste-yolo11n

# Or check results.csv after training
```

## Model Comparison

After training, compare models:

| Model | License | Params | mAP@50 | Latency (ms) | TFLite Size |
|-------|---------|--------|---------|---------------|-------------|
| YOLOv8n (legacy) | AGPL-3.0 | 3.2M | 37.3 (COCO) | ~45 | 12.25 MB |
| **YOLO11n (current)** | **AGPL-3.0** | **2.6M** | **39.5 (COCO)** | **~35** | **~8-12 MB** |
| Custom YOLO11n (trained) | AGPL-3.0 | 2.6M | ~50+ (waste) | ~35 | ~8-12 MB |

## Troubleshooting

### "No images found"
```bash
ls datasets/waste-yolo/train/images/
# Should contain .jpg or .png files
```

### "CUDA out of memory"
```bash
python scripts/train_waste_yolo.py --batch 8  # Reduce batch size
```

### "TFLite export failed"
```bash
pip install --upgrade ultralytics
```

### Low accuracy
- Increase dataset size (aim for 3000+ images)
- Train longer (200+ epochs)
- Check label quality (incorrect labels = poor accuracy)
- Ensure class balance (roughly equal images per class)

## Licensing

YOLO11n is AGPL-3.0:
- ✅ Free for personal, research, and open-source use
- ⚠️ Requires Enterprise License for closed-source commercial use
- 💡 Alternative: Use YOLOX-S (Apache 2.0) for commercial freedom

## References

- [YOLO11 Paper](https://docs.ultralytics.com/models/yolo11/) (Ultralytics)
- [Ultralytics YOLO11](https://docs.ultralytics.com/models/yolo11/)
- [TACO Dataset](https://github.com/pedropro/TACO) (MIT)
- [TrashNet Dataset](https://github.com/garythung/trashnet) (MIT)
- [Roboflow Universe](https://universe.roboflow.com/) (various licenses)

