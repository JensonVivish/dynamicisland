import java.util.Properties
import java.io.FileInputStream

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

    // Load keystore from local file if it exists (for local builds),
    // otherwise CI injects it via environment variables (see workflow).
    val ksFile = rootProject.file("keystore/digiisland.jks")
    val hasLocalKs = ksFile.exists()
    val ksPass = System.getenv("KEYSTORE_PASSWORD") ?: if (hasLocalKs) "digiisland" else "android"
    val keyAlias = System.getenv("KEY_ALIAS") ?: "digiisland"
    val keyPass = System.getenv("KEY_PASSWORD") ?: if (hasLocalKs) "digiisland" else "android"

val releaseKeyAlias = System.getenv("KEY_ALIAS")
val releaseKeyPassword = System.getenv("KEY_PASSWORD")
val releaseKeystorePassword = System.getenv("KEYSTORE_PASSWORD")
val releaseKeystorePath = System.getenv("KEYSTORE_PATH")

android {
    signingConfigs {
        create("release") {
            storeFile = file(releaseKeystorePath!!)
            storePassword = releaseKeystorePassword!!
            keyAlias = releaseKeyAlias!!
            keyPassword = releaseKeyPassword!!
        }
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
