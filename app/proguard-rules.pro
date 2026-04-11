# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Kotlin metadata
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep Gson
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep ESP32 data models
-keep class com.driversafety.ai.** { *; }

# Keep Bluetooth UUIDs
-keep class java.util.UUID { *; }
