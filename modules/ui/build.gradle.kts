plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jarvismini.ui"
    compileSdk = 34

    defaultConfig {
        minSdk = 26   // ⚠️ MATCH app module
        targetSdk = 34
    }

    buildFeatures {
        compose = true   // ✅ REQUIRED
    }

    composeOptions {
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

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))

    // Compose core
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose")

    // Runtime
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
}
