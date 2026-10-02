import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Release signing comes from a `.env` at the repo root (written by the release workflow from
 * repository secrets, or by hand locally). Without one, release builds are simply unsigned.
 */
val envProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file(".env")).asText.orNull
        ?.let { load(it.reader()) }
}
val hasReleaseKeystore = !envProperties.getProperty("KEYSTORE_PATH").isNullOrBlank()

/** Groq's key is built in: from `.env` locally, from the `GROQ_API_KEY` secret in CI. */
val groqApiKey = envProperties.getProperty("GROQ_API_KEY")
    ?: providers.environmentVariable("GROQ_API_KEY").orNull
    ?: ""

android {
    namespace = "bassamalim.halala"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "bassamalim.halala"
        // Android 10: modern biometrics and scoped storage (see the spec).
        minSdk = 29
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"

        buildConfigField("String", "GROQ_API_KEY", "\"$groqApiKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = file(envProperties.getProperty("KEYSTORE_PATH"))
                storePassword = envProperties.getProperty("KEYSTORE_PASSWORD", "")
                keyAlias = envProperties.getProperty("KEY_ALIAS", "")
                keyPassword = envProperties.getProperty("KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        debug {
            // The owner's own key, so debug builds from here, from CI and the releases all
            // install over each other. Without a `.env`, the machine's debug key as usual.
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
        }
        release {
            optimization {
                enable = false
            }
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else null
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // Settings shows the version number; Groq's key.
        buildConfig = true
    }
    sourceSets {
        // MigrationTestHelper reads the exported schemas as assets, and Robolectric only sees
        // the tested variant's (a test source set's are never merged). Debug builds only.
        getByName("debug").assets.directories.add("$projectDir/schemas")
    }
    testOptions {
        unitTests {
            // Repository tests run Room on Robolectric.
            isIncludeAndroidResources = true
            // Robolectric's JVM otherwise takes a quarter of RAM, which beside the IDE and the
            // Gradle daemons is enough to run the machine out of memory.
            all { it.maxHeapSize = "1g"; it.maxParallelForks = 1 }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    // Schemas are checked in so Room migrations can be written against them.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.android.compiler)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.biometric)
    // The home-screen widget.
    implementation(libs.androidx.glance.appwidget)
    // Argon2id for the encrypted backup's passphrase (nothing else of it is used).
    implementation(libs.bouncycastle.prov)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
