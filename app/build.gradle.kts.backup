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
        compose = true
    }

    composeOptions {
        // ⚠️ MUST match your Kotlin version!
        // If using Kotlin 1.9.22 → use 1.5.8
        // If using Kotlin 2.0.0 → use 2.0.0
        kotlinCompilerExtensionVersion = "1.5.8"
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

    // ✅ Compose BOM - this controls versions of compose dependencies
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))

    // ✅ Compose UI (versions controlled by BOM)
    implementation("androidx.activity:activity-compose")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    
    // ✅ Material 3 (required for your UI)
    implementation("androidx.compose.material3:material3")
    
    // ✅ Material Icons Extended (required for Icons.Default.* in your code)
    implementation("androidx.compose.material:material-icons-extended")
    
    // ✅ Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // ✅ Foundation (required for Canvas, background, border, etc.)
    implementation("androidx.compose.foundation:foundation")

    // ✅ Runtime (required for remember, mutableStateOf, LaunchedEffect)
    implementation("androidx.compose.runtime:runtime")
    
    // ✅ Animation (required for animateFloat, rememberInfiniteTransition)
    implementation("androidx.compose.animation:animation")

    // Debug tools
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Material (for non-compose usage)
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
