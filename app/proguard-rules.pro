# Add project specific ProGuard rules here.

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keep class com.xerahs.android.core.data.remote.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Gson
-keep class com.google.gson.** { *; }
-keepattributes *Annotation*
-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep data classes used for Gson deserialization
-keep class com.xerahs.android.feature.settings.data.GitHubRelease { *; }
-keep class com.xerahs.android.feature.settings.data.GitHubAsset { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# JSch
-keep class com.jcraft.jsch.** { *; }
-dontwarn org.newsclub.net.unix.**
-dontwarn org.slf4j.**
-dontwarn org.ietf.jgss.**
-dontwarn com.sun.jna.**
-dontwarn com.sun.jna.platform.**

# Apache Commons Net
-keep class org.apache.commons.net.** { *; }

# Google Tink / ErrorProne annotations
-dontwarn com.google.errorprone.annotations.**

# Logging
-dontwarn org.apache.logging.log4j.**
