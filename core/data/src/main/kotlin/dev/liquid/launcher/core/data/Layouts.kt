package dev.liquid.launcher.core.data

import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * One saved home setup: the pages and their items, the settings that shape the home screen
 * ([LauncherSettings.layoutKeys]) and optionally its own wallpaper (a file next to the layouts).
 */
@Serializable
data class SavedLayout(
    val id: String,
    val name: String,
    val layout: HomeLayout,
    val settings: Map<String, String>,
    /** File name of this layout's wallpaper, or null to leave the wallpaper alone when switching. */
    val wallpaper: String? = null,
)

@Serializable
data class LayoutsState(
    val layouts: List<SavedLayout> = emptyList(),
    /** The layout the home screen was last switched to or saved as. */
    val current: String? = null,
)

/** What switching needs from outside this module: the wallpaper to apply. */
data class SwitchResult(val wallpaper: File?)

/**
 * Saved layouts: save the current setup under a name, switch between setups (one undo step),
 * rename, duplicate, delete, export and import. Settings outside the layout keys are global
 * and never touched.
 */
class LayoutsRepository(
    private val store: JsonStore<LayoutsState>,
    private val home: LayoutRepository,
    private val settings: SettingsStore,
    private val dir: File,
) {
    val state: StateFlow<LayoutsState> = store.value

    /** The state before the last switch, for [undoSwitch]. */
    private var beforeSwitch: Pair<SavedLayout, String?>? = null

    fun find(id: String): SavedLayout? = store.get().layouts.firstOrNull { it.id == id }

    fun wallpaperFile(layout: SavedLayout): File? = layout.wallpaper?.let { File(dir, it) }?.takeIf { it.exists() }

    private fun capture(id: String, name: String, wallpaper: String?): SavedLayout =
        SavedLayout(id, name, home.layout.value, settings.exportValues(LauncherSettings.layoutKeys), wallpaper)

    /** Saves the current home setup as a new layout (with a copy of [wallpaper] if given) and makes it current. */
    fun saveCurrentAs(name: String, wallpaper: File?): SavedLayout {
        val id = LayoutOps.newId()
        val file = wallpaper?.takeIf { it.exists() }?.let { source ->
            dir.mkdirs()
            val target = File(dir, "$id.png")
            source.copyTo(target, overwrite = true)
            target.name
        }
        val saved = capture(id, name.trim().ifEmpty { "Layout" }, file)
        store.update { it.copy(layouts = it.layouts + saved, current = id) }
        DebugLog.event("Layouts", "saved \"${saved.name}\"")
        return saved
    }

    /** Writes the current home setup into the current layout (keeping its name and wallpaper). */
    fun updateCurrent() {
        val state = store.get()
        val current = state.layouts.firstOrNull { it.id == state.current } ?: return
        val updated = capture(current.id, current.name, current.wallpaper)
        if (updated == current) return
        store.update { s -> s.copy(layouts = s.layouts.map { if (it.id == current.id) updated else it }) }
    }

    /**
     * Applies layout [id]. With [keepChanges] the current layout is saved first, so each
     * layout keeps its own edits. Returns what the caller must apply itself, or null when
     * the layout does not exist.
     */
    fun switchTo(id: String, keepChanges: Boolean): SwitchResult? {
        val target = find(id) ?: return null
        if (keepChanges) updateCurrent()
        val state = store.get()
        beforeSwitch = capture("undo", "Before switching", null) to state.current
        apply(target)
        store.update { it.copy(current = id) }
        DebugLog.event("Layouts", "switched to \"${target.name}\"")
        return SwitchResult(wallpaperFile(target))
    }

    /** The layout after the current one, wrapping around. */
    fun next(): SavedLayout? {
        val state = store.get()
        if (state.layouts.isEmpty()) return null
        val index = state.layouts.indexOfFirst { it.id == state.current }
        return state.layouts[(index + 1).mod(state.layouts.size)]
    }

    val canUndoSwitch: Boolean get() = beforeSwitch != null

    /** Goes back to the setup before the last switch. The wallpaper is not changed back. */
    fun undoSwitch(): Boolean {
        val (snapshot, current) = beforeSwitch ?: return false
        beforeSwitch = null
        apply(snapshot)
        store.update { it.copy(current = current) }
        return true
    }

    private fun apply(layout: SavedLayout) {
        home.commit(layout.layout)
        settings.importValues(layout.settings, LauncherSettings.layoutKeys)
    }

    fun rename(id: String, name: String) {
        val clean = name.trim().ifEmpty { return }
        store.update { s -> s.copy(layouts = s.layouts.map { if (it.id == id) it.copy(name = clean) else it }) }
    }

    fun duplicate(id: String): SavedLayout? {
        val source = find(id) ?: return null
        val newId = LayoutOps.newId()
        val wallpaper = wallpaperFile(source)?.let { file ->
            val target = File(dir, "$newId.png")
            file.copyTo(target, overwrite = true)
            target.name
        }
        val copy = source.copy(id = newId, name = source.name + " copy", wallpaper = wallpaper)
        store.update { it.copy(layouts = it.layouts + copy) }
        return copy
    }

    fun delete(id: String) {
        find(id)?.let { wallpaperFile(it)?.delete() }
        store.update { s -> s.copy(layouts = s.layouts.filter { it.id != id }, current = s.current.takeIf { it != id }) }
    }

    /** Sets or clears a layout's own wallpaper. */
    fun setWallpaper(id: String, wallpaper: File?) {
        val layout = find(id) ?: return
        wallpaperFile(layout)?.delete()
        val name = wallpaper?.takeIf { it.exists() }?.let { source ->
            dir.mkdirs()
            val target = File(dir, "$id.png")
            source.copyTo(target, overwrite = true)
            target.name
        }
        store.update { s -> s.copy(layouts = s.layouts.map { if (it.id == id) it.copy(wallpaper = name) else it }) }
    }

    fun move(from: Int, to: Int) {
        store.update { s ->
            if (from !in s.layouts.indices || to !in s.layouts.indices) return@update s
            val list = s.layouts.toMutableList()
            list.add(to, list.removeAt(from))
            s.copy(layouts = list)
        }
    }

    /** A layout as one file: `layout.json` and, if it has one, `wallpaper.png`. */
    fun export(id: String, out: OutputStream): Boolean {
        val layout = find(id) ?: return false
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("layout.json"))
            zip.write(json.encodeToString(SavedLayout.serializer(), layout.copy(wallpaper = null)).toByteArray())
            zip.closeEntry()
            wallpaperFile(layout)?.let { file ->
                zip.putNextEntry(ZipEntry("wallpaper.png"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return true
    }

    /** Adds the layout in [input] (from [export]) under a new id. Apps that are not installed stay as empty cells. */
    fun import(input: InputStream): SavedLayout? {
        var layout: SavedLayout? = null
        var wallpaper: ByteArray? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when (entry.name) {
                    "layout.json" -> layout = json.decodeFromString(SavedLayout.serializer(), zip.readBytes().decodeToString())
                    "wallpaper.png" -> wallpaper = zip.readBytes()
                }
            }
        }
        val read = layout ?: return null
        val id = LayoutOps.newId()
        val file = wallpaper?.let { bytes ->
            dir.mkdirs()
            File(dir, "$id.png").apply { writeBytes(bytes) }.name
        }
        val added = read.copy(id = id, wallpaper = file)
        store.update { it.copy(layouts = it.layouts + added) }
        return added
    }

    internal companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    }
}
