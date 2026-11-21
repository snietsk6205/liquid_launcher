package dev.liquid.launcher.core.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsMap
import java.text.Normalizer

/*
 * 0.13.0 (D179, Med: "Search everything: Settings, contacts, calendar events, notes and your flashcards"). Besides apps,
 * search finds options of Settings, people, events of the calendars, the notes written for the evening check-in
 * ("Note: …" events) and the cards of the deck. Everything is read on the phone, nothing leaves it; contacts are read
 * only once allowed, from a row search offers once. Each source can be turned off (Settings → Search → More).
 */

/** One thing search found besides apps. */
sealed interface Found {
    val key: String

    /** An option (or an editor) of Settings; [target] opens its page on its tab ("page:pill:layout:pill.live.music"). */
    data class Option(val title: String, val path: String, val target: String) : Found {
        override val key: String get() = "option:$target"
    }

    data class Contact(val id: Long, val lookup: String, val name: String, val photo: String?) : Found {
        override val key: String get() = "contact:$id"
    }

    /** One occurrence of an event (the next one for a repeating event, else the latest). */
    data class Event(
        val id: Long,
        val title: String,
        val begin: Long,
        val end: Long,
        val allDay: Boolean,
        val color: Int,
        val place: String,
    ) : Found {
        override val key: String get() = "event:$id"
    }

    /** A note for the evening check-in: [text] without "Note:". */
    data class Note(val id: Long, val text: String, val begin: Long) : Found {
        override val key: String get() = "note:$id"
    }

    data class Card(val card: DeckCard) : Found {
        override val key: String get() = "card:" + card.id
    }

    /** Contacts would be searched once allowed (offered once, from search). */
    data object AllowContacts : Found {
        override val key: String get() = "allow-contacts"
    }
}

object Everything {
    /** What search looks in besides apps (Settings → Search → More). */
    data class Sources(
        val settings: Boolean = true,
        val contacts: Boolean = true,
        val events: Boolean = true,
        val notes: Boolean = true,
        val cards: Boolean = true,
    ) {
        val any: Boolean get() = settings || contacts || events || notes || cards
    }

    private val MARKS = Regex("\\p{Mn}+")

    /** Lower case without accents: "Révision" finds "revision" and the other way round. */
    fun normalize(text: String): String = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(MARKS, "")

    fun words(query: String): List<String> =
        normalize(query).split(' ', ',', '-', '\'', '’', '.', ':').filter { it.isNotBlank() }

    /** Every word of [words] is in one of [texts]. */
    fun matches(words: List<String>, vararg texts: String): Boolean {
        if (words.isEmpty()) return false
        val all = texts.joinToString(" ") { normalize(it) }
        return words.all { it in all }
    }

    /**
     * Whether [found] (found for an earlier, shorter query) still fits [query]: kept on screen while the new search
     * runs, so the rows do not flicker away and back at each letter.
     */
    fun stillMatches(found: Found, query: String): Boolean {
        val w = words(query)
        return when (found) {
            is Found.Option -> matches(w, found.title, found.path)
            is Found.Contact -> matches(w, found.name)
            is Found.Event -> matches(w, found.title, found.place)
            is Found.Note -> matches(w, found.text)
            is Found.Card -> matches(w, found.card.q, found.card.a, found.card.module)
            Found.AllowContacts -> true
        }
    }

    /** The cards of [deck] whose question, answer or module has every word of [query]. */
    fun cards(deck: Deck, query: String, limit: Int = 3): List<DeckCard> {
        val w = words(query)
        if (w.isEmpty()) return emptyList()
        return deck.cards.asSequence().filter { matches(w, it.q, it.a, it.module) }.take(limit).toList()
    }

    /**
     * Options of Settings with every word of [query] in their name, what they do, their words or their place (the
     * fuzzy settings search would find a setting for most names, so search only takes exact words), the page's own
     * editors first.
     */
    fun options(query: String, limit: Int = 3): List<Found.Option> {
        val w = words(query)
        if (w.isEmpty() || query.trim().length < 3) return emptyList()
        val out = ArrayList<Found.Option>()
        for (e in SettingsMap.searchEditors(query)) {
            val surface = SettingsMap.surface(e.surfaceId) ?: continue
            if (!matches(w, e.title, e.summary, e.keywords, surface.title)) continue
            out += Found.Option(e.title, "${surface.entry.title} › ${surface.title} › ${e.tab.title}", "page:${surface.id}:${e.tab.name.lowercase()}")
            if (out.size >= limit) return out
        }
        for (setting in LauncherSettings.search(query)) {
            val path = SettingsMap.path(setting)
            if (!matches(w, setting.searchText, path)) continue
            val surface = SettingsMap.surfaceOf(setting)
            val tab = SettingsMap.tabOf(setting)
            out += Found.Option(setting.title, path, "page:${surface.id}:${tab.name.lowercase()}:${setting.key}")
            if (out.size >= limit) break
        }
        return out
    }

    private fun allowed(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** Whether contacts may be read. */
    fun contactsAllowed(context: Context): Boolean = allowed(context, Manifest.permission.READ_CONTACTS)

    /** People whose name starts a word with [query] (the contacts' own filter), at most [limit]. */
    fun contacts(context: Context, query: String, limit: Int = 4): List<Found.Contact> {
        if (!contactsAllowed(context)) return emptyList()
        val out = ArrayList<Found.Contact>()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query.trim()))
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
        )
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val name = c.getString(2)?.trim().orEmpty()
                    if (name.isEmpty()) continue
                    out += Found.Contact(c.getLong(0), c.getString(1).orEmpty(), name, c.getString(3))
                }
            }
        } catch (e: Exception) {
            DebugLog.error("Search", "contacts failed: $e")
        }
        return out
    }

    /**
     * Events from [pastDays] ago to [aheadDays] ahead whose title or place has every word of [query], and the notes
     * ("Note: …") among them. A repeating event counts once: its next occurrence, else its latest. The launcher's own
     * "launcher-data" event is never shown.
     */
    fun calendar(
        context: Context,
        query: String,
        now: Long = System.currentTimeMillis(),
        pastDays: Int = 180,
        aheadDays: Int = 180,
    ): Pair<List<Found.Event>, List<Found.Note>> {
        if (!allowed(context, Manifest.permission.READ_CALENDAR)) return emptyList<Found.Event>() to emptyList()
        val w = words(query)
        if (w.isEmpty()) return emptyList<Found.Event>() to emptyList()
        val day = 24 * 60 * 60_000L
        val builder = CalendarContract.Instances.CONTENT_SEARCH_URI.buildUpon()
        ContentUris.appendId(builder, now - pastDays * day)
        ContentUris.appendId(builder, now + aheadDays * day)
        builder.appendPath(query.trim())
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val events = LinkedHashMap<Long, Found.Event>()
        val notes = LinkedHashMap<Long, Found.Note>()
        try {
            context.contentResolver.query(builder.build(), projection, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
                var rows = 0
                while (c.moveToNext() && rows++ < 400) {
                    val id = c.getLong(0)
                    val title = c.getString(1)?.trim().orEmpty()
                    if (title.isEmpty() || title.equals("launcher-data", ignoreCase = true)) continue
                    val begin = c.getLong(2)
                    if (title.startsWith(StudyFormat.NOTE_PREFIX, ignoreCase = true)) {
                        val text = title.substring(StudyFormat.NOTE_PREFIX.length).trim()
                        if (text.isNotEmpty() && matches(w, text)) notes[id] = Found.Note(id, text, begin)
                        continue
                    }
                    val place = c.getString(6)?.trim().orEmpty()
                    if (!matches(w, title, place)) continue
                    val event = Found.Event(id, title, begin, c.getLong(3), c.getInt(4) == 1, c.getInt(5), place)
                    val known = events[id]
                    // The next occurrence wins (the rows come in order); a past one only until a coming one is seen.
                    if (known == null || known.end < now) events[id] = event
                }
            }
        } catch (e: Exception) {
            DebugLog.error("Search", "calendar failed: $e")
        }
        val (coming, past) = events.values.partition { it.end >= now }
        val sortedEvents = coming.sortedBy { it.begin } + past.sortedByDescending { it.begin }
        return sortedEvents to notes.values.sortedByDescending { it.begin }
    }

    /**
     * Everything [sources] allows for [query], in the order search shows it: people, events, notes, cards, options.
     * Runs on a background thread (it reads the contacts and the calendar).
     */
    fun find(context: Context, deck: Deck, query: String, sources: Sources, contactsAsked: Boolean): List<Found> {
        val text = query.trim()
        if (text.length < 2 || !sources.any) return emptyList()
        val out = ArrayList<Found>()
        var offerContacts = false
        if (sources.contacts) {
            // The row asking for contacts comes with what looks like a name only (not a calculation or a link).
            if (contactsAllowed(context)) out += contacts(context, text)
            else offerContacts = !contactsAsked && text.length >= 3 && text.all { it.isLetter() || it == ' ' || it == '-' || it == '\'' }
        }
        if (sources.events || sources.notes) {
            val (events, notes) = calendar(context, text)
            if (sources.events) out += events.take(3)
            if (sources.notes) out += notes.take(3)
        }
        if (sources.cards) out += cards(deck, text).map { Found.Card(it) }
        if (sources.settings) out += options(text)
        if (offerContacts) out += Found.AllowContacts
        return out
    }
}
