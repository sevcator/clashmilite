plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "org.sevcator.miniclash"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.sevcator.miniclash"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs.useLegacyPackaging = false }
}

dependencies {
    implementation(files("libs/libmihomo-android.aar"))
    implementation("org.yaml:snakeyaml:2.2")
}
