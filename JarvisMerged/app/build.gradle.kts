plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}
android {
  namespace = "com.jarvismini"
  compileSdk = 34
  defaultConfig {
    applicationId = "com.jarvismini"
    minSdk = 23
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"
  }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
}
dependencies {
  implementation(project(":modules:core"))
  implementation(project(":modules:automation"))
  implementation(project(":modules:llm"))
  implementation(project(":modules:smart"))
  implementation(project(":modules:ui"))
}
