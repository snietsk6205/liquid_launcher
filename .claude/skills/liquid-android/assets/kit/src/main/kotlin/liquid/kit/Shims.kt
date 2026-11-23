package liquid.kit

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The few things the kit takes from the app around it. Liquid Launcher's own versions read its settings (Motion and
 * haptics) and name what is on screen; wire these to your app's settings the same way (see AppSettings).
 */

/** Every animation's speed: durations divide by it, spring stiffness multiplies by its square (the "Speed" setting). */
object MotionScale {
    @Volatile
    var speed = 1f

    fun ms(base: Int): Int = (base / speed.coerceIn(0.25f, 4f)).toInt()

    fun stiffness(base: Float): Float = base * speed * speed
}

/** Haptic ticks behind one switch: [tick] when something passes a point, [confirm] when a gesture is recognised. */
object Haptics {
    @Volatile
    var enabled = true

    @Volatile
    var strong = false

    fun tick(view: View?) {
        if (enabled) view?.performHapticFeedback(if (strong) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.CLOCK_TICK)
    }

    fun confirm(view: View?) {
        if (enabled) view?.performHapticFeedback(if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CONTEXT_CLICK)
    }
}

/** What is on screen ("drawer", "search", "speed check: …"), for the frame meter's lines. Set it as screens open and close. */
object FrameCost {
    @Volatile
    var label = ""

    /** Fewer frames than this are not worth a line. */
    @Volatile
    var minFrames = 120
}

/** The icons the settings kit draws (its back arrow and the breadcrumbs' home); add your own beside them. */
object LauncherIcons {
    val ArrowBack = R.drawable.ic_arrow_back
    val Home = R.drawable.ic_home
    val ChevronRight = R.drawable.ic_chevron_right
    val Check = R.drawable.ic_check
    val Tune = R.drawable.ic_tune
}

/** A vector drawable as an icon, tinted, at a size. */
@Composable
fun LauncherIcon(
    icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    Icon(painter = painterResource(icon), contentDescription = contentDescription, modifier = modifier.size(size), tint = tint)
}
