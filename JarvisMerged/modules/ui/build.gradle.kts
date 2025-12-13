plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.jarvismini.engine"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20231013")
    implementation(kotlin("stdlib"))
}