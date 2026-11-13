package dev.liquid.launcher

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.data.LaunchClock
import dev.liquid.launcher.core.designsystem.LauncherTheme
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.feature.home.HomeCallbacks
import dev.liquid.launcher.feature.home.HomeReturnState
import dev.liquid.launcher.feature.home.HomeScreen
import dev.liquid.launcher.feature.redirect.RedirectStatus
import dev.liquid.launcher.feature.settings.SettingsActivity

/**
 * The home screen. It is an ordinary activity, not the default launcher: the accessibility
 * service brings it to the front whenever the system launcher's home screen would show.
 */
class MainActivity : ComponentActivity() {

    private val returnState = HomeReturnState()
    private var firstStart = true
    private var resumed = false
    private var pausedAt = 0L
    private var imageCallback: ((android.net.Uri?) -> Unit)? = null
    private val pickMedia = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        imageCallback?.invoke(uri)
        imageCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        // Home is the end of the back stack: back does nothing here unless an overlay is open
        // (the home screen registers its own handler for that, which takes precedence).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        val app = application as LauncherApp
        val callbacks = HomeCallbacks(
            openSettings = { startActivity(SettingsActivity.intent(this)) },
            openSetup = { startActivity(SettingsActivity.intent(this, SettingsActivity.SCREEN_SETUP)) },
            openSection = { startActivity(SettingsActivity.intent(this, SettingsActivity.SECTION_PREFIX + it)) },
            pickImage = { callback ->
                imageCallback = callback
                try {
                    pickMedia.launch(
                        androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                } catch (e: Exception) {
                    DebugLog.error("App", "photo picker failed: $e")
                    imageCallback = null
                    callback(null)
                }
            },
        )
        setContent {
            LauncherTheme {
                HomeScreen(
                    data = app.data,
                    wallpaper = app.wallpaper,
                    returnState = returnState,
                    callbacks = callbacks,
                )
            }
        }

        intent?.getStringExtra(EXTRA_OPEN)?.let { returnState.request = it }

        val lastCrash = java.io.File(filesDir, "last-crash.txt")
        if (lastCrash.exists()) {
            DebugLog.error("App", "previous run crashed:\n" + lastCrash.readText().take(4000))
            lastCrash.delete()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        // Settings asked to open something here (the wallpaper screen): not a Home press.
        intent.getStringExtra(EXTRA_OPEN)?.let {
            returnState.request = it
            return
        }
        // Home on the launcher passes through the system launcher and the redirect brings us
        // straight back. A quick return without having started an app counts as a Home press.
        val now = SystemClock.uptimeMillis()
        val quickReturn = now - pausedAt < HOME_PRESS_WINDOW_MS && LaunchClock.last < pausedAt - 300
        if (resumed || quickReturn) returnState.onHomePressed()
    }

    override fun onStart() {
        super.onStart()
        // Set before the first frame, so the content is never drawn at full size first.
        returnState.onReturn(animate = !firstStart)
        firstStart = false
        applyExcludeFromRecents()
    }

    override fun onResume() {
        super.onResume()
        // The wallpaper may have changed while another app was open (themed icons).
        dev.liquid.launcher.core.data.LauncherData.get(this).checkWallpaperColours()
        resumed = true
        RedirectStatus.onLauncherResumed()
        window.decorView.post {
            Choreographer.getInstance().postFrameCallback { RedirectStatus.onLauncherFrame() }
        }
    }

    override fun onPause() {
        resumed = false
        pausedAt = SystemClock.uptimeMillis()
        RedirectStatus.onLauncherPaused()
        super.onPause()
    }

    companion object {
        private const val HOME_PRESS_WINDOW_MS = 1500L

        /** Extra naming what to open on the home screen: "wallpaper" or "layouts". */
        const val EXTRA_OPEN = "dev.liquid.launcher.OPEN"
    }

    private fun applyExcludeFromRecents() {
        val exclude = (application as LauncherApp).settings.get(LauncherSettings.ExcludeFromRecents)
        try {
            getSystemService(ActivityManager::class.java).appTasks
                .filter { it.taskInfo?.taskId == taskId }
                .forEach { it.setExcludeFromRecents(exclude) }
        } catch (e: Exception) {
            DebugLog.error("App", "setExcludeFromRecents failed: $e")
        }
    }
}
