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
    val organisation = OrganisationRepository(
        JsonStore(File(dir, "organisation.json"), Organisation.serializer(), Organisation(), scope),
    )

    init {
        layout.ensureDefault(settings.get(LauncherSettings.HomeColumns), settings.get(LauncherSettings.HomeRows))
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
        )
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
