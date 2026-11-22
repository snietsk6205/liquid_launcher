package dev.liquid.launcher.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import dev.liquid.launcher.core.settings.SettingDependencies
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.WallpaperStore
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsEntry
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.observe
import dev.liquid.launcher.feature.redirect.RedirectStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun MainScreen(
    store: SettingsStore,
    wallpaper: WallpaperStore,
    open: (Screen) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    ScreenScaffold(title = "Liquid Launcher", onBack = onBack) {
        // 0.15.0 (D191): WM Keyboard's search field, a pill on the page's own surface.
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search settings") },
            leadingIcon = { LauncherIcon(LauncherIcons.Search, contentDescription = null) },
            trailingIcon = if (query.isEmpty()) null else ({
                androidx.compose.material3.IconButton(onClick = { query = "" }) { LauncherIcon(LauncherIcons.Close, contentDescription = "Clear") }
            }),
            shape = RoundedCornerShape(50),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )

        UpdateBanner(store) { open(Screen.Updates) }

        if (query.isNotBlank()) {
            // W4: a result opens its page on its tab, with the option lit up.
            val results = remember(query) { LauncherSettings.search(query) }
            // 0.10.2: a page's own editors (the search page's cards, the pill's items…) are found too.
            val editors = remember(query) { dev.liquid.launcher.core.settings.SettingsMap.searchEditors(query) }
            if (results.isEmpty() && editors.isEmpty()) {
                BodyText("No option matches \"$query\"", Modifier.padding(20.dp))
            }
            GroupCards {
                editors.forEachIndexed { i, e ->
                    val surface = dev.liquid.launcher.core.settings.SettingsMap.surface(e.surfaceId)
                    Box(Modifier.staggerIn(i)) {
                        NavRow(
                            e.title,
                            listOfNotNull(surface?.entry?.title, surface?.title, e.tab.title).joinToString(" › ") + "\n" + e.summary,
                            icon = surface?.let { surfaceIcon(it.id, it.entry) },
                            accent = surface?.let { entryAccent(it.entry) },
                        ) {
                            open(Screen.Page(e.surfaceId, e.tab))
                        }
                    }
                }
                results.take(60).forEachIndexed { i, setting -> Box(Modifier.staggerIn(editors.size + i)) { SearchResultRow(setting, open, query) } }
            }
            return@ScreenScaffold
        }

        StatusCard(store)

        // What was changed last, to carry on where you were or to undo an experiment.
        val recent by store.observe(LauncherSettings.RecentlyChanged)
        val recentSettings = remember(recent) {
            recent.mapNotNull { key -> LauncherSettings.all.firstOrNull { it.key == key && !it.hidden } }.take(4)
        }
        if (recentSettings.isNotEmpty()) {
            SectionHeader("Recently changed")
            GroupCards { recentSettings.forEach { setting -> SearchResultRow(setting, open) } }
        }

        // W4: seven entries; the pages of each are one surface of the launcher. 0.15.0: each in its own hue, its tile
        // flying into the heading of the entry it opens.
        SectionHeader("Settings")
        GroupCards {
            SettingsEntry.entries.forEachIndexed { i, entry ->
                Box(Modifier.staggerIn(i)) {
                    NavRow(
                        entry.title, entry.summary,
                        icon = entryIcon(entry), accent = entryAccent(entry), flightKey = "entry:${entry.name}",
                    ) { open(Screen.Entry(entry)) }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(store: SettingsStore) {
    val context = LocalContext.current
    val connected by RedirectStatus.connected.collectAsStateCompat()
    val state by RedirectStatus.engineState.collectAsStateCompat()
    val systemLauncher by RedirectStatus.systemLauncher.collectAsStateCompat()
    val version by RedirectStatus.systemLauncherVersion.collectAsStateCompat()
    val blocked by RedirectStatus.launchBlocked.collectAsStateCompat()
    val pausedUntil by RedirectStatus.pausedUntilWall.collectAsStateCompat()
    val enabled by store.observe(LauncherSettings.RedirectEnabled)
    val verified by store.observe(LauncherSettings.DetectorVerified)
    val pauseSeconds by store.observe(LauncherSettings.SystemHomePause)
    // Re-read counters and the pause countdown once a second while this screen is open.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val paused = pausedUntil > now

    InfoCard {
        Row {
            StatusDot(ok = connected && enabled && !blocked)
            Spacer(Modifier.width(10.dp))
            Text(
                text = when {
                    !connected -> "Redirect service is not running"
                    !enabled -> "Redirect is switched off"
                    blocked -> "Launches are being blocked"
                    paused -> "Paused for ${((pausedUntil - now) / 1000).coerceAtLeast(0)} s"
                    else -> "Redirect is active"
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (connected) {
            BodyText(
                buildString {
                    append("Watching: ").append(systemLauncher)
                    if (version.isNotEmpty()) append("\nVersion: ").append(version)
                    append("\nState: ").append(state.name.lowercase())
                    append("\nRecents detection: ").append(if (verified) "confirmed" else "not seen yet (open recents once)")
                    append("\nRedirects: ").append(RedirectStatus.launchesConfirmed.get())
                        .append(" confirmed of ").append(RedirectStatus.launchesRequested.get())
                    append("\nLaunch to first frame: ").append(RedirectStatus.launchLatency.summary())
                },
                Modifier.padding(top = 8.dp),
            )
            Row(Modifier.padding(top = 10.dp)) {
                if (paused) {
                    OutlinedButton(onClick = { RedirectStatus.resume() }) { Text("Resume") }
                } else {
                    // Pausing is for using the system launcher, so it opens it; the redirect comes back by itself.
                    OutlinedButton(onClick = { RedirectStatus.openSystemLauncher(context, 5 * 60_000L) }) {
                        Text("Pause 5 min")
                    }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { RedirectStatus.openSystemLauncher(context, pauseSeconds * 1000L) }) {
                    Text("Open system launcher")
                }
            }
        } else {
            BodyText("Open the setup checklist to switch it on.", Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
internal fun WallpaperRow(wallpaper: WallpaperStore) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val image by wallpaper.image.collectAsStateCompat()
    // The wallpaper screen lives on the home screen, where every change is seen on the real thing.
    NavRow(
        title = "Wallpaper",
        summary = if (image != null) {
            "Your library, placing, parallax, blur and dim, changing automatically. Opens on the home screen"
        } else {
            "The system wallpaper shows through. Tap to pick one from your library or photos"
        },
    ) { openOnHome(context, "wallpaper") }
    if (image != null) {
        NavRow("Stop managing the wallpaper", "The system wallpaper stays as it is") {
            scope.launch { wallpaper.clear() }
        }
    }
}
