# WasegMul Developer Guide & Engineering Handbook

A comprehensive manual for engineering, building, testing, and extending the WasegMul waste classification platform.

---

## 1. System Requirements & Setup

### 1.1 Prerequisites
- **Android Studio**: Ladybug (2024.2.1) or later
- **JDK**: 21 (configured in Android Studio under Settings → Build, Execution, Deployment → Build Tools → Gradle)
- **Android SDK**: API 35 platform tools (minimum supported SDK: API 29 / Android 10)
- **Python**: 3.8+ (Python 3.10+ recommended for dataset preparation and YOLO training)
- **Hardware**: Physical Android device with camera recommended for CameraX and YOLO verification

### 1.2 Repository Setup

```bash
# Clone the repository with submodules if any
git clone https://github.com/manoj-ck2008/WasegMul.git
cd WasegMul

# Validate build environment and run unit tests
./gradlew testDebugUnitTest
```

---

## 2. Project Architecture & Codebase Layout

```
WasegMul/
├── app/                                 # Android application module
│   ├── src/main/java/com/agrelius/wasegmul/
│   │   ├── data/                        # Room Database, DAO, Entity models, TypeConverters
│   │   ├── ml/                          # TFLite ModelManager, YoloDetector, Preprocessing
│   │   │   ├── classifiers/             # TfliteClassifier base, Category & Subclass classifiers
│   │   │   └── preprocessing/           # ImagePreprocessor, Normalization, Hardware bitmaps
│   │   ├── navigation/                  # AppNavigation, Route definitions
│   │   ├── repository/                  # WasteRepository interface & DefaultWasteRepository
│   │   ├── ui/                          # Jetpack Compose presentation layer
│   │   │   ├── classify/                # Scanner interface, viewmodel, animations
│   │   │   ├── components/              # Reusable design system: GlassCard, HeroCard, etc.
│   │   │   ├── guide/                   # Eco Encyclopedia and disposal guide
│   │   │   ├── history/                 # History logs, filters, CSV export
│   │   │   ├── home/                    # Dashboard, quick actions, impact metrics
│   │   │   ├── result/                  # Analysis report, feedback, share intent
│   │   │   ├── settings/                # Preferences, Material You toggle, legal dialog
│   │   │   ├── splash/                  # Animated neural logo splash
│   │   │   ├── theme/                   # Material 3 Theme, Typography, GlassColors
│   │   │   └── yolo/                    # Real-time YOLO detection overlay & ROI tap-to-classify
│   │   ├── utils/                       # SettingsManager (DataStore), EcoThoughts quotes
│   │   └── viewmodel/                   # HomeViewModel and shared state
│   ├── src/main/assets/                 # Bundled TFLite models and label files
│   ├── src/test/java/                   # Unit test suite (Repository, EcoImpact, Arbitrator)
│   └── schemas/                         # Room SQLite exported database schemas
├── shared/                              # Kotlin Multiplatform (KMP) shared library
│   └── src/commonMain/kotlin/com/agrelius/wasegmul/
│       ├── CommonModels.kt              # PredictionResult, WasteRecord, WasteMetadata
│       ├── EcoImpactCalculator.kt       # Carbon, water, energy, and tree metrics
│       ├── MLArbitrator.kt              # Decision state machine and Shannon entropy
│       ├── MessageGenerator.kt          # Dynamic contextual user guidance
│       ├── PredictionCodec.kt           # Prediction serialization codec
│       ├── WasteKnowledgeBase.kt        # Disposal protocols and reference sources
│       ├── WasteMapping.kt              # Subclass to category taxonomy mapping
│       └── IOSBridge.kt                 # Swift-compatible flat array interop bridge
├── iosApp/                              # iOS application shell consuming KMP framework
├── scripts/                             # Python tooling and training scripts
│   ├── prepare_dataset.py               # Multi-source dataset merge, dedup, and split
│   ├── train_waste_yolo.py              # Local YOLO11n fine-tuning
│   ├── kaggle_train.py                  # Kaggle GPU training pipeline
│   ├── export_waste_model_tflite.py     # PyTorch to ONNX to TFLite converter
│   └── export_yolo_tflite.py            # Baseline YOLOv8n exporter
├── docs/                                # Technical documentation, ADRs, and guides
├── taxonomy.yaml                        # Single source of truth for waste categories
└── datasets_manifest.yaml               # Dataset registry with licenses and attributions
```

---

## 3. Build & Release Engineering

### 3.1 Version Configuration

Version configuration is declared in `gradle.properties`:
```properties
VERSION_NAME=2.0.0
VERSION_CODE=3
```

### 3.2 Production Release Signing

To create a signed release APK or Android App Bundle (AAB):
1. Create `keystore.properties` in the project root:
   ```properties
   storeFile=/absolute/path/to/upload-keystore.jks
   storePassword=your_keystore_password
   keyAlias=your_key_alias
   keyPassword=your_key_password
   ```
2. Run the release build:
   ```bash
   ./gradlew assembleRelease
   ```
   The build script incorporates ProGuard and R8 rules defined in `app/proguard-rules.pro` to keep reflection-sensitive TFLite classes and Room entities intact.

---

## 4. Testing & Verification

### 4.1 Automated Unit Tests

WasegMul maintains strict test-driven discipline. All unit tests execute without emulators:

```bash
# Execute the full unit test suite
./gradlew testDebugUnitTest
```

### 4.2 Core Test Suites

| Test Suite | Location | Scope |
|:---|:---|:---|
| `WasteRepositoryTest` | `app/src/test/...` | Room repository CRUD, single record deletion, and category re-derivation |
| `EcoImpactTest` | `app/src/test/...` | Carbon avoidance, water savings, energy (kWh), and user correction exclusions |
| `MLArbitratorTest` | `app/src/test/...` | Consistent predictions, high-entropy fallback, and subclass priority overrides |
| `MappersTest` | `app/src/test/...` | Entity to domain model bidirectional conversions and timestamp safety |
| `MessageGeneratorTest` | `app/src/test/...` | Humanized label formatting and contextual message selection |
| `PredictionCodecTest` | `app/src/test/...` | String serialization, deserialization, and corrupted payload resilience |
| `WasteKnowledgeBaseTest`| `app/src/test/...` | Case-insensitive lookups, peripheral guidance, and URL source citations |
| `WasteMappingTest` | `app/src/test/...` | Canonical 30 subclasses check, category lookups, and extended YOLO mapping |

---

## 5. Machine Learning Pipeline Architecture

### 5.1 Bundled Model Specifications

| Model Asset | Format | Input Shape | Purpose |
|:---|:---|:---|:---|
| `category_model_finetuned.tflite` | TFLite FP32 | `[1, 224, 224, 3]` | 4-class broad category classifier |
| `subclass_model_finetuned.tflite` | TFLite FP32 | `[1, 224, 224, 3]` | 30-class fine material classifier |
| `yolov8n.tflite` / `waste_yolo11n.tflite` | TFLite FP32/INT8 | `[1, 640, 640, 3]` | Real-time multi-object localizer |

### 5.2 Zero-Allocation YOLO Pipeline

The real-time camera analyzer in `YoloDetector.kt` avoids creating objects on every frame:
- Input tensors are written directly to a single preallocated direct `ByteBuffer`.
- Output tensors are parsed in-place using stride arithmetic (`parseDetections`) without creating intermediate 2.8 MB transposed arrays.
- Bitmask non-maximum suppression (`nmsPerClass`) marks suppressed boxes in-place using a boolean array.

---

## 6. Database Migrations

Room database migrations are strictly declared in `WasteDatabase.kt` and tracked in `app/schemas/`:

```kotlin
// Example: Applying migration 8 to 9
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_waste_history_category ON waste_history (category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_waste_history_subclass ON waste_history (subclass)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_waste_history_feedback ON waste_history (feedback)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_waste_history_timestamp ON waste_history (timestamp)")
    }
}
```

When changing the schema:
1. Increment `version` in `@Database(version = N, ...)`.
2. Provide a new migration object in `ALL_MIGRATIONS`.
3. Re-run `./gradlew testDebugUnitTest` to auto-export the new JSON schema in `app/schemas/`.

---

## 7. UI/UX Design System & Jetpack Compose Previews

### 7.1 Dynamic Themes
WasegMul supports 3 curated themes plus Android 12+ Material You Dynamic Color:
- **Dark Mode**: Emerald neon accents with deep charcoal surfaces.
- **Light Mode**: Clean eco-white with forest green primary highlights.
- **Colour Mode**: Earthy olive tones designed for natural contrast.
- **Dynamic Color**: Enabled via `SettingsScreen`, matching device wallpaper tones using `dynamicLightColorScheme` and `dynamicDarkColorScheme`.

### 7.2 Compose Previews
All components and screens include `@Preview` composables wrapped in `WasegMulTheme`:
- `GlassCardPreview` (`GlassCard.kt`)
- `HeroCardPreview` (`HeroCard.kt`)
- `InsightCardPreview` (`InsightCard.kt`)
- `ConfidenceBadgePreview` (`ConfidenceBadge.kt`)
- `AppLogoPreview` (`AppLogo.kt`)
- `GuideScreenPreview` (`GuideScreen.kt`)
- `SettingsScreenPreview` (`SettingsScreen.kt`)
- `HistoryCardPreview` & `EmptyHistoryStatePreview` (`HistoryScreen.kt`)
- `DetectionOverlayPreview` (`YoloScreen.kt`)

---

## 8. Contributing Standards

1. Follow the domain terminology defined in `CONTEXT.md`.
2. Maintain zero test regressions: `./gradlew testDebugUnitTest` must pass on every commit.
3. Record significant architectural decisions under `docs/adr/`.
4. Avoid em-dashes in commit messages and documentation prose.
