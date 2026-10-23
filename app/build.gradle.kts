plugins { id("com.android.application") }
android {
    namespace = "cn.shixu.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "cn.shixu.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 55
        versionName = "1.0.12"
        providers.gradleProperty("targetAbis").orNull?.let { ndk { abiFilters += it.split(',') } }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    signingConfigs.getByName("debug") {
        storeFile = rootProject.file("build/debug.jks")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
    }
    buildTypes.getByName("release") {
        isMinifyEnabled = true
        isShrinkResources = true
        signingConfig = signingConfigs.getByName("debug")
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    packaging { jniLibs.useLegacyPackaging = true }
}
dependencies { implementation("com.google.mlkit:text-recognition-chinese:16.0.1") }

