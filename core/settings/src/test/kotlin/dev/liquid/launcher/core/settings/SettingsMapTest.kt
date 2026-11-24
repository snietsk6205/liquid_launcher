package dev.liquid.launcher.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMapTest {
    @Test
    fun everyOptionHasAPageAndATab() {
        for (setting in LauncherSettings.all) {
            val surface = SettingsMap.surfaceOf(setting)
            assertEquals(setting.key, setting.hidden, surface.internal)
            if (!setting.hidden) assertTrue(setting.key, setting.section in surface.sections)
            assertNotNull(SettingsMap.tabOf(setting))
        }
        val shown = SettingsMap.visibleSurfaces.sumOf { s -> SettingsTab.entries.sumOf { SettingsMap.settingsOf(s, it).size } }
        assertEquals(LauncherSettings.all.count { !it.hidden }, shown)
    }

    @Test
    fun everyNamedKeyIsARealOptionOfThatPage() {
        val byKey = LauncherSettings.all.associateBy { it.key }
        for (surface in SettingsMap.surfaces) {
            for ((key, tab) in surface.exact) {
                val setting = byKey[key]
                assertNotNull("${surface.id}: no option $key", setting)
                assertTrue("${surface.id}: $key is in ${setting!!.section}", setting.section in surface.sections)
                assertEquals("${surface.id}: $key", surface to tab, SettingsMap.surfaceOf(setting) to SettingsMap.tabOf(setting))
            }
            for ((prefix, _) in surface.prefixes) {
                assertTrue("${surface.id}: $prefix* matches nothing", LauncherSettings.all.any { it.key.startsWith(prefix) && it.section in surface.sections })
            }
        }
    }

    @Test
    fun eightEntriesEachWithPages() {
        // 0.16.0 (D209): Calendar is the eighth, after Study.
        assertEquals(8, SettingsEntry.entries.size)
        assertEquals(SettingsEntry.STUDY.ordinal + 1, SettingsEntry.CALENDAR.ordinal)
        assertEquals("Calendar › Calendar › Layout", SettingsMap.path(LauncherSettings.CalendarSize))
        assertEquals("Calendar › Holidays and school year › Layout", SettingsMap.path(LauncherSettings.CalendarHolidays))
        for (entry in SettingsEntry.entries) assertTrue(entry.title, SettingsMap.surfacesOf(entry).isNotEmpty())
        assertEquals(SettingsMap.surfaces.size, SettingsMap.surfaces.map { it.id }.toSet().size)
    }

    @Test
    fun everySectionOf09StillOpensAPage() {
        for (section in LauncherSettings.Sections.all) {
            val (surface, _) = SettingsMap.forSection(section.id) ?: error("section ${section.id} opens nothing")
            assertTrue(section.id, !surface.internal)
        }
        assertEquals("drawer" to SettingsTab.BEHAVIOUR, SettingsMap.forSection("drawerMotion")!!.let { it.first.id to it.second })
    }

    @Test
    fun infoDescribesAnOption() {
        val info = SettingsMap.info(LauncherSettings.HapticsStrength)
        assertEquals("motion.hapticsStrength", info.id)
        assertEquals("choice", info.type)
        assertEquals(LauncherSettings.HapticsEnabled.key, info.dependsOn)
        assertEquals(SettingsEntry.MOTION, info.entry)
        assertEquals("advanced", info.level)
        assertEquals("layout", SettingsMap.info(LauncherSettings.HomeColumns).scope)
        assertEquals("Home › Pill › Behaviour", SettingsMap.path(LauncherSettings.PillTap))
    }

    @Test
    fun theWidgetsOwnOptionsMatterOnlyWhileItSharesNothing() {
        val store = SettingsStore.inMemory()
        assertEquals(null, SettingDependencies.whyOff(LauncherSettings.CalendarWidgetMarks, store))
        assertTrue(SettingDependencies.isVisible(LauncherSettings.CalendarWidgetMarks, store))
        store.set(LauncherSettings.CalendarShareWidget, true)
        assertEquals(
            "Only when \u201CThe Calendar widget uses the calendar's settings\u201D is off",
            SettingDependencies.whyOff(LauncherSettings.CalendarWidgetMarks, store),
        )
        assertTrue(!SettingDependencies.isVisible(LauncherSettings.CalendarWidgetSchool, store))
        assertEquals("!calendar.shareWidget", SettingsMap.info(LauncherSettings.CalendarWidgetToday).dependsOn)
    }

    @Test
    fun aGreyedOptionSaysWhy() {
        val store = SettingsStore.inMemory()
        store.set(LauncherSettings.HapticsEnabled, false)
        assertEquals("Only when “Haptic feedback” is on", SettingDependencies.whyOff(LauncherSettings.HapticsStrength, store))
        store.set(LauncherSettings.HapticsEnabled, true)
        assertEquals(null, SettingDependencies.whyOff(LauncherSettings.HapticsStrength, store))
    }

    @Test
    fun pageEditorsAreFoundBySearch() {
        for (e in SettingsMap.editors) {
            val surface = SettingsMap.surface(e.surfaceId)
            assertTrue(e.surfaceId, surface != null && surface in SettingsMap.visibleSurfaces)
        }
        assertEquals("search", SettingsMap.searchEditors("cards").first().surfaceId)
        assertTrue(SettingsMap.searchEditors("search page cards").isNotEmpty())
        assertTrue(SettingsMap.searchEditors("nothing like this").isEmpty())
    }
}
