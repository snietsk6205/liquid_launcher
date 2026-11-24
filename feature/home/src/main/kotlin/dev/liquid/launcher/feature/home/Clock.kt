package dev.liquid.launcher.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.liquid.launcher.core.common.WallClock

/**
 * Current time, updated at the start of each minute. Read from the home screen's one time source ([WallClock],
 * D168), which the app refreshes from the wall clock at screen on, unlock, resume and on the system's minute tick:
 * a delay of its own would count uptime and show an old minute for a while after the screen was off.
 */
@Composable
fun rememberMinuteTick(): Long {
    val tick by WallClock.tick.collectAsState()
    return tick.millis
}

/** Changes whenever [WallClock] publishes (each minute, and when the time or zone is set): a key for faster ticks. */
@Composable
internal fun rememberClockSerial(): Long {
    val tick by WallClock.tick.collectAsState()
    return tick.serial
}
