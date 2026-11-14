package dev.liquid.launcher.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (build ${info.longVersionCode})"
        } catch (e: Exception) {
            "unknown"
        }
    }
    ScreenScaffold(title = "About", onBack = onBack) {
        InfoCard {
            Text("Liquid Launcher", style = MaterialTheme.typography.titleMedium)
            BodyText("Version $version\nPhase 2: customisation (home setup, look, touch and motion)", Modifier.padding(top = 4.dp))
        }
        SectionHeader("Privacy")
        BodyText(
            "The launcher collects nothing. Its only network use is checking GitHub for a new build and downloading it, when you ask " +
                "(or once a day if you switch that on in Updates). The accessibility service only receives events from the system launcher.",
            Modifier.padding(horizontal = 20.dp),
        )
        SectionHeader("Credits")
        BodyText(
            "Liquid glass effects: Backdrop by Kyant0 (Apache License 2.0)\n" +
                "Reference for glass surfaces and motion: Artefact by Chethan616 (MIT / Apache 2.0)\n" +
                "Reference for the settings architecture: WM Keyboard by wasi-master (MIT)\n" +
                "Icons: Material Symbols by Google (Apache License 2.0)\n" +
                "Jetpack Compose and AndroidX by Google (Apache License 2.0)",
            Modifier.padding(horizontal = 20.dp),
        )
    }
}
