plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jarvismini.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 23
        targetSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Core Android extensions
    implementation("androidx.core:core-ktx:1.12.0")

    // Gson for JSON serialization/deserialization
    implementation("com.google.code.gson:gson:2.10.1")
}
