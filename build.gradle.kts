plugins {
    id("com.android.application") version "9.4.0" apply false
    id("com.google.devtools.ksp") version "2.3.6" apply false
    // Compose compiler: must match the Kotlin version built into AGP (2.2.10 for AGP 9.4).
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    // Screenshot tests on the JVM (Robolectric): ./gradlew verifyRoborazziDebug
    id("io.github.takahirom.roborazzi") version "1.77.0" apply false
}
