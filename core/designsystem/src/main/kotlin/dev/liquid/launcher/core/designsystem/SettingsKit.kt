package dev.liquid.launcher.core.designsystem

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/*
 * 0.15.0 (D191), shared since 0.15.2 (D193): the settings kit, WM Keyboard's house style (github.com/wasi-master/wmkeyboard,
 * MIT licence, (c) 2026 Wasi Master: its app/SettingsUi.kt, SettingsMotion.kt and MainActivity.kt's navigation), in the
 * wallpaper's colours. Settings and the Theme lab (drawn by the home module) are built from these pieces, so every
 * screen of Settings looks and moves the same: a hue per place on icon tiles and headings, headings with breadcrumbs
 * that fold into the bar, rows on cards stacked in groups, page pushes with flying tiles, rows staggered in, segmented
 * tabs, chips, an expressive slider.
 */

/** One step of the breadcrumbs: its name, and how deep in the back stack the screen it names is (tap: back there). */
data class KitCrumb(val label: String, val depth: Int)

/** What the screen being drawn is: its hue, its icon, the way to it, and the key its icon tile flies by. */
data class KitScreenInfo(
    val accent: Color? = null,
    val icon: Int? = null,
    val crumbs: List<KitCrumb> = emptyList(),
    val flightKey: String? = null,
)

val LocalKitScreen = staticCompositionLocalOf { KitScreenInfo() }

/** Back to the screen [depth] deep (a breadcrumb). */
val LocalKitBackTo = staticCompositionLocalOf<(Int) -> Unit> { {} }

/** The hue rows on this screen wear: the screen's own, else the theme's primary. */
@Composable
fun kitAccent(): Color = LocalKitScreen.current.accent ?: MaterialTheme.colorScheme.primary

/** The background is dark (the theme follows the system). */
@Composable
fun kitDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

// ---- icon tiles ----

val KitTileSize = 40.dp
private val KitTileRadius = 13.dp

/**
 * A rounded tile tinted with [accent] behind an icon: WM Keyboard's most recognisable piece. Its glyph takes the accent,
 * a little darker on a light background so it reads.
 */
@Composable
fun KitTile(icon: Int, accent: Color, modifier: Modifier = Modifier, size: Dp = KitTileSize) {
    val dark = kitDark()
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(KitTileRadius * (size / KitTileSize)))
            .background(accent.copy(alpha = if (dark) 0.24f else 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        LauncherIcon(icon, contentDescription = null, tint = if (dark) accent else lerp(accent, Color.Black, 0.28f), size = size * 0.55f)
    }
}

// ---- the heading's colours ----

/** The heading at the top of a page (it only warms towards the place's hue) and the bar it leaves when scrolled. */
data class KitBarTints(val expanded: Color, val collapsed: Color)

@Composable
fun kitBarTints(accent: Color?): KitBarTints {
    val surface = MaterialTheme.colorScheme.surface
    val container = MaterialTheme.colorScheme.surfaceContainer
    if (accent == null) return KitBarTints(surface, container)
    val dark = kitDark()
    return KitBarTints(
        expanded = lerp(surface, accent, if (dark) 0.07f else 0.05f),
        collapsed = lerp(container, accent, if (dark) 0.18f else 0.13f),
    )
}

// ---- grouped cards ----

/** The radius at the two ends of a group's stack of cards, and where one card meets the next. */
private val GroupEndCorner = 24.dp
private val GroupInnerCorner = 6.dp
private val GroupGap = 3.dp

/** Rows inside a group draw no card of their own: the group draws them. */
val LocalKitInGroup = compositionLocalOf { false }

/**
 * Rows stacked into one group, each on its own card (WM Keyboard's GroupCard): the ends of the stack rounded at 24 dp,
 * the cards between at 6 dp, 3 dp apart. A row with nothing to show takes no card and no gap. The cards are drawn by
 * the group itself, from where its rows were placed, so any row (an option, a link, an editor) can be part of one.
 */
@Composable
fun KitGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val color = MaterialTheme.colorScheme.surfaceContainer
    val bounds = remember { ArrayList<Pair<Float, Float>>() }
    Layout(
        content = { CompositionLocalProvider(LocalKitInGroup provides true, content = content) },
        modifier = modifier
            .padding16()
            .drawBehind {
                val end = GroupEndCorner.toPx()
                val inner = GroupInnerCorner.toPx()
                val shown = bounds.filter { it.second > it.first }
                shown.forEachIndexed { i, (top, bottom) ->
                    val t = if (i == 0) end else inner
                    val b = if (i == shown.lastIndex) end else inner
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = 0f, top = top, right = size.width, bottom = bottom,
                                topLeftCornerRadius = CornerRadius(t), topRightCornerRadius = CornerRadius(t),
                                bottomRightCornerRadius = CornerRadius(b), bottomLeftCornerRadius = CornerRadius(b),
                            ),
                        )
                    }
                    drawPath(path, color)
                }
            },
    ) { measurables, constraints ->
        val gap = GroupGap.roundToPx()
        val loose = constraints.copy(minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        var y = 0
        val positions = IntArray(placeables.size)
        var first = true
        placeables.forEachIndexed { i, p ->
            if (p.height > 0 && !first) y += gap
            positions[i] = y
            if (p.height > 0) {
                y += p.height
                first = false
            }
        }
        bounds.clear()
        placeables.forEachIndexed { i, p -> bounds += positions[i].toFloat() to (positions[i] + p.height).toFloat() }
        layout(constraints.maxWidth, y) {
            placeables.forEachIndexed { i, p -> p.place(0, positions[i]) }
        }
    }
}

private fun Modifier.padding16(): Modifier = this.padding(horizontal = 16.dp)

/** A card of its own for a row that is not in a group: rounded all round, at the page's margins. */
@Composable
fun Modifier.kitLoneCard(): Modifier {
    if (LocalKitInGroup.current) return this
    return this
        .padding(horizontal = 16.dp, vertical = 2.dp)
        .clip(RoundedCornerShape(GroupEndCorner))
        .background(MaterialTheme.colorScheme.surfaceContainer)
}

// ---- motion ----

/** A page push's length, and its curve: Material's emphasized decelerate (leaves fast, lands slowly). */
const val KitNavMs = 340
val KitNavEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalKitShared = compositionLocalOf<SharedTransitionScope?> { null }
val LocalKitNav = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
private val FlightBounds = BoundsTransform { _, _ -> tween(KitNavMs, easing = FastOutSlowInEasing) }

/**
 * One end of a kitFlight between screens: a row's icon tile and the heading's of the page it opens. Nothing when the screens
 * are not animating (or outside the navigation).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.kitFlight(key: String?): Modifier {
    if (key == null) return this
    val shared = LocalKitShared.current ?: return this
    val anim = LocalKitNav.current ?: return this
    return with(shared) {
        this@kitFlight.sharedElement(rememberSharedContentState(key), anim, boundsTransform = FlightBounds, zIndexInOverlay = 1f)
    }
}

/** How long one row takes to settle in, the beat between two, and how many beats at most (the rest come together). */
private const val RowInMs = 220
private const val RowStepMs = 35
private const val RowMaxSteps = 8
private val RowRise = 6.dp

/**
 * A row arriving with its page: fades in and rises 6 dp, [index] beats after the first (WM Keyboard's StaggerIn). Read in
 * the draw phase; plays once, when the row is first composed.
 */
@Composable
fun Modifier.kitStaggerIn(index: Int): Modifier {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        shown.animateTo(1f, tween(RowInMs, delayMillis = RowStepMs * index.coerceIn(0, RowMaxSteps), easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        val t = shown.value
        alpha = t
        translationY = (1f - t) * RowRise.toPx()
    }
}


/**
 * Screen frame, 0.15.0 (D191) in WM Keyboard's style: a bar with the back button, and under it the page's heading, its
 * name large beside its icon tile on a faint wash of its hue, with the breadcrumbs that led here. As the page scrolls the
 * heading goes under the bar and the name takes its place there. A screen that scrolls on its own ([scroll] false)
 * passes its [headerScroll], and the heading folds away as it moves.
 */
@Composable
fun KitScaffold(
    title: String,
    onBack: () -> Unit,
    scroll: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    headerScroll: androidx.compose.foundation.ScrollState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val info = LocalKitScreen.current
    val tints = kitBarTints(info.accent)
    val own = rememberScrollState()
    val folding = if (scroll) own else headerScroll
    val headPx = with(androidx.compose.ui.platform.LocalDensity.current) { KitHeadingFold.toPx() }
    val collapsed: () -> Float = { ((folding?.value ?: 0) / headPx).coerceIn(0f, 1f) }
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .drawBehind { drawRect(androidx.compose.ui.graphics.lerp(tints.expanded, tints.collapsed, collapsed())) }
                    .statusBarsPadding(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        LauncherIcon(LauncherIcons.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp)
                            .graphicsLayer {
                                // The name moves into the bar as the heading goes under it.
                                val t = ((collapsed() - 0.55f) / 0.45f).coerceIn(0f, 1f)
                                alpha = t
                                translationY = (1f - t) * 10.dp.toPx()
                            },
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    actions()
                }
            }
            if (scroll) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(own)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                ) {
                    KitPageHeading(title, info, tints, fold = null)
                    Spacer(Modifier.height(8.dp))
                    content()
                }
            } else {
                KitPageHeading(title, info, tints, fold = if (headerScroll != null) collapsed else null)
                Column(Modifier.fillMaxSize().navigationBarsPadding(), content = content)
            }
        }
    }
}

/** How far a page scrolls before its heading has gone under the bar. */
private val KitHeadingFold = 96.dp

/** The expanded title's size: long names start smaller so the heading stays on one line (WM Keyboard's bands). */
private fun kitHeadingSp(title: String): Float = when {
    title.length <= 14 -> 32f
    title.length <= 18 -> 28f
    title.length <= 22 -> 24f
    title.length <= 25 -> 21f
    else -> 19f
}

@Composable
private fun KitPageHeading(title: String, info: KitScreenInfo, tints: KitBarTints, fold: (() -> Float)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (fold == null) Modifier else Modifier
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val h = (placeable.height * (1f - fold())).roundToInt().coerceAtLeast(0)
                        layout(placeable.width, h) { placeable.place(0, h - placeable.height) }
                    },
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(tints.expanded)
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = info.icon
            val accent = info.accent
            if (icon != null && accent != null) {
                KitTile(icon, accent, Modifier.kitFlight(info.flightKey), size = 46.dp)
                Spacer(Modifier.width(14.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = androidx.compose.ui.unit.TextUnit(kitHeadingSp(title), androidx.compose.ui.unit.TextUnitType.Sp),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(-0.018f, androidx.compose.ui.unit.TextUnitType.Em),
                ),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        if (info.crumbs.isNotEmpty()) KitBreadcrumbs(info.crumbs, info.accent, tints.collapsed)
    }
}

/** The way here, as chips: the place you are in wears its hue; a tap on another goes back there. */
@Composable
private fun KitBreadcrumbs(crumbs: List<KitCrumb>, accent: Color?, band: Color) {
    val backTo = LocalKitBackTo.current
    val tint = accent ?: MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .background(band)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { i, crumb ->
            val last = i == crumbs.lastIndex
            if (i > 0) {
                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
            }
            val shape = RoundedCornerShape(50)
            Row(
                Modifier
                    .clip(shape)
                    .background(if (last) tint.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
                    .then(if (last) Modifier.border(1.dp, tint.copy(alpha = 0.55f), shape) else Modifier)
                    .clickable(enabled = !last) { backTo(crumb.depth) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (i == 0) {
                    LauncherIcon(LauncherIcons.Home, contentDescription = null, size = 15.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    crumb.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (last) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A group's heading, level with the text of the rows under it, in the place's hue. */
@Composable
fun KitSection(text: String) {
    val accent = kitAccent()
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        color = if (kitDark()) androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.onSurface, accent, 0.45f) else androidx.compose.ui.graphics.lerp(accent, Color.Black, 0.28f),
        modifier = Modifier.padding(start = 32.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
    )
}

/**
 * A row that opens something (WM Keyboard's row): its icon on a tile in the place's hue, the title, what it holds, a
 * value on the right and a chevron. On its own card, or on its group's ([KitGroup]). [flightKey]: the tile flies into
 * the heading of the page it opens.
 */
@Composable
fun KitNavRow(
    title: String,
    summary: String = "",
    icon: Int? = null,
    accent: Color? = null,
    value: String? = null,
    flightKey: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .kitLoneCard()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            KitTile(icon, accent ?: kitAccent(), Modifier.kitFlight(flightKey))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
            if (summary.isNotEmpty()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.widthIn(max = 132.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        LauncherIcon(
            LauncherIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun KitCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = color,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}


/**
 * WM Keyboard's slider (Material 3 Expressive): a thick rounded track, filled in the place's hue up to a tall handle bar
 * with a gap on each side, and a dot at the far end. Drag or tap anywhere on it.
 */
@Composable
fun KitSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = kitAccent()
    val rest = MaterialTheme.colorScheme.secondaryContainer
    val change by androidx.compose.runtime.rememberUpdatedState(onValueChange)
    val finish by androidx.compose.runtime.rememberUpdatedState(onValueChangeFinished)
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val shown by androidx.compose.animation.core.animateFloatAsState(
        fraction,
        androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 1400f),
        label = "slider",
    )
    var pressed by remember { mutableStateOf(false) }
    val handleW by androidx.compose.animation.core.animateDpAsState(if (pressed) 3.dp else 4.dp, label = "sliderHandle")
    fun at(x: Float, width: Float): Float = valueRange.start + (x / width.coerceAtLeast(1f)).coerceIn(0f, 1f) * span
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                ) {
                    change(at(it.x, size.width.toFloat()))
                    finish()
                }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { pressed = true; change(at(it.x, size.width.toFloat())) },
                    onDragEnd = { pressed = false; finish() },
                    onDragCancel = { pressed = false; finish() },
                ) { pointer, _ ->
                    pointer.consume()
                    change(at(pointer.position.x, size.width.toFloat()))
                }
            }
            .drawBehind {
                val trackH = 16.dp.toPx()
                val top = (size.height - trackH) / 2f
                val gap = 6.dp.toPx()
                val hw = handleW.toPx()
                val x = shown * size.width
                val r = androidx.compose.ui.geometry.CornerRadius(trackH / 2f)
                val small = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                val activeEnd = (x - gap - hw / 2f).coerceAtLeast(0f)
                if (activeEnd > 0f) {
                    drawRoundRect(
                        accent.copy(alpha = if (enabled) 1f else 0.4f),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                        size = androidx.compose.ui.geometry.Size(activeEnd, trackH),
                        cornerRadius = if (activeEnd > trackH) r else small,
                    )
                }
                val restStart = (x + gap + hw / 2f).coerceAtMost(size.width)
                if (restStart < size.width) {
                    drawRoundRect(
                        rest,
                        topLeft = androidx.compose.ui.geometry.Offset(restStart, top),
                        size = androidx.compose.ui.geometry.Size(size.width - restStart, trackH),
                        cornerRadius = r,
                    )
                    // The far end's dot.
                    drawCircle(accent.copy(alpha = 0.8f), radius = 2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width - trackH / 2f, size.height / 2f))
                }
                drawRoundRect(
                    accent,
                    topLeft = androidx.compose.ui.geometry.Offset(x - hw / 2f, 0f),
                    size = androidx.compose.ui.geometry.Size(hw, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(hw / 2f),
                )
            },
    )
}

/**
 * The page's tabs as one segmented control (WM Keyboard's "Auto | Grouped | Separate"): the chosen one under a
 * highlight in the place's hue that slides from tab to tab; a count says how many options of a tab you changed.
 */
@Composable
fun KitSegments(labels: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val accent = kitAccent()
    val count = labels.size.coerceAtLeast(1)
    val index = selected.coerceIn(0, count - 1)
    val slide by androidx.compose.animation.core.animateFloatAsState(
        index.toFloat(),
        androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 520f),
        label = "segmentHighlight",
    )
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(44.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        // The highlight, under the labels.
        Box(
            Modifier
                .fillMaxSize()
                .padding(3.dp)
                .drawBehind {
                    val w = size.width / count
                    drawRoundRect(
                        accent.copy(alpha = 0.22f),
                        topLeft = androidx.compose.ui.geometry.Offset(w * slide, 0f),
                        size = androidx.compose.ui.geometry.Size(w, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                },
        )
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, label ->
                val on = i == index
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(shape)
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium),
                        color = if (on) (if (kitDark()) accent else lerp(accent, Color.Black, 0.3f)) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Choices as chips (WM Keyboard's "Tap | Key | Click"): the chosen one washed in the place's hue with a tick; scrolls
 * sideways when they do not fit.
 */
@Composable
fun KitChips(options: List<Pair<String, String>>, selected: (String) -> Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onChoose: (String) -> Unit) {
    val accent = kitAccent()
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        options.forEach { (option, label) ->
            val on = selected(option)
            val shape = RoundedCornerShape(12.dp)
            Row(
                Modifier
                    .clip(shape)
                    .background(if (on) accent.copy(alpha = 0.22f) else Color.Transparent)
                    .border(1.dp, if (on) accent.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant, shape)
                    .clickable(enabled = enabled) { onChoose(option) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (on) {
                    LauncherIcon(LauncherIcons.Check, contentDescription = null, size = 16.dp, tint = if (kitDark()) accent else lerp(accent, Color.Black, 0.3f))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A row that folds a group's fine tuning open or closed; its chevron turns. */
@Composable
fun KitFoldRow(title: String, summary: String, open: Boolean, onToggle: () -> Unit) {
    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 90f else 0f, label = "foldChevron")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        KitTile(dev.liquid.launcher.core.designsystem.LauncherIcons.Tune, kitAccent())
        Spacer(Modifier.padding(start = 16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
            if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        dev.liquid.launcher.core.designsystem.LauncherIcon(
            dev.liquid.launcher.core.designsystem.LauncherIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { rotationZ = turn },
        )
    }
}


/**
 * One chip, with Material's FilterChip parameters (so a screen can switch to the kit's look by name): washed in the
 * place's hue with a tick when [selected], outlined otherwise.
 */
@Composable
fun KitChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val accent = kitAccent()
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.22f) else Color.Transparent)
            .border(1.dp, if (selected) accent.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            LauncherIcon(LauncherIcons.Check, contentDescription = null, size = 16.dp, tint = if (kitDark()) accent else lerp(accent, Color.Black, 0.3f))
            Spacer(Modifier.width(6.dp))
        }
        androidx.compose.material3.ProvideTextStyle(
            MaterialTheme.typography.labelLarge.copy(color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant),
        ) { label() }
    }
}

/** The kit's slider with Material's Slider parameters (a drop-in for screens built on Material's). */
@Composable
fun KitSlide(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
) = KitSlider(value, onValueChange, { onValueChangeFinished?.invoke() }, valueRange, enabled, modifier)
