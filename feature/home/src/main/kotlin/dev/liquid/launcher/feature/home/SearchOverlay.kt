package dev.liquid.launcher.feature.home

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
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

/** Swipe-down search: apps (typo-tolerant, ranked by use), calculator, bangs and the web. */
@Composable
fun SearchOverlay(
    controller: HomeController,
    config: SearchConfig,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    apps: List<AppEntry>,
) {
    val usage by controller.data.usage.usage.collectAsState()
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val appear = remember { Animatable(0f) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (config.keyboard) runCatching { focus.requestFocus() }
        appear.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 500f))
    }
    // The home screen behind blurs with this (Settings → Wallpaper → blur behind search).
    LaunchedEffect(Unit) { androidx.compose.runtime.snapshotFlow { appear.value }.collect { controller.searchAmount = it.coerceIn(0f, 1f) } }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { controller.searchAmount = 0f } }

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
        controller.searchOpen = false
    }

    val fieldShape = RoundedCornerShape(50)
    val fieldColor = if (glass.lightGlass(Surfaces.Pill, lightBackground)) Color(0xFF16181C) else Color.White

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = config.dim))
            .pointerInput(Unit) { detectTapGestures { controller.searchOpen = false } }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
                .graphicsLayer { translationY = (1f - appear.value) * -40.dp.toPx() },
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
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = HomeTheme.font, color = fieldColor, fontSize = 16.sp),
                        cursorBrush = SolidColor(fieldColor),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let { open(it) } }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(50)).clickable { query = "" }, contentAlignment = Alignment.Center) {
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
                                AppIconImage(app.icon, Modifier.size(48.dp), app.label)
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
                AppIconImage(result.app.icon, Modifier.size(40.dp), null)
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
