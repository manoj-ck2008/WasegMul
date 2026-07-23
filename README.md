<p align="center">
  <img src="app/src/main/res/drawable/ic_launcher_foreground.xml" width="120" alt="WasegMul Logo">
</p>

<h1 align="center">WasegMul</h1>

<p align="center">
  <strong>Waste Segregation with Multi-Model AI</strong>
</p>

<p align="center">
  <a href="https://github.com/manoj-ck2008/WasegMul/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/manoj-ck2008/WasegMul" alt="License">
  </a>
  <img src="https://img.shields.io/badge/Android-29%2B-brightgreen" alt="Min SDK">
  <img src="https://img.shields.io/badge/Kotlin-2.1.0-7F52FF" alt="Kotlin">
  <img src="https://img.shields.io/badge/TensorFlow%20Lite-2.17.0-FF6F00" alt="TFLite">
  <img src="https://img.shields.io/badge/Compose%20BOM-2024.11.00-4285F4" alt="Compose">
</p>

<p align="center">
  An on-device waste classification Android app powered by TensorFlow Lite and multi-model AI arbitration. Point your camera at any waste item to instantly identify its category, subclass, and the correct disposal method — entirely offline, with no data leaving your device.
</p>

---

## Overview

WasegMul (**W**aste **Seg**regation **Mul**ti-model) is an Android application that uses two EfficientNet classifiers running on TensorFlow Lite to identify waste items in real-time. The app classifies items into 4 categories and 30 subclasses, then provides actionable disposal guidance sourced from EPA and recycling authority databases.

**Key design principle**: All inference runs on-device. No images or data are transmitted to any server. This is a privacy-first application.

### How It Works

```
Camera / Gallery Image
        │
        ▼
┌──────────────────┐
│ Image Preprocessor│  Center-crop + resize to 224×224
└────────┬─────────┘
         │
    ┌────┴────┐
    ▼         ▼
┌────────┐ ┌──────────┐
│Category│ │ Subclass │
│ Model  │ │  Model   │
│ (4 cls)│ │ (30 cls) │
└───┬────┘ └────┬─────┘
    │           │
    ▼           ▼
┌──────────────────┐
│  ML Arbitrator   │  Cross-checks predictions, estimates uncertainty
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Knowledge Base   │  Disposal guide, environmental impact, sources
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  Result Screen   │  Classification report with feedback loop
└──────────────────┘
```

Additionally, a **YOLOv8n** model provides real-time object detection (80 COCO classes) in a separate live camera view.

---

## Features

### Implemented

- **On-device waste classification** — Dual EfficientNet models classify items into 4 categories and 30 subclasses
- **TensorFlow Lite inference** — All ML processing runs locally on the device
- **YOLOv8n live detection** — Real-time object detection with bounding boxes and labels
- **Multi-model arbitration** — Smart cross-checking between category and subclass predictions with entropy-based uncertainty estimation
- **Waste knowledge base** — Detailed disposal guides, environmental impact data, and recycling benefits for 30 waste types
- **Camera and gallery input** — Capture photos or select from device gallery
- **Classification history** — Room database with timestamped records and feedback tracking
- **Material Design 3 UI** — Glassmorphism cards, animated organic backgrounds, falling petals
- **3 theme modes** — Dark (emerald neon), Light, Colour (olive nature)
- **Offline operation** — No internet connection required for any core feature
- **Privacy-first** — Zero data transmission; all processing on-device
- **Feedback loop** — Users can mark classifications as correct/incorrect to improve future UX
- **Dynamic messages** — 8 classification modes with contextual user-facing messages

### Under Development

> These features are actively being developed and are not yet stable.

- **Multi-object detection** — Detect and classify multiple waste items simultaneously
- **TACO dataset fine-tuning** — Training on the Trash Annotations in Context dataset for improved accuracy
- **Batch classification** — Process multiple images in a single session
- **Export/share reports** — Generate and share classification summaries

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Kotlin 2.1.0 |
| **UI Framework** | Jetpack Compose (BOM 2024.11.00) |
| **Design System** | Material Design 3 |
| **ML Runtime** | TensorFlow Lite 2.17.0 |
| **ML Models** | EfficientNet (classification), YOLOv8n (detection) |
| **Camera** | CameraX 1.4.1 |
| **Database** | Room 2.6.1 |
| **Preferences** | DataStore 1.1.1 |
| **Navigation** | Navigation Compose 2.8.5 |
| **Build System** | Gradle 8.11 + Version Catalog |
| **Multiplatform** | Kotlin Multiplatform (Android + iOS) |

---

## Screenshots

> Screenshots coming soon. The app features a dark emerald theme with glassmorphism cards, animated backgrounds, and a clean Material Design 3 interface.

| Home | Classify | Result | History | YOLO Detection |
|------|----------|--------|---------|----------------|
| *Dashboard with stats and quick actions* | *Camera/gallery image selection with scanning animation* | *Classification report with confidence breakdown* | *Past classifications with feedback editing* | *Live camera feed with bounding boxes* |

---

## Installation

### Prerequisites

- **Android Studio** Koala 2024.1.1 or later
- **JDK 17** (bundled with Android Studio)
- **Android SDK** with API 35 platform tools

### Steps

1. **Clone the repository**
   ```bash
   git clone https://github.com/manoj-ck2008/WasegMul.git
   cd WasegMul
   ```

2. **Open in Android Studio**
   - File → Open → Select the `WasegMul` directory
   - Wait for Gradle sync to complete

3. **Build and run**
   - Connect an Android device (API 29+) or start an emulator
   - Click the Run button or:
     ```bash
     ./gradlew assembleDebug
     ```

### Release Build

Release builds require a signing keystore. Create a `keystore.properties` file in the project root:

```properties
storeFile=path/to/your/release.keystore
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

Then build:

```bash
./gradlew assembleRelease
```

> **Note**: Release builds will fail without `keystore.properties`. This is intentional — the build system enforces signing requirements for production releases.

---

## Model Information

### Category Classifier

| Property | Value |
|----------|-------|
| Architecture | EfficientNet (fine-tuned) |
| Input | 224×224 RGB image |
| Output | 4-class probability distribution |
| Classes | E-Waste, Organic, Recyclable, Trash |

### Subclass Classifier

| Property | Value |
|----------|-------|
| Architecture | EfficientNet (fine-tuned) |
| Input | 224×224 RGB image |
| Output | 30-class probability distribution |
| Classes | Air-Conditioner, Battery, Cardboard, Electronic Component, Electronic Device, Glass, Keyboard, Laptop, Metal, Microwave, Miscellaneous Trash, Mobile, Mouse, Organic, PCB, Paper, Plastic, Player, Printer, Refrigerator, Television, Textile Trash, Washing Machine, automobile wastes, clothing, disposable_plastic_cutlery, light bulbs, shoes, styrofoam_cups, styrofoam_food_containers |

### YOLOv8n Detection Model

| Property | Value |
|----------|-------|
| Architecture | YOLOv8 nano |
| Input | Dynamic resolution (auto-resized) |
| Output | 80 COCO class bounding boxes |
| Use case | Real-time object detection in live camera view |

### Model Arbitration

The ML Arbitrator cross-checks predictions between the category and subclass models:

- **Agreement**: Both models agree on the category → high confidence
- **Partial agreement**: Subclass prediction matches one of the top category predictions → moderate confidence
- **Degraded mode**: One model fails → fallback to the surviving model with reduced confidence
- **Uncertain**: High entropy in predictions → user is advised to verify manually

---

## Architecture

### Module Structure

```
WasegMul/
├── app/                  Android application module
│   ├── src/main/
│   │   ├── assets/       TFLite models + class labels
│   │   ├── java/         Kotlin source code
│   │   │   ├── data/     Room database, DAO, entities
│   │   │   ├── ml/       Model management, classifiers, YOLO detector
│   │   │   ├── navigation/  Screen routes, NavHost setup
│   │   │   ├── repository/  Data access facade
│   │   │   ├── ui/       Compose screens, components, theme
│   │   │   ├── viewmodel/   ViewModels
│   │   │   └── utils/    Settings, utilities
│   │   └── res/          Android resources
│   └── proguard-rules.pro
├── shared/               Kotlin Multiplatform module
│   └── src/commonMain/   Domain logic (MLArbitrator, KnowledgeBase, etc.)
├── iosApp/               iOS shell (SwiftUI)
├── scripts/              Model export utilities
└── docs/                 Internal documentation
```

### Screen Flow

```
Splash → Home ─┬─→ Classify → Result
               ├─→ History ─→ Result (historical record)
               ├─→ Settings
               └─→ YOLO (live detection)
```

### Key Classes

| Class | Purpose |
|-------|---------|
| `ClassificationViewModel` | Orchestrates the classification pipeline |
| `ModelManager` | Manages TFLite model lifecycle and parallel inference |
| `MLArbitrator` | Cross-checks category/subclass predictions |
| `WasteKnowledgeBase` | Maps 30 subclasses to disposal guides |
| `YoloDetector` | YOLOv8n inference with NMS post-processing |
| `WasteRepository` | Data access facade over Room database |
| `SettingsManager` | DataStore-backed theme preferences |

---

## Folder Structure

```
WasegMul/
├── .github/                    GitHub templates and workflows
│   └── ISSUE_TEMPLATE/         Issue templates
├── app/
│   ├── build.gradle.kts        App module build configuration
│   ├── proguard-rules.pro      R8/ProGuard rules
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/             ML models + labels
│       │   ├── category_model_finetuned.tflite
│       │   ├── category_classes.txt
│       │   ├── subclass_model_finetuned.tflite
│       │   ├── subclass_classes.txt
│       │   └── yolov8n.tflite
│       └── java/com/agrelius/wasegmul/
│           ├── data/           Room database layer
│           ├── ml/             ML inference pipeline
│           ├── navigation/     Navigation setup
│           ├── repository/     Data access
│           ├── ui/             Compose UI
│           ├── viewmodel/      ViewModels
│           └── utils/          Utilities
├── shared/
│   ├── build.gradle.kts        KMP module configuration
│   └── src/commonMain/         Shared domain logic
├── iosApp/                     iOS shell
├── scripts/                    Utility scripts
├── docs/                       Documentation
├── gradle/                     Gradle wrapper + version catalog
├── build.gradle.kts            Root build file
├── settings.gradle.kts         Module declarations
├── gradle.properties           Build properties + version
├── LICENSE                     MIT License
├── CONTRIBUTING.md             Contribution guidelines
├── CODE_OF_CONDUCT.md          Community standards
├── SECURITY.md                 Security policy
└── CHANGELOG.md                Release history
```

---

## Known Limitations

- **Classification accuracy**: Models are trained on specific datasets and may not generalize perfectly to all waste types in all contexts
- **Single-object focus**: The EfficientNet classifiers process one item at a time; multi-object detection is under development
- **YOLO is COCO-based**: The YOLOv8n model detects general objects (80 COCO classes), not waste-specific categories
- **No iOS classification yet**: The iOS shell demonstrates the shared knowledge base but does not yet include camera or ML inference
- **No cloud features**: All data is local; there is no sync, backup, or cloud-based analysis

---

## Roadmap

- [ ] Multi-object detection with simultaneous classification
- [ ] TACO dataset fine-tuning for waste-specific object detection
- [ ] iOS camera and classification pipeline
- [ ] Batch image processing
- [ ] Classification report export (PDF/share)
- [ ] Additional waste subclass categories
- [ ] Model accuracy benchmarking suite
- [ ] Accessibility improvements (TalkBack, font scaling)
- [ ] Localization (multi-language support)

---

## Contributing

Contributions are welcome! Please read the [Contributing Guidelines](CONTRIBUTING.md) before submitting a pull request.

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

## Acknowledgements

- [TensorFlow Lite](https://www.tensorflow.org/lite) — On-device ML inference
- [Ultralytics YOLOv8](https://docs.ultralytics.com/) — Object detection
- [Jetpack Compose](https://developer.android.com/jetpack/compose) — Modern Android UI
- [CameraX](https://developer.android.com/media/camerax) — Camera integration
- [EPA Recycling Guidelines](https://www.epa.gov/recycle) — Disposal information
- [Call2Recycle](https://call2recycle.org/) — Battery recycling data
- [Contributor Covenant](https://www.contributor-covenant.org/) — Code of Conduct

---

## Author

**Manoj** — [GitHub](https://github.com/manoj-ck2008)
