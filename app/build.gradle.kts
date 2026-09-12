plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.iptvtv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.iptvtv"
        minSdk = 23              // covers older Android TV boxes and Fire TV sticks
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // Pinned in-tree so every builder signs with the same key. The app used to take
    // whichever ~/.android/debug.keystore the build container happened to have, and
    // when that container went away so did the key — the phone then refused every
    // update until the app, and its favourites, were uninstalled.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        release { isMinifyEnabled = false }
        // Debug-signed but R8-minified: ~3x smaller APK for slow-network installs (TV stick).
        create("slim") {
            initWith(getByName("debug"))
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "debug"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")

    // Playback: HLS (.m3u8), MPEG-TS, DASH, progressive
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // Channel logos
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Casting to a Chromecast / Google TV, the way Plex and YouTube do it.
    // Pulls androidx.mediarouter in transitively — that is what does discovery.
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
