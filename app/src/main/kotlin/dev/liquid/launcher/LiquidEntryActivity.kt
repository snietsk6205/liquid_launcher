package dev.liquid.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.liquid.launcher.core.common.DebugLog

/**
 * 0.12.0 (D171): the way in from other apps and from the keyboard. No window of its own: it hands the text to the home
 * screen's search and finishes.
 *
 * - "Liquid" in the text selection menu of any app (`ACTION_PROCESS_TEXT`): search with the selected text.
 * - "Search in Liquid" in the share sheet (`ACTION_SEND`, text): the same. Text that starts with "Ask Claude: " (what the
 *   WM Keyboard plugin shares for its Ask button) opens search ready to ask Claude with the rest.
 * - `dev.liquid.launcher.action.SEARCH` with the extras `query` (text) and `ask` (boolean), for Tasker and the like.
 *
 * Asking Claude only fills search in Ask mode: the question goes to the Claude app when Go is pressed, never by itself.
 */
class LiquidEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 0.16.0: pictures shared to "Set as Liquid wallpaper" (or "Set as" in a gallery) go to the wallpaper library. This
        // activity has no window and must finish at once, so the home screen copies them in; the read permission goes
        // with them (ClipData carries it for each picture).
        val pictures = pictures(intent)
        if (pictures.isNotEmpty()) {
            DebugLog.event("Wallpaper", "${pictures.size} picture(s) shared to the launcher")
            val clip = android.content.ClipData.newRawUri("pictures", pictures.first())
            pictures.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
            val open = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_SHARED_PICTURES)
                .also { it.clipData = clip }
            try {
                startActivity(Intent(open).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            } catch (e: SecurityException) {
                // The app that shared them gave no read permission the launcher can pass on (run 144 crashed here): the
                // home screen tries without it and says so plainly when it cannot read them. Never a crash.
                DebugLog.error("Wallpaper", "shared pictures came without a read permission to pass on: ${e.message}")
                runCatching { startActivity(open) }
            }
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            return
        }
        val (text, ask, from) = read(intent)
        if (!text.isNullOrBlank()) {
            DebugLog.event("Search", "text from $from (${text.length} characters)${if (ask) ", to ask Claude" else ""}")
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_OPEN, "search")
                    .putExtra(EXTRA_QUERY, text.trim().take(MAX_CHARS))
                    .putExtra(EXTRA_ASK, ask),
            )
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    /** The pictures of a share (one or several) or of "Set as"; none for text. */
    private fun pictures(intent: Intent?): List<android.net.Uri> {
        if (intent == null) return emptyList()
        // 0.16.0 (D207): videos too (they become video wallpapers).
        val picture = intent.type?.let { it.startsWith("image/") || it.startsWith("video/") } == true
        return when (intent.action) {
            Intent.ACTION_SEND -> if (picture) listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)) else emptyList()
            Intent.ACTION_SEND_MULTIPLE -> if (picture) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java).orEmpty() else emptyList()
            Intent.ACTION_ATTACH_DATA -> listOfNotNull(intent.data)
            else -> emptyList()
        }
    }

    private fun read(intent: Intent?): Triple<String?, Boolean, String> = when (intent?.action) {
        Intent.ACTION_PROCESS_TEXT -> Triple(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString(), false, "the text selection menu")
        Intent.ACTION_SEND -> {
            val shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            if (shared != null && shared.startsWith(ASK_PREFIX)) Triple(shared.removePrefix(ASK_PREFIX), true, "a share")
            else Triple(shared, false, "a share")
        }
        ACTION_SEARCH -> Triple(intent.getStringExtra(EXTRA_QUERY), intent.getBooleanExtra(EXTRA_ASK, false), "another app")
        else -> Triple(null, false, "")
    }

    companion object {
        const val ACTION_SEARCH = "dev.liquid.launcher.action.SEARCH"
        const val EXTRA_QUERY = "query"
        const val EXTRA_ASK = "ask"
        const val ASK_PREFIX = "Ask Claude: "
        private const val MAX_CHARS = 4000
    }
}
