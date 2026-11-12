package dev.liquid.launcher.feature.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.observe
import dev.liquid.launcher.feature.redirect.CalibrationState
import dev.liquid.launcher.feature.redirect.RedirectStatus

@Composable
internal fun CalibrationScreen(store: SettingsStore, onBack: () -> Unit) {
    val state by RedirectStatus.calibration.collectAsStateCompat()
    val connected by RedirectStatus.connected.collectAsStateCompat()
    val learned by store.observe(LauncherSettings.LearnedIds)
    val verified by store.observe(LauncherSettings.DetectorVerified)

    ScreenScaffold(title = "Calibrate recents", onBack = onBack) {
        InfoCard {
            Text(
                if (verified) "Recents detection is working" else "Recents has not been recognised yet",
                style = MaterialTheme.typography.titleMedium,
            )
            BodyText(
                "The launcher tells the home screen from recents by view ids inside the system launcher. " +
                    "If a system update renames them, calibration learns the new ones.\n\n" +
                    "1. Tap Start.\n" +
                    "2. Go to the home screen and wait for the message.\n" +
                    "3. Open recents and wait for the second message.\n\n" +
                    "The redirect is paused while this runs. It takes about ten seconds.",
                Modifier.padding(top = 6.dp),
            )
            Row(Modifier.padding(top = 10.dp)) {
                when (state) {
                    is CalibrationState.Running -> OutlinedButton(onClick = { RedirectStatus.cancelCalibration() }) {
                        Text("Cancel")
                    }

                    else -> Button(onClick = { RedirectStatus.startCalibration() }, enabled = connected) {
                        Text("Start")
                    }
                }
                if (learned.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { store.reset(LauncherSettings.LearnedIds) }) { Text("Forget learned ids") }
                }
            }
            if (!connected) BodyText("The redirect service is not running.", Modifier.padding(top = 8.dp))
        }

        when (val s = state) {
            CalibrationState.Idle -> Unit
            is CalibrationState.Running -> InfoCard {
                Text("Step ${s.step} of 2", style = MaterialTheme.typography.titleMedium)
                BodyText(s.message, Modifier.padding(top = 4.dp))
            }

            is CalibrationState.Done -> InfoCard {
                Text("Done", style = MaterialTheme.typography.titleMedium)
                BodyText(
                    "Learned ${s.ids.size} ids:\n" + s.ids.joinToString("\n") { it.substringAfter(":id/") },
                    Modifier.padding(top = 4.dp),
                )
            }

            is CalibrationState.Failed -> InfoCard(color = MaterialTheme.colorScheme.errorContainer) {
                Text("Failed", style = MaterialTheme.typography.titleMedium)
                BodyText(s.reason, Modifier.padding(top = 4.dp))
            }
        }

        if (learned.isNotEmpty()) {
            SectionHeader("Learned ids")
            BodyText(
                learned.joinToString("\n") { it.substringAfter(":id/") },
                Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}
