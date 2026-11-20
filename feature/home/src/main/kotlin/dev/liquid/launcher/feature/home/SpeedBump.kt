package dev.liquid.launcher.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.liquidGlass
import kotlinx.coroutines.delay

/**
 * The speed bump (C1, 0.9.4): an app tagged as distracting, asked for while a study block runs, waits
 * behind this card. "Back to <block>" is the main button; "Open anyway" unlocks after five seconds.
 * It grows out of the icon that was tapped and leaves through [Presence] like the other overlays.
 */
@Composable
internal fun SpeedBumpOverlay(bump: SpeedBump, glass: GlassConfig, lightBackground: Boolean, backdrop: Backdrop, controller: HomeController) {
    val color = sheetContentColor(glass, lightBackground)
    val appear = remember { Animatable(0f) }
    var wait by remember(bump) { mutableIntStateOf(5) }
    LaunchedEffect(bump) { appear.animateTo(1f, Motion.enter()) }
    // 0.11.1: on leaving it shrinks back towards the icon it grew from.
    val presence = LocalPresence.current
    LaunchedEffect(bump) {
        while (wait > 0) {
            delay(1000)
            wait--
        }
    }
    val tick = rememberMinuteTick()
    val left = remember(tick, bump) { (bump.block.end - System.currentTimeMillis()).coerceAtLeast(0) }
    val accent = eventColor(bump.block)
    val origin = bump.bounds
    val shape = RoundedCornerShape(30.dp)
    val layer = rememberLayerBackdrop()
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = null, indication = null) { controller.speedBump = null },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val p = appear.value * presence.value
                    val s = 0.86f + 0.14f * p
                    scaleX = s
                    scaleY = s
                    // From the icon that was tapped, when there is one.
                    if (origin != null && size.width > 0f) {
                        val dx = origin.exactCenterX() - (controller.screenWidth / 2f)
                        val dy = origin.exactCenterY() - (controller.screenHeight / 2f)
                        translationX = dx * (1f - p)
                        translationY = dy * (1f - p)
                    }
                }
                .sheetSurface(backdrop, shape, glass, lightBackground, exported = layer)
                .bloomLight()
                .clip(shape)
                .boardPicture(shape)
                .clickable(interactionSource = null, indication = null) { }
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProvideControls(controller, glass, layer) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                    AppIcon(bump.app, false, Modifier.size(30.dp), bump.app.label)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    bump.block.title.subject + " · " + studyDuration(left) + " left",
                    style = studyText(color, 18, FontWeight.SemiBold), textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${bump.app.label} can wait until the block ends.",
                    style = studyText(color, 13, alpha = 0.75f), textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassButton(
                        if (wait > 0) "Open anyway · $wait" else "Open anyway", color,
                        enabled = wait == 0,
                    ) { controller.openAnyway() }
                    Spacer(Modifier.width(2.dp))
                    GlassButton(
                        "Back to ${bump.block.title.subject}", color, icon = LauncherIcons.School,
                        emphasis = ControlEmphasis.Main, accent = accent,
                    ) { controller.backToBlock() }
                }
            }
        }
    }
}
