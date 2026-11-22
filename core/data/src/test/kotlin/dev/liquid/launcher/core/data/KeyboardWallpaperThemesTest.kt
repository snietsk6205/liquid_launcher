package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardWallpaperThemesTest {
    @Test
    fun remembersWhichThemesWmKeyboardHas() {
        assertEquals(emptySet<Int>(), KeyboardWallpaperThemes.parse(""))
        assertEquals(setOf(0), KeyboardWallpaperThemes.parse("1"))
        assertEquals(setOf(0, 1), KeyboardWallpaperThemes.parse("1,2"))
        assertEquals(setOf(1), KeyboardWallpaperThemes.parse(" 2 , 7"))
        assertEquals("1,2", KeyboardWallpaperThemes.encode(setOf(1, 0)))
        assertEquals("", KeyboardWallpaperThemes.encode(emptySet()))
    }

    @Test
    fun readsWmKeyboardsAnswers() {
        // Med's log of 2026-11-22: every change answered this.
        assertTrue(KeyboardWallpaperThemes.missing("no theme is called \"Liquid wallpaper 2\""))
        assertFalse(KeyboardWallpaperThemes.missing("custom_1763800000001"))
        assertFalse(KeyboardWallpaperThemes.missing(null))
        assertTrue(KeyboardWallpaperThemes.automaticWins("custom_1 (automatic theme is on and takes precedence)"))
        assertFalse(KeyboardWallpaperThemes.automaticWins("custom_1"))
    }
}
