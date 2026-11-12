package dev.liquid.launcher

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.designsystem.LauncherTheme
import dev.liquid.launcher.core.settings.LauncherSettings
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        // Home is the end of the back stack: back does nothing here.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        val app = application as LauncherApp
        setContent {
            LauncherTheme {
                HomeScreen(
                    store = app.settings,
                    wallpaper = app.wallpaper,
                    apps = app.apps,
                    returnState = returnState,
                    onOpenSettings = { startActivity(SettingsActivity.intent(this)) },
                    onOpenSetup = { startActivity(SettingsActivity.intent(this, SettingsActivity.SCREEN_SETUP)) },
                )
            }
        }

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
        RedirectStatus.onLauncherResumed()
        window.decorView.post {
            Choreographer.getInstance().postFrameCallback { RedirectStatus.onLauncherFrame() }
        }
    }

    override fun onPause() {
        RedirectStatus.onLauncherPaused()
        super.onPause()
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
