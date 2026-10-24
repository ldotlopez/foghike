// AGP 9 compiles Kotlin itself (built-in Kotlin), so no Kotlin plugin is applied here.
plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
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
}

// Room writes the schema of each version here; needed for real migrations later.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.10.1")
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
}
