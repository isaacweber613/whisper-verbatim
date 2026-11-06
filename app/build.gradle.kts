import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Upload-key details live in keystore.properties, which is never committed.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.kafkasl.phonewhisper"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.isaacweber.wordforword"
        minSdk = 30
        targetSdk = 36
        versionCode = 18
        versionName = "0.7.1"

        ndk { abiFilters += "arm64-v8a" }
    }

    buildFeatures { buildConfig = true }

    // Editions from the same code, released together. Two choices are combined:
    //   edition  regular: no keyboard shortcut, Android 11+. Code stays uncompressed in the APK,
    //                     so the app doesn't grow after installing.
    //            keypad:  adds the keyboard shortcut for phones with physical keys, Android 5.1+.
    //   updates  selfInstall: the app downloads and installs its own updates. Needs
    //                     REQUEST_INSTALL_PACKAGES, which makes Play Protect block it as harmful.
    //            browser: Update opens the APK in the browser; no install permission.
    // Released: regularSelfInstall, keypadSelfInstall, regularBrowser (see scripts/release.sh).
    flavorDimensions += listOf("edition", "updates")
    productFlavors {
        create("regular") {
            dimension = "edition"
            minSdk = 30
            buildConfigField("boolean", "KEYPAD", "false")
            buildConfigField("String", "APK_BASE", "\"WordForWord\"")
        }
        create("keypad") {
            dimension = "edition"
            minSdk = 22
            buildConfigField("boolean", "KEYPAD", "true")
            buildConfigField("String", "APK_BASE", "\"WordForWordKeypad\"")
        }
        create("selfInstall") {
            dimension = "updates"
            buildConfigField("String", "APK_SUFFIX", "\"\"")
        }
        create("browser") {
            dimension = "updates"
            buildConfigField("String", "APK_SUFFIX", "\"Lite\"")
        }
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("upload") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("upload")
            // Remove library code and resources the app never uses.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    @Suppress("DEPRECATION")
    kotlinOptions { jvmTarget = "17" }

    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.apache.commons:commons-compress:1.27.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
