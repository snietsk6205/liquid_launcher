plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.liquid.launcher.core.data"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:settings"))
    api(libs.kotlinx.serialization.json)
    api(libs.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
