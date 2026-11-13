package dev.liquid.launcher.core.data

import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A built-in theme: a name and setting values (as [SettingsStore.exportValues] writes them). */
data class ThemePreset(val name: String, val values: Map<String, String>)

/** Theme presets and `.lltheme` files (theme settings plus an imported font, if any). */
object Themes {
    private val S = LauncherSettings
    private fun s(v: String) = "s:$v"
    private fun i(v: Int) = "i:$v"

    val presets = listOf(
        ThemePreset("Liquid (default)", emptyMap()),
        ThemePreset(
            "Frost",
            mapOf(
                S.ThemeFont.key to s("light"), S.ThemeClockWeight.key to s("thin"), S.GlassTintMode.key to s("light"),
                S.GlassDefault.blur.key to i(12), S.GlassDefault.tint.key to i(30), S.ThemeAccent.key to s("#8AB4F8"),
            ),
        ),
        ThemePreset(
            "Midnight",
            mapOf(
                S.ThemeFont.key to s("medium"), S.ThemeClockWeight.key to s("medium"), S.GlassTintMode.key to s("dark"),
                S.ThemeGlassColor.key to s("#141A33"), S.GlassDefault.tint.key to i(40), S.ThemeAccent.key to s("#7C8CFF"),
            ),
        ),
        ThemePreset(
            "Paper",
            mapOf(
                S.ThemeFont.key to s("serif"), S.ThemeClockFont.key to s("serif"), S.ThemeClockWeight.key to s("normal"),
                S.ThemeTextColor.key to s("#1E1B16"), S.ThemeGlassColor.key to s("#FFF6E5"), S.GlassTintMode.key to s("light"),
                S.GlassDefault.tint.key to i(55), S.ThemeAccent.key to s("#B5835A"), S.ThemeIconShape.key to s("rounded"),
            ),
        ),
        ThemePreset(
            "Mono",
            mapOf(
                S.ThemeFont.key to s("mono"), S.ThemeClockWeight.key to s("normal"), S.ThemeAccent.key to s("#B0BEC5"),
                S.ThemeIconShape.key to s("square"), S.GlassDefault.refraction.key to i(20),
            ),
        ),
        ThemePreset(
            "Candy",
            mapOf(
                S.ThemeFont.key to s("casual"), S.ThemeClockWeight.key to s("bold"), S.ThemeAccent.key to s("#F06292"),
                S.ThemeGlassColor.key to s("#FFC1DA"), S.GlassDefault.tint.key to i(28), S.ThemeIconShape.key to s("circle"),
            ),
        ),
    )

    /** Applies [preset]: every theme setting it does not name goes back to its default. The imported font stays on disk. */
    fun apply(settings: SettingsStore, preset: ThemePreset) = settings.importValues(preset.values, S.themeKeys)

    fun export(settings: SettingsStore, fontsDir: File, out: OutputStream) {
        val values = settings.exportValues(S.themeKeys)
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("theme.json"))
            zip.write(LayoutsRepository.json.encodeToString(MapSerializer(String.serializer(), String.serializer()), values).toByteArray())
            zip.closeEntry()
            val font = settings.get(S.ThemeFontFile)
            File(fontsDir, font).takeIf { font.isNotBlank() && it.exists() }?.let { file ->
                zip.putNextEntry(ZipEntry("font/${file.name}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Applies the theme in [input]; returns false when it is not a theme file. */
    fun import(settings: SettingsStore, fontsDir: File, input: InputStream): Boolean {
        var values: Map<String, String>? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == "theme.json" ->
                        values = LayoutsRepository.json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), zip.readBytes().decodeToString())
                    entry.name.startsWith("font/") && !entry.name.contains("..") -> {
                        fontsDir.mkdirs()
                        File(fontsDir, entry.name.removePrefix("font/")).writeBytes(zip.readBytes())
                    }
                }
            }
        }
        val read = values ?: return false
        settings.importValues(read, S.themeKeys)
        return true
    }
}
