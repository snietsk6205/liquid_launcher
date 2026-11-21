package dev.liquid.launcher.core.data

/**
 * C5, notification triage (0.9.16): which notifications get through while a study block runs. Everything else
 * waits (it is snoozed until the block ends, so nothing is lost) and is summed up afterwards. Pure decisions,
 * tested without a phone; the notification listener applies them.
 */
object QuietRules {
    /** What the listener knows of one notification. */
    data class Incoming(
        val pkg: String,
        val category: String? = null,
        /** Ongoing or a foreground service: a call in progress, music, navigation, a download. */
        val ongoing: Boolean = false,
        val title: String = "",
        val text: String = "",
        /** Gmail puts the account the mail is for here. */
        val subText: String = "",
        /** Names of the people the notification is about (Notification.EXTRA_PEOPLE_LIST). */
        val people: List<String> = emptyList(),
        /** The launcher's tags of the app that posted it. */
        val tags: Set<String> = emptySet(),
    )

    /** Categories that are never held: calls, alarms, the system's own messages. */
    private val alwaysCategories = setOf("call", "alarm", "sys", "navigation", "transport", "stopwatch", "service")

    /** Packages that are never held: the system itself. */
    private val alwaysPackages = setOf("android", "com.android.systemui", "com.android.phone", "com.android.server.telecom")

    /** The words of a comma-separated setting, trimmed, without empty ones. */
    fun list(setting: String): List<String> = setting.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * True when [n] gets through: the system, calls and alarms, anything ongoing, the launcher itself, an app with
     * one of [tags], a message about one of [people] (by name, ignoring case), or mail for one of [emails] (an
     * address or its end, such as "@school.tn").
     */
    fun letThrough(n: Incoming, people: List<String>, emails: List<String>, tags: List<String>, ownPackage: String): Boolean {
        if (n.ongoing) return true
        if (n.pkg == ownPackage || n.pkg in alwaysPackages) return true
        if (n.category != null && n.category in alwaysCategories) return true
        if (tags.any { t -> n.tags.any { it.equals(t, ignoreCase = true) } }) return true
        val names = listOf(n.title) + n.people
        if (people.any { person -> names.any { mentions(it, person) } }) return true
        val mail = listOf(n.subText, n.text, n.title)
        if (emails.any { address -> address.length >= 3 && mail.any { it.contains(address, ignoreCase = true) } }) return true
        return false
    }

    /** True when [text] names [person]: the whole name, not inside another word ("Ali" is not in "Alice"). */
    private fun mentions(text: String, person: String): Boolean {
        if (person.length < 2) return false
        var from = 0
        while (true) {
            val at = text.indexOf(person, from, ignoreCase = true)
            if (at < 0) return false
            val before = if (at == 0) ' ' else text[at - 1]
            val afterIndex = at + person.length
            val after = if (afterIndex >= text.length) ' ' else text[afterIndex]
            if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
            from = at + 1
        }
    }

    /** "WhatsApp 6 · Instagram 5 · Gmail 3" from the held notifications' app names, most first. */
    fun summary(held: List<String>, max: Int = 4): String {
        val counts = held.groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        val shown = counts.take(max).joinToString(" · ") { "${it.key} ${it.value}" }
        val rest = counts.drop(max).sumOf { it.value }
        return if (rest > 0) "$shown · others $rest" else shown
    }
}
