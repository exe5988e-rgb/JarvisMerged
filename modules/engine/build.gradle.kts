plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.jarvismini.engine"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        targetSdk = 34
    }
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20231013")

    // 🔥 REQUIRED
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
