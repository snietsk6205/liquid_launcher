package dev.liquid.launcher.feature.home

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.data.LauncherAction
import kotlin.math.abs

/** The launcher's own haptic ticks, following the Motion and haptics settings. */
object Haptics {
    @Volatile
    var config = MotionConfig()

    /** A light tick: passing a point, stepping through something. */
    fun tick(view: View?) {
        if (!config.haptics || view == null) return
        view.performHapticFeedback(if (config.strong) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.CLOCK_TICK)
    }

    /** A firmer one: a gesture was recognised, an item was picked up. */
    fun confirm(view: View?) {
        if (!config.haptics || !config.pickUpTick || view == null) return
        view.performHapticFeedback(if (config.strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CONTEXT_CLICK)
    }
}

/** Scales the launcher's own animations by the "speed" setting. */
object MotionScale {
    @Volatile
    var speed = 1f

    fun ms(base: Int): Int = (base / speed.coerceIn(0.25f, 4f)).toInt()

    fun stiffness(base: Float): Float = base * speed * speed
}

private enum class HomeTouch { Undecided, Vertical, TwoFingers, Other }

/**
 * Everything one or two fingers do on the home screen's empty space and over icons (before
 * an icon's long press): swipes up and down (the drawer follows the finger), swipes along a
 * side edge, pinches and two-finger swipes. Horizontal swipes are left to the pager.
 *
 * One finger is watched after the pages had their turn (an icon or the pager may take it);
 * once a second finger lands, both are taken before anything else sees them, so the pager or
 * an icon cannot act on a pinch.
 */
internal fun Modifier.homeGestures(gestures: GestureConfig, controller: HomeController, view: View?): Modifier =
    pointerInput(gestures, controller.editMode) {
        if (controller.editMode) return@pointerInput
        val slop = viewConfiguration.touchSlop
        val threshold = 48.dp.toPx()
        val edgeWidth = gestures.edgeWidthDp.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // Fed with whole pointer changes, so moves the system batched into one event still count.
            val tracker = VelocityTracker()
            tracker.addPointerInputChange(down)
            // A swipe from a side edge uses that edge's actions, when they do something.
            val edge = when {
                down.position.x < edgeWidth -> -1
                down.position.x > size.width - edgeWidth -> 1
                else -> 0
            }
            val up = when (edge) {
                -1 -> gestures.edgeLeftUp
                1 -> gestures.edgeRightUp
                else -> LauncherAction.None
            }.takeIf { it != LauncherAction.None } ?: gestures.swipeUp
            val downAction = when (edge) {
                -1 -> gestures.edgeLeftDown
                1 -> gestures.edgeRightDown
                else -> LauncherAction.None
            }.takeIf { it != LauncherAction.None } ?: gestures.swipeDown
            val follow = up == LauncherAction.OpenDrawer

            var mode = HomeTouch.Undecided
            var total = Offset.Zero
            var dragged = 0f
            var startDistance = 1f
            var startCentroid = Offset.Zero
            var zoom = 1f
            var pan = Offset.Zero

            while (true) {
                val pass = if (mode == HomeTouch.TwoFingers) PointerEventPass.Initial else PointerEventPass.Main
                val event = awaitPointerEvent(pass)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                // A second finger soon after the first: a two-finger gesture, even if the first already moved a little.
                val early = event.changes.first().uptimeMillis - down.uptimeMillis < 250
                if (pressed.size >= 2 && gestures.anyTwoFinger && (mode == HomeTouch.Undecided || (mode == HomeTouch.Vertical && early))) {
                    if (dragged != 0f) {
                        controller.dragDrawerBy(-dragged)
                        dragged = 0f
                        controller.settleDrawer(0f)
                    }
                    mode = HomeTouch.TwoFingers
                    startDistance = (pressed[0].position - pressed[1].position).getDistance().coerceAtLeast(1f)
                    startCentroid = (pressed[0].position + pressed[1].position) / 2f
                    controller.menu = null
                    event.changes.forEach { it.consume() }
                    continue
                }
                when (mode) {
                    HomeTouch.Undecided -> {
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        // An icon, the pager or the long-press menu took it.
                        if (change.isConsumed) {
                            mode = HomeTouch.Other
                            continue
                        }
                        total += change.positionChange()
                        tracker.addPointerInputChange(change)
                        if (total.getDistance() > slop) {
                            mode = if (abs(total.y) > abs(total.x)) HomeTouch.Vertical else HomeTouch.Other
                            if (mode == HomeTouch.Vertical) {
                                controller.menu = null
                                change.consume()
                            }
                        }
                    }
                    HomeTouch.Vertical -> {
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val dy = change.positionChange().y
                        total += change.positionChange()
                        tracker.addPointerInputChange(change)
                        change.consume()
                        if (follow && (total.y < 0 || controller.drawer.value > 0f)) {
                            val delta = -dy / controller.screenHeight
                            controller.dragDrawerBy(delta)
                            dragged += delta
                        }
                    }
                    HomeTouch.TwoFingers -> {
                        if (pressed.size >= 2) {
                            val a = pressed[0].position
                            val b = pressed[1].position
                            zoom = (a - b).getDistance() / startDistance
                            pan = (a + b) / 2f - startCentroid
                        }
                        event.changes.forEach { it.consume() }
                    }
                    HomeTouch.Other -> Unit
                }
            }

            when (mode) {
                HomeTouch.Vertical -> {
                    val velocity = tracker.calculateVelocity().y
                    DebugLog.event("Home", "swipe: " + total.y.toInt() + " px, " + velocity.toInt() + " px/s" + if (edge != 0) " (edge)" else "")
                    when {
                        follow && controller.drawer.value > 0f -> controller.settleDrawer(velocity)
                        total.y < -threshold || velocity < -1200f -> controller.run(up)
                        total.y > threshold || velocity > 1200f -> controller.run(downAction)
                    }
                }
                HomeTouch.TwoFingers -> {
                    val action = when {
                        zoom < 0.78f -> gestures.pinchIn
                        zoom > 1.28f -> gestures.pinchOut
                        abs(pan.y) > abs(pan.x) && pan.y < -threshold -> gestures.twoUp
                        abs(pan.y) > abs(pan.x) && pan.y > threshold -> gestures.twoDown
                        else -> LauncherAction.None
                    }
                    DebugLog.event("Home", "two fingers: zoom " + "%.2f".format(zoom) + ", moved " + pan.y.toInt() + " px -> " + action.encode())
                    if (action != LauncherAction.None) {
                        Haptics.confirm(view)
                        controller.run(action)
                    }
                }
                else -> Unit
            }
        }
    }
