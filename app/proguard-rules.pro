# ─────────────────────────────────────────────────────────────────────────────
# WasegMul ProGuard / R8 rules
# ─────────────────────────────────────────────────────────────────────────────

# Keep line numbers for readable crash stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep generic signatures (needed by Room, Compose, kotlinx coroutines).
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# ── Room ────────────────────────────────────────────────────────────────────
# Room's generated Dao_Impl classes use reflection; keep entities and Dao interfaces.
-keep class com.agrelius.wasegmul.data.WasteRecord { *; }
-keep class com.agrelius.wasegmul.data.WasteDao { *; }
-keep class com.agrelius.wasegmul.data.WasteDatabase { *; }
-keep class androidx.room.** { *; }

# ── TensorFlow Lite ──────────────────────────────────────────────────────────
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-dontwarn org.tensorflow.lite.**

# ── Google Play Services TFLite ──────────────────────────────────────────────
# Official consumer ProGuard snippet for play-services-tflite.
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.gms.tflite.** { *; }
-dontwarn com.google.android.gms.internal.mlkit_common.**
-dontwarn com.google.android.gms.internal.mlkit_vision_common.**

# Our classifier classes (loaded by name from assets).
-keep class com.agrelius.wasegmul.ml.** { *; }

# ── Shared KMP module (reflection-free, but keep model classes for safety) ───
-keep class com.agrelius.wasegmul.WasteRecord { *; }
-keep class com.agrelius.wasegmul.PredictionResult { *; }
-keep class com.agrelius.wasegmul.ClassificationResult { *; }
-keep class com.agrelius.wasegmul.InternalResult { *; }
-keep class com.agrelius.wasegmul.WasteInfo { *; }
-keep class com.agrelius.wasegmul.WasteMapping { *; }
-keep class com.agrelius.wasegmul.WasteMapping$MaterialMetaData { *; }
-keep class com.agrelius.wasegmul.MLArbitrator { *; }
-keep class com.agrelius.wasegmul.WasteKnowledgeBase { *; }
-keep class com.agrelius.wasegmul.PredictionCodec { *; }

# ── Compose / Kotlin metadata ────────────────────────────────────────────────
# Compose compiler emits metadata referenced at runtime; keep only what's needed.
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-dontwarn androidx.compose.**

# ── DataStore / coroutines / Guava (TFLite dependency) ───────────────────────
-keep class androidx.datastore.** { *; }
-dontwarn kotlinx.coroutines.**
-dontwarn com.google.common.**
-dontwarn com.google.android.gms.**

# ── Application class (referenced from AndroidManifest) ──────────────────────
-keep class com.agrelius.wasegmul.WasegMulApp { *; }
-keep class com.agrelius.wasegmul.MainActivity { *; }
