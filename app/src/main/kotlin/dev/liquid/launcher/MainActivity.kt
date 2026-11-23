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
import kotlinx.coroutines.launch

/**
 * The home screen. It is an ordinary activity, not the default launcher: the accessibility
 * service brings it to the front whenever the system launcher's home screen would show.
 */
class MainActivity : ComponentActivity() {

    private val returnState = HomeReturnState()
    private val frameMeter = FrameMeter()
    private var clock: ClockRefresher? = null
    private var firstStart = true
    private var resumed = false
    private var pausedAt = 0L
    private var imageCallback: ((android.net.Uri?) -> Unit)? = null
    private val pickMedia = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        imageCallback?.invoke(uri)
        imageCallback = null
    }
    private var imagesCallback: ((List<android.net.Uri>) -> Unit)? = null
    private val pickMediaMany = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        imagesCallback?.invoke(uris)
        imagesCallback = null
    }
    private var folderCallback: ((android.net.Uri?) -> Unit)? = null
    private val pickTree = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
        folderCallback?.invoke(uri)
        folderCallback = null
    }

    private var contactsCallback: ((Boolean) -> Unit)? = null
    private val askContacts = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        contactsCallback?.invoke(granted)
        contactsCallback = null
    }

    private var calendarCallback: ((Boolean) -> Unit)? = null
    private val askCalendar = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { result ->
        calendarCallback?.invoke(result[android.Manifest.permission.READ_CALENDAR] == true)
        calendarCallback = null
    }
    // 0.11.2: the camera's answer is handled here, by whichever window is up when it comes (a launcher restarted while
    // the camera was open used to lose it); what is being taken is written down in the settings (BoardPhotos).
    private val takePhotoLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        val app = application as LauncherApp
        // 0.13.0: a camera that cannot hand a photo back: the photo picker opens on the newest photos.
        if (dev.liquid.launcher.core.data.BoardPhotos.pending(app.settings)?.pick == true) {
            imageCallback = { uri ->
                app.appScope.launch {
                    dev.liquid.launcher.core.data.BoardPhotos.savePicked(app, app.settings, uri)?.let { module ->
                        dev.liquid.launcher.core.common.Notices.post(dev.liquid.launcher.core.common.NoticeKind.Study, "Board photo: $module", "Pictures/ESSAI/$module")
                    }
                }
            }
            runCatching {
                pickMedia.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
            }.onFailure { DebugLog.error("App", "photo picker failed: $it") }
            return@registerForActivityResult
        }
        app.appScope.launch {
            dev.liquid.launcher.core.data.BoardPhotos.finish(app, app.settings, result.resultCode == RESULT_OK)?.let { module ->
                dev.liquid.launcher.core.common.Notices.post(dev.liquid.launcher.core.common.NoticeKind.Study, "Board photo: $module", "Pictures/ESSAI/$module")
            }
        }
    }
    private var locationCallback: ((Boolean) -> Unit)? = null
    private val askLocation = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { result ->
        locationCallback?.invoke(result[android.Manifest.permission.ACCESS_FINE_LOCATION] == true)
        locationCallback = null
    }
    private var fontCallback: ((android.net.Uri?) -> Unit)? = null
    private val pickFontFile = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        fontCallback?.invoke(uri)
        fontCallback = null
    }

    // Other apps' widgets (0.7.0, D100): the system's "allow" dialog and the widget's own set-up screen.
    private var widgetBindCallback: ((Boolean) -> Unit)? = null
    private val bindWidget = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        widgetBindCallback?.invoke(result.resultCode == RESULT_OK)
        widgetBindCallback = null
    }
    private var widgetSetUpCallback: ((Boolean) -> Unit)? = null

    @Deprecated("The widget host starts set-up screens with a request code; their result arrives here.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_WIDGET_SET_UP) {
            widgetSetUpCallback?.invoke(resultCode == RESULT_OK)
            widgetSetUpCallback = null
            return
        }
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A second creation in one process means Android rebuilt the home screen: every icon, the wallpaper and the glass again.
        screensCreated++
        DebugLog.event("Home", if (screensCreated == 1) "screen created" else "screen created again (#$screensCreated in this process): everything is drawn anew")
        lastConfiguration = android.content.res.Configuration(resources.configuration)
        // 0.11.3 (D168): one time source for every clock and timer, read from the wall clock at screen on and unlock.
        clock = ClockRefresher(applicationContext).also { it.register() }
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
            // 0.13.0: a link to a page starts Settings afresh. With only NEW_TASK, Android brought a Settings left open in
            // the background to the front as it was, and the page asked for never came (the emulator test caught it).
            openSection = {
                startActivity(SettingsActivity.intent(this, SettingsActivity.SECTION_PREFIX + it).addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
            },
            openSettingsAt = { startActivity(SettingsActivity.intent(this, it).addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)) },
            requestContacts = { callback ->
                if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    callback(true)
                } else {
                    contactsCallback = callback
                    try {
                        askContacts.launch(android.Manifest.permission.READ_CONTACTS)
                    } catch (e: Exception) {
                        DebugLog.error("App", "contacts permission request failed: $e")
                        contactsCallback = null
                        callback(false)
                    }
                }
            },
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
            pickImages = { callback ->
                imagesCallback = callback
                try {
                    pickMediaMany.launch(
                        androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                } catch (e: Exception) {
                    DebugLog.error("App", "photo picker failed: $e")
                    imagesCallback = null
                    callback(emptyList())
                }
            },
            requestCalendar = { callback ->
                val read = checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val write = checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (read && write) {
                    callback(true)
                } else {
                    calendarCallback = callback
                    try {
                        askCalendar.launch(arrayOf(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR))
                    } catch (e: Exception) {
                        DebugLog.error("App", "calendar permission request failed: $e")
                        calendarCallback = null
                        callback(read)
                    }
                }
            },
            takePhoto = { intent ->
                try {
                    takePhotoLauncher.launch(intent)
                } catch (e: Exception) {
                    DebugLog.error("App", "camera failed: $e")
                    android.widget.Toast.makeText(this, "Could not open the camera", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            openBoardCamera = { module ->
                try {
                    startActivity(dev.liquid.launcher.core.data.BoardPhotos.cameraIntent(this, module))
                } catch (e: Exception) {
                    DebugLog.error("App", "board camera failed: $e")
                    android.widget.Toast.makeText(this, "Could not open the camera", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            requestLocation = { callback ->
                if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    callback(true)
                } else {
                    locationCallback = callback
                    try {
                        // Android 12+ wants the coarse permission asked for together with the fine one.
                        askLocation.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                    } catch (e: Exception) {
                        DebugLog.error("App", "location permission request failed: $e")
                        locationCallback = null
                        callback(false)
                    }
                }
            },
            pickFont = { callback ->
                fontCallback = callback
                try {
                    // Font files rarely carry a font type on Android, so any file can be picked; the launcher checks it loads.
                    pickFontFile.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream", "*/*"))
                } catch (e: Exception) {
                    DebugLog.error("App", "file picker failed: $e")
                    fontCallback = null
                    callback(null)
                }
            },
            bindWidget = { intent, callback ->
                widgetBindCallback = callback
                try {
                    bindWidget.launch(intent)
                } catch (e: Exception) {
                    DebugLog.error("Widgets", "the system's widget dialog failed: $e")
                    widgetBindCallback = null
                    callback(false)
                }
            },
            setUpWidget = { id, callback ->
                widgetSetUpCallback = callback
                try {
                    app.data.widgets.host.startAppWidgetConfigureActivityForResult(this, id, 0, REQUEST_WIDGET_SET_UP, null)
                } catch (e: Exception) {
                    // A set-up screen that cannot be opened from a launcher: place the widget as it is.
                    DebugLog.error("Widgets", "the widget's set-up screen failed: $e")
                    widgetSetUpCallback = null
                    callback(true)
                }
            },
            pickFolder = { callback ->
                folderCallback = callback
                try {
                    pickTree.launch(null)
                } catch (e: Exception) {
                    DebugLog.error("App", "folder picker failed: $e")
                    folderCallback = null
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

        intent?.let { takeSearchText(it) }
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
        takeSearchText(intent)
        intent.getStringExtra(EXTRA_OPEN)?.let {
            returnState.request = it
            return
        }
        // A return after the drawer or keyboard was left open: make sure nothing of it is left.
        returnState.onLeft()
        // Home on the launcher passes through the system launcher and the redirect brings us
        // straight back. A quick return without having started an app counts as a Home press.
        val now = SystemClock.uptimeMillis()
        val quickReturn = now - pausedAt < HOME_PRESS_WINDOW_MS && LaunchClock.last < pausedAt - 300
        if (resumed || quickReturn) returnState.onHomePressed()
    }

    override fun onStart() {
        super.onStart()
        clock?.onStart()
        (application as LauncherApp).data.widgets.listen(true)
        // Set before the first frame, so the content is never drawn at full size first. 0.11.3 (D170): fading in, the
        // whole screen fades in over the system's home screen; the redirect then never asks for the last picture.
        val settings = (application as LauncherApp).settings
        val fades = settings.get(LauncherSettings.SettleStyle) != "none" && settings.get(LauncherSettings.SettleDuration) > 0
        returnState.onReturn(animate = !firstStart && fades, wholeScreen = fades)
        dev.liquid.launcher.feature.home.KeyboardLeaving.active = false
        firstStart = false
        applyExcludeFromRecents()
    }

    override fun onResume() {
        super.onResume()
        clock?.onResume()
        dev.liquid.launcher.feature.home.KeyboardLeaving.active = false
        applySelectionEntry()
        // The wallpaper may have changed while another app was open (themed icons).
        dev.liquid.launcher.core.data.LauncherData.get(this).checkWallpaperColours()
        // So may the calendar (the study companion's blocks and tasks).
        dev.liquid.launcher.core.data.LauncherData.get(this).study.refresh()
        // And ask the phone to sync the study calendars (at most every 5 minutes): some phones sync them hours late.
        dev.liquid.launcher.core.data.LauncherData.get(this).study.requestSync()
        resumed = true
        window.addOnFrameMetricsAvailableListener(frameMeter, android.os.Handler(mainLooper))
        RedirectStatus.onLauncherResumed()
        window.decorView.post {
            Choreographer.getInstance().postFrameCallback { RedirectStatus.onLauncherFrame() }
        }
    }

    override fun onPause() {
        resumed = false
        runCatching { window.removeOnFrameMetricsAvailableListener(frameMeter) }
        frameMeter.flush()
        pausedAt = SystemClock.uptimeMillis()
        // The system takes its picture of this screen about now. If it shows the keyboard, or something
        // that is closed while the launcher is away, the redirect must not bring that picture back.
        val keyboard = try {
            window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true
        } catch (e: Exception) {
            false
        }
        // 0.11.3 (D170): with nothing open that closes while away, a keyboard still on screen is only on its way out (an
        // app was started from search): the screen is laid out without it at once, so the picture taken as the launcher
        // leaves shows it as it will be on return, and stays usable.
        // Only when an app was just started from the launcher: paused under recents or a dialog, search keeps its keyboard.
        val closes = returnState.closesWhileAway()
        val leftover = keyboard && !closes && returnState.appJustStarted()
        if (leftover) dev.liquid.launcher.feature.home.KeyboardLeaving.active = true
        RedirectStatus.staleSnapshot = closes || (keyboard && !leftover)
        RedirectStatus.onLauncherPaused()
        super.onPause()
    }

    /**
     * Changes the launcher handles without being rebuilt (0.7.1, D106). The one that matters: when the
     * system wallpaper changes, Android 12+ recolours the system theme (Material You), an "assets
     * paths" change, and used to restart this screen 3 to 8 s after every wallpaper change.
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val changes = lastConfiguration?.diff(newConfig) ?: 0
        lastConfiguration = android.content.res.Configuration(newConfig)
        if (changes and android.content.pm.ActivityInfo.CONFIG_ASSETS_PATHS != 0) {
            DebugLog.event("Home", "system theme colours changed: the screen is kept (not rebuilt)")
        }
    }

    private var lastConfiguration: android.content.res.Configuration? = null

    override fun onStop() {
        super.onStop()
        clock?.onStop()
        // Nothing of the launcher is visible any more: close what an app was started from.
        returnState.onLeft()
        (application as LauncherApp).data.widgets.listen(false)
    }

    /** 0.12.0 (D171): text handed over by [LiquidEntryActivity] for search ("search" request). */
    private fun takeSearchText(intent: Intent) {
        if (intent.getStringExtra(EXTRA_OPEN) != "search") return
        returnState.searchText = intent.getStringExtra(LiquidEntryActivity.EXTRA_QUERY)
        returnState.searchAsk = intent.getBooleanExtra(LiquidEntryActivity.EXTRA_ASK, false)
    }

    /** 0.12.0: "Liquid" in the text selection menu follows its setting (Settings → Search → More). */
    private fun applySelectionEntry() {
        val wanted = (application as LauncherApp).settings.get(LauncherSettings.SearchSelectionEntry)
        val component = android.content.ComponentName(this, "dev.liquid.launcher.LiquidSelectionEntry")
        val state = if (wanted) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        try {
            if (packageManager.getComponentEnabledSetting(component) != state) {
                packageManager.setComponentEnabledSetting(component, state, android.content.pm.PackageManager.DONT_KILL_APP)
                DebugLog.event("Search", "\"Liquid\" in the text selection menu: ${if (wanted) "on" else "off"}")
            }
        } catch (e: Exception) {
            DebugLog.error("Search", "could not change the text selection entry: $e")
        }
    }

    override fun onDestroy() {
        clock?.unregister()
        clock = null
        super.onDestroy()
    }

    companion object {
        private var screensCreated = 0
        private const val HOME_PRESS_WINDOW_MS = 1500L
        private const val REQUEST_WIDGET_SET_UP = 0x5717

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
