package dev.liquid.launcher.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.data.Looks
import dev.liquid.launcher.core.settings.SettingsStore

/**
 * Looks (0.8.0, D110): the glass, the buttons and the effects in one tap, shown at the top of
 * Liquid glass and Effects and touch. The options below stay free to change afterwards; the
 * chip of the look in use stays lit only while nothing was changed.
 */
@Composable
internal fun LooksEditor(store: SettingsStore) {
    val values by store.values.collectAsStateCompat()
    val current = remember(values) { Looks.current(store) }
    InfoCard {
        Text("Looks", style = MaterialTheme.typography.titleMedium)
        BodyText(
            (current?.about ?: "Your own mix.") + " A look sets the glass, the buttons and the effects together; colours, fonts and the wallpaper stay.",
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (preset in Looks.presets) {
                dev.liquid.launcher.core.designsystem.KitChip(
                    selected = preset == current,
                    onClick = { Looks.apply(store, preset) },
                    label = { Text(preset.name) },
                )
            }
        }
    }
}
