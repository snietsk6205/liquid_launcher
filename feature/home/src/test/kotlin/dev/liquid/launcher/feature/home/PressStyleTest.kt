package dev.liquid.launcher.feature.home

import dev.liquid.launcher.core.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PressStyleTest {
    @Test
    fun everyMotionHasASaneSpring() {
        val before = PressStyle.style to PressStyle.wobble
        try {
            for ((style, _) in LauncherSettings.PressFeel.options) {
                PressStyle.style = style
                for (wobble in listOf(0f, 0.5f, 1f)) {
                    PressStyle.wobble = wobble
                    val d = PressStyle.releaseDamping()
                    assertTrue("$style at $wobble: $d", d in 0.15f..1f)
                    if (wobble == 0f) assertEquals("$style settles without overshoot at wobble 0", 1f, d, 0.0001f)
                }
                assertTrue(style, PressStyle.shrink() in 0f..0.3f)
            }
        } finally {
            PressStyle.style = before.first
            PressStyle.wobble = before.second
        }
    }

    @Test
    fun wobbleOfOneHalfKeepsTheSquishOf072() {
        PressStyle.style = "squishy"
        PressStyle.wobble = 0.5f
        assertEquals(0.42f, PressStyle.releaseDamping(), 0.0001f)
    }
}
