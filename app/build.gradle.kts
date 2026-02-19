import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        load(file.inputStream())
    }
}

val sheetsApiKey: String =
    localProperties.getProperty("GOOGLE_SHEETS_API_KEY") ?: ""

// Release signing (Play Store upload key): set KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD in CI
val releaseKeystoreFile = System.getenv("KEYSTORE_FILE")

android {
    namespace = "com.lajthabalazs.doughdough"
    compileSdk = 35

    signingConfigs {
        if (releaseKeystoreFile != null) {
            create("release") {
                storeFile = file(releaseKeystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.lajthabalazs.doughdough"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // API key from gradle.properties or local.properties (optional - gviz fallback for gid=0)
        buildConfigField("String", "GOOGLE_SHEETS_API_KEY", "\"${sheetsApiKey.replace("\"", "\\\"")}\"")
    }

    buildTypes {
        release {
            if (releaseKeystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    packaging {
        resources {
            excludes += listOf("META-INF/INDEX.LIST", "META-INF/DEPENDENCIES")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.google.api.client.gson)
    implementation(libs.google.api.services.sheets)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}