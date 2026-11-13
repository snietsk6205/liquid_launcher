package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    @Test
    fun exactBeatsPrefixBeatsWordStartBeatsContains() {
        val exact = Fuzzy.score("maps", "Maps")!!
        val prefix = Fuzzy.score("map", "Maps")!!
        val word = Fuzzy.score("map", "Google Maps")!!
        val contains = Fuzzy.score("aps", "Maps")!!
        assertTrue(exact > prefix)
        assertTrue(prefix > word)
        assertTrue(word > contains)
    }

    @Test
    fun initialsAndAccents() {
        assertNotNull(Fuzzy.score("gm", "Google Maps"))
        assertNotNull(Fuzzy.score("cafe", "Café"))
        assertEquals(1000, Fuzzy.score("cafe", "Café"))
    }

    @Test
    fun typosAreTolerated() {
        assertNotNull(Fuzzy.score("whatsap", "WhatsApp"))
        assertNotNull(Fuzzy.score("yuotube", "YouTube"))
        assertNotNull(Fuzzy.score("calender", "Calendar"))
        assertNull(Fuzzy.score("zzz", "Calendar"))
    }

    @Test
    fun subsequence() {
        assertNotNull(Fuzzy.score("tlgrm", "Telegram"))
    }

    @Test
    fun rankPutsTheBestFirstAndUsesBoost() {
        val apps = listOf("Settings", "Messages", "Maps", "Mail")
        val ranked = Fuzzy.rank("ma", apps, { it })
        assertEquals(setOf("Maps", "Mail"), ranked.take(2).toSet())
        assertTrue(ranked.indexOf("Messages") > 1)
        val boosted = Fuzzy.rank("m", listOf("Maps", "Mail"), { it }, { if (it == "Mail") 100 else 0 })
        assertEquals("Mail", boosted.first())
    }

    @Test
    fun calculator() {
        assertEquals("7", Calculator.evaluate("1+2*3"))
        assertEquals("9", Calculator.evaluate("(1+2)*3"))
        assertEquals("2.5", Calculator.evaluate("5/2"))
        assertEquals("2.5", Calculator.evaluate("5 ÷ 2"))
        assertEquals("1024", Calculator.evaluate("2^10"))
        assertEquals("20", Calculator.evaluate("200*10%"))
        assertEquals("-3", Calculator.evaluate("-1-2"))
        assertEquals("3.5", Calculator.evaluate("1,5+2"))
        assertNull(Calculator.evaluate("42"))
        assertNull(Calculator.evaluate("maps"))
        assertNull(Calculator.evaluate("1/0"))
        assertNull(Calculator.evaluate("2*(3"))
    }

    @Test
    fun bangs() {
        val (bang, rest) = Bangs.parse("!yt lofi beats")!!
        assertEquals("yt", bang.trigger)
        assertEquals("lofi beats", rest)
        assertNull(Bangs.parse("!unknown x"))
        assertEquals("https://www.youtube.com/results?search_query=lofi+beats", Bangs.url(bang.url, rest))
    }

    @Test
    fun pillSlotsRoundTrip() {
        val ref = AppRef("p", "p.Main", 0)
        val slots = listOf(PillSlot.Search, PillSlot.Date, PillSlot.Time, PillSlot.Spacer,
            PillSlot.Action(LauncherAction.OpenDrawer), PillSlot.App(ref))
        assertEquals(slots, PillSlot.decodeAll(slots.map { it.encode() }))
        assertNull(PillSlot.decode("action:none"))
        assertNull(PillSlot.decode("bogus"))
    }
}
