package dev.liquid.launcher.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.LetterArea
import dev.liquid.launcher.core.settings.LetterAreas
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.observe
import kotlin.math.roundToInt

/**
 * Settings → Drawer and search → Letter index → Layout: the letter areas (0.11.1). As many as wanted, each with the edge
 * its letters come out of, its place (across the screen and down the app list) and its size; the preview above shows
 * them and swipes from the first.
 */
@Composable
internal fun LetterAreasEditor(store: SettingsStore) {
    val style by store.observe(LauncherSettings.IndexStyle)
    val on by store.observe(LauncherSettings.IndexDial)
    val raw by store.observe(LauncherSettings.IndexAreas)
    val areas = remember(raw) { LetterAreas.parseAll(raw) }
    fun save(list: List<LetterArea>) = store.set(LauncherSettings.IndexAreas, LetterAreas.formatAll(list))

    InfoCard {
        Text("Letter areas", style = MaterialTheme.typography.titleMedium)
        if (style != "string" || !on) {
            BodyText(
                if (style != "string") "Letter areas work with the String letters (\"Letter index\" above)."
                else "Switch on \"Letter areas\" above to place them.",
            )
            return@InfoCard
        }
        BodyText(
            "Start a swipe inside an area and the letters come out at its edge, as if your finger were on them there, and " +
                "follow it. A tap or a long press still reaches the app under the circle.",
        )
        areas.forEachIndexed { i, area ->
            if (i > 0) HorizontalDivider(Modifier.padding(top = 10.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Area ${i + 1}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { save(areas.filterIndexed { j, _ -> j != i }) }) {
                    LauncherIcon(LauncherIcons.Close, contentDescription = "Remove area ${i + 1}", size = 20.dp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Letters from the", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                dev.liquid.launcher.core.designsystem.KitChip(selected = !area.right, onClick = { save(areas.replace(i, area.copy(right = false))) }, label = { Text("Left edge") })
                dev.liquid.launcher.core.designsystem.KitChip(selected = area.right, onClick = { save(areas.replace(i, area.copy(right = true))) }, label = { Text("Right edge") })
            }
            AreaSlider("Across", area.across, 0, 100, 1, "%") { save(areas.replace(i, area.copy(across = it))) }
            AreaSlider("Down the apps", area.down, 0, 100, 1, "%") { save(areas.replace(i, area.copy(down = it))) }
            AreaSlider("Size", area.sizeDp, LetterArea.MIN_SIZE, LetterArea.MAX_SIZE, 4, "dp") { save(areas.replace(i, area.copy(sizeDp = it))) }
        }
        if (areas.isEmpty()) BodyText("No area yet: the letters come only from the edge.")
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { save(areas + LetterAreas.next(areas)) }) {
                LauncherIcon(LauncherIcons.Add, contentDescription = null, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text("Add an area")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { store.reset(LauncherSettings.IndexAreas) }) { Text("Reset") }
        }
    }
}

/** A slider that saves when let go, showing its value while it moves. */
@Composable
private fun AreaSlider(label: String, value: Int, min: Int, max: Int, step: Int, unit: String, onSet: (Int) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragged by remember { mutableFloatStateOf(value.toFloat()) }
    fun snapped(v: Float) = (min + ((v - min) / step).roundToInt() * step).coerceIn(min, max)
    val shown = if (dragging) snapped(dragged) else value
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("$shown $unit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        dev.liquid.launcher.core.designsystem.KitSlide(
            value = if (dragging) dragged else value.toFloat(),
            onValueChange = {
                dragging = true
                dragged = it
            },
            onValueChangeFinished = {
                onSet(snapped(dragged))
                dragging = false
            },
            valueRange = min.toFloat()..max.toFloat(),
        )
    }
}

private fun <T> List<T>.replace(i: Int, value: T): List<T> = mapIndexed { j, v -> if (j == i) value else v }
