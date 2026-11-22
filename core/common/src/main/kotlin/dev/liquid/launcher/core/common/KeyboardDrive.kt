package dev.liquid.launcher.core.common

/**
 * 0.11.3 (Med chose "Keyboard slides with the pill"): how far up the launcher slides the keyboard, 0..1, while search
 * opens. In step with search's own progress; a keyboard that became ready to be moved after search had started
 * (its window is drawn first: 200 to 724 ms on Med's phone) catches up over [CATCH_UP_MS], eased out, instead of
 * jumping to where search already is. It never goes ahead of search, so the field, which rides on it, is never
 * left behind.
 */
object KeyboardDrive {
    const val CATCH_UP_MS = 180L

    /** Asking for the keyboard longer than this without it becoming ready: it is shown the usual way instead. */
    const val ASK_LIMIT_MS = 1_500L

    /** [progress]: how far search has opened (a spring can pass 1); [sinceReadyMs]: since the keyboard became movable. */
    fun fraction(progress: Float, sinceReadyMs: Long): Float {
        val p = progress.coerceIn(0f, 1f)
        val t = (sinceReadyMs.toFloat() / CATCH_UP_MS).coerceIn(0f, 1f)
        val catchUp = 1f - (1f - t) * (1f - t) * (1f - t)
        return minOf(p, catchUp)
    }

    /** The drive can hand the keyboard over, fully up: search is open and still, and the keyboard has caught up. */
    fun done(progress: Float, open: Boolean, moving: Boolean, sinceReadyMs: Long): Boolean =
        open && !moving && progress >= 0.999f && sinceReadyMs >= CATCH_UP_MS

    /** 0.13.1: how much taller than expected a keyboard may say it is before its height is doubted, in dp. */
    const val TALLER_DP = 48f

    /**
     * 0.13.1 (Med: "after I change the wallpaper, the pill goes higher than intended the first time"): whether the height
     * the system gives for the keyboard fully up ([shownPx]) is believed over the height it was expected at ([expectedPx]:
     * its picture's, else the lowest recent opening; 0: none). A keyboard that has just rebuilt itself (WM Keyboard after
     * the wallpaper changed its colours, or back from an app where it showed its suggestion strip) is laid out taller at
     * first (1188 px instead of 933 px on Med's phone, 85 dp) and comes down to its real height a moment later; the
     * pill then keeps to the expected height instead of rising to the first one. Shorter is always believed.
     */
    fun believes(shownPx: Float, expectedPx: Float, tolerancePx: Float): Boolean =
        expectedPx <= 0f || shownPx <= expectedPx + tolerancePx

    /**
     * The share of the keyboard's own height ([shownPx]) to show so that its top stands at [risePx] x [progress], the
     * pill's line (0..1). Equal heights at full progress give 1: the keyboard fully up.
     */
    fun share(risePx: Float, progress: Float, shownPx: Float): Float =
        if (shownPx <= 0f) 0f else (risePx * progress.coerceIn(0f, 1f) / shownPx).coerceIn(0f, 1f)
}
