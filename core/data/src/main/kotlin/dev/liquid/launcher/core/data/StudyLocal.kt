package dev.liquid.launcher.core.data

/**
 * A change the launcher could not write to the calendar (no write permission, or "Write ticks
 * back" is off). `docs/05-study-integration.md` 5.2: the launcher then "still shows everything
 * and keeps ticks to itself". It is kept on the phone as the event's text with the marks applied
 * ([title], [description]), together with the calendar text it was made from ([baseTitle],
 * [baseDescription]).
 *
 * It is shown instead of the calendar's text for as long as the calendar still holds that base
 * text. Once the event changes in the calendar (the assistant re-planned it), the calendar wins
 * and the phone's copy is dropped. A later change that can be written starts from the phone's
 * copy, so ticks seen on the phone are not lost from the event.
 */
data class StudyLocalEdit(
    val baseTitle: String,
    val baseDescription: String,
    val title: String,
    val description: String,
    val savedAt: Long,
)

object StudyLocal {
    /** True while the calendar still holds the text [edit] was made from. */
    fun isCurrent(edit: StudyLocalEdit, calendarTitle: String, calendarDescription: String): Boolean =
        edit.baseTitle == calendarTitle && edit.baseDescription == calendarDescription

    /** [event] as the launcher shows it: with the phone's own ticks while they still apply. */
    fun apply(event: StudyEvent, edit: StudyLocalEdit?): StudyEvent =
        if (edit != null && isCurrent(edit, event.rawTitle, event.description)) event.copy(rawTitle = edit.title, description = edit.description) else event

    /**
     * Focused minutes from [startedAt] to [until], rounded to the nearest minute. A timer started
     * on a block runs to the end of that block (5.3): when [blockEnd] is known, time after it
     * does not count. At most 12 hours.
     */
    fun focusMinutes(startedAt: Long, until: Long, blockEnd: Long = 0): Int {
        val stop = if (blockEnd > startedAt) minOf(until, blockEnd) else until
        return ((stop - startedAt + 30_000) / 60_000).toInt().coerceIn(0, 12 * 60)
    }

    /** True when a focus timer on a block ending at [blockEnd] has run out at [now]. */
    fun focusOver(blockEnd: Long, now: Long): Boolean = blockEnd > 0 && now >= blockEnd

    // ---- the phone's copies, as text for preferences (no JSON library, so it is tested on the JVM) ----

    private const val FIELD = '\u001F'
    private const val RECORD = '\u001E'

    private fun clean(text: String) = text.replace(FIELD, ' ').replace(RECORD, ' ')

    fun encode(edits: Map<String, StudyLocalEdit>): String = edits.entries.joinToString(RECORD.toString()) { (key, e) ->
        listOf(key, e.baseTitle, e.baseDescription, e.title, e.description, e.savedAt.toString()).joinToString(FIELD.toString()) { clean(it) }
    }

    fun decode(text: String?): MutableMap<String, StudyLocalEdit> {
        val out = LinkedHashMap<String, StudyLocalEdit>()
        if (text.isNullOrEmpty()) return out
        for (record in text.split(RECORD)) {
            val f = record.split(FIELD)
            if (f.size != 6) continue
            out[f[0]] = StudyLocalEdit(f[1], f[2], f[3], f[4], f[5].toLongOrNull() ?: 0)
        }
        return out
    }
}
