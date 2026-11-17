package dev.liquid.launcher.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.data.LiquidTheme
import dev.liquid.launcher.core.data.LiquidThemes
import dev.liquid.launcher.core.data.ThemeApply
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.observe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The theme of buttons and chips (P4, 0.9.13), at the top of Settings → Buttons: which theme is in use, its colours
 * at a glance, and import (the launcher's theme files, WM Keyboard themes and backups), export and back to your
 * keyboard theme. The buttons in the preview above show every change at once.
 */
@Composable
internal fun ControlThemeCard(store: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mode by store.observe(LauncherSettings.ThemeControls)
    val json by store.observe(LauncherSettings.ThemeJson)
    val theme = remember(json) { ThemeApply.current(store) }
    var message by remember { mutableStateOf<String?>(null) }

    val importTheme = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBefore('.')?.takeIf { it.isNotBlank() } ?: "Imported theme"
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            val imported = text?.let { LiquidThemes.decode(it, fallbackName = name) }
            if (imported == null) {
                message = "That file is not a theme (the launcher's or WM Keyboard's)"
            } else {
                ThemeApply.use(store, imported)
                message = "Using \"${imported.name}\"; its accent and glass colour are in Settings → Theme"
            }
        }
    }
    val exportTheme = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(LiquidThemes.encode(theme).encodeToByteArray()) } != null
                }.getOrDefault(false)
            }
            message = if (ok) "Theme saved" else "Saving failed"
        }
    }

    InfoCard {
        Text("Theme", style = MaterialTheme.typography.titleMedium)
        if (mode != "theme") {
            BodyText("Off: buttons use the launcher's own look (the options below). Switch \"Buttons and chips\" to Your theme to use it.")
        }
        Text(theme.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
        theme.source?.let { BodyText(it) }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Swatch(theme.button.fill, theme.button.text, "Aa")
            Swatch(theme.button.pressed, theme.button.pressedText ?: theme.button.text, "Aa")
            Swatch(theme.chip.fill, theme.chip.text, "Aa")
            Swatch(theme.chip.active, theme.chip.activeText, "Aa")
            Swatch(theme.tool.fill, theme.tool.text, "●")
            Swatch(theme.accent, 0xFF000000, "")
        }
        BodyText(
            "Button, pressed, chip, chip on, round button, accent. Glass: ${theme.frost.style.lowercase()}, " +
                "rim ${(theme.frost.rim * 100).toInt()} %" + if (theme.frost.liquid) ", bend ${(theme.frost.refraction * 100).toInt()} %" else "",
            Modifier.padding(top = 6.dp),
        )
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { importTheme.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }) { Text("Import…") }
            OutlinedButton(onClick = { exportTheme.launch(theme.name.replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "theme" } + ".liquidtheme.json") }) { Text("Export…") }
        }
        if (theme != LiquidThemes.Keyboard || mode != "theme") {
            TextButton(onClick = {
                ThemeApply.use(store, LiquidThemes.Keyboard)
                message = "Back to your keyboard theme (Nice liquid)"
            }) { Text("Back to your keyboard theme") }
        }
        BodyText(
            "Import reads the launcher's theme files and WM Keyboard themes and backups. The theme's accent and glass " +
                "colour go to Settings → Theme, where you can still change them.",
            Modifier.padding(top = 4.dp),
        )
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp)) }
    }
}

/** A colour of the theme with a sample of its text. */
@Composable
private fun Swatch(fill: Long, text: Long, sample: String) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .size(width = 40.dp, height = 28.dp)
            .background(Color(0xFF2A2A2E), shape)
            .background(Color(fill), shape)
            .border(1.dp, Color.White.copy(alpha = 0.15f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (sample.isNotEmpty()) Text(sample, color = Color(text), style = MaterialTheme.typography.labelMedium)
    }
}
