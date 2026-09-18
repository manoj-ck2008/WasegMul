# Changelog

All notable changes to WasegMul will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - Unreleased

### Added
- **Interactive ROI Tap-and-Classify**: Tap detected bounding boxes in YOLO live camera view to crop and classify items directly with EfficientNet.
- **Energy Metric**: Added `energySavedKwh` (kWh) to `EcoImpactCalculator` (Recyclables: 4.2 kWh/kg, E-Waste: 6.5 kWh/kg, Organic: 0.3 kWh/kg).
- **Material 3 Dynamic Color**: Support for Android 12+ Material You dynamic color palettes with toggle in Settings.
- **Frosted Glass Blur**: Background blur on Android 12+ (API 31+) in `GlassCard` without blurring card content.
- **Room Migration 8 to 9**: Single-column indices on `category`, `subclass`, and `feedback` columns in `waste_history` (one index per column, not composite).
- **Room Migration 9 to 10**: New `barcode_products` offline cache table with indices on `category` and `lastAccessed` for barcode resolution.
- **Room Migration 10 to 11**: Barcode provenance columns on `waste_history` (`source`, `productName`, `barcode`); destructive migration removed (fail-closed `fallbackToDestructiveMigrationOnDowngrade` only).
- **History Management**: Individual item deletion with confirmation dialog, sorting chips (Newest, Oldest, Confidence), and background CSV export with cache file cleanup.
- **Result Screen Thumbnail**: Display of captured waste image alongside classification metrics, with a "Scan Another" action.
- **Compose Previews**: Comprehensive `@Preview` composables across screens and UI components.
- **Swift Interop Bridge**: Flat primitive array API `arbitrateMLFlat` in `IOSBridge` for clean Swift consumption.
- **Waste Taxonomy Harmonization**: Unified YOLO visual classes with classifier subclasses in `WasteMapping` and `WasteKnowledgeBase`.

### Changed
- **Zero-Allocation ML Inference**: Preallocated reusable direct ByteBuffers, pixel arrays, and canvas buffers in `YoloDetector`, eliminating 9+ MB per frame GC allocation churn.
- **Zero-Allocation Transpose**: Direct indexing in YOLO output parsing supporting both channel-first and anchor-first layouts.
- **NMS Optimization**: Switched to boolean suppression mask to eliminate list mutation and object allocation in the detection loop.
- **Classifier Architecture**: Extracted shared `TfliteClassifier` base class, eliminating 95% of boilerplate across category and subclass classifiers.
- **Repository Interface**: Extracted `interface WasteRepository` with `DefaultWasteRepository` implementation.
- **Domain Harmonization**: Aligned `PredictionResult` with canonical `subclass` nomenclature, validated confidence ranges (0f..1f), and removed obsolete `ScoredPrediction`.

### Fixed
- **Native SIGSEGV Crashes**: Fixed race conditions during `ModelManager.close()` and `YoloDetector.close()` with cooperative cancellation flags.
- **CameraX Aspect Ratio Drift**: Fixed coordinate stretching in `DetectionOverlay` by tracking camera frame dimensions against viewport with `FILL_CENTER` scaling.
- **ML Arbitrator Confused Category Bug**: Corrected priority logic to trust high-confidence subclass predictions when category classifier entropy is high.
- **Correction Data Integrity**: Synchronized category re-derivation in `WasteRepository` when users submit a corrected subclass.
- **Timestamp Desync**: Ensured generated entity timestamps are mirrored into in-memory ViewModel state.
- **UI Visual Bugs**: Fixed orbiting node ring positioning in `AppLogo`, dp-to-px scaling in `FallingPetals`, and double TalkBack speech in `FeedbackSection`.
- **Stale Documentation**: Corrected training guide non-ASCII characters, FAQ offline runtime details, and YOLO naming consistency.

## [1.1.0] - 2026-07-16

### Added
- YOLOv8n real-time object detection with CameraX live preview
- YOLO detection screen with bounding boxes, labels, and FPS counter
- ML model export script (`scripts/export_yolo_tflite.py`)
- Dual-model ML arbitration (category + subclass classifiers)
- Waste knowledge base with disposal guides for 30 waste types
- Entropy-based uncertainty estimation
- 8 classification modes with dynamic user messages
- Glassmorphism UI with animated organic backgrounds
- Falling petals particle animation
- 3 theme modes: Dark (emerald), Light, Colour (olive/nature)
- Material Design 3 with custom glass color system
- Room database with migration support (v6 through v8)
- DataStore preferences for theme persistence
- History screen with inline feedback editing
- Result screen with confidence breakdown
- Splash screen with animated neural leaf logo
- Camera and gallery image selection
- Haptic feedback on button presses
- ProGuard/R8 rules for release builds
- Network security configuration
- Backup exclusion rules

### Changed
- Migrated to Compose BOM 2024.11.00
- Updated TensorFlow Lite to 2.17.0
- Updated CameraX to 1.4.1
- Updated Kotlin to 2.1.0
- Improved error handling with typed ClassificationOutcome
- Enhanced ProGuard rules for TFLite, Room, and CameraX

### Fixed
- Crash prevention for null bitmap handling
- Thread safety in model initialization
- Memory management for bitmap recycling
- Navigation back-stack handling
- Accessibility improvements

## [1.0.0] - 2026-06-01

### Added
- Initial release
- Dual EfficientNet classifiers (4-category, 30-subclass)
- On-device TFLite inference
- Camera and gallery image selection
- Classification history with Room database
- Material Design UI with dark theme
