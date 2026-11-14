package dev.liquid.launcher.core.data

import android.app.WallpaperManager
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import dev.liquid.launcher.core.common.DebugLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import dev.liquid.launcher.core.common.WallpaperTones
import dev.liquid.launcher.core.common.ColourTiming
import kotlinx.coroutines.flow.combine
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** The launcher's long-lived data: one instance per process, shared by every screen. */
class LauncherData private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Bumped when the palette changes: only icons rendered in colour (fitted to a pack, work badge) are redrawn. */
    private val themeTick = MutableStateFlow(0)

    /** Bumped when the system's colours change: icon packs that follow them are loaded again. */
    private val packTick = MutableStateFlow(0)
    private val _colourTick = MutableStateFlow(0)
    private val _iconPalette = MutableStateFlow<Pair<Int, Int>?>(null)

    /** Changes whenever the wallpaper's colours may have changed (accent, light or dark text, themed icons). */
    val colourTick: StateFlow<Int> = _colourTick

    /**
     * Background and foreground colour of themed icons, or null when they are off. Icons take
     * these when they are drawn, so a change here is only a redraw.
     */
    val iconPalette: StateFlow<Pair<Int, Int>?> = _iconPalette

    /** What the colours looked like at the last check: seed, light flag and themed palette. */
    @Volatile
    private var lastColours: Triple<Int, Boolean, Pair<Int, Int>?>? = null

    /**
     * With the "system" colour source, after the launcher set a wallpaper: the system palette
     * it had before ([stale]); until it changes (or [until]), the new wallpaper's own tones are used.
     */
    private class SystemHold(val stale: Pair<Int, Int>, val until: Long)

    @Volatile
    private var systemHold: SystemHold? = null
    private val dir = File(context.filesDir, "data")

    val settings: SettingsStore = SettingsStore.get(context)

    private val metaStore = JsonStore(
        File(dir, "apps.json"), MapSerializer(String.serializer(), AppMeta.serializer()), emptyMap(), scope,
    )
    val usage = UsageStore(
        JsonStore(File(dir, "usage.json"), MapSerializer(String.serializer(), Usage.serializer()), emptyMap(), scope, 2000),
    )
    val apps = AppsRepository(context, metaStore, usage, scope)
    val layout = LayoutRepository(JsonStore(File(dir, "layout.json"), HomeLayout.serializer(), HomeLayout(), scope))
    private val organisationStore = JsonStore(File(dir, "organisation.json"), Organisation.serializer(), Organisation(), scope)
    val organisation = OrganisationRepository(organisationStore)
    private val layoutsStore = JsonStore(File(dir, "layouts.json"), LayoutsState.serializer(), LayoutsState(), scope)
    val layouts = LayoutsRepository(layoutsStore, layout, settings, File(context.filesDir, "layouts"))
    private val wallpapersStore = JsonStore(File(dir, "wallpapers.json"), WallpaperLibraryState.serializer(), WallpaperLibraryState(), scope)
    val wallpapers = WallpaperLibrary(context, wallpapersStore, File(context.filesDir, "wallpaper/library"), File(context.cacheDir, "wallpaper-thumbs"))

    init {
        layout.ensureDefault(settings.get(LauncherSettings.HomeColumns), settings.get(LauncherSettings.HomeRows))
        scope.launch {
            settings.values.map { settings.get(LauncherSettings.ThemeIconShape) }.distinctUntilChanged().collect { apps.iconShape = it }
        }
        scope.launch {
            combine(settings.values, themeTick, packTick) { _, _, pack -> iconConfig(pack) }
                .distinctUntilChanged()
                .collect { config ->
                    _iconPalette.value = config.themed?.let { it.background to it.foreground }
                    apps.iconConfig.value = config
                }
        }
        // A wallpaper the launcher sets itself brings its colours along: recolour at once.
        WallpaperTones.load(context)
        scope.launch {
            // The system id arrives a moment later (after the system took the image): not a new colour.
            WallpaperTones.tone.map { it?.copy(systemId = 0) }.distinctUntilChanged().drop(1).collect {
                DebugLog.event("Icons", "wallpaper colours from the launcher's own image, recolouring now")
                afterOwnWallpaper()
            }
        }
        // Wallpapers set elsewhere: the system's colours, which can arrive late, so look again a little later too.
        WallpaperManager.getInstance(context).addOnColorsChangedListener({ _, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                ColourTiming.mark("system reported new wallpaper colours")
                checkWallpaperColours(reloadPacks = true)
                scope.launch {
                    delay(2000)
                    checkWallpaperColours()
                    delay(4000)
                    checkWallpaperColours()
                }
            }
        }, Handler(Looper.getMainLooper()))
        context.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = checkWallpaperColours(reloadPacks = true)
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        })
    }

    /**
     * Updates what follows the wallpaper colours if they changed (or always with [force]).
     * [reloadPacks] also loads icon packs again that may follow the system colours. Cheap
     * when nothing changed.
     */
    fun checkWallpaperColours(force: Boolean = false, reloadPacks: Boolean = false) {
        scope.launch {
            val themed = settings.get(LauncherSettings.ThemedIcons)
            val palette = if (themed) palette() else null
            val colours = Triple(ThemedColors.seed(context), ThemedColors.isLight(context), palette)
            val changed = colours != lastColours
            if (force || changed) {
                lastColours = colours
                _colourTick.value++
                if (themed) themeTick.value++
                if (changed && palette != null) ColourTiming.mark("palette from the data layer")
            }
            if (themed && reloadPacks && settings.get(LauncherSettings.IconPack).isNotBlank()) packTick.value++
        }
    }

    /**
     * Called when the launcher has just shown a wallpaper of its own (its colours are known).
     * With the "system" colour source the phone's palette follows only later, so until it
     * changes the new wallpaper's own tones are used instead of the old palette.
     */
    fun afterOwnWallpaper() {
        if (settings.get(LauncherSettings.ThemedIconsColors) == "system") {
            val stale = ThemedColors.palette(context, "system", settings.get(LauncherSettings.ThemedIconsStyle))
            systemHold = SystemHold(stale, android.os.SystemClock.uptimeMillis() + 20_000)
        }
        checkWallpaperColours(force = true)
    }

    /** Themed icon colours for a wallpaper whose main colour is [seed] (one about to be shown), or null when themed icons are off. */
    fun paletteFor(seed: Int): Pair<Int, Int>? {
        if (!settings.get(LauncherSettings.ThemedIcons)) return null
        return ThemedColors.palette(context, settings.get(LauncherSettings.ThemedIconsColors), settings.get(LauncherSettings.ThemedIconsStyle), seed)
    }

    private fun palette(): Pair<Int, Int> {
        val source = settings.get(LauncherSettings.ThemedIconsColors)
        val style = settings.get(LauncherSettings.ThemedIconsStyle)
        val hold = systemHold
        if (source == "system" && hold != null) {
            val tone = WallpaperTones.current(context)
            val system = ThemedColors.palette(context, "system", style)
            if (tone != null && system == hold.stale && android.os.SystemClock.uptimeMillis() < hold.until) {
                return ThemedColors.palette(context, source, style, tone.primary)
            }
            systemHold = null
            return system
        }
        return ThemedColors.palette(context, source, style)
    }

    private fun iconConfig(tick: Int): IconConfig {
        val main = settings.get(LauncherSettings.IconPack)
        fun pick(value: String) = if (value == LauncherSettings.SAME_AS_MAIN) main else value
        val themed = if (settings.get(LauncherSettings.ThemedIcons)) {
            val (background, foreground) = palette()
            val where = settings.get(LauncherSettings.ThemedIconsWhere)
            ThemedIcons(
                home = where != "drawer",
                drawer = where != "home",
                tintUncovered = settings.get(LauncherSettings.ThemedIconsUncovered) == "tint",
                background = background,
                foreground = foreground,
            )
        } else {
            null
        }
        return IconConfig(
            homePack = pick(settings.get(LauncherSettings.IconPackHome)),
            drawerPack = pick(settings.get(LauncherSettings.IconPackDrawer)),
            mask = settings.get(LauncherSettings.IconMask),
            themed = themed,
            packEpoch = if (themed != null) tick else 0,
            shape = settings.get(LauncherSettings.ThemeIconShape),
        )
    }

    // ---- backup ----

    /**
     * Everything the user set up, as one zip: settings (without this phone's counters and
     * measurements), home layout, tags and tabs, app labels / tags / icons, saved layouts, the
     * wallpaper library, and the files under [fileDirs] (wallpapers and the library's pictures,
     * layout wallpapers, fonts), relative to the app's files.
     */
    fun backup(out: java.io.OutputStream, fileDirs: List<String> = listOf("wallpaper", "layouts", "fonts")) {
        val json = LayoutsRepository.json
        java.util.zip.ZipOutputStream(out).use { zip ->
            fun entry(name: String, text: String) {
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            entry("backup.json", "{\"format\":1,\"created\":${System.currentTimeMillis()}}")
            val values = settings.exportValues().filterKeys { !LauncherSettings.isDeviceState(it) }
            entry("settings.json", json.encodeToString(MapSerializer(String.serializer(), String.serializer()), values))
            entry("data/apps.json", json.encodeToString(MapSerializer(String.serializer(), AppMeta.serializer()), metaStore.get()))
            entry("data/layout.json", json.encodeToString(HomeLayout.serializer(), layout.layout.value))
            entry("data/organisation.json", json.encodeToString(Organisation.serializer(), organisationStore.get()))
            entry("data/layouts.json", json.encodeToString(LayoutsState.serializer(), layoutsStore.get()))
            entry("data/wallpapers.json", json.encodeToString(WallpaperLibraryState.serializer(), wallpapersStore.get().copy(folders = emptyList())))
            for (dirName in fileDirs) {
                val root = File(context.filesDir, dirName)
                root.walkTopDown().filter { it.isFile }.forEach { file ->
                    zip.putNextEntry(java.util.zip.ZipEntry("files/" + file.relativeTo(context.filesDir).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        DebugLog.event("Backup", "backup written")
    }

    /**
     * Replaces everything with the backup in [input]. Returns null on success or what went
     * wrong (then nothing was changed). The caller reloads the wallpaper afterwards.
     */
    fun restore(input: java.io.InputStream): String? {
        val json = LayoutsRepository.json
        val texts = HashMap<String, String>()
        val files = HashMap<String, ByteArray>()
        try {
            java.util.zip.ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name
                    // Never write outside the app's files.
                    if (name.contains("..")) continue
                    if (name.startsWith("files/")) files[name.removePrefix("files/")] = zip.readBytes()
                    else texts[name] = zip.readBytes().decodeToString()
                }
            }
            if ("backup.json" !in texts) return "This is not a Liquid Launcher backup"
            val values = texts["settings.json"]?.let { json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it) }
            val meta = texts["data/apps.json"]?.let { json.decodeFromString(MapSerializer(String.serializer(), AppMeta.serializer()), it) }
            val home = texts["data/layout.json"]?.let { json.decodeFromString(HomeLayout.serializer(), it) }
            val org = texts["data/organisation.json"]?.let { json.decodeFromString(Organisation.serializer(), it) }
            val saved = texts["data/layouts.json"]?.let { json.decodeFromString(LayoutsState.serializer(), it) }
            val library = texts["data/wallpapers.json"]?.let { json.decodeFromString(WallpaperLibraryState.serializer(), it) }
            // Decoded everything without errors: now apply.
            for ((path, bytes) in files) {
                val target = File(context.filesDir, path)
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
            }
            if (values != null) {
                val keys = (settings.values.value.keys + values.keys).filter { !LauncherSettings.isDeviceState(it) }.toSet()
                settings.importValues(values, keys)
            }
            meta?.let { metaStore.set(it) }
            home?.let { layout.commit(it) }
            org?.let { organisationStore.set(it) }
            saved?.let { layoutsStore.set(it) }
            // Linked phone folders need the picker's permission again on a new phone, so they are not restored.
            library?.let { restored -> wallpapersStore.set(restored.copy(folders = wallpapersStore.get().folders)) }
            DebugLog.event("Backup", "restored (${files.size} files)")
            return null
        } catch (e: Exception) {
            DebugLog.error("Backup", "restore failed: $e")
            return e.message ?: e.toString()
        }
    }

    companion object {
        @Volatile
        private var instance: LauncherData? = null

        fun get(context: Context): LauncherData =
            instance ?: synchronized(this) {
                instance ?: LauncherData(context.applicationContext).also { instance = it }
            }
    }
}
