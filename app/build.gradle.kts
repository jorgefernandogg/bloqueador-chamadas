plugins {
    id("com.android.application")
}

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val keystorePath: String? = System.getenv("KEYSTORE_PATH")

android {
    namespace = "br.com.jf.bloqueador"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.com.jf.bloqueador"
        minSdk = 29
        targetSdk = 36
        versionCode = runNumber
        versionName = "2.0.$runNumber"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystorePath != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // Erros de segurança do Lint quebram o build.
        error += listOf("ExportedService", "ExportedReceiver", "HardcodedDebugMode", "AllowBackup")
        // App pessoal sem tradução e com UI montada em código.
        disable += listOf(
            "SetTextI18n", "HardcodedText", "UseSwitchCompatOrMaterialCode",
            "OldTargetApi", "GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable"
        )
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
