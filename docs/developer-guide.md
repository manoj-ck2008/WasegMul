# Developer Guide

## Prerequisites

- **Android Studio**: Ladybug (2024.2.1) or later
- **JDK**: 21
- **Android SDK**: 35
- **Physical Device**: Recommended for camera features (emulator camera works but is slower)

## Building the Project

```bash
# Clone the repository
git clone https://github.com/manoj-ck2008/WasegMul.git
cd WasegMul

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run unit tests
./gradlew testDebugUnitTest

# Run lint checks
./gradlew lint
```

## Project Configuration

### Version Management

App version is defined in `gradle.properties`:

```properties
VERSION_NAME=1.1.0
VERSION_CODE=2
```

This is the single source of truth. Do not modify `version.properties` (deprecated).

### Release Signing

For release builds, create `keystore.properties` in the project root:

```properties
storeFile=path/to/your.keystore
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

**Never commit this file to version control.**

### Build Types

- **Debug**: `.debug` suffix on applicationId, debuggable
- **Release**: R8 minification, resource shrinking, requires signing config

## TFLite Models

The app bundles three TFLite models in `app/src/main/assets/`:

| Model | Size | Purpose |
|-------|------|---------|
| `category_model_finetuned.tflite` | 16.57 MB | 4-class category classification |
| `subclass_model_finetuned.tflite` | 16.59 MB | 30-class subclass classification |
| `yolov8n.tflite` | 12.25 MB | YOLOv8n object detection |

### Updating Models

1. Replace the `.tflite` file in `app/src/main/assets/`
2. Update corresponding `_classes.txt` label file if classes changed
3. Update ProGuard rules if new classes are added
4. Test on a mid-range device for performance

### Exporting YOLO to TFLite

```bash
pip install ultralytics onnx2tf
python scripts/export_yolo_tflite.py
```

## Debugging

### Common Build Issues

**"RELEASE BUILD BLOCKED: No keystore.properties found"**
- Create `keystore.properties` or build debug variant

**"Could not resolve all dependencies"**
- Check internet connection
- Try `./gradlew clean` and rebuild

**Camera not working on emulator**
- Use a physical device for camera features
- Or enable camera in emulator settings

### Log Analysis

The app uses Android's logging system. Filter by tag:

- `ModelManager` - ML initialization
- `YoloDetector` - YOLO detection
- `ClassificationViewModel` - Classification pipeline
- `HomeViewModel` - History operations

### Memory Profiling

Use Android Studio Profiler to monitor:
- Bitmap memory during classification
- TFLite model loading
- Room database operations

## Testing

### Unit Tests

Located in `app/src/test/`. Currently placeholder tests only.

```bash
./gradlew testDebugUnitTest
```

### Instrumented Tests

Located in `app/src/androidTest/`. Currently placeholder tests only.

```bash
./gradlew connectedDebugAndroidTest
```

### Manual Testing Checklist

1. Launch app → Splash animation plays
2. Home screen loads with stats
3. Camera permission request appears
4. Capture image → Classification runs
5. Result screen shows prediction and insights
6. History tab shows past classifications
7. Settings → Theme toggle works
8. YOLO screen shows live detection
9. Feedback buttons work on result screen
10. Data purge clears all history

## Code Quality

### Lint

```bash
./gradlew lint
```

### Code Style

Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html):

- Use `camelCase` for functions/properties
- Use `PascalCase` for classes/objects
- Add KDoc for public APIs
- Keep functions focused and concise

### Recommended Tools

- **Detekt**: Static analysis for Kotlin (not yet configured)
- **ktlint**: Code formatting (not yet configured)
- **LeakCanary**: Memory leak detection (debug builds only)

## Architecture Patterns

- **MVVM**: ViewModels manage UI state
- **Repository Pattern**: WasteRepository as data source
- **Sealed Classes**: For type-safe state representation
- **Coroutines + Flow**: Asynchronous operations
- **Dependency Injection**: Manual via Application class

## Performance Optimization

- Models use Play Services TFLite (reduces APK size)
- Image preprocessing is thread-safe
- Database queries are paginated (limit 500 records)
- Compose recomposition is minimized with stable types

## Contributing

See [CONTRIBUTING.md](../CONTRIBUTING.md) for contribution guidelines.
