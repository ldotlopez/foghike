// AGP 9 compiles Kotlin itself (built-in Kotlin), so no Kotlin plugin is applied here.
plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "com.cuarentaydos.foghike"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cuarentaydos.foghike"
        minSdk = 31 // Android 12: old phones are used as hiking GPS units
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    buildFeatures {
        compose = true
    }

    // Robolectric needs the app's resources (strings, drawables) to render screens in JVM tests.
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric's Android 16 (SDK 36) setup touches java.io internals, closed by default since JDK 17.
        unitTests.all { it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED") }
    }

    // The release key never lives in the repo: CI (and local release builds) pass it via env vars.
    signingConfigs {
        create("release") {
            System.getenv("SIGNING_STORE_FILE")?.let { storeFile = file(it) }
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        // Own application id, so a debug build installs next to a release one (signed with another key)
        // instead of failing with a signature mismatch. Its name comes from src/debug/res.
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            if (System.getenv("SIGNING_STORE_FILE") != null) signingConfig = signingConfigs.getByName("release")
            // Only ARM phones are targeted; drops MapLibre's x86 native libraries from the APK.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
    }
}

// Room writes the schema of each version here; needed for real migrations later.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Reference screenshots live in the repo, next to the tests that render them.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    implementation("androidx.activity:activity-compose:1.10.1")
    // The BOM picks matching versions for every Compose library below.
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview") // @Preview
    debugImplementation("androidx.compose.ui:ui-tooling") // renders previews in Android Studio
    debugImplementation("androidx.compose.ui:ui-test-manifest") // empty activity that screenshot tests render into
    // Not used directly; pins the version that older transitive copies would otherwise give lint (release build fails).
    implementation("androidx.fragment:fragment:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.maplibre.gl:android-sdk:11.13.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("net.sf.kxml:kxml2:2.3.0") // XmlPullParser implementation for GPX parser tests on the JVM
    testImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.77.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.77.0")
}
