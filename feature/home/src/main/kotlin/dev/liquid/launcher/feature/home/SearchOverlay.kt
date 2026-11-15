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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
}

/**
 * Swipe-down search: apps (typo-tolerant, ranked by use), calculator, bangs and the web.
 *
 * Since 0.7.0 (D99) it opens like the drawer: its progress is [HomeController.search], which a swipe
 * down drags under the finger. With "One space" (default) the home screen glides down and fades
 * while the field and results come down from above and the wallpaper drifts the same way, so
 * search is the space above the home screen as the drawer is the space below it. The field is
 * glass, so it moves by layout (not by layer) and keeps refracting what is really behind it.
 */
@Composable
fun SearchOverlay(
    controller: HomeController,
    config: SearchConfig,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    apps: List<AppEntry>,
    transition: String = "space",
    motion: DrawerMotion? = null,
    fieldInPill: Boolean = false,
    env: PageEnv? = null,
) {
    val usage by controller.data.usage.usage.collectAsState()
    val query = controller.searchQuery
    val focus = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val progress = controller.search
    val context = LocalContext.current
    // The keyboard comes once search is opening for good (released past the open point, or the pill tapped),
    // and goes as soon as it starts to close. With the field in the pill, the pill's field does this.
    if (!fieldInPill) {
        LaunchedEffect(controller.searchOpen) {
            if (controller.searchOpen) {
                if (config.keyboard) runCatching { focus.requestFocus() }
            } else {
                focusManager.clearFocus(force = true)
            }
        }
    }
    val rise = (motion?.rise ?: 0.12f).coerceIn(0.04f, 0.4f)

    val visible = remember(apps) { apps.filter { !it.hidden } }
    val suggestions = remember(visible, usage) {
        visible.filter { (usage[it.key]?.count ?: 0) > 0 }
            .sortedByDescending { controller.data.usage.score(it.key) }
            .take(5)
    }
    val results = remember(query, visible, usage, config.engine) { buildResults(query, visible, usage, config.engine) }

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
        }
        controller.closeSearch()
    }
    androidx.compose.runtime.SideEffect { controller.searchSubmit = { results.firstOrNull()?.let { open(it) } } }

    if (fieldInPill) {
        BottomSearch(controller, config, glass, transition, rise, env, suggestions, results, query) { open(it) }
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
                        "depth" -> (1f - p) * -40.dp.toPx()
                        // From above, as far as the drawer's apps come from below.
                        else -> (1f - p) * -rise * controller.screenHeight
                    }
                    IntOffset(0, dy.roundToInt())
                }
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    alpha = if (transition == "space") ((p - 0.04f) / 0.72f).coerceIn(0f, 1f) else p
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
                LauncherIcon(LauncherIcons.Search, contentDescription = null, tint = fieldColor, size = 22.dp)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("Apps, calculations, web, !yt …", style = TextStyle(fontFamily = HomeTheme.font, color = fieldColor.copy(alpha = 0.6f), fontSize = 16.sp))
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
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(50)).clickable { controller.searchQuery = "" }, contentAlignment = Alignment.Center) {
                        LauncherIcon(LauncherIcons.Close, contentDescription = "Clear", tint = fieldColor, size = 20.dp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (query.isBlank()) {
                if (suggestions.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (app in suggestions) {
                            Column(
                                Modifier.width(64.dp).clip(RoundedCornerShape(16.dp)).clickable { open(SearchResult.App(app)) }.padding(4.dp),
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
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(results, key = { resultKey(it) }) { result -> ResultRow(result) { open(result) } }
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
}

private fun buildResults(query: String, apps: List<AppEntry>, usage: Map<String, Usage>, engine: String): List<SearchResult> {
    val text = query.trim()
    if (text.isEmpty()) return emptyList()
    val out = ArrayList<SearchResult>()
    Bangs.parse(text)?.let { (bang, rest) ->
        if (rest.isNotEmpty()) out += SearchResult.Bang(bang.name, Bangs.url(bang.url, rest), rest)
    }
    Calculator.evaluate(text)?.let { out += SearchResult.Calc(text, it) }
    if (!text.startsWith("!")) {
        Fuzzy.rank(text, apps, { it.label }, { usageBoost(it, usage) }).take(8).forEach { out += SearchResult.App(it) }
        out += SearchResult.Web(text, WebSearch.url(engine, text))
    }
    return out
}

@Composable
private fun ResultRow(result: SearchResult, onClick: () -> Unit) {
    val style = TextStyle(fontFamily = HomeTheme.font, color = Color.White, fontSize = 16.sp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
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
        }
    }
}

@Composable
private fun IconBox(icon: Int) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        LauncherIcon(icon, contentDescription = null, tint = Color.White, size = 22.dp)
    }
}


// ---- search as part of the home screen (0.7.1, D104, D105) ----

private val PillFieldHeight = 54.dp
private val PillFieldBottom = 14.dp

/**
 * The search field inside the morphing pill: the pill itself becomes it (the same glass shape),
 * at the bottom of the screen, riding up with the keyboard. It edits [HomeController.searchQuery].
 */
@Composable
internal fun SearchPillField(controller: HomeController, color: Color, interactive: Boolean, modifier: Modifier) {
    val focus = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard by controller.data.settings.derive { get(dev.liquid.launcher.core.settings.LauncherSettings.SearchOpenKeyboard) }
    LaunchedEffect(controller.searchOpen) {
        if (controller.searchOpen) {
            if (keyboard) runCatching { focus.requestFocus() }
        } else {
            focusManager.clearFocus(force = true)
        }
    }
    Row(modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        LauncherIcon(LauncherIcons.Search, contentDescription = null, tint = color, size = 22.dp)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (controller.searchQuery.isEmpty()) {
                Text("Apps, calculations, web, !yt …", style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.6f), fontSize = 16.sp), maxLines = 1)
            }
            BasicTextField(
                value = controller.searchQuery,
                onValueChange = { controller.searchQuery = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 16.sp),
                cursorBrush = SolidColor(color),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { controller.searchSubmit?.invoke() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .semantics { contentDescription = "Search" },
            )
        }
        if (controller.searchQuery.isNotEmpty() && interactive) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(50)).clickable { controller.searchQuery = "" }, contentAlignment = Alignment.Center) {
                LauncherIcon(LauncherIcons.Close, contentDescription = "Clear", tint = color, size = 20.dp)
            }
        }
    }
}

/**
 * Search with its field in the pill at the bottom: results grow upwards from the field (the best
 * nearest the thumb), frequent apps sit just above it, and with nothing typed the space above holds
 * the search screen's own widgets. "One space" brings the content down from above while the home
 * screen glides down; "Zoom" brings it forward while the home screen falls back.
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
    open: (SearchResult) -> Unit,
) {
    val progress = controller.search
    val layout by controller.data.layout.layout.collectAsState()
    val widgets = layout.search
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { if (!controller.searchEditing) controller.closeSearch() } }
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }.background(Color.Black.copy(alpha = config.dim)))
        ProvideControls(controller, glass, null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    // Room for the field in the pill below.
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = PillFieldHeight + PillFieldBottom + 10.dp)
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
                        alpha = if (transition == "space" || transition == "zoom") ((p - 0.04f) / 0.72f).coerceIn(0f, 1f) else p
                        if (transition == "zoom") {
                            // Comes forward as the home screen falls back.
                            val scale = 1.08f - 0.08f * p
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                        }
                    },
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (query.isBlank()) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Bottom) {
                        SearchWidgets(controller, widgets, env)
                    }
                    if (suggestions.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            for (app in suggestions) {
                                Column(
                                    Modifier.width(64.dp).clip(RoundedCornerShape(16.dp)).clickable { open(SearchResult.App(app)) }.padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    AppIcon(app, false, Modifier.size(48.dp), app.label)
                                    Text(
                                        app.label,
                                        style = TextStyle(fontFamily = HomeTheme.font, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // The best result nearest the field and the thumb.
                    LazyColumn(Modifier.fillMaxWidth().weight(1f), reverseLayout = true) {
                        items(results, key = { resultKey(it) }) { result -> ResultRow(result) { open(result) } }
                    }
                }
            }
        }
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
