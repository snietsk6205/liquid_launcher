package dev.liquid.launcher.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.designsystem.LauncherIcons

/*
 * W4 (0.10.1): every surface of the home screen leads to its own settings page. A long press on the pill (when it
 * is left on its default), the dock, an open folder, the search page, the hub or the drawer shows one glass chip,
 * "Customise …", where the finger is; a tap opens Settings on that page, the gear opens Settings itself.
 */

data class CustomiseOffer(val section: String, val title: String, val at: Offset)

/** A long press on this surface (where nothing inside takes it) offers "Customise [title]". */
internal fun Modifier.customiseOnLongPress(controller: HomeController, section: String, title: String): Modifier = composed {
    var origin by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(section) {
            detectTapGestures(onLongPress = { controller.offerCustomise(section, title, origin + it) })
        }
}

/** The chip, over everything, until it is tapped or anything else is. */
@Composable
internal fun CustomiseChip(controller: HomeController, offer: CustomiseOffer, ink: Color) {
    val appear = remember(offer) { Animatable(0f) }
    LaunchedEffect(offer) { appear.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = MotionScale.stiffness(520f))) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(offer) { detectTapGestures { controller.customise = null } },
    ) {
        Row(
            Modifier
                .layout { measurable, constraints ->
                    val chip = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    val margin = 12.dp.roundToPx()
                    // Above the finger, kept inside the screen.
                    val x = (offer.at.x - chip.width / 2f).toInt().coerceIn(margin, (constraints.maxWidth - chip.width - margin).coerceAtLeast(margin))
                    val above = (offer.at.y - chip.height - 28.dp.toPx()).toInt()
                    val y = (if (above > margin * 4) above else (offer.at.y + 28.dp.toPx()).toInt()).coerceIn(margin, (constraints.maxHeight - chip.height - margin).coerceAtLeast(margin))
                    layout(constraints.maxWidth, constraints.maxHeight) { chip.place(x, y) }
                }
                .graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    scaleX = 0.6f + 0.4f * p
                    scaleY = 0.6f + 0.4f * p
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
            horizontalArrangement = Arrangement.spacedBy((8 * themeGap).dp),
        ) {
            GlassButton("Customise ${offer.title}", ink, emphasis = ControlEmphasis.Main) {
                controller.customise = null
                controller.openSection(offer.section)
            }
            GlassButton(null, ink, icon = LauncherIcons.Settings, emphasis = ControlEmphasis.Secondary, contentDescription = "All settings") {
                controller.customise = null
                controller.run(dev.liquid.launcher.core.data.LauncherAction.OpenSettings)
            }
        }
    }
}
