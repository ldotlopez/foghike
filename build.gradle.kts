plugins {
    id("com.android.application") version "9.4.0" apply false
    id("com.google.devtools.ksp") version "2.3.6" apply false
    // Compose compiler: must match the Kotlin version built into AGP (2.2.10 for AGP 9.4).
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    // Screenshot tests on the JVM (Robolectric): ./gradlew verifyRoborazziDebug
    id("io.github.takahirom.roborazzi") version "1.77.0" apply false
    // Kotlin lint/format: ./gradlew spotlessCheck / spotlessApply (ktlint rules, settings in .editorconfig)
    id("com.diffplug.spotless") version "7.2.1"
}

// Spotless lists the files itself, so it works with AGP's built-in Kotlin (no Kotlin plugin to hook into).
spotless {
    kotlin {
        target("app/src/**/*.kt")
        ktlint("1.7.1")
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint("1.7.1")
    }
}
