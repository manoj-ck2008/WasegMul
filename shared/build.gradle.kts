plugins {
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.agrelius.wasegmul.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 29
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    // In a full KMP project, we would add iosTarget() etc. here.
    // For now, we're setting up the structure.
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
