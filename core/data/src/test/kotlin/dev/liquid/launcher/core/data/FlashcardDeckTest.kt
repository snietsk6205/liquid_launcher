package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FlashcardDeckTest {
    private val today = LocalDate.of(2026, 11, 21)
    private val beppo = FeedCard("conv-mono", "Proba 2", "Si \$(f_n)\$ est croissante…", "Convergence monotone")
    private val fatou = FeedCard("fatou", "Proba 2", "Lemme de Fatou ?", "\$\$\\int \\liminf f_n \\le \\liminf \\int f_n\$\$")

    @Test
    fun theSingleFlashcardStillReadsAsADeckOfOne() {
        val feed = StudyFeedParser.parse("""{"v":1,"flashcard":{"module":"Micro","q":"Élasticité ?","a":"…"}}""")!!
        assertEquals(1, feed.cards.size)
        val deck = FlashcardDeck.merge(Deck(), feed.cards, today)
        assertEquals(1, deck.cards.size)
        assertTrue(deck.cards[0].id.startsWith("q:"))
        assertEquals(today.toString(), deck.cards[0].due)
    }

    @Test
    fun aDeckFromLauncherDataAddsOnlyNewCardsAndLosesNone() {
        val feed = StudyFeedParser.parse("""{"flashcards":[{"id":"conv-mono","module":"Proba 2","q":"A","a":"B"},{"id":"fatou","q":"C","a":"D"}]}""")!!
        var deck = FlashcardDeck.merge(Deck(), feed.cards, today)
        assertEquals(2, deck.cards.size)
        deck = FlashcardDeck.answer(deck, "conv-mono", right = true, today)
        // The next day's event holds one known card (new wording) and one new one: the known card keeps its schedule.
        val next = today.plusDays(1)
        deck = FlashcardDeck.merge(deck, listOf(FeedCard("conv-mono", "Proba 2", "A (reworded)", "B"), FeedCard("dct", "Proba 2", "E", "F")), next)
        assertEquals(listOf("conv-mono", "fatou", "dct"), deck.cards.map { it.id })
        assertEquals("A (reworded)", deck.cards[0].q)
        assertEquals(1, deck.cards[0].step)
        // Merging the same event again changes nothing (it is read at every refresh).
        assertTrue(FlashcardDeck.merge(deck, listOf(FeedCard("dct", "Proba 2", "E", "F")), next) === deck)
    }

    @Test
    fun rightWaitsLongerEachTimeAndWrongComesBackTomorrow() {
        var deck = FlashcardDeck.merge(Deck(), listOf(beppo), today)
        var day = today
        val waits = ArrayList<Long>()
        repeat(6) {
            deck = FlashcardDeck.answer(deck, "conv-mono", right = true, day)
            val due = LocalDate.parse(deck.cards[0].due)
            waits += java.time.temporal.ChronoUnit.DAYS.between(day, due)
            day = due
        }
        assertEquals(listOf(1L, 3L, 7L, 14L, 30L, 30L), waits)
        deck = FlashcardDeck.answer(deck, "conv-mono", right = false, day)
        assertEquals(day.plusDays(1).toString(), deck.cards[0].due)
        assertEquals(0, deck.cards[0].step)
        assertEquals(1, deck.cards[0].wrong)
    }

    @Test
    fun searchShowsTheLongestWaitingFirstThenAllDone() {
        var deck = FlashcardDeck.merge(Deck(), listOf(beppo), today.minusDays(3))
        deck = FlashcardDeck.merge(deck, listOf(fatou), today)
        assertEquals(listOf("conv-mono", "fatou"), FlashcardDeck.due(deck, today).map { it.id })
        deck = FlashcardDeck.answer(deck, "conv-mono", right = true, today)
        deck = FlashcardDeck.answer(deck, "fatou", right = false, today)
        assertTrue(FlashcardDeck.due(deck, today).isEmpty())
        assertEquals(today.plusDays(1) to 2, FlashcardDeck.next(deck, today))
        assertNull(FlashcardDeck.next(Deck(), today))
    }

    @Test
    fun todaysAnsweredCardOf0112StaysHiddenToday() {
        val deck = FlashcardDeck.merge(Deck(), listOf(beppo, fatou), today)
        val migrated = FlashcardDeck.migrateAnswered(deck, beppo.q, today)
        assertEquals(listOf("fatou"), FlashcardDeck.due(migrated, today).map { it.id })
        // Tomorrow both are due; the one left from today has waited longer and comes first.
        assertEquals(listOf("fatou", "conv-mono"), FlashcardDeck.due(migrated, today.plusDays(1)).map { it.id })
    }
}
