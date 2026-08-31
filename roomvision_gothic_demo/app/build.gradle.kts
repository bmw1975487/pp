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
        versionCode = 43
        versionName = "4.3.0"
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
        disable += setOf("MissingPermission", "GestureBackNavigation", "WrongThread")
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
}
