package dev.liquid.launcher.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.data.SearchCards
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.observe

/** Settings → Search: which cards the search page shows with nothing typed, and in what order (0.9.3). */
@Composable
internal fun SearchCardsEditor(store: SettingsStore) {
    val raw by store.observe(LauncherSettings.SearchCards)
    val shown = remember(raw) { SearchCards.shown(raw) }
    val hidden = SearchCards.all.filter { it.id !in shown }

    fun save(list: List<String>) = store.set(LauncherSettings.SearchCards, list)

    InfoCard {
        Text("Cards on the search page", style = MaterialTheme.typography.titleMedium)
        BodyText("With nothing typed, top to bottom. The last one sits just above the field, under your thumb; the page scrolls up for the rest.")
        shown.forEachIndexed { index, id ->
            val card = SearchCards.all.first { it.id == id }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = true, onCheckedChange = { save(shown - id) })
                Column(Modifier.weight(1f)) {
                    Text(card.name, style = MaterialTheme.typography.bodyLarge)
                    Text(card.about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { save(shown.move(index, -1)) }, enabled = index > 0) {
                    LauncherIcon(LauncherIcons.ArrowUp, contentDescription = "Higher", size = 20.dp)
                }
                IconButton(onClick = { save(shown.move(index, 1)) }, enabled = index < shown.lastIndex) {
                    LauncherIcon(LauncherIcons.ArrowDown, contentDescription = "Lower", size = 20.dp)
                }
            }
        }
        hidden.forEach { card ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = false, onCheckedChange = { save(shown + card.id) })
                Column(Modifier.weight(1f)) {
                    Text(card.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(card.about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        TextButton(onClick = { store.reset(LauncherSettings.SearchCards) }, modifier = Modifier.padding(top = 4.dp)) { Text("Reset the cards") }
    }
}
