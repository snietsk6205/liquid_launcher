package dev.liquid.launcher.feature.home

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * Other apps' widgets in the launcher's look (P7, 0.9.17), inside their own views: solid card and row fills give
 * way to the glass plate or the wallpaper, and text takes the launcher's text colour (lighter text stays lighter).
 * Run again after every update, since the app re-applies its own colours each time.
 *
 * What cannot be restyled, and stays as the app draws it: pictures (photos, album art, maps, a widget that sends
 * its whole content as one image, as some clocks and weather widgets do), colours inside images and icons, and
 * fills drawn by a custom view the widget class draws itself.
 */
internal object WidgetRestyle {
    /** Restyles [root]'s views for text in [text] (ARGB). */
    fun apply(root: View, text: Int) {
        walk(root, text, 0)
    }

    private fun walk(v: View, text: Int, depth: Int) {
        val bg = v.background
        if (bg != null && solid(bg)) {
            // Keep a press ripple's feedback: only its solid content goes.
            if (bg is RippleDrawable) {
                if (bg.numberOfLayers > 0 && solid(bg.getDrawable(0))) bg.setDrawable(0, ColorDrawable(Color.TRANSPARENT))
            } else {
                v.background = null
            }
        }
        if (v is TextView) {
            val now = v.currentTextColor
            val wanted = shade(now, text)
            if (now != wanted) v.setTextColor(wanted)
            val hint = v.currentHintTextColor
            val hintWanted = shade(hint, text, secondary = true)
            if (hint != hintWanted) v.setHintTextColor(hintWanted)
        }
        if (v is ViewGroup && depth < 40) {
            for (i in 0 until v.childCount) walk(v.getChildAt(i), text, depth + 1)
        }
    }

    /** True for a plain fill: a colour, a filled shape, or layers of them (cards, list rows). Pictures are kept. */
    private fun solid(d: Drawable?): Boolean = when (d) {
        null -> false
        is ColorDrawable -> Color.alpha(d.color) > 0
        is GradientDrawable -> d.color?.defaultColor?.let { Color.alpha(it) > 0 } ?: (d.colors?.isNotEmpty() == true)
        is InsetDrawable -> solid(d.drawable)
        is RippleDrawable -> d.numberOfLayers > 0 && solid(d.getDrawable(0))
        is LayerDrawable -> d.numberOfLayers > 0 && (0 until d.numberOfLayers).all { solid(d.getDrawable(it)) }
        else -> false
    }

    /**
     * The launcher's [text] colour for a text that was [original]: as opaque as it was, and a muted (grey or
     * see-through) text stays muted, so titles and details keep their order.
     */
    private fun shade(original: Int, text: Int, secondary: Boolean = false): Int {
        val alpha = Color.alpha(original)
        val hsv = FloatArray(3)
        Color.colorToHSV(original, hsv)
        val muted = secondary || alpha < 200 || (hsv[1] < 0.15f && hsv[2] in 0.3f..0.75f)
        val a = if (muted) (minOf(alpha, 255) * 0.72f).toInt().coerceIn(90, 200) else alpha
        return Color.argb(a, Color.red(text), Color.green(text), Color.blue(text))
    }
}

/**
 * The battery's level (0..100) and whether it charges, while [on]; null when off or unknown. Read from the
 * system's sticky battery broadcast (A4, 0.9.18).
 */
@androidx.compose.runtime.Composable
internal fun rememberBattery(on: Boolean): Pair<Int, Boolean>? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Pair<Int, Boolean>?>(null) }
    androidx.compose.runtime.DisposableEffect(on) {
        if (!on) {
            state.value = null
            return@DisposableEffect onDispose { }
        }
        fun read(intent: android.content.Intent?) {
            if (intent == null) return
            val raw = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
            state.value = if (raw < 0) null else (raw * 100 / scale) to charging
        }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, intent: android.content.Intent) = read(intent)
        }
        read(context.registerReceiver(receiver, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return state.value
}
