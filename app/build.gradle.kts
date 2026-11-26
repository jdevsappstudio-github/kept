import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutlibraries.android)
}

// google-services.json is never committed (H8). With it, Firebase is configured at build time;
// without it the app still builds and runs, and says plainly that AI is not set up.
val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// Release signing from keystore.properties (gitignored). Absent = unsigned release, still builds.
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseSigning = keystoreProperties.getProperty("storeFile") != null

// versionCode = MAJOR*10000 + MINOR*100 + PATCH.
val appVersionName = "0.1.1"
val appVersionCode = appVersionName.split(".").map { it.toInt() }.let { (a, b, c) -> a * 10000 + b * 100 + c }

android {
    namespace = "com.jdevsappstudio.kept"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jdevsappstudio.kept"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("boolean", "HAS_FIREBASE", "$hasFirebaseConfig")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Same applicationId as release so one Firebase Android app covers both (App Check
            // debug provider in debug, Play Integrity in release).
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// MigrationTestHelper reads the exported schemas from the test APK's assets.
android.sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process) // app foreground state: Nano is foreground-only
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.workmanager)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)

    // All AI goes through Firebase AI Logic (hackathon rule, H3).
    implementation(libs.firebase.ai)
    implementation(libs.firebase.ai.ondevice)
    ksp(libs.firebase.ai.ksp) // @Generable card schema: structured output on device
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.config) // read model + effort, swappable without a release
    debugImplementation(libs.firebase.appcheck.debug)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    // SQL tests on a real phone (install -r + am instrument; never connectedAndroidTest, it uninstalls the app).
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.assertk)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
