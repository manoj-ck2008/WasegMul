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

30 subclasses including: Aluminum Cans, Batteries, Cardboard, Clothing, Coffee Grounds, Computer Monitors, Copper Wire, Disposable Cutlery, Glass Bottles, Keyboard, Laptop, LED Light Bulbs, Microwave, Mobile Phone, Organic Leaves, Paper, Paper Cups, Plastic Bags, Plastic Bottles, Plastic Cups, Plastic Forks, Plastic Straws, Plastic Trash Bags, Plastic Wrappers, Printers, Refrigerator, Shoes, Styrofoam Cups, Styrofoam Food Containers, and Air-Conditioner.

### How does YOLO detection work?

YOLO (You Only Look Once) provides real-time object detection via the camera. It can detect 80 different object types from the COCO dataset. Note: The current YOLO model uses general COCO classes, not waste-specific classes. Waste-specific YOLO fine-tuning is planned.

### What is "degraded mode"?

If one of the two classifiers (category or subclass) fails, the app still provides results using the other classifier's output with appropriate messaging.

## Technical

### What Android versions are supported?

Android 10 (API 29) and above.

### Why does the app use Google Play Services TFLite?

Using Play Services TFLite reduces the APK size significantly (the full TFLite runtime is ~30MB). However, it requires Google Play Services on the device. Devices without Play Services cannot run the app.

### Can I use the app on non-Google Play devices?

Currently no. A future version could bundle the full TFLite runtime for compatibility with non-GMS devices.

### How much storage does the app use?

Approximately 45MB for the bundled TFLite models, plus app code and data. Total is typically under 100MB.

### Does the app collect any data?

No. The app runs entirely offline and collects no analytics, telemetry, or user data. All classification history stays on the device.

## Development

### How do I add a new waste category?

1. Retrain the category model with the new class
2. Update `category_classes.txt`
3. Update `WasteMapping.kt` with the new category
4. Update `WasteKnowledgeBase.kt` with disposal guidance
5. Update `MessageGenerator.kt` with category-specific messages

### How do I contribute?

See [CONTRIBUTING.md](../CONTRIBUTING.md) for guidelines.

### Where are the TFLite models trained?

The training pipeline is not included in this repository. The models were fine-tuned using EfficientNet architecture on waste classification datasets.

### Can I replace the models with my own?

Yes. Replace the `.tflite` files in `app/src/main/assets/` and update the corresponding class label files. Ensure your model accepts 224x224x3 FLOAT32 input.
