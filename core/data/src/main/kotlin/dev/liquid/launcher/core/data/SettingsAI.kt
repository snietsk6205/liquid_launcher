package dev.liquid.launcher.core.data

import dev.liquid.launcher.core.settings.ActionSetting
import dev.liquid.launcher.core.settings.BoolSetting
import dev.liquid.launcher.core.settings.ChoiceSetting
import dev.liquid.launcher.core.settings.ColorSetting
import dev.liquid.launcher.core.settings.IntSetting
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.Setting
import dev.liquid.launcher.core.settings.SettingsMap
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.SettingsTab
import dev.liquid.launcher.core.settings.StringListSetting
import dev.liquid.launcher.core.settings.TextSetting
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/*
 * W5 (0.10.1): tuning the launcher with Claude, without any network call from the launcher. "Export for AI" writes
 * one Markdown document: what the launcher is, how to answer (only a JSON patch), what you changed, and every option
 * with its id, kind, range or choices, default and current value. Claude's answer is pasted back: [parse] checks
 * every entry against the registry (unknown ids, wrong kinds, values out of range are refused and shown in red),
 * [apply] changes only the valid ones and returns what [undo] needs to put everything back in one tap.
 */
object SettingsAI {

    /** The JSON value an option holds now, as Claude should write it back. */
    fun jsonValue(setting: Setting<*>, settings: SettingsStore): String = literal(setting, settings.get(setting))

    private fun literal(setting: Setting<*>, value: Any?): String = when (setting) {
        is BoolSetting, is IntSetting -> value.toString()
        is StringListSetting -> (value as List<*>).joinToString(", ", "[", "]") { quote(it.toString()) }
        else -> quote(value.toString())
    }

    private fun quote(text: String): String = JsonPrimitive(text).toString()

    /** The document for Claude; [question] is what you want, [version] the launcher's. */
    fun export(settings: SettingsStore, version: String, question: String): String = buildString {
        appendLine("# Liquid Launcher: settings to tune")
        appendLine()
        appendLine("Liquid Launcher $version is my Android home screen (a Jetpack Compose launcher on a HONOR 90 Lite).")
        appendLine("Below is every option it has, with its id, kind, allowed values, default and current value.")
        appendLine()
        appendLine("## How to answer")
        appendLine()
        appendLine("Answer with **one JSON object and nothing else** (no prose before or after, no code fence), in this form:")
        appendLine()
        appendLine("    {\"set\": {\"<id>\": <value>, ...}, \"reset\": [\"<id>\", ...], \"why\": \"<one short sentence>\"}")
        appendLine()
        appendLine("- Use only ids listed below; anything else is refused.")
        appendLine("- switch: true or false. number: a whole number inside its range. choice: one of the listed values (the part before the brackets).")
        appendLine("- colour: \"#RRGGBB\", or \"\" for automatic. text and action: a string. list: an array of strings.")
        appendLine("- \"reset\" puts options back to their default. Leave out what should stay as it is.")
        appendLine("- I will see every change before it is applied, and can undo it in one tap.")
        appendLine()
        appendLine("## My question")
        appendLine()
        appendLine(question.trim().ifEmpty { "Look at my settings and suggest what would make the launcher nicer and smoother to use." })
        appendLine()
        val shown = LauncherSettings.all.filter { !it.hidden }
        val changed = shown.filter { settings.isChanged(it) }
        appendLine("## What I changed from the defaults (${changed.size})")
        appendLine()
        if (changed.isEmpty()) appendLine("Nothing yet.")
        for (setting in changed) {
            appendLine("- `${setting.key}` (${setting.title}): ${jsonValue(setting, settings)} (default ${literal(setting, setting.default)})")
        }
        appendLine()
        appendLine("## Every option")
        for (surface in SettingsMap.visibleSurfaces) {
            val options = SettingsTab.entries.flatMap { SettingsMap.settingsOf(surface, it) }
            if (options.isEmpty()) continue
            appendLine()
            appendLine("### ${surface.entry.title} › ${surface.title}")
            appendLine()
            for (setting in options) {
                val info = SettingsMap.info(setting)
                append("- `").append(info.id).append("` ").append(info.type)
                if (info.values.isNotEmpty() && setting !is BoolSetting) append(" [").append(info.values).append(']')
                append(", default ").append(literal(setting, setting.default))
                append(", now ").append(jsonValue(setting, settings))
                append(": ").append(setting.title)
                if (setting.summary.isNotEmpty()) append(". ").append(setting.summary.trimEnd('.'))
                info.dependsOn?.let { append(" (only with `").append(it).append("`)") }
                appendLine()
            }
        }
    }

    /** One entry of Claude's answer: what it changes, or why it is refused. */
    class Change(
        val id: String,
        val setting: Setting<*>?,
        /** What the option shows now and would show after, in words. */
        val now: String,
        val then: String,
        /** Why the entry is refused (shown in red); null when it can be applied. */
        val problem: String?,
        internal val write: ((SettingsStore) -> Unit)?,
    )

    class Patch(val changes: List<Change>, val why: String?, val error: String?) {
        val valid: List<Change> get() = changes.filter { it.problem == null }
    }

    /** Reads Claude's answer: the JSON object in it (a code fence or text around it is tolerated). */
    fun parse(answer: String, settings: SettingsStore): Patch {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return Patch(emptyList(), null, "No JSON object in the answer")
        val root = runCatching { Json.parseToJsonElement(answer.substring(start, end + 1)) }.getOrNull() as? JsonObject
            ?: return Patch(emptyList(), null, "The answer is not valid JSON")
        val why = (root["why"] as? JsonPrimitive)?.contentOrNull
        val set: Map<String, JsonElement> = (root["set"] as? JsonObject)
            ?: if ("set" in root || "reset" in root) emptyMap() else root.filterKeys { it != "why" }
        val reset = (root["reset"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        val byKey = LauncherSettings.all.associateBy { it.key }
        val changes = ArrayList<Change>()
        for ((id, value) in set) changes += change(id, byKey[id], value, settings)
        for (id in reset) {
            val setting = byKey[id]
            changes += when {
                setting == null || setting.hidden -> Change(id, null, "", "default", "No option has this id", null)
                else -> Change(id, setting, shown(setting, settings.get(setting)), shown(setting, setting.default) + " (default)", null) { it.reset(setting) }
            }
        }
        return Patch(changes, why, if (changes.isEmpty()) "The answer changes nothing" else null)
    }

    private fun change(id: String, setting: Setting<*>?, value: JsonElement, settings: SettingsStore): Change {
        if (setting == null || setting.hidden) return Change(id, null, "", value.toString(), "No option has this id", null)
        val now = shown(setting, settings.get(setting))
        fun bad(reason: String) = Change(id, setting, now, value.toString(), reason, null)
        val p = value as? JsonPrimitive
        return when (setting) {
            is BoolSetting -> {
                val b = p?.booleanOrNull ?: when (p?.contentOrNull?.lowercase()) { "on" -> true; "off" -> false; else -> null }
                    ?: return bad("Needs true or false")
                Change(id, setting, now, shown(setting, b), null) { it.set(setting, b) }
            }
            is IntSetting -> {
                val n = p?.intOrNull ?: p?.contentOrNull?.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 }?.toInt()
                    ?: return bad("Needs a whole number")
                if (n < setting.min || n > setting.max) return bad("Outside ${setting.min}..${setting.max}")
                Change(id, setting, now, shown(setting, n), null) { it.set(setting, n) }
            }
            is ChoiceSetting -> {
                val text = p?.takeIf { it.isString }?.content ?: return bad("Needs one of: " + setting.options.joinToString { it.first })
                val v = setting.options.firstOrNull { it.first == text }?.first
                    ?: setting.options.firstOrNull { it.second.equals(text, ignoreCase = true) }?.first
                    ?: return bad("Not one of: " + setting.options.joinToString { it.first })
                Change(id, setting, now, shown(setting, v), null) { it.set(setting, v) }
            }
            is ColorSetting -> {
                val text = p?.takeIf { it.isString }?.content?.trim() ?: return bad("Needs \"#RRGGBB\" or \"\"")
                if (text.isNotEmpty() && ColorSetting.parse(text) == null) return bad("Needs \"#RRGGBB\" or \"\"")
                val v = if (text.isEmpty()) "" else ColorSetting.format(ColorSetting.parse(text)!!)
                Change(id, setting, now, shown(setting, v), null) { it.set(setting, v) }
            }
            is ActionSetting -> {
                val text = p?.takeIf { it.isString }?.content?.trim() ?: return bad("Needs an action code")
                if (text != "none" && LauncherAction.decode(text) == LauncherAction.None) return bad("Unknown action")
                Change(id, setting, now, text, null) { it.set(setting, text) }
            }
            is TextSetting -> {
                val text = p?.takeIf { it.isString }?.content ?: return bad("Needs text")
                Change(id, setting, now, text, null) { it.set(setting, text) }
            }
            is StringListSetting -> {
                val list = (value as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { e -> e.isString }?.content ?: return bad("Needs a list of texts") }
                    ?: return bad("Needs a list of texts")
                Change(id, setting, now, list.joinToString(", "), null) { it.set(setting, list) }
            }
        }
    }

    /** A value as the settings screens show it. */
    fun shown(setting: Setting<*>, value: Any?): String = when (setting) {
        is BoolSetting -> if (value == true) "on" else "off"
        is ChoiceSetting -> setting.labelOf(value.toString())
        is IntSetting -> "$value" + if (setting.unit.isNotEmpty()) " ${setting.unit}" else ""
        is ColorSetting -> value.toString().ifEmpty { setting.autoLabel }
        is StringListSetting -> (value as List<*>).joinToString(", ").ifEmpty { "none" }
        else -> value.toString().ifEmpty { "empty" }
    }

    /** What undoes an apply: the stored values of the options it touched, as they were. */
    class Undo internal constructor(internal val keys: Set<String>, internal val before: Map<String, String>)

    fun apply(patch: Patch, settings: SettingsStore): Undo {
        val valid = patch.valid
        val keys = valid.map { it.id }.toSet()
        val undo = Undo(keys, settings.exportValues(keys))
        for (change in valid) change.write?.invoke(settings)
        dev.liquid.launcher.core.common.DebugLog.event("Settings", "applied ${valid.size} suggestion(s) from Claude")
        return undo
    }

    fun undo(undo: Undo, settings: SettingsStore) {
        settings.importValues(undo.before, undo.keys)
        dev.liquid.launcher.core.common.DebugLog.event("Settings", "undid ${undo.keys.size} suggestion(s)")
    }
}
