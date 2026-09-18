# ─────────────────────────────────────────────────────────────────────────────
# WasegMul ProGuard / R8 rules
# ─────────────────────────────────────────────────────────────────────────────

# Keep line numbers for readable crash stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep generic signatures (needed by Room, Compose, kotlinx coroutines).
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# ── Room (app-specific only - library ships its own rules) ───────────────────
-keep class com.agrelius.wasegmul.data.WasteRecord { *; }
-keep class com.agrelius.wasegmul.data.WasteDao { <methods>; }
-keep class com.agrelius.wasegmul.data.WasteDatabase { *; }
-keep class com.agrelius.wasegmul.data.BarcodeProduct { *; }
-keep class com.agrelius.wasegmul.data.BarcodeProductDao { <methods>; }
-keep class com.agrelius.wasegmul.data.disposal.** { *; }

# ── Serialization & Network Models ───────────────────────────────────────────
# (single block: SerialName members + OFF DTOs + shared packaging mapper;
# duplicates removed — keep this the ONE network/serialization section)
-keepattributes *Annotation*
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep class com.agrelius.wasegmul.network.** { *; }
-keepclassmembers class com.agrelius.wasegmul.network.** {
    <fields>;
    <methods>;
}
-keepclasseswithmembers class com.agrelius.wasegmul.network.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Serializer core (required when minify is on)
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**
-keep class com.agrelius.wasegmul.ResolvedPackagingComponent { *; }
-keep class com.agrelius.wasegmul.PackagingWasteMapper { *; }

# ── Enums (needed for valueOf() lookups) ──────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── ViewModel Factories (needed for R8 to keep Factory.create()) ──────────────
-keep class * extends androidx.lifecycle.ViewModel
-keep class * extends androidx.lifecycle.ViewModelProvider$Factory { *; }

# ── TensorFlow Lite (app-specific only - library ships its own rules) ────────
-keep class com.agrelius.wasegmul.ml.classifiers.** { *; }
-keep class com.agrelius.wasegmul.ml.YoloDetector { *; }
-keep class com.agrelius.wasegmul.ml.Detection { *; }
-keep class com.agrelius.wasegmul.ml.ModelManager { *; }

# ── Google Play Services TFLite ──────────────────────────────────────────────
-dontwarn com.google.android.gms.internal.mlkit_common.**
-dontwarn com.google.android.gms.internal.mlkit_vision_common.**

# ── Shared KMP module (reflection-free, but keep model classes for safety) ───
-keep class com.agrelius.wasegmul.WasteRecord { *; }
-keep class com.agrelius.wasegmul.PredictionResult { *; }
-keep class com.agrelius.wasegmul.ClassificationResult { *; }
-keep class com.agrelius.wasegmul.InternalResult { *; }
-keep class com.agrelius.wasegmul.WasteInfo { *; }
-keep class com.agrelius.wasegmul.WasteMapping { *; }
-keep class com.agrelius.wasegmul.WasteMapping$MaterialMetaData { *; }
-keep class com.agrelius.wasegmul.WasteKnowledgeBase { *; }
-keep class com.agrelius.wasegmul.MLArbitrator { *; }
-keep class com.agrelius.wasegmul.MessageGenerator { *; }
-keep class com.agrelius.wasegmul.PredictionCodec { *; }
-keep class com.agrelius.wasegmul.EcoImpactCalculator { *; }
-keep class com.agrelius.wasegmul.EcoImpactMetrics { *; }
-keep class com.agrelius.wasegmul.IOSBridge { *; }
-keep class com.agrelius.wasegmul.ClassificationMode { *; }

# ── CameraX (app-specific only - library ships its own rules) ────────────────
-keep class androidx.camera.core.ImageProxy { *; }

# ── Kotlin coroutines / Guava (TFLite dependency) ────────────────────────────
-dontwarn com.google.common.**

# ── Application class (referenced from AndroidManifest) ──────────────────────
-keep class com.agrelius.wasegmul.WasegMulApp { *; }
-keep class com.agrelius.wasegmul.MainActivity { *; }

# ── kotlinx.serialization (OFF API DTOs): merged into the Serialization & ──
# ── Network Models block above; kept here as pointer only (no duplicate rules).

# ── Disposal enum used via valueOf() ───────────────────────────────────────────
-keep class com.agrelius.wasegmul.data.disposal.DisposalCenterType { *; }
-keep class com.agrelius.wasegmul.data.disposal.DisposalCenter { *; }
