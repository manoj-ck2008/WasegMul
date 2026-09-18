import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// Version from gradle.properties (single source of truth).
val appVersionName = (project.findProperty("VERSION_NAME") as String?) ?: "1.1.0"
val appVersionCode = (project.findProperty("VERSION_CODE") as String?)?.toIntOrNull() ?: 2

// Reads optional release signing credentials from the project root (keystore.properties).
// This file is NOT committed to VCS: see README for the expected keys.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.agrelius.wasegmul"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.agrelius.wasegmul"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Make version available in BuildConfig
        buildConfigField("String", "VERSION_NAME", "\"$appVersionName\"")
        buildConfigField("int", "VERSION_CODE", "$appVersionCode")
    }

    signingConfigs {
        create("release") {
            keystoreProperties["storeFile"]?.let { storeFile = rootProject.file(it) }
            storePassword = keystoreProperties["storePassword"] as String?
            keyAlias = keystoreProperties["keyAlias"] as String?
            keyPassword = keystoreProperties["keyPassword"] as String?
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true      // R8 code shrinking + obfuscation
            isShrinkResources = true    // Remove unused resources
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release builds MUST be signed with the release key.
            // CI/CD must provide keystore.properties; local dev uses debug builds.
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}


// Validate release signing credentials at build time.
tasks.configureEach {
    if (name.startsWith("merge") && name.contains("Release", ignoreCase = true)) {
        doFirst {
            if (keystoreProperties.isEmpty()) {
                throw GradleException(
                    "RELEASE BUILD BLOCKED: No keystore.properties found. " +
                    "Create keystore.properties in the project root with release signing credentials."
                )
            }
            val storeFileProp = keystoreProperties["storeFile"]?.toString()
            if (storeFileProp.isNullOrBlank() || !rootProject.file(storeFileProp).exists()) {
                throw GradleException("RELEASE BUILD BLOCKED: storeFile missing or not found: $storeFileProp")
            }
            for (k in listOf("storePassword", "keyAlias", "keyPassword")) {
                if (keystoreProperties[k]?.toString().isNullOrBlank()) {
                    throw GradleException("RELEASE BUILD BLOCKED: $k missing in keystore.properties")
                }
            }
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // ML & Camera
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)
    implementation(libs.playServicesTflite)
    implementation(libs.kotlinxCoroutinesPlayServices)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit Barcode Scanning (on-device, offline-capable)
    implementation(libs.mlkit.barcode.scanning)

    // Network (Open Food Facts API via shared KMP Ktor client)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
