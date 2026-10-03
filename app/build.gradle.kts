plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)   // screenshot tests: every screen at phone, tablet and TV sizes
}

/*
 * Versions come from the release tag. CI passes -PreleaseVersion=1.2.3 for tag v1.2.3;
 * versionCode is derived from it (1.2.3 -> 10203), so every release installs over the
 * last. A local build is 0.0.0-dev / 1, below any release.
 */
val releaseVersion: String? = providers.gradleProperty("releaseVersion").orNull
val releaseVersionCode: Int = releaseVersion?.let { v ->
    val parts = v.split('.').map { it.toIntOrNull() ?: error("releaseVersion must look like 1.2.3, got $v") }
    require(parts.size == 3) { "releaseVersion must look like 1.2.3, got $v" }
    parts[0] * 10_000 + parts[1] * 100 + parts[2]
} ?: 1

/*
 * Release signing reads its key from the environment — the release workflow writes the
 * keystore from repository secrets — so the key never lives in the repository. Without
 * those variables a release build is left unsigned rather than signed with something else.
 */
val releaseKeystore: String? = System.getenv("RELEASE_KEYSTORE_PATH")

android {
    namespace = "com.iptvtv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.iptvtv"
        minSdk = 23              // covers older Android TV boxes and Fire TV sticks
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersion ?: "0.0.0-dev"
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
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
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
    testOptions.unitTests.isIncludeAndroidResources = true   // Robolectric-backed Room tests
    // A full-length tablet render is 2560x5200px; Roborazzi's conversion of it ran the
    // default test heap out of memory.
    testOptions.unitTests.all { it.maxHeapSize = "2g" }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")   // committed, so later migrations can be written against v1
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)   // type-safe navigation routes

    // Local source of truth: playlists are parsed into Room, screens read it as Flows.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Playback: HLS (.m3u8), MPEG-TS, DASH, progressive
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)

    // Channel logos
    implementation(libs.coil.compose)

    // Casting to a Chromecast / Google TV, the way Plex and YouTube do it.
    // Pulls androidx.mediarouter in transitively — that is what does discovery.
    implementation(libs.play.services.cast.framework)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.androidx.compose.ui.test.manifest)   // the host activity Roborazzi renders into
}
