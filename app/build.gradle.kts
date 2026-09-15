plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("app.cash.paparazzi") version "1.3.5"
}

import java.io.File

android {
    namespace = "com.scoreorbit.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.scoreorbit.android"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // Release signing reads the local keystore created for this app
    // (~/.local/share/score-orbit/, never committed). Fresh clones without
    // it simply build unsigned release APKs.
    val scoreOrbitHome = File(System.getProperty("user.home"), ".local/share/score-orbit")
    val releaseKeystore = File(scoreOrbitHome, "score-orbit-release.jks")
    signingConfigs {
        create("release") {
            storeFile = releaseKeystore
            val pw = System.getenv("SCORE_ORBIT_KEY_PASSWORD")
                ?: runCatching { File(scoreOrbitHome, "keystore-password.txt").readText().trim() }.getOrNull()
            storePassword = pw
            keyAlias = "scoreorbit"
            keyPassword = pw
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystore.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.animation:animation")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.14.2")
}
