plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rork.recto"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rork.recto"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // Config comes from an environment variable (how Rork/CI inject it), or
        // failing that a Gradle property. The Gradle-property fallback exists
        // because Android Studio is usually launched from a desktop launcher and
        // so does not inherit shell exports — put the values in
        // ~/.gradle/gradle.properties (outside this repo, so they can never be
        // committed) and local builds pick them up. See docs/LOCAL_SETUP.md.
        // Both providers are configuration-cache safe; reading a file here would
        // not be, and this project has the configuration cache enabled.
        fun escapedConfigValue(name: String): String = providers.environmentVariable(name)
            .orElse(providers.gradleProperty(name))
            .orElse("")
            .get()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")

        buildConfigField("String", "REVENUECAT_API_KEY", "\"${escapedConfigValue("EXPO_PUBLIC_REVENUECAT_ANDROID_API_KEY")}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${escapedConfigValue("EXPO_PUBLIC_SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${escapedConfigValue("EXPO_PUBLIC_SUPABASE_ANON_KEY")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.revenuecat.purchases)
    debugImplementation(libs.androidx.ui.tooling)
}
