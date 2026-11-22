package dev.liquid.launcher.feature.home

import dev.liquid.launcher.core.settings.derive
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.layout
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.data.ClaudeQuery
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import kotlin.math.roundToInt
import android.content.Intent
import dev.liquid.launcher.core.data.AppEntry
import dev.liquid.launcher.core.data.LauncherAction
import dev.liquid.launcher.core.data.Bangs
import dev.liquid.launcher.core.data.Calculator
import dev.liquid.launcher.core.data.Fuzzy
import dev.liquid.launcher.core.data.Usage
import dev.liquid.launcher.core.data.WebSearch
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.liquidGlass

private sealed interface SearchResult {
    data class App(val app: AppEntry) : SearchResult
    data class Calc(val expression: String, val value: String) : SearchResult
    data class Bang(val name: String, val url: String, val query: String) : SearchResult
    data class Web(val query: String, val url: String) : SearchResult

    /** Ask Claude with [prompt] (0.7.2, D109). */
    data class Claude(val prompt: String) : SearchResult

    /** A row of a study command, "!next", "!exam", "!plan", "!abs" (0.9.3, D3). */
    data class Study(val row: StudyRow) : SearchResult

    /** 0.10.2: a study command offered while "!" is being typed; a tap puts it in the field. */
    data class Command(val command: String, val about: String) : SearchResult

    /** 0.10.2: one of your prompt templates, offered while "?" is typed alone; a tap asks Claude with it. */
    data class Prompt(val label: String, val prompt: String) : SearchResult

    /** 0.13.0 (D179): a person, an event, a note, a flashcard or an option of Settings. */
    data class More(val found: dev.liquid.launcher.core.data.Found) : SearchResult
}

/**
 * Swipe-down search: apps (typo-tolerant, ranked by use), calculator, bangs, the web and Claude.
 *
 * Since 0.7.0 (D99) it opens like the drawer: its progress is [HomeController.search], which a swipe
 * down drags under the finger. Since 0.7.2 (D107) the default, "Lift", lets the home screen fall
 * back into the blurred, slightly growing wallpaper while the pill flows straight to where the
 * keyboard will be (its height is remembered), and the results grow out of the field. The field
 * is glass, so it moves by layout (not by layer) and keeps refracting what is really behind it.
 */
@Composable
fun SearchOverlay(
    controller: HomeController,
    config: SearchConfig,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    apps: List<AppEntry>,
    transition: String = "lift",
    motion: DrawerMotion? = null,
    fieldInPill: Boolean = false,
    env: PageEnv? = null,
    pillOffsetDp: Int = 0,
    live: Boolean = true,
) {
    val usage by controller.data.usage.usage.collectAsState()
    val query = controller.searchQuery
    val focus = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val progress = controller.search
    val context = LocalContext.current
    // The keyboard comes once search is opening for good (released past the open point, or the pill tapped),
    // and goes as soon as it starts to close. With the field in the pill, the pill's field does this.
    if (!fieldInPill && live) SearchKeyboard(controller, focus, config.keyboard)
    if (live) KeyboardWatch(controller)
    val rise = (motion?.rise ?: 0.12f).coerceIn(0.04f, 0.4f)

    val visible = remember(apps) { apps.filter { !it.hidden } }
    val suggestions = remember(visible, usage) {
        visible.filter { (usage[it.key]?.count ?: 0) > 0 }
            .sortedByDescending { controller.data.usage.score(it.key) }
            .take(5)
    }
    val claudeApp = remember(visible) { visible.firstOrNull { it.component.packageName == controller.claudePackage && !it.isWorkProfile } }
    val askMode = controller.askClaudeMode && config.claude != "off"
    val studyDay by controller.data.study.day.collectAsState()
    val studyLoaded by controller.data.study.loaded.collectAsState()
    val minute = rememberMinuteTick()
    val templates by controller.data.settings.derive { get(dev.liquid.launcher.core.settings.LauncherSettings.StudyTemplates) }
    // 0.13.0 (D179): search everything. People, events, notes, cards and Settings are read off the main thread a moment
    // after the last letter; what was found for the letters before stays meanwhile if it still fits.
    val sources by controller.data.settings.derive {
        dev.liquid.launcher.core.data.Everything.Sources(
            settings = get(dev.liquid.launcher.core.settings.LauncherSettings.SearchFindSettings),
            contacts = get(dev.liquid.launcher.core.settings.LauncherSettings.SearchFindContacts),
            events = get(dev.liquid.launcher.core.settings.LauncherSettings.SearchFindEvents),
            notes = get(dev.liquid.launcher.core.settings.LauncherSettings.SearchFindNotes),
            cards = get(dev.liquid.launcher.core.settings.LauncherSettings.SearchFindCards),
        )
    }
    val contactsAsked by controller.data.settings.derive { get(dev.liquid.launcher.core.settings.LauncherSettings.SearchContactsAsked) }
    var found by remember { mutableStateOf<List<dev.liquid.launcher.core.data.Found>>(emptyList()) }
    var refind by remember { mutableStateOf(0) }
    LaunchedEffect(query, sources, askMode, contactsAsked, refind, live) {
        val text = query.trim()
        if (!live || askMode || text.length < 2 || text.startsWith("!") || text.startsWith("?")) {
            found = emptyList()
            return@LaunchedEffect
        }
        found = found.filter { dev.liquid.launcher.core.data.Everything.stillMatches(it, text) }
        kotlinx.coroutines.delay(110)
        val deck = controller.data.study.deck.value
        found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            dev.liquid.launcher.core.data.Everything.find(context.applicationContext, deck, text, sources, contactsAsked)
        }
    }
    val results = remember(query, visible, usage, config.engine, config.claude, askMode, studyDay, studyLoaded, minute, templates, found) {
        buildResults(query, visible, usage, config.engine, config.claude, askMode, studyDay, studyLoaded, templates, found)
    }

    fun open(result: SearchResult) {
        when (result) {
            is SearchResult.App -> controller.launch(result.app)
            is SearchResult.Calc -> {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Result", result.value))
                controller.toast("Copied ${result.value}")
                return
            }
            is SearchResult.Bang -> controller.openUrl(result.url)
            is SearchResult.Web -> controller.openUrl(result.url)
            // D1: sent with "[1A ESSAI · P2 · now: <block or free>]" in front while the study companion is on.
            is SearchResult.Claude -> controller.askClaude(controller.withStudyHeader(result.prompt))
            is SearchResult.Command -> {
                Haptics.tick(controller.view)
                controller.searchQuery = result.command
                return
            }
            is SearchResult.Prompt -> controller.askTemplate(result.prompt, heroBlock(studyDay, System.currentTimeMillis()))
            is SearchResult.More -> when (val f = result.found) {
                is dev.liquid.launcher.core.data.Found.Option -> controller.openSettingsAt(f.target)
                is dev.liquid.launcher.core.data.Found.Contact -> controller.start(contactIntent(f))
                is dev.liquid.launcher.core.data.Found.Event -> controller.start(eventIntent(f.id, f.begin, f.end))
                is dev.liquid.launcher.core.data.Found.Note -> controller.start(eventIntent(f.id, f.begin, f.begin))
                is dev.liquid.launcher.core.data.Found.Card -> controller.openReview(f.card.id)
                dev.liquid.launcher.core.data.Found.AllowContacts -> {
                    controller.requestContacts { granted ->
                        controller.data.settings.set(dev.liquid.launcher.core.settings.LauncherSettings.SearchContactsAsked, true)
                        if (granted) refind++
                    }
                    return
                }
            }
            is SearchResult.Study -> {
                val key = result.row.key
                when {
                    key.startsWith("block:") || key.startsWith("abs:") || key == "today" -> {
                        controller.openToday()
                        return
                    }
                    key.startsWith("exam:") -> {
                        // "exam:<event id>:<begin>"; an exam from launcher-data has no event and opens the calendar on its day.
                        val parts = key.removePrefix("exam:").split(':')
                        val id = parts.getOrNull(0)?.toLongOrNull()
                        val begin = parts.getOrNull(1)?.toLongOrNull()
                        if (id != null && begin != null) controller.start(examIntent(dev.liquid.launcher.core.data.StudyEvent(id, 0, begin, begin, true, "")))
                    }
                    key == "plan" -> controller.run(LauncherAction.StudyPlan)
                    key == "checkin" -> controller.eveningCheckin()
                    key == "off" -> controller.openSection("study")
                    // Nothing to open: the row only says what it found.
                    else -> return
                }
            }
        }
        controller.closeSearch()
    }
    // Enter opens the first app, calculation, Claude or the web as before 0.13.0: people, events, notes, cards and
    // Settings arrive a moment after the letters, and Enter should not depend on how quickly they came.
    androidx.compose.runtime.SideEffect { controller.searchSubmit = { results.firstOrNull { it !is SearchResult.More }?.let { open(it) } } }

    if (fieldInPill) {
        BottomSearch(controller, config, glass, transition, rise, env, suggestions, results, query, claudeApp, pillOffsetDp, live) { open(it) }
        return
    }

    val fieldShape = RoundedCornerShape(50)
    // W3: the search bar in the theme's pill look: dark by default (it read washed out over a bright page).
    val fieldColor = pillContentColor(dev.liquid.launcher.core.data.ThemePill.SEARCH, if (glass.lightGlass(Surfaces.Pill, lightBackground)) Color(0xFF16181C) else Color.White, lightBehind = lightBackground)

    Box(
        Modifier
            .fillMaxSize()
            // W4: a long press on the empty page offers "Customise search".
            .pointerInput(Unit) { detectTapGestures(onLongPress = { controller.offerCustomise("search", "search", it) }) { controller.closeSearch() } }
    ) {
        // The dim fades in place; the content moves.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }.background(Color.Black.copy(alpha = config.dim)))
        ProvideControls(controller, glass, null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .offset {
                        val p = progress.value
                        val dy = when (transition) {
                            "fade", "zoom" -> 0f
                            "depth", "lift" -> (1f - p) * -40.dp.toPx()
                            // From above, as far as the drawer's apps come from below.
                            else -> (1f - p) * -rise * controller.screenHeight
                        }
                        IntOffset(0, dy.roundToInt())
                    }
                    .graphicsLayer {
                        val p = progress.value.coerceIn(0f, 1f)
                        alpha = if (transition == "space" || transition == "lift") ((p - 0.04f) / 0.72f).coerceIn(0f, 1f) else p
                    },
            ) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .pillSurface(dev.liquid.launcher.core.data.ThemePill.SEARCH, backdrop, fieldShape, glass.style(Surfaces.Pill, lightBackground))
                        .clip(fieldShape)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FieldModeIcon(controller, fieldColor, config.claude != "off")
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(fieldHint(askMode), style = TextStyle(fontFamily = HomeTheme.font, color = fieldColor.copy(alpha = 0.6f), fontSize = 16.sp), maxLines = 1)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { controller.searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = HomeTheme.font, color = fieldColor, fontSize = 16.sp),
                            cursorBrush = SolidColor(fieldColor),
                            keyboardOptions = searchKeyboardOptions(ask = askMode),
                            keyboardActions = searchKeyboardActions { results.firstOrNull()?.let { open(it) } },
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Box(Modifier.size(36.dp).squishClickable { controller.searchQuery = "" }.clip(RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                            LauncherIcon(LauncherIcons.Close, contentDescription = "Clear", tint = fieldColor, size = 20.dp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (query.isBlank()) {
                    ClipboardChips(controller, config, live)
                    if (askMode) {
                        AskClaudeHint(controller, claudeApp)
                    } else if (suggestions.isNotEmpty()) {
                        SuggestionRow(controller, suggestions, transition) { open(SearchResult.App(it)) }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(results, key = { resultKey(it) }) { result ->
                            ResultRow(result, claudeApp, Modifier.animateItem()) { open(result) }
                        }
                    }
                }
            }
        }
    }
}

private fun resultKey(result: SearchResult): String = when (result) {
    is SearchResult.App -> "app:" + result.app.key
    is SearchResult.Calc -> "calc"
    is SearchResult.Bang -> "bang"
    is SearchResult.Web -> "web"
    is SearchResult.Claude -> "claude"
    is SearchResult.Study -> "study:" + result.row.key
    is SearchResult.Command -> "command:" + result.command
    is SearchResult.Prompt -> "prompt:" + result.label
    is SearchResult.More -> "more:" + result.found.key
}

private fun fieldHint(askMode: Boolean): String = if (askMode) "Ask Claude anything…" else "Apps, people, events, cards, settings…"

private fun buildResults(
    query: String,
    apps: List<AppEntry>,
    usage: Map<String, Usage>,
    engine: String,
    claude: String,
    askMode: Boolean,
    studyDay: dev.liquid.launcher.core.data.StudyDay = dev.liquid.launcher.core.data.StudyDay(),
    studyLoaded: Boolean = false,
    templates: List<String> = emptyList(),
    found: List<dev.liquid.launcher.core.data.Found> = emptyList(),
): List<SearchResult> {
    val text = query.trim()
    if (text.isEmpty()) return emptyList()
    // Switched to Claude (the field's icon or the "Ask Claude" action): everything typed goes there.
    if (askMode) return listOf(SearchResult.Claude(text))
    // "?text": straight to Claude (D3); "?" alone lists your prompts (0.10.2).
    if (claude != "off") {
        StudyCommands.question(text)?.let { question ->
            return if (question.isEmpty()) claudePrompts(templates).map { (label, prompt) -> SearchResult.Prompt(label, prompt) } else listOf(SearchResult.Claude(question))
        }
    }
    // "!next", "!exam", "!plan", "!abs" (D3).
    StudyCommands.parse(text)?.let { command ->
        return studyRows(command, studyDay, System.currentTimeMillis(), studyLoaded).map { SearchResult.Study(it) }
    }
    // 0.10.2: "!" and the start of a command ("!n") offer the study commands, one tap each.
    StudyCommands.started(text)?.let { commands ->
        return commands.map { (command, about) -> SearchResult.Command(command, about) }
    }
    if (claude != "off") {
        ClaudeQuery.bang(text)?.let { prompt ->
            return if (prompt.isEmpty()) emptyList() else listOf(SearchResult.Claude(prompt))
        }
    }
    val out = ArrayList<SearchResult>()
    Bangs.parse(text)?.let { (bang, rest) ->
        if (rest.isNotEmpty()) out += SearchResult.Bang(bang.name, Bangs.url(bang.url, rest), rest)
    }
    val calc = Calculator.evaluate(text)
    calc?.let { out += SearchResult.Calc(text, it) }
    if (!text.startsWith("!")) {
        val ask = SearchResult.Claude(text)
        // A question or a request puts Claude first (after a calculation, which answers at once).
        val claudeFirst = claude == "first" || (claude == "questions" && calc == null && ClaudeQuery.looksLikeQuestion(text))
        if (claudeFirst) out += ask
        Fuzzy.rank(text, apps, { it.label }, { usageBoost(it, usage) }).take(8).forEach { out += SearchResult.App(it) }
        // 0.13.0 (D179): people, events, notes, cards and Settings, after the apps.
        found.forEach { out += SearchResult.More(it) }
        if (!claudeFirst && claude != "off") out += ask
        out += SearchResult.Web(text, WebSearch.url(engine, text))
    }
    return out
}

@Composable
private fun ResultRow(result: SearchResult, claudeApp: AppEntry?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val style = TextStyle(fontFamily = HomeTheme.font, color = Color.White, fontSize = 16.sp)
    Row(
        modifier
            .fillMaxWidth()
            // Inside a scrolling list: a short wait before squashing, so a scroll never does (D108).
            .squishClickable(delayMs = 50L, onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (result) {
            is SearchResult.App -> {
                AppIcon(result.app, false, Modifier.size(40.dp), null)
                Spacer(Modifier.width(14.dp))
                Text(result.app.label, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (result.app.isWorkProfile) {
                    Spacer(Modifier.width(8.dp))
                    LauncherIcon(LauncherIcons.Work, contentDescription = "Work", tint = Color.White.copy(alpha = 0.6f), size = 16.dp)
                }
            }
            is SearchResult.Calc -> {
                IconBox(LauncherIcons.Calculate)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("= ${result.value}", style = style.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium))
                    Text("Tap to copy", style = style.copy(fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f)))
                }
            }
            is SearchResult.Bang -> {
                IconBox(LauncherIcons.Web)
                Spacer(Modifier.width(14.dp))
                Text("Search ${result.name} for “${result.query}”", style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            is SearchResult.Web -> {
                IconBox(LauncherIcons.Web)
                Spacer(Modifier.width(14.dp))
                Text("Search the web for “${result.query}”", style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            is SearchResult.Claude -> {
                if (claudeApp != null) AppIcon(claudeApp, false, Modifier.size(40.dp), null) else IconBox(LauncherIcons.Sparkle, HomeTheme.accent.copy(alpha = 0.35f))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Ask Claude", style = style.copy(fontWeight = FontWeight.Medium))
                    Text(
                        "“${result.prompt}”",
                        style = style.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f)),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                LauncherIcon(LauncherIcons.Sparkle, contentDescription = null, tint = Color.White.copy(alpha = 0.55f), size = 18.dp)
            }
            is SearchResult.Command -> {
                IconBox(StudyCommands.icon(result.command))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(result.command, style = style.copy(fontWeight = FontWeight.Medium))
                    Text(result.about, style = style.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f)), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            is SearchResult.Prompt -> {
                IconBox(LauncherIcons.Sparkle, HomeTheme.accent.copy(alpha = 0.35f))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(result.label, style = style.copy(fontWeight = FontWeight.Medium))
                    Text("“${result.prompt}”", style = style.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f)), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            is SearchResult.More -> FoundRow(result.found, style)
            is SearchResult.Study -> {
                IconBox(result.row.icon, (result.row.color ?: Color.White).copy(alpha = 0.28f))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(result.row.title, style = style.copy(fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(result.row.detail, style = style.copy(fontSize = 13.sp, color = (result.row.color ?: Color.White).copy(alpha = 0.8f)), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun IconBox(icon: Int, background: Color = Color.White.copy(alpha = 0.12f)) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        LauncherIcon(icon, contentDescription = null, tint = Color.White, size = 22.dp)
    }
}

/**
 * The field's leading icon: a magnifier, or a sparkle while search asks Claude. A tap switches
 * between them (when Ask Claude is on), with a little turn and pop.
 */
@Composable
private fun FieldModeIcon(controller: HomeController, color: Color, claudeOn: Boolean) {
    val ask = controller.askClaudeMode && claudeOn
    val turn by animateFloatAsState(if (ask) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = MotionScale.stiffness(420f)), label = "fieldMode")
    Box(
        Modifier
            .size(32.dp)
            .then(if (claudeOn) Modifier.squishClickable { controller.askClaudeMode = !controller.askClaudeMode } else Modifier)
            .semantics { contentDescription = if (ask) "Asking Claude: tap to search" else "Search: tap to ask Claude" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { alpha = (1f - turn).coerceIn(0f, 1f); rotationZ = -90f * turn; scaleX = 1f - 0.4f * turn; scaleY = 1f - 0.4f * turn }) {
            LauncherIcon(LauncherIcons.Search, contentDescription = null, tint = color, size = 22.dp)
        }
        Box(Modifier.graphicsLayer { alpha = turn.coerceIn(0f, 1f); rotationZ = 90f * (1f - turn); scaleX = 0.6f + 0.4f * turn; scaleY = 0.6f + 0.4f * turn }) {
            LauncherIcon(LauncherIcons.Sparkle, contentDescription = null, tint = color, size = 22.dp)
        }
    }
}

/**
 * Brings the keyboard up for search's field (0.8.0, D114), and takes focus away when search closes.
 *
 * 0.11.3 (Med chose "Keyboard slides with the pill", D169): with the field above the keyboard ([drives]), the launcher
 * asks for the keyboard on the first frame search starts opening (the swipe's start) and slides it in itself, in step
 * with search ([driveKeyboard]). When the keyboard or the system refuses, or is not ready in time, it is shown the
 * usual way ([askUsual]) and the field rides on it.
 *
 * The usual way: on the phone the keyboard sometimes never came ("no keyboard came" in the log, five times in a
 * morning): a focus request made while the window is still getting focus (just after a redirect brought the launcher
 * to the front) is dropped by the system, and a field that kept its focus asks for nothing. So: wait for the window's
 * focus, ask for focus and for the keyboard explicitly, and ask again while no keyboard is on its way.
 */
@Composable
private fun SearchKeyboard(controller: HomeController, focus: FocusRequester, wanted: Boolean, drives: Boolean = false) {
    val window = androidx.compose.ui.platform.LocalWindowInfo.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val view = androidx.compose.ui.platform.LocalView.current
    val insets = rememberFieldInsets()
    // 0.15.0 (D188): the opening's drive, and the drive taking the keyboard down with search; one at a time, so the
    // keyboard is never asked for twice (up to 0.14 a close while the opening's drive still held it asked again, the
    // system took the first back and the keyboard jumped up before going down).
    val driving = remember { mutableStateOf(false) }
    val following = remember { mutableStateOf(false) }
    val wasOpen = remember { mutableStateOf(false) }
    val followScope = androidx.compose.runtime.rememberCoroutineScope()
    fun follow() {
        if (!drives || driving.value || following.value) return
        following.value = true
        followScope.launch {
            try {
                runCatching { driveKeyboardDown(controller, view) }
            } finally {
                following.value = false
            }
        }
    }

    // 0.11.2: a keyboard already sliding in counts as coming (its animation has a target): asking again then could
    // restart it. On Med's phone about half the openings had no keyboard yet at 200 ms and were asked again.
    fun coming(): Boolean = view.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true ||
        insets.imeTarget() > insets.nav() + 1f || insets.ime() > insets.nav() + 1f

    suspend fun askUsual() {
        if (!window.isWindowFocused) {
            DebugLog.event("Home", "search: waiting for the window's focus before asking for the keyboard")
            kotlinx.coroutines.withTimeoutOrNull(1500) { snapshotFlow { window.isWindowFocused }.first { it } }
        }
        runCatching { focus.requestFocus() }
        keyboard?.show()
        for ((i, wait) in listOf(300L, 450L, 900L).withIndex()) {
            kotlinx.coroutines.delay(wait)
            if (!controller.searchOpen || coming()) return
            DebugLog.event("Home", "search: no keyboard yet, asking again (${i + 1}; window focused: ${if (window.isWindowFocused) "yes" else "no"})")
            // The first two only ask again, so a keyboard that is merely slow (the emulator's takes ~3 s) is not
            // interrupted; the last drops focus and takes it anew, for a field that kept focus and asks for nothing.
            if (i == 2) {
                focusManager.clearFocus(force = true)
                runCatching { focus.requestFocus() }
            }
            keyboard?.show()
            if (i > 0) view.windowInsetsController?.show(android.view.WindowInsets.Type.ime())
        }
    }

    if (drives) {
        // One drive per opening of search, from its first frame (the field is there from 2 % open).
        LaunchedEffect(controller.keyboardSession) {
            if (!wanted || !controller.expectKeyboard || controller.speedChecking || !controller.live) return@LaunchedEffect
            if (controller.keyboardPath != null) return@LaunchedEffect
            driving.value = true
            try {
                driveKeyboard(controller, view, focus, window) { askUsual() }
            } finally {
                driving.value = false
            }
        }
        // 0.15.0 (D188): a finger pushing open search away takes the keyboard with it from its first movement (up to 0.14
        // the keyboard stayed up until the finger let go, and the pill waited on it).
        LaunchedEffect(Unit) {
            snapshotFlow { controller.searchDragging && controller.searchOpen }.collect { pushed -> if (pushed) follow() }
        }
    }
    LaunchedEffect(controller.searchOpen) {
        if (!controller.searchOpen) {
            // Only when search closes: on the field's first frame (a swipe still moving) the drive has just focused it.
            // 0.13.0: the keyboard slides down with the pill first, then the field lets go of the focus. 0.15.0: by the
            // drive that already holds it, if one does; the focus goes once it is down (letting go of the focus first
            // hides the keyboard on the system's own curve and takes it from the drive).
            if (wasOpen.value) {
                if (drives) {
                    follow()
                    kotlinx.coroutines.withTimeoutOrNull(2_500) { snapshotFlow { driving.value || following.value }.first { !it } }
                }
                if (!controller.searchOpen) focusManager.clearFocus(force = true)
            }
            wasOpen.value = false
            return@LaunchedEffect
        }
        wasOpen.value = true
        if (!wanted || controller.speedChecking) return@LaunchedEffect
        if (drives && controller.expectKeyboard) {
            // A drive running asks (and falls back to the usual way itself); asking the usual way meanwhile would take
            // the keyboard from it. With none running (search opened again before it had closed), the usual way.
            kotlinx.coroutines.delay(32)
            if (driving.value) return@LaunchedEffect
            // 0.15.0: the keyboard already up (search sprang open again while it was following it down): nothing to ask.
            if (following.value || coming()) return@LaunchedEffect
        }
        askUsual()
    }
}

/**
 * 0.11.3 (D169), 0.13.0 (D175): asks for the keyboard as search starts opening and moves it in step with search's
 * progress, frame by frame ([KeyboardDriver]). Until it is ready, its picture ([KeyboardPicture], drawn by
 * [KeyboardStandIn]) rises in its place from the first frame; once ready, the keyboard is put exactly where the picture
 * is and the picture goes. Handed back fully up once search is open and still, or put away if search closes. Falls
 * back to [usual] when refused or not ready in time (the picture then stays up until the keyboard is). Logged.
 */
private suspend fun driveKeyboard(
    controller: HomeController,
    view: android.view.View,
    focus: FocusRequester,
    window: androidx.compose.ui.platform.WindowInfo,
    usual: suspend () -> Unit,
) {
    val drive = dev.liquid.launcher.core.common.KeyboardDrive
    controller.keyboardPath = "asking"
    if (!window.isWindowFocused) {
        kotlinx.coroutines.withTimeoutOrNull(1500) { snapshotFlow { window.isWindowFocused }.first { it } }
    }
    // Focus without showing the keyboard (the field does not show it on focus): the keyboard binds to the field. Its
    // input session starts on the next frame, and asking for control names the field that has it.
    runCatching { focus.requestFocus() }
    androidx.compose.runtime.withFrameNanos { }
    androidx.compose.runtime.withFrameNanos { }
    val driver = KeyboardDriver(view)
    fun at() = "search at " + "%.2f".format(controller.search.value) + ", the field at ${controller.fieldBottomPx.roundToInt()} px"
    suspend fun fallBack() {
        controller.keyboardPath = "usual"
        // 0.15.0: a finger pushing search away: wait to see where it lets go (asking now would bring the keyboard up
        // under a pill on its way down).
        if (controller.searchOpen && controller.searchDragging) snapshotFlow { controller.searchDragging }.first { !it }
        val wanted = controller.searchOpen || controller.searchDragging
        DebugLog.event(
            "Home",
            "search: the keyboard " + (if (wanted) "is shown the usual way" else "was let go as search closed") +
                " (${driver.cancelReason}, ${android.os.SystemClock.uptimeMillis() - driver.askedAt} ms after asking; ${at()})" +
                if (wanted) "; its picture stays until it is up" else "",
        )
        if (wanted) usual()
    }
    if (!driver.ask()) {
        fallBack()
        return
    }
    try {
        val answered = kotlinx.coroutines.withTimeoutOrNull(drive.ASK_LIMIT_MS) {
            snapshotFlow { driver.phase }.first { it != KeyboardDriver.Phase.Asking }
        }
        if (answered == null) driver.giveUp("not ready after ${drive.ASK_LIMIT_MS} ms")
        if (driver.phase != KeyboardDriver.Phase.Ready) {
            fallBack()
            return
        }
        controller.keyboardPath = "drive"
        controller.keyboardDriveShown = driver.shownPx.toFloat()
        controller.keyboardDriving = true
        val pictured = KeyboardPicture.image != null && controller.keyboardStandInOn
        // 0.13.1: a keyboard that has just rebuilt itself says it is taller than it will be (1188 px for 933 px on Med's
        // phone, the first opening after a wallpaper change): the pill keeps to the height it opens at.
        val expected = if (controller.keyboardHeightKnown) controller.keyboardRise else 0f
        val believed = drive.believes(driver.shownPx.toFloat(), expected, drive.TALLER_DP * view.resources.displayMetrics.density)
        DebugLog.event(
            "Home",
            "search: the keyboard rises with the pill: ready ${driver.readyAt - driver.askedAt} ms after asking at the swipe's start " +
                "(${at()}; ${if (pictured) "its picture rose meanwhile" else "no picture of it yet"}; it goes to ${driver.shownPx} px" +
                (if (believed) ")" else "; taller than the ${expected.roundToInt()} px it opens at, so the pill keeps to that until it settles)"),
        )
        // The keyboard takes the picture's place this frame, at its height; the rise then follows its real height.
        var frames = 0
        var topped = false
        var lastShare = -1f
        var stillFrames = 0
        while (driver.phase == KeyboardDriver.Phase.Ready) {
            val p = controller.search.value
            if (controller.searchOpen && p >= 0.998f) topped = true
            if (!controller.searchOpen) topped = false
            val rise = controller.keyboardRise.takeIf { it > 0f } ?: driver.shownPx.toFloat()
            // Where the picture stands now, as a share of the keyboard's own height; fully up once search is (unless its
            // height is doubted: then its top stays on the pill's line). 0.15.0: never past fully up while search bounces.
            val f = drive.share(rise, if (topped) 1f else drive.landed(p), driver.shownPx.toFloat())
            driver.move(f)
            if (frames == 2) {
                // Two frames in, the keyboard stands where its picture is: the picture goes, the rise glides to its height.
                controller.keyboardStandInOn = false
                if (driver.shownPx > 0 && believed) controller.glideKeyboardRise(driver.shownPx.toFloat())
            }
            frames++
            // Up and no longer moving (the rise's glide to the real height done): it can be handed over.
            stillFrames = if (topped && frames > 2 && kotlin.math.abs(f - lastShare) < 0.0005f) stillFrames + 1 else 0
            lastShare = f
            androidx.compose.runtime.withFrameNanos { }
            if (driver.phase != KeyboardDriver.Phase.Ready) break
            val moving = controller.search.isRunning || controller.searchDragging
            // 0.15.0 (D188): handed back as soon as it stands fully up (the pill's bounce is only a squash now), so a
            // swipe closing search soon after never meets a keyboard still held.
            if (drive.handOver(controller.searchOpen, controller.searchDragging, fullyUp = topped, frames = stillFrames) ||
                (controller.searchOpen && !moving && controller.search.value >= 0.999f && frames > 2)
            ) {
                driver.finish(true)
                controller.keyboardSteady = controller.searchOpen
                break
            }
            if (!controller.searchOpen && !moving && controller.search.value <= 0.001f) {
                driver.finish(false)
                break
            }
        }
        if (driver.phase == KeyboardDriver.Phase.Cancelled) fallBack()
    } finally {
        // Leaving (search closed, or the field left the screen): the keyboard is handed back where search is going.
        when (driver.phase) {
            KeyboardDriver.Phase.Ready -> driver.finish(controller.searchOpen)
            KeyboardDriver.Phase.Asking -> driver.giveUp("search closed before the keyboard was ready")
            else -> Unit
        }
        controller.keyboardDriving = false
        if (controller.keyboardPath == "drive") controller.keyboardStandInOn = false
    }
}

/**
 * 0.13.0 (D175): search closing with the keyboard up: the keyboard slides down with the pill, on search's spring, as
 * one piece (the system's own hide animation ran on a curve of its own). True when it was driven down.
 *
 * 0.15.0 (D188): also from the first movement of a finger pushing open search away, following the finger, and back up
 * if search springs open again; handed back once search is still, closed (put away) or open (fully up).
 */
private suspend fun driveKeyboardDown(controller: HomeController, view: android.view.View): Boolean {
    val drive = dev.liquid.launcher.core.common.KeyboardDrive
    val up = view.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true
    if (!up || controller.search.value <= 0.001f) return false
    val driver = KeyboardDriver(view)
    if (!driver.ask()) return false
    val ready = kotlinx.coroutines.withTimeoutOrNull(250) {
        snapshotFlow { driver.phase }.first { it != KeyboardDriver.Phase.Asking }
    }
    if (ready == null || driver.phase != KeyboardDriver.Phase.Ready) {
        driver.giveUp("not ready to close in time")
        return false
    }
    controller.keyboardSteady = false
    val start = drive.landed(controller.search.value).coerceAtLeast(0.001f)
    try {
        while (driver.phase == KeyboardDriver.Phase.Ready) {
            val p = drive.landed(controller.search.value)
            driver.move((p / start).coerceIn(0f, 1f))
            val moving = controller.search.isRunning || controller.searchDragging
            if (!moving && !controller.searchOpen && controller.search.value <= 0.001f) {
                driver.finish(false)
                break
            }
            if (!moving && controller.searchOpen && controller.search.value >= 0.998f) {
                driver.finish(true)
                controller.keyboardSteady = true
                break
            }
            androidx.compose.runtime.withFrameNanos { }
        }
    } finally {
        if (driver.phase == KeyboardDriver.Phase.Ready) driver.finish(controller.searchOpen)
    }
    return true
}

/**
 * 0.13.0 (D175): the keyboard's picture, rising in its place with the pill from the first frame of the swipe until the
 * keyboard itself is ready (then the keyboard covers it and it goes). Without a picture yet, a plain plate.
 */
@Composable
internal fun KeyboardStandIn(controller: HomeController, modifier: Modifier) {
    if (!controller.keyboardStandInOn || controller.keyboardRise <= 0f) return
    val image = KeyboardPicture.image
    Box(
        modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val h = controller.keyboardRise.roundToInt().coerceIn(1, constraints.maxHeight.coerceAtLeast(1))
                val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(constraints.maxWidth, h))
                layout(constraints.maxWidth, h) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                // In step with search, as the keyboard will be (read here: moving it redraws nothing else).
                translationY = size.height - keyboardTop(controller, controller.search.value)
            },
    ) {
        if (image != null) {
            androidx.compose.foundation.Image(
                image, contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(Color(0xFF1B1C20)))
        }
    }
}

/**
 * Watches the keyboard while search is on screen (D107): remembers its height once it is up, lets
 * the field follow it down when it is put away while search stays open, and gives up on it when it
 * never came.
 */
@Composable
private fun KeyboardWatch(controller: HomeController) {
    val insets = rememberFieldInsets()
    LaunchedEffect(insets) {
        var seenUp = false
        snapshotFlow { insets.imeTarget() > insets.nav() + 1f }.collect { up ->
            if (up) {
                seenUp = true
                // A keyboard that came late (after the wait below gave up) takes the field again.
                if (controller.searchOpen && controller.searchKeyboard && controller.searchField == "keyboard" && controller.searchInPill) {
                    controller.expectKeyboard = true
                }
            } else if (seenUp && controller.searchOpen) {
                // Put away (Back, or the keyboard's own button): the field glides down with it.
                controller.expectKeyboard = false
                controller.stopKeyboardRise()
            }
        }
    }
    // 0.13.0: the keyboard's picture kept from before a restart.
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(Unit) { KeyboardPicture.load(appContext) }
    // When search last opened, for the log's "started … ms after search opened".
    val openedAt = remember { longArrayOf(0L) }
    LaunchedEffect(controller.searchOpen) { if (controller.searchOpen) openedAt[0] = android.os.SystemClock.uptimeMillis() }
    LaunchedEffect(insets) {
        // 0.11.2: every opening is remembered with the last few (search lays its results out above the lowest of them,
        // KeyboardLine). A later settle while the keyboard stays open only lowers the newest height (a strip that went
        // away), never raises it (one that came while typing: 0.10.4 remembered 1041 px after typing, then 933 px).
        // 0.11.3: each opening says how the keyboard came (slid with the pill, or the usual way), where the field was
        // when it started and where both ended.
        var opening = false
        var startedAt = 0L
        var fieldAtStart = 0f
        var lastSettled = -1f
        val watchScope = this
        // 0.13.1: the picture is taken on the side, 180 ms after the keyboard stood still: waiting for it inside the
        // collection below held back the keyboard's next height (the pill stayed too high for those 180 ms).
        fun takePictureSoon(at: Float) {
            if (!controller.searchOpen) return
            val height = at.roundToInt()
            val screen = controller.screenHeight.roundToInt()
            watchScope.launch {
                kotlinx.coroutines.delay(180)
                if (controller.searchOpen && kotlin.math.abs(insets.ime() - at) < 1f) KeyboardPicture.take(appContext, height, screen)
            }
        }

        /**
         * 0.13.1 (Med: "when I change the wallpaper the pill goes higher than intended the first time, then the next time it
         * is normal"): the keyboard stood still at [at] while search is open. Up to 0.13.0 the pill kept the height the
         * keyboard first said it had (1188 px) after it came down to its real one (933 px); now the pill comes down onto it
         * (a glide) and is never left above a gap. Higher: the field already rides on it.
         */
        var growCheck: kotlinx.coroutines.Job? = null
        fun followSettled(at: Float) {
            if (!controller.searchOpen || controller.keyboardDriving) return
            val rise = controller.keyboardRise
            if (rise <= 0f) return
            if (at < rise - 1f) {
                growCheck?.cancel()
                DebugLog.event("Home", "search: the pill comes down onto the keyboard, from ${rise.roundToInt()} to ${at.roundToInt()} px")
                controller.glideKeyboardRise(at)
            } else if (at > rise + 1f) {
                if (!controller.keyboardSteady) {
                    // Still arriving (shown the usual way): the pill already rides on it; it stays there.
                    controller.settleKeyboardRise(at)
                } else {
                    // 0.15.0 (D188): taller while search is open: followed only if it stays taller. WM Keyboard says it is
                    // 85 dp taller for a moment as it rebuilds (after a wallpaper change, 25 ms on Med's phone); up to
                    // 0.14 the pill went up with it and came back down.
                    growCheck?.cancel()
                    growCheck = watchScope.launch {
                        kotlinx.coroutines.delay(dev.liquid.launcher.core.common.KeyboardDrive.GROW_SETTLE_MS)
                        val now = insets.ime()
                        if (controller.searchOpen && now > controller.keyboardRise + 1f) {
                            DebugLog.event("Home", "search: the keyboard stayed taller (${now.roundToInt()} px): the pill rises onto it")
                            controller.glideKeyboardRise(now)
                        } else {
                            DebugLog.event("Home", "search: the keyboard was taller for a moment (${at.roundToInt()} px): the pill stayed")
                        }
                    }
                }
            }
            if (controller.keyboardDriveShown > 0f) controller.keyboardDriveShown = at
        }
        snapshotFlow { Triple(insets.ime(), insets.imeTarget(), insets.nav()) }.collect { (now, target, nav) ->
            val up = now > nav + 1f
            if (!up && target <= nav + 1f) {
                // Away: the next time it comes up is an opening.
                opening = true
                startedAt = 0L
                lastSettled = -1f
                return@collect
            }
            if (opening && startedAt == 0L && up) {
                startedAt = android.os.SystemClock.uptimeMillis()
                fieldAtStart = controller.fieldBottomPx
            }
            // Where it is going: while the launcher drives it, the height the system gave for it fully up (its own
            // animation target may not be reported then); otherwise the target. On its way out it has not settled.
            // 0.13.1: only while the launcher still holds it; once handed back, the system's target is the truth (a
            // keyboard whose first height was doubted settles where it really is, never at that first height).
            val goal = when {
                opening && controller.keyboardPath == "drive" && controller.keyboardDriving && controller.keyboardDriveShown > nav + 1f -> controller.keyboardDriveShown
                target > nav + 1f -> target
                else -> return@collect
            }
            if (!up || kotlin.math.abs(now - goal) >= 1f || kotlin.math.abs(now - lastSettled) < 1f) return@collect
            if (opening) {
                val how = when (controller.keyboardPath) {
                    "drive" -> "slid in with the pill"
                    "usual" -> "shown the usual way, the field rode on it"
                    else -> "came by itself"
                }
                val after = if (openedAt[0] > 0L && startedAt >= openedAt[0]) "; it started ${startedAt - openedAt[0]} ms after search opened" else "; it started before search was open"
                DebugLog.event(
                    "Home",
                    "search: keyboard opened at ${now.roundToInt()} px ($how)$after, the field then at ${fieldAtStart.roundToInt()} px; " +
                        "the field ended at ${controller.fieldBottomPx.roundToInt()} px",
                )
                controller.rememberKeyboard(now, opening = true)
                opening = false
                // 0.13.0 (D175): up and still: its picture stands in for it at the next opening; a picture still up
                // (the keyboard came the usual way) goes now that the keyboard covers it.
                controller.keyboardStandInOn = false
                followSettled(now)
                // 0.15.0 (D188): from now on the open pill keeps to this line (moments taller are not followed).
                if (controller.searchOpen && controller.keyboardRise > 0f) controller.keyboardSteady = true
                takePictureSoon(now)
            } else if (controller.searchOpen) {
                if (now < lastSettled - 1f) {
                    DebugLog.event("Home", "search: keyboard went down to ${now.roundToInt()} px while open")
                    // 0.13.1: its new look and height, for the next opening's picture (a wallpaper change recolours it).
                    takePictureSoon(now)
                }
                controller.rememberKeyboard(now, opening = false)
                followSettled(now)
            }
            lastSettled = now
        }
    }
    LaunchedEffect(controller.searchOpen) {
        if (controller.searchOpen && controller.expectKeyboard) {
            // Generous: on a slow phone the keyboard can take two seconds or more (the emulator: 2.7 s); the drive
            // asks for 1.5 s and the usual way asks again until 1.7 s after that.
            kotlinx.coroutines.delay(4000)
            if (controller.searchOpen && controller.expectKeyboard && insets.imeTarget() <= insets.nav() + 1f && insets.ime() <= insets.nav() + 1f) {
                DebugLog.event("Home", "search: no keyboard came, the field comes down")
                controller.expectKeyboard = false
                controller.stopKeyboardRise()
            }
        }
    }
}

/**
 * Text copied in the last two minutes: Paste, and Ask Claude about it (D109). Only the clip's
 * description is checked while search opens; the text is read when a button is pressed.
 */
@Composable
private fun ClipboardChips(controller: HomeController, config: SearchConfig, live: Boolean) {
    if (!config.clipboard || !live) return
    var used by remember { mutableStateOf(false) }
    val offered = remember(controller.searchOpen) { controller.searchOpen && controller.recentClip() }
    if (!offered || used) return
    val color = Color.White
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassButton("Paste", color, icon = LauncherIcons.Paste) {
            controller.clipText()?.let { controller.searchQuery = it.take(600) }
            used = true
        }
        if (config.claude != "off") {
            GlassButton("Ask Claude about it", color, icon = LauncherIcons.Sparkle) {
                val text = controller.clipText()
                if (text != null) {
                    controller.askClaude(text)
                    controller.closeSearch()
                }
                used = true
            }
        }
    }
}

/** While search asks Claude with nothing typed: what Go will do. */
@Composable
private fun AskClaudeHint(controller: HomeController, claudeApp: AppEntry?) {
    val whereTo = if (claudeApp != null && controller.data.settings.get(dev.liquid.launcher.core.settings.LauncherSettings.SearchClaudeTarget) == "app") "the Claude app" else "claude.ai"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (claudeApp != null) AppIcon(claudeApp, false, Modifier.size(32.dp), null) else LauncherIcon(LauncherIcons.Sparkle, contentDescription = null, tint = Color.White, size = 24.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Ask Claude anything", style = TextStyle(fontFamily = HomeTheme.font, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium))
            Text(
                "Go sends it to $whereTo. Tap the sparkle to search again",
                style = TextStyle(fontFamily = HomeTheme.font, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp),
            )
        }
    }
}

/**
 * Frequent apps. Each pops in on its own as search opens, one after another from the left,
 * following the finger (their timing is a slice of search's progress).
 */
@Composable
internal fun SuggestionRow(controller: HomeController, suggestions: List<AppEntry>, transition: String, open: (AppEntry) -> Unit) {
    val progress = controller.search
    val staggered = transition != "fade" && transition != "depth"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        for ((i, app) in suggestions.withIndex()) {
            Column(
                Modifier
                    .width(64.dp)
                    .graphicsLayer {
                        if (!staggered) return@graphicsLayer
                        val t = ((progress.value - 0.3f - i * 0.07f) / 0.38f).coerceIn(0f, 1f)
                        val s = 0.55f + 0.45f * easeOutBack(t)
                        scaleX = s
                        scaleY = s
                        alpha = t
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
                    .squishClickable { open(app) }
                    .clip(RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(app, false, Modifier.size(48.dp), app.label)
                Text(
                    app.label,
                    style = TextStyle(fontFamily = HomeTheme.font, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 0..1 with a small overshoot near the end. */
private fun easeOutBack(t: Float): Float {
    val c1 = 1.4f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

// ---- search as part of the home screen (0.7.1, D104, D105; 0.7.2, D107) ----


/**
 * The search field inside the morphing pill: the pill itself becomes it (the same glass shape),
 * above the keyboard. It edits [HomeController.searchQuery]. [canFocus]: false in a settings
 * preview, which must never bring up a keyboard.
 */
@Composable
internal fun SearchPillField(controller: HomeController, color: Color, interactive: Boolean, modifier: Modifier, canFocus: Boolean = true) {
    val focus = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard by controller.data.settings.derive { get(dev.liquid.launcher.core.settings.LauncherSettings.SearchOpenKeyboard) }
    val claudeOn by controller.data.settings.derive { get(dev.liquid.launcher.core.settings.LauncherSettings.SearchClaude) != "off" }
    if (canFocus) SearchKeyboard(controller, focus, keyboard, drives = controller.searchField == "keyboard")
    Row(modifier.padding(start = 12.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        FieldModeIcon(controller, color, claudeOn && interactive)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (controller.searchQuery.isEmpty()) {
                Text(fieldHint(controller.askClaudeMode && claudeOn), style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.6f), fontSize = 16.sp), maxLines = 1)
            }
            val query = controller.searchQuery
            var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(query, androidx.compose.ui.text.TextRange(query.length))) }
            // Text put in from outside (a command chip, Clear) arrives with the cursor at its end.
            val shown = if (field.text == query) field else androidx.compose.ui.text.input.TextFieldValue(query, androidx.compose.ui.text.TextRange(query.length))
            BasicTextField(
                value = shown,
                onValueChange = {
                    field = it
                    controller.searchQuery = it.text
                },
                singleLine = true,
                textStyle = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 16.sp),
                cursorBrush = SolidColor(color),
                // 0.11.3: focus alone does not bring the keyboard: search asks for it itself, to slide it in with the pill.
                keyboardOptions = searchKeyboardOptions(ask = controller.askClaudeMode && claudeOn, showOnFocus = false),
                keyboardActions = searchKeyboardActions { controller.searchSubmit?.invoke() },
                enabled = canFocus,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .semantics { contentDescription = "Search" },
            )
        }
        if (controller.searchQuery.isNotEmpty() && interactive) {
            Box(Modifier.size(36.dp).squishClickable { controller.searchQuery = "" }.clip(RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                LauncherIcon(LauncherIcons.Close, contentDescription = "Clear", tint = color, size = 20.dp)
            }
        }
    }
}

/**
 * 0.12.0 (D172, Med's keyboard answer "search-friendly typing"): app names and calculations are not corrected (no
 * autocorrection, no automatic capital) and the keyboard's action key is Search; a question for Claude is ordinary text
 * again (corrected, with a capital) and the key is Send. The keyboard reads these from the field when it opens.
 */
internal fun searchKeyboardOptions(ask: Boolean, showOnFocus: Boolean = true) = KeyboardOptions(
    capitalization = if (ask) androidx.compose.ui.text.input.KeyboardCapitalization.Sentences else androidx.compose.ui.text.input.KeyboardCapitalization.None,
    autoCorrectEnabled = ask,
    keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    imeAction = if (ask) ImeAction.Send else ImeAction.Search,
    showKeyboardOnFocus = showOnFocus,
)

internal fun searchKeyboardActions(submit: () -> Unit) = KeyboardActions(onSearch = { submit() }, onSend = { submit() }, onGo = { submit() })

/**
 * Lays its content out above the open search field: [reserve] px from the bottom of the screen
 * are left free (read while laying out, so the keyboard's movement never recomposes it).
 */
private fun Modifier.reserveBottom(reserve: androidx.compose.ui.unit.Density.() -> Float): Modifier =
    layout { measurable, constraints ->
        val height = constraints.maxHeight
        val free = (height - reserve().roundToInt()).coerceIn(0, height)
        val placeable = measurable.measure(constraints.copy(minHeight = free, maxHeight = free))
        layout(constraints.maxWidth, height) { placeable.place(0, 0) }
    }

/**
 * Search with its field in the pill: results grow upwards from the field (the best nearest the
 * thumb), frequent apps sit just above it, and with nothing typed the space above holds the
 * search screen's own widgets. The content rides with the field as it flows up to the keyboard
 * and grows out of it ("Lift", "Zoom"); "One space" brings it down from above while the home
 * screen glides down.
 */
@Composable
private fun BottomSearch(
    controller: HomeController,
    config: SearchConfig,
    glass: GlassConfig,
    transition: String,
    rise: Float,
    env: PageEnv?,
    suggestions: List<AppEntry>,
    results: List<SearchResult>,
    query: String,
    claudeApp: AppEntry?,
    pillOffsetDp: Int,
    live: Boolean,
    open: (SearchResult) -> Unit,
) {
    val progress = controller.search
    val layout by controller.data.layout.layout.collectAsState()
    val widgets = layout.search
    val insets = rememberFieldInsets()
    val askMode = controller.askClaudeMode && config.claude != "off"
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { if (!controller.searchEditing) controller.closeSearch() } }
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }.background(Color.Black.copy(alpha = config.dim)))
        // A settings preview has no keyboard: a plate shows where it would be.
        if (!live) PreviewKeyboard(controller, insets, Modifier.align(Alignment.BottomCenter))
        // 0.13.0 (D175): the keyboard's picture rising in its place until the keyboard itself is ready.
        if (live) KeyboardStandIn(controller, Modifier.align(Alignment.BottomCenter))
        ProvideControls(controller, glass, null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    // Room for the field where it ends up: above the keyboard that is coming (0.11.3: planned from its
                    // height, so the results stay still while the keyboard slides in and only ride with the field).
                    .reserveBottom {
                        val size = controller.searchFieldSize
                        fieldBottomPlanned(controller, insets, size.gapDp.dp.toPx()) + size.heightDp.dp.toPx() + 10.dp.toPx()
                    }
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                    .offset {
                        val p = progress.value
                        val dy = when (transition) {
                            "space" -> (1f - p) * -rise * controller.screenHeight
                            "depth" -> (1f - p) * -40.dp.toPx()
                            else -> 0f
                        }
                        IntOffset(0, dy.roundToInt())
                    }
                    .graphicsLayer {
                        val p = progress.value.coerceIn(0f, 1f)
                        val grows = transition == "lift" || transition == "zoom"
                        if (grows) {
                            // Rides with the field on its way up, so the results grow out of it.
                            val gap = controller.searchFieldSize.gapDp.dp.toPx()
                            val now = fieldBottom(controller, insets, p, pillOffsetDp.dp.toPx(), gap, true)
                            val end = fieldBottomPlanned(controller, insets, gap)
                            translationY = (end - now).coerceAtLeast(0f)
                        }
                        alpha = if (transition == "space" || grows) ((p - 0.04f) / 0.72f).coerceIn(0f, 1f) else p
                        // 0.15.0 (D188): the fade is applied to each card as it draws, not through an offscreen copy of the
                        // whole page on every frame of the opening (the cards barely overlap, so it looks the same).
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                        val scale = when (transition) {
                            "lift" -> 0.92f + 0.08f * p
                            // Comes forward as the home screen falls back.
                            "zoom" -> 1.08f - 0.08f * p
                            else -> 1f
                        }
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    },
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (query.isBlank()) {
                    if (!askMode) {
                        // Study first (0.9.3): the cards chosen in Settings → Search, from the field upwards.
                        SearchPage(controller, config.cards, widgets, env, suggestions, transition, Modifier.weight(1f)) { open(SearchResult.App(it)) }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    ClipboardChips(controller, config, live)
                    if (askMode) AskClaudeHint(controller, claudeApp)
                } else {
                    // The best result nearest the field and the thumb; rows slide as the ranking changes.
                    LazyColumn(Modifier.fillMaxWidth().weight(1f), reverseLayout = true) {
                        items(results, key = { resultKey(it) }) { result ->
                            ResultRow(result, claudeApp, Modifier.animateItem()) { open(result) }
                        }
                    }
                }
            }
        }
    }
}

/** Where the keyboard would be, for the Search settings' preview: rises as search opens. */
@Composable
private fun PreviewKeyboard(controller: HomeController, insets: FieldInsets, modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val planned = if (controller.keyboardRise > 0f) controller.keyboardRise else controller.keyboardGuess
                val h = if (controller.expectKeyboard) planned.roundToInt().coerceIn(0, constraints.maxHeight) else 0
                val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(constraints.maxWidth, h))
                layout(constraints.maxWidth, h) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                // 0.11.3: it slides in with the pill, as the real keyboard does, and the field rides on it.
                val k = controller.search.value.coerceIn(0f, 1f)
                translationY = (1f - k) * size.height
            }
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(Color(0xFF202226).copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
    ) {
        Text("Keyboard", style = TextStyle(fontFamily = HomeTheme.font, color = Color.White.copy(alpha = 0.35f), fontSize = 14.sp))
    }
}

/**
 * The search screen's own widgets (D105), top to bottom, each across the width: built-in ones and
 * other apps'. "Add widget" opens the picker for this screen; "Edit" shows buttons to move,
 * resize and remove them.
 */
@Composable
internal fun SearchWidgets(
    controller: HomeController,
    widgets: List<dev.liquid.launcher.core.data.HomeItem>,
    env: PageEnv?,
    room: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Infinity,
) {
    val color = Color.White
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rows = env?.grid?.rows ?: 6
    val cell = with(density) { (controller.screenHeight * 0.8f / rows.coerceAtLeast(1)).toDp() }
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy((8 * themeGap).dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        if (widgets.isEmpty()) {
            Text(
                "Add widgets here: music, notes, weather, your study block…",
                style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.6f), fontSize = 12.sp),
                modifier = Modifier.weight(1f),
            )
        }
        if (widgets.isNotEmpty()) {
            GlassButton(if (controller.searchEditing) "Done" else "Edit", color, emphasis = ControlEmphasis.Secondary, icon = if (controller.searchEditing) LauncherIcons.Check else LauncherIcons.Edit) {
                controller.searchEditing = !controller.searchEditing
            }
        }
        GlassButton("Add widget", color, icon = LauncherIcons.Add) { controller.pickSearchWidget() }
    }
    if (env == null) return
    for ((i, item) in widgets.withIndex()) {
        Box(
            Modifier
                .fillMaxWidth()
                // Never taller than the space above the keyboard, so a widget is always seen whole (0.9.3):
                // the calendar adapts to its height (a month above 190 dp, today and what comes next below).
                .height(if (room == androidx.compose.ui.unit.Dp.Infinity) cell * item.h else minOf(cell * item.h, (room - 56.dp).coerceAtLeast(150.dp)))
                .padding(vertical = 3.dp),
        ) {
            WidgetContent(item, env, controller)
            if (controller.searchEditing) {
                // Covers the widget while editing, so the buttons are all that can be touched.
                Box(Modifier.matchParentSize().clip(RoundedCornerShape(22.dp)).background(Color.Black.copy(alpha = 0.35f)).pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy((6 * themeGap).dp)) {
                    if (i > 0) GlassButton(null, color, icon = LauncherIcons.ArrowUp, contentDescription = "Move up") { controller.moveSearchWidget(item.id, up = true) }
                    if (i < widgets.size - 1) GlassButton(null, color, icon = LauncherIcons.ArrowDown, contentDescription = "Move down") { controller.moveSearchWidget(item.id, up = false) }
                    GlassButton(null, color, icon = LauncherIcons.RemoveCircle, contentDescription = "Shorter") { controller.resizeSearchWidget(item.id, -1) }
                    GlassButton(null, color, icon = LauncherIcons.AddCircle, contentDescription = "Taller") { controller.resizeSearchWidget(item.id, +1) }
                    GlassButton("Remove", color, icon = LauncherIcons.Delete) { controller.removeSearchWidget(item.id) }
                }
            }
        }
    }
}
