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


---

✅ This batch:

1. Fully persistent progress with Gson.


2. Time-aware: scheduled vs completed vs missed.


3. Idempotent: hydration and missed-task marking safe multiple times.


4. Missed reminders: MissedTaskChecker only announces overdue tasks.


5. Minimal off-main-thread I/O, safe for early bootstrap.




---

If you want, the next step could be adding automatic daily re-checks for missed tasks so users are reminded even if the app wasn’t restarted.

Do you want me to do that?
