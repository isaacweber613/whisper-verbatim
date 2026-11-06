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
        minSdk = 22
        targetSdk = 36
        versionCode = 23
        versionName = "0.7.6"

        ndk { abiFilters += "arm64-v8a" }
    }

    buildFeatures { buildConfig = true }

    // Three builds from the same code. All have the keyboard shortcut and run on Android 5.1+.
    // They differ only in how updates arrive:
    //   browser:     WordForWord.apk on GitHub. Update opens the new APK in the browser.
    //   selfInstall: WordForWordSelfUpdate.apk on GitHub. The app downloads and installs its own
    //                updates. Needs REQUEST_INSTALL_PACKAGES, which makes Play Protect block it.
    //   play:        the Google Play app bundle. No update check at all: Play installs updates,
    //                and Play policy bans apps that update themselves any other way.
    flavorDimensions += "updates"
    productFlavors {
        create("browser") {
            dimension = "updates"
            buildConfigField("String", "APK_NAME", "\"WordForWord.apk\"")
            buildConfigField("boolean", "UPDATES", "true")
        }
        create("selfInstall") {
            dimension = "updates"
            buildConfigField("String", "APK_NAME", "\"WordForWordSelfUpdate.apk\"")
            buildConfigField("boolean", "UPDATES", "true")
        }
        create("play") {
            dimension = "updates"
            buildConfigField("String", "APK_NAME", "\"\"")
            buildConfigField("boolean", "UPDATES", "false")
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

    // Only the Play build uses the private upload key. The GitHub builds are signed with the
    // debug key by scripts/release.sh, because a different key can't install over earlier releases.
    productFlavors.getByName("play").signingConfig = signingConfigs.findByName("upload")

    buildTypes {
        release {
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
