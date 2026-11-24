package dev.liquid.launcher.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.positionInRoot
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
 * W4 (0.10.1): Settings in seven entries (eight since 0.16.0), each a list of pages; a page is one surface of the launcher with its
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
        BodyText(entry.summary, Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        // 0.15.0 (D191): the pages as one group of cards, each with its icon in the entry's hue (its tile flies into the
        // page's heading), coming in one after the other.
        var row = 0
        GroupCards {
            when (entry) {
                SettingsEntry.LOOK -> Box(Modifier.staggerIn(row++)) {
                    NavRow(
                        "Theme lab",
                        "Every option of your WM Keyboard theme on every button, chip, pill, sheet and menu, with a live preview, presets, import and export",
                        icon = dev.liquid.launcher.core.designsystem.LauncherIcons.Palette, flightKey = "tool:themeLab",
                    ) { open(Screen.ThemeLab) }
                }
                SettingsEntry.WALLPAPER -> WallpaperRow(wallpaper)
                else -> Unit
            }
            for (surface in SettingsMap.surfacesOf(entry)) {
                val count = SettingsMap.settingsOf(surface).size
                Box(Modifier.staggerIn(row++)) {
                    NavRow(
                        surface.title, surface.summary,
                        icon = surfaceIcon(surface.id, entry), value = if (count > 0) "$count" else null, flightKey = "page:${surface.id}",
                    ) { open(Screen.Page(surface.id)) }
                }
            }
        }
        if (entry == SettingsEntry.SYSTEM) {
            SectionHeader("Tools")
            val icons = dev.liquid.launcher.core.designsystem.LauncherIcons
            GroupCards {
                NavRow("Setup checklist", "Permissions and phone settings the redirect needs", icon = icons.CheckCircle, flightKey = "tool:setup") { open(Screen.Setup) }
                NavRow("Diagnostics", "Device checks and the glass benchmark", icon = icons.Info, flightKey = "tool:diagnostics") { open(Screen.Diagnostics) }
                NavRow("Calibrate recents detection", "Only needed if recents is not recognised", icon = icons.Tune, flightKey = "tool:calibration") { open(Screen.Calibration) }
                NavRow("Debug log", "What the redirect saw and decided, frame times, how long wallpaper changes took", icon = icons.Code, flightKey = "tool:log") { open(Screen.Log) }
                NavRow("Updates", "Check GitHub for a new build and install it over this one", icon = icons.ArrowDown, flightKey = "tool:updates") { open(Screen.Updates) }
                NavRow("All changes", "What changed in each version", icon = icons.Pages, flightKey = "tool:changelog") { open(Screen.Changelog) }
                NavRow("About", "Version and credits", icon = icons.Info, flightKey = "tool:about") { open(Screen.About) }
            }
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
    "wallpaperFolders" to setOf(SettingsTab.MORE),
    "calendar" to setOf(SettingsTab.MORE),
    "calendarDays" to setOf(SettingsTab.MORE),
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
        "wallpaperFolders" to SettingsTab.MORE -> IslandEditor()
        // 0.16.0 (D209–D211): the calendar's lists.
        "calendar" to SettingsTab.MORE -> CalendarsEditor(store)
        "calendarDays" to SettingsTab.MORE -> {
            SchoolYearEditor(store)
            HolidayChangesEditor(store)
        }
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
    val preview = SettingsPreviews.provider?.let { p -> previewSection(surface.id, tab)?.let { id -> p to id } }
    val scroll = rememberScrollState()

    // 0.15.0 (D191): the heading folds away as the options scroll (the preview stays).
    // The live preview (a whole home screen) is composed once the page has slid in, then fades in: composing it on the
    // push's first frame made the slide stutter.
    val nav = LocalNavScope.current
    val settled = nav == null || (!nav.transition.isRunning && nav.transition.currentState == androidx.compose.animation.EnterExitState.Visible)
    var previewOn by remember { mutableStateOf(settled) }
    LaunchedEffect(settled) { if (settled) previewOn = true }
    val previewIn by androidx.compose.animation.core.animateFloatAsState(if (previewOn) 1f else 0f, androidx.compose.animation.core.tween(220), label = "previewIn")
    ScreenScaffold(title = surface.title, onBack = onBack, scroll = false, headerScroll = scroll) {
        if (preview != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).graphicsLayer { alpha = previewIn }) {
                    if (previewOn) preview.first(preview.second)
                }
            }
        }
        if (tabs.size > 1) {
            PageTabs(tabs, tab, counts = tabs.map { t -> SettingsMap.settingsOf(surface, t).count { store.isChanged(it, values) } }) { tab = it }
        }
        // A tab's options swap in place: the old ones leave a little ahead of the new ones arriving (WM Keyboard's swap).
        androidx.compose.animation.AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180, delayMillis = 40)) +
                        androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(220, delayMillis = 40)) { it / 40 }
                    ) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140))
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = "pageTab",
        ) { shownTab ->
            TabBody(store, surface, shownTab, tabs, highlight, open, if (shownTab == tab) scroll else rememberScrollState())
        }
    }
}

/** The options of one tab of a page, under their editors, in groups of cards; the fine tuning folded under "Advanced". */
@Composable
private fun TabBody(
    store: SettingsStore,
    surface: dev.liquid.launcher.core.settings.SettingsSurface,
    tab: SettingsTab,
    tabs: List<SettingsTab>,
    highlight: String?,
    open: (Screen) -> Unit,
    scroll: androidx.compose.foundation.ScrollState,
) {
    val values by store.values.collectAsStateCompat()
    val settings = remember(surface.id, tab) { SettingsMap.settingsOf(surface, tab) }
    var target by remember(surface.id, tab, highlight) { mutableFloatStateOf(-1f) }
    // Where the list's window starts on screen: a row lit by search says where it is on screen (it sits in a group now).
    val viewTop = remember { floatArrayOf(0f) }
    LaunchedEffect(target) {
        if (target >= 0f) scroll.animateScrollTo((target - viewTop[0] + scroll.value - 120f).toInt().coerceAtLeast(0))
    }
    Column(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { viewTop[0] = it.positionInRoot().y }
            .verticalScroll(scroll)
            .padding(bottom = 24.dp),
    ) {
        if (tab == tabs.first()) {
            BodyText(
                surface.summary + ". Hold an option to reset it, explain it or copy its id; a dot marks what you changed.",
                Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
        }
        PageEditors(surface.id, tab, store, open)
        // What most changes are about first; the fine tuning waits under "Advanced", open when it holds the
        // option search led to or something changed.
        val (advanced, basic) = settings.partition { LauncherSettings.isAdvanced(it) }
        if (basic.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            GroupCards {
                basic.forEachIndexed { i, it -> Box(Modifier.staggerIn(i)) { PageRow(it, store, highlight) { y -> target = y } } }
            }
        }
        if (advanced.isNotEmpty()) {
            var expanded by remember(surface.id, tab) {
                mutableStateOf(basic.isEmpty() || advanced.any { store.isChanged(it, values) || it.key == highlight })
            }
            SectionHeader("Advanced")
            GroupCards {
                FoldRow(
                    if (expanded) "Fine tuning" else "${advanced.size} more",
                    if (expanded) "The defaults are chosen to work" else advanced.take(3).joinToString(", ") { it.title } + if (advanced.size > 3) "…" else "",
                    expanded,
                ) { expanded = !expanded }
                if (expanded) advanced.forEachIndexed { i, it -> Box(Modifier.staggerIn(i)) { PageRow(it, store, highlight) { y -> target = y } } }
            }
        }
        if (settings.any { store.isChanged(it, values) }) {
            OutlinedButton(
                onClick = { settings.forEach { store.reset(it) } },
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            ) { Text("Reset ${tab.title}") }
        }
    }
}

@Composable
private fun PageTabs(tabs: List<SettingsTab>, selected: SettingsTab, counts: List<Int>, onSelect: (SettingsTab) -> Unit) =
    dev.liquid.launcher.core.designsystem.KitSegments(
        tabs.mapIndexed { i, t -> if (counts.getOrElse(i) { 0 } > 0) "${t.title} · ${counts[i]}" else t.title },
        tabs.indexOf(selected).coerceAtLeast(0),
    ) { onSelect(tabs[it]) }

@Composable
private fun FoldRow(title: String, summary: String, open: Boolean, onToggle: () -> Unit) =
    dev.liquid.launcher.core.designsystem.KitFoldRow(title, summary, open, onToggle)

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
            .then(if (lit) Modifier.onGloballyPositioned { onPlaced(it.positionInRoot().y) } else Modifier),
    ) {
        CompositionLocalProvider(LocalRowContext provides RowContext(why, lit)) { SettingRow(setting, store) }
    }
}

/** A search result: the option and where it lives; a tap opens its page on its tab. */
@Composable
internal fun SearchResultRow(setting: Setting<*>, open: (Screen) -> Unit, query: String = "") {
    val surface = SettingsMap.surfaceOf(setting)
    val tab = SettingsMap.tabOf(setting)
    // 0.15.0 (D191): WM Keyboard's search results: the words that matched in bold, the way to the option in its place's
    // hue under the summary, and the page's icon on a tile.
    val accent = entryAccent(surface.entry)
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .loneCard()
            .androidx_clickable { open(Screen.Page(surface.id, tab, setting.key)) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        IconTile(surfaceIcon(surface.id, surface.entry), accent)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                highlighted(setting.title, query),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
            )
            if (setting.summary.isNotEmpty()) {
                Text(
                    setting.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Text(
                SettingsMap.path(setting),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = if (darkSettings()) accent else androidx.compose.ui.graphics.lerp(accent, Color.Black, 0.28f),
            )
        }
    }
}

private fun Modifier.androidx_clickable(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))

/** [text] with every word of [query] found in it in bold (accents and case ignored). */
private fun highlighted(text: String, query: String): androidx.compose.ui.text.AnnotatedString {
    val words = query.trim().split(' ').filter { it.length >= 2 }
    if (words.isEmpty()) return androidx.compose.ui.text.AnnotatedString(text)
    fun fold(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()
    val plain = fold(text)
    val bold = BooleanArray(text.length)
    // Folding keeps one letter for one letter in the Latin text Settings uses, so positions line up.
    if (plain.length == text.length) {
        for (w in words.map(::fold)) {
            var at = plain.indexOf(w)
            while (at >= 0) {
                for (i in at until (at + w.length).coerceAtMost(text.length)) bold[i] = true
                at = plain.indexOf(w, at + w.length)
            }
        }
    }
    return androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val on = bold[i]
            var j = i
            while (j < text.length && bold[j] == on) j++
            if (on) {
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold))
                append(text.substring(i, j))
                pop()
            } else {
                append(text.substring(i, j))
            }
            i = j
        }
    }
}

/** The page and tab a 0.9 section link opens. */
internal fun pageForSection(section: String): Screen.Page? =
    SettingsMap.forSection(section)?.let { (surface, tab) -> Screen.Page(surface.id, tab) }
