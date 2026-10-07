# =============================================================================
# Norilsk Transit — R8/ProGuard rules for v1.2.7
# =============================================================================

# --- General Android / Kotlin ------------------------------------------------
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Keep generic signatures for Retrofit, Moshi, kotlinx-serialization
-keepattributes Signature

# --- Kotlin coroutines -------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# --- Kotlinx Serialization (DTO) ----------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep companion serializer() of @Serializable classes
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep INSTANCE.serializer() of serializable objects
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep generated serializers for our DTOs
-keep,includedescriptorclasses class com.example.**$$serializer { *; }
-keepclassmembers class com.example.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Moshi -------------------------------------------------------------------
-dontwarn org.jetbrains.annotations.**
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keep @com.squareup.moshi.JsonQualifier @interface *

# Generated Moshi adapters (codegen)
-keep class **JsonAdapter { *; }
-keepnames @com.squareup.moshi.JsonClass class *
-keepclassmembers @com.squareup.moshi.JsonClass class * {
    <init>(...);
    <fields>;
}

# --- Retrofit / OkHttp -------------------------------------------------------
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# --- Room --------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# --- Firebase ----------------------------------------------------------------
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
-keep class com.example.push.NorilskFirebaseService { *; }
-keep class com.google.firebase.crashlytics.** { *; }
-keepattributes Signature, *Annotation*

# Firebase Init: don't strip google-services init
-keep class com.google.gms.google.services.** { *; }

# --- Yandex MapKit (native bindings) -----------------------------------------
-keep class com.yandex.mapkit.** { *; }
-keep class com.yandex.runtime.** { *; }
-dontwarn com.yandex.mapkit.**
-dontwarn com.yandex.runtime.**
-keepclassmembers class com.yandex.mapkit.** { native <methods>; }

# --- OkHttp logging interceptor ---------------------------------------------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Compose -----------------------------------------------------------------
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# --- CameraX (reflection-based) ----------------------------------------------
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# --- Coil image loader -------------------------------------------------------
-keep class coil.** { *; }
-dontwarn coil.**

# --- DataStore Preferences --------------------------------------------------
-keep class androidx.datastore.preferences.** { *; }

# --- Coroutines ----------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# --- Navigation Compose -----------------------------------------------------
-keepnames class * extends androidx.navigation.NavType

# --- Accompanist Permissions -------------------------------------------------
-keep class com.google.accompanist.permissions.** { *; }
-dontwarn com.google.accompanist.permissions.**

# --- Application / entry points (must not be obfuscated) -------------------
-keep class com.example.NorilskTransportApp { *; }
-keep class com.example.MainActivity { *; }
-keep class com.example.service.StopAlarmService { *; }
-keep class com.example.push.NorilskFirebaseService { *; }

# --- Models / DTOs / Repositories (reflection-based serialization) --------
-keep class com.example.data.** { *; }
-keep class com.example.data.local.dto.** { *; }
-keep class com.example.data.remote.** { *; }
-keep class com.example.data.local.entity.** { *; }

# --- kotlinx-serialization on our data classes ------------------------------
-keepclasseswithmembers class com.example.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Service classes that may be referenced from manifest ------------------
-keep class com.example.service.** { *; }
-keep class com.example.push.** { *; }
-keep class com.example.receiver.** { *; }

# --- Kotlin metadata ---------------------------------------------------------
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# --- Misc / third-party ------------------------------------------------------
-dontwarn java.lang.invoke.**
-dontwarn **$$serializer
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**

# Strip log lines for release (optional, saves ~5% APK size)
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}
