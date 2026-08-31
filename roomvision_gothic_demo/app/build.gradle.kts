plugins {
    id("com.android.application")
}

android {
    namespace = "com.roomvision.demo"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        // Separate package so 5.2 cannot accidentally launch an older 4.x/5.1 install.
        applicationId = "com.roomvision.modern"
        minSdk = 29
        targetSdk = 36
        versionCode = 52
        versionName = "5.2.0"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    androidResources {
        noCompress += "tflite"
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

    implementation("com.google.android.filament:filament-android:1.75.1")
    implementation("com.google.ai.edge.litert:litert:2.2.0")
    implementation("com.google.mediapipe:tasks-vision:1.0.0")

    // AGSL RuntimeShader is provided by Android 13+ and is used by Gothic directly.
}
