import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.jupiterconnect"
    compileSdk = 35

    defaultConfig {
        // Keep the original package identity: Play Protect trusts this package
        // from earlier installs, while a brand-new package is flagged as an
        // unknown sideload even though the signing key is identical.
        applicationId = "com.jupiterconnect"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.1.4"
    }

    signingConfigs {
        // Local keystore is never committed; CI falls back to the debug key.
        val props = Properties().apply {
            val f = rootProject.file("keystore.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        if (props.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Sign debug with the same project key as release so an installed
        // Jupiter Connect can be upgraded in place. Without this, debug builds
        // use the shared Android Debug key and `adb install -r` (or a tapped
        // update) fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE.
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures { compose = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
