package dev.liquid.launcher.feature.home

import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.data.AppEntry
import dev.liquid.launcher.core.data.AppRef
import dev.liquid.launcher.core.data.LauncherAction
import dev.liquid.launcher.core.data.PillSlot
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.liquidGlass
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun actionIcon(action: LauncherAction): Int = when (action) {
    LauncherAction.OpenDrawer -> LauncherIcons.Apps
    LauncherAction.OpenSearch -> LauncherIcons.Search
    LauncherAction.OpenSettings -> LauncherIcons.Settings
    LauncherAction.EditHome -> LauncherIcons.Edit
    LauncherAction.Notifications -> LauncherIcons.Notifications
    LauncherAction.QuickSettings -> LauncherIcons.Tune
    LauncherAction.LockScreen -> LauncherIcons.Lock
    LauncherAction.Recents -> LauncherIcons.Recents
    LauncherAction.SystemLauncher -> LauncherIcons.Home
    LauncherAction.OpenCalendar -> LauncherIcons.Calendar
    is LauncherAction.LaunchApp, is LauncherAction.LaunchActivity -> LauncherIcons.Apps
    LauncherAction.HomeMenu -> LauncherIcons.Tune
    LauncherAction.Wallpaper, LauncherAction.WallpaperStudio -> LauncherIcons.Image
    LauncherAction.ShuffleWallpaper -> LauncherIcons.Shuffle
    LauncherAction.TagFilter -> LauncherIcons.Filter
    LauncherAction.AddPage -> LauncherIcons.Add
    LauncherAction.Pages -> LauncherIcons.Pages
    LauncherAction.Layouts, LauncherAction.NextLayout, is LauncherAction.SwitchLayout -> LauncherIcons.Layers
    LauncherAction.SaveLayout -> LauncherIcons.Save
    is LauncherAction.Section -> when (action.id) {
        "icons" -> LauncherIcons.Palette
        "drawer" -> LauncherIcons.Apps
        "tags" -> LauncherIcons.Label
        "wallpaper" -> LauncherIcons.Image
        else -> LauncherIcons.Settings
    }
    LauncherAction.None -> LauncherIcons.CircleOutline
}

/** The glass bar: items from settings, its own tap, long-press and swipe actions. */
@Composable
fun Pill(
    config: PillConfig,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    apps: Map<AppRef, AppEntry>,
    controller: HomeController,
    interactive: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = config.cornerPercent)
    val color = if (glass.lightGlass(Surfaces.Pill, lightBackground)) Color(0xFF16181C) else Color.White
    val flexible = config.slots.any { it == PillSlot.Search || it == PillSlot.Spacer }
    // Remembered: the pill recomposes while the drawer moves, and a new gesture lambda would cancel the swipe.
    val gestures = remember(config, controller) { Modifier.pillGestures(config, controller) }
    Row(
        modifier = modifier
            .fillMaxWidth(config.widthFraction)
            .height(config.heightDp.dp)
            .liquidGlass(backdrop, shape, glass.style(Surfaces.Pill, lightBackground))
            .clip(shape)
            .then(if (interactive) gestures else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (flexible) Arrangement.spacedBy(4.dp) else Arrangement.SpaceEvenly,
    ) {
        for (slot in config.slots) {
            PillSlotView(slot, config, color, apps, controller, interactive)
        }
    }
}

private fun Modifier.pillGestures(config: PillConfig, controller: HomeController): Modifier = this
    .pointerInput(config.tap, config.longPress) {
        detectTapGestures(
            onTap = { controller.run(config.tap) },
            onLongPress = { controller.run(config.longPress) },
        )
    }
    .pointerInput(config.swipeUp, config.swipeDown) {
        val tracker = VelocityTracker()
        var total = 0f
        val follow = config.swipeUp == LauncherAction.OpenDrawer
        detectVerticalDragGestures(
            onDragStart = {
                tracker.resetTracking()
                total = 0f
            },
            onVerticalDrag = { change, dy ->
                tracker.addPointerInputChange(change)
                total += dy
                change.consume()
                if (follow && (total < 0 || controller.drawer.value > 0f)) {
                    controller.dragDrawerBy(-dy / controller.screenHeight)
                }
            },
            onDragEnd = {
                val velocity = tracker.calculateVelocity().y
                val threshold = 40.dp.toPx()
                when {
                    follow && controller.drawer.value > 0f -> controller.settleDrawer(velocity)
                    total < -threshold || velocity < -1000f -> controller.run(config.swipeUp)
                    total > threshold || velocity > 1000f -> controller.run(config.swipeDown)
                }
            },
            onDragCancel = { if (follow) controller.settleDrawer(0f) },
        )
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RowScope.PillSlotView(
    slot: PillSlot,
    config: PillConfig,
    color: Color,
    apps: Map<AppRef, AppEntry>,
    controller: HomeController,
    interactive: Boolean,
) {
    fun Modifier.press(onClick: () -> Unit): Modifier =
        if (!interactive) this else combinedClickable(
            interactionSource = null,
            indication = null,
            onClick = onClick,
            onLongClick = { controller.run(config.longPress) },
        )

    val textStyle = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    when (slot) {
        PillSlot.Search -> Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .press { controller.openSearch() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherIcon(LauncherIcons.Search, contentDescription = "Search", tint = color, size = config.iconSizeDp.dp)
            if (config.hint.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                Text(config.hint, style = textStyle.copy(color = color.copy(alpha = 0.75f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        PillSlot.Spacer -> Spacer(Modifier.weight(1f))

        PillSlot.Date -> {
            val now = rememberMinuteTick()
            val text = remember(now) { LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) }
            Box(Modifier.fillMaxHeight().padding(horizontal = 6.dp).press { controller.run(LauncherAction.OpenCalendar) }, contentAlignment = Alignment.Center) {
                Text(text, style = textStyle, maxLines = 1)
            }
        }

        PillSlot.Time -> {
            val now = rememberMinuteTick()
            val text = remember(now) { LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())) }
            Box(
                Modifier.fillMaxHeight().padding(horizontal = 6.dp).press { controller.start(Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
                contentAlignment = Alignment.Center,
            ) {
                Text(text, style = textStyle, maxLines = 1)
            }
        }

        is PillSlot.Action -> Box(
            Modifier
                .size((config.iconSizeDp + 16).dp)
                .clip(CircleShape)
                .press { controller.run(slot.action) },
            contentAlignment = Alignment.Center,
        ) {
            LauncherIcon(actionIcon(slot.action), contentDescription = null, tint = color, size = config.iconSizeDp.dp)
        }

        is PillSlot.App -> {
            val app = apps[slot.ref]
            Box(
                Modifier
                    .size((config.iconSizeDp + 16).dp)
                    .clip(CircleShape)
                    .press { if (app != null) controller.launch(app) },
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(app, true, Modifier.size((config.iconSizeDp + 6).dp), app?.label)
            }
        }
    }
}
