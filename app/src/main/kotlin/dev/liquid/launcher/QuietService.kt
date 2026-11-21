package dev.liquid.launcher

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.common.NoticeKind
import dev.liquid.launcher.core.common.Notices
import dev.liquid.launcher.core.data.LauncherData
import dev.liquid.launcher.core.data.QuietRules
import dev.liquid.launcher.core.data.StudyStatus
import dev.liquid.launcher.core.settings.LauncherSettings

/**
 * C5, notification triage (0.9.16): while a study block runs, a notification that is not let through
 * ([QuietRules]) is snoozed until the block ends. Android gives it back then, so nothing is lost, and the home
 * screen says how many waited and from which apps. Only with Settings → Study companion → "Quiet during blocks"
 * and notification access; a block marked done (or missed) lets everything through again.
 */
class QuietService : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())

    /** The apps whose notifications waited during the block ending at [heldUntil] (one entry per notification). */
    private val held = ArrayList<String>()
    private var heldBlock = ""
    private var heldUntil = 0L

    override fun onListenerConnected() {
        DebugLog.event("Quiet", "notification access on")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            triage(sbn)
        } catch (e: Throwable) {
            DebugLog.error("Quiet", "triage failed: $e")
        }
    }

    private fun triage(sbn: StatusBarNotification) {
        val data = LauncherData.get(this)
        val settings = data.settings
        if (!settings.get(LauncherSettings.StudyQuiet) || !data.study.enabled) return
        val now = System.currentTimeMillis()
        val block = data.study.day.value.current(now) ?: return
        if (block.status != StudyStatus.Open) return
        val n = sbn.notification ?: return
        // A summary of a group comes with its children; holding it alone would leave an empty group.
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val extras = n.extras
        val app = data.apps.apps.value.firstOrNull { it.ref.pkg == sbn.packageName }
        val tagNames = app?.tags.orEmpty().mapNotNull { id -> data.organisation.value.value.tags.firstOrNull { it.id == id }?.name }.toSet()
        val people = runCatching {
            @Suppress("DEPRECATION")
            extras.getParcelableArrayList<android.app.Person>(Notification.EXTRA_PEOPLE_LIST)?.mapNotNull { it.name?.toString() }
        }.getOrNull().orEmpty()
        val incoming = QuietRules.Incoming(
            pkg = sbn.packageName,
            category = n.category,
            ongoing = sbn.isOngoing || (n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty(),
            people = people,
            tags = tagNames,
        )
        val through = QuietRules.letThrough(
            incoming,
            QuietRules.list(settings.get(LauncherSettings.StudyQuietPeople)),
            QuietRules.list(settings.get(LauncherSettings.StudyQuietEmail)),
            QuietRules.list(settings.get(LauncherSettings.StudyQuietTags)),
            packageName,
        )
        if (through) return
        val wait = block.end - now
        // Too close to the end to be worth holding.
        if (wait < 30_000) return
        snoozeNotification(sbn.key, wait + 15_000)
        val label = app?.label ?: sbn.packageName.substringAfterLast('.')
        DebugLog.event("Quiet", "held a notification of $label until the end of ${block.title.subject}")
        if (heldUntil != block.end) {
            // A new block: start counting, and sum up when it ends.
            held.clear()
            heldUntil = block.end
            heldBlock = block.title.subject
            val until = block.end
            handler.postDelayed({ sumUp(until) }, wait + 20_000)
        }
        held += label
    }

    /** After the block: how many waited and from which apps, on the home screen. */
    private fun sumUp(until: Long) {
        if (heldUntil != until || held.isEmpty()) return
        val count = held.size
        Notices.post(
            NoticeKind.Study,
            "$heldBlock is over · $count notification${if (count == 1) "" else "s"} waited",
            QuietRules.summary(held) + " · back in your notifications",
        )
        DebugLog.event("Quiet", "after $heldBlock: $count notification(s) given back")
        held.clear()
        heldUntil = 0L
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
