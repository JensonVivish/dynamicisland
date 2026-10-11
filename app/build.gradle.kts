
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.digiforgedynamics.island"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.digiforgedynamics.island"
        minSdk = 29
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"
    }

    // Local keystore or CI-provided environment variables.
    val ksFile = rootProject.file("keystore/digiisland.jks")
    val hasLocalKs = ksFile.exists()

    val ksPass = System.getenv("KEYSTORE_PASSWORD")
        ?: if (hasLocalKs) "digiisland" else "android"

    val alias = System.getenv("KEY_ALIAS") ?: "digiisland"

    val keyPass = System.getenv("KEY_PASSWORD")
        ?: if (hasLocalKs) "digiisland" else "android"

    val ciKeystorePath = System.getenv("KEYSTORE_PATH")

    signingConfigs {
        create("release") {
            storeFile = if (!ciKeystorePath.isNullOrBlank()) {
                rootProject.file(ciKeystorePath)
            } else {
                ksFile
            }

            storePassword = ksPass
            keyAlias = alias
            keyPassword = keyPass
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            signingConfig = signingConfigs.getByName("release")
        }

        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
