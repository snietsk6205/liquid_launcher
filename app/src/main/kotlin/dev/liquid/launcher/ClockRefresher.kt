package dev.liquid.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.common.WallClock

/**
 * Keeps the home screen's [WallClock] current (0.11.3, D168): the system's minute tick, the time or the time zone
 * being set, the screen turning on and the unlock each read the wall clock at once, and so do the launcher starting
 * and resuming. A delay to the next minute is only the backup: it counts uptime, which stops while the phone sleeps.
 */
internal class ClockRefresher(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val power = context.getSystemService(PowerManager::class.java)
    private var registered = false
    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_TIME_TICK -> refresh("the minute tick")
                Intent.ACTION_TIME_CHANGED -> refresh("the time being set", force = true)
                Intent.ACTION_TIMEZONE_CHANGED -> refresh("the time zone changing", force = true)
                Intent.ACTION_SCREEN_ON -> refresh("the screen turning on")
                Intent.ACTION_USER_PRESENT -> refresh("the unlock")
            }
        }
    }

    private val backup = Runnable {
        refresh("the backup delay")
    }

    /** From the home screen's creation to its end: the broadcasts. */
    fun register() {
        if (registered) return
        try {
            context.registerReceiver(
                receiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_TIME_TICK)
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                },
                null, handler, Context.RECEIVER_NOT_EXPORTED,
            )
            registered = true
        } catch (e: Exception) {
            DebugLog.error("Clock", "could not listen to the time broadcasts: $e (the backup delay keeps the clocks going)")
        }
    }

    fun unregister() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }

    /** Before the first frame after the screen comes back: the clocks never draw an old minute first. */
    fun onStart() {
        started = true
        refresh("the launcher starting")
    }

    fun onResume() = refresh("the launcher resuming")

    fun onStop() {
        started = false
        handler.removeCallbacks(backup)
    }

    private fun refresh(reason: String, force: Boolean = false) {
        WallClock.refresh(reason, force = force, visible = power?.isInteractive != false)
        // Each refresh aims the backup at the start of the next minute again.
        handler.removeCallbacks(backup)
        if (started) handler.postDelayed(backup, WallClock.untilNextMinute(System.currentTimeMillis()))
    }
}
