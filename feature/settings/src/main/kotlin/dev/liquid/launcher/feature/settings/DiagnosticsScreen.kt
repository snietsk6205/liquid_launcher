package dev.liquid.launcher.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.settings.SettingsStore

@Composable
internal fun DiagnosticsScreen(store: SettingsStore, open: (Screen) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val items by DiagnosticsState.items
    val glass by DiagnosticsState.glass

    // Refresh whenever the screen opens, including on the way back from the benchmark.
    LaunchedEffect(Unit) {
        DiagnosticsState.items.value = Diagnostics.run(context, store)
    }

    ScreenScaffold(title = "Diagnostics", onBack = onBack) {
        InfoCard {
            BodyText(
                "These checks confirm that this phone behaves the way the launcher's design assumes. " +
                    "Run the glass benchmark, then share the report.",
            )
            Row(Modifier.padding(top = 10.dp)) {
                Button(onClick = { open(Screen.GlassBench) }) {
                    Text(if (glass.isEmpty()) "Run glass benchmark" else "Run benchmark again")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    val fresh = Diagnostics.run(context, store)
                    DiagnosticsState.items.value = fresh
                    shareText(context, "Liquid Launcher diagnostics", Diagnostics.report(context, fresh))
                }) { Text("Share report") }
            }
        }

        items.forEach { item ->
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                val (icon, tint) = when (item.status) {
                    DiagStatus.OK -> LauncherIcons.CheckCircle to Color(0xFF2E9E5B)
                    DiagStatus.WARN -> LauncherIcons.Error to Color(0xFFD08A00)
                    DiagStatus.FAIL -> LauncherIcons.Error to MaterialTheme.colorScheme.error
                    DiagStatus.INFO -> LauncherIcons.Info to MaterialTheme.colorScheme.onSurfaceVariant
                }
                LauncherIcon(icon, contentDescription = item.status.mark, tint = tint)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(item.title, style = MaterialTheme.typography.bodyLarge)
                    BodyText(item.detail)
                }
            }
        }
    }
}
