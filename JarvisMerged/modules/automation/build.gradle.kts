plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
}
android { namespace = "com.jarvismini.automation"; compileSdk=34; defaultConfig{minSdk=23} }
dependencies { implementation(project(":modules:llm")); implementation(project(":modules:smart")) }
