package dev.liquid.launcher.feature.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates

/** Callbacks of [iconGestures]. Positions are in root coordinates. */
interface IconGestureHandler {
    fun onTap()
    fun onLongPress()

    /** The finger moved after a long press: the item is picked up. [grab] is the finger inside the item. */
    fun onDragStart(grab: Offset, finger: Offset)
    fun onDrag(finger: Offset)
    fun onDragEnd()
    fun onDragCancel()
}

/**
 * Tap launches, long press opens the menu, long press and move picks the item up. One
 * gesture detector instead of separate tap and drag detectors, so a long press never also
 * counts as a tap. Before the long press nothing is consumed, so a parent can still take
 * a swipe (pager, drawer).
 */
@Composable
fun Modifier.iconGestures(
    coordinates: () -> LayoutCoordinates?,
    handler: IconGestureHandler,
    enabled: Boolean = true,
): Modifier {
    val current by rememberUpdatedState(handler)
    val coords by rememberUpdatedState(coordinates)
    if (!enabled) return this
    // Remembered so recompositions (which happen during a drag) never restart the detector:
    // a new pointerInput lambda would cancel the gesture in progress.
    val gesture = remember { Modifier.iconPointerInput({ current }, { coords() }) }
    return this.then(gesture)
}

private fun Modifier.iconPointerInput(
    handler: () -> IconGestureHandler,
    coords: () -> LayoutCoordinates?,
): Modifier {
    return pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var up: PointerInputChange? = null
            val beforeLongPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                up = waitForUpOrCancellation()
                true
            }
            if (beforeLongPress == true) {
                // Released (tap) or taken by a parent (swipe) before the long press.
                if (up != null) handler().onTap()
                return@awaitEachGesture
            }
            handler().onLongPress()
            var dragging = false
            var travelled = Offset.Zero
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (dragging) handler().onDragEnd()
                    change.consume()
                    return@awaitEachGesture
                }
                travelled += change.positionChange()
                change.consume()
                val layout = coords() ?: continue
                if (!layout.isAttached) continue
                val root = layout.localToRoot(change.position)
                if (!dragging && travelled.getDistance() > slop) {
                    dragging = true
                    handler().onDragStart(down.position, root)
                }
                if (dragging) handler().onDrag(root)
            }
            if (dragging) handler().onDragCancel()
        }
    }
}
