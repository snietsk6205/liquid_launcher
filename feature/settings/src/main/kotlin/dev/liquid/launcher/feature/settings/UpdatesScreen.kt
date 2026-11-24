package dev.liquid.launcher.feature.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.common.Changelog
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.observe
import kotlinx.coroutines.launch
import java.io.File

/** What the updater is doing, shared by the updates screen and the banner on the main screen. */
internal object UpdateState {
    var result by mutableStateOf<UpdateCheck?>(null)
}

/** Check for a new build on GitHub, download it and install it over this one. */
@Composable
internal fun UpdatesScreen(store: SettingsStore, open: (Screen) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val daily by store.observe(LauncherSettings.UpdatesDaily)
    var checking by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var apk by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val installed = remember { "${Updater.installedVersion(context)} (build ${Updater.installedBuild(context)})" }

    fun check() {
        scope.launch {
            checking = true
            message = null
            UpdateState.result = Updater.check(context)
            checking = false
        }
    }

    fun install(file: File) {
        if (!Updater.canInstall(context)) {
            message = "Android asks once whether Liquid Launcher may install apps. Allow it on the next screen, come back, then tap Install."
            Updater.openInstallPermission(context)
            return
        }
        Updater.install(context, file)
    }

    ScreenScaffold(title = "Updates", onBack = onBack) {
        InfoCard {
            Text("Installed", style = MaterialTheme.typography.titleMedium)
            BodyText(installed)
            Row(Modifier.padding(top = 8.dp)) {
                Button(onClick = { check() }, enabled = !checking && progress == null) { Text(if (checking) "Checking…" else "Check for updates") }
            }
            when (val result = UpdateState.result) {
                is UpdateCheck.Current -> BodyText("This is the latest build (${result.release.version}, build ${result.release.build}).", Modifier.padding(top = 8.dp))
                is UpdateCheck.Failed -> BodyText(result.message, Modifier.padding(top = 8.dp))
                is UpdateCheck.Newer -> {
                    val release = result.release
                    BodyText(
                        "${release.version} (build ${release.build}) is available" +
                            if (release.size > 0) ", ${release.size / (1024 * 1024)} MB." else ".",
                        Modifier.padding(top = 8.dp),
                    )
                    // W1: what it brings, before installing (the release's changelog section).
                    val notes = remember(release) { Changelog.parseSection(release.version, null, release.notes) }
                    if (notes.itemCount > 0) ChangesView(notes, "What's new in ${release.version}", Modifier.padding(top = 12.dp))
                    val p = progress
                    val file = apk
                    when {
                        p != null -> LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        file != null -> Row(Modifier.padding(top = 8.dp)) { Button(onClick = { install(file) }) { Text("Install") } }
                        else -> Row(Modifier.padding(top = 8.dp)) {
                            Button(onClick = {
                                scope.launch {
                                    progress = 0f
                                    try {
                                        val downloaded = Updater.download(context, release) { progress = it }
                                        apk = downloaded
                                        progress = null
                                        install(downloaded)
                                    } catch (e: Exception) {
                                        progress = null
                                        message = "Download failed: ${e.message ?: e.javaClass.simpleName}"
                                    }
                                }
                            }) { Text("Download and install") }
                        }
                    }
                }
                null -> Unit
            }
            message?.let { BodyText(it, Modifier.padding(top = 8.dp)) }
        }
        NavRow("All changes", "What each version added, changed, removed and fixed") { open(Screen.Changelog) }
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Check once a day", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = daily, onCheckedChange = { store.set(LauncherSettings.UpdatesDaily, it) })
            }
            BodyText("When settings opens, at most once a day. Off: only when you tap \"Check for updates\".")
        }
        BodyText(
            "Builds come from the releases of github.com/${Updater.REPOSITORY}. They are signed with the same key, so an update installs over " +
                "this one and keeps all your settings, layouts and wallpapers. The only network use of the launcher is this check and the download.",
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Spacer(Modifier.width(1.dp))
    }
}

/** On the main settings screen: a row when the daily check found a newer build. */
@Composable
internal fun UpdateBanner(store: SettingsStore, open: () -> Unit) {
    val context = LocalContext.current
    val daily by store.observe(LauncherSettings.UpdatesDaily)
    androidx.compose.runtime.LaunchedEffect(daily) {
        if (Updater.dueForDailyCheck(context, daily)) {
            val found = Updater.check(context)
            UpdateState.result = found
            // Also as a glass notice the next time the home screen is in front.
            (found as? UpdateCheck.Newer)?.let { newer ->
                dev.liquid.launcher.core.common.Notices.post(
                    dev.liquid.launcher.core.common.NoticeKind.Update,
                    "Update available: ${newer.release.version} build ${newer.release.build}", "Tap to open settings", "settings",
                )
            }
        }
    }
    val newer = UpdateState.result as? UpdateCheck.Newer ?: return
    NavRow("Update available: ${newer.release.version} (build ${newer.release.build})", "Tap to download and install", icon = dev.liquid.launcher.core.designsystem.LauncherIcons.ArrowDown, onClick = open)
}

/** One version's changes as the changelog words them (W1, 0.10.0): its note, then Added, Changed, Removed, Fixed. */
@Composable
internal fun ChangesView(version: Changelog.Version, heading: String?, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(modifier) {
        if (heading != null) Text(heading, style = MaterialTheme.typography.titleMedium)
        version.note?.let { BodyText(it, Modifier.padding(top = 4.dp)) }
        for (part in version.parts) {
            Text(
                part.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
            )
            for (item in part.items) {
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(14.dp))
                    Text(Changelog.plain(item), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Every version of the changelog shipped in the app, newest first, the installed one marked (W1). */
@Composable
internal fun ChangelogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val installed = remember { Updater.installedVersion(context) }
    val versions = remember {
        runCatching { context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() } }
            .map { Changelog.parse(it) }
            .getOrDefault(emptyList())
    }
    ScreenScaffold(title = "Changelog", onBack = onBack) {
        if (versions.isEmpty()) {
            BodyText(
                "This build has no changelog inside. It is on GitHub: github.com/${Updater.REPOSITORY}/blob/HEAD/CHANGELOG.md",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        for (v in versions) {
            InfoCard {
                val title = v.version + (v.date?.let { " · $it" } ?: "") + if (v.version == installed) " · installed" else ""
                ChangesView(v, title)
            }
        }
    }
}
