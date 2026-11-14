package dev.liquid.launcher.core.common

/**
 * Timing lines for one wallpaper change, so the debug log shows where the time goes:
 * picked → wallpaper ready → palette ready → on screen → icons redrawn → system wallpaper set
 * → system colours. Each line has the milliseconds since the change started.
 */
object ColourTiming {
    @Volatile
    private var start = 0L

    @Volatile
    private var what = ""

    private fun now() = System.nanoTime() / 1_000_000

    /** A new change starts (a wallpaper was picked, a layout switch began). */
    fun begin(change: String) {
        start = now()
        what = change
        DebugLog.event("Timing", "$change: started")
    }

    /** A step of the current change; ignored when no change started in the last 30 s. */
    fun mark(step: String) {
        if (start == 0L) return
        val elapsed = now() - start
        if (elapsed > 30_000) return
        DebugLog.event("Timing", "$what: $step +$elapsed ms")
    }
}
