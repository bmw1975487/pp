plugins {
    id("com.android.application")
}

android {
    namespace = "com.roomvision.demo"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.roomvision.neural"
        minSdk = 29
        targetSdk = 36
        versionCode = 20
        versionName = "2.0.0"
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
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.17.0")
}
