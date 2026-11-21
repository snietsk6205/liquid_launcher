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
}
