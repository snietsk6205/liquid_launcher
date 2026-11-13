package dev.liquid.launcher.core.data

import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files

class LayoutsTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val settings = SettingsStore.inMemory()
    private val home = LayoutRepository(JsonStore(null, HomeLayout.serializer(), HomeLayout(), scope)).apply { ensureDefault(5, 9) }
    private val layouts = LayoutsRepository(
        JsonStore(null, LayoutsState.serializer(), LayoutsState(), scope), home, settings, Files.createTempDirectory("layouts").toFile(),
    )

    private fun pages() = home.layout.value.pages.size

    @Test
    fun switchingRestoresHomeSettingsAndLeavesGlobalOnesAlone() {
        val study = layouts.saveCurrentAs("Study", null)
        val startPages = pages()
        settings.set(LauncherSettings.HomeColumns, 4)
        home.commit(LayoutOps.addPage(home.layout.value))
        val projects = layouts.saveCurrentAs("Projects", null)
        settings.set(LauncherSettings.DrawerColumns, 6)

        layouts.switchTo(study.id, keepChanges = true)
        assertEquals(5, settings.get(LauncherSettings.HomeColumns))
        assertEquals(startPages, pages())
        assertEquals(6, settings.get(LauncherSettings.DrawerColumns))
        assertEquals(study.id, layouts.state.value.current)

        assertTrue(layouts.undoSwitch())
        assertEquals(4, settings.get(LauncherSettings.HomeColumns))
        assertEquals(projects.id, layouts.state.value.current)
    }

    @Test
    fun eachLayoutKeepsItsOwnChanges() {
        val study = layouts.saveCurrentAs("Study", null)
        val projects = layouts.saveCurrentAs("Projects", null)
        settings.set(LauncherSettings.HomeColumns, 7)
        layouts.switchTo(study.id, keepChanges = true)
        assertEquals(5, settings.get(LauncherSettings.HomeColumns))
        layouts.switchTo(projects.id, keepChanges = true)
        assertEquals(7, settings.get(LauncherSettings.HomeColumns))
    }

    @Test
    fun exportAndImportMakeACopy() {
        settings.set(LauncherSettings.HomeColumns, 6)
        val saved = layouts.saveCurrentAs("Study", null)
        val bytes = ByteArrayOutputStream().also { layouts.export(saved.id, it) }.toByteArray()
        val imported = layouts.import(ByteArrayInputStream(bytes))!!
        assertTrue(imported.id != saved.id)
        assertEquals("Study", imported.name)
        assertEquals(saved.settings, imported.settings)
        assertEquals(2, layouts.state.value.layouts.size)
    }
}
