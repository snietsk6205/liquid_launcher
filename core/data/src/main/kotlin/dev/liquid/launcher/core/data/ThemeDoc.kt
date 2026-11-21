package dev.liquid.launcher.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/*
 * W3 (0.10.0): the theme as a document in WM Keyboard's own format.
 *
 * 0.9.13 kept only the parts of a WM Keyboard theme that buttons and chips used (LiquidTheme). The Theme lab edits every
 * option of the format, so the theme is now stored as WM Keyboard writes it (its ThemeSpec JSON, MIT,
 * github.com/wasi-master/wmkeyboard), every field kept as it came, and [LiquidTheme] is what the launcher draws, resolved
 * from it (ThemeResolve). Exporting writes the same JSON back, so a theme goes from the keyboard to the launcher and back.
 *
 * The launcher's additions live under one key, [ThemeDoc.EXT] ("liquidLauncher"), which WM Keyboard ignores (its decoder
 * skips unknown keys): which colours follow the wallpaper and with which role, the three pills' own looks, the glow of
 * controls and the colours of cards (WM Keyboard has none of these).
 */

/** A role a colour can take from the wallpaper (W3, spec 3.3). */
object WallpaperRole {
    const val DOMINANT = "dominant"
    const val VIBRANT = "vibrant"
    const val MUTED = "muted"
    const val DARK = "dark"
    const val LIGHT = "light"
    val all = listOf(DOMINANT, VIBRANT, MUTED, DARK, LIGHT)
    val labels = mapOf(DOMINANT to "Dominant", VIBRANT to "Vibrant", MUTED to "Muted", DARK to "Dark", LIGHT to "Light")
}

/** The three main pills (spec 3.4). */
object ThemePill {
    const val HOME = "home"
    const val DRAWER = "drawer"
    const val SEARCH = "search"
    val all = listOf(HOME, DRAWER, SEARCH)
    val labels = mapOf(HOME to "Home pill", DRAWER to "Drawer pill", SEARCH to "Search bar")
}

/** An immutable theme document: [json] is the WM Keyboard theme object, with the launcher's block under [EXT]. */
class ThemeDoc(val json: JsonObject) {

    /** The element at a dotted [path] ("frost.backdropBlur"); null when absent, JsonNull when set to Auto. */
    fun element(path: String): JsonElement? {
        var node: JsonElement = json
        for (part in path.split('.')) {
            node = (node as? JsonObject)?.get(part) ?: return null
        }
        return node
    }

    private fun primitive(path: String): JsonPrimitive? = (element(path) as? JsonPrimitive)?.takeIf { it !is JsonNull }

    /** An ARGB colour: WM Keyboard writes numbers; "#AARRGGBB" and "#RRGGBB" are read too. */
    fun color(path: String): Long? = primitive(path)?.let { p ->
        p.longOrNull ?: p.contentOrNull?.let { parseColor(it) }
    }

    fun float(path: String): Float? = primitive(path)?.doubleOrNull?.toFloat()
    fun int(path: String): Int? = primitive(path)?.doubleOrNull?.toInt()
    fun bool(path: String): Boolean? = primitive(path)?.booleanOrNull
    fun string(path: String): String? = primitive(path)?.takeIf { it.isString }?.content

    fun colors(path: String): List<Long> = (element(path) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.longOrNull }.orEmpty()
    fun strings(path: String): List<String> = (element(path) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

    /** A copy with [value] at [path] (nested objects made as needed); null writes JSON null, which is Auto. */
    fun with(path: String, value: JsonElement?): ThemeDoc = ThemeDoc(put(json, path.split('.'), value ?: JsonNull))

    fun withColor(path: String, value: Long?): ThemeDoc = with(path, value?.let { JsonPrimitive(it) })
    fun withFloat(path: String, value: Float?): ThemeDoc = with(path, value?.let { JsonPrimitive(round(it)) })
    fun withInt(path: String, value: Int?): ThemeDoc = with(path, value?.let { JsonPrimitive(it) })
    fun withBool(path: String, value: Boolean?): ThemeDoc = with(path, value?.let { JsonPrimitive(it) })
    fun withString(path: String, value: String?): ThemeDoc = with(path, value?.let { JsonPrimitive(it) })
    fun withStrings(path: String, value: List<String>): ThemeDoc = with(path, JsonArray(value.map { JsonPrimitive(it) }))
    fun withColors(path: String, value: List<Long>): ThemeDoc = with(path, JsonArray(value.map { JsonPrimitive(it) }))

    /** A copy without [path]: absent keys read as the field's default, as in WM Keyboard. */
    fun without(path: String): ThemeDoc = ThemeDoc(remove(json, path.split('.')))

    // ---- the launcher's block ----

    private val ext: JsonObject get() = json[EXT] as? JsonObject ?: JsonObject(emptyMap())

    private fun withExt(key: String, value: JsonElement?): ThemeDoc {
        val map = ext.toMutableMap()
        if (value == null) map.remove(key) else map[key] = value
        val root = json.toMutableMap()
        root[EXT] = JsonObject(map)
        return ThemeDoc(JsonObject(root))
    }

    /** The wallpaper role [field] follows, or null when it is the fixed colour stored in the theme. */
    fun follow(field: String): String? = ((ext["follow"] as? JsonObject)?.get(field) as? JsonPrimitive)?.contentOrNull

    fun withFollow(field: String, role: String?): ThemeDoc {
        val map = ((ext["follow"] as? JsonObject) ?: JsonObject(emptyMap())).toMutableMap()
        if (role == null) map.remove(field) else map[field] = JsonPrimitive(role)
        return withExt("follow", JsonObject(map))
    }

    val followed: Map<String, String>
        get() = (ext["follow"] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap().orEmpty()

    /** "shared": the three pills take the space bar's look; "individual": each has a theme of its own. */
    val pillLook: String get() = (ext["pillLook"] as? JsonPrimitive)?.contentOrNull ?: "shared"

    fun withPillLook(look: String): ThemeDoc = withExt("pillLook", JsonPrimitive(look))

    /** One pill's own theme (individual look), or null when it has none yet. */
    fun pill(which: String): ThemeDoc? = ((ext["pills"] as? JsonObject)?.get(which) as? JsonObject)?.let { ThemeDoc(it) }

    fun withPill(which: String, doc: ThemeDoc?): ThemeDoc {
        val map = ((ext["pills"] as? JsonObject) ?: JsonObject(emptyMap())).toMutableMap()
        // A pill's theme has no pills of its own.
        if (doc == null) map.remove(which) else map[which] = JsonObject(doc.json - EXT + (EXT to JsonObject(doc.ext - "pills" - "pillLook")))
        return withExt("pills", JsonObject(map))
    }

    /** A value of the launcher's block by dotted [path] (cards, glow, ...). */
    fun extElement(path: String): JsonElement? = element("$EXT.$path")

    val name: String get() = string("name")?.takeIf { it.isNotBlank() } ?: "Theme"

    fun encode(): String = pretty.encodeToString(JsonObject.serializer(), json)

    override fun equals(other: Any?): Boolean = other is ThemeDoc && other.json == json
    override fun hashCode(): Int = json.hashCode()

    companion object {
        const val EXT = "liquidLauncher"

        private val pretty = Json { prettyPrint = true }
        private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parseColor(text: String): Long? {
            val hex = text.trim().removePrefix("#")
            return when (hex.length) {
                6 -> hex.toLongOrNull(16)?.let { 0xFF000000L or it }
                8 -> hex.toLongOrNull(16)
                else -> null
            }
        }

        fun formatColor(argb: Long): String = "#%08X".format(argb and 0xFFFFFFFFL)

        /** Floats are kept to 4 decimals, so a slider's value does not write 0.30000001 into the file. */
        private fun round(v: Float): Double = Math.round(v * 10000.0) / 10000.0

        private fun put(obj: JsonObject, path: List<String>, value: JsonElement): JsonObject {
            val map = obj.toMutableMap()
            if (path.size == 1) {
                map[path[0]] = value
            } else {
                val child = map[path[0]] as? JsonObject ?: JsonObject(emptyMap())
                map[path[0]] = put(child, path.drop(1), value)
            }
            return JsonObject(map)
        }

        private fun remove(obj: JsonObject, path: List<String>): JsonObject {
            val map = obj.toMutableMap()
            if (path.size == 1) {
                map.remove(path[0])
            } else {
                val child = map[path[0]] as? JsonObject ?: return obj
                map[path[0]] = remove(child, path.drop(1))
            }
            return JsonObject(map)
        }

        /**
         * A theme document from [text]: a WM Keyboard theme, a WM Keyboard export or backup holding themes (the first),
         * or the launcher's 0.9.13 theme format (converted to the same fields). Null when it is none of these.
         */
        fun parse(text: String): ThemeDoc? {
            val root = runCatching { lenient.parseToJsonElement(text.trim()) }.getOrNull() ?: return null
            val obj = when (root) {
                is JsonObject -> root
                is JsonArray -> root.firstOrNull() as? JsonObject
                else -> null
            } ?: return null
            return when {
                obj.containsKey("keyBackground") -> ThemeDoc(obj)
                obj.containsKey("liquidTheme") -> LiquidThemes.decodeV1(obj)?.let { ThemeDocs.fromV1(it) }
                obj["themes"] is JsonArray -> ((obj["themes"] as JsonArray).firstOrNull() as? JsonObject)?.takeIf { it.containsKey("keyBackground") }?.let { ThemeDoc(it) }
                obj["customThemes"] is JsonArray -> ((obj["customThemes"] as JsonArray).firstOrNull() as? JsonObject)?.takeIf { it.containsKey("keyBackground") }?.let { ThemeDoc(it) }
                else -> null
            }
        }
    }
}
