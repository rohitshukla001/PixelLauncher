import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.rohitshukla.pixellauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.rohitshukla.pixellauncher"
        minSdk = 26
        targetSdk = 35
        // Bump for every APK handed out for testing, so the tester can tell builds apart (shown in Settings).
        versionCode = 10
        versionName = "0.1.9"
    }

    // Release signing reads keystore.properties at the project root (kept out of git):
    // storeFile, storePassword, keyAlias, keyPassword. Without it, release builds come out unsigned.
    val keystore = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
        Properties().apply { file.inputStream().use(::load) }
    }
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    // The language is switched inside the app, so every language must be in the download.
    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Copies the latest debug APK to apk/PixelLauncher.apk after every debug build, for manual testing
// on a real phone (same convention as PixelScroll).
val exportApk by tasks.registering(Copy::class) {
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(rootProject.layout.projectDirectory.dir("apk"))
    rename { "PixelLauncher.apk" }
}
tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(exportApk)
}
