package dev.liquid.launcher.feature.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons

/**
 * Screen frame, 0.15.0 (D191) in WM Keyboard's style: a bar with the back button, and under it the page's heading, its
 * name large beside its icon tile on a faint wash of its hue, with the breadcrumbs that led here. As the page scrolls the
 * heading goes under the bar and the name takes its place there. A screen that scrolls on its own ([scroll] false)
 * passes its [headerScroll], and the heading folds away as it moves.
 */
@Composable
internal fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    scroll: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    headerScroll: androidx.compose.foundation.ScrollState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val info = LocalScreenInfo.current
    val tints = barTints(info.accent)
    val own = rememberScrollState()
    val folding = if (scroll) own else headerScroll
    val headPx = with(androidx.compose.ui.platform.LocalDensity.current) { HeadingFold.toPx() }
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
                    PageHeading(title, info, tints, fold = null)
                    Spacer(Modifier.height(8.dp))
                    content()
                }
            } else {
                PageHeading(title, info, tints, fold = if (headerScroll != null) collapsed else null)
                Column(Modifier.fillMaxSize().navigationBarsPadding(), content = content)
            }
        }
    }
}

/** How far a page scrolls before its heading has gone under the bar. */
private val HeadingFold = 96.dp

/** The expanded title's size: long names start smaller so the heading stays on one line (WM Keyboard's bands). */
private fun headingSp(title: String): Float = when {
    title.length <= 14 -> 32f
    title.length <= 18 -> 28f
    title.length <= 22 -> 24f
    title.length <= 25 -> 21f
    else -> 19f
}

@Composable
private fun PageHeading(title: String, info: ScreenInfo, tints: BarTints, fold: (() -> Float)?) {
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
                IconTile(icon, accent, Modifier.flight(info.flightKey), size = 46.dp)
                Spacer(Modifier.width(14.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = androidx.compose.ui.unit.TextUnit(headingSp(title), androidx.compose.ui.unit.TextUnitType.Sp),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(-0.018f, androidx.compose.ui.unit.TextUnitType.Em),
                ),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        if (info.crumbs.isNotEmpty()) Breadcrumbs(info.crumbs, info.accent, tints.collapsed)
    }
}

/** The way here, as chips: the place you are in wears its hue; a tap on another goes back there. */
@Composable
private fun Breadcrumbs(crumbs: List<Crumb>, accent: Color?, band: Color) {
    val backTo = LocalBackTo.current
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
internal fun SectionHeader(text: String) {
    val accent = screenAccent()
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        color = if (darkSettings()) androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.onSurface, accent, 0.45f) else androidx.compose.ui.graphics.lerp(accent, Color.Black, 0.28f),
        modifier = Modifier.padding(start = 32.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
    )
}

/**
 * A row that opens something (WM Keyboard's row): its icon on a tile in the place's hue, the title, what it holds, a
 * value on the right and a chevron. On its own card, or on its group's ([GroupCards]). [flightKey]: the tile flies into
 * the heading of the page it opens.
 */
@Composable
internal fun NavRow(
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
            .loneCard()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconTile(icon, accent ?: screenAccent(), Modifier.flight(flightKey))
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
internal fun InfoCard(
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

@Composable
internal fun BodyText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
internal fun StatusDot(ok: Boolean?, modifier: Modifier = Modifier) {
    val (icon, tint) = when (ok) {
        true -> LauncherIcons.CheckCircle to Color(0xFF2E9E5B)
        false -> LauncherIcons.Error to MaterialTheme.colorScheme.error
        null -> LauncherIcons.CircleOutline to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(modifier) { LauncherIcon(icon, contentDescription = null, tint = tint) }
}

internal fun shareText(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

internal fun startSafely(context: Context, vararg intents: Intent): Boolean {
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (e: Exception) {
            // Try the next candidate.
        }
    }
    return false
}
