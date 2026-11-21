package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EverythingTest {
    @Test
    fun accentsAndCaseDoNotMatter() {
        assertEquals("revision d'analyse", Everything.normalize("Révision d'Analyse"))
        assertTrue(Everything.matches(Everything.words("revision"), "Révision"))
        assertTrue(Everything.matches(Everything.words("ÉLEC"), "electronique"))
    }

    @Test
    fun everyWordMustBeSomewhere() {
        val words = Everything.words("analyse td")
        assertTrue(Everything.matches(words, "Analyse", "TD salle B12"))
        assertFalse(Everything.matches(words, "Analyse", "Cours"))
        assertFalse(Everything.matches(emptyList(), "anything"))
    }

    @Test
    fun cardsAreFoundByQuestionAnswerOrModule() {
        val deck = Deck(
            cards = listOf(
                DeckCard("1", module = "Analyse", q = "Dérivée de \\sin x ?", a = "\\cos x"),
                DeckCard("2", module = "Physique", q = "Unité de la force ?", a = "Le newton"),
                DeckCard("3", module = "Analyse", q = "Intégrale de 1/x ?", a = "ln|x|"),
            )
        )
        assertEquals(listOf("1", "3"), Everything.cards(deck, "analyse").map { it.id })
        assertEquals(listOf("2"), Everything.cards(deck, "newton").map { it.id })
        assertEquals(listOf("1"), Everything.cards(deck, "derivee").map { it.id })
        assertEquals(1, Everything.cards(deck, "analyse", limit = 1).size)
        assertTrue(Everything.cards(deck, "  ").isEmpty())
    }

    @Test
    fun optionsOpenTheirPageOnTheirTab() {
        val stretch = Everything.options("liquid stretch")
        assertTrue(stretch.any { it.title == "Liquid stretch" && it.target == "page:search:behaviour:search.stretch" })
        val music = Everything.options("live item playing")
        assertTrue(music.any { it.target == "page:pill:layout:pill.live.music" })
        // Two letters are not enough, and a name finds no option.
        assertTrue(Everything.options("li").isEmpty())
        assertTrue(Everything.options("marie curie").isEmpty())
    }

    @Test
    fun earlierResultsStayOnlyIfTheyStillFit() {
        val event = Found.Event(1, "Analyse TD", 0, 0, false, 0, "B12")
        val contact = Found.Contact(2, "k", "Marie Curie", null)
        assertTrue(Everything.stillMatches(event, "analyse b12"))
        assertFalse(Everything.stillMatches(event, "analyse physique"))
        assertTrue(Everything.stillMatches(contact, "marie"))
        assertFalse(Everything.stillMatches(contact, "mario"))
        assertTrue(Everything.stillMatches(Found.AllowContacts, "anything"))
    }
}
