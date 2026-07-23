# WasegMul Architecture

## System Architecture

WasegMul uses a layered architecture with clear separation of concerns:

```
┌─────────────────────────────────────────────────┐
│                   UI Layer                       │
│  Compose Screens + ViewModels + Components      │
├─────────────────────────────────────────────────┤
│                Navigation Layer                  │
│  Screen Routes + NavHost + Arguments            │
├─────────────────────────────────────────────────┤
│                  ML Layer                        │
│  ModelManager + Classifiers + YOLO + Preprocess │
├─────────────────────────────────────────────────┤
│               Domain Layer (shared)             │
│  MLArbitrator + KnowledgeBase + WasteMapping   │
├─────────────────────────────────────────────────┤
│                 Data Layer                       │
│  Room DB + DAO + Repository + DataStore         │
└─────────────────────────────────────────────────┘
```

## ML Pipeline

The classification pipeline processes images through these stages:

### 1. Image Acquisition
- User captures via camera or selects from gallery
- Image is decoded to `Bitmap` and passed to `ClassificationViewModel`

### 2. Preprocessing (`ImagePreprocessor`)
- Center-crop to square aspect ratio
- Resize to 224×224 pixels
- Convert to FLOAT32 tensor with normalization

### 3. Parallel Inference (`ModelManager`)
- Both models run in parallel on separate threads
- Category model: 4-class EfficientNet → E-Waste/Organic/Recyclable/Trash
- Subclass model: 30-class EfficientNet → specific waste type
- Top-K predictions extracted from each model

### 4. Arbitration (`MLArbitrator`)
- Cross-checks subclass predictions against category predictions
- Computes Shannon entropy for uncertainty estimation
- Determines classification mode (8 possible states)
- Assigns confidence level (High/Medium/Low/Uncertain)

### 5. Knowledge Lookup (`WasteKnowledgeBase`)
- Maps subclass label to disposal guide
- Provides environmental impact information
- Includes recycling benefits and authoritative sources

### 6. Result Presentation
- Displays category, subclass, confidence, and disposal guide
- User can provide feedback (Correct/Incorrect/Not Sure)
- Record persisted to Room database

## YOLO Detection Pipeline

Separate from the classification pipeline:

1. CameraX preview provides live frames
2. Frames are converted to `Bitmap` via `ImageProxy`
3. `YoloDetector` runs YOLOv8n inference
4. Non-maximum suppression (NMS) filters detections
5. Results are rendered as bounding boxes on the camera preview

## Module Architecture

### `app` Module (Android)

Contains all Android-specific code:
- **ML inference**: TFLite model loading and execution
- **UI**: Compose screens, components, and theme
- **Data**: Room database and DataStore
- **Camera**: CameraX integration

### `shared` Module (Kotlin Multiplatform)

Contains platform-independent domain logic:
- **MLArbitrator**: Prediction cross-checking logic
- **WasteKnowledgeBase**: Disposal information for 30 waste types
- **WasteMapping**: Subclass-to-category mapping with material metadata
- **PredictionCodec**: String codec for persisting predictions
- **MessageGenerator**: Dynamic user-facing messages per classification mode
- **CommonModels**: Shared data classes (WasteRecord, PredictionResult, etc.)

### `iosApp` Module (SwiftUI)

Minimal iOS shell that bridges to the shared Kotlin module:
- Demonstrates knowledge base lookup via `IOSBridge`
- Does not yet include camera or ML inference

## Data Flow

```
User Action → ViewModel → Repository → Room DB
                    ↓
              ModelManager → Classifiers → TFLite
                    ↓
              MLArbitrator → KnowledgeBase
                    ↓
              UI Update → Screen Display
```

## Theme System

Three theme modes with a custom glassmorphism system:

1. **Dark**: Emerald neon palette with glass-effect cards
2. **Light**: Clean white/green palette
3. **Colour**: Olive/nature palette (defined but not yet exposed in settings)

Glass effects are provided via `LocalGlassColors` CompositionLocal, allowing any composable to access the current glass surface and border colors.

## Navigation

Single-activity architecture with Jetpack Navigation Compose:

- `Screen` sealed class defines routes
- `AppNavigation` composable sets up the NavHost
- ViewModels are scoped to the NavHost level (shared across destinations)
- Record IDs are passed as navigation arguments for historical results
