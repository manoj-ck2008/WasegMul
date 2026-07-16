# ─────────────────────────────────────────────────────────────────────────────
# WasegMul ProGuard / R8 rules
# ─────────────────────────────────────────────────────────────────────────────

# Keep line numbers for readable crash stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep generic signatures (needed by Room, Compose, kotlinx coroutines).
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# ── Room (app-specific only — library ships its own rules) ───────────────────
-keep class com.agrelius.wasegmul.data.WasteRecord { *; }
-keep class com.agrelius.wasegmul.data.WasteDao { *; }
-keep class com.agrelius.wasegmul.data.WasteDatabase { *; }

# ── TensorFlow Lite (app-specific only — library ships its own rules) ────────
-keep class com.agrelius.wasegmul.ml.classifiers.** { *; }

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

# ── CameraX (app-specific only — library ships its own rules) ────────────────
-keep class androidx.camera.core.ImageProxy { *; }

# ── Kotlin coroutines / Guava (TFLite dependency) ────────────────────────────
-dontwarn com.google.common.**
-dontwarn com.google.android.gms.internal.mlkit_vision_common.**

# ── Application class (referenced from AndroidManifest) ──────────────────────
-keep class com.agrelius.wasegmul.WasegMulApp { *; }
-keep class com.agrelius.wasegmul.MainActivity { *; }
