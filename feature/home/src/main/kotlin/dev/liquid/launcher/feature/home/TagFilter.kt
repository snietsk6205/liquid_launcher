package dev.liquid.launcher.feature.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.data.AppEntry
import dev.liquid.launcher.core.data.Organisation
import dev.liquid.launcher.core.data.Tag
import dev.liquid.launcher.core.data.Usage
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.liquidGlass
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import kotlin.math.abs
import kotlin.math.ln

/** How filtering the drawer by tag looks and behaves. */
data class TagFilterConfig(
    /** "above", "top" or "hidden". */
    val position: String,
    /** "used", "az" or "pinned". */
    val order: String,
    /** Chips shown; 0 = all. */
    val count: Int,
    val counts: Boolean,
    /** "single" or "multi". */
    val tap: String,
    /** "any" or "all". */
    val match: String,
    val swipe: Boolean,
    val keep: Boolean,
    val pinned: List<String>,
)

fun SettingsStore.tagFilterConfig() = TagFilterConfig(
    position = get(LauncherSettings.TagStripPosition),
    order = get(LauncherSettings.TagStripOrder),
    count = get(LauncherSettings.TagStripCount),
    counts = get(LauncherSettings.TagStripCounts),
    tap = get(LauncherSettings.TagTap),
    match = get(LauncherSettings.TagMatch),
    swipe = get(LauncherSettings.TagSwipe),
    keep = get(LauncherSettings.TagFilterKeep),
    pinned = get(LauncherSettings.TagPinned),
)

/** Height the chip row takes above the search field. */
internal val TagStripHeight = 46.dp

/** Pure rules of tag filtering, kept apart for clarity (and tests). */
object TagFilterOps {
    /** Apps that pass [filter]: any or all of its tags. An empty filter passes everything. */
    fun matches(appTags: List<String>, filter: Set<String>, match: String): Boolean = when {
        filter.isEmpty() -> true
        match == "all" -> filter.all { it in appTags }
        else -> appTags.any { it in filter }
    }

    /**
     * Tags in chip order: pinned ones in their order, then the others by use (with a gentle
     * pull towards recent use) or A to Z. Tags without apps go last.
     */
    fun ordered(tags: List<Tag>, pinned: List<String>, order: String, usage: Map<String, Usage>, counts: Map<String, Int>, now: Long): List<Tag> {
        val byId = tags.associateBy { it.id }
        val first = pinned.mapNotNull { byId[it] }
        if (order == "pinned") return first
        val rest = tags.filter { it.id !in pinned }
        val sorted = when (order) {
            "az" -> rest.sortedBy { it.name.lowercase() }
            else -> rest.sortedWith(
                compareByDescending<Tag> { score(usage["tag:${it.id}"], now) }.thenBy { it.name.lowercase() }
            )
        }
        return first + sorted.sortedBy { if ((counts[it.id] ?: 0) == 0) 1 else 0 }
    }

    private fun score(usage: Usage?, now: Long): Double {
        if (usage == null || usage.count == 0) return 0.0
        val days = (now - usage.last).coerceAtLeast(0) / 86_400_000.0
        return ln(1.0 + usage.count) + 1.5 / (1.0 + days)
    }

    /** The filter after swiping one step through [order] (+1 next, -1 back); past the ends: no filter. */
    fun step(order: List<String>, current: Set<String>, direction: Int): Set<String> {
        if (order.isEmpty()) return emptySet()
        val at = if (current.size == 1) order.indexOf(current.first()) else -1
        val next = when {
            at < 0 -> if (direction > 0) 0 else order.lastIndex
            else -> at + direction
        }
        return if (next in order.indices) setOf(order[next]) else emptySet()
    }

    /** A short name for the filter: "Anime", "Anime + 2". */
    fun label(filter: Set<String>, org: Organisation): String {
        val names = filter.mapNotNull { org.tag(it)?.name }
        return when {
            names.isEmpty() -> ""
            names.size == 1 -> names[0]
            else -> names[0] + " + " + (names.size - 1)
        }
    }
}

/**
 * The row of tag chips: "All apps", then the tags in chip order, then "All tags" for the full
 * list. A tap shows one tag (or toggles it, see settings), holding adds it to the selection.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TagStrip(
    tags: List<Tag>,
    counts: Map<String, Int>,
    config: TagFilterConfig,
    controller: HomeController,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    interactive: Boolean,
    modifier: Modifier,
) {
    val view = LocalView.current
    val color = if (glass.lightGlass(Surfaces.Drawer, lightBackground)) Color(0xFF16181C) else Color.White
    val filter = controller.tagFilter
    val shown = if (config.count > 0) tags.take(config.count) else tags
    LazyRow(
        modifier = modifier.fillMaxWidth().height(TagStripHeight),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "all") {
            StripChip("All apps", null, filter.isEmpty(), color, glass, lightBackground, backdrop, interactive, onTap = { controller.filterByTags(emptySet()) }, onHold = null)
        }
        items(shown, key = { it.id }) { tag ->
            val on = tag.id in filter
            StripChip(
                tag.name, counts[tag.id]?.takeIf { config.counts }, on, color, glass, lightBackground, backdrop, interactive,
                onTap = {
                    controller.filterByTags(
                        when {
                            config.tap == "multi" -> if (on) filter - tag.id else filter + tag.id
                            on && filter.size == 1 -> emptySet()
                            else -> setOf(tag.id)
                        }
                    )
                },
                onHold = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    controller.filterByTags(if (on) filter - tag.id else filter + tag.id)
                },
            )
        }
        item(key = "more") {
            StripChip("All tags", null, false, color, glass, lightBackground, backdrop, interactive, icon = LauncherIcons.Filter, onTap = { controller.tagSheet = true }, onHold = null)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StripChip(
    label: String,
    count: Int?,
    on: Boolean,
    color: Color,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    interactive: Boolean,
    icon: Int? = null,
    onTap: () -> Unit,
    onHold: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(50)
    val accent = HomeTheme.accent
    val textColor = if (on) readableOn(accent) else color
    Row(
        Modifier
            .height(36.dp)
            .liquidGlass(backdrop, shape, glass.style(Surfaces.Drawer, lightBackground))
            .clip(shape)
            .background(if (on) accent.copy(alpha = 0.9f) else Color.Transparent)
            .then(if (interactive) Modifier.combinedClickable(onClick = onTap, onLongClick = onHold) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            LauncherIcon(icon, contentDescription = null, tint = textColor, size = 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, maxLines = 1, style = TextStyle(fontFamily = HomeTheme.font, color = textColor, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium))
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text("$count", style = TextStyle(fontFamily = HomeTheme.font, color = textColor.copy(alpha = 0.7f), fontSize = 12.sp))
        }
    }
}

/** Dark or light text, whichever reads on [background]. */
internal fun readableOn(background: Color): Color =
    if (0.2126f * background.red + 0.7152f * background.green + 0.0722f * background.blue > 0.6f) Color(0xFF16181C) else Color.White

/**
 * The active filter at the start of the search field: the tag's name with a clear button.
 * Tapping the name opens the full tag list.
 */
@Composable
internal fun ActiveTagChip(controller: HomeController, label: String, interactive: Boolean) {
    val accent = HomeTheme.accent
    val text = readableOn(accent)
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.9f))
            .then(if (interactive) Modifier.clickable { controller.tagSheet = true } else Modifier)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp), style = TextStyle(fontFamily = HomeTheme.font, color = text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .then(if (interactive) Modifier.clickable { controller.filterByTags(emptySet()) } else Modifier),
            contentAlignment = Alignment.Center,
        ) { LauncherIcon(LauncherIcons.Close, contentDescription = "Show all apps", tint = text, size = 16.dp) }
    }
}

/**
 * Sideways swipes on the search field step through the tags (in chip order). Watches the
 * gesture before the text field does and only takes it once it is clearly sideways, so taps
 * and vertical drags (closing the drawer) work as before.
 */
internal fun Modifier.tagSwipe(enabled: () -> Boolean, onSwipe: (Int) -> Unit): Modifier = pointerInput(Unit) {
    val threshold = 44.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!enabled()) return@awaitEachGesture
        var dx = 0f
        var dy = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            dx += change.position.x - change.previousPosition.x
            dy += change.position.y - change.previousPosition.y
            // Clearly vertical (closing the drawer, scrolling): not a tag swipe.
            if (abs(dy) > viewConfiguration.touchSlop && abs(dy) > abs(dx)) break
            if (abs(dx) > threshold && abs(dx) > abs(dy) * 1.5f) {
                change.consume()
                onSwipe(if (dx < 0) 1 else -1)
                // Swallow the rest of this gesture.
                while (true) {
                    val rest = awaitPointerEvent(PointerEventPass.Initial)
                    val c = rest.changes.firstOrNull { it.id == down.id } ?: break
                    c.consume()
                    if (!c.pressed) break
                }
                break
            }
        }
    }
}

/**
 * The full tag list over the drawer: its own search, pinned tags, most used, then all A to Z,
 * each with how many apps it has. Tap a name to show only that tag; tap the circle to select
 * several; the pin keeps a tag at the front of the chips.
 */
@Composable
internal fun TagSheet(
    org: Organisation,
    counts: Map<String, Int>,
    usage: Map<String, Usage>,
    config: TagFilterConfig,
    controller: HomeController,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
) {
    val settings = controller.data.settings
    val color = if (glass.lightGlass(Surfaces.Menu, lightBackground)) Color(0xFF16181C) else Color.White
    var query by remember { mutableStateOf("") }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 520f)) }
    val pinned = config.pinned
    val filter = controller.tagFilter
    val now = remember { System.currentTimeMillis() }
    val sections = remember(org, query, pinned, usage, counts) {
        val all = org.tags.sortedBy { it.name.lowercase() }
        if (query.isNotBlank()) {
            listOf("Matching" to all.filter { it.name.contains(query.trim(), ignoreCase = true) })
        } else {
            val pinnedTags = pinned.mapNotNull { org.tag(it) }
            val used = TagFilterOps.ordered(org.tags.filter { it.id !in pinned }, emptyList(), "used", usage, counts, now)
                .filter { (usage["tag:${it.id}"]?.count ?: 0) > 0 }
                .take(5)
            listOfNotNull(
                ("Pinned" to pinnedTags).takeIf { pinnedTags.isNotEmpty() },
                ("Most used" to used).takeIf { used.isNotEmpty() },
                "All tags" to all,
            )
        }
    }
    val shape = RoundedCornerShape(30.dp)

    fun togglePin(tag: Tag) {
        val current = settings.get(LauncherSettings.TagPinned)
        settings.set(LauncherSettings.TagPinned, if (tag.id in current) current - tag.id else current + tag.id)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f * appear.value.coerceIn(0f, 1f)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controller.tagSheet = false },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(12.dp)
                .graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * 60.dp.toPx()
                }
                .liquidGlass(backdrop, shape, glass.style(Surfaces.Menu, lightBackground))
                .clip(shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(top = 14.dp, bottom = 10.dp),
        ) {
            // Search field of its own: with many tags, typing two letters is faster than scrolling.
            Row(
                Modifier
                    .padding(horizontal = 14.dp)
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LauncherIcon(LauncherIcons.Search, contentDescription = null, tint = color, size = 18.dp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Find a tag", style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.6f), fontSize = 15.sp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 15.sp),
                        cursorBrush = SolidColor(color),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp).padding(top = 6.dp)) {
                for ((title, list) in sections) {
                    item(key = "h:$title") {
                        Text(
                            title,
                            modifier = Modifier.padding(start = 22.dp, top = 10.dp, bottom = 4.dp),
                            style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.65f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        )
                    }
                    items(list, key = { "$title:${it.id}" }) { tag ->
                        TagRow(
                            tag = tag,
                            count = counts[tag.id] ?: 0,
                            selected = tag.id in filter,
                            pinned = tag.id in pinned,
                            color = color,
                            onOnly = {
                                controller.filterByTags(setOf(tag.id))
                                controller.tagSheet = false
                            },
                            onToggle = { controller.filterByTags(if (tag.id in filter) filter - tag.id else filter + tag.id) },
                            onPin = { togglePin(tag) },
                        )
                    }
                }
                if (org.tags.isEmpty()) {
                    item {
                        Text(
                            "No tags yet. Hold an app and choose Tags, or create one from the tabs pill.",
                            modifier = Modifier.padding(22.dp),
                            style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.8f), fontSize = 14.sp),
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // With several tags selected, show apps with any of them or only those with all.
                val all = config.match == "all"
                SheetPill(if (all) "Apps with all" else "Apps with any", color, Modifier.weight(1f)) {
                    settings.set(LauncherSettings.TagMatch, if (all) "any" else "all")
                }
                SheetPill("Clear", color, Modifier.weight(0.7f)) { controller.filterByTags(emptySet()) }
                SheetPill("Done", color, Modifier.weight(0.7f), filled = true) { controller.tagSheet = false }
            }
        }
    }
}

@Composable
private fun TagRow(
    tag: Tag,
    count: Int,
    selected: Boolean,
    pinned: Boolean,
    color: Color,
    onOnly: () -> Unit,
    onToggle: () -> Unit,
    onPin: () -> Unit,
) {
    val accent = HomeTheme.accent
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOnly).padding(start = 10.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (selected) accent else Color.Transparent)
                    .border(1.5.dp, if (selected) accent else color.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (selected) LauncherIcon(LauncherIcons.Check, contentDescription = null, tint = readableOn(accent), size = 14.dp) }
        }
        Text(tag.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 15.sp))
        Text("$count", modifier = Modifier.padding(horizontal = 8.dp), style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.6f), fontSize = 13.sp))
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onPin), contentAlignment = Alignment.Center) {
            LauncherIcon(LauncherIcons.Star, contentDescription = if (pinned) "Unpin" else "Pin", tint = if (pinned) accent else color.copy(alpha = 0.35f), size = 20.dp)
        }
    }
}

@Composable
private fun SheetPill(label: String, color: Color, modifier: Modifier, filled: Boolean = false, onClick: () -> Unit) {
    val inverse = if (color == Color.White) Color(0xFF16181C) else Color.White
    Box(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(50))
            .background(if (filled) color else color.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, maxLines = 1, style = TextStyle(fontFamily = HomeTheme.font, color = if (filled) inverse else color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)) }
}

/** How many visible apps carry each tag. */
internal fun tagCounts(apps: List<AppEntry>): Map<String, Int> {
    val map = HashMap<String, Int>()
    for (app in apps) for (t in app.tags) map[t] = (map[t] ?: 0) + 1
    return map
}
