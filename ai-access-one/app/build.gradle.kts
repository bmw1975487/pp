plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace="com.bmw1975487.aione"; compileSdk=36
 defaultConfig { applicationId="com.bmw1975487.aione"; minSdk=26; targetSdk=36; versionCode=1; versionName="0.1.0" }
 flavorDimensions += "mode"
 productFlavors {
   create("bootstrap") { dimension="mode"; applicationIdSuffix=".bootstrap"; versionNameSuffix="-bootstrap"; buildConfigField("boolean","NETWORK_CORE_ENABLED","false") }
   create("full") { dimension="mode"; buildConfigField("boolean","NETWORK_CORE_ENABLED","true") }
 }
 buildTypes { debug { isMinifyEnabled=false }; release { isMinifyEnabled=true; isShrinkResources=false; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro") } }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
 buildFeatures { buildConfig=true }
}
dependencies { "fullImplementation"(files("libs/libbox.aar")) }
