package dev.liquid.launcher.core.data

import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAITest {
    @Test
    fun theExportHasTheRulesTheQuestionAndEveryOption() {
        val s = SettingsStore.inMemory()
        val columns = if (LauncherSettings.HomeColumns.default == 6) 5 else 6
        s.set(LauncherSettings.HomeColumns, columns)
        val md = SettingsAI.export(s, "0.10.1", "Make it calmer")
        assertTrue(md.contains("one JSON object and nothing else"))
        assertTrue(md.contains("Make it calmer"))
        assertTrue(md.contains("`home.columns` (Columns): $columns"))
        for (setting in LauncherSettings.all.filter { !it.hidden }) assertTrue(setting.key, md.contains("`${setting.key}`"))
        assertFalse(md.contains("`state."))
    }

    @Test
    fun aPatchIsCheckedAppliedAndUndone() {
        val s = SettingsStore.inMemory()
        s.set(LauncherSettings.HomeRows, 7)
        val columns = if (LauncherSettings.HomeColumns.default == 6) 5 else 6
        val answer = """Here you go:
            ```json
            {"set": {"home.columns": $columns, "motion.haptics": false, "drawer.sort": "nope", "home.rows": 99, "no.such": 1,
                     "theme.accent": "#5484ed"},
             "reset": ["home.rows"], "why": "Calmer"}
            ```"""
        val patch = SettingsAI.parse(answer, s)
        assertNull(patch.error)
        assertEquals("Calmer", patch.why)
        val problems = patch.changes.filter { it.problem != null }.map { it.id }.toSet()
        assertEquals(setOf("drawer.sort", "home.rows", "no.such"), problems)
        assertEquals(4, patch.valid.size)
        val undo = SettingsAI.apply(patch, s)
        assertEquals(columns, s.get(LauncherSettings.HomeColumns))
        assertEquals(false, s.get(LauncherSettings.HapticsEnabled))
        assertEquals("#5484ED", s.get(LauncherSettings.ThemeAccent))
        assertEquals(LauncherSettings.HomeRows.default, s.get(LauncherSettings.HomeRows))
        SettingsAI.undo(undo, s)
        assertEquals(LauncherSettings.HomeColumns.default, s.get(LauncherSettings.HomeColumns))
        assertEquals(true, s.get(LauncherSettings.HapticsEnabled))
        assertEquals(7, s.get(LauncherSettings.HomeRows))
        assertEquals(LauncherSettings.ThemeAccent.default, s.get(LauncherSettings.ThemeAccent))
    }

    @Test
    fun aFlatObjectAndNonsense() {
        val s = SettingsStore.inMemory()
        assertEquals(1, SettingsAI.parse("""{"home.columns": 4}""", s).valid.size)
        assertNotNull(SettingsAI.parse("Sure! I would change the columns.", s).error)
        assertNotNull(SettingsAI.parse("{not json}", s).error)
        assertEquals("Not one of", SettingsAI.parse("""{"set":{"drawer.sort":"nope"}}""", s).changes.single().problem!!.take(10))
    }
}
