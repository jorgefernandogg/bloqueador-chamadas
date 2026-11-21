plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val keystorePath: String? = System.getenv("KEYSTORE_PATH")

android {
    namespace = "br.com.jf.bloqueador"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.jf.bloqueador"
        minSdk = 29
        targetSdk = 35
        versionCode = runNumber
        versionName = "1.0.$runNumber"
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

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // Erros de segurança do Lint (ex.: componente exportado sem proteção) quebram o build.
        error += listOf("ExportedService", "ExportedReceiver", "HardcodedDebugMode", "AllowBackup")
        // App pessoal sem tradução e com UI montada em código.
        disable += listOf("SetTextI18n", "HardcodedText", "UseSwitchCompatOrMaterialCode", "OldTargetApi", "GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
