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
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search settings") },
            leadingIcon = { LauncherIcon(LauncherIcons.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (query.isNotBlank()) {
            val results = remember(query) { LauncherSettings.search(query) }
            if (results.isEmpty()) {
                BodyText("No option matches \"$query\"", Modifier.padding(20.dp))
            }
            results.forEach { setting ->
                val section = LauncherSettings.Sections.all.firstOrNull { it.id == setting.section }
                if (section != null) {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp, top = 10.dp),
                    )
                }
                SettingRow(setting, store)
            }
            return@ScreenScaffold
        }

        StatusCard(store)

        SectionHeader("Get started")
        NavRow("Setup checklist", "Permissions and phone settings the redirect needs") { open(Screen.Setup) }
        NavRow("Diagnostics", "Device checks and the glass benchmark") { open(Screen.Diagnostics) }

        LauncherSettings.Sections.all.groupBy { it.group }.forEach { (group, sections) ->
            SectionHeader(group)
            sections.forEach { section ->
                NavRow(section.title, section.summary) { open(Screen.Section(section.id)) }
            }
            if (group == "Launcher") WallpaperRow(wallpaper)
        }

        SectionHeader("Tools")
        NavRow("Calibrate recents detection", "Only needed if recents is not recognised") { open(Screen.Calibration) }
        NavRow("Debug log", "What the redirect saw and decided") { open(Screen.Log) }
        NavRow("About", "Version and credits") { open(Screen.About) }
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
                    OutlinedButton(onClick = { RedirectStatus.pause(5 * 60_000L, "paused from settings") }) {
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
    val image by wallpaper.image.collectAsStateCompat()
    var message by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            message = "Setting wallpaper…"
            scope.launch {
                val error = wallpaper.setFromUri(uri)
                message = if (error == null) "Wallpaper set" else "Failed: $error"
            }
        }
    }
    NavRow(
        title = "Wallpaper",
        summary = when {
            message.isNotEmpty() -> message
            image != null -> "Managed by the launcher (glass can refract it). Tap to change"
            else -> "System wallpaper shows through. Tap to pick one the launcher manages"
        },
    ) {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    if (image != null) {
        NavRow("Stop managing the wallpaper", "The system wallpaper stays as it is") {
            scope.launch {
                wallpaper.clear()
                message = ""
            }
        }
    }
}

@Composable
internal fun SectionScreen(store: SettingsStore, sectionId: String, onBack: () -> Unit) {
    val section = LauncherSettings.Sections.all.first { it.id == sectionId }
    val settings = remember(sectionId) { LauncherSettings.inSection(sectionId) }
    val values by store.values.collectAsStateCompat()
    val anyChanged = settings.any { values.containsKey(it.key) }
    val preview = SettingsPreviews.provider?.takeIf { sectionId in SettingsPreviews.sections }

    ScreenScaffold(title = section.title, onBack = onBack, scroll = false) {
        // The preview stays in view while the options below scroll, so every change shows at once.
        if (preview != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))) { preview(sectionId) }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            BodyText(
                section.summary + ". Long-press an option to reset it. A dot marks options you changed.",
                Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            when (sectionId) {
                "pill" -> PillItemsEditor(store)
                "menu" -> MenuEntriesEditor(store)
                "tags" -> TabsAndTagsEditor()
                "icons" -> IconPacksEditor(store)
                "homeMenu" -> HomeMenuEditor(store)
                "wallpaper" -> WallpaperEditor()
                "layouts" -> LayoutsAndBackupEditor()
            }
            settings.forEach { setting ->
                if (SettingDependencies.isVisible(setting, store)) SettingRow(setting, store)
            }
            if (anyChanged) {
                OutlinedButton(
                    onClick = { settings.forEach { store.reset(it) } },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                ) { Text("Reset this section") }
            }
        }
    }
}
