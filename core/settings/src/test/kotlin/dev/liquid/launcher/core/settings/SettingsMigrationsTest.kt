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
    fun theAreasAreOnAndTheLettersHiddenByDefault() {
        val store = SettingsStore.inMemory()
        assertTrue(store.get(LauncherSettings.IndexDial))
        assertTrue(store.get(LauncherSettings.IndexAreasOnly))
        assertEquals("touch", store.get(LauncherSettings.IndexVisibility))
        assertEquals(listOf(LetterArea.Default), LetterAreas.parseAll(store.get(LauncherSettings.IndexAreas)))
    }

    @Test
    fun aMovedCircleBecomesTheFirstAreaWhereItWas() {
        val store = SettingsStore.inMemory()
        store.set(LauncherSettings.IndexDialX, 46)
        store.set(LauncherSettings.IndexDialY, 41)
        store.set(LauncherSettings.IndexDialEdge, true)
        SettingsMigrations.migrate0111(store)
        assertEquals(listOf(LetterArea(right = true, across = 46, down = 41, sizeDp = 120)), LetterAreas.parseAll(store.get(LauncherSettings.IndexAreas)))
        assertEquals(false, store.get(LauncherSettings.IndexAreasOnly))
        // Once only: areas set later are kept.
        store.set(LauncherSettings.IndexAreas, listOf("left,10,10,80"))
        SettingsMigrations.migrate0111(store)
        assertEquals(listOf("left,10,10,80"), store.get(LauncherSettings.IndexAreas))
    }

    @Test
    fun anUntouchedCircleLeavesTheDefaultArea() {
        val store = SettingsStore.inMemory()
        SettingsMigrations.migrate0111(store)
        assertEquals(false, store.isChanged(LauncherSettings.IndexAreas))
        assertTrue(store.get(LauncherSettings.IndexAreasOnly))
    }

    @Test
    fun areasRoundTripAndBadEntriesAreSkipped() {
        val areas = listOf(LetterArea(true, 84, 45, 120), LetterArea(false, 10, 70, 90))
        assertEquals(areas, LetterAreas.parseAll(LetterAreas.formatAll(areas)))
        assertEquals(listOf(LetterArea(false, 100, 0, LetterArea.MIN_SIZE)), LetterAreas.parseAll(listOf("left,140,-5,10", "up,1,2,3", "right,1,2")))
        // A new area goes to the other side, lower down.
        assertEquals(LetterArea(false, 16, 65, 120), LetterAreas.next(listOf(LetterArea.Default)))
    }
}
