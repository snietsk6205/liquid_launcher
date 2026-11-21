package dev.liquid.launcher.core.common

/**
 * The launcher's CHANGELOG.md (W1, 0.10.0), read for the Updates screen, the release notes GitHub
 * sends and the one-time "What's new" card. The file has one "## <version> — <date>" section per
 * version, each with "### Added / Changed / Removed / Fixed" parts made of "- " items; an item may
 * wrap onto indented lines. A line in *italics* right under the version is its note ("Not
 * published on its own: …").
 */
object Changelog {
    data class Part(val title: String, val items: List<String>)

    data class Version(val version: String, val date: String?, val note: String?, val parts: List<Part>) {
        val itemCount: Int get() = parts.sumOf { it.items.size }
    }

    /** Every version in [text], newest first as written. */
    fun parse(text: String): List<Version> {
        val versions = ArrayList<Version>()
        var name: String? = null
        var date: String? = null
        val body = StringBuilder()
        fun flush() {
            val v = name ?: return
            versions += parseSection(v, date, body.toString())
        }
        for (line in text.lines()) {
            if (line.startsWith("## ")) {
                flush()
                val heading = line.removePrefix("## ").trim()
                val split = heading.split(Regex("\\s+[—–-]\\s+"), limit = 2)
                name = split[0].trim()
                date = split.getOrNull(1)?.trim()
                body.setLength(0)
            } else if (name != null) {
                body.append(line).append('\n')
            }
        }
        flush()
        return versions
    }

    /** The section of [version], or null. */
    fun find(text: String, version: String): Version? = parse(text).firstOrNull { it.version == version.removePrefix("v") }

    /**
     * One version's body: the GitHub release notes are exactly this (the "### …" parts without the
     * version heading), so the Updates screen reads them with the same rules.
     */
    fun parseSection(version: String, date: String?, body: String): Version {
        val parts = ArrayList<Part>()
        var note: String? = null
        var title: String? = null
        val items = ArrayList<String>()
        var item: StringBuilder? = null
        fun endItem() {
            item?.let { items += it.toString().trim() }
            item = null
        }
        fun endPart() {
            endItem()
            title?.let { if (items.isNotEmpty()) parts += Part(it, items.toList()) }
            items.clear()
        }
        for (raw in body.lines()) {
            val line = raw.trimEnd()
            when {
                line.startsWith("### ") -> {
                    endPart()
                    title = line.removePrefix("### ").trim()
                }
                // The build line GitHub's notes end with, after a rule.
                line.trim() == "---" -> {
                    endPart()
                    title = null
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    endItem()
                    if (title == null) title = "Changes"
                    item = StringBuilder(line.substring(2).trim())
                }
                line.isNotBlank() && raw.startsWith("  ") && item != null -> item!!.append(' ').append(line.trim())
                line.isBlank() -> endItem()
                title == null && note == null && line.startsWith("*") && line.endsWith("*") && line.length > 2 ->
                    note = line.trim('*').trim()
            }
        }
        endPart()
        return Version(version, date, note, parts)
    }

    /** Markdown marks left in an item (`code`, **bold**) taken out for plain text. */
    fun plain(item: String): String = item.replace("**", "").replace("`", "")
}
