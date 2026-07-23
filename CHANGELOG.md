# Changelog

All notable changes to WasegMul will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-07-20

### Added
- YOLOv8n real-time object detection with CameraX integration
- Live camera feed with bounding box overlays
- FPS counter for detection performance monitoring
- ML arbitrator for smart routing between category and subclass models
- Degraded classification mode (graceful fallback when one model fails)
- Shannon entropy-based category confusion detection
- Comprehensive disposal knowledge base for 30 waste subclasses
- Dynamic classification messages with context-aware variants
- Room database with migration support (v6 through v8)
- DataStore preferences for theme persistence
- Network security configuration
- Data extraction rules for privacy

### Changed
- UI redesign with emerald dark theme and glassmorphism
- All user-facing strings extracted to `strings.xml`
- Enhanced ProGuard/R8 rules for TFLite and Play Services
- Improved error handling with typed `ClassificationOutcome` sealed class

### Fixed
- ArrowBack deprecation (migrated to AutoMirrored version)
- Status bar color deprecation warnings
- Thread safety in ML pipeline
- Memory management in bitmap processing
- Concurrency issues in ViewModel lifecycle

## [1.0.0] - 2026-07-01

### Added
- Initial release
- Offline waste classification using TensorFlow Lite
- EfficientNet-based category classifier (4 classes)
- EfficientNet-based subclass classifier (30 classes)
- Material Design 3 UI with dark/light themes
- Camera and gallery image input
- Classification history with Room database
- Environmental impact insights
- Feedback and correction system
- Kotlin Multiplatform shared module
- iOS stub application
