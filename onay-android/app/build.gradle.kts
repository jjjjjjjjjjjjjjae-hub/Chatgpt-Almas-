plugins {
    id("com.android.application")
}
android {
    namespace = "kz.almas.onayuidemo"
    compileSdk = 35
    defaultConfig {
        applicationId = "kz.almas.onayuidemo"
        minSdk = 23
        targetSdk = 35
        versionCode = 9
        versionName = "9.0-demo"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
