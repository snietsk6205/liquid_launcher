package dev.liquid.launcher.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockStyleTest {
    private val base = ClockStyle.fromSettings(ClockConfig(h24 = true, scale = 1f, showDate = false, glass = false))

    @Test
    fun aClockWithoutItsOwnValuesFollowsTheSettings() {
        assertEquals(base, ClockStyle.decode(null, base))
        assertEquals(base, ClockStyle.decode(emptyMap(), base))
        val glassy = ClockStyle.fromSettings(ClockConfig(h24 = false, scale = 1.2f, showDate = true, glass = true))
        assertEquals("glass", glassy.plate)
        assertEquals("below", glassy.date)
        assertEquals(120, glassy.size)
        assertEquals(false, glassy.h24)
    }

    @Test
    fun onlyChangedValuesAreStored_andComeBack() {
        val style = base.copy(design = "analog", font = "file:My Font.ttf", weight = 700, color = "#4DABF7", seconds = "sweep", shadow = false)
        val stored = style.encode(base)
        assertEquals(setOf("design", "font", "weight", "color", "seconds", "shadow"), stored.keys)
        assertEquals(style, ClockStyle.decode(stored, base))
        assertTrue(base.encode(base).isEmpty())
    }

    @Test
    fun brokenValuesFallBack() {
        val style = ClockStyle.decode(mapOf("size" to "9999", "weight" to "heavy", "shadow" to "maybe"), base)
        assertEquals(200, style.size)
        assertEquals(base.weight, style.weight)
        assertEquals(base.shadow, style.shadow)
    }

    @Test
    fun timeInWords() {
        assertEquals(listOf("two", "o'clock"), timeInWords(14, 1))
        assertEquals(listOf("ten past", "two"), timeInWords(14, 11))
        assertEquals(listOf("half past", "two"), timeInWords(2, 31))
        assertEquals(listOf("quarter to", "three"), timeInWords(14, 44))
        assertEquals(listOf("five to", "twelve"), timeInWords(23, 56))
        assertEquals(listOf("twelve", "o'clock"), timeInWords(23, 58))
        assertEquals(listOf("twelve", "o'clock"), timeInWords(0, 0))
    }

    @Test
    fun stringWeightAndReach() {
        assertEquals(1f, stringWeight(0f, 10f), 0.0001f)
        assertEquals(0.5f, stringWeight(5f, 10f), 0.0001f)
        assertEquals(0f, stringWeight(10f, 10f), 0.0001f)
        assertEquals(0f, stringWeight(-14f, 10f), 0.0001f)
        // Pinned: the curve ends exactly at the end of the column, unless the finger is very close to it.
        assertEquals(8f, stringReach(toEnd = 8f, free = 20f, pinned = true), 0.0001f)
        assertEquals(5f, stringReach(toEnd = 2f, free = 20f, pinned = true), 0.0001f)
        assertEquals(20f, stringReach(toEnd = 26f, free = 20f, pinned = true), 0.0001f)
        assertEquals(20f, stringReach(toEnd = 8f, free = 20f, pinned = false), 0.0001f)
    }
}
