plugins {
    id("com.android.application")
}

android {
    namespace = "com.roomvision.demo"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.roomvision.filters"
        minSdk = 29
        targetSdk = 36
        versionCode = 50
        versionName = "5.0.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        disable += setOf("MissingPermission", "GestureBackNavigation", "WrongThread", "UnsafeOptInUsageError")
        abortOnError = true
        warningsAsErrors = false
    }
}

dependencies {
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")

    // 2026 rendering / on-device vision stack.
    // Filament supplies the real-time renderer; we explicitly request its Vulkan backend.
    implementation("com.google.android.filament:filament-android:1.75.1")

    // Current Google AI Edge runtime. Kept available for later world-specific neural models.
    implementation("com.google.ai.edge.litert:litert:2.2.0")

    // Scene / vision tasks for future semantic masks and geometry-aware worlds.
    implementation("com.google.mediapipe:tasks-vision:1.0.0")

    // AGSL RuntimeShader is part of the Android framework on Android 13+;
    // no extra dependency is required. Gothic uses it directly when available.
}
