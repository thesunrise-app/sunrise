plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val run = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "app.sunrise.focus"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.sunrise.focus"
        minSdk = 26
        targetSdk = 34
        versionCode = run
        versionName = "1.0.$run"
    }

    // Même clé à chaque compilation, pour que les mises à jour s'installent par-dessus.
    signingConfigs {
        create("sunrise") {
            storeFile = file("sunrise.keystore")
            storePassword = "sunrise-app"
            keyAlias = "sunrise"
            keyPassword = "sunrise-app"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sunrise")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.webkit:webkit:1.11.0")
}
