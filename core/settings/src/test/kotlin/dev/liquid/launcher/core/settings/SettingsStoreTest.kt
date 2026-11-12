package dev.liquid.launcher.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {
    @Test
    fun defaultsAreReturnedWhenNothingIsStored() {
        val store = SettingsStore.inMemory()
        assertEquals(true, store.get(LauncherSettings.RedirectEnabled))
        assertEquals(80, store.get(LauncherSettings.OnRecentsExitDelay))
        assertEquals(listOf("overview_panel_launcher3", "overview_panel"), store.get(LauncherSettings.RecentsIds))
        assertFalse(store.isChanged(LauncherSettings.RedirectEnabled))
    }

    @Test
    fun setAndReset() {
        val store = SettingsStore.inMemory()
        store.set(LauncherSettings.OnHomeDelay, 120)
        assertEquals(120, store.get(LauncherSettings.OnHomeDelay))
        assertTrue(store.isChanged(LauncherSettings.OnHomeDelay))
        store.reset(LauncherSettings.OnHomeDelay)
        assertEquals(LauncherSettings.OnHomeDelay.default, store.get(LauncherSettings.OnHomeDelay))
        assertFalse(store.isChanged(LauncherSettings.OnHomeDelay))
    }

    @Test
    fun settingTheDefaultValueStoresNothing() {
        val store = SettingsStore.inMemory()
        store.set(LauncherSettings.OnHomeDelay, 200)
        store.set(LauncherSettings.OnHomeDelay, LauncherSettings.OnHomeDelay.default)
        assertFalse(store.isChanged(LauncherSettings.OnHomeDelay))
    }

    @Test
    fun listsRoundTrip() {
        val store = SettingsStore.inMemory()
        store.set(LauncherSettings.LearnedIds, listOf("a:id/x", "a:id/y"))
        assertEquals(listOf("a:id/x", "a:id/y"), store.get(LauncherSettings.LearnedIds))
    }

    @Test
    fun outOfRangeAndUnknownValuesFallBack() {
        val store = SettingsStore.inMemory(
            mapOf(
                LauncherSettings.OnHomeDelay.key to 99999,
                LauncherSettings.ScreenOffMode.key to "bogus",
                LauncherSettings.RedirectEnabled.key to "not a boolean",
            )
        )
        assertEquals(LauncherSettings.OnHomeDelay.max, store.get(LauncherSettings.OnHomeDelay))
        assertEquals("always", store.get(LauncherSettings.ScreenOffMode))
        assertEquals(true, store.get(LauncherSettings.RedirectEnabled))
    }

    @Test
    fun keysAreUnique() {
        val keys = LauncherSettings.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun searchFindsByKeyword() {
        val hits = LauncherSettings.search("loop guard")
        assertTrue(hits.contains(LauncherSettings.GuardCount))
        assertTrue(LauncherSettings.search("zzzz").isEmpty())
    }
}
