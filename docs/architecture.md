# WasegMul System Architecture & Technical Specification

This document provides a comprehensive architectural specification of the WasegMul (Waste Segregation Multi-model AI) platform, covering the multi-model machine learning pipeline, shared Kotlin Multiplatform (KMP) domain layer, Android UI/UX architecture, Room database schema, and thread synchronization guarantees.

---

## 1. High-Level System Architecture

WasegMul is structured into clean architectural layers following separation-of-concerns principles. The application core is divided between an Android-specific host (`app/`) and a platform-agnostic Kotlin Multiplatform library (`shared/`).

```mermaid
graph TD
    subgraph UI_Layer ["Presentation Layer (Jetpack Compose)"]
        NavHost["AppNavigation (NavHost)"]
        Screens["Screens: Home, Classify, YOLO Live, Result, History, Guide, Settings"]
        Components["Components: GlassCard, HeroCard, InsightCard, AppLogo, FallingPetals"]
        ViewModels["ViewModels: ClassificationViewModel, HomeViewModel, SettingsViewModel"]
    end

    subgraph ML_Layer ["Machine Learning Inference Layer"]
        ModelManager["ModelManager (Parallel Inference Coordinator)"]
        CatClassifier["CategoryClassifier (4 Classes)"]
        SubClassifier["SubclassClassifier (30 Subclasses)"]
        TfliteBase["TfliteClassifier (Shared Native Interpreter Core)"]
        YoloDetector["YoloDetector (Zero-Allocation YOLO11n/v8n Pipeline)"]
        Preprocessor["ImagePreprocessor (Hardware Bitmap Conversion & Normalization)"]
    end

    subgraph Domain_Layer ["Shared KMP Domain Layer (:shared)"]
        MLArbitrator["MLArbitrator (Cross-Validation & Shannon Entropy)"]
        EcoImpact["EcoImpactCalculator (CO2, Water, Energy kWh, Trees)"]
        KnowledgeBase["WasteKnowledgeBase (Disposal Guides & Protocols)"]
        WasteMapping["WasteMapping (Subclass to Category Metadata Mapping)"]
        MessageGen["MessageGenerator (Contextual User Messages)"]
        PredictionCodec["PredictionCodec (String Serializer)"]
        IOSBridge["IOSBridge (Swift Flat Array Bridge)"]
    end

    subgraph Data_Layer ["Persistence & Platform Services"]
        Repository["WasteRepository (Interface & DefaultWasteRepository)"]
        WasteDao["WasteDao (Room Data Access Object)"]
        WasteDB["WasteDatabase (Room SQLite Schema v9)"]
        DataStore["SettingsManager (Preferences DataStore)"]
        CameraX["CameraX (Preview & ImageAnalysis)"]
    end

    Screens --> ViewModels
    ViewModels --> Repository
    ViewModels --> ModelManager
    ViewModels --> YoloDetector
    ModelManager --> CatClassifier
    ModelManager --> SubClassifier
    CatClassifier --> TfliteBase
    SubClassifier --> TfliteBase
    ModelManager --> MLArbitrator
    MLArbitrator --> WasteMapping
    MLArbitrator --> KnowledgeBase
    MLArbitrator --> MessageGen
    Repository --> WasteDao
    WasteDao --> WasteDB
    Repository --> EcoImpact
    YoloDetector --> Preprocessor
    CameraX --> YoloDetector
    Screens --> DataStore
```

---

## 2. Multi-Model Inference & Arbitration Pipeline

The primary classification pipeline coordinates two specialized EfficientNet neural networks executing in parallel, combined through an analytical arbitration state machine.

### 2.1 Inference & Arbitration Flow

```mermaid
flowchart TD
    Start(["Input Image: Camera / Gallery"]) --> Preproc["ImagePreprocessor: Center-Crop & Resize (224x224x3)"]
    Preproc --> ParallelExec{"ModelManager: Parallel Execution"}

    subgraph Parallel_Inference ["Parallel Native TFLite Inference"]
        ParallelExec -->|"async(Dispatchers.Default)"| CatInfer["Category Model: EfficientNet-B0 (4 Classes)"]
        ParallelExec -->|"async(Dispatchers.Default)"| SubInfer["Subclass Model: EfficientNet-B0 (30 Classes)"]
    end

    CatInfer --> RawCat["Category Probabilities (Softmax)"]
    SubInfer --> RawSub["Subclass Probabilities (Softmax)"]

    RawCat --> Arbitrator["MLArbitrator.arbitrate(categoryPredictions, subclassPredictions)"]
    RawSub --> Arbitrator

    subgraph Arbitration_Logic ["MLArbitrator Decision Process"]
        Arbitrator --> CheckAgreement{"Does top subclass map to top category?"}
        CheckAgreement -->|"Yes: Consistent"| ModeAgree["Outcome: AGREEMENT (High Confidence)"]
        CheckAgreement -->|"No: Conflict"| EvalEntropy["Compute Shannon Entropy on Category Distribution"]

        EvalEntropy --> ConfCheck{"Subclass Conf >= 0.70 & Category Conf < 0.60?"}
        ConfCheck -->|"Yes: Subclass Overrides"| SubOverride["Override Category with Subclass Mapped Category"]
        ConfCheck -->|"No: Low Confidence"| CatOverride["Trust Category or Mark UNCERTAIN"]
    end

    ModeAgree --> KnowledgeLookup["WasteKnowledgeBase: Protocol, Impact, Sources"]
    SubOverride --> KnowledgeLookup
    CatOverride --> KnowledgeLookup

    KnowledgeLookup --> OutputResult["Construct PredictionResult"]
    OutputResult --> SaveDB["WasteRepository.insert(WasteRecord)"]
    OutputResult --> DisplayUI["ResultScreen: Display Breakdown & Insights"]
```

### 2.2 Shannon Entropy & Uncertainty Estimation

The arbitrator computes Shannon entropy across the category probability distribution \(P = \{p_1, p_2, \dots, p_N\}\) to measure model ambiguity:

$$H(P) = - \sum_{i=1}^{N} p_i \log_2(p_i)$$

- Normalized entropy: \(H_{\text{norm}} = \frac{H(P)}{\log_2(N)}\) where \(N = 4\) categories.
- When \(H_{\text{norm}} > 0.75\), the category classifier is categorized as confused.
- If the subclass model outputs a high-confidence match (confidence \(\ge 0.70\)) whose mapped category differs from the category model's confused output, the arbitrator trusts the subclass evidence and resolves the category conflict automatically.

### 2.3 Arbitration State Machine

```mermaid
stateDiagram-v2
    [*] --> Evaluating

    Evaluating --> HighConfidenceAgreement: Subclass mapped category == Category & Confidence >= 0.70
    Evaluating --> ModerateConfidenceAgreement: Subclass mapped category == Category & Confidence < 0.70
    Evaluating --> SubclassOverrideCategory: Subclass Conf >= 0.70 & Category Conf < 0.60
    Evaluating --> CategoryOverrideSubclass: Subclass Conf < 0.50 & Category Conf >= 0.75
    Evaluating --> ConflictedPrediction: Both Conf >= 0.70 but Categories Disagree
    Evaluating --> DegradedSubclassOnly: Category classifier unavailable
    Evaluating --> DegradedCategoryOnly: Subclass classifier unavailable
    Evaluating --> LowConfidenceUncertain: Both models confidence < 0.50

    HighConfidenceAgreement --> OutputProduced
    ModerateConfidenceAgreement --> OutputProduced
    SubclassOverrideCategory --> OutputProduced
    CategoryOverrideSubclass --> OutputProduced
    ConflictedPrediction --> OutputProduced
    DegradedSubclassOnly --> OutputProduced
    DegradedCategoryOnly --> OutputProduced
    LowConfidenceUncertain --> OutputProduced
```

---

## 3. Real-Time YOLO Detection Architecture

The YOLO pipeline operates asynchronously on the live CameraX video stream, performing real-time multi-object localization and interactive Region-of-Interest (ROI) classification.

```mermaid
sequenceDiagram
    autonumber
    participant Camera as CameraX (ImageAnalysis)
    participant Detector as YoloDetector
    participant UI as YoloScreen (DetectionOverlay)
    participant User as User Interaction
    participant Manager as ModelManager

    Camera->>Detector: ImageProxy frame (YUV/RGBA)
    Detector->>Detector: Convert to Bitmap & Letterbox draw (Direct Canvas)
    Detector->>Detector: Direct ByteBuffer copy (Zero-Allocation)
    Detector->>Detector: Native TFLite Interpreter.run(inputBuffer, outputBuffer)
    Detector->>Detector: Direct indexing parseDetections (Channel-first / Anchor-first)
    Detector->>Detector: Vectorized NMS with boolean suppression mask
    Detector-->>UI: List of Detection(boundingBox, confidence, label)
    UI->>UI: Dynamic aspect ratio scaling (FILL_CENTER alignment)
    UI->>UI: Draw animated bounding boxes & confidence badges

    User->>UI: Tap on bounding box (ROI selection)
    UI->>UI: Highlight selected box with emerald focus outline
    User->>UI: Tap "Capture & Classify"
    UI->>UI: Crop bounding box sub-bitmap with 8% padding
    UI->>Manager: classify(croppedBitmap)
    Manager-->>UI: ClassificationOutcome.Success(PredictionResult)
    UI->>UI: Navigate to ResultScreen
```

### 3.1 Zero-Allocation Memory Design in `YoloDetector`

To prevent garbage collection pauses during 20-30 FPS camera analysis:
1. **Preallocated Buffers**: A single native direct `ByteBuffer` for model input (`1 * 640 * 640 * 3 * 4` bytes = 4.91 MB) and output (`1 * C * Anchors * 4` bytes) are allocated once at initialization.
2. **Zero-Allocation Transposition**: Rather than copying and transposing 2.8 MB multidimensional float arrays per frame, the detector uses mathematical index calculations:
   - Output layout detection determines stride dynamically: `outputArray[c * numAnchors + a]` vs `outputArray[a * numChannels + c]`.
3. **NMS Suppression Bitmask**: Non-maximum suppression uses a preallocated `BooleanArray(candidates.size)` to flag suppressed boxes in O(N) memory without allocating list mutations.

---

## 4. Data Layer & Room Database Schema

The persistence layer is implemented using Room SQLite, updated to schema version 9 with composite indices to guarantee instant queries across historical logs.

### 4.1 Database Entity-Relationship Diagram

```mermaid
erDiagram
    WASTE_HISTORY {
        INTEGER id PK "Auto-generated primary key"
        TEXT category "Category name (Recyclable, Organic, etc.) [Indexed]"
        TEXT subclass "Subclass name (e.g. plastic_bottle) [Indexed]"
        REAL confidence "Model confidence value (0.0 to 1.0)"
        INTEGER timestamp "Unix epoch milliseconds [Indexed]"
        TEXT feedback "Feedback flag ('correct', 'incorrect', null) [Indexed]"
        TEXT correctedSubclass "User-supplied correction subclass"
        REAL estimatedWeight "Estimated unit weight in kilograms"
        TEXT topPredictions "Serialized top predictions JSON/CSV"
        TEXT imagePath "Local filesystem path (optional)"
        TEXT featureVector "Latent vector embeddings (optional)"
    }
```

### 4.2 Room Migration 8 to 9

Migration `MIGRATION_8_9` creates targeted performance indices on critical query columns:
```sql
CREATE INDEX IF NOT EXISTS index_waste_history_category ON waste_history (category);
CREATE INDEX IF NOT EXISTS index_waste_history_subclass ON waste_history (subclass);
CREATE INDEX IF NOT EXISTS index_waste_history_feedback ON waste_history (feedback);
CREATE INDEX IF NOT EXISTS index_waste_history_timestamp ON waste_history (timestamp);
```

---

## 5. UI Navigation Graph

The application follows single-activity architecture powered by Jetpack Navigation Compose.

```mermaid
graph LR
    Splash["SplashScreen"] -->|"Animation Timeout (1.8s)"| Home["HomeScreen"]
    Home -->|"Launch Scanner"| Classify["ClassifyScreen (CameraX Capture)"]
    Home -->|"Import Gallery"| Classify
    Home -->|"Live YOLO Detect"| Yolo["YoloScreen (Real-Time Localizer)"]
    Home -->|"History Action"| History["HistoryScreen (Log, Search & Filter)"]
    Home -->|"Disposal Guide"| Guide["GuideScreen (Encyclopedia)"]
    Home -->|"Settings Icon"| Settings["SettingsScreen (Config & Licenses)"]

    Classify -->|"Inference Success"| Result["ResultScreen (Report & Actions)"]
    Yolo -->|"Capture & Classify ROI"| Result
    History -->|"Tap Record"| Result

    Result -->|"Acknowledge & Close"| Home
    Result -->|"Scan Another"| Classify
    Guide -->|"Back Navigation"| Home
    Settings -->|"Back Navigation"| Home
    History -->|"Back Navigation"| Home
```

---

## 6. Eco Impact Calculation Model

The `EcoImpactCalculator` derives cumulative environmental diversion statistics from confirmed and uncorrected records:

```mermaid
graph TD
    subgraph Records ["Waste History Filter"]
        ValidRecords["Exclude records marked as incorrect without correction"]
        CorrectedRecords["Use correctedCategory for corrected records"]
    end

    subgraph Metrics ["Derived Metrics"]
        Weight["Total Weight (kg) = Sum of estimatedWeight"]
        CO2["Carbon Offset = Weight * Category CO2 Factor"]
        Water["Water Conserved = Weight * Category Water Factor"]
        Energy["Energy Saved (kWh) = Weight * Category Energy Factor"]
        Trees["Tree Absorption Eq = Carbon Offset / 21.77 kg/year"]
    end

    Records --> Weight
    Weight --> CO2
    Weight --> Water
    Weight --> Energy
    CO2 --> Trees
```

### Mathematical Multipliers

| Category | Carbon Offset (kg CO2e / kg) | Water Conserved (Litres / kg) | Energy Saved (kWh / kg) |
|:---|:---|:---|:---|
| **Recyclable** | 1.80 | 25.0 | 4.20 |
| **E-Waste** | 3.50 | 15.0 | 6.50 |
| **Organic** | 0.50 | 2.0 | 0.30 |
| **Trash** | 0.00 | 0.0 | 0.00 |
| **Hazardous** | 2.20 | 10.0 | 3.00 |

---

## 7. Concurrency & Thread Safety Model

To prevent Android lifecycle race conditions, UI jank, and native TFLite segmentation faults:

```mermaid
sequenceDiagram
    participant UI as Main / UI Thread
    participant VM as ViewModelScope (Dispatchers.Default)
    participant MM as ModelManager
    participant Lock as classifyMutex / Mutex
    participant Native as libtensorflowlite_jni.so

    UI->>VM: classify(bitmap)
    VM->>MM: classify()
    MM->>Lock: withLock
    Lock-->>MM: Acquired
    Note over MM,Native: Active native inference in progress

    Note over UI: User presses back / ViewModel onCleared()
    UI->>MM: close()
    Note over MM: Sets closed = true atomically
    MM->>Lock: tryLock()
    Note over MM: Lock is held by classify()!
    Note over MM: Non-blocking fallback: skips immediate free, lets in-flight classify finish safely
    MM-->>UI: Returns immediately (Zero UI Jank)

    Native-->>MM: Inference completed
    MM->>Lock: unlock()
    Note over MM: In-flight execution sees closed == true and safely discards output
    MM->>Native: Interpreter.close() (Safely freed without SIGSEGV)
```
