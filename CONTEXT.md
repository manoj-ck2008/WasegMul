# WasegMul Context

Multimodal waste segregation and classification system across Android (Jetpack Compose, CameraX, TensorFlow Lite) and Kotlin Multiplatform (KMP shared domain logic and iOS bridge).

## Language

### Waste Classification Domain

**Category**:
The top-level classification tier of waste (e.g., Organic, Recyclable, Hazardous, E-Waste, Residual).
_Avoid_: Type, Kind, Tag, Group

**Subclass**:
The fine-grained specific material identity within a category (e.g., PET Bottle, Cardboard Box, Banana Peel).
_Avoid_: Subtype, Sub-item, SpecificType

**Waste Record**:
The persisted entity representing a classified waste item, containing category, subclass, confidence, weight estimate, and impact metrics.
_Avoid_: ScanItem, LogEntry, TrashRow

**ML Arbitrator**:
The shared engine (`MLArbitrator`) that reconciles category and subclass predictions using hierarchical taxonomy rules and confidence thresholds.
_Avoid_: ModelResolver, DecisionTree, ClassifierDecider

**YOLO Detector**:
The object detection model (`YoloDetector`) running in real-time on camera frames to locate waste items and generate bounding boxes.
_Avoid_: ObjectFinder, BoxLocator

**Eco Impact**:
The quantifiable ecological benefit (carbon emissions avoided in kg CO2, water saved in litres, energy saved in kWh) calculated by `EcoImpactCalculator`.
_Avoid_: GreenScore, EcoPoints, CarbonReduction

**Knowledge Base**:
The repository (`WasteKnowledgeBase`) providing disposal instructions, environmental context, and recycling guidelines per waste class.
_Avoid_: DisposalWiki, GuidanceStore, RuleEngine
