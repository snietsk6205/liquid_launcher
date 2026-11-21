package dev.liquid.launcher.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What a glass notice is about. Each kind has its own switch in Settings → Glass notices. */
enum class NoticeKind(
    val id: String,
    /** How long a notice of this kind may wait for the launcher to come to the front before it is stale. */
    val patienceMs: Long,
) {
    Layout("layout", 4_000),
    Wallpaper("wallpaper", 4_000),
    TagFilter("tags", 3_000),
    Redirect("redirect", 20_000),
    Update("update", 12 * 60 * 60_000L),
    Study("study", 10 * 60_000L),
    Task("task", 10 * 60_000L),
    Info("info", 15_000),
}

/**
 * A short message shown as a glass label over the home screen. [action] is a launcher action
 * in its text form (see LauncherAction), run when the notice is tapped.
 */
data class Notice(
    val kind: NoticeKind,
    val text: String,
    val detail: String? = null,
    val action: String? = null,
)

/**
 * Where any part of the launcher announces something: a layout switch, a new wallpaper, a tag
 * filter, the redirect pausing, an update, a study block starting. The home screen shows them
 * one after another; this object only queues them, so the data layer, the redirect service and
 * the settings app can post without knowing about the home screen.
 */
object Notices {
    private class Waiting(val notice: Notice, val postedAt: Long)

    private val lock = Any()
    private val waiting = ArrayDeque<Waiting>()
    private val _tick = MutableStateFlow(0)

    /** Changes whenever a notice is posted. */
    val tick: StateFlow<Int> = _tick

    /** Monotonic clock; replaced in tests. */
    var clock: () -> Long = { android.os.SystemClock.uptimeMillis() }

    /** Queues [notice]. A waiting notice of the same kind is replaced: only the newest matters. */
    fun post(notice: Notice) {
        synchronized(lock) {
            waiting.removeAll { it.notice.kind == notice.kind }
            waiting.addLast(Waiting(notice, clock()))
            while (waiting.size > MAX_WAITING) waiting.removeFirst()
        }
        _tick.value++
    }

    fun post(kind: NoticeKind, text: String, detail: String? = null, action: String? = null) = post(Notice(kind, text, detail, action))

    /** The oldest notice that is still fresh, removed from the queue; null when there is none. */
    fun next(): Notice? = synchronized(lock) {
        val now = clock()
        while (waiting.isNotEmpty()) {
            val first = waiting.removeFirst()
            if (now - first.postedAt <= first.notice.kind.patienceMs) return@synchronized first.notice
        }
        null
    }

    /** Puts a notice back at the front (it could not be shown yet). */
    fun defer(notice: Notice, postedAgoMs: Long = 0) = synchronized(lock) {
        waiting.addFirst(Waiting(notice, clock() - postedAgoMs))
    }

    val hasWaiting: Boolean get() = synchronized(lock) { waiting.isNotEmpty() }

    private const val MAX_WAITING = 6
}
