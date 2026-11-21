plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.liquid.launcher"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.liquid.launcher"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // CI passes the run number so every build installs over the previous one.
        versionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: 1
        versionName = providers.gradleProperty("app.versionName").get()
    }

    signingConfigs {
        // A dedicated key kept in this private repository so every build has the same
        // signature and updates install over each other. See DECISIONS.md, D19.
        create("shared") {
            storeFile = rootProject.file("keystore/liquid-launcher.jks")
            storePassword = "liquid-launcher"
            keyAlias = "liquid"
            keyPassword = "liquid-launcher"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            signingConfig = signingConfigs.getByName("shared")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.version",
                "META-INF/**/LICENSE.txt",
                "kotlin-tooling-metadata.json",
                "DebugProbesKt.bin",
            )
        }
    }

    lint {
        // The app deliberately targets the phone's own API level (DECISIONS.md, D16).
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:settings"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":feature:redirect"))
    implementation(project(":feature:home"))
    implementation(project(":feature:settings"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
