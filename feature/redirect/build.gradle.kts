plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.liquid.launcher.feature.redirect"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:settings"))
    testImplementation(libs.junit)
}
