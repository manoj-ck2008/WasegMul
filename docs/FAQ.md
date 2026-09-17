# FAQ

## General

### What is WasegMul?

WasegMul (Waste Segregation Multi-model AI) is an Android application that uses deep learning to classify waste items into categories and specific material types. It helps users make informed disposal decisions.

### Is the app available on the Play Store?

Not yet. You can build from source or download the APK from GitHub Releases.

### Does the app require internet?

No. All classification runs on-device using TensorFlow Lite. No internet connection is required.

### What languages does the app support?

Currently English only. The UI strings are in `strings.xml` and can be translated.

## Classification

### How accurate is the classification?

Accuracy depends on image quality, lighting, and the specific waste item. The models are fine-tuned for common waste items but may not perform well on unusual or unusual-looking items.

### What categories can the app detect?

- **E-Waste**: Electronic waste (batteries, phones, laptops, etc.)
- **Organic**: Organic waste (food scraps, leaves, etc.)
- **Recyclable**: Recyclable materials (paper, glass, plastic, etc.)
- **Trash**: Non-recyclable waste

### What materials can it identify specifically?

30 subclasses: Air-Conditioner, Battery, Cardboard, Electronic Component, Electronic Device, Glass, Keyboard, Laptop, Metal, Microwave, Miscellaneous Trash, Mobile, Mouse, Organic, PCB, Paper, Plastic, Player, Printer, Refrigerator, Television, Textile Trash, Washing Machine, automobile wastes, clothing, disposable_plastic_cutlery, light bulbs, shoes, styrofoam_cups, styrofoam_food_containers.

### How does YOLO detection work?

YOLO (You Only Look Once) provides real-time bounding box detection via the camera. The app supports fine-tuned waste-specific YOLO models (15 visual classes such as bottle, can, cup, electronic, food_waste, plastic_container) via `waste_classes.txt`, as well as baseline YOLOv8n/YOLO11n models (80 COCO classes).

### What is "degraded mode"?

If one of the two classifiers (category or subclass) fails or is unavailable, the app still provides results using the available classifier output with appropriate advisory messaging.

## Technical

### What Android versions are supported?

Android 10 (API 29) and above.

### Does the app require Google Play Services for TFLite?

No. The app bundles the native TensorFlow Lite runtime (`org.tensorflow:tensorflow-lite`) directly within the APK. It runs fully offline on any compatible Android 10+ device, including AOSP and de-Googled devices without Google Play Services.

### How much storage does the app use?

Approximately 45MB for the bundled TFLite models, plus app code and database. Total install footprint is typically under 100MB.

### Does the app collect any data?

No. The app runs entirely offline and collects no analytics, telemetry, or user data. All classification history is stored locally in an encrypted or private Room SQLite database on the device.

## Development

### How do I add a new waste category?

1. Retrain or fine-tune the classification model with the new class
2. Update `taxonomy.yaml` and `category_classes.txt`
3. Update `WasteMapping.kt` with the new category mapping
4. Update `WasteKnowledgeBase.kt` with disposal guidance
5. Update `MessageGenerator.kt` with category-specific messages

### How do I contribute?

See [CONTRIBUTING.md](../CONTRIBUTING.md) for guidelines.

### Where are the TFLite models trained?

The YOLO detection training and export pipeline is provided in `scripts/`:
- `scripts/prepare_dataset.py`: Dataset ingestion, annotation normalization, and splitting
- `scripts/train_waste_yolo.py`: Local YOLO11n fine-tuning
- `scripts/kaggle_train.py`: Full Kaggle GPU training pipeline
- `scripts/export_waste_model_tflite.py`: PyTorch to ONNX to TFLite INT8/FP32 export

See [docs/training-guide.md](training-guide.md) for end-to-end instructions.

### Can I replace the models with my own?

Yes. Place your custom `.tflite` model in `app/src/main/assets/` and update the corresponding class label files (`waste_classes.txt`, `category_classes.txt`, `subclass_classes.txt`). Ensure your classifier model accepts 224x224x3 FLOAT32 input.

