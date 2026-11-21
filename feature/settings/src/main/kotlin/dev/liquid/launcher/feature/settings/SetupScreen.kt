package dev.liquid.launcher.feature.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.designsystem.WallpaperStore
import dev.liquid.launcher.core.settings.BoolSetting
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.observe
import dev.liquid.launcher.feature.redirect.LauncherAccessibilityService
import dev.liquid.launcher.feature.redirect.RedirectStatus
import kotlinx.coroutines.delay

internal object SetupChecks {
    fun accessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val mine = ComponentName(context, LauncherAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }

    fun batteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun overlayAllowed(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** HONOR / Huawei "App launch" page, where automatic management must be switched off. */
    fun appLaunchIntents(context: Context): Array<Intent> = arrayOf(
        Intent().setComponent(
            ComponentName(
                "com.hihonor.systemmanager",
                "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            )
        ),
        appDetails(context),
    )
}

@Composable
internal fun SetupScreen(
    store: SettingsStore,
    wallpaper: WallpaperStore,
    open: (Screen) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // Settings pages are separate apps, so re-check once a second while this screen is open.
    val tick by produceState(0) {
        while (true) {
            delay(1000)
            value++
        }
    }
    val connected by RedirectStatus.connected.collectAsStateCompat()
    val blocked by RedirectStatus.launchBlocked.collectAsStateCompat()
    val pauseSeconds by store.observe(LauncherSettings.SystemHomePause)
    val accessibilityOn = tick.let { SetupChecks.accessibilityEnabled(context) }
    val batteryOk = tick.let { SetupChecks.batteryUnrestricted(context) }
    val overlayOk = tick.let { SetupChecks.overlayAllowed(context) }

    ScreenScaffold(title = "Setup checklist", onBack = onBack) {
        BodyText(
            "Steps 1 to 4 are needed. The rest is optional. Each step shows a tick once it is done.",
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        Step(
            number = 1,
            title = "Switch on the redirect service",
            done = accessibilityOn && connected,
            text = if (accessibilityOn && !connected) {
                "The service is enabled but not running. Switch it off and on again in Accessibility."
            } else {
                "Accessibility → Installed apps (or Downloaded apps) → Liquid Launcher redirect → On.\n" +
                    "If the switch is greyed out: open App info, tap ⋮ in the top right corner, " +
                    "choose \"Allow restricted settings\", then try again."
            },
        ) {
            OutlinedButton(onClick = { startSafely(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                Text("Accessibility")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { startSafely(context, SetupChecks.appDetails(context)) }) { Text("App info") }
        }

        Step(
            number = 2,
            title = "Allow background activity",
            done = batteryOk,
            text = "Lets the service keep running. Choose \"Allow\" in the dialog.",
        ) {
            OutlinedButton(onClick = {
                startSafely(
                    context,
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}"),
                    ),
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                )
            }) { Text("Battery setting") }
        }

        ManualStep(
            number = 3,
            title = "App launch: manage manually",
            text = "Settings → Apps → App launch → Liquid Launcher → switch off \"Manage automatically\" " +
                "and keep all three switches on (Auto-launch, Secondary launch, Run in background). " +
                "Without this, HONOR may stop the service after some hours.",
            setting = LauncherSettings.SetupAppLaunchDone,
            store = store,
        ) {
            OutlinedButton(onClick = { startSafely(context, *SetupChecks.appLaunchIntents(context)) }) {
                Text("App launch")
            }
        }

        ManualStep(
            number = 4,
            title = "Empty the HONOR Home screen",
            text = "Keep HONOR Home in drawer style and remove every icon and widget from its home screen " +
                "and dock. Then the moment before the launcher appears shows only wallpaper. " +
                "The button opens HONOR Home and pauses the redirect for $pauseSeconds seconds.",
            setting = LauncherSettings.SetupEmptyHomeDone,
            store = store,
        ) {
            OutlinedButton(onClick = { RedirectStatus.openSystemLauncher(context, pauseSeconds * 1000L) }) {
                Text("Open HONOR Home")
            }
        }

        ManualStep(
            number = 5,
            title = "Disable the MacroDroid macros",
            text = "Switch off \"Home after recents\", \"Best w\" and \"Best 2\", otherwise both will react.",
            setting = LauncherSettings.SetupMacrosDone,
            store = store,
        ) {}

        SectionHeader("Optional")
        WallpaperRow(wallpaper)

        Step(
            number = 6,
            title = "Display over other apps",
            done = if (overlayOk) true else null,
            text = if (blocked) {
                "The launcher did not come to the front. Granting this permission is the fallback that allows it."
            } else {
                "Only needed if the status card says launches are being blocked."
            },
        ) {
            OutlinedButton(onClick = {
                startSafely(
                    context,
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                )
            }) { Text("Permission") }
        }

        NavRow("Run diagnostics", "Checks this phone and produces a report to share") { open(Screen.Diagnostics) }
    }
}

@Composable
private fun Step(
    number: Int,
    title: String,
    done: Boolean?,
    text: String,
    actions: @Composable () -> Unit,
) {
    InfoCard {
        Row {
            StatusDot(ok = if (done == true) true else null)
            Spacer(Modifier.width(10.dp))
            Text("$number. $title", style = MaterialTheme.typography.titleMedium)
        }
        BodyText(text, Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 8.dp)) { actions() }
    }
}

/** A step the app cannot verify itself; the user ticks it. */
@Composable
private fun ManualStep(
    number: Int,
    title: String,
    text: String,
    setting: BoolSetting,
    store: SettingsStore,
    actions: @Composable () -> Unit,
) {
    val done by store.observe(setting)
    InfoCard {
        Row {
            StatusDot(ok = if (done) true else null)
            Spacer(Modifier.width(10.dp))
            Text("$number. $title", style = MaterialTheme.typography.titleMedium)
        }
        BodyText(text, Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 8.dp)) {
            actions()
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { store.set(setting, !done) }) {
                Text(if (done) "Mark as not done" else "I did this")
            }
        }
    }
}
