import java.util.Properties

plugins {
    id("com.android.application")
    id("base")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        f.inputStream().use { load(it) }
    }
}

val artifactBaseName = "lotterynet-kotlin-v1.0.13-kotlin"
val sentryDsn = localProps.getProperty("sentry.dsn")
    ?: System.getenv("SENTRY_DSN")
    ?: ""
val sentryEnvironment = localProps.getProperty("sentry.environment")
    ?: System.getenv("SENTRY_ENVIRONMENT")
    ?: "production"
val sentryTracesSampleRate = localProps.getProperty("sentry.traces.sample-rate")
    ?: System.getenv("SENTRY_TRACES_SAMPLE_RATE")
    ?: "0.2"
val sentryLogsEnabled = localProps.getProperty("sentry.logs.enabled")
    ?: System.getenv("SENTRY_LOGS_ENABLED")
    ?: "true"

fun asBuildConfigString(value: String): String {
    return "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"") + "\""
}

base {
    archivesName.set(artifactBaseName)
}

android {
    namespace = "com.lotterynet.pro"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.lotterynet.pro"
        minSdk = 24
        targetSdk = 36
        versionCode = 14
        versionName = "1.0.13-kotlin"
        manifestPlaceholders += mapOf(
            "sentryDsn" to sentryDsn,
            "sentryTracesSampleRate" to sentryTracesSampleRate,
            "sentryLogsEnabled" to sentryLogsEnabled,
            "sentryEnvironment" to sentryEnvironment,
        )
        buildConfigField("String", "SENTRY_DSN", asBuildConfigString(sentryDsn))
        buildConfigField("String", "SENTRY_ENVIRONMENT", asBuildConfigString(sentryEnvironment))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = localProps.getProperty("release.storeFile")
            val storePasswordValue = localProps.getProperty("release.storePassword")
            val keyAliasValue = localProps.getProperty("release.keyAlias")
            val keyPasswordValue = localProps.getProperty("release.keyPassword")

            if (!storeFilePath.isNullOrBlank()) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.webkit:webkit:1.15.0")
    implementation("androidx.print:print:1.1.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(platform("io.github.jan-tennert.supabase:bom:3.6.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.ktor:ktor-client-okhttp:3.4.3")
    implementation("io.ktor:ktor-client-websockets:3.4.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.caverock:androidsvg:1.4")
    implementation("io.sentry:sentry-android:8.34.0")
    implementation("androidx.security:security-crypto:1.0.0")

    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material3.adaptive:adaptive:1.2.0")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.2.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.2.0")
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
