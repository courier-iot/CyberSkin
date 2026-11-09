plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The release keystore is supplied by GitHub Actions secrets (see build-apk.yml).
// Without it (e.g. a local build or a fork), release builds fall back to the debug key.
val releaseKeystore: String? = System.getenv("CYBERSKINOS_KEYSTORE")

android {
    namespace = "com.stellarite.cyberskinos"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.stellarite.cyberskinos"
        minSdk = 26
        targetSdk = 34
        // Bump BOTH before every release. versionCode must always go up,
        // or Android refuses to install the update over the old version.
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            if (releaseKeystore != null) {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("CYBERSKINOS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CYBERSKINOS_KEY_ALIAS")
                keyPassword = System.getenv("CYBERSKINOS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (releaseKeystore != null)
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.13.1")
}
