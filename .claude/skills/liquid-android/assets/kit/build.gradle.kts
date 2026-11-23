// The Liquid kit as an Android library module: settings registry and store, the WM-Keyboard-style settings UI,
// liquid glass, motion (springs, presence, press squash, stagger) and diagnostics (debug log, notices, frame meter,
// changelog). Copy this folder into a new project as `kit`, add `include(":kit")`, and depend on it.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "liquid.kit"
    compileSdk = 36
    defaultConfig { minSdk = 33 } // RuntimeShader (the real glass) needs Android 13
    buildFeatures { compose = true }
}

dependencies {
    // Pick current stable versions at least two weeks old (Liquid Launcher used compose 1.12.1, material3 1.4.0,
    // activity 1.13.0, datastore 1.3.0, backdrop 2.0.1, coroutines 1.11.0).
    implementation("androidx.compose.ui:ui:1.12.1")
    implementation("androidx.compose.foundation:foundation:1.12.1")
    implementation("androidx.compose.animation:animation:1.12.1")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.datastore:datastore-preferences:1.3.0")
    implementation("io.github.kyant0:backdrop:2.0.1") // Kyant0/AndroidLiquidGlass, Apache-2.0
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
