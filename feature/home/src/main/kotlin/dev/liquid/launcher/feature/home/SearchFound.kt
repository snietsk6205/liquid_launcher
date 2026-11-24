package dev.liquid.launcher.feature.home

import android.content.ContentUris
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.liquid.launcher.core.data.Found
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** 0.13.0 (D179): opens what search found (a contact's card, an event in the calendar app). */
internal fun contactIntent(found: Found.Contact): Intent =
    Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(found.id, found.lookup))

internal fun eventIntent(id: Long, begin: Long, end: Long): Intent =
    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)

/** "Today · 14:00", "Tomorrow", "Tue 25 Nov · 09:30", "12 Mar 2025" (another year). All-day events have no time. */
internal fun eventWhen(begin: Long, allDay: Boolean, today: LocalDate = LocalDate.now()): String {
    val zone = if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(begin).atZone(zone)
    val date = at.toLocalDate()
    val locale = Locale.getDefault()
    val day = when (ChronoUnit.DAYS.between(today, date)) {
        0L -> "Today"
        1L -> "Tomorrow"
        -1L -> "Yesterday"
        else -> if (date.year == today.year) date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
        else date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
    }
    return if (allDay) day else day + " · " + at.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm", locale))
}

@Composable
private fun FoundIcon(icon: Int, background: Color = Color.White.copy(alpha = 0.12f)) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(background), contentAlignment = Alignment.Center) {
        LauncherIcon(icon, contentDescription = null, tint = Color.White, size = 22.dp)
    }
}

/** A contact's photo, else their initials on a colour of their own. */
@Composable
private fun ContactAvatar(contact: Found.Contact) {
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(null, contact.photo) {
        val uri = contact.photo ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            }.getOrNull()
        }
    }
    val shown = photo
    if (shown != null) {
        Image(shown, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(CircleShape))
        return
    }
    val hue = ((contact.name.hashCode() % 360) + 360) % 360
    val tint = Color.hsl(hue.toFloat(), 0.45f, 0.42f)
    val initials = contact.name.split(' ', '-').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
    Box(Modifier.size(40.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
        Text(initials, style = TextStyle(fontFamily = HomeTheme.font, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

/** The row of one thing search found besides apps, in search's own white text. */
@Composable
internal fun RowScope.FoundRow(found: Found, style: TextStyle) {
    val detail = style.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
    @Composable
    fun lines(title: String, sub: String, titleMath: Boolean = false) {
        Column(Modifier.weight(1f)) {
            if (titleMath) MathText(title, style.copy(fontWeight = FontWeight.Medium))
            else Text(title, style = style.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, style = detail, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    when (found) {
        is Found.Contact -> {
            ContactAvatar(found)
            Spacer(Modifier.width(14.dp))
            lines(found.name, "Contact")
        }
        is Found.Event -> {
            val color = if (found.color != 0) Color(found.color or 0xFF000000.toInt()) else HomeTheme.accent
            FoundIcon(LauncherIcons.Calendar, color.copy(alpha = 0.45f))
            Spacer(Modifier.width(14.dp))
            lines(found.title, listOf(eventWhen(found.begin, found.allDay), found.place).filter { it.isNotBlank() }.joinToString(" · "))
        }
        is Found.Note -> {
            FoundIcon(LauncherIcons.Edit)
            Spacer(Modifier.width(14.dp))
            lines(found.text, "Note · " + eventWhen(found.begin, allDay = false))
        }
        is Found.Card -> {
            FoundIcon(LauncherIcons.Layers, HomeTheme.accent.copy(alpha = 0.35f))
            Spacer(Modifier.width(14.dp))
            lines(found.card.q, "Flashcard" + if (found.card.module.isNotBlank()) " · " + found.card.module else "", titleMath = true)
        }
        is Found.Option -> {
            FoundIcon(LauncherIcons.Settings)
            Spacer(Modifier.width(14.dp))
            lines(found.title, "Settings › " + found.path)
        }
        Found.AllowContacts -> {
            FoundIcon(LauncherIcons.Chat)
            Spacer(Modifier.width(14.dp))
            lines("Find people here too", "Allow contacts: search reads them on the phone only")
        }
    }
}
