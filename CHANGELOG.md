# Changelog

All notable changes to WasegMul will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
