# ============================================================
# Hilt
# ============================================================
# Hilt generates components at compile time; R8 must not
# remove generated classes referenced via reflection.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @javax.inject.Singleton class * { *; }
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel

# ============================================================
# Room
# ============================================================
# Room generates DAO implementations at compile time.
# Keep entity classes and their fields for schema inspection.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }

# ============================================================
# Kotlin Coroutines
# ============================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ============================================================
# Kotlin Serialization (not used in v1 but safe to include)
# ============================================================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# ============================================================
# Timber
# ============================================================
# Timber uses reflection to identify the calling class name.
-dontwarn org.jetbrains.**
-keep class timber.log.Timber { *; }

# ============================================================
# Jetpack Compose
# ============================================================
# Compose compiler generates classes; R8 must not rename them.
-keep class androidx.compose.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# ============================================================
# DataStore
# ============================================================
-keep class androidx.datastore.** { *; }

# ============================================================
# AndroidX Navigation
# ============================================================
-keep class androidx.navigation.** { *; }

# ============================================================
# General Android
# ============================================================
# Keep Parcelable implementations (used for MediaProjection Intent extras)
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep BuildConfig
-keep class com.audiolan.app.BuildConfig { *; }

# ============================================================
# VBAN / domain layer (pure Kotlin - no special rules needed)
# ============================================================
# VbanEncoder, VbanDecoder, VbanPacketizer are pure Kotlin objects.
# R8 may inline them but this is safe. No keep rules needed.
