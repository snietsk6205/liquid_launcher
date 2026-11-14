package dev.liquid.launcher.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.data.AppEntry
import dev.liquid.launcher.core.data.AppRef
import dev.liquid.launcher.core.data.LauncherAction
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.liquidGlass

/** What the home menu shows: the two pills' items and whether they have labels. */
data class HomeMenuConfig(val setup: List<LauncherAction>, val actions: List<LauncherAction>, val labels: Boolean, val sink: Boolean)

fun dev.liquid.launcher.core.settings.SettingsStore.homeMenuConfig() = HomeMenuConfig(
    setup = get(dev.liquid.launcher.core.settings.LauncherSettings.HomeMenuSetup).map(LauncherAction::decode).filter { it != LauncherAction.None },
    actions = get(dev.liquid.launcher.core.settings.LauncherSettings.HomeMenuActions).map(LauncherAction::decode).filter { it != LauncherAction.None },
    labels = get(dev.liquid.launcher.core.settings.LauncherSettings.HomeMenuLabels),
    sink = get(dev.liquid.launcher.core.settings.LauncherSettings.HomeMenuSink),
)

/** Short name of an action as a home menu item. */
internal fun menuLabel(action: LauncherAction, apps: Map<AppRef, AppEntry>, layoutName: (String) -> String?): String = when (action) {
    LauncherAction.OpenSettings -> "Settings"
    LauncherAction.EditHome -> "Edit"
    LauncherAction.HomeMenu -> "Menu"
    LauncherAction.Wallpaper -> "Wallpaper"
    LauncherAction.WallpaperStudio -> "Wallpapers…"
    LauncherAction.ShuffleWallpaper -> "Shuffle"
    LauncherAction.TagFilter -> "Tags"
    LauncherAction.AddPage -> "Add page"
    LauncherAction.Pages -> "Pages"
    LauncherAction.Layouts -> "Layouts"
    LauncherAction.SaveLayout -> "Save layout"
    LauncherAction.NextLayout -> "Next layout"
    LauncherAction.OpenDrawer -> "Apps"
    LauncherAction.OpenSearch -> "Search"
    LauncherAction.Notifications -> "Notifications"
    LauncherAction.QuickSettings -> "Quick settings"
    LauncherAction.LockScreen -> "Lock"
    LauncherAction.Recents -> "Recents"
    LauncherAction.OpenCalendar -> "Calendar"
    LauncherAction.SystemLauncher -> "System home"
    is LauncherAction.SwitchLayout -> layoutName(action.id) ?: "Layout"
    is LauncherAction.Section -> LauncherAction.sections.firstOrNull { it.first == action.id }?.second?.removeSuffix(" settings") ?: "Settings"
    is LauncherAction.LaunchApp -> apps[action.ref]?.label ?: "App"
    is LauncherAction.LaunchActivity -> action.label
    LauncherAction.None -> ""
}

/**
 * Two glass pills rising at the bottom when empty space is held: "set up" above, "actions"
 * below. Items run with a tap, or by sliding onto them from the long press and letting go.
 */
@Composable
internal fun HomeMenu(
    config: HomeMenuConfig,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    apps: Map<AppRef, AppEntry>,
    controller: HomeController,
) {
    val layouts by controller.data.layouts.state.collectAsState()
    val color = if (glass.lightGlass(Surfaces.Menu, lightBackground)) Color(0xFF16181C) else Color.White
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = MotionScale.stiffness(520f))) }
    fun keyOf(pill: Int, index: Int) = "$pill:$index"
    val byKey = remember(config) {
        buildMap {
            config.setup.forEachIndexed { i, a -> put(keyOf(0, i), a) }
            config.actions.forEachIndexed { i, a -> put(keyOf(1, i), a) }
        }
    }
    fun run(action: LauncherAction) {
        controller.homeMenu = false
        controller.run(action)
    }
    controller.homeMenuRun = { key -> byKey[key]?.let(::run) }
    DisposableEffect(Unit) {
        onDispose {
            controller.homeMenuTargets.clear()
            controller.homeMenuFinger = null
        }
    }
    val finger = controller.homeMenuFinger
    val hover = finger?.let { f -> controller.homeMenuTargets.entries.firstOrNull { it.value.contains(f) }?.key }
    val layoutName: (String) -> String? = { id -> layouts.layouts.firstOrNull { it.id == id }?.name }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controller.homeMenu = false },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val pills = listOf(config.setup, config.actions)
            pills.forEachIndexed { pillIndex, items ->
                if (items.isEmpty()) return@forEachIndexed
                val shape = if (config.labels) RoundedCornerShape(30.dp) else RoundedCornerShape(50)
                Row(
                    Modifier
                        .graphicsLayer {
                            // The lower pill leads, the upper one follows a moment later.
                            val delay = if (pillIndex == 0) 0.12f else 0f
                            val p = ((appear.value - delay) / (1f - delay)).coerceIn(0f, 1.2f)
                            alpha = p.coerceIn(0f, 1f)
                            translationY = (1f - p) * 36.dp.toPx()
                            scaleX = 0.92f + 0.08f * p
                            scaleY = 0.92f + 0.08f * p
                        }
                        .liquidGlass(backdrop, shape, glass.style(Surfaces.Menu, lightBackground))
                        .clip(shape)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items.forEachIndexed { i, action ->
                        val key = keyOf(pillIndex, i)
                        val current = action is LauncherAction.SwitchLayout && action.id == layouts.current
                        MenuItem(
                            action = action,
                            label = menuLabel(action, apps, layoutName),
                            labels = config.labels,
                            color = color,
                            hovered = hover == key,
                            current = current,
                            modifier = Modifier.onPlaced { coords ->
                                if (coords.isAttached) controller.homeMenuTargets[key] = coords.boundsInRoot()
                            },
                            onClick = { run(action) },
                        )
                    }
                }
            }
            if (pills.all { it.isEmpty() }) {
                Text(
                    "The home menu is empty. Add items in Settings → Home menu.",
                    style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 14.sp),
                    modifier = Modifier
                        .liquidGlass(backdrop, RoundedCornerShape(20.dp), glass.style(Surfaces.Menu, lightBackground))
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun MenuItem(
    action: LauncherAction,
    label: String,
    labels: Boolean,
    color: Color,
    hovered: Boolean,
    current: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .widthIn(min = if (labels) 66.dp else 48.dp)
            .clip(shape)
            .background(HomeTheme.accent.copy(alpha = if (hovered) 0.35f else 0f), shape)
            .drawBehind {
                if (current) drawRoundRect(HomeTheme.accent, style = Stroke(1.5.dp.toPx()), cornerRadius = CornerRadius(22.dp.toPx()))
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = if (labels) 8.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            LauncherIcon(actionIcon(action), contentDescription = if (labels) null else label, tint = color, size = 24.dp)
        }
        if (labels) {
            Text(
                label,
                modifier = Modifier.width(72.dp).padding(top = 4.dp),
                style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
