import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
}

// Handle tink dependency conflicts for UnifiedPush 3.0.10
configurations.all {
    val tink = "com.google.crypto.tink:tink:1.20.0"
    resolutionStrategy {
        force(tink)
        dependencySubstitution {
            substitute(module("com.google.crypto.tink:tink-android")).using(module(tink))
        }
    }
}

android {
    namespace = "to.us.charlesst.discord"
    compileSdk = 36

    defaultConfig {
        applicationId = "to.us.charlesst.discord"
        minSdk = 31
        targetSdk = 36
        versionCode = 110
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Signing config - reads from environment variables or keystore.properties
    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
                storeFile = file(keystoreProperties["storeFile"].toString())
                storePassword = keystoreProperties["storePassword"].toString()
                keyAlias = keystoreProperties["keyAlias"].toString()
                keyPassword = keystoreProperties["keyPassword"].toString()
            } else {
                // Fall back to environment variables (for CI/CD)
                storeFile = file(System.getenv("KEYSTORE_FILE") ?: "keystore.jks")
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions += "variant"
    productFlavors {
        create("client") {
            dimension = "variant"
            applicationIdSuffix = ""
            resValue("string", "app_name", "Discord")
            buildConfigField("boolean", "IS_PROXY_BUILD", "false")
        }
        create("proxy") {
            dimension = "variant"
            applicationIdSuffix = ".proxy"
            resValue("string", "app_name", "Discord Proxy")
            buildConfigField("boolean", "IS_PROXY_BUILD", "true")
        }
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
            freeCompilerArgs.addAll(
                "-Xopt-in=kotlin.RequiresOptIn",
                "-Xopt-in=android.annotation.RequiresApi"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.unifiedpush.connector)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.tink.apps.webpush)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}