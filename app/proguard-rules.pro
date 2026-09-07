# ==============================================================================
# Pixense ProGuard & R8 Optimization Rules
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. Stack Traces & Firebase Crashlytics Deobfuscation
# ------------------------------------------------------------------------------
# Preserve line number information and source files for readable Crashlytics reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve standard annotations and signatures
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ------------------------------------------------------------------------------
# 2. App Data Models & Room Database
# ------------------------------------------------------------------------------
# Preserve application data models, DTOs, and enums used across AI & UI layers
-keep class com.pixense.app.data.model.** { *; }
-keepclassmembers enum com.pixense.app.data.model.** { *; }

# Room Database entities, DAOs, and TypeConverters
-keep @androidx.room.Entity class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.TypeConverter { *; }
-keep class com.pixense.app.data.db.** { *; }
-dontwarn androidx.room.paging.**

# ------------------------------------------------------------------------------
# 3. Google Mobile Ads (AdMob) SDK
# ------------------------------------------------------------------------------
# Keep Google Play Services Ads public classes and callbacks
-keep public class com.google.android.gms.ads.** {
    public *;
}
-keep public class com.google.ads.** {
    public *;
}
-dontwarn com.google.android.gms.ads.**
-dontwarn com.google.ads.**

# ------------------------------------------------------------------------------
# 4. CameraX
# ------------------------------------------------------------------------------
# CameraX relies on reflection to discover device-specific quirks and implementations
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.view.** { *; }
-keep class androidx.camera.extensions.** { *; }
-dontwarn androidx.camera.**

# ------------------------------------------------------------------------------
# 5. OkHttp & Okio
# ------------------------------------------------------------------------------
# Suppress warnings on optional platform integrations (Conscrypt, BouncyCastle)
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keep OkHttp PublicSuffixDatabase resource
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ------------------------------------------------------------------------------
# 6. Kotlinx Coroutines & Flow
# ------------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}

# ------------------------------------------------------------------------------
# 7. Jetpack Compose
# ------------------------------------------------------------------------------
-dontwarn androidx.compose.**

# ------------------------------------------------------------------------------
# 8. Coil Image Loader
# ------------------------------------------------------------------------------
-dontwarn coil.**

# ------------------------------------------------------------------------------
# 9. Firebase & Google Services
# ------------------------------------------------------------------------------
-dontwarn com.google.firebase.**

# ------------------------------------------------------------------------------
# 10. Strip Debug & Verbose Logging in Release
# ------------------------------------------------------------------------------
# Strip verbose and debug logging calls in release builds to reduce APK size & overhead
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

