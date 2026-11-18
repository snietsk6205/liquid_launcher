package dev.liquid.launcher.feature.home

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.common.Changelog
import dev.liquid.launcher.core.common.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * W1 (0.10.0): after an update, the new version's part of the changelog once, as a card above the pill, with
 * "Read all" (a sheet with every change) and "Got it". The changelog is the copy CI puts in the app's assets.
 */
internal object WhatsNew {
    private const val PREFS = "whatsNew"
    private const val SEEN = "seen"

    private fun installedVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }.getOrDefault("")

    /** The installed version's changes, when they have not been shown yet; null otherwise. */
    fun pending(context: Context): Changelog.Version? {
        val version = installedVersion(context).ifBlank { return null }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(SEEN, null) == version) return null
        val text = runCatching { context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() } }.getOrNull()
        val found = text?.let { Changelog.find(it, version) }?.takeIf { it.itemCount > 0 }
        // Nothing to show for this version (no changelog in a local build): never ask again for it.
        if (found == null) markSeen(context, version)
        return found
    }

    fun markSeen(context: Context, version: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(SEEN, version).apply()
        DebugLog.event("Home", "what's new in $version: seen")
    }
}

/** Shows [WhatsNew] once the home screen has settled with nothing open over it. */
@Composable
internal fun WhatsNewHost(controller: HomeController, glass: GlassConfig, lightBackground: Boolean, backdrop: Backdrop) {
    val context = LocalContext.current
    var version by remember { mutableStateOf<Changelog.Version?>(null) }
    var reading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1500)
        version = withContext(Dispatchers.IO) { WhatsNew.pending(context) }
    }
    val quiet by remember {
        derivedStateOf {
            controller.drawer.value < 0.01f && controller.search.value < 0.01f && !controller.todaySheet && controller.menu == null &&
                !controller.homeMenu && !controller.layoutsSheet && !controller.wallpaperStudio && !controller.wallpaperStrip &&
                !controller.widgetPicker && controller.clockStudio == null && !controller.editMode && controller.widgetOptions == null &&
                controller.tagging == null && controller.speedBump == null
        }
    }
    val v = version
    fun done() {
        v?.let { WhatsNew.markSeen(context, it.version) }
        version = null
        reading = false
    }
    ProvideControls(controller, glass, null) {
        Presence(v?.takeIf { !reading && quiet }) { shown -> WhatsNewCard(shown, glass, lightBackground, backdrop, controller, onRead = { reading = true }, onDone = { done() }) }
        Presence(v?.takeIf { reading }) { shown ->
            LiquidSheet(
                onDismiss = { done() },
                glass = glass,
                lightBackground = lightBackground,
                backdrop = backdrop,
                title = "What's new in ${shown.version}",
                actions = {
                    Spacer(Modifier.weight(1f))
                    GlassButton("Got it", LocalSheetContent.current, emphasis = ControlEmphasis.Main) { done() }
                },
            ) {
                shown.note?.let { SheetText(it, Modifier.padding(bottom = 6.dp)) }
                for (part in shown.parts) {
                    SheetText(part.title, Modifier.padding(top = 10.dp, bottom = 4.dp), alpha = 0.6f, size = 13)
                    for (item in part.items) {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 3.dp, bottom = 3.dp)) {
                            Text("•", style = TextStyle(fontFamily = HomeTheme.font, color = LocalSheetContent.current, fontSize = 15.sp), modifier = Modifier.width(16.dp))
                            Text(Changelog.plain(item), style = TextStyle(fontFamily = HomeTheme.font, color = LocalSheetContent.current, fontSize = 15.sp))
                        }
                    }
                }
            }
        }
    }
}

/** The card: the version, its first changes, "Read all" and "Got it", above the pill. */
@Composable
private fun WhatsNewCard(
    version: Changelog.Version,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    controller: HomeController,
    onRead: () -> Unit,
    onDone: () -> Unit,
) {
    val color = sheetContentColor(glass, lightBackground)
    val density = LocalDensity.current
    // Just above the pill (or where it usually is).
    val above = controller.pillBounds?.let { with(density) { (controller.screenHeight - it.top).toDp() } }?.takeIf { it > 0.dp } ?: 110.dp
    val shape = RoundedCornerShape(28.dp)
    val firsts = version.parts.flatMap { part -> part.items.map { part.title to it } }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = above + 12.dp)
                .fillMaxWidth()
                .sheetSurface(backdrop, shape, glass, lightBackground)
                .bloomLight()
                .clip(shape)
                .boardPicture(shape)
                .padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
        ) {
            Text(
                "What's new in ${version.version}",
                style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            )
            for ((part, item) in firsts.take(3)) {
                Text(
                    "$part · ${Changelog.plain(item)}",
                    modifier = Modifier.padding(top = 4.dp),
                    style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.82f), fontSize = 13.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (firsts.size > 3) {
                Text(
                    "and ${firsts.size - 3} more",
                    modifier = Modifier.padding(top = 4.dp),
                    style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.6f), fontSize = 12.sp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                GlassButton("Read all", color, onClick = onRead)
                Spacer(Modifier.width(8.dp))
                GlassButton("Got it", color, emphasis = ControlEmphasis.Main, onClick = onDone)
            }
        }
    }
}
