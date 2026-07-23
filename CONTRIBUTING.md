# Contributing to WasegMul

Thank you for your interest in contributing to WasegMul! This document provides guidelines and instructions for contributing.

## Code of Conduct

By participating in this project, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md).

## How to Contribute

### Reporting Bugs

Before creating bug reports, please check existing issues to avoid duplicates. When creating a bug report, include:

- A clear and descriptive title
- Steps to reproduce the issue
- Expected behavior vs actual behavior
- Device/OS version information
- Screenshots if applicable

### Suggesting Features

Feature requests are welcome. Please open an issue with:

- A clear description of the proposed feature
- The motivation/use case
- Any technical considerations

### Pull Requests

1. Fork the repository
2. Create a feature branch from `main`
3. Make your changes following the code style below
4. Test your changes thoroughly
5. Submit a pull request with a clear description

## Development Setup

### Prerequisites

- Android Studio Ladybug (2024.2.1) or later
- JDK 21
- Android SDK 35

### Getting Started

```bash
# Clone your fork
git clone https://github.com/<your-username>/WasegMul.git
cd WasegMul

# Open in Android Studio and sync Gradle
# Build the project
./gradlew assembleDebug
```

### Project Structure

```
WasegMul/
├── app/                    # Main Android application module
├── shared/                 # Kotlin Multiplatform shared module
├── iosApp/                 # iOS application entry point
├── gradle/                 # Gradle wrapper and version catalog
└── scripts/                # Build utility scripts
```

## Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use descriptive variable and function names
- Keep functions focused and concise
- Add KDoc comments for public APIs
- Use `camelCase` for functions and properties
- Use `PascalCase` for classes and objects

### Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

- `feat:` for new features
- `fix:` for bug fixes
- `docs:` for documentation changes
- `style:` for formatting changes
- `refactor:` for code refactoring
- `test:` for test additions/changes
- `chore:` for maintenance tasks

Example:
```
feat: add batch classification support
fix: handle null bitmap in classifier
docs: update installation instructions
```

## Testing

- Run unit tests: `./gradlew testDebugUnitTest`
- Run instrumented tests: `./gradlew connectedDebugAndroidTest`
- Run lint: `./gradlew lint`

## Architecture

The app follows MVVM architecture with:

- **UI Layer**: Jetpack Compose screens and ViewModels
- **Domain Layer**: ML inference (TFLite classifiers, YOLO detector)
- **Data Layer**: Room database, DataStore preferences

## Model Changes

If modifying TFLite models:

1. Update the model file in `app/src/main/assets/`
2. Update corresponding class labels if needed
3. Update ProGuard rules if new classes are added
4. Test inference performance on mid-range devices

## Questions?

Open a [GitHub Discussion](https://github.com/manoj-ck2008/WasegMul/discussions) for general questions.
