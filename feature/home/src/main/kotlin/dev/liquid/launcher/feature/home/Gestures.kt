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

    /** True when a vertical swipe on the item does something of its own ([up] or down). */
    fun hasSwipe(up: Boolean): Boolean = false
    fun onSwipe(up: Boolean) = Unit
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

private enum class Early { Tap, Gone, SwipeUp, SwipeDown }

private fun Modifier.iconPointerInput(
    handler: () -> IconGestureHandler,
    coords: () -> LayoutCoordinates?,
): Modifier {
    return pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // Before the long press: a tap, a swipe of the item's own, or a swipe for a parent.
            val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var moved = Offset.Zero
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull Early.Gone
                    if (!change.pressed) {
                        return@withTimeoutOrNull if (change.isConsumed) Early.Gone else Early.Tap
                    }
                    if (change.isConsumed) return@withTimeoutOrNull Early.Gone
                    moved += change.positionChange()
                    if (moved.getDistance() > slop) {
                        val vertical = kotlin.math.abs(moved.y) > kotlin.math.abs(moved.x)
                        val upward = moved.y < 0
                        if (vertical && handler().hasSwipe(upward)) {
                            change.consume()
                            return@withTimeoutOrNull if (upward) Early.SwipeUp else Early.SwipeDown
                        }
                        // Not ours: leave the swipe to the pager, the drawer or search.
                        return@withTimeoutOrNull Early.Gone
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                Early.Gone
            }
            when (early) {
                Early.Tap -> {
                    handler().onTap()
                    return@awaitEachGesture
                }
                Early.Gone -> return@awaitEachGesture
                Early.SwipeUp, Early.SwipeDown -> {
                    // Take the rest of the gesture, then run the action on release.
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                    }
                    handler().onSwipe(early == Early.SwipeUp)
                    return@awaitEachGesture
                }
                null -> Unit
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
