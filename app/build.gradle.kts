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
        compose = true   // ✅ REQUIRED
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8" // ✅ Stable for Kotlin + AGP
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

    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    // Compose BOM (controls ALL compose versions)
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))

    // Compose core
    implementation("androidx.activity:activity-compose")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")


    // Debug tools
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Material (still OK for non-compose usage)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Modules
    implementation(project(":modules:core"))
    implementation(project(":modules:automation"))
    implementation(project(":modules:engine"))
    implementation(project(":modules:smart"))
    implementation(project(":modules:callhandler"))
}
