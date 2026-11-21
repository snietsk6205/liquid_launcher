package dev.liquid.launcher.core.data

/** One block's new times in a shifted evening. */
data class ShiftMove(val event: StudyEvent, val begin: Long, val end: Long) {
    /** Minutes cut from the block's length (0 when it only moved). */
    val shortenedMinutes: Int get() = (((event.end - event.begin) - (end - begin)) / 60_000L).toInt()
}

/** "Shift tonight" (A3) worked out: every block that moves, and what it costs. */
data class ShiftPlan(
    val deltaMinutes: Int,
    val moves: List<ShiftMove>,
    /** The block cut short so the evening still ends by the latest end, if one had to be. */
    val shortened: StudyEvent? = null,
    val shortenedMinutes: Int = 0,
    /** Even with that block at its shortest the evening ends after the latest end. */
    val endsLate: Boolean = false,
) {
    val lastEnd: Long get() = moves.maxOfOrNull { it.end } ?: 0L
}

/**
 * Works out "shift tonight" without touching the calendar, so it is tested without a phone.
 *
 * Blocks that move: today's blocks that are still open, have no tick and no focus time, and
 * are not over at the moment of arrival. A block that began while Med was still on the way
 * counts as not started. Blocks that ended before arrival stay where they are (they are
 * missed, not late).
 */
object StudyShift {
    /** A shortened block keeps at least this long. */
    const val MIN_BLOCK_MINUTES = 20

    /** Subjects that give way first when the evening is too long. */
    private val lowPriority = listOf("catch-up", "catch up", "rattrapage", "sunday check", "checkpoint")

    fun pending(blocks: List<StudyEvent>, homeAt: Long): List<StudyEvent> = blocks.filter { block ->
        block.status == StudyStatus.Open && block.end > homeAt && block.note.focusMinutes == 0 && block.tasks.none { it.done }
    }.sortedBy { it.begin }

    /**
     * Minutes to move the evening by when home at [homeAt]: the first block then starts
     * [leadMinutes] after arrival, rounded up to 5 minutes. 0 when home early enough.
     */
    fun deltaFor(blocks: List<StudyEvent>, homeAt: Long, leadMinutes: Int = 20): Int {
        val first = pending(blocks, homeAt).firstOrNull() ?: return 0
        val late = homeAt + leadMinutes * 60_000L - first.begin
        if (late <= 0) return 0
        val minutes = ((late + 59_999) / 60_000).toInt()
        return (minutes + 4) / 5 * 5
    }

    /**
     * The plan for moving the evening by [deltaMinutes]. With [shorten] and an evening that would end
     * after [latestEnd], the lowest-priority block (catch-up, checkpoint, else the last one) is cut by
     * the overflow, down to [MIN_BLOCK_MINUTES], and the blocks after it move up by the same amount.
     */
    fun plan(blocks: List<StudyEvent>, homeAt: Long, deltaMinutes: Int, latestEnd: Long, shorten: Boolean): ShiftPlan {
        val pending = pending(blocks, homeAt)
        val delta = deltaMinutes * 60_000L
        var moves = pending.map { ShiftMove(it, it.begin + delta, it.end + delta) }
        val overflow = (moves.maxOfOrNull { it.end } ?: 0L) - latestEnd
        if (moves.isEmpty() || overflow <= 0 || !shorten) return ShiftPlan(deltaMinutes, moves, endsLate = overflow > 0 && moves.isNotEmpty())
        val victim = pending.firstOrNull { block -> lowPriority.any { it in block.title.subject.lowercase() } } ?: pending.last()
        val length = victim.end - victim.begin
        val cut = minOf(overflow, (length - MIN_BLOCK_MINUTES * 60_000L).coerceAtLeast(0L))
        // Whole minutes, so the calendar keeps round times.
        val cutMinutes = ((cut + 59_999) / 60_000).toInt().coerceAtMost((length / 60_000L).toInt())
        val cutMs = cutMinutes * 60_000L
        moves = moves.map { move ->
            when {
                move.event.key == victim.key -> move.copy(end = move.end - cutMs)
                move.event.begin >= victim.end -> move.copy(begin = move.begin - cutMs, end = move.end - cutMs)
                else -> move
            }
        }
        return ShiftPlan(
            deltaMinutes, moves,
            shortened = victim.takeIf { cutMinutes > 0 }, shortenedMinutes = cutMinutes,
            endsLate = (moves.maxOf { it.end }) > latestEnd,
        )
    }
}
