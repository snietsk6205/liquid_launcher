package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotGesturesTest {
    @Test
    fun roundTrip_keepsEachActionAndDropsDefaults() {
        val study = PillSlot.Action(LauncherAction.Today).encode()
        val map = mapOf(
            study to SlotGestures(longPress = LauncherAction.StudyNote, swipeUp = LauncherAction.AskClaude),
            PillSlot.Date.encode() to SlotGestures(tap = LauncherAction.None),
            PillSlot.Time.encode() to SlotGestures(),
        )
        val lines = SlotGestures.encodeAll(map)
        assertEquals(2, lines.size)
        val back = SlotGestures.decodeAll(lines)
        assertEquals(LauncherAction.StudyNote, back[study]?.longPress)
        assertEquals(LauncherAction.AskClaude, back[study]?.swipeUp)
        assertNull(back[study]?.tap)
        // "Nothing" is a choice of its own, not the default.
        assertEquals(LauncherAction.None, back[PillSlot.Date.encode()]?.tap)
        assertTrue(PillSlot.Time.encode() !in back)
    }

    @Test
    fun actionsWithSeparatorsInsideSurvive() {
        val screen = LauncherAction.LaunchActivity("com.example", "com.example.Main", "Main | screen")
        val back = SlotGestures.decodeAll(SlotGestures.encodeAll(mapOf("search" to SlotGestures(tap = screen))))
        assertEquals(screen, back["search"]?.tap)
        assertEquals("study_note", LauncherAction.StudyNote.encode())
        assertEquals(LauncherAction.StudyNote, LauncherAction.decode("study_note"))
    }

    @Test
    fun customiseThePillIsAnActionOfThePillOnly() {
        assertEquals(LauncherAction.CustomisePill, LauncherAction.decode(LauncherAction.CustomisePill.encode()))
        assertTrue(LauncherAction.builtIns.none { it.first == LauncherAction.CustomisePill })
        assertTrue(LauncherAction.pillOnly.any { it.first == LauncherAction.CustomisePill })
        assertEquals("Customise the pill (a chip to its settings page)", LauncherAction.label(LauncherAction.CustomisePill) { null })
    }
}
