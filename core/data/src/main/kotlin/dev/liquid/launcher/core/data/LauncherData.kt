package dev.liquid.launcher.core.data

import android.content.Context
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
class LauncherData private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
            settings.values
                .map { iconConfig() }
                .distinctUntilChanged()
                .collect { apps.iconConfig.value = it }
        }
    }

    private fun iconConfig(): IconConfig {
        val main = settings.get(LauncherSettings.IconPack)
        fun pick(value: String) = if (value == LauncherSettings.SAME_AS_MAIN) main else value
        return IconConfig(
            homePack = pick(settings.get(LauncherSettings.IconPackHome)),
            drawerPack = pick(settings.get(LauncherSettings.IconPackDrawer)),
            mask = settings.get(LauncherSettings.IconMask),
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
