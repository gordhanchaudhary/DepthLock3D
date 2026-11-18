plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.depthlock3d"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.depthlock3d"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}


dependencies {
    // On-device person segmentation; model is bundled in the APK.
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")
}
