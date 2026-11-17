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
import dev.liquid.launcher.core.data.AppEntry
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
    val results = remember(query, visible, usage, config.engine, config.claude, askMode) {
        buildResults(query, visible, usage, config.engine, config.claude, askMode)
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
        }
        controller.closeSearch()
    }
    androidx.compose.runtime.SideEffect { controller.searchSubmit = { results.firstOrNull()?.let { open(it) } } }

    if (fieldInPill) {
        BottomSearch(controller, config, glass, transition, rise, env, suggestions, results, query, claudeApp, pillOffsetDp, live) { open(it) }
        return
    }

    val fieldShape = RoundedCornerShape(50)
    val fieldColor = if (glass.lightGlass(Surfaces.Pill, lightBackground)) Color(0xFF16181C) else Color.White

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { controller.closeSearch() } }
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
                        .liquidGlass(backdrop, fieldShape, glass.style(Surfaces.Pill, lightBackground))
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
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let { open(it) } }),
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
}

private fun fieldHint(askMode: Boolean): String = if (askMode) "Ask Claude anything…" else "Apps, calculations, web, Claude…"

private fun buildResults(
    query: String,
    apps: List<AppEntry>,
    usage: Map<String, Usage>,
    engine: String,
    claude: String,
    askMode: Boolean,
): List<SearchResult> {
    val text = query.trim()
    if (text.isEmpty()) return emptyList()
    // Switched to Claude (the field's icon or the "Ask Claude" action): everything typed goes there.
    if (askMode) return listOf(SearchResult.Claude(text))
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
    val turn by animateFloatAsState(if (ask) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 420f), label = "fieldMode")
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
 * Brings the keyboard up for search's field (0.8.0, D114), and takes focus away when search
 * closes. On the phone the keyboard sometimes never came ("no keyboard came" in the log, five
 * times in a morning): a focus request made while the window is still getting focus (just after
 * a redirect brought the launcher to the front) is dropped by the system, and a field that kept
 * its focus asks for nothing. So: wait for the window's focus, ask for focus and for the keyboard
 * explicitly, and ask again (dropping and taking focus anew) while no keyboard is on its way.
 */
@Composable
private fun SearchKeyboard(controller: HomeController, focus: FocusRequester, wanted: Boolean) {
    val window = androidx.compose.ui.platform.LocalWindowInfo.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val view = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(controller.searchOpen) {
        if (!controller.searchOpen) {
            focusManager.clearFocus(force = true)
            return@LaunchedEffect
        }
        if (!wanted) return@LaunchedEffect
        if (!window.isWindowFocused) {
            DebugLog.event("Home", "search: waiting for the window's focus before asking for the keyboard")
            kotlinx.coroutines.withTimeoutOrNull(1500) { snapshotFlow { window.isWindowFocused }.first { it } }
        }
        runCatching { focus.requestFocus() }
        keyboard?.show()
        fun coming(): Boolean = view.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true
        for ((i, wait) in listOf(350L, 600L, 900L).withIndex()) {
            kotlinx.coroutines.delay(wait)
            if (!controller.searchOpen || coming()) return@LaunchedEffect
            DebugLog.event("Home", "search: no keyboard yet, asking again (${i + 1}; window focused: ${if (window.isWindowFocused) "yes" else "no"})")
            // The first two only ask again, so a keyboard that is merely slow (the emulator's takes ~3 s) is not
            // interrupted; the last drops focus and takes it anew, for a field that kept focus and asks for nothing.
            if (i == 2) {
                focusManager.clearFocus(force = true)
                runCatching { focus.requestFocus() }
            }
            keyboard?.show()
            view.windowInsetsController?.show(android.view.WindowInsets.Type.ime())
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
            }
        }
    }
    LaunchedEffect(insets) {
        // The final height, once an animation has ended (a keyboard can change height while up);
        // remembered only then, so the line never jumps at the start of an animation.
        snapshotFlow { insets.ime() }.collect { now ->
            if (now > insets.nav() + 1f && kotlin.math.abs(now - insets.imeTarget()) < 1f) controller.rememberKeyboard(now)
        }
    }
    LaunchedEffect(controller.searchOpen) {
        if (controller.searchOpen && controller.expectKeyboard) {
            // Generous: on a slow phone the keyboard can take two seconds or more (the emulator: 2.7 s),
            // and SearchKeyboard asks again until 1.7 s.
            kotlinx.coroutines.delay(3500)
            if (controller.searchOpen && insets.imeTarget() <= insets.nav() + 1f) {
                DebugLog.event("Home", "search: no keyboard came, the field goes down")
                controller.expectKeyboard = false
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
private fun SuggestionRow(controller: HomeController, suggestions: List<AppEntry>, transition: String, open: (AppEntry) -> Unit) {
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

private val PillFieldHeight = 54.dp
private val PillFieldBottom = 14.dp

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
    if (canFocus) SearchKeyboard(controller, focus, keyboard)
    Row(modifier.padding(start = 12.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        FieldModeIcon(controller, color, claudeOn && interactive)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (controller.searchQuery.isEmpty()) {
                Text(fieldHint(controller.askClaudeMode && claudeOn), style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.6f), fontSize = 16.sp), maxLines = 1)
            }
            BasicTextField(
                value = controller.searchQuery,
                onValueChange = { controller.searchQuery = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 16.sp),
                cursorBrush = SolidColor(color),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { controller.searchSubmit?.invoke() }),
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
        ProvideControls(controller, glass, null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    // Room for the field where it ends up: above the keyboard that is coming.
                    .reserveBottom {
                        fieldBottom(controller, insets, 1f, pillOffsetDp.dp.toPx(), PillFieldBottom.toPx(), true) +
                            PillFieldHeight.toPx() + 10.dp.toPx()
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
                            val now = fieldBottom(controller, insets, p, pillOffsetDp.dp.toPx(), PillFieldBottom.toPx(), true)
                            val end = fieldBottom(controller, insets, 1f, pillOffsetDp.dp.toPx(), PillFieldBottom.toPx(), true)
                            translationY = (end - now).coerceAtLeast(0f)
                        }
                        alpha = if (transition == "space" || grows) ((p - 0.04f) / 0.72f).coerceIn(0f, 1f) else p
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
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Bottom) {
                            SearchWidgets(controller, widgets, env)
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    ClipboardChips(controller, config, live)
                    if (askMode) {
                        AskClaudeHint(controller, claudeApp)
                    } else if (suggestions.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        SuggestionRow(controller, suggestions, transition) { open(SearchResult.App(it)) }
                    }
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
                val h = if (controller.expectKeyboard) controller.searchFieldLine(insets.nav(), 0f, 0f).roundToInt().coerceIn(0, constraints.maxHeight) else 0
                val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(constraints.maxWidth, h))
                layout(constraints.maxWidth, h) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                val k = ((controller.search.value - 0.45f) / 0.55f).coerceIn(0f, 1f)
                translationY = (1f - k) * (1f - k) * size.height
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
private fun SearchWidgets(controller: HomeController, widgets: List<dev.liquid.launcher.core.data.HomeItem>, env: PageEnv?) {
    val color = Color.White
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rows = env?.grid?.rows ?: 6
    val cell = with(density) { (controller.screenHeight * 0.8f / rows.coerceAtLeast(1)).toDp() }
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        if (widgets.isEmpty()) {
            Text(
                "Add widgets here: music, notes, weather, your study block…",
                style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.6f), fontSize = 12.sp),
                modifier = Modifier.weight(1f),
            )
        }
        if (widgets.isNotEmpty()) {
            GlassButton(if (controller.searchEditing) "Done" else "Edit", color, icon = if (controller.searchEditing) LauncherIcons.Check else LauncherIcons.Edit) {
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
                .height(cell * item.h)
                .padding(vertical = 3.dp),
        ) {
            WidgetContent(item, env, controller)
            if (controller.searchEditing) {
                // Covers the widget while editing, so the buttons are all that can be touched.
                Box(Modifier.matchParentSize().clip(RoundedCornerShape(22.dp)).background(Color.Black.copy(alpha = 0.35f)).pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
