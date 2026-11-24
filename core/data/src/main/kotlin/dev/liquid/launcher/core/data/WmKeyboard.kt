package dev.liquid.launcher.core.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import dev.liquid.launcher.core.common.DebugLog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 0.12.0 (D172): Med's keyboard, WM Keyboard (github.com/wasi-master/wmkeyboard), through its automation intents (its
 * docs: Reference › Automation). Every command is a broadcast to its package with an action starting with
 * `com.wasimaster.wmkeyboard.action.`; the keyboard answers with result -1 and what it did, or 0 and why not (for
 * example "not allowed: turn on … under Settings › Privacy › Allowed actions"). Nothing works until "Let other apps
 * control the keyboard" is on in WM Keyboard › Privacy.
 */
object WmKeyboard {
    const val PACKAGE = "com.wasimaster.wmkeyboard"
    private const val ACTION = "$PACKAGE.action."
    private const val NO_ANSWER = 7

    /** The keyboard's answer: [ok] with what it did in [text], or not, with why in [text] (null: it did not answer). */
    data class Answer(val ok: Boolean, val text: String?)

    /** One of the layouts the keyboard has on: [inUse] is the one on screen ("*" in its list). */
    data class Layout(val id: String, val language: String, val name: String, val inUse: Boolean)

    fun installed(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    /** WM Keyboard is the keyboard in use. */
    fun inUse(context: Context): Boolean =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith("$PACKAGE/") == true

    /** Sends [command] (SET_LAYOUT, LIST_LAYOUTS…) with string [extras] and waits up to [timeoutMs] for the answer. */
    suspend fun send(context: Context, command: String, extras: Map<String, String> = emptyMap(), timeoutMs: Long = 2_000): Answer {
        val intent = Intent(ACTION + command).setPackage(PACKAGE)
        for ((k, v) in extras) intent.putExtra(k, v)
        val answer = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context, i: Intent) {
                        val code = resultCode
                        val data = resultData
                        if (cont.isActive) cont.resume(
                            when (code) {
                                -1 -> Answer(true, data)
                                NO_ANSWER -> Answer(false, null)
                                else -> Answer(false, data ?: "refused")
                            },
                        )
                    }
                }
                try {
                    context.sendOrderedBroadcast(intent, null, receiver, Handler(Looper.getMainLooper()), NO_ANSWER, null, null)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(Answer(false, "could not ask: $e"))
                }
            }
        }
        return answer ?: Answer(false, null)
    }

    /** The layouts from a LIST_LAYOUTS answer: one per line, id, language and name separated by tabs, "*" before the one in use. */
    fun parseLayouts(text: String?): List<Layout> = text.orEmpty().lines().mapNotNull { raw ->
        val line = raw.trimEnd()
        if (line.isBlank()) return@mapNotNull null
        val inUse = line.trimStart().startsWith("*")
        val body = line.trimStart().removePrefix("*").trim()
        val parts = body.split('\t').map { it.trim() }.filter { it.isNotEmpty() }
            .let { if (it.size >= 3) it else body.split(Regex("\\s{2,}")).map { p -> p.trim() }.filter { p -> p.isNotEmpty() } }
        if (parts.isEmpty()) return@mapNotNull null
        Layout(parts[0], parts.getOrElse(1) { "" }, parts.drop(2).joinToString(" ").ifEmpty { parts[0] }, inUse)
    }

    /** Words for the log and Settings: an answer as a sentence. */
    fun describe(answer: Answer): String = when {
        answer.ok -> "done" + (answer.text?.let { " ($it)" } ?: "")
        answer.text == null -> "no answer (is WM Keyboard installed and up to date?)"
        else -> answer.text
    }
}
