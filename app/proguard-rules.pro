# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# proguard-rules.pro

# WorkManager rules - IMPORTANT: Keep these rules!
-keep class androidx.work.** { *; }
-keep interface androidx.work.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }

# Keep Multidex classes
-keep class androidx.multidex.** { *; }

# Keep annotations needed by WorkManager / Kotlinx Serialization if any (less common with R8)
-keepnames @interface org.jetbrains.annotations.Nullable
-keepnames @interface org.jetbrains.annotations.NotNull
-keep @kotlinx.serialization.InternalSerializationApi class * { *; }

# General rules for AndroidX/Kotlin if not already present
-keep class * extends androidx.fragment.app.Fragment { *; }
-keep class * extends androidx.activity.ComponentActivity { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.savedstate.SavedStateRegistry$SavedStateProvider { *; }

# Optional: suppress warnings for common libraries if they appear
-dontwarn okio.**
-dontwarn com.google.protobuf.**
-dontwarn java.nio.file.** # For API < 26 where Files.readAllLines is used implicitly (though minSdk 21 means this is less relevant)