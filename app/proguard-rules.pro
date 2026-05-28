# ProGuard rules for LunaTV with ExoPlayer

# ExoPlayer / Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep LunaBridge for JS interface
-keepclassmembers class com.lunatv.app.LunaBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep PlayerActivity
-keep class com.lunatv.app.PlayerActivity { *; }
-keep class com.lunatv.app.MainActivity { *; }
