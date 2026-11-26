import java.util.Properties

plugins {
    id("com.android.application") version "8.7.3"
    kotlin("android") version "2.1.0"
    kotlin("plugin.compose") version "2.1.0"
}

val releaseProps = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }

android {
    namespace = "com.wetune"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.wetune"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "1.2.1"
    }
    // release key lives outside the project (~/.wetune); its path and password are in local.properties (not committed)
    signingConfigs {
        create("release") {
            releaseProps.getProperty("release.store")?.let { storeFile = rootProject.file(it) }
            storePassword = releaseProps.getProperty("release.password"); keyAlias = "wetune"; keyPassword = releaseProps.getProperty("release.password")
        }
    }
    buildTypes {
        release { signingConfig = signingConfigs.getByName("release") }
        // release code (not debuggable, so it runs compiled) signed with the debug key: installs over a debug build to measure jank
        create("perf") { initWith(getByName("release")); signingConfig = signingConfigs.getByName("debug"); matchingFallbacks += "release" }
    }
    buildFeatures { compose = true }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true // NewPipeExtractor uses Java 11+ APIs
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-splashscreen:1.0.1") // the launch screen on Android 8-11 too, and its exit animation
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1") // Apple Music's animated covers are HLS streams
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("dev.chrisbanes.haze:haze:1.2.2")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1") // installs Compose's baseline profiles: less jank on first runs
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
