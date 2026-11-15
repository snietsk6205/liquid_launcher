package dev.liquid.launcher.core.data

/** Whether a block or task is still open. Written as a mark in front of the event's title. */
enum class StudyStatus { Open, Done, Skipped }

/** One checklist line in a block's description. [index] counts the checklist lines from 0. */
data class StudyTask(val index: Int, val text: String, val done: Boolean)

/** An event's title without its status mark: "Subject: topic". */
data class StudyTitle(val status: StudyStatus, val subject: String, val topic: String) {
    /** The title as written, without the mark. */
    val text: String get() = if (topic.isEmpty()) subject else "$subject: $topic"
}

/** What the launcher's status line at the end of a description says. */
data class StudyNote(val doneAt: String? = null, val skipped: Boolean = false, val focusMinutes: Int = 0)

/**
 * The conventions that turn calendar events into study blocks and tasks, and back
 * (`docs/05-study-integration.md`, format version 1). Pure text handling, so it is tested
 * without a phone.
 *
 * The launcher only ever changes three things in an event: the mark in front of the title,
 * the letter inside a task's brackets, and its own status line at the end of the description.
 */
object StudyFormat {
    const val DONE_MARK = "✓"
    const val SKIPPED_MARK = "✗"
    const val STATUS_PREFIX = "— launcher:"

    private val doneMarks = listOf("✓", "✔", "✅", "☑")
    private val skippedMarks = listOf("✗", "✘", "❌")

    fun parseTitle(raw: String): StudyTitle {
        var text = raw.trim()
        var status = StudyStatus.Open
        doneMarks.firstOrNull { text.startsWith(it) }?.let {
            status = StudyStatus.Done
            text = text.removePrefix(it).trim()
        }
        if (status == StudyStatus.Open) {
            skippedMarks.firstOrNull { text.startsWith(it) }?.let {
                status = StudyStatus.Skipped
                text = text.removePrefix(it).trim()
            }
        }
        val colon = text.indexOf(':')
        // "Subject: topic". A colon far into the title, or one inside a time ("at 14:30"), is not a subject.
        return if (colon in 1..40 && colon < text.length - 1 && !text[colon + 1].isDigit()) {
            StudyTitle(status, text.substring(0, colon).trim(), text.substring(colon + 1).trim())
        } else {
            StudyTitle(status, text.trimEnd(':').trim(), "")
        }
    }

    /** [raw] with the mark for [status] in front (and any earlier mark removed). */
    fun withStatus(raw: String, status: StudyStatus): String {
        var text = raw.trim()
        (doneMarks + skippedMarks).firstOrNull { text.startsWith(it) }?.let { text = text.removePrefix(it).trim() }
        return when (status) {
            StudyStatus.Done -> "$DONE_MARK $text"
            StudyStatus.Skipped -> "$SKIPPED_MARK $text"
            StudyStatus.Open -> text
        }
    }

    // ---- descriptions ----

    private val breaks = Regex("(?i)<br\\s*/?>|</p>|</div>|</li>|</h[1-6]>")
    private val tags = Regex("<[^>]+>")

    /** True when the description is HTML (edited in Google Calendar's web page). */
    fun isHtml(description: String): Boolean = Regex("(?i)<br|<p[ >]|<div|<li|<ul|<span|<b>|<a ").containsMatchIn(description)

    /** The description as plain lines; HTML is flattened. */
    fun lines(description: String): List<String> {
        val plain = if (isHtml(description)) {
            description.replace(breaks, "\n").replace(tags, "")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        } else {
            description
        }
        return plain.split('\n').map { it.trim() }
    }

    private val taskLine = Regex("^(?:[-*•]\\s*)?(?:\\[([ xX]?)]|([☐☑☒]))\\s*(.*)$")

    /** The checklist lines of a description, in order. */
    fun tasks(description: String): List<StudyTask> {
        val out = ArrayList<StudyTask>()
        for (line in lines(description)) {
            val match = taskLine.find(line) ?: continue
            val box = match.groupValues[1]
            val symbol = match.groupValues[2]
            val done = box.equals("x", ignoreCase = true) || symbol == "☑" || symbol == "☒"
            out += StudyTask(out.size, match.groupValues[3].trim(), done)
        }
        return out
    }

    /** Everything that is neither a task nor the launcher's status line, as text. */
    fun notes(description: String): String =
        lines(description).filter { it.isNotEmpty() && taskLine.find(it) == null && !it.startsWith(STATUS_PREFIX) }.joinToString("\n")

    private val link = Regex("https?://[^\\s<>\"']+")

    /** The first web link in [text], if any. */
    fun firstLink(text: String): String? = link.find(text)?.value?.trimEnd('.', ',', ')', ';')

    // A checkbox at the start of a line: after the beginning, a line break or an HTML tag.
    private val rawBox = Regex("(?:^|(?<=\\n)|(?<=>))([ \\t]*(?:[-*•][ \\t]*)?)(?:\\[([ xX]?)]|([☐☑☒]))")

    /**
     * [description] with task [index] ticked or unticked. Only the box changes; the wording,
     * the notes and any HTML stay as they are. Returns null when that task cannot be found.
     */
    fun setTask(description: String, index: Int, done: Boolean): String? {
        val wanted = tasks(description).getOrNull(index) ?: return null
        val boxes = rawBox.findAll(description).toList()
        // Normally the boxes in the text are the tasks, in the same order; otherwise look for the task's words.
        val match = boxes.getOrNull(index).takeIf { boxes.size == tasks(description).size }
            ?: boxes.firstOrNull { box ->
                val after = lines(description.substring(box.range.last + 1)).firstOrNull().orEmpty()
                after.startsWith(wanted.text.take(24))
            }
            ?: return null
        val replacement = match.groupValues[1] + if (match.groupValues[3].isNotEmpty()) (if (done) "☑" else "☐") else (if (done) "[x]" else "[ ]")
        return description.replaceRange(match.range, replacement)
    }

    // ---- the launcher's status line ----

    private val statusLine = Regex("(?:\\r?\\n|(?i)<br\\s*/?>)?[ \\t]*" + Regex.escape(STATUS_PREFIX) + "[^\\n<]*")

    fun note(description: String): StudyNote {
        val line = lines(description).lastOrNull { it.startsWith(STATUS_PREFIX) } ?: return StudyNote()
        return StudyNote(
            doneAt = Regex("done (\\d{1,2}:\\d{2})").find(line)?.groupValues?.get(1),
            skipped = "skipped" in line,
            focusMinutes = Regex("focus (\\d+) min").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
        )
    }

    /** The status line for [note] and the tasks' state; null when there is nothing to say. */
    fun statusText(note: StudyNote, tasksDone: Int, tasksTotal: Int): String? {
        val parts = ArrayList<String>()
        when {
            note.doneAt != null -> parts += "done ${note.doneAt}"
            note.skipped -> parts += "skipped"
        }
        if (tasksTotal > 0 && (tasksDone > 0 || note.doneAt != null)) parts += "$tasksDone/$tasksTotal tasks"
        if (note.focusMinutes > 0) parts += "focus ${note.focusMinutes} min"
        return if (parts.isEmpty()) null else "$STATUS_PREFIX " + parts.joinToString(" · ")
    }

    /** [description] with its status line replaced by [line] (removed when null). Always the last line. */
    fun withStatusLine(description: String, line: String?): String {
        val cleaned = description.replace(statusLine, "").trimEnd()
        if (line == null) return cleaned
        if (cleaned.isEmpty()) return line
        return cleaned + (if (isHtml(cleaned)) "<br>" else "\n") + line
    }

    /** [description] after a change: the status line brought up to date with the tasks and [note]. */
    fun refreshed(description: String, note: StudyNote): String {
        val all = tasks(description)
        return withStatusLine(description, statusText(note, all.count { it.done }, all.size))
    }
}

/** A study block (a timed event) or a task (an all-day event), as read from the calendar. */
data class StudyEvent(
    val eventId: Long,
    val calendarId: Long,
    /** Start and end in milliseconds; for a task, midnight UTC of its first day and of the day after its last. */
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val rawTitle: String,
    val description: String = "",
    val location: String = "",
    /** ARGB of the event (or its calendar). */
    val color: Int = 0,
    /** One occurrence of a repeating event: changes are written as an exception. */
    val recurring: Boolean = false,
) {
    val title: StudyTitle get() = StudyFormat.parseTitle(rawTitle)
    val status: StudyStatus get() = title.status
    val tasks: List<StudyTask> get() = StudyFormat.tasks(description)
    val notes: String get() = StudyFormat.notes(description)
    val link: String? get() = StudyFormat.firstLink(location) ?: StudyFormat.firstLink(description)
    val note: StudyNote get() = StudyFormat.note(description)
    val key: String get() = "$eventId@$begin"
}

/** What the study companion shows for one moment in time. */
data class StudyDay(
    /** Today's blocks, in time order. */
    val blocks: List<StudyEvent> = emptyList(),
    /** Today's tasks (all-day events of the task calendars). */
    val tasks: List<StudyEvent> = emptyList(),
    /** Tasks of earlier days that are still open, newest first. */
    val overdue: List<StudyEvent> = emptyList(),
    /** Tomorrow's blocks, in time order (0.6.4: all of them, for the Tomorrow peek in Today). */
    val tomorrowBlocks: List<StudyEvent> = emptyList(),
    /** Tomorrow's tasks. */
    val tomorrowTasks: List<StudyEvent> = emptyList(),
    /** Edits of study events on the phone that the calendar sync has not sent to the account yet. */
    val waitingToSync: Int = 0,
) {
    val isEmpty: Boolean get() = blocks.isEmpty() && tasks.isEmpty() && overdue.isEmpty()

    /** The first block of tomorrow, if any. */
    val tomorrow: StudyEvent? get() = tomorrowBlocks.firstOrNull()

    /** Blocks of today that have ended and are neither done nor skipped, latest first. */
    fun unmarked(now: Long): List<StudyEvent> = blocks.filter { it.end <= now && it.status == StudyStatus.Open }.sortedByDescending { it.end }

    /** The block running at [now]: the latest one that has started and not ended, open ones first. */
    fun current(now: Long): StudyEvent? {
        val running = blocks.filter { now >= it.begin && now < it.end }
        return running.lastOrNull { it.status == StudyStatus.Open } ?: running.lastOrNull()
    }

    /** The next block that starts after [now] today. */
    fun next(now: Long): StudyEvent? = blocks.firstOrNull { it.begin > now }

    val openTasks: Int get() = tasks.count { it.status == StudyStatus.Open } + overdue.size
}
