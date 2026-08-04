# Kotlinx Serialization
-keepattributes *Annotation*
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}
-keep class kotlinx.serialization.json.** { *; }

# Ktor
-keep class io.ktor.** { *; }
-dontwarn java.lang.management.**
-dontwarn io.ktor.util.debug.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }

# RevenueCat
-keep class com.revenuecat.purchases.** { *; }

# Coil
-keep class coil3.** { *; }

# Koin
-keep class org.koin.** { *; }
