package dev.liquid.launcher.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.data.AppEntry
import dev.liquid.launcher.core.data.AppRef
import dev.liquid.launcher.core.data.HomeLayout
import dev.liquid.launcher.core.designsystem.liquidGlass
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.LauncherSettings as S

data class DockConfig(val enabled: Boolean, val slots: Int, val glass: Boolean, val labels: Boolean) {
    /** Height the dock takes above the pill. */
    val height: Dp get() = if (labels) 84.dp else 68.dp
}

fun SettingsStore.dockConfig() = DockConfig(get(S.DockEnabled), get(S.DockSlots), get(S.DockBackground) == "glass", get(S.DockLabels))

/**
 * A row of apps that stays on every page. Apps are dropped onto it from the drawer, a page
 * or a folder; held, they have the usual menu; dragged, they move to another slot or a page.
 */
@Composable
internal fun Dock(
    config: DockConfig,
    layout: HomeLayout,
    apps: Map<AppRef, AppEntry>,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    color: Color,
    shadow: Shadow?,
    controller: HomeController,
    interactive: Boolean,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(30.dp)
    controller.dockSlots = config.slots
    DisposableEffect(Unit) { onDispose { controller.dockBounds = null } }
    val drag = controller.drag
    Row(
        modifier
            .fillMaxWidth()
            .height(config.height)
            .onPlaced { if (it.isAttached) controller.dockBounds = it.boundsInRoot() }
            .then(if (config.glass) Modifier.liquidGlass(backdrop, shape, glass.style(Surfaces.Pill, lightBackground)).clip(shape) else Modifier)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (slot in 0 until config.slots) {
            val item = layout.dock.firstOrNull { it.x == slot }
            val app = item?.app?.let { apps[it] }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // While something is dragged over the dock, the slot under the finger lights up.
                    .drawBehind {
                        if (drag != null && controller.dockSlotAt(drag.finger) == slot) {
                            drawCircle(color.copy(alpha = 0.22f), radius = size.minDimension * 0.46f)
                        } else if (drag != null && item == null) {
                            drawCircle(color.copy(alpha = 0.08f), radius = size.minDimension * 0.36f)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (item != null && app != null) {
                    key(item.id) {
                        val holder = remember { CoordinatesHolder() }
                        Column(
                            Modifier
                                .onPlaced { holder.value = it }
                                .iconGestures({ holder.value }, appHandler(item, app, holder, controller), interactive),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppIconImage(app.homeIcon, Modifier.size(50.dp), app.label)
                            if (config.labels) {
                                Text(
                                    app.label,
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 11.sp, textAlign = TextAlign.Center, shadow = shadow),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                } else if (controller.editMode && drag == null) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color.copy(alpha = 0.25f)))
                }
            }
        }
    }
}
