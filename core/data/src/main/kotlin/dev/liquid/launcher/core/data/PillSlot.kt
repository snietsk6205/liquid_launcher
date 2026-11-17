package dev.liquid.launcher.core.data

/** One item in the pill. Stored in settings as a short text ([encode]). */
sealed interface PillSlot {
    /** Search field look: icon and hint text. Takes the free width. */
    data object Search : PillSlot
    data object Date : PillSlot
    data object Time : PillSlot

    /** The study block running now (or next), from the study companion; tap opens Today (0.6.3). */
    data object Study : PillSlot

    /** Empty, flexible space. */
    data object Spacer : PillSlot
    data class Action(val action: LauncherAction) : PillSlot
    data class App(val ref: AppRef) : PillSlot

    fun encode(): String = when (this) {
        Search -> "search"
        Date -> "date"
        Time -> "time"
        Study -> "study"
        Spacer -> "spacer"
        is Action -> "action:${action.encode()}"
        is App -> "app:${ref.key}"
    }

    companion object {
        fun decode(text: String): PillSlot? = when {
            text == "search" -> Search
            text == "date" -> Date
            text == "time" -> Time
            text == "study" -> Study
            text == "spacer" -> Spacer
            text.startsWith("action:") -> LauncherAction.decode(text.removePrefix("action:"))
                .takeIf { it != LauncherAction.None }?.let { Action(it) }
            text.startsWith("app:") -> AppRef.parse(text.removePrefix("app:"))?.let { App(it) }
            else -> null
        }

        fun decodeAll(list: List<String>): List<PillSlot> = list.mapNotNull { decode(it) }
    }
}

/**
 * Tap, long press and swipes of one pill item (0.9.1). Null means the item's own default: its action for a
 * tap, the pill's long press, and the pill's swipes. Stored per item code ([PillSlot.encode]).
 */
data class SlotGestures(
    val tap: LauncherAction? = null,
    val longPress: LauncherAction? = null,
    val swipeUp: LauncherAction? = null,
    val swipeDown: LauncherAction? = null,
) {
    val isDefault: Boolean get() = tap == null && longPress == null && swipeUp == null && swipeDown == null

    companion object {
        /** One line per item: its code and the four actions, tab separated (empty = default). */
        fun decodeAll(lines: List<String>): Map<String, SlotGestures> = lines.mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 5 || parts[0].isBlank()) return@mapNotNull null
            fun action(text: String): LauncherAction? = text.trim().takeIf { it.isNotEmpty() }?.let { LauncherAction.decode(it) }
            parts[0] to SlotGestures(action(parts[1]), action(parts[2]), action(parts[3]), action(parts[4]))
        }.toMap()

        fun encodeAll(map: Map<String, SlotGestures>): List<String> = map.filterValues { !it.isDefault }.map { (slot, g) ->
            listOf(slot, g.tap?.encode().orEmpty(), g.longPress?.encode().orEmpty(), g.swipeUp?.encode().orEmpty(), g.swipeDown?.encode().orEmpty())
                .joinToString("\t")
        }
    }
}
