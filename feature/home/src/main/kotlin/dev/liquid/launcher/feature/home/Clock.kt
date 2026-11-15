package dev.liquid.launcher.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

/** Current time, updated at the start of each minute. */
@Composable
fun rememberMinuteTick(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000 - value % 60_000 + 25)
        }
    }
    return now
}
