plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.liquid.launcher.core.common"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
