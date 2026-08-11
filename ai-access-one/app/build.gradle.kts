plugins {
    id("com.android.application")
}

android {
    namespace = "com.bmw1975487.aione"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bmw1975487.aione.bootstrap"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1-diag"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
