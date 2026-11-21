package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WmKeyboardTest {
    @Test
    fun theLayoutListIsReadAsTheKeyboardWritesIt() {
        // As its docs show the answer to LIST_LAYOUTS: id, language and name separated by tabs, "*" on the one in use.
        val list = WmKeyboard.parseLayouts("* builtin_qwerty\ten\tQWERTY\n  builtin_avro\tbn\tAvro phonetic\n")
        assertEquals(2, list.size)
        assertEquals(WmKeyboard.Layout("builtin_qwerty", "en", "QWERTY", inUse = true), list[0])
        assertEquals(WmKeyboard.Layout("builtin_avro", "bn", "Avro phonetic", inUse = false), list[1])
    }

    @Test
    fun spacesInsteadOfTabsStillRead() {
        val list = WmKeyboard.parseLayouts("* builtin_qwerty  en  QWERTY\n  builtin_azerty  fr  AZERTY")
        assertEquals(listOf("builtin_qwerty", "builtin_azerty"), list.map { it.id })
        assertEquals("AZERTY", list[1].name)
        assertTrue(list[0].inUse && !list[1].inUse)
    }

    @Test
    fun anEmptyOrMissingAnswerIsNoLayouts() {
        assertEquals(emptyList<WmKeyboard.Layout>(), WmKeyboard.parseLayouts(null))
        assertEquals(emptyList<WmKeyboard.Layout>(), WmKeyboard.parseLayouts("\n \n"))
    }

    @Test
    fun answersReadAsSentences() {
        assertEquals("done (builtin_french)", WmKeyboard.describe(WmKeyboard.Answer(true, "builtin_french")))
        assertTrue(WmKeyboard.describe(WmKeyboard.Answer(false, null)).startsWith("no answer"))
        assertEquals("not allowed: turn on \"layout\"", WmKeyboard.describe(WmKeyboard.Answer(false, "not allowed: turn on \"layout\"")))
    }
}
