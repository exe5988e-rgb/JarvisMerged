-keep class com.syncwave.app.AndroidBridge { *; }
-keepclassmembers class com.syncwave.app.AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.syncwave.app.AudioForegroundService { *; }
