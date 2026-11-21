package dev.liquid.launcher.feature.home

import android.graphics.Insets
import android.os.CancellationSignal
import android.os.SystemClock
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsAnimationControlListener
import android.view.WindowInsetsAnimationController
import android.view.animation.LinearInterpolator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/**
 * 0.11.3 (Med chose "Keyboard slides with the pill", D169): the launcher moves the keyboard itself, frame by frame, in
 * step with search opening, through the platform's `WindowInsetsController.controlWindowInsetsAnimation`. Asking for
 * the control also asks the keyboard to come up for the focused field (which took focus without showing it); the
 * keyboard can be moved once its window is drawn ([Phase.Ready]). The system or the keyboard can refuse, or take the
 * control back ([Phase.Cancelled]): search then shows the keyboard the usual way and the field rides on it.
 */
internal class KeyboardDriver(private val view: View) {
    enum class Phase { Asking, Ready, Finished, Cancelled }

    /** Read by the drive's coroutine (snapshot state, so it can wait for a change). */
    var phase by mutableStateOf(Phase.Asking)
        private set

    val askedAt: Long = SystemClock.uptimeMillis()
    var readyAt = 0L
        private set

    /** The keyboard's height fully up and fully down, px from the bottom of the screen, as the system gave them. */
    var shownPx = 0
        private set
    private var hiddenPx = 0

    /** Why the control was lost, for the log. */
    var cancelReason = ""
        private set

    /** The last fraction set, for the log. */
    var fraction = 0f
        private set

    private var control: WindowInsetsAnimationController? = null
    private val signal = CancellationSignal()

    private val listener = object : WindowInsetsAnimationControlListener {
        override fun onReady(controller: WindowInsetsAnimationController, types: Int) {
            if (types and WindowInsets.Type.ime() == 0) {
                // Granted for something else only: give it back.
                controller.finish(false)
                cancelReason = "the system gave control of other insets only"
                phase = Phase.Cancelled
                return
            }
            control = controller
            shownPx = controller.shownStateInsets.bottom
            hiddenPx = controller.hiddenStateInsets.bottom
            readyAt = SystemClock.uptimeMillis()
            // Start from where it is (hidden): nothing moves until the drive says so.
            controller.setInsetsAndAlpha(controller.currentInsets, 1f, 0f)
            phase = Phase.Ready
        }

        override fun onFinished(controller: WindowInsetsAnimationController) {
            control = null
            if (phase != Phase.Cancelled) phase = Phase.Finished
        }

        override fun onCancelled(controller: WindowInsetsAnimationController?) {
            control = null
            if (phase == Phase.Finished) return
            if (cancelReason.isEmpty()) {
                cancelReason = if (controller == null) "the keyboard or the system refused to let the launcher move it"
                else "the system took the keyboard back while the launcher moved it"
            }
            phase = Phase.Cancelled
        }
    }

    /** Asks for the control (and so for the keyboard). False when it could not even be asked. */
    fun ask(): Boolean {
        val insetsController = view.windowInsetsController
        if (insetsController == null) {
            cancelReason = "the window has no insets controller"
            phase = Phase.Cancelled
            return false
        }
        return try {
            insetsController.controlWindowInsetsAnimation(WindowInsets.Type.ime(), -1L, LinearInterpolator(), signal, listener)
            true
        } catch (e: Exception) {
            cancelReason = "asking failed: $e"
            phase = Phase.Cancelled
            false
        }
    }

    /** Moves the keyboard to [fraction] of the way up (0: hidden, 1: fully up). */
    fun move(fraction: Float) {
        val c = control ?: return
        if (c.isFinished || c.isCancelled) return
        val f = fraction.coerceIn(0f, 1f)
        this.fraction = f
        val bottom = hiddenPx + ((shownPx - hiddenPx) * f).roundToInt()
        c.setInsetsAndAlpha(Insets.of(0, 0, 0, bottom), 1f, f)
    }

    /** Hands the keyboard back to the system, fully up ([shown]) or put away. */
    fun finish(shown: Boolean) {
        val c = control
        if (c != null && !c.isFinished && !c.isCancelled) {
            c.finish(shown)
        } else if (phase == Phase.Asking) {
            cancelReason = "search closed before the keyboard was ready"
            if (!signal.isCanceled) signal.cancel()
        }
    }

    /** Stops asking (the keyboard was not ready in time). */
    fun giveUp(reason: String) {
        if (phase != Phase.Asking) return
        cancelReason = reason
        if (!signal.isCanceled) signal.cancel()
        phase = Phase.Cancelled
    }
}
