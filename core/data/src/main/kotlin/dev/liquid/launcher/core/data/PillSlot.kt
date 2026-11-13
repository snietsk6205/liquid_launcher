package dev.liquid.launcher.core.data

/** One item in the pill. Stored in settings as a short text ([encode]). */
sealed interface PillSlot {
    /** Search field look: icon and hint text. Takes the free width. */
    data object Search : PillSlot
    data object Date : PillSlot
    data object Time : PillSlot

    /** Empty, flexible space. */
    data object Spacer : PillSlot
    data class Action(val action: LauncherAction) : PillSlot
    data class App(val ref: AppRef) : PillSlot

    fun encode(): String = when (this) {
        Search -> "search"
        Date -> "date"
        Time -> "time"
        Spacer -> "spacer"
        is Action -> "action:${action.encode()}"
        is App -> "app:${ref.key}"
    }

    companion object {
        fun decode(text: String): PillSlot? = when {
            text == "search" -> Search
            text == "date" -> Date
            text == "time" -> Time
            text == "spacer" -> Spacer
            text.startsWith("action:") -> LauncherAction.decode(text.removePrefix("action:"))
                .takeIf { it != LauncherAction.None }?.let { Action(it) }
            text.startsWith("app:") -> AppRef.parse(text.removePrefix("app:"))?.let { App(it) }
            else -> null
        }

        fun decodeAll(list: List<String>): List<PillSlot> = list.mapNotNull { decode(it) }
    }
}
