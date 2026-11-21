package dev.liquid.launcher.core.data

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.time.LocalDate

/*
 * 0.12.0 (D173, Med chose "a small deck with repeats"): the flashcards Claude writes into launcher-data are kept in a
 * local deck. New cards join it (due the day they come); a card already in it keeps its schedule when it comes again;
 * none is lost when a new launcher-data event replaces the old one. Wrong comes back tomorrow; right waits longer each
 * time: 1, 3, 7, 14, then 30 days. Search shows the first card due and how many more are due, then "All done for today".
 * A wrong answer still writes "Note: flashcard wrong <module>: <question>" for the evening check-in.
 */

/** A card of the deck; dates are yyyy-MM-dd. [step] counts right answers in a row (which wait comes next). */
@Serializable
data class DeckCard(
    val id: String,
    val module: String = "",
    val q: String = "",
    val a: String = "",
    val added: String = "",
    val due: String = "",
    val step: Int = 0,
    val right: Int = 0,
    val wrong: Int = 0,
    val last: String? = null,
)

@Serializable
data class Deck(val v: Int = 1, val cards: List<DeckCard> = emptyList())

object FlashcardDeck {
    /** Days a right answer waits, the n-th right in a row taking the n-th (the last one stays). */
    val WAITS = listOf(1, 3, 7, 14, 30)

    /** The card's name across days: its own id, else one made from its module and question. */
    fun idOf(card: FeedCard): String {
        card.id.trim().takeIf { it.isNotEmpty() }?.let { return it }
        val digest = MessageDigest.getInstance("SHA-1").digest((card.module.trim() + "\n" + card.q.trim()).toByteArray())
        return "q:" + digest.take(6).joinToString("") { "%02x".format(it) }
    }

    /** [deck] with the cards of [cards] it did not have (due [today]); known ones take the new wording, keep their schedule. */
    fun merge(deck: Deck, cards: List<FeedCard>, today: LocalDate): Deck {
        if (cards.isEmpty()) return deck
        val byId = LinkedHashMap<String, DeckCard>()
        deck.cards.forEach { byId[it.id] = it }
        var changed = false
        for (card in cards) {
            if (card.q.isBlank()) continue
            val id = idOf(card)
            val known = byId[id]
            if (known == null) {
                byId[id] = DeckCard(id, card.module.trim(), card.q.trim(), card.a.trim(), today.toString(), today.toString())
                changed = true
            } else if (known.q != card.q.trim() || known.a != card.a.trim() || known.module != card.module.trim()) {
                byId[id] = known.copy(module = card.module.trim(), q = card.q.trim(), a = card.a.trim())
                changed = true
            }
        }
        return if (changed) deck.copy(cards = byId.values.toList()) else deck
    }

    private fun day(text: String?): LocalDate? = text?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** The cards due on [today] or before, the longest waiting first (then in the order they came). */
    fun due(deck: Deck, today: LocalDate): List<DeckCard> = deck.cards.withIndex()
        .filter { (_, c) -> (day(c.due) ?: today) <= today }
        .sortedWith(compareBy({ day(it.value.due) ?: today }, { it.index }))
        .map { it.value }

    /** [deck] after [id] was answered on [today]: wrong comes back tomorrow, right waits the next of [WAITS]. */
    fun answer(deck: Deck, id: String, right: Boolean, today: LocalDate): Deck = deck.copy(
        cards = deck.cards.map { c ->
            if (c.id != id) return@map c
            if (right) {
                val wait = WAITS[c.step.coerceIn(0, WAITS.lastIndex)]
                c.copy(due = today.plusDays(wait.toLong()).toString(), step = c.step + 1, right = c.right + 1, last = today.toString())
            } else {
                c.copy(due = today.plusDays(1).toString(), step = 0, wrong = c.wrong + 1, last = today.toString())
            }
        },
    )

    /** The next day after [today] with cards due, and how many: for "All done for today". */
    fun next(deck: Deck, today: LocalDate): Pair<LocalDate, Int>? = deck.cards.mapNotNull { day(it.due) }
        .filter { it > today }.groupingBy { it }.eachCount().minByOrNull { it.key }?.toPair()

    /**
     * 0.11.2's state, the one card of the day answered (its question, on [today]): that card waits until tomorrow, so
     * it stays out of search today as it did.
     */
    fun migrateAnswered(deck: Deck, answeredQ: String?, today: LocalDate): Deck {
        if (answeredQ.isNullOrBlank()) return deck
        return deck.copy(cards = deck.cards.map { c ->
            if (c.q == answeredQ.trim() && (day(c.due) ?: today) <= today) c.copy(due = today.plusDays(1).toString(), last = today.toString()) else c
        })
    }
}
