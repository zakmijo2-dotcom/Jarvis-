# JARVIS Release R8 / ProGuard Configuration

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Strip verbose, debug, and info logs in release builds while preserving errors/warnings
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Kotlinx Serialization & Navigation Compose @Serializable routes
-dontnote kotlinx.serialization.SerializationKt
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations
-keep @kotlinx.serialization.Serializable class com.example.** { *; }
-keep,includedescriptorclasses class com.example.**$$serializer { *; }
-keepclassmembers class com.example.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# LiteRT-LM native inference runtime and JNI callbacks
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**

# Picovoice Porcupine native wake-word engine and JNI callbacks
-keep class ai.picovoice.porcupine.** { *; }
-dontwarn ai.picovoice.porcupine.**

# Room database, DAOs & entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-dontwarn androidx.room.paging.**

# OkHttp / Okio / TLS platform reflection hooks
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepclassmembers class okhttp3.internal.platform.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn javax.naming.**
-dontwarn java.awt.**
-dontwarn sun.misc.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Shizuku API, Provider & AIDL UserService (instantiated via reflection in Shizuku server process)
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-keep class com.example.core.device.IJarvisShizukuService { *; }
-keep class com.example.core.device.IJarvisShizukuService$** { *; }
-keep class com.example.core.device.JarvisShizukuUserService {
    public <init>();
    *;
}
-keepclassmembers class com.example.core.device.AllowlistedCommandValidator { *; }

# Pure-Kotlin Wireless ADB (Kadb: SPAKE2 + TLS 1.3)
-keep class com.flyfishxu.kadb.** { *; }
-dontwarn com.flyfishxu.kadb.**

# On-device ML Kit Text Recognition (offline bundled Latin OCR)
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# Autonomous Phone-Use Vision, MCP & Web data contracts
-keep class com.example.core.vision.** { *; }
-keep class com.example.core.mcp.** { *; }
-keep class com.example.core.web.** { *; }


