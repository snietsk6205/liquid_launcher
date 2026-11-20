package dev.liquid.launcher.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMigrationsTest {
    @Test
    fun lettersShownAlwaysHideOnceForTheCircle() {
        val store = SettingsStore.inMemory()
        store.set(LauncherSettings.IndexVisibility, "always")
        SettingsMigrations.migrate0110(store)
        assertEquals("touch", store.get(LauncherSettings.IndexVisibility))
        assertTrue(store.get(LauncherSettings.IndexMigrated0110))
        // Chosen again afterwards: kept.
        store.set(LauncherSettings.IndexVisibility, "always")
        SettingsMigrations.migrate0110(store)
        assertEquals("always", store.get(LauncherSettings.IndexVisibility))
    }

    @Test
    fun anotherChoiceIsKept() {
        val store = SettingsStore.inMemory()
        store.set(LauncherSettings.IndexVisibility, "scroll")
        SettingsMigrations.migrate0110(store)
        assertEquals("scroll", store.get(LauncherSettings.IndexVisibility))
    }

    @Test
    fun theCircleIsOnAndTheLettersHiddenByDefault() {
        val store = SettingsStore.inMemory()
        assertTrue(store.get(LauncherSettings.IndexDial))
        assertEquals("touch", store.get(LauncherSettings.IndexVisibility))
        assertEquals(84, store.get(LauncherSettings.IndexDialX))
        assertEquals(45, store.get(LauncherSettings.IndexDialY))
    }
}
