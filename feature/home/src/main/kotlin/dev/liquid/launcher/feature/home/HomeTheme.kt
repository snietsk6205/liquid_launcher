package dev.liquid.launcher.feature.home

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.data.ThemedColors
import dev.liquid.launcher.core.settings.ColorSetting
import dev.liquid.launcher.core.settings.SettingsStore
import java.io.File
import dev.liquid.launcher.core.settings.LauncherSettings as S

/** The theme as the home screen uses it. Null colours mean "automatic". */
data class ThemeConfig(
    val textColor: Color?,
    val accent: Color?,
    val glassColor: Color?,
    val font: String,
    val clockFont: String,
    val clockWeight: String,
    val fontFile: String,
)

private fun color(value: String): Color? = ColorSetting.parse(value)?.let { Color(it) }

fun SettingsStore.themeConfig() = ThemeConfig(
    textColor = color(get(S.ThemeTextColor)),
    accent = color(get(S.ThemeAccent)),
    glassColor = color(get(S.ThemeGlassColor)),
    font = get(S.ThemeFont),
    clockFont = get(S.ThemeClockFont),
    clockWeight = get(S.ThemeClockWeight),
    fontFile = get(S.ThemeFontFile),
)

/**
 * Fonts and accent read by every text and highlight of the home screen. Snapshot state, so a
 * theme change redraws what uses them; set by the home screen from [ThemeConfig].
 */
object HomeTheme {
    var font by mutableStateOf<FontFamily?>(null)
    var clockFont by mutableStateOf<FontFamily?>(null)
    var clockWeight by mutableStateOf(FontWeight.Light)
    var accent by mutableStateOf(Color(0xFF8AB4F8))
    private var automaticAccent = true

    /** The wallpaper just changed (its colours are known): an automatic accent follows in this frame. */
    fun refreshAccent(context: Context) {
        if (automaticAccent) accent = autoAccent(context)
    }

    fun apply(context: Context, theme: ThemeConfig) {
        automaticAccent = theme.accent == null
        val fontDir = File(context.filesDir, "fonts")
        font = family(theme.font, fontDir, theme.fontFile)
        clockFont = if (theme.clockFont == "same") font else family(theme.clockFont, fontDir, theme.fontFile)
        clockWeight = when (theme.clockWeight) {
            "thin" -> FontWeight.Thin
            "normal" -> FontWeight.Normal
            "medium" -> FontWeight.Medium
            "bold" -> FontWeight.Bold
            else -> FontWeight.Light
        }
        accent = theme.accent ?: autoAccent(context)
    }

    /** A clear, mid-tone version of the wallpaper's main colour. */
    private fun autoAccent(context: Context): Color {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(ThemedColors.seed(context), hsl)
        return Color(androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(hsl[0], hsl[1].coerceIn(0.45f, 0.85f), 0.66f)))
    }

    private fun family(id: String, dir: File, file: String): FontFamily? = when (id) {
        "light" -> FontFamily(Typeface.create("sans-serif-light", Typeface.NORMAL))
        "medium" -> FontFamily(Typeface.create("sans-serif-medium", Typeface.NORMAL))
        "condensed" -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
        "serif" -> FontFamily.Serif
        "mono" -> FontFamily.Monospace
        "casual" -> FontFamily(Typeface.create("casual", Typeface.NORMAL))
        "cursive" -> FontFamily.Cursive
        "file" -> File(dir, file).takeIf { file.isNotBlank() && it.exists() }?.let {
            try {
                FontFamily(Font(it))
            } catch (e: Exception) {
                DebugLog.error("Theme", "font file unusable: $e")
                null
            }
        }
        else -> null
    }
}
