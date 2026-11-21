plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.github.mpridal.thermocamera"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.mpridal.thermocamera"
        minSdk = 26
        targetSdk = 37
        // Release builds get the version from the tag: -PversionName=1.3.0 (zadání kap. 9).
        val vName = (project.findProperty("versionName") as String?) ?: "0.1.0"
        val parts = vName.split('.').map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)
        versionName = vName
        versionCode = parts[0] * 10000 + parts[1] * 100 + parts[2]
        buildConfigField("String", "UPDATE_REPO", "\"mpridal/thermocamera\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // Phones and tablets (arm64; 32-bit ARM kept for low-end tablets – a 5 kB library).
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    ndkVersion = "28.2.13676358"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        // Keystore from environment (CI: GitHub Secrets) – never in the repository (zadání kap. 9).
        val ks = System.getenv("THERMOCAMERA_KEYSTORE")
        if (ks != null && file(ks).exists()) {
            create("release") {
                storeFile = file(ks)
                storePassword = System.getenv("THERMOCAMERA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("THERMOCAMERA_KEY_ALIAS")
                keyPassword = System.getenv("THERMOCAMERA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Debug builds also run on the x86_64 emulator.
            ndk { abiFilters += "x86_64" }
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        localeFilters += listOf("en", "cs", "es", "it", "fr", "de")
    }

    lint {
        error += listOf("MissingTranslation", "ExtraTranslation", "HardcodedText")
        warningsAsErrors = false
        abortOnError = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":thermal-core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.windowsize)
    implementation(libs.compose.material.icons.core)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
