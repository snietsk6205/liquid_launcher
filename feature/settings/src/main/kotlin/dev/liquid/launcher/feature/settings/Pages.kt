package dev.liquid.launcher.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.designsystem.WallpaperStore
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.Setting
import dev.liquid.launcher.core.settings.SettingDependencies
import dev.liquid.launcher.core.settings.SettingsEntry
import dev.liquid.launcher.core.settings.SettingsMap
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.SettingsTab
import kotlinx.coroutines.delay

/*
 * W4 (0.10.1): Settings in seven entries, each a list of pages; a page is one surface of the launcher with its
 * live preview fixed at the top and its options in tabs (Layout, Look, Behaviour, More). Search and "Recently
 * changed" open the page on the option's tab and light the option up.
 */

/** What a row needs to know about where it is drawn: why it does nothing now, and whether search led here. */
internal class RowContext(val whyOff: String?, val highlighted: Boolean)

internal val LocalRowContext = staticCompositionLocalOf { RowContext(null, false) }

/** One entry of the first screen: its pages, and the screens that belong to it. */
@Composable
internal fun EntryScreen(store: SettingsStore, wallpaper: WallpaperStore, entry: SettingsEntry, open: (Screen) -> Unit, onBack: () -> Unit) {
    ScreenScaffold(title = entry.title, onBack = onBack) {
        BodyText(entry.summary, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        when (entry) {
            SettingsEntry.LOOK -> NavRow(
                "Theme lab",
                "Every option of your WM Keyboard theme on every button, chip, pill, sheet and menu, with a live preview, presets, import and export",
            ) { open(Screen.ThemeLab) }
            SettingsEntry.WALLPAPER -> WallpaperRow(wallpaper)
            else -> Unit
        }
        for (surface in SettingsMap.surfacesOf(entry)) {
            val count = SettingsMap.settingsOf(surface).size
            NavRow(surface.title, surface.summary + if (count > 0) " · $count options" else "") { open(Screen.Page(surface.id)) }
        }
        if (entry == SettingsEntry.SYSTEM) {
            SectionHeader("Tools")
            NavRow("Setup checklist", "Permissions and phone settings the redirect needs") { open(Screen.Setup) }
            NavRow("Diagnostics", "Device checks and the glass benchmark") { open(Screen.Diagnostics) }
            NavRow("Calibrate recents detection", "Only needed if recents is not recognised") { open(Screen.Calibration) }
            NavRow("Debug log", "What the redirect saw and decided, frame times, how long wallpaper changes took") { open(Screen.Log) }
            NavRow("Updates", "Check GitHub for a new build and install it over this one") { open(Screen.Updates) }
            NavRow("All changes", "What changed in each version") { open(Screen.Changelog) }
            NavRow("About", "Version and credits") { open(Screen.About) }
        }
    }
}

/** The 0.9 section shown at the top of a page as its live preview (the home module draws it). */
private fun previewSection(surface: String, tab: SettingsTab): String? = when (surface) {
    "menus" -> "homeMenu"
    "index" -> "drawerIndex"
    "moving" -> "effects"
    "drawer" -> if (tab == SettingsTab.BEHAVIOUR) "drawerMotion" else "drawer"
    else -> surface
}.takeIf { it in SettingsPreviews.sections }

/** Tabs that hold an editor of their own (lists, packs, presets) besides options. */
private val editorTabs: Map<String, Set<SettingsTab>> = mapOf(
    "pill" to setOf(SettingsTab.LAYOUT, SettingsTab.LOOK),
    "menus" to setOf(SettingsTab.LAYOUT),
    "search" to setOf(SettingsTab.LAYOUT),
    "tags" to setOf(SettingsTab.LAYOUT),
    "index" to setOf(SettingsTab.LAYOUT),
    "icons" to setOf(SettingsTab.LOOK),
    "theme" to setOf(SettingsTab.LOOK),
    "glass" to setOf(SettingsTab.LOOK),
    "effects" to setOf(SettingsTab.LOOK),
    "buttons" to setOf(SettingsTab.LOOK),
    "wallpaper" to setOf(SettingsTab.LOOK),
    "study" to setOf(SettingsTab.LAYOUT, SettingsTab.MORE),
    "layouts" to setOf(SettingsTab.LAYOUT),
    "backup" to setOf(SettingsTab.BEHAVIOUR, SettingsTab.MORE),
    "redirect" to setOf(SettingsTab.LOOK),
    "keyboard" to setOf(SettingsTab.LOOK, SettingsTab.BEHAVIOUR),
)

@Composable
private fun PageEditors(surface: String, tab: SettingsTab, store: SettingsStore, open: (Screen) -> Unit) {
    when (surface to tab) {
        "pill" to SettingsTab.LAYOUT -> {
            PillItemsEditor(store)
            LiveMusicAccessCard(store)
        }
        "pill" to SettingsTab.LOOK -> PillStudyEditor(store)
        "menus" to SettingsTab.LAYOUT -> {
            HomeMenuEditor(store)
            MenuEntriesEditor(store)
        }
        "search" to SettingsTab.LAYOUT -> SearchCardsEditor(store)
        // 0.12.0 (D171, D172): WM Keyboard and the launcher.
        "keyboard" to SettingsTab.LOOK -> {
            // 0.13.1 (D180): the wallpaper behind the keys, then the theme it goes with.
            KeyboardWallpaperEditor(store)
            KeyboardThemeEditor(store)
        }
        "keyboard" to SettingsTab.BEHAVIOUR -> KeyboardModeGuide()
        // 0.11.3 (D170): the arrival's live preview is the real thing: the system's home, then the redirect.
        "redirect" to SettingsTab.LOOK -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            NavRow("Try the arrival", "Shows the system's home screen; the launcher comes back the way set above (Settings closes)") {
                if (!dev.liquid.launcher.feature.redirect.RedirectStatus.tryArrival(context)) {
                    android.widget.Toast.makeText(context, "The redirect is not running yet", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        "index" to SettingsTab.LAYOUT -> LetterAreasEditor(store)
        "tags" to SettingsTab.LAYOUT -> TabsAndTagsEditor()
        "icons" to SettingsTab.LOOK -> IconPacksEditor(store)
        "theme" to SettingsTab.LOOK -> {
            NavRow("Theme lab", "The accent, the colours of every control, fonts and sizes of the theme live there") { open(Screen.ThemeLab) }
            ThemeEditor(store)
        }
        "glass" to SettingsTab.LOOK -> {
            NavRow(
                "Tune the glass",
                "Every value of the real glass on a live picture over your wallpaper, with presets; measures the frame rate too",
            ) { open(Screen.GlassTune) }
            LooksEditor(store)
        }
        "effects" to SettingsTab.LOOK -> LooksEditor(store)
        "buttons" to SettingsTab.LOOK -> NavRow(
            "Theme lab",
            "Every option of your WM Keyboard theme on every button, chip, pill, sheet and menu, with a live preview, presets, import and export",
        ) { open(Screen.ThemeLab) }
        "wallpaper" to SettingsTab.LOOK -> WallpaperEditor()
        "study" to SettingsTab.LAYOUT -> StudyEditor(store)
        "study" to SettingsTab.MORE -> CameraAppEditor(store)
        "layouts" to SettingsTab.LAYOUT -> LayoutsAndBackupEditor()
        "backup" to SettingsTab.BEHAVIOUR -> BackupEditor(store)
        "backup" to SettingsTab.MORE -> ClaudeEditor(store)
    }
}

/** A page: the preview stays while the tab's options scroll under it. */
@Composable
internal fun PageScreen(store: SettingsStore, surfaceId: String, startTab: SettingsTab?, highlight: String?, open: (Screen) -> Unit, onBack: () -> Unit) {
    val surface = SettingsMap.surface(surfaceId) ?: SettingsMap.visibleSurfaces.first()
    val tabs = remember(surfaceId) {
        SettingsTab.entries.filter { SettingsMap.settingsOf(surface, it).isNotEmpty() || it in editorTabs[surface.id].orEmpty() }
            .ifEmpty { listOf(SettingsTab.MORE) }
    }
    var tab by rememberSaveable(surfaceId) { mutableStateOf(startTab?.takeIf { it in tabs } ?: tabs.first()) }
    val values by store.values.collectAsStateCompat()
    val settings = remember(surfaceId, tab) { SettingsMap.settingsOf(surface, tab) }
    val preview = SettingsPreviews.provider?.let { p -> previewSection(surface.id, tab)?.let { id -> p to id } }

    ScreenScaffold(title = surface.title, onBack = onBack, scroll = false) {
        if (preview != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))) { preview.first(preview.second) }
            }
        }
        if (tabs.size > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (t in tabs) {
                    val changed = SettingsMap.settingsOf(surface, t).count { store.isChanged(it, values) }
                    FilterChip(
                        selected = t == tab,
                        onClick = { tab = t },
                        label = { Text(if (changed > 0) "${t.title} · $changed" else t.title) },
                    )
                }
            }
        }
        val scroll = rememberScrollState()
        var target by remember(surfaceId, tab, highlight) { mutableFloatStateOf(-1f) }
        LaunchedEffect(target) {
            if (target >= 0f) scroll.animateScrollTo((target - 120f).toInt().coerceAtLeast(0))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scroll)
                .padding(bottom = 24.dp),
        ) {
            if (tab == tabs.first()) {
                BodyText(
                    surface.summary + ". Hold an option to reset it, explain it or copy its id; a dot marks what you changed.",
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            PageEditors(surface.id, tab, store, open)
            // What most changes are about first; the fine tuning waits under "Advanced", open when it holds the
            // option search led to or something changed.
            val (advanced, basic) = settings.partition { LauncherSettings.isAdvanced(it) }
            basic.forEach { PageRow(it, store, highlight) { y -> target = y } }
            if (advanced.isNotEmpty()) {
                var expanded by remember(surfaceId, tab) {
                    mutableStateOf(basic.isEmpty() || advanced.any { store.isChanged(it, values) || it.key == highlight })
                }
                NavRow(
                    if (expanded) "Advanced" else "Advanced (${advanced.size} more)",
                    if (expanded) "Fine tuning; the defaults are chosen to work" else advanced.take(3).joinToString(", ") { it.title } + if (advanced.size > 3) "…" else "",
                ) { expanded = !expanded }
                if (expanded) advanced.forEach { PageRow(it, store, highlight) { y -> target = y } }
            }
            if (settings.any { store.isChanged(it, values) }) {
                OutlinedButton(
                    onClick = { settings.forEach { store.reset(it) } },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                ) { Text("Reset ${tab.title}") }
            }
        }
    }
}

/** A row on a page: greyed with its reason while what it needs is off, lit for a moment when search led to it. */
@Composable
private fun PageRow(setting: Setting<*>, store: SettingsStore, highlight: String?, onPlaced: (Float) -> Unit) {
    val values by store.values.collectAsStateCompat()
    val why = remember(values, setting) { SettingDependencies.whyOff(setting, store) }
    val lit = setting.key == highlight
    var glow by remember(setting.key, highlight) { mutableStateOf(lit) }
    if (lit) LaunchedEffect(highlight) {
        delay(2200)
        glow = false
    }
    val background by animateColorAsState(
        if (glow) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
        tween(700), label = "settingGlow",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .background(background)
            .then(if (lit) Modifier.onGloballyPositioned { onPlaced(it.positionInParent().y) } else Modifier),
    ) {
        CompositionLocalProvider(LocalRowContext provides RowContext(why, lit)) { SettingRow(setting, store) }
    }
}

/** A search result: the option and where it lives; a tap opens its page on its tab. */
@Composable
internal fun SearchResultRow(setting: Setting<*>, open: (Screen) -> Unit) {
    val surface = SettingsMap.surfaceOf(setting)
    val tab = SettingsMap.tabOf(setting)
    NavRow(setting.title, SettingsMap.path(setting) + if (setting.summary.isNotEmpty()) "\n" + setting.summary else "") {
        open(Screen.Page(surface.id, tab, setting.key))
    }
}

/** The page and tab a 0.9 section link opens. */
internal fun pageForSection(section: String): Screen.Page? =
    SettingsMap.forSection(section)?.let { (surface, tab) -> Screen.Page(surface.id, tab) }
