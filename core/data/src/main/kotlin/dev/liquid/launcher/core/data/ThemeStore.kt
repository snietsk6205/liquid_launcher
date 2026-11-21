package dev.liquid.launcher.core.data

import android.content.Context
import android.util.Base64
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.security.MessageDigest

/**
 * Where the theme lives (W3, 0.10.0): Settings' "theme.json" holds the theme document (WM Keyboard's JSON plus the
 * launcher's block); pictures are files in the app's own storage, named in the document, as WM Keyboard keeps them.
 */
object ThemeStore {
    /** The value 0.9.13 wrote into Settings → Theme → Accent when you had not chosen one: your keyboard's pink. */
    private const val V013_ACCENT = "#D182AF"

    /** The theme document in use (the default when none was saved). */
    fun doc(settings: SettingsStore): ThemeDoc =
        settings.get(LauncherSettings.ThemeJson).takeIf { it.isNotBlank() }?.let { ThemeDoc.parse(it) } ?: ThemeDocs.Default

    fun save(settings: SettingsStore, doc: ThemeDoc) {
        settings.set(LauncherSettings.ThemeJson, doc.encode())
        settings.set(LauncherSettings.ThemeControls, "theme")
    }

    /** "Current look" in the lab: the theme as it was when 0.10 first ran, null on a new install. */
    fun previous(settings: SettingsStore): ThemeDoc? =
        settings.get(LauncherSettings.ThemePrevious).takeIf { it.isNotBlank() }?.let { ThemeDoc.parse(it) }

    /** "Before 0.10.2" in the lab: the look you had when 0.10.2 made your keyboard theme the default; null on a new install. */
    fun before0102(settings: SettingsStore): ThemeDoc? =
        settings.get(LauncherSettings.ThemeBefore0102).takeIf { it.isNotBlank() }?.let { ThemeDoc.parse(it) }

    /** "My backup" in the lab: your WM Keyboard theme, shipped in the app, with its picture as a file. */
    fun backup(context: Context): ThemeDoc? = runCatching {
        context.assets.open("themes/nice_liquid.wmtheme.json").bufferedReader().use { it.readText() }
    }.getOrNull()?.let { ThemeDoc.parse(it) }?.let { materialize(context, it) }

    /**
     * Once, on the first start of 0.10: the theme in use becomes a document (a 0.9.13 theme is converted, nothing is
     * lost), the accent is the fixed blue unless you had chosen your own colour, and the main button keeps the colour
     * you chose for it. The document as it then is stays as the lab's "Current look".
     */
    fun migrate010(settings: SettingsStore) {
        if (settings.get(LauncherSettings.ThemeMigrated010)) return
        settings.set(LauncherSettings.ThemeMigrated010, true)
        val accent = settings.get(LauncherSettings.ThemeAccent).trim()
        val chosen = accent.isNotEmpty() && !accent.equals(V013_ACCENT, ignoreCase = true)
        // 0.10.2: with no theme saved (a new install), the default is used as it is (its accent follows the wallpaper),
        // unless an accent or a main button colour you chose has to be kept in it.
        val blank = settings.get(LauncherSettings.ThemeJson).isBlank()
        val mainChosen = settings.isChanged(LauncherSettings.ButtonMain)
        if (blank && !chosen && !mainChosen) return
        var doc = doc(settings)
        if (chosen || !blank) {
            doc = doc.withColor("accent", if (chosen) ThemeDoc.parseColor(accent) ?: ThemeDocs.BLUE else ThemeDocs.BLUE).withFollow("accent", null)
        }
        if (!blank || mainChosen) doc = doc.withString("${ThemeDoc.EXT}.mainColor", settings.get(LauncherSettings.ButtonMain))
        doc = withLauncherDefaults(doc)
        settings.set(LauncherSettings.ThemeJson, doc.encode())
        settings.set(LauncherSettings.ThemePrevious, doc.encode())
        DebugLog.event("Theme", "moved to the 0.10 theme document; accent ${if (chosen) "kept ($accent)" else "fixed blue"}")
    }

    /**
     * Once, on the first start of 0.10.2 (your feedback on 0.10.1):
     * - the dark fill 0.10.1 gave the pills (and its text) goes, wherever it is still exactly that value: in the theme,
     *   in each pill's own theme, and in the look kept from before 0.10; the search bar keeps a tint a third as strong.
     *   A colour you set yourself is kept;
     * - the theme in use (so cleaned) is kept as the lab's preset "Before 0.10.2", and the default becomes your keyboard
     *   theme in the wallpaper's colours ([ThemeDocs.Default]); the other looks of your theme (its variants) come along.
     * A new install has nothing to keep and already uses the default.
     */
    fun migrate0102(settings: SettingsStore) {
        if (settings.get(LauncherSettings.ThemeMigrated0102)) return
        settings.set(LauncherSettings.ThemeMigrated0102, true)
        previous(settings)?.let { settings.set(LauncherSettings.ThemePrevious, withoutDarkPills(it).encode()) }
        val stored = settings.get(LauncherSettings.ThemeJson)
        if (stored.isBlank()) return
        val mine = ThemeDoc.parse(stored)?.let { withoutDarkPills(it) } ?: return
        settings.set(LauncherSettings.ThemeBefore0102, mine.encode())
        var next = ThemeDocs.Default
        mine.element("variants")?.let { if (it is JsonArray && it.isNotEmpty()) next = next.with("variants", it) }
        settings.set(LauncherSettings.ThemeJson, next.encode())
        settings.set(LauncherSettings.ThemeControls, "theme")
        DebugLog.event("Theme", "0.10.2: your keyboard theme in the wallpaper's colours is the default; the look before is the preset \"Before 0.10.2\"")
    }

    /**
     * [doc] without the dark pills of 0.10.1: their fill and text where they are still exactly what 0.10.1 wrote; the
     * search bar keeps a tint a third as strong. Each pill's own theme (individual pills) likewise.
     */
    fun withoutDarkPills(doc: ThemeDoc): ThemeDoc {
        val ext = ThemeDoc.EXT
        var d = doc
        if (d.color("$ext.space.background") == ThemeDocs.DARK_PILL && d.follow("$ext.space.background") == null) {
            d = d.without("$ext.space.background")
            if (d.color("$ext.space.searchBackground") == null) d = d.withColor("$ext.space.searchBackground", ThemeDocs.SEARCH_TINT)
            if (d.color("$ext.space.text") == ThemeDocs.DARK_PILL_TEXT && d.follow("$ext.space.text") == null) d = d.without("$ext.space.text")
        }
        for (which in ThemePill.all) {
            var pill = d.pill(which) ?: continue
            if (pill.color("$ext.space.background") == ThemeDocs.DARK_PILL && pill.follow("$ext.space.background") == null) {
                pill = if (which == ThemePill.SEARCH) pill.withColor("$ext.space.background", ThemeDocs.SEARCH_TINT) else pill.without("$ext.space.background")
                if (pill.color("$ext.space.text") == ThemeDocs.DARK_PILL_TEXT && pill.follow("$ext.space.text") == null) pill = pill.without("$ext.space.text")
                d = d.withPill(which, pill)
            }
        }
        return d
    }

    /**
     * What a keyboard theme cannot say, from the launcher's defaults where the document has nothing: cards and the
     * pills' shared look. Anything already there is kept. (0.10.0 and 0.10.1 also made the pills dark here; 0.10.2 no
     * longer does: a theme's pills are its own glass.)
     */
    fun withLauncherDefaults(doc: ThemeDoc): ThemeDoc {
        val ext = ThemeDoc.EXT
        var out = doc
        if (out.extElement("card.background") == null) out = out.withColor("$ext.card.background", 0x59141218L)
        if (out.extElement("card.cornerDp") == null) out = out.withInt("$ext.card.cornerDp", 22)
        if (out.extElement("pillLook") == null) out = out.withPillLook("shared")
        return out
    }

    // ---- pictures ----

    private fun dir(context: Context) = File(context.filesDir, "theme").apply { mkdirs() }

    private fun store(context: Context, bytes: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }.take(16)
        val file = File(dir(context), "$hash.img")
        if (!file.exists()) file.writeBytes(bytes)
        return file.absolutePath
    }

    /** Keeps a picture the user chose (a gallery or file URI) for the theme; returns its path. */
    fun keepPicture(context: Context, uri: android.net.Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { store(context, it) }
    }.onFailure { DebugLog.error("Theme", "picture not kept: $it") }.getOrNull()

    private fun decode(b64: String?): ByteArray? = b64?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }

    /** Asset slot (WM Keyboard's) → the field naming the picture. */
    private val slots = mapOf(
        "keyTexture" to "keyTexture", "keyTextureModifier" to "keyTextureModifier", "keyTextureEnter" to "keyTextureEnter",
        "keyTextureSpace" to "keyTextureSpace", "keyTexturePressed" to "keyTexturePressed", "popupTexture" to "popupTexture",
    )

    /**
     * An imported theme with its pictures written to files: the background picture (WM Keyboard's
     * backgroundImageBase64) and the textures, stickers and particle pictures of its `assets`. The base64 copies
     * leave the document, so Settings stays small.
     */
    fun materialize(context: Context, doc: ThemeDoc): ThemeDoc {
        var d = doc
        decode(doc.string("backgroundImageBase64"))?.let { d = d.withString("backgroundImage", store(context, it)) }
        d = d.without("backgroundImageBase64").without("backgroundImageLandscapeBase64")
        val assets = doc.element("assets") as? JsonObject
        if (assets != null) {
            for ((slot, value) in assets) {
                val bytes = decode((value as? JsonPrimitive)?.contentOrNull) ?: continue
                val path = store(context, bytes)
                when {
                    slot in slots -> d = d.withString(slots.getValue(slot), path)
                    slot.startsWith("decal:") -> {
                        val id = slot.removePrefix("decal:")
                        val list = (d.element("decals") as? JsonArray)?.map { e ->
                            val o = e as? JsonObject ?: return@map e
                            if ((o["id"] as? JsonPrimitive)?.contentOrNull == id) JsonObject(o + ("image" to JsonPrimitive(path))) else o
                        }
                        if (list != null) d = d.with("decals", JsonArray(list))
                    }
                    slot.startsWith("effectImage:") -> {
                        val index = slot.removePrefix("effectImage:").toIntOrNull() ?: continue
                        val list = d.strings("keyEffectImages").toMutableList()
                        while (list.size <= index) list.add("")
                        list[index] = path
                        d = d.withStrings("keyEffectImages", list)
                    }
                }
            }
            d = d.without("assets")
        }
        return withLauncherDefaults(d)
    }

    /**
     * The theme as a file to share or to open in WM Keyboard: pictures go back into `assets` and
     * backgroundImageBase64, as WM Keyboard exports them, and local paths leave the file.
     */
    fun embed(doc: ThemeDoc): ThemeDoc {
        var d = doc
        fun b64(path: String?): String? = path?.let { p ->
            runCatching { Base64.encodeToString(File(p).readBytes(), Base64.NO_WRAP) }.getOrNull()
        }
        b64(doc.string("backgroundImage"))?.let { d = d.withString("backgroundImageBase64", it) }
        d = d.withString("backgroundImage", null)
        val assets = LinkedHashMap<String, JsonPrimitive>()
        for ((slot, field) in slots) {
            b64(doc.string(field))?.let { assets[slot] = JsonPrimitive(it) }
            d = d.withString(field, null)
        }
        (doc.element("decals") as? JsonArray)?.let { decals ->
            val out = decals.map { e ->
                val o = e as? JsonObject ?: return@map e
                val id = (o["id"] as? JsonPrimitive)?.contentOrNull
                b64((o["image"] as? JsonPrimitive)?.contentOrNull)?.let { if (id != null) assets["decal:$id"] = JsonPrimitive(it) }
                JsonObject(o - "image")
            }
            d = d.with("decals", JsonArray(out))
        }
        doc.strings("keyEffectImages").forEachIndexed { i, p -> b64(p)?.let { assets["effectImage:$i"] = JsonPrimitive(it) } }
        if (doc.strings("keyEffectImages").isNotEmpty()) d = d.withStrings("keyEffectImages", emptyList())
        if (assets.isNotEmpty()) d = d.with("assets", JsonObject(assets))
        return d
    }
}
