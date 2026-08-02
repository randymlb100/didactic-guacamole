// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("com.android.test") version "9.2.1" apply false
    id("androidx.baselineprofile") version "1.5.0-alpha06" apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
    id("java") // This allows the 'java' block below to work
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.named<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
