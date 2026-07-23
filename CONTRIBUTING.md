# Contributing to WasegMul

Thank you for your interest in contributing to WasegMul! This guide will help you get started.

## Getting Started

1. **Fork** the repository
2. **Clone** your fork:
   ```bash
   git clone https://github.com/<your-username>/WasegMul.git
   ```
3. **Open** the project in Android Studio (Koala or later)
4. **Sync** Gradle and let the build complete

## Development Setup

- **JDK**: 17 (bundled with Android Studio)
- **Android SDK**: compileSdk 35, minSdk 29
- **Gradle**: 8.11 (wrapper included)
- **Kotlin**: 2.1.0

No external API keys or services are required. All ML inference runs on-device.

## Project Structure

```
WasegMul/
├── app/            Android application (UI, ML inference, data layer)
├── shared/         Kotlin Multiplatform module (domain logic, knowledge base)
├── iosApp/         iOS shell (SwiftUI)
├── scripts/        Utility scripts (model export)
└── docs/           Internal documentation
```

## Making Changes

### Branching

- Create a feature branch from `main`:
  ```bash
  git checkout -b feature/your-feature-name
  ```

### Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Keep composables focused and small
- Prefer `stringResource()` over hardcoded strings for user-facing text

### Commit Messages

Use [Conventional Commits](https://www.conventionalcommits.org/) format:

```
<type>: <description>
```

Types:
- `feat` - New feature
- `fix` - Bug fix
- `docs` - Documentation only
- `style` - Code style (formatting, no logic change)
- `refactor` - Code restructuring (no feature change)
- `test` - Adding or updating tests
- `chore` - Build, CI, or tooling changes

Examples:
```
feat: add batch classification mode
fix: prevent crash when camera permission is denied
docs: update README with model performance table
```

### Testing

- Verify the app builds: `./gradlew assembleDebug`
- Test on a physical device or emulator (API 29+)
- Test the classification pipeline end-to-end
- Test YOLO live detection on supported devices

## Pull Request Process

1. Ensure your branch is up-to-date with `main`
2. Verify the project builds without errors
3. Write a clear PR description explaining **what** and **why**
4. Link any related issues
5. Request a review from a maintainer

## Reporting Issues

- Use the **Bug Report** template for defects
- Use the **Feature Request** template for suggestions
- Include device model, Android version, and app version when reporting bugs

## ML Model Contributions

If you want to improve the classification models:

1. The training pipeline uses EfficientNet-based architectures
2. Models are exported to TFLite format and placed in `app/src/main/assets/`
3. Class labels are in `category_classes.txt` (4 classes) and `subclass_classes.txt` (30 subclasses)
4. The YOLO model export script is in `scripts/export_yolo_tflite.py`

## Code of Conduct

This project follows the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md). By participating, you agree to its terms.

## Questions?

Open a [Discussion](https://github.com/manoj-ck2008/WasegMul/discussions) or reach out to the maintainers.
