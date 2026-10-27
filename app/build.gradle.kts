plugins {
    id("com.android.application")
}

android {
    namespace = "dev.kaze.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.kaze.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
        versionName = "1." + (System.getenv("BUILD_NUMBER") ?: "0")
    }

    // The same key every build, so updates install over the old version.
    // CI writes the keystore from a repository secret; local builds fall back to the debug key.
    val ksFile = file(System.getenv("KAZE_KEYSTORE") ?: "kaze.jks")
    signingConfigs {
        if (ksFile.exists()) {
            create("kaze") {
                storeFile = ksFile
                storePassword = System.getenv("KAZE_KEYSTORE_PASSWORD")
                keyAlias = "kaze"
                keyPassword = System.getenv("KAZE_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = if (ksFile.exists()) signingConfigs.getByName("kaze") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
