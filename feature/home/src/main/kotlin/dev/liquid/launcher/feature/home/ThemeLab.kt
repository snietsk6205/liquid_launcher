package dev.liquid.launcher.feature.home

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.common.WallpaperPalette
import dev.liquid.launcher.core.common.WallpaperTones
import dev.liquid.launcher.core.data.LiquidTheme
import dev.liquid.launcher.core.data.ThemeDoc
import dev.liquid.launcher.core.data.ThemeDocs
import dev.liquid.launcher.core.data.ThemeField
import dev.liquid.launcher.core.data.ThemeFieldKind
import dev.liquid.launcher.core.data.ThemeFields
import dev.liquid.launcher.core.data.ThemeGroup
import dev.liquid.launcher.core.data.ThemePill
import dev.liquid.launcher.core.data.ThemeResolve
import dev.liquid.launcher.core.data.ThemeStore
import dev.liquid.launcher.core.data.WallpaperRole
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.WallpaperStore
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.derive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * W3 (0.10.0): the Theme lab. Every option of a WM Keyboard theme the launcher draws (ThemeFields), each applied as it
 * changes, over a large live preview of every kind of control (or the real home, drawer and search). Presets, import
 * and export in WM Keyboard's own format, an export of your tuning, undo and redo, and a reset per group. The pills
 * share one look or have a full theme each. Settings shows this screen through SettingsPreviews (this module draws it).
 */

/**
 * A pill's own theme as it starts: the whole theme without pills of its own, with the pills' colours (and its follow
 * roles); the search bar with its tint as its fill (0.10.2).
 */
private fun pillSeed(d: ThemeDoc, which: String): ThemeDoc {
    val ext = ThemeDoc.EXT
    val follow = d.followed
    var p = ThemeDoc(JsonObject(d.json - ext + (ext to JsonObject(emptyMap()))))
    for ((field, role) in follow) if (!field.startsWith("$ext.")) p = p.withFollow(field, role)
    d.extElement("space")?.let { p = p.with("$ext.space", it) }
    for (k in listOf("background", "text", "pressed")) follow["$ext.space.$k"]?.let { p = p.withFollow("$ext.space.$k", it) }
    d.extElement("pillMaterial")?.let { p = p.with("$ext.pillMaterial", it) }
    if (which == ThemePill.SEARCH) {
        d.color("$ext.space.searchBackground")?.let { tint ->
            p = p.withColor("$ext.space.background", tint).withFollow("$ext.space.background", follow["$ext.space.searchBackground"])
        }
    }
    p = p.without("$ext.space.searchBackground")
    return p
}

/** What the lab is editing, its history, and its link to Settings. */
private class LabState(private val store: SettingsStore, initial: ThemeDoc, private val scope: kotlinx.coroutines.CoroutineScope) {
    var doc by mutableStateOf(initial)
    /** Null: the whole launcher; else the pill whose own theme is edited (individual pills). */
    var target by mutableStateOf<String?>(null)
    val undo = mutableStateListOf<ThemeDoc>()
    val redo = mutableStateListOf<ThemeDoc>()
    private var dragStart: ThemeDoc? = null
    private var saveJob: Job? = null

    /** The document the rows show: the whole theme, or the target pill's own. */
    val editing: ThemeDoc get() = target?.let { doc.pill(it) ?: pillSeed(doc, it) } ?: doc

    /** Applies [change] to what is edited; [continuous] while a slider moves (one undo step for the whole drag). */
    fun edit(continuous: Boolean = false, change: (ThemeDoc) -> ThemeDoc) {
        if (continuous) {
            if (dragStart == null) dragStart = doc
        } else {
            val before = dragStart ?: doc
            dragStart = null
            if (before != doc || !continuous) push(before)
        }
        val t = target
        doc = if (t == null) change(doc) else doc.withPill(t, change(doc.pill(t) ?: pillSeed(doc, t)))
        save(immediately = !continuous)
    }

    /** Ends a slider's drag: its value becomes one undo step. */
    fun endDrag() {
        dragStart?.let { push(it) }
        dragStart = null
        save(immediately = true)
    }

    private fun push(before: ThemeDoc) {
        if (undo.lastOrNull() == before) return
        undo.add(before)
        if (undo.size > 80) undo.removeAt(0)
        redo.clear()
    }

    fun replace(next: ThemeDoc) {
        push(doc)
        doc = next
        save(immediately = true)
    }

    fun undo() {
        val prev = undo.removeLastOrNull() ?: return
        redo.add(doc)
        doc = prev
        save(immediately = true)
    }

    fun redo() {
        val next = redo.removeLastOrNull() ?: return
        undo.add(doc)
        doc = next
        save(immediately = true)
    }

    /** Written to Settings, so the real home, drawer and search (and their previews) follow; a moving slider writes every 80 ms. */
    private fun save(immediately: Boolean) {
        saveJob?.cancel()
        val d = doc
        saveJob = scope.launch {
            if (!immediately) delay(80)
            ThemeStore.save(store, d)
        }
    }
}

/** The lab, as Settings → Look → Theme lab shows it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThemeLab(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val scope = rememberCoroutineScope()
    val state = remember { LabState(store, ThemeStore.doc(store), scope) }
    var mode by remember { mutableStateOf("sample") }
    var group by remember { mutableStateOf(ThemeGroup.COLOURS) }
    var pillsTab by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val tone by WallpaperTones.tone.collectAsState()
    val palette = remember(tone) {
        tone?.roles ?: runCatching { WallpaperPalette.fromSeed(dev.liquid.launcher.core.data.ThemedColors.seed(context)) }.getOrNull()
    }
    val resolved = remember(state.doc, palette) { ThemeResolve.resolve(state.doc, palette) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val doc = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
                    ?.let { ThemeDoc.parse(it) }?.let { ThemeStore.materialize(context, it) }
            }
            if (doc == null) {
                message = "That file is not a theme (a WM Keyboard theme or backup, or a launcher theme)."
            } else {
                state.replace(doc)
                message = "Imported \"${doc.name}\". Undo puts your theme back."
                DebugLog.event("Theme", "imported ${doc.name}")
            }
        }
    }
    var exportTuning by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val doc = state.doc
        val tuning = exportTuning
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val out = ThemeStore.embed(
                        if (tuning) doc.withString("${ThemeDoc.EXT}.source", "Your tuning, " + java.time.LocalDate.now()) else doc
                    )
                    context.contentResolver.openOutputStream(uri)?.use { it.write(out.encode().toByteArray()) }
                }.isSuccess
            }
            message = if (!ok) "Could not write the file." else if (tuning)
                "Saved. Send this file in a later session to make it the launcher's default look." else "Saved. WM Keyboard can import it as it is."
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            // Title bar: back, undo, redo, more.
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { LauncherIcon(LauncherIcons.ArrowBack, contentDescription = "Back") }
                Column(Modifier.weight(1f)) {
                    Text("Theme lab", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    Text(state.doc.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { state.undo() }, enabled = state.undo.isNotEmpty()) { LauncherIcon(LauncherIcons.Undo, contentDescription = "Undo") }
                IconButton(onClick = { state.redo() }, enabled = state.redo.isNotEmpty()) { LauncherIcon(LauncherIcons.Redo, contentDescription = "Redo") }
                Box {
                    IconButton(onClick = { menu = true }) { LauncherIcon(LauncherIcons.Tune, contentDescription = "Presets, import and export") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Preset: my backup") }, onClick = {
                            menu = false
                            scope.launch {
                                val backup = withContext(Dispatchers.IO) { ThemeStore.backup(context) }
                                if (backup != null) state.replace(backup) else message = "Your backup is not in this build."
                            }
                        })
                        DropdownMenuItem(text = { Text("Preset: before 0.10.2") }, onClick = {
                            menu = false
                            val before = ThemeStore.before0102(store)
                            if (before != null) state.replace(before) else message = "No earlier look: this phone started with 0.10.2's default."
                        })
                        DropdownMenuItem(text = { Text("Preset: before 0.10.1") }, onClick = {
                            menu = false
                            val previous = ThemeStore.previous(store)
                            if (previous != null) state.replace(previous) else message = "No earlier look: this is a new install."
                        })
                        DropdownMenuItem(text = { Text("Preset: the default (your keyboard theme, wallpaper colours)") }, onClick = { menu = false; state.replace(ThemeDocs.Default) })
                        DropdownMenuItem(text = { Text("Import a theme…") }, onClick = { menu = false; importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
                        DropdownMenuItem(text = { Text("Export (.wmtheme.json)…") }, onClick = {
                            menu = false
                            exportTuning = false
                            exporter.launch(state.doc.name.replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "theme" } + ".wmtheme.json")
                        })
                        DropdownMenuItem(text = { Text("Export my tuning…") }, onClick = {
                            menu = false
                            exportTuning = true
                            exporter.launch("liquid-launcher-tuning-${java.time.LocalDate.now()}.wmtheme.json")
                        })
                    }
                }
            }
            // The live preview, always in sight.
            LabPreview(mode, resolved, Modifier.fillMaxWidth().fillMaxHeight(0.4f).padding(horizontal = 12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((id, label) in listOf("sample" to "Every control", "home" to "Home", "drawer" to "Drawer", "search" to "Search")) {
                    FilterChip(selected = mode == id, onClick = { mode = id }, label = { Text(label) })
                }
            }
            // What is edited: the whole launcher, or one pill (individual pills).
            if (state.doc.pillLook == "individual") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = state.target == null, onClick = { state.target = null }, label = { Text("Whole launcher") })
                    for (p in ThemePill.all) FilterChip(selected = state.target == p, onClick = { state.target = p }, label = { Text(ThemePill.labels.getValue(p)) })
                }
            }
            // Groups.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (g in ThemeGroup.entries) {
                    FilterChip(selected = !pillsTab && group == g, onClick = { group = g; pillsTab = false }, label = { Text(g.title) })
                }
                FilterChip(selected = pillsTab, onClick = { pillsTab = true }, label = { Text("Pills") })
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                message?.let { m ->
                    Text(m, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).clickable { message = null })
                }
                if (pillsTab) {
                    PillsGroup(state)
                } else {
                    GroupRows(group, state, palette)
                }
            }
        }
    }
}

/** The preview: every kind of control in the theme over your wallpaper, or the real home, drawer or search. */
@Composable
private fun LabPreview(mode: String, theme: LiquidTheme, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(Color.Black)) {
        when (mode) {
            "home" -> HomeSectionPreview("home")
            "drawer" -> HomeSectionPreview("drawer")
            "search" -> HomeSectionPreview("search")
            else -> ThemeSample(theme)
        }
    }
}

/** Every kind of control the theme styles, pressable, over the wallpaper: a sheet with rows and buttons, a menu, a card, the three pills. */
@Composable
private fun ThemeSample(theme: LiquidTheme) {
    val context = LocalContext.current
    val wallpaper = remember { WallpaperStore.get(context) }
    val image by wallpaper.image.collectAsState()
    val settings = remember { SettingsStore.get(context) }
    val look by settings.derive { controlLook() }
    val glass by settings.derive { glassConfig() }
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            val picture = image
            if (picture != null) Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF6A7A8C), Color(0xFF2B2F3A), Color(0xFFB0C46A)))))
        }
        val env = remember(look, glass, backdrop, theme) { ControlEnv(look, glass, backdrop, null, theme) }
        val bursts = remember { ParticleBursts() }
        CompositionLocalProvider(LocalControls provides env, LocalParticles provides bursts) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // The home pill and the drawer pill.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SamplePill(ThemePill.HOME, "Home pill", Modifier.weight(1f), backdrop)
                    SamplePill(ThemePill.DRAWER, "Drawer pill", Modifier.weight(1f), backdrop)
                }
                // A sheet with rows, chips and buttons (the board).
                val (sheetShape, sheetOutline) = sheetOutline(RoundedCornerShape(28.dp))
                val ink = sheetContentColor(glass, false)
                Box(Modifier.fillMaxWidth().sheetSurface(backdrop, sheetShape, glass, false, sheetOutline).bloomLight().clip(sheetOutline)) {
                    BoardPicture(Modifier.matchParentSize())
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("A sheet", style = TextStyle(fontFamily = HomeTheme.font, color = ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                        Text("Its text, a detail line, and its controls", style = TextStyle(fontFamily = HomeTheme.font, color = theme.board?.secondaryText?.let { Color(it) } ?: ink.copy(alpha = 0.7f), fontSize = 13.sp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy((8f * theme.gapScale).dp)) {
                            ThemeChip("Recent", on = true, trailing = "12") { }
                            ThemeChip("Favourites", on = false) { }
                            ThemeChip("All", on = false) { }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy((8f * theme.gapScale).dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassButton("Explain", ink, icon = LauncherIcons.Sparkle) { }
                            GlassButton("Quiz me", ink) { }
                            GlassButton(null, ink, icon = LauncherIcons.Calendar, contentDescription = "Round button") { }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy((8f * theme.gapScale).dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassButton("Cancel", ink, emphasis = ControlEmphasis.Secondary) { }
                            Spacer(Modifier.weight(1f))
                            GlassButton("Apply", ink, emphasis = ControlEmphasis.Main, accent = Color(ThemeDocs.BLUE)) { }
                        }
                    }
                }
                // A menu and a card.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val menuShape = menuOutline(RoundedCornerShape(22.dp))
                    val menuInk = menuContentColor(glass, false)
                    Column(Modifier.weight(1f).menuSurface(backdrop, menuShape, glass, false).clip(menuShape).padding(vertical = 6.dp)) {
                        for ((i, label) in listOf("Open", "Tags", "Hide").withIndex()) {
                            val selected = i == 1
                            Text(
                                label,
                                modifier = Modifier.fillMaxWidth()
                                    .background(if (selected) theme.popup?.active?.let { Color(it) } ?: Color.White.copy(alpha = 0.12f) else Color.Transparent)
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                style = TextStyle(
                                    fontFamily = HomeTheme.font, fontSize = 14.sp,
                                    color = if (selected) theme.popup?.activeText?.let { Color(it) } ?: menuInk else menuInk,
                                ),
                            )
                        }
                    }
                    val card = themedCardSurface(backdrop, glass)
                    val cardShape = card?.second ?: RoundedCornerShape(22.dp)
                    Column(
                        Modifier.weight(1f).then(card?.first ?: Modifier.background(Color.White.copy(alpha = 0.09f), cardShape)).clip(cardShape).padding(12.dp),
                    ) {
                        val cardInk = cardContentColor(Color.White)
                        Text("25 days", style = TextStyle(fontFamily = HomeTheme.font, color = cardInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
                        Text("A card", style = TextStyle(fontFamily = HomeTheme.font, color = cardInk.copy(alpha = 0.75f), fontSize = 12.sp))
                    }
                }
                SamplePill(ThemePill.SEARCH, "Search bar", Modifier.fillMaxWidth(), backdrop)
            }
            ParticleLayer(bursts)
        }
    }
}

@Composable
private fun SamplePill(which: String, label: String, modifier: Modifier, backdrop: com.kyant.backdrop.Backdrop) {
    val shape = RoundedCornerShape(50)
    val env = LocalControls.current ?: return
    val ink = pillContentColor(which, Color.White)
    Row(
        modifier.height(46.dp).pillSurface(which, backdrop, shape, env.glass.style(Surfaces.Pill, false)).clip(shape).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LauncherIcon(LauncherIcons.Search, contentDescription = null, tint = ink, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, style = TextStyle(fontFamily = HomeTheme.font, color = ink, fontSize = 14.sp), maxLines = 1)
    }
}

// ---- rows ----

@Composable
private fun GroupRows(group: ThemeGroup, state: LabState, palette: WallpaperPalette?) {
    val doc = state.editing
    val fields = ThemeFields.of(group)
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(group.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        TextButton(onClick = {
            state.edit { d -> fields.fold(d) { acc, f -> resetField(acc, f) } }
        }) { Text("Reset group") }
    }
    var lastSection = ""
    for (field in fields) {
        if (field.section != lastSection) {
            lastSection = field.section
            Text(
                field.section,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
            )
        }
        FieldRow(field, doc, state, palette)
    }
    if (group == ThemeGroup.EXTRAS) ExtrasRows(state)
    if (group == ThemeGroup.FROST) FrostPresets(state)
}

private fun isDefault(doc: ThemeDoc, path: String): Boolean {
    fun norm(e: JsonElement?) = if (e is JsonNull) null else e
    return norm(doc.element(path)) == norm(ThemeDocs.Default.element(path)) && doc.follow(path) == ThemeDocs.Default.follow(path)
}

/** [f] back to the default theme's value, and to the wallpaper role it follows there (0.10.2: most colours do). */
private fun resetField(doc: ThemeDoc, f: ThemeField): ThemeDoc {
    val d = ThemeDocs.Default.element(f.path)
    return (if (d == null) doc.without(f.path) else doc.with(f.path, d)).withFollow(f.path, ThemeDocs.Default.follow(f.path))
}

/** One option: title, value on the right, a dot when it differs from the default; long press for Reset, Explain, Copy id. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FieldRow(field: ThemeField, doc: ThemeDoc, state: LabState, palette: WallpaperPalette?) {
    val applies = ThemeFields.applies(field, doc)
    var menu by remember { mutableStateOf(false) }
    var explain by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val alpha = if (applies) 1f else 0.45f
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = true,
                    onClick = { if (applies) picking = true },
                    onLongClick = { menu = true },
                )
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(field.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
                if (!isDefault(doc, field.path)) {
                    Box(Modifier.padding(end = 8.dp).size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
                ValueView(field, doc, state, applies)
            }
            if (!applies) {
                Text(field.needsReason ?: "Not used now", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (applies && (field.kind == ThemeFieldKind.FLOAT || field.kind == ThemeFieldKind.INT)) {
                val value = doc.float(field.path) ?: ThemeDocs.Default.float(field.path) ?: field.min
                Slider(
                    value = value.coerceIn(field.min, field.max),
                    onValueChange = { v ->
                        val stepped = if (field.step > 0f) (Math.round(v / field.step) * field.step) else v
                        state.edit(continuous = true) { d -> if (field.kind == ThemeFieldKind.INT) d.withInt(field.path, stepped.toInt()) else d.withFloat(field.path, stepped) }
                    },
                    onValueChangeFinished = { state.endDrag() },
                    valueRange = field.min..field.max,
                )
            }
            if (applies && field.kind == ThemeFieldKind.CHOICE && field.options.size <= 6) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val current = doc.string(field.path) ?: ""
                    for ((value, label) in field.options) {
                        FilterChip(selected = current == value, onClick = { state.edit { d -> setChoice(d, field, value) } }, label = { Text(label) })
                    }
                }
            }
            if (applies && field.kind == ThemeFieldKind.SURFACES) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val on = doc.strings(field.path).toSet()
                    for ((value, label) in field.options) {
                        FilterChip(selected = value in on, onClick = {
                            state.edit { d -> d.withStrings(field.path, field.options.map { it.first }.filter { (it in on) != (it == value) }) }
                        }, label = { Text(label) })
                    }
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Reset") }, onClick = { menu = false; state.edit { d -> resetField(d, field) } })
            DropdownMenuItem(text = { Text("Explain") }, onClick = { menu = false; explain = true })
            DropdownMenuItem(text = { Text("Copy id") }, onClick = { menu = false; clipboard.setText(AnnotatedString("theme." + field.path)) })
        }
    }
    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            title = { Text(field.title) },
            text = { Text(field.summary + (field.needsReason?.let { "\n\n$it." } ?: "") + "\n\nWM Keyboard: ${field.path}") },
            confirmButton = { TextButton(onClick = { explain = false }) { Text("Close") } },
        )
    }
    if (picking) {
        when (field.kind) {
            ThemeFieldKind.COLOR -> ColourDialog(field, doc, state, palette) { picking = false }
            ThemeFieldKind.CHOICE -> if (field.options.size > 6) ChoiceDialog(field, doc, state) { picking = false } else picking = false
            ThemeFieldKind.BOOL -> {
                state.edit { d -> d.withBool(field.path, !(d.bool(field.path) ?: false)) }
                picking = false
            }
            ThemeFieldKind.IMAGE, ThemeFieldKind.IMAGES -> PictureDialog(field, doc, state) { picking = false }
            ThemeFieldKind.GRADIENT -> GradientDialog(field, doc, state) { picking = false }
            ThemeFieldKind.COLORS -> ColoursDialog(field, doc, state) { picking = false }
            ThemeFieldKind.TEXT -> TextDialog(field, doc, state) { picking = false }
            else -> picking = false
        }
    }
}

/** A choice: an empty value removes the option (None), and a material, effect or bloom made empty removes its block. */
private fun setChoice(d: ThemeDoc, field: ThemeField, value: String): ThemeDoc = when {
    value.isNotEmpty() -> d.withString(field.path, value)
    field.path == "keyMaterial.kind" -> d.without("keyMaterial")
    field.path == "boardEffect.kind" -> d.without("boardEffect")
    field.path == "touchBloom.motion" -> d.withString(field.path, null)
    else -> d.withString(field.path, null)
}

@Composable
private fun ValueView(field: ThemeField, doc: ThemeDoc, state: LabState, applies: Boolean) {
    val style = MaterialTheme.typography.bodyMedium
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (field.kind) {
        ThemeFieldKind.BOOL -> Switch(checked = doc.bool(field.path) ?: false, enabled = applies, onCheckedChange = { v -> state.edit { d -> d.withBool(field.path, v) } })
        ThemeFieldKind.COLOR -> {
            val c = doc.color(field.path)
            val follow = doc.follow(field.path)
            if (follow != null) Text("Wallpaper: " + (WallpaperRole.labels[follow] ?: follow), style = style, color = muted, modifier = Modifier.padding(end = 8.dp))
            if (c == null && follow == null) Text("Auto", style = style, color = muted)
            else Box(Modifier.size(26.dp).clip(CircleShape).background(checker()).background(Color(c ?: 0xFF888888)).border(1.dp, muted.copy(alpha = 0.5f), CircleShape))
        }
        ThemeFieldKind.FLOAT, ThemeFieldKind.INT -> {
            val v = doc.float(field.path)
            Text(if (v == null) "Auto" else formatValue(field, v), style = style, color = muted)
        }
        ThemeFieldKind.CHOICE -> {
            val v = doc.string(field.path) ?: ""
            Text(field.options.firstOrNull { it.first == v }?.second ?: if (v.isEmpty()) "None" else v, style = style, color = muted, maxLines = 1)
        }
        ThemeFieldKind.IMAGE -> Text(if (doc.string(field.path).isNullOrBlank()) "None" else "Picture set", style = style, color = muted)
        ThemeFieldKind.IMAGES -> Text("${doc.strings(field.path).count { it.isNotBlank() }} of 6", style = style, color = muted)
        ThemeFieldKind.GRADIENT -> Text(if (doc.element(field.path) is JsonObject) "${(doc.element("${field.path}.colors") as? JsonArray)?.size ?: 0} colours" else "None", style = style, color = muted)
        ThemeFieldKind.COLORS -> Row { for (c in doc.colors(field.path)) Box(Modifier.padding(start = 3.dp).size(16.dp).clip(CircleShape).background(Color(c))) }
        ThemeFieldKind.SURFACES -> Text("${doc.strings(field.path).size} of ${field.options.size}", style = style, color = muted)
        ThemeFieldKind.TEXT -> Text(doc.string(field.path) ?: "", style = style, color = muted)
    }
}

private fun formatValue(field: ThemeField, v: Float): String {
    val text = if (field.kind == ThemeFieldKind.INT || field.step >= 1f) v.toInt().toString() else "%.2f".format(v)
    return if (field.unit.isEmpty()) text else if (field.unit == "×") "$text×" else "$text ${field.unit}"
}

private fun checker(): Brush = Brush.linearGradient(listOf(Color(0xFFBBBBBB), Color(0xFF777777)))

// ---- dialogs ----

/** A colour: fixed (picked with hue, saturation, value and opacity, or as hex) or following a role of the wallpaper. */
@Composable
private fun ColourDialog(field: ThemeField, doc: ThemeDoc, state: LabState, palette: WallpaperPalette?, onDone: () -> Unit) {
    val start = doc.color(field.path) ?: 0xFF888888
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(start.toInt(), it) } }
    var h by remember { mutableStateOf(hsv[0]) }
    var s by remember { mutableStateOf(hsv[1]) }
    var v by remember { mutableStateOf(hsv[2]) }
    var a by remember { mutableStateOf(((start ushr 24) and 0xFF) / 255f) }
    var follow by remember { mutableStateOf(doc.follow(field.path)) }
    var hex by remember { mutableStateOf(ThemeDoc.formatColor(start)) }
    fun current(): Long = (((a * 255f).toInt().coerceIn(0, 255).toLong() shl 24) or (android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)).toLong() and 0xFFFFFF))
    fun push(continuous: Boolean) {
        val c = current()
        hex = ThemeDoc.formatColor(c)
        state.edit(continuous) { d -> d.withColor(field.path, c).withFollow(field.path, follow) }
    }
    AlertDialog(
        onDismissRequest = { state.endDrag(); onDone() },
        title = { Text(field.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(checker()).background(Color(current())))
                Text("Follow the wallpaper", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = follow == null, onClick = { follow = null; push(false) }, label = { Text("Fixed") })
                    for (role in WallpaperRole.all) {
                        FilterChip(selected = follow == role, onClick = {
                            follow = role
                            // The colour now shows the role's; its opacity stays yours.
                            palette?.of(role)?.let { rgb ->
                                val c = FloatArray(3).also { android.graphics.Color.colorToHSV(rgb, it) }
                                h = c[0]; s = c[1]; v = c[2]
                            }
                            push(false)
                        }, label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                palette?.let { Box(Modifier.size(12.dp).clip(CircleShape).background(Color(it.of(role)))) }
                                Spacer(Modifier.width(4.dp))
                                Text(WallpaperRole.labels.getValue(role))
                            }
                        })
                    }
                }
                if (field.path == "accent") {
                    // 0.10.2: the accent follows the wallpaper by default; the fixed blue of 0.10.1 stays one tap away.
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = follow == null && current() == ThemeDocs.BLUE, onClick = {
                            follow = null
                            val c = FloatArray(3).also { android.graphics.Color.colorToHSV(ThemeDocs.BLUE.toInt(), it) }
                            h = c[0]; s = c[1]; v = c[2]; a = 1f
                            push(false)
                        }, label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(Color(ThemeDocs.BLUE)))
                                Spacer(Modifier.width(4.dp))
                                Text("Blueberry (fixed blue)")
                            }
                        })
                    }
                }
                if (follow == null) {
                    LabSlider("Hue", h, 0f, 360f) { h = it; push(true) }
                    LabSlider("Saturation", s, 0f, 1f) { s = it; push(true) }
                    LabSlider("Brightness", v, 0f, 1f) { v = it; push(true) }
                }
                LabSlider("Opacity", a, 0f, 1f) { a = it; push(true) }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { t ->
                        hex = t
                        ThemeDoc.parseColor(t)?.let { c ->
                            val x = FloatArray(3).also { android.graphics.Color.colorToHSV(c.toInt(), it) }
                            h = x[0]; s = x[1]; v = x[2]; a = ((c ushr 24) and 0xFF) / 255f
                            state.edit { d -> d.withColor(field.path, c).withFollow(field.path, follow) }
                        }
                    },
                    singleLine = true,
                    label = { Text("#AARRGGBB") },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { state.endDrag(); onDone() }) { Text("Done") } },
        dismissButton = if (field.auto) ({
            TextButton(onClick = { state.edit { d -> d.withColor(field.path, null).withFollow(field.path, null) }; onDone() }) { Text("Auto") }
        }) else null,
    )
}

@Composable
private fun LabSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Slider(value = value.coerceIn(min, max), onValueChange = onChange, valueRange = min..max)
    }
}

@Composable
private fun ChoiceDialog(field: ThemeField, doc: ThemeDoc, state: LabState, onDone: () -> Unit) {
    val current = doc.string(field.path) ?: ""
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(field.title) },
        text = {
            LazyColumn(Modifier.height(420.dp)) {
                items(field.options) { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { state.edit { d -> setChoice(d, field, value) } }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = current == value, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
    )
}

/** Pictures: chosen from the gallery, kept in the launcher's own storage. */
@Composable
private fun PictureDialog(field: ThemeField, doc: ThemeDoc, state: LabState, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val many = field.kind == ThemeFieldKind.IMAGES
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) { ThemeStore.keepPicture(context, uri) } ?: return@launch
            state.edit { d ->
                if (many) d.withStrings(field.path, (d.strings(field.path).filter { it.isNotBlank() } + path).take(6)) else d.withString(field.path, path)
            }
        }
    }
    val current = if (many) doc.strings(field.path).filter { it.isNotBlank() } else listOfNotNull(doc.string(field.path)?.takeIf { it.isNotBlank() })
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(field.title) },
        text = {
            Column {
                Text(field.summary, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (path in current) {
                        val image = rememberThemeImage(path, 256)
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Color.Gray)) {
                            if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text(if (many) "Add a picture" else if (current.isEmpty()) "Choose a picture" else "Replace")
            }
        },
        dismissButton = {
            Row {
                if (current.isNotEmpty()) TextButton(onClick = { state.edit { d -> if (many) d.withStrings(field.path, emptyList()) else d.withString(field.path, null) }; onDone() }) { Text("Remove") }
                TextButton(onClick = onDone) { Text("Done") }
            }
        },
    )
}

/** A gradient: two to four colours, its kind and its angle. */
@Composable
private fun GradientDialog(field: ThemeField, doc: ThemeDoc, state: LabState, onDone: () -> Unit) {
    val g = doc.element(field.path) as? JsonObject
    var colors by remember { mutableStateOf(doc.colors("${field.path}.colors").ifEmpty { listOf(0x33FFFFFFL, 0x00FFFFFFL) }) }
    var type by remember { mutableStateOf((g?.get("type") as? JsonPrimitive)?.content ?: "LINEAR") }
    var angle by remember { mutableStateOf(doc.float("${field.path}.angleDeg") ?: 90f) }
    var editing by remember { mutableStateOf<Int?>(null) }
    fun push(continuous: Boolean = false) = state.edit(continuous) { d ->
        d.with(field.path, JsonObject(mapOf("colors" to JsonArray(colors.map { JsonPrimitive(it) }), "type" to JsonPrimitive(type), "angleDeg" to JsonPrimitive(angle))))
    }
    AlertDialog(
        onDismissRequest = { state.endDrag(); onDone() },
        title = { Text(field.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(checker()).background(Brush.horizontalGradient(colors.map { Color(it) })))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    colors.forEachIndexed { i, c ->
                        Box(Modifier.size(34.dp).clip(CircleShape).background(checker()).background(Color(c)).border(2.dp, if (editing == i) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape).clickable { editing = i })
                    }
                    if (colors.size < 4) TextButton(onClick = { colors = colors + colors.last(); push() }) { Text("Add") }
                    if (colors.size > 2) TextButton(onClick = { colors = colors.dropLast(1); editing = null; push() }) { Text("Remove") }
                }
                editing?.let { i ->
                    val c = colors[i]
                    var hexText by remember(i) { mutableStateOf(ThemeDoc.formatColor(c)) }
                    OutlinedTextField(value = hexText, onValueChange = { t ->
                        hexText = t
                        ThemeDoc.parseColor(t)?.let { v -> colors = colors.toMutableList().also { it[i] = v }; push() }
                    }, singleLine = true, label = { Text("Colour ${i + 1}, #AARRGGBB") })
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (t in listOf("LINEAR" to "Linear", "RADIAL" to "Radial", "SWEEP" to "Sweep")) {
                        FilterChip(selected = type == t.first, onClick = { type = t.first; push() }, label = { Text(t.second) })
                    }
                }
                if (type == "LINEAR") LabSlider("Angle: ${angle.toInt()}°", angle, 0f, 360f) { angle = it; push(true) }
            }
        },
        confirmButton = { TextButton(onClick = { state.endDrag(); onDone() }) { Text("Done") } },
        dismissButton = { TextButton(onClick = { state.edit { d -> d.withString(field.path, null) }; onDone() }) { Text("None") } },
    )
}

/** Up to four colours (a moving background's). */
@Composable
private fun ColoursDialog(field: ThemeField, doc: ThemeDoc, state: LabState, onDone: () -> Unit) {
    var colors by remember { mutableStateOf(doc.colors(field.path)) }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(field.title) },
        text = {
            Column {
                Text(field.summary, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEachIndexed { i, c ->
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(c)).clickable {
                            colors = colors.filterIndexed { j, _ -> j != i }
                            state.edit { d -> d.withColors(field.path, colors) }
                        })
                    }
                }
                if (colors.size < 4) {
                    OutlinedTextField(value = text, onValueChange = { t ->
                        text = t
                        ThemeDoc.parseColor(t)?.let { c ->
                            colors = colors + c
                            text = ""
                            state.edit { d -> d.withColors(field.path, colors) }
                        }
                    }, singleLine = true, label = { Text("Add #RRGGBB (tap a colour to remove it)") }, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
    )
}

@Composable
private fun TextDialog(field: ThemeField, doc: ThemeDoc, state: LabState, onDone: () -> Unit) {
    var text by remember { mutableStateOf(doc.string(field.path) ?: "") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(field.title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { state.edit { d -> d.withString(field.path, text.ifBlank { null }) }; onDone() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

/** WM Keyboard's frost presets: the frost's numbers and the controls' see-through fills together. */
@Composable
private fun FrostPresets(state: LabState) {
    Text("Presets", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // (name, blur, saturation, brightness, rim, grain, style, refraction, dispersion, key alpha, popup alpha)
        val presets = listOf(
            listOf("Frosted", 14f, 1.25f, 0f, 0.5f, 0.06f, "FROSTED", 0.6f, 0.25f, 0x40, 0xB3),
            listOf("Clear", 6f, 1.1f, 0.05f, 0.85f, 0.02f, "FROSTED", 0.6f, 0.25f, 0x26, 0x99),
            listOf("Mica", 25f, 0.8f, -0.05f, 0.25f, 0.1f, "FROSTED", 0.6f, 0.25f, 0x66, 0xCC),
            listOf("Smoked", 18f, 1f, -0.2f, 0.3f, 0.08f, "FROSTED", 0.6f, 0.25f, 0x59, 0xCC),
            listOf("Liquid", 5f, 1.15f, 0.03f, 0.9f, 0f, "LIQUID", 0.7f, 0.3f, 0x2E, 0x99),
        )
        for (p in presets) {
            FilterChip(selected = false, onClick = {
                state.edit { d ->
                    fun alpha(path: String, a: Int, x: ThemeDoc): ThemeDoc = x.color(path)?.let { c -> x.withColor(path, ((a.toLong() and 0xFF) shl 24) or (c and 0xFFFFFF)) } ?: x
                    var x = d.withBool("frost.enabled", true)
                        .withFloat("frost.backdropBlur", p[1] as Float).withFloat("frost.saturation", p[2] as Float).withFloat("frost.brightness", p[3] as Float)
                        .withFloat("frost.rim", p[4] as Float).withFloat("frost.grain", p[5] as Float).withString("frost.style", p[6] as String)
                        .withFloat("frost.refraction", p[7] as Float).withFloat("frost.dispersion", p[8] as Float)
                    for (path in listOf("keyBackground", "modifierKeyBackground", "chipBackground", "toolCircleBackground")) x = alpha(path, p[9] as Int, x)
                    alpha("popupBackground", p[10] as Int, x)
                }
            }, label = { Text(p[0] as String) })
        }
    }
}

/** Stickers on sheets (WM Keyboard's decals) and other looks of this theme (its variants). */
@Composable
private fun ExtrasRows(state: LabState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val doc = state.editing
    val decals = (doc.element("decals") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) { ThemeStore.keepPicture(context, uri) } ?: return@launch
            state.edit { d ->
                val list = (d.element("decals") as? JsonArray).orEmpty()
                val id = "d" + System.currentTimeMillis().toString(36)
                d.with("decals", JsonArray((list + JsonObject(mapOf(
                    "id" to JsonPrimitive(id), "image" to JsonPrimitive(path), "x" to JsonPrimitive(0.8), "y" to JsonPrimitive(0.2),
                    "scale" to JsonPrimitive(0.25), "rotationDeg" to JsonPrimitive(0), "opacity" to JsonPrimitive(1),
                ))).take(6)))
            }
        }
    }
    Text("Stickers on sheets", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
    decals.forEachIndexed { i, d ->
        fun f(k: String, def: Float) = (d[k] as? JsonPrimitive)?.content?.toFloatOrNull() ?: def
        Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sticker ${i + 1}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { state.edit { x -> x.with("decals", JsonArray(decals.filterIndexed { j, _ -> j != i })) } }) { Text("Remove") }
            }
            for ((key, label, range) in listOf(Triple("x", "Across", -0.2f..1.2f), Triple("y", "Down", -0.2f..1.2f), Triple("scale", "Size", 0.05f..1f), Triple("rotationDeg", "Turn", -180f..180f), Triple("opacity", "Opacity", 0f..1f))) {
                LabSlider(label, f(key, if (key == "scale") 0.25f else if (key == "opacity") 1f else 0.5f), range.start, range.endInclusive) { v ->
                    state.edit(continuous = true) { x ->
                        val list = (x.element("decals") as? JsonArray)?.toMutableList() ?: return@edit x
                        val o = list.getOrNull(i) as? JsonObject ?: return@edit x
                        list[i] = JsonObject(o + (key to JsonPrimitive(v.toDouble())))
                        x.with("decals", JsonArray(list))
                    }
                }
            }
        }
    }
    if (decals.size < 6) {
        TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Add a sticker") }
    }
    // Variants: other looks kept inside the theme; switching swaps the current look with the chosen one.
    Text("Other looks of this theme", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
    val variants = (state.doc.element("variants") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    variants.forEachIndexed { i, v ->
        val name = (v["name"] as? JsonPrimitive)?.content ?: "Look ${i + 2}"
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val current = JsonObject(state.doc.json - "variants")
                val others = variants.filterIndexed { j, _ -> j != i } + current
                state.replace(ThemeDoc(JsonObject(v - "variants" + ("variants" to JsonArray(others)))))
            }) { Text("Use") }
            TextButton(onClick = { state.edit { d -> d.with("variants", JsonArray(variants.filterIndexed { j, _ -> j != i })) } }) { Text("Delete") }
        }
    }
    if (variants.size < 12) {
        TextButton(onClick = {
            state.edit { d ->
                val copy = JsonObject(d.json - "variants" + ("name" to JsonPrimitive(d.name + " ${variants.size + 2}")))
                d.with("variants", JsonArray(variants + copy))
            }
        }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Keep this look as another look") }
    }
}

/** The pills: one look for all three, or a full theme each; and copying one's look to another. */
@Composable
private fun PillsGroup(state: LabState) {
    val doc = state.doc
    Text(
        "The home pill, the drawer's pill and the search bar. Shared: they take the Pills colours of the theme. " +
            "Individual: each has every option of the lab; choose which one you edit at the top.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = doc.pillLook != "individual", onClick = { state.target = null; state.edit { it.withPillLook("shared") } }, label = { Text("Shared") })
        FilterChip(selected = doc.pillLook == "individual", onClick = { state.edit { it.withPillLook("individual") } }, label = { Text("Individual") })
    }
    if (doc.pillLook == "individual") {
        for (to in ThemePill.all) {
            Text("Copy to the ${ThemePill.labels.getValue(to).lowercase()} from…", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = false, onClick = {
                    state.replace(doc.withPill(to, pillSeed(doc, to)))
                }, label = { Text("Whole launcher") })
                for (from in ThemePill.all.filter { it != to }) {
                    FilterChip(selected = false, onClick = {
                        doc.pill(from)?.let { state.replace(doc.withPill(to, it)) }
                    }, label = { Text(ThemePill.labels.getValue(from)) })
                }
            }
        }
    }
}
