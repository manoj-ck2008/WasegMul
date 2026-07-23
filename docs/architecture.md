# Architecture

## Overview

WasegMul follows the MVVM (Model-View-ViewModel) architecture pattern with a clean separation between UI, domain, and data layers. The app uses Jetpack Compose for UI, TensorFlow Lite for on-device ML inference, and Room for local persistence.

## Layers

### Presentation Layer

- **Screens**: Jetpack Compose composables that define the UI
- **ViewModels**: Manage UI state and business logic
- **Navigation**: Compose Navigation with typed routes

### Domain Layer

- **ML Pipeline**: ModelManager, MLArbitrator, MessageGenerator
- **Classifiers**: CategoryClassifier, SubclassClassifier, YoloDetector
- **Knowledge Base**: WasteKnowledgeBase with disposal guidance

### Data Layer

- **Room Database**: WasteRecord entity, WasteDao, WasteDatabase
- **Repository**: WasteRepository as single source of truth
- **Preferences**: SettingsManager using DataStore

## ML Pipeline

```
Image Input (Camera/Gallery)
        │
        ▼
┌─────────────────────┐
│  ImagePreprocessor   │  Center-crop, resize to 224x224, FLOAT32
└─────────────────────┘
        │
        ├──────────────────┐
        ▼                  ▼
┌──────────────┐  ┌──────────────┐
│   Category   │  │   Subclass   │  EfficientNet-based TFLite models
│  Classifier  │  │  Classifier  │
└──────────────┘  └──────────────┘
        │                  │
        └──────────────────┘
                │
                ▼
┌─────────────────────┐
│    MLArbitrator      │  Resolves conflicts using Shannon entropy
└─────────────────────┘
        │
        ├── Full Classification (models agree)
        ├── Override Mode (category overrides subclass)
        └── Degraded Mode (one model failed)
                │
                ▼
┌─────────────────────┐
│  WasteKnowledgeBase  │  Disposal guidance, environmental insights
└─────────────────────┘
        │
        ▼
┌─────────────────────┐
│   ResultScreen       │  Display prediction, insights, feedback UI
└─────────────────────┘
```

## YOLO Detection Pipeline

```
CameraX Preview
        │
        ▼
┌─────────────────────┐
│    YoloDetector      │  YOLOv8n inference
│  640x640 input       │
│  8400 anchor boxes   │
└─────────────────────┘
        │
        ▼
┌─────────────────────┐
│   Non-Maximum        │  IoU-based suppression
│   Suppression        │  Confidence threshold: 0.65
│                      │  IoU threshold: 0.45
└─────────────────────┘
        │
        ▼
┌─────────────────────┐
│  Bounding Boxes      │  Displayed on camera preview
│  + Labels            │
└─────────────────────┘
```

## Key Classes

### ModelManager (`app/.../ml/ModelManager.kt`)

Orchestrates both classifiers. Handles initialization via Google Play Services TFLite. Supports degraded classification when one model fails.

### MLArbitrator (`shared/.../MLArbitrator.kt`)

Resolves conflicts between category and subclass predictions using Shannon entropy analysis. The category model is the source of truth when models disagree.

### WasteKnowledgeBase (`shared/.../WasteKnowledgeBase.kt`)

Authoritative disposal and environmental data for all 30 waste subclasses. Sources include EPA, UN, and industry standards.

### YoloDetector (`app/.../ml/YoloDetector.kt`)

Standalone YOLOv8n object detection. Manages input/output ByteBuffer, performs NMS (Non-Maximum Suppression), and computes IoU for bounding box filtering.

## Database Schema

### WasteRecord Entity

| Column | Type | Description |
|--------|------|-------------|
| id | Long (PK, auto) | Unique identifier |
| category | String | Predicted category |
| subclass | String | Predicted subclass |
| confidence | Float | Prediction confidence |
| estimatedWeight | Float | Estimated weight in kg |
| featureVector | String? | Encoded prediction features |
| imagePath | String? | Path to source image |
| feedback | String? | User feedback (correct/incorrect/not_sure) |
| correctedSubclass | String? | User's corrected classification |
| topPredictions | String? | Top-K predictions encoded |
| timestamp | Long | Classification timestamp |

### Database Migrations

- v6 → v7: Added `topPredictions` column
- v7 → v8: Added index on `timestamp` column

## Navigation

The app uses Compose Navigation with a sealed class defining 7 routes:

1. **Splash** - App introduction animation
2. **Home** - Main dashboard with stats and quick actions
3. **Classify** - Image classification flow
4. **Result** - Classification results with insights
5. **History** - Past classifications list
6. **Settings** - Theme and data management
7. **YOLO** - Real-time object detection

## Theme System

Three theme modes:

- **Dark**: Emerald green palette with glassmorphism
- **Light**: Clean green-toned Material Design
- **Colour**: Nature-inspired with olive, grass, and sky tones

Theme preference is persisted using DataStore Preferences.

## ProGuard/R8 Rules

Release builds apply code shrinking and obfuscation. Key keep rules:

- Room entities and DAOs
- TFLite classifier classes
- Shared KMP model classes
- ViewModel factories
- CameraX ImageProxy
- Application and Activity classes

See `app/proguard-rules.pro` for complete rules.
