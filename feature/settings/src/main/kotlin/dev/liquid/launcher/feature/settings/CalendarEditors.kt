package dev.liquid.launcher.feature.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.data.PhoneCalendar
import dev.liquid.launcher.core.data.PhoneCalendars
import dev.liquid.launcher.core.data.SchoolDate
import dev.liquid.launcher.core.data.SchoolYear
import dev.liquid.launcher.core.data.TunisiaHolidays
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.observe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Settings › Calendar (0.16.0, D209–D211): the editors of what is not a single option: which of the phone's calendars
 * the calendar shows and in what colour, the school year (built in, editable, deletable, pinned under the clock) and
 * the holidays moved or hidden by hand.
 */

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())

/** Colours offered for a calendar (Material's, readable on light and dark glass). */
private val calendarSwatches = listOf(
    0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1, 0xFF3949AB, 0xFF1E88E5, 0xFF039BE5, 0xFF00897B,
    0xFF43A047, 0xFF7CB342, 0xFFFDD835, 0xFFFB8C00, 0xFFF4511E, 0xFF6D4C41, 0xFF757575, 0xFF546E7A,
).map { it.toInt() }

@Composable
internal fun CalendarsEditor(store: SettingsStore) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val allowed = remember(tick) { PhoneCalendars.canRead(context) }
    val calendars by produceState(emptyList<PhoneCalendar>(), tick) { value = withContext(Dispatchers.IO) { PhoneCalendars.calendars(context) } }
    val hiddenLines by store.observe(LauncherSettings.CalendarHidden)
    val colourLines by store.observe(LauncherSettings.CalendarColours)
    val hidden = remember(hiddenLines) { PhoneCalendars.parseHidden(hiddenLines) }
    val colours = remember(colourLines) { PhoneCalendars.parseColours(colourLines) }
    var choosing by remember { mutableStateOf<PhoneCalendar?>(null) }
    SectionHeader("Calendars and colours")
    if (!allowed) {
        InfoCard {
            BodyText("The calendar shows your events once it may read the phone's calendars. It only reads them; a new event is added in your calendar app.")
            TextButton(onClick = { ask.launch(Manifest.permission.READ_CALENDAR) }) { Text("Allow reading your calendars") }
        }
        return
    }
    if (calendars.isEmpty()) {
        InfoCard { BodyText("No calendar on this phone yet.") }
        return
    }
    GroupCards {
        for (cal in calendars) {
            val shown = cal.id !in hidden
            val colour = colours[cal.id] ?: cal.color
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { store.set(LauncherSettings.CalendarHidden, toggled(hidden, cal.id).map { it.toString() }) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(colour))
                        .border(2.dp, if (cal.id in colours) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else Color.Transparent, CircleShape)
                        .clickable { choosing = cal },
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(cal.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BodyText(cal.account + if (!cal.visible) " · hidden in your calendar app" else "")
                }
                Switch(
                    checked = shown && cal.visible, enabled = cal.visible,
                    onCheckedChange = { store.set(LauncherSettings.CalendarHidden, toggled(hidden, cal.id).map { it.toString() }) },
                    colors = SwitchDefaults.colors(checkedTrackColor = screenAccent(), checkedBorderColor = screenAccent(), checkedThumbColor = Color.White),
                )
            }
        }
    }
    BodyText("Tap a colour to change it. The Calendar widget follows these when it uses the calendar's settings.", Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
    choosing?.let { cal ->
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text(cal.name) },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in calendarSwatches) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .clickable {
                                    store.set(LauncherSettings.CalendarColours, PhoneCalendars.encodeColours(colours + (cal.id to c)))
                                    choosing = null
                                },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    store.set(LauncherSettings.CalendarColours, PhoneCalendars.encodeColours(colours - cal.id))
                    choosing = null
                }) { Text("The calendar's own") }
            },
            dismissButton = { TextButton(onClick = { choosing = null }) { Text("Cancel") } },
        )
    }
}

private fun toggled(set: Set<Long>, id: Long): Set<Long> = if (id in set) set - id else set + id

@Composable
internal fun SchoolYearEditor(store: SettingsStore) {
    val text by store.observe(LauncherSettings.CalendarSchoolYear)
    val dates = remember(text) { SchoolYear.sorted(SchoolYear.parse(text)) }
    val today = remember { LocalDate.now() }
    var editing by remember { mutableStateOf<SchoolDate?>(null) }
    fun save(list: List<SchoolDate>) = store.set(LauncherSettings.CalendarSchoolYear, SchoolYear.encode(list))
    SectionHeader("School year")
    InfoCard {
        BodyText(
            "ESSAI 2026–27 is built in. The school calls these dates indicative, so change, move or delete any of them; ≈ marks a " +
                "date given as \"about\". Pin one and its countdown shows on the line under the clock (one with the exam there " +
                "when it is the same day). They never switch the exam-week layout on.",
        )
    }
    if (dates.isEmpty()) {
        InfoCard { BodyText("No dates. Add one, or bring back the ones the launcher came with.") }
    } else {
        GroupCards {
            for (d in dates) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editing = d }
                        .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(d.title.ifBlank { d.label }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val span = d.startDate?.let { s ->
                            val e = d.endDate
                            (if (d.approx) "≈ " else "") + s.format(dayFormat) + if (e != null && e != s) " – " + e.format(dayFormat) else ""
                        } ?: "No date"
                        BodyText(span + (SchoolYear.countdown(d, today)?.let { " · $it" } ?: " · over"))
                    }
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { save(SchoolYear.togglePin(dates, d.id)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        LauncherIcon(
                            LauncherIcons.Pin, contentDescription = if (d.pinned) "Unpin ${d.label}" else "Pin ${d.label} under the clock",
                            tint = if (d.pinned) screenAccent() else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        )
                    }
                }
            }
        }
    }
    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        TextButton(onClick = { editing = SchoolDate(SchoolYear.newId(dates, System.currentTimeMillis()), "", start = today.toString()) }) { Text("Add a date") }
        TextButton(onClick = { save(SchoolYear.restoreBuiltIn(dates)) }) { Text("Bring back the built-in dates") }
    }
    editing?.let { d ->
        SchoolDateDialog(
            d,
            isNew = dates.none { it.id == d.id },
            onSave = {
                save(SchoolYear.put(dates, it))
                editing = null
            },
            onDelete = {
                save(SchoolYear.remove(dates, d.id))
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun SchoolDateDialog(start: SchoolDate, isNew: Boolean, onSave: (SchoolDate) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var d by remember(start.id) { mutableStateOf(start) }
    fun pick(current: LocalDate?, done: (LocalDate) -> Unit) {
        val at = current ?: LocalDate.now()
        android.app.DatePickerDialog(context, { _, y, m, day -> done(LocalDate.of(y, m + 1, day)) }, at.year, at.monthValue - 1, at.dayOfMonth).show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New date" else d.title.ifBlank { "Date" }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(d.title, { d = d.copy(title = it) }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(d.short, { d = d.copy(short = it) }, label = { Text("In countdowns (\"DS P2\")") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("From", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(52.dp))
                    TextButton(onClick = { pick(d.startDate) { d = d.copy(start = it.toString()) } }) { Text(d.startDate?.format(dayFormat) ?: "Choose") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Until", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(52.dp))
                    TextButton(onClick = { pick(d.endDate ?: d.startDate) { d = d.copy(end = it.toString()) } }) {
                        Text(if (d.end.isBlank()) "One day" else d.endDate?.format(dayFormat) ?: "Choose")
                    }
                    if (d.end.isNotBlank()) TextButton(onClick = { d = d.copy(end = "") }) { Text("One day") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((kind, label) in SchoolYear.kinds) {
                        val on = d.kind == kind
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (on) screenAccent().copy(alpha = 0.22f) else Color.Transparent)
                                .border(1.dp, if (on) screenAccent().copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .clickable { d = d.copy(kind = kind) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                SwitchLine("About (≈): the school gave it with \"~\"", d.approx) { d = d.copy(approx = it) }
                SwitchLine("Countdown under the clock", d.pinned) { d = d.copy(pinned = it) }
            }
        },
        confirmButton = {
            TextButton(enabled = d.startDate != null && (d.title.isNotBlank() || d.short.isNotBlank()), onClick = { onSave(d) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun SwitchLine(text: String, on: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { change(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = change, colors = SwitchDefaults.colors(checkedTrackColor = screenAccent(), checkedBorderColor = screenAccent(), checkedThumbColor = Color.White))
    }
}

@Composable
internal fun HolidayChangesEditor(store: SettingsStore) {
    val text by store.observe(LauncherSettings.CalendarHolidayChanges)
    val changes = remember(text) { TunisiaHolidays.parseChanges(text) }
    SectionHeader("Holidays set by hand")
    if (changes.isEmpty()) {
        InfoCard {
            BodyText(
                "None. The Islamic holidays come from the Umm al-Qura tables with ≈. When Tunisia announces a different day, tap " +
                    "that day in the calendar: the holiday in its agenda moves a day earlier or later, or hides.",
            )
        }
        return
    }
    GroupCards {
        for ((id, value) in changes.entries.sortedBy { it.key }) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(TunisiaHolidays.describe(id), style = MaterialTheme.typography.bodyLarge)
                    BodyText(
                        if (value == TunisiaHolidays.HIDDEN) "Hidden"
                        else "On " + (runCatching { LocalDate.parse(value).format(dayFormat) }.getOrNull() ?: value),
                    )
                }
                TextButton(onClick = { store.set(LauncherSettings.CalendarHolidayChanges, TunisiaHolidays.encodeChanges(TunisiaHolidays.reset(changes, id))) }) {
                    Text("Put back")
                }
            }
        }
    }
}
