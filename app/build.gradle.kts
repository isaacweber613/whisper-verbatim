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
        minSdk = 23
        targetSdk = 36
        versionCode = 30
        versionName = "0.8.3"

        ndk { abiFilters += "arm64-v8a" }
    }

    buildFeatures { buildConfig = true }

    // Anonymous usage counts (Analytics.kt). A PostHog project key is public by design: it can
    // only send events, not read them. Empty turns analytics off.
    defaultConfig {
        buildConfigField("String", "POSTHOG_KEY", "\"phc_BjYGs7MHnDDkKMYWY7Cod5AiHWMLubtbEoC3XCrg5sjo\"")
        buildConfigField("String", "POSTHOG_HOST", "\"https://us.i.posthog.com\"")
    }

    // Three builds from the same code. All have the keyboard shortcut and run on Android 6+.
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
            buildConfigField("String", "SERVICE_URL", "\"\"")
        }
        create("selfInstall") {
            dimension = "updates"
            buildConfigField("String", "APK_NAME", "\"WordForWordSelfUpdate.apk\"")
            buildConfigField("boolean", "UPDATES", "true")
            buildConfigField("String", "SERVICE_URL", "\"\"")
        }
        create("play") {
            dimension = "updates"
            buildConfigField("String", "APK_NAME", "\"\"")
            buildConfigField("boolean", "UPDATES", "false")
            // Free minutes through Word for Word's server (server/, FreeMinutes.kt).
            buildConfigField("String", "SERVICE_URL", "\"https://word-for-word.ynbsd6242.workers.dev\"")
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
    implementation("com.posthog:posthog-android:3.76.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
