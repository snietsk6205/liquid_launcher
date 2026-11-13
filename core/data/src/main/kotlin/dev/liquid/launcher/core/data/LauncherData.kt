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

    /** Bumped when the wallpaper colours change, so themed icons and colour-following packs are redrawn. */
    private val themeTick = MutableStateFlow(0)
    @Volatile
    private var lastPalette: Pair<Int, Int>? = null
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

    init {
        layout.ensureDefault(settings.get(LauncherSettings.HomeColumns), settings.get(LauncherSettings.HomeRows))
        scope.launch {
            settings.values.map { settings.get(LauncherSettings.ThemeIconShape) }.distinctUntilChanged().collect { apps.iconShape = it }
        }
        scope.launch {
            combine(settings.values, themeTick) { _, tick -> iconConfig(tick) }
                .distinctUntilChanged()
                .collect { apps.iconConfig.value = it }
        }
        // Wallpaper colours change a moment before the system palette follows, so look again a little later too.
        WallpaperManager.getInstance(context).addOnColorsChangedListener({ _, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                checkWallpaperColours()
                scope.launch {
                    delay(2000)
                    checkWallpaperColours(force = true)
                    delay(4000)
                    checkWallpaperColours()
                }
            }
        }, Handler(Looper.getMainLooper()))
        context.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = checkWallpaperColours()
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        })
    }

    /**
     * Redraws themed icons if the wallpaper colours changed (or always with [force], which
     * also reloads icon packs that use the system colours). Cheap when nothing changed.
     */
    fun checkWallpaperColours(force: Boolean = false) {
        if (!settings.get(LauncherSettings.ThemedIcons)) return
        scope.launch {
            val palette = palette()
            if (force || palette != lastPalette) {
                DebugLog.event("Icons", "wallpaper colours changed, redrawing themed icons")
                themeTick.value++
            }
        }
    }

    private fun palette(): Pair<Int, Int> =
        ThemedColors.palette(context, settings.get(LauncherSettings.ThemedIconsColors), settings.get(LauncherSettings.ThemedIconsStyle))

    private fun iconConfig(tick: Int): IconConfig {
        val main = settings.get(LauncherSettings.IconPack)
        fun pick(value: String) = if (value == LauncherSettings.SAME_AS_MAIN) main else value
        val themed = if (settings.get(LauncherSettings.ThemedIcons)) {
            val (background, foreground) = palette().also { lastPalette = it }
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
     * measurements), home layout, tags and tabs, app labels / tags / icons, saved layouts, and
     * the files under [fileDirs] (wallpapers, layout wallpapers), relative to the app's files.
     */
    fun backup(out: java.io.OutputStream, fileDirs: List<String> = listOf("wallpaper", "layouts")) {
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
