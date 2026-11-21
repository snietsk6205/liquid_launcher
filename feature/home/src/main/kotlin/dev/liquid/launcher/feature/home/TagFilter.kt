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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import kotlin.math.ln

/** How filtering the drawer by tag looks and behaves. */
data class TagFilterConfig(
    /** "above", "top" or "hidden". */
    val position: String,
    /** Where the full tag list's button sits: "rowEnd", "rowStart", "fieldRight" or "fieldLeft". */
    val button: String,
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
    /** Size of the round button for the full tag list in the chip row (0.9.8, P5). */
    val buttonDp: Int = 50,
    val keep: Boolean,
    val pinned: List<String>,
)

fun SettingsStore.tagFilterConfig() = TagFilterConfig(
    position = get(LauncherSettings.TagStripPosition),
    button = get(LauncherSettings.TagButtonPosition).let { b ->
        // Without the chip row, the button moves into the search field so it is always there.
        if (get(LauncherSettings.TagStripPosition) == "hidden" && b.startsWith("row")) "fieldRight" else b
    },
    order = get(LauncherSettings.TagStripOrder),
    count = get(LauncherSettings.TagStripCount),
    counts = get(LauncherSettings.TagStripCounts),
    tap = get(LauncherSettings.TagTap),
    match = get(LauncherSettings.TagMatch),
    swipe = get(LauncherSettings.TagSwipe),
    keep = get(LauncherSettings.TagFilterKeep),
    pinned = get(LauncherSettings.TagPinned),
    buttonDp = get(LauncherSettings.TagButtonSize),
)

/** The chip row's height: room for the tag button (0.9.8). */
internal fun TagFilterConfig.stripHeight(): androidx.compose.ui.unit.Dp = maxOf(46, buttonDp + 6).dp

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
    // P5 (0.9.15): one selection that slides from chip to chip, a shared element instead of a jump. It follows the
    // chip it is going to as the row scrolls, and a new choice mid-way starts from where it is drawn.
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
    val single: Any? = when {
        filter.isEmpty() -> "all"
        filter.size == 1 -> filter.first()
        else -> null
    }
    val theme = LocalControls.current?.theme
    val selection = theme?.chip?.active?.let { Color(it) } ?: HomeTheme.accent.copy(alpha = 0.9f)
    val slide = remember { Animatable(1f) }
    val drawn = remember { SlideHolder() }
    fun targetOf(key: Any?): Pair<Float, Float>? {
        val info = rowState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return null
        val x = (item.offset - info.viewportStartOffset).toFloat()
        return x to x + item.size
    }
    LaunchedEffect(single) {
        val target = targetOf(single)
        val from = drawn.edges
        if (target != null && from != null) {
            drawn.deltaStart = from.first - target.first
            drawn.deltaEnd = from.second - target.second
            slide.snapTo(0f)
            slide.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = MotionScale.stiffness(520f)))
        }
    }
    Row(modifier.fillMaxWidth().height(config.stripHeight()), verticalAlignment = Alignment.CenterVertically) {
        // The list button is fixed at one end, so it never scrolls away with the chips.
        if (config.button == "rowStart") {
            Spacer(Modifier.width(14.dp))
            TagsButton(controller, color, Modifier.liquidGlass(backdrop, CircleShape, glass.style(Surfaces.Drawer, lightBackground)), interactive, size = config.buttonDp.dp)
        }
        // One glass surface for the whole row. Up to 0.5.0 every chip was its own glass (a
        // shader pass each, redrawn whenever the list under them scrolled), against D8.
        val rowShape = RoundedCornerShape(50)
        LazyRow(
            state = rowState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = if (config.button.startsWith("row")) 8.dp else 14.dp)
                .height(40.dp)
                .liquidGlass(backdrop, rowShape, glass.style(Surfaces.Drawer, lightBackground))
                .clip(rowShape)
                .drawBehind {
                    val target = if (single == null) null else targetOf(single)
                    if (target == null) {
                        drawn.edges = null
                        return@drawBehind
                    }
                    val k = 1f - slide.value
                    val start = target.first + drawn.deltaStart * k
                    val end = target.second + drawn.deltaEnd * k
                    drawn.edges = start to end
                    val h = 34.dp.toPx()
                    val chip = theme?.chip
                    val radius = when (chip?.shape) {
                        null, "PILL", "CIRCLE" -> h / 2f
                        "SHARP" -> 0f
                        else -> (chip?.cornerDp ?: 12).dp.toPx().coerceAtMost(h / 2f)
                    }
                    drawRoundRect(
                        selection,
                        topLeft = Offset(start, (size.height - h) / 2f),
                        size = androidx.compose.ui.geometry.Size((end - start).coerceAtLeast(1f), h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                    )
                },
            contentPadding = PaddingValues(horizontal = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item(key = "all") {
                StripChip("All apps", null, filter.isEmpty(), color, glass, lightBackground, backdrop, interactive, ownFill = single == null, onTap = { controller.filterByTags(emptySet()) }, onHold = null)
            }
            items(shown, key = { it.id }) { tag ->
                val on = tag.id in filter
                StripChip(
                    tag.name, counts[tag.id]?.takeIf { config.counts }, on, color, glass, lightBackground, backdrop, interactive,
                    ownFill = single == null,
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
                        Haptics.confirm(view)
                        controller.filterByTags(if (on) filter - tag.id else filter + tag.id)
                    },
                )
            }
        }
        if (config.button == "rowEnd") {
            TagsButton(controller, color, Modifier.liquidGlass(backdrop, CircleShape, glass.style(Surfaces.Drawer, lightBackground)), interactive, size = config.buttonDp.dp)
            Spacer(Modifier.width(14.dp))
        }
    }
}

/**
 * The round button that opens the full tag list. A dot shows when a filter is on. The list
 * grows out of the button, so its place on screen is remembered at the tap.
 */
@Composable
internal fun TagsButton(controller: HomeController, color: Color, surface: Modifier, interactive: Boolean, size: androidx.compose.ui.unit.Dp = 36.dp) {
    val holder = remember { CoordinatesHolder() }
    val active = controller.tagFilter.isNotEmpty()
    Box(
        Modifier
            .size(size)
            .onPlaced { holder.value = it }
            .then(surface)
            .clip(CircleShape)
            .then(
                if (interactive) {
                    Modifier.clickable { controller.openTagSheet(holder.value?.takeIf { it.isAttached }?.boundsInRoot()) }
                } else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        LauncherIcon(LauncherIcons.Filter, contentDescription = "All tags", tint = color, size = (size * 0.46f).coerceAtLeast(18.dp))
        if (active) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = size * 0.19f, end = size * 0.19f)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(HomeTheme.accent)
            )
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
    ownFill: Boolean = true,
    onTap: () -> Unit,
    onHold: (() -> Unit)?,
) {
    if (LocalControls.current?.theme != null) {
        ThemeChip(
            label, on, height = 34.dp, icon = icon, trailing = count?.toString(), ownFill = ownFill, restFill = false, restColor = color,
            onLongClick = onHold, onClick = if (interactive) onTap else null,
        )
        return
    }
    val shape = RoundedCornerShape(50)
    val accent = HomeTheme.accent
    val textColor = if (on) readableOn(accent) else color
    Row(
        Modifier
            .height(34.dp)
            .clip(shape)
            .background(if (on && ownFill) accent.copy(alpha = 0.9f) else Color.Transparent)
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

/** Where the strip's sliding selection was last drawn (its start and end), and the gap it is closing. */
private class SlideHolder {
    var edges: Pair<Float, Float>? = null
    var deltaStart = 0f
    var deltaEnd = 0f
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
    val holder = remember { CoordinatesHolder() }
    val accent = HomeTheme.accent
    val text = readableOn(accent)
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.9f))
            .onPlaced { holder.value = it }
            .then(if (interactive) Modifier.clickable { controller.openTagSheet(holder.value?.takeIf { it.isAttached }?.boundsInRoot()) } else Modifier)
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
 *
 * 0.10.2: everything the thumb needs is at the bottom: the buttons, the search field above them and the pinned
 * tags right above the field; the list grows upwards from there (most used, then all A to Z). The pin is on the
 * left of a row and the tick on the right.
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
    /** 0 at the button, 1 open; read while laying out and drawing only (0.11.1). */
    progress: () -> Float,
) {
    val settings = controller.data.settings
    val color = sheetContentColor(glass, lightBackground)
    var query by remember { mutableStateOf("") }
    val pinned = config.pinned
    val filter = controller.tagFilter
    val now = remember { System.currentTimeMillis() }
    val sections = remember(org, query, pinned, usage, counts) {
        val all = org.tags.sortedBy { it.name.lowercase() }
        if (query.isNotBlank()) {
            // The best match nearest the field: names that start with what is typed first.
            val q = query.trim()
            listOf("Matching" to all.filter { it.name.contains(q, ignoreCase = true) }.sortedBy { if (it.name.startsWith(q, ignoreCase = true)) 0 else 1 })
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
    fun togglePin(tag: Tag) {
        val current = settings.get(LauncherSettings.TagPinned)
        settings.set(LauncherSettings.TagPinned, if (tag.id in current) current - tag.id else current + tag.id)
    }

    val density = LocalDensity.current
    val insets = WindowInsets.navigationBars.union(WindowInsets.ime)
    var origin by remember { mutableStateOf(Offset.Zero) }
    val from = controller.tagSheetOrigin
    // The glass grows from the button's rounded shape into the sheet's.
    val startRadius = with(density) { (from?.height ?: 56.dp.toPx()) / 2f }
    val endRadius = with(density) { 30.dp.toPx() }
    val latestProgress = androidx.compose.runtime.rememberUpdatedState(progress)
    fun radius() = startRadius + (endRadius - startRadius) * latestProgress.value().coerceIn(0f, 1f)
    val corners = remember(startRadius, endRadius) { RoundedCornerShape(GrowingCorner { radius() }) }

    Box(
        Modifier
            .fillMaxSize()
            .onPlaced { origin = it.positionInRoot() }
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.25f * progress().coerceIn(0f, 1f))) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controller.tagSheet = false },
    ) {
      Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
          Box(Modifier.sheetSurface(backdrop, corners, glass, lightBackground).bloomLight().boardPicture(corners))
          Column(
            Modifier
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(top = 14.dp, bottom = 10.dp),
          ) {
            // W2: the list takes the height that is left above the search and the buttons, and scrolls.
            // 0.10.2: it grows upwards from the field: the pinned tags (or the best match) nearest the thumb, then the
            // most used, then all A to Z at the top; a section's title sits above its rows.
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).padding(bottom = 6.dp), reverseLayout = true) {
                if (org.tags.isEmpty()) {
                    item {
                        Text(
                            "No tags yet. Hold an app and choose Tags, or create one from the tabs pill.",
                            modifier = Modifier.padding(22.dp),
                            style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.8f), fontSize = 14.sp),
                        )
                    }
                }
                for ((title, list) in sections) {
                    // Reversed layout: emitted from the bottom up. A to Z reads downwards; the others start at the bottom.
                    val rows = if (title == "All tags") list.asReversed() else list
                    items(rows, key = { "$title:${it.id}" }) { tag ->
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
                            onApps = {
                                controller.tagSheet = false
                                controller.tagApps = tag
                            },
                        )
                    }
                    item(key = "h:$title") {
                        Text(
                            title,
                            modifier = Modifier.padding(start = 22.dp, top = 10.dp, bottom = 4.dp),
                            style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.65f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
            // Search field of its own: with many tags, typing two letters is faster than scrolling.
            // W3: the search field in the theme's button colours.
            val (fieldBackground, fieldInk) = fieldStyle(color)
            Row(
                Modifier
                    .padding(horizontal = 14.dp)
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .then(fieldBackground)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LauncherIcon(LauncherIcons.Search, contentDescription = null, tint = fieldInk, size = 18.dp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Find a tag", style = TextStyle(fontFamily = HomeTheme.font, color = fieldInk.copy(alpha = 0.6f), fontSize = 15.sp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = HomeTheme.font, color = fieldInk, fontSize = 15.sp),
                        cursorBrush = SolidColor(fieldInk),
                        // The hint is the field's label too (screen readers, and the emulator test finds the field by it).
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Find a tag" },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy((8 * themeGap).dp),
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
        },
      ) { measurables, constraints ->
        val margin = 12.dp.roundToPx()
        // W2: above the keyboard and the navigation bar, and above the drawer's field when it is at the bottom
        // (it is drawn over the drawer, so it hid the sheet's buttons).
        val pillTop = controller.drawerPillTop?.let { (it - origin.y).roundToInt() }?.takeIf { it in 1 until constraints.maxHeight }
        val bottom = maxOf(insets.getBottom(this) + margin, if (pillTop != null) constraints.maxHeight - pillTop + margin else 0)
        val top = 48.dp.roundToPx()
        val width = constraints.maxWidth - margin * 2
        val room = (constraints.maxHeight - bottom - top).coerceAtLeast(0)
        val content = measurables[1].measure(
            Constraints(minWidth = width, maxWidth = width, maxHeight = minOf(room, ((constraints.maxHeight - bottom) * SheetMaxFraction).roundToInt()))
        )
        val end = androidx.compose.ui.geometry.Rect(
            margin.toFloat(), (constraints.maxHeight - bottom - content.height).toFloat(),
            (margin + width).toFloat(), (constraints.maxHeight - bottom).toFloat(),
        )
        val start = from?.translate(-origin) ?: androidx.compose.ui.geometry.Rect(
            end.center.x - 28.dp.toPx(), end.bottom - 56.dp.toPx(), end.center.x + 28.dp.toPx(), end.bottom,
        )
        val p = progress()
        val rect = lerpRect(start, end, p)
        val glassPlaceable = measurables[0].measure(
            Constraints.fixed(rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1))
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            glassPlaceable.place(rect.left.roundToInt(), rect.top.roundToInt())
            content.placeWithLayer(end.left.roundToInt(), end.top.roundToInt()) {
                // The list appears once the glass has mostly grown, and never outside it.
                alpha = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
                clip = true
                shape = LocalRect(rect.translate(-end.topLeft), radius())
            }
        }
      }
    }
}

private fun lerpRect(a: androidx.compose.ui.geometry.Rect, b: androidx.compose.ui.geometry.Rect, t: Float) = androidx.compose.ui.geometry.Rect(
    a.left + (b.left - a.left) * t,
    a.top + (b.top - a.top) * t,
    maxOf(a.left + (b.left - a.left) * t + 1f, a.right + (b.right - a.right) * t),
    maxOf(a.top + (b.top - a.top) * t + 1f, a.bottom + (b.bottom - a.bottom) * t),
)

/**
 * The tag sheet's glass corners (0.11.1): from the button's round ends to the sheet's, as it grows. Its size changes
 * every frame while it grows, so its corners are worked out again each frame, with the radius of that moment; until
 * then a new shape was made each frame by composing the sheet (and the drawer around it) again.
 */
private class GrowingCorner(private val radius: () -> Float) : androidx.compose.foundation.shape.CornerSize {
    override fun toPx(shapeSize: androidx.compose.ui.geometry.Size, density: androidx.compose.ui.unit.Density): Float = radius()
}

/** Clips a layer to [rect] (in the layer's own coordinates) with rounded corners. */
private class LocalRect(private val rect: androidx.compose.ui.geometry.Rect, private val radius: Float) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density) =
        androidx.compose.ui.graphics.Outline.Rounded(androidx.compose.ui.geometry.RoundRect(rect, androidx.compose.ui.geometry.CornerRadius(radius)))
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
    onApps: () -> Unit,
) {
    val accent = HomeTheme.accent
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOnly).padding(start = 6.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // W3: the pin and the check in the theme's round-button and chip colours. 0.10.2: the pin on the left, the
        // tick on the right, under the thumb.
        ThemeToggleIcon(LauncherIcons.Star, pinned, color, if (pinned) "Unpin" else "Pin", onPin)
        Text(tag.name, modifier = Modifier.weight(1f).padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 15.sp))
        Text("$count", modifier = Modifier.padding(horizontal = 8.dp), style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.6f), fontSize = 13.sp))
        // W6: every app with a check for this tag.
        ThemeToggleIcon(LauncherIcons.Apps, false, color, "Apps in ${tag.name}", onApps)
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
            ThemeCheck(selected, color, 22.dp)
        }
    }
}

@Composable
private fun SheetPill(label: String, color: Color, modifier: Modifier, filled: Boolean = false, onClick: () -> Unit) {
    // W3: Done is the theme's main button, the others its secondary buttons.
    if (currentTheme != null) {
        GlassButton(label, color, modifier, emphasis = if (filled) ControlEmphasis.Main else ControlEmphasis.Secondary, onClick = onClick)
        return
    }
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
