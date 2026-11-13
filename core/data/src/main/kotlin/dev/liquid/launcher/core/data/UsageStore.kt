package dev.liquid.launcher.core.data

import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar
import kotlin.math.ln

/** Counts launches made through the launcher. Nothing here needs a permission. */
class UsageStore(private val store: JsonStore<Map<String, Usage>>) {
    val usage: StateFlow<Map<String, Usage>> = store.value

    fun record(key: String, now: Long = System.currentTimeMillis()) {
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        store.update { all ->
            val old = all[key] ?: Usage()
            val hours = (if (old.hours.size == 24) old.hours else List(24) { 0 }).toMutableList()
            hours[hour] = hours[hour] + 1
            all + (key to Usage(count = old.count + 1, last = now, hours = hours))
        }
    }

    fun count(key: String): Int = store.get()[key]?.count ?: 0

    /**
     * How likely this app is wanted right now: overall use, use around this hour of day,
     * and how recently it was opened.
     */
    fun score(key: String, now: Long = System.currentTimeMillis()): Double {
        val usage = store.get()[key] ?: return 0.0
        if (usage.count == 0) return 0.0
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        val nearHour = if (usage.hours.size == 24) {
            (usage.hours[hour] + 0.5 * usage.hours[(hour + 23) % 24] + 0.5 * usage.hours[(hour + 1) % 24]) / usage.count
        } else {
            0.0
        }
        val ageDays = (now - usage.last).coerceAtLeast(0) / 86_400_000.0
        return ln(1.0 + usage.count) + 2.0 * nearHour + 1.0 / (1.0 + ageDays)
    }
}
