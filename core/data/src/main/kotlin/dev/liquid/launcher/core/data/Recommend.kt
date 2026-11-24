package dev.liquid.launcher.core.data

/**
 * 0.14.0 (D185, Med: "add some recommended tasks"): what Today recommends, from two sources (Med chose both): the
 * tasks Claude writes at the end of each study chat (launcher-data "recommended", up to three) and what the launcher
 * knows by itself: the flashcards due, and an exam or deadline within two weeks other than the one already in Today's
 * header. In order: a deadline within three days, Claude's tasks, the flashcards, a deadline within two weeks; at most
 * [MAX]. Pure, so it is tested here.
 */
object Recommend {
    enum class Kind { Claude, Cards, Exam }

    /** One recommended task: [key] names it for its tick (stable across reads), [urgent] colours it. */
    data class Item(val key: String, val text: String, val kind: Kind, val urgent: Boolean = false)

    const val MAX = 4
    private const val DAY = 86_400_000L

    /**
     * Today's recommended tasks. [exams] soonest first (the day's exams, from launcher-data or the class calendars);
     * [headerExam] is the one Today's header already counts down (not repeated); [due] the flashcards due today.
     */
    fun items(feed: StudyFeed?, exams: List<StudyEvent>, unconfirmed: Set<String>, due: Int, now: Long, headerExam: String? = null): List<Item> {
        val soon = exams.filter { it.begin > now && it.key != headerExam && (it.begin - now) / DAY <= DayLine.EXAM_DAYS }
        fun examItem(e: StudyEvent): Item {
            val days = (e.begin - now) / DAY
            val left = if (days <= DayLine.URGENT_DAYS) "${((e.begin - now) / 3_600_000L).coerceAtLeast(1)} h" else "$days d"
            val about = if (e.key in unconfirmed) "≈" else ""
            return Item("exam:" + e.key, e.title.text.ifBlank { e.rawTitle } + " in " + about + left, Kind.Exam, urgent = days <= DayLine.URGENT_DAYS)
        }
        val out = ArrayList<Item>()
        soon.firstOrNull { (it.begin - now) / DAY <= DayLine.URGENT_DAYS }?.let { out += examItem(it) }
        feed?.tasks.orEmpty().forEach { out += Item("claude:" + it.lowercase(), it, Kind.Claude) }
        if (due > 0) out += Item("cards", if (due == 1) "1 flashcard due" else "$due flashcards due", Kind.Cards)
        soon.firstOrNull { it.key !in out.map { item -> item.key.removePrefix("exam:") } }?.let { out += examItem(it) }
        return out.distinctBy { it.key }.take(MAX)
    }

    /** Ticks are kept per day as "yyyy-MM-dd|key": the ones of [today] only. */
    fun ticked(stored: List<String>, today: String): Set<String> =
        stored.filter { it.startsWith("$today|") }.map { it.substringAfter('|') }.toSet()

    /** [stored] with [key] ticked or not on [today]; other days' ticks are dropped. */
    fun tick(stored: List<String>, today: String, key: String, done: Boolean): List<String> {
        val kept = stored.filter { it.startsWith("$today|") && it != "$today|$key" }
        return if (done) kept + "$today|$key" else kept
    }
}
