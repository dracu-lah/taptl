import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing lives outside the repo: ~/.android/taptl-keystore.properties
// (storeFile, storePassword, keyAlias, keyPassword). Without it, release builds are unsigned.
val keystoreProps = File(System.getProperty("user.home"), ".android/taptl-keystore.properties")
    .takeIf { it.exists() }
    ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }

android {
    namespace = "dev.dracu.taptl"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.dracu.taptl"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        // ML Kit ships big native libs; the target phones are all arm64.
        ndk { abiFilters += "arm64-v8a" }
    }
    // The app's own text is English only; drop the libraries' strings for other locales.
    androidResources {
        localeFilters += "en"
    }

    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    // Native libs (~27 MB, mostly ML Kit) and dex are stored raw by default. Compressed, the APK is
    // about half the size; they get unpacked once at install instead of mapped from the APK.
    packaging {
        jniLibs { useLegacyPackaging = true }
        dex { useLegacyPackaging = true }
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.mlkit.text.latin)
    implementation(libs.mlkit.text.chinese)
    implementation(libs.mlkit.text.japanese)
    implementation(libs.mlkit.text.korean)
    implementation(libs.mlkit.text.devanagari)
    implementation(libs.mlkit.translate)
}
