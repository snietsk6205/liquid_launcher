package dev.liquid.launcher.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.designsystem.liquidGlass
import kotlinx.coroutines.delay

/*
 * W2 (0.10.0): one container for every sheet of the home screen, so none can overflow. Before, each sheet built its
 * own glass column with no height limit (the layouts sheet, a widget's options, the wallpaper strip) or a fixed one
 * (the tag list: 380 dp, whose buttons then fell behind the drawer's pill), and the per-app tag dialog listed every
 * tag above its "New tag" field, so from about nine tags the field could no longer be reached.
 */

/** The colour of text and icons on the sheet around them. */
internal val LocalSheetContent = staticCompositionLocalOf { Color.White }

/** At most this share of the space between the status bar and the keyboard (or navigation bar). */
internal const val SheetMaxFraction = 0.85f

/**
 * A sheet rising from the bottom of the screen: a fixed [header] (its [title] first), a body that scrolls between
 * it and a fixed [footer] and row of [actions], never taller than 85 % of the space above the keyboard or navigation bar. The
 * keyboard lifts the whole sheet, so a field in it stays in sight; [bringIntoViewWhenFocused] scrolls a field in the
 * body back into view when the keyboard arrives. Tapping outside calls [onDismiss].
 *
 * [scroll] null: the body scrolls by itself (a LazyColumn); it is given the height that is left, at most.
 */
@Composable
internal fun LiquidSheet(
    onDismiss: () -> Unit,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    title: String? = null,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    /** Fixed under the body, above [actions]: a field that must stay reachable, a summary. */
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    scroll: ScrollState? = rememberScrollState(),
    maxFraction: Float = SheetMaxFraction,
    cornerRadius: Dp = 32.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val color = sheetContentColor(glass, lightBackground)
    // W3: the theme's sheet shape and corners (the board), else rounded.
    val (shape, outline) = sheetOutline(RoundedCornerShape(cornerRadius))
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.enter()) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
    ) {
        val limit = maxHeight * maxFraction.coerceIn(0.3f, 1f)
        CompositionLocalProvider(LocalSheetContent provides color) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .fillMaxWidth()
                    .heightIn(max = limit)
                    .graphicsLayer {
                        alpha = appear.value.coerceIn(0f, 1f)
                        translationY = (1f - appear.value) * 40.dp.toPx()
                    }
                    .sheetSurface(backdrop, shape, glass, lightBackground, outline)
                    .bloomLight()
                    .clip(outline)
                    // Taps on the sheet stay on the sheet.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            ) {
            // W3: the theme's picture and stickers behind the content.
            BoardPicture(Modifier.matchParentSize())
            Column(Modifier.padding(top = 16.dp, bottom = if (actions != null) 14.dp else 16.dp)) {
                if (title != null) SheetTitle(title)
                header?.invoke(this)
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .then(if (scroll != null) Modifier.verticalScroll(scroll) else Modifier),
                    content = content,
                )
                footer?.invoke(this)
                if (actions != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions,
                    )
                }
            }
            }
        }
    }
}

/** The title at the top of a sheet. */
@Composable
internal fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        style = TextStyle(fontFamily = HomeTheme.font, color = LocalSheetContent.current, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    )
}

/** Body text of a sheet, in its colour. */
@Composable
internal fun SheetText(text: String, modifier: Modifier = Modifier, alpha: Float = 0.82f, size: Int = 14) {
    Text(
        text,
        modifier = modifier.padding(horizontal = 20.dp),
        style = TextStyle(fontFamily = HomeTheme.font, color = LocalSheetContent.current.copy(alpha = alpha), fontSize = size.sp),
    )
}

/**
 * Scrolls a text field back into view when it gets the focus and again when the keyboard has risen (the field is
 * focused before the keyboard takes its space, so the first request alone can come too early).
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.bringIntoViewWhenFocused(): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom) {
        if (focused) {
            delay(60)
            requester.bringIntoView()
        }
    }
    bringIntoViewRequester(requester).onFocusChanged { focused = it.isFocused }
}

/**
 * A menu or popup that grows from where it was opened has no bottom edge to rise from, so it is capped
 * here instead: at most [fraction] of the screen's height, scrolling inside (W2).
 */
@Composable
internal fun Modifier.menuHeightLimit(fraction: Float = SheetMaxFraction): Modifier {
    val density = LocalDensity.current
    val screen = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height
    val max = with(density) { (screen * fraction).toDp() }
    return heightIn(max = max)
}

/** A one-line text field of a sheet: a soft capsule with a hint, in the sheet's colour, kept in view while typing. */
@Composable
internal fun SheetField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    onDone: (() -> Unit)? = null,
) {
    val (fieldBackground, color) = fieldStyle(LocalSheetContent.current)
    Row(
        modifier
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(50))
            .then(fieldBackground)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            dev.liquid.launcher.core.designsystem.LauncherIcon(icon, contentDescription = null, tint = color.copy(alpha = 0.8f), size = 18.dp)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 10.dp))
        }
        androidx.compose.foundation.layout.Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(hint, style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.55f), fontSize = 15.sp), maxLines = 1)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 15.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(color),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone?.invoke() }),
                // The hint is the field's label too (screen readers, and the emulator test finds the field by it).
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).bringIntoViewWhenFocused()
                    .semantics { contentDescription = hint },
            )
        }
    }
}

/** A row of a sheet's list with a check circle: [checked] fills it with the accent. */
@Composable
internal fun SheetCheckRow(label: String, checked: Boolean, modifier: Modifier = Modifier, detail: String? = null, onClick: () -> Unit) {
    val color = LocalSheetContent.current
    val accent = HomeTheme.accent
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(Modifier.padding(end = 14.dp)) { ThemeCheck(checked, color, 22.dp) }
        Text(label, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 15.sp))
        if (detail != null) Text(detail, style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.6f), fontSize = 13.sp))
    }
}
