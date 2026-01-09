plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jarvismini"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jarvismini"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        viewBinding = true
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    // 🔌 HTTP client for localhost API (Termux → Jarvis)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation(project(":modules:core"))
    implementation(project(":modules:automation"))
    implementation(project(":modules:engine"))
    implementation(project(":modules:smart"))
    implementation(project(":modules:ui"))
    implementation(project(":modules:callhandler"))
}
