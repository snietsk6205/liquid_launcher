package dev.liquid.launcher.core.data

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * 0.16.0 (D210): the Islamic (Hijri) calendar, from Java's Umm al-Qura tables. Tunisia fixes its Islamic dates by
 * sighting the moon, so they can be a day off the tables: [shift] moves them (Settings › Calendar), and the calendar
 * marks them with ≈ until a date is set by hand.
 */
object Hijri {
    data class Date(val year: Int, val month: Int, val day: Int)

    val months = listOf(
        "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani", "Jumada al-Ula", "Jumada al-Akhira",
        "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'da", "Dhu al-Hijja",
    )

    /** The Hijri date of [date], with the Islamic dates [shift] days later than the tables; null outside the tables. */
    fun of(date: LocalDate, shift: Int = 0): Date? = runCatching {
        val h = HijrahDate.from(date.minusDays(shift.toLong()))
        Date(h.get(ChronoField.YEAR), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH))
    }.getOrNull()

    /** The day [year]-[month]-[day] (Hijri) falls on, [shift] days later than the tables; null when it does not exist. */
    fun toGregorian(year: Int, month: Int, day: Int, shift: Int = 0): LocalDate? = runCatching {
        LocalDate.from(HijrahDate.of(year, month, day)).plusDays(shift.toLong())
    }.getOrNull()

    /** "14 Jumada al-Ula 1448". */
    fun format(d: Date): String = "${d.day} ${months.getOrElse(d.month - 1) { "?" }} ${d.year}"
}

/** One day of a public holiday. [id] names the holiday's occurrence ("fitr.1448"), the same for each of its days. */
data class Holiday(
    val id: String,
    val name: String,
    val date: LocalDate,
    /** It follows the moon: ≈ unless [confirmed]. */
    val islamic: Boolean,
    /** Its date was set by hand (moved), so it is no longer an estimate. */
    val confirmed: Boolean = false,
    /** 1 for the first day; 2 for the second day of the Aïds. */
    val dayNumber: Int = 1,
) {
    /** "Aïd el-Fitr", "≈ Aïd el-Fitr (2nd day)". */
    val label: String get() = (if (islamic && !confirmed) "≈ " else "") + name + if (dayNumber > 1) " (day $dayNumber)" else ""
}

/**
 * Tunisia's public holidays (0.16.0, D210): the fixed ones of the 2021 decree (New Year, Independence, Martyrs, Labour,
 * Republic, Women, Evacuation, Revolution on 17 December; 14 January is no longer one) and the Islamic ones from the
 * Hijri tables (Ras el Am el Hijri, Mouled, two days of each Aïd). The government sometimes adds a day to an Aïd: a
 * holiday can be moved or hidden ([changes]: id → ISO date of its first day, or "hidden").
 */
object TunisiaHolidays {
    private data class Fixed(val id: String, val name: String, val month: Int, val day: Int)
    private data class Islamic(val id: String, val name: String, val month: Int, val day: Int, val days: Int)

    private val fixed = listOf(
        Fixed("newyear", "New Year's Day", 1, 1),
        Fixed("independence", "Independence Day", 3, 20),
        Fixed("martyrs", "Martyrs' Day", 4, 9),
        Fixed("labour", "Labour Day", 5, 1),
        Fixed("republic", "Republic Day", 7, 25),
        Fixed("women", "Women's Day", 8, 13),
        Fixed("evacuation", "Evacuation Day", 10, 15),
        Fixed("revolution", "Revolution Day", 12, 17),
    )

    private val islamic = listOf(
        Islamic("hijri", "Ras el Am el Hijri", 1, 1, 1),
        Islamic("mouled", "Mouled", 3, 12, 1),
        Islamic("fitr", "Aïd el-Fitr", 10, 1, 2),
        Islamic("idha", "Aïd el-Idha", 12, 10, 2),
    )

    const val HIDDEN = "hidden"

    private val changesSerializer = MapSerializer(String.serializer(), String.serializer())

    fun parseChanges(text: String): Map<String, String> =
        if (text.isBlank()) emptyMap() else runCatching { LauncherJson.decodeFromString(changesSerializer, text) }.getOrElse { emptyMap() }

    fun encodeChanges(changes: Map<String, String>): String = if (changes.isEmpty()) "" else LauncherJson.encodeToString(changesSerializer, changes)

    /** Every holiday day from [from] to [to] (both included), with [shift] for the Islamic ones and the [changes]. */
    fun between(from: LocalDate, to: LocalDate, shift: Int = 0, changes: Map<String, String> = emptyMap()): List<Holiday> {
        if (to.isBefore(from)) return emptyList()
        val out = ArrayList<Holiday>()
        fun add(id: String, name: String, first: LocalDate, days: Int, isIslamic: Boolean) {
            val change = changes[id]
            if (change == HIDDEN) return
            val moved = change?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val start = moved ?: first
            for (i in 0 until days) {
                val date = start.plusDays(i.toLong())
                if (!date.isBefore(from) && !date.isAfter(to)) out += Holiday(id, name, date, isIslamic, confirmed = moved != null, dayNumber = i + 1)
            }
        }
        // A moved holiday may leave its year by a day or two: look a year around.
        for (year in from.year - 1..to.year + 1) {
            for (f in fixed) add("${f.id}.$year", f.name, LocalDate.of(year, f.month, f.day), 1, false)
        }
        val first = Hijri.of(from.minusDays(40), shift)?.year
        val last = Hijri.of(to.plusDays(40), shift)?.year
        if (first != null && last != null) {
            for (h in first..last) for (i in islamic) {
                val date = Hijri.toGregorian(h, i.month, i.day, shift) ?: continue
                add("${i.id}.$h", i.name, date, i.days, true)
            }
        }
        return out.sortedWith(compareBy({ it.date }, { it.name }))
    }

    fun onDay(day: LocalDate, shift: Int = 0, changes: Map<String, String> = emptyMap()): List<Holiday> = between(day, day, shift, changes)

    /** [changes] with the holiday [h] moved by [days] (its first day). */
    fun move(changes: Map<String, String>, h: Holiday, days: Long): Map<String, String> {
        val first = h.date.minusDays((h.dayNumber - 1).toLong())
        return changes + (h.id to first.plusDays(days).toString())
    }

    fun hide(changes: Map<String, String>, h: Holiday): Map<String, String> = changes + (h.id to HIDDEN)

    fun reset(changes: Map<String, String>, id: String): Map<String, String> = changes - id

    /** "Aïd el-Fitr 1448", "New Year's Day 2027": a holiday's occurrence named by its [id] (for the list of changes). */
    fun describe(id: String): String {
        val kind = id.substringBefore('.')
        val year = id.substringAfter('.', "")
        val name = fixed.firstOrNull { it.id == kind }?.name ?: islamic.firstOrNull { it.id == kind }?.name ?: kind
        return if (year.isEmpty()) name else "$name $year"
    }
}
