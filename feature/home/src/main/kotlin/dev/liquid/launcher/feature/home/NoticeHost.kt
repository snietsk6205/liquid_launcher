package dev.liquid.launcher.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.common.Notice
import dev.liquid.launcher.core.common.NoticeKind
import dev.liquid.launcher.core.common.Notices
import dev.liquid.launcher.core.data.LauncherAction
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.liquidGlass
import dev.liquid.launcher.core.settings.LauncherSettings as S
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.derive
import dev.liquid.launcher.feature.redirect.RedirectStatus
import kotlinx.coroutines.delay

/** How glass notices are shown. */
data class NoticeConfig(
    val enabled: Boolean,
    /** Ids of the kinds that are shown. */
    val kinds: Set<String>,
    /** "top", "bottom" or "centre". */
    val position: String,
    val durationMs: Int,
    /** "glass", "solid" or "plain". */
    val style: String,
    val icons: Boolean,
)

fun SettingsStore.noticeConfig() = NoticeConfig(
    enabled = get(S.NoticesEnabled),
    kinds = S.noticeKinds.filter { get(it.value) }.keys,
    position = get(S.NoticePosition),
    durationMs = get(S.NoticeDuration),
    style = get(S.NoticeStyle),
    icons = get(S.NoticeIcons),
)

private fun iconOf(kind: NoticeKind): Int = when (kind) {
    NoticeKind.Layout -> LauncherIcons.Layers
    NoticeKind.Wallpaper -> LauncherIcons.Image
    NoticeKind.TagFilter -> LauncherIcons.Filter
    NoticeKind.Redirect -> LauncherIcons.Home
    NoticeKind.Update -> LauncherIcons.ArrowDown
    NoticeKind.Study -> LauncherIcons.School
    NoticeKind.Task -> LauncherIcons.CheckCircle
    NoticeKind.Info -> LauncherIcons.Info
}

/**
 * Shows the launcher's notices one after another as a glass label: who switched to which
 * layout, a new wallpaper, a tag filter, the redirect pausing and coming back, an update, a
 * study block starting. It grew out of the layout-name label of 0.4.1.
 *
 * A notice waits (up to its kind's patience) for the launcher to be in front, shows for the
 * set time, and makes way early when another one is waiting. Tapping runs its action, if any.
 */
@Composable
internal fun NoticeHost(controller: HomeController, glass: GlassConfig, lightBackground: Boolean, backdrop: Backdrop) {
    val config by controller.data.settings.derive { noticeConfig() }
    val current by rememberUpdatedState(config)
    var notice by remember { mutableStateOf<Notice?>(null) }
    val shown = remember { Animatable(0f) }

    RedirectNotices()

    LaunchedEffect(Unit) {
        Notices.tick.collect {
            while (true) {
                val next = Notices.next() ?: break
                if (!current.enabled || next.kind.id !in current.kinds) continue
                // Raised while the launcher was not in front (the redirect resuming, an update found in settings): wait for it.
                var waited = 0L
                while (!RedirectStatus.isLauncherResumed() && waited < next.kind.patienceMs) {
                    delay(250)
                    waited += 250
                }
                if (!RedirectStatus.isLauncherResumed()) continue
                if (waited > 0) delay(350)
                notice = next
                shown.animateTo(1f, Motion.pop())
                var visibleFor = 0
                while (visibleFor < current.durationMs) {
                    delay(80)
                    visibleFor += 80
                    // Something newer is waiting, or the user tapped it away.
                    if ((Notices.hasWaiting && visibleFor >= 600) || notice == null) break
                }
                shown.animateTo(0f, Motion.exit())
                notice = null
            }
        }
    }

    val showing = notice ?: return
    val style = config.style
    val shape = RoundedCornerShape(50)
    val accent = HomeTheme.accent
    val color = when (style) {
        "solid" -> readableOn(accent)
        "plain" -> if (lightBackground) Color(0xFF16181C) else Color.White
        else -> if (glass.lightGlass(Surfaces.Menu, lightBackground)) Color(0xFF16181C) else Color.White
    }
    val shadow = if (style == "plain" && !lightBackground) Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 6f) else null
    val fromBelow = config.position == "bottom"
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .align(
                    when (config.position) {
                        "bottom" -> Alignment.BottomCenter
                        "centre" -> Alignment.Center
                        else -> Alignment.TopCenter
                    }
                )
                .then(
                    when (config.position) {
                        "bottom" -> Modifier.navigationBarsPadding().padding(bottom = 112.dp)
                        "centre" -> Modifier
                        else -> Modifier.statusBarsPadding().padding(top = 14.dp)
                    }
                )
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    val p = shown.value
                    alpha = p.coerceIn(0f, 1f)
                    val scale = 0.88f + 0.12f * p
                    scaleX = scale
                    scaleY = scale
                    translationY = (1f - p) * 14.dp.toPx() * (if (fromBelow) 1f else -1f)
                }
                .then(
                    when (style) {
                        "solid" -> Modifier.background(accent, shape)
                        "plain" -> Modifier
                        else -> Modifier.liquidGlass(backdrop, shape, glass.style(Surfaces.Menu, lightBackground))
                    }
                )
                .clip(shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    showing.action?.let { controller.run(LauncherAction.decode(it)) }
                    notice = null
                }
                .padding(horizontal = 18.dp, vertical = if (showing.detail != null) 7.dp else 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (config.icons) {
                LauncherIcon(iconOf(showing.kind), contentDescription = null, tint = color, size = 18.dp)
                Spacer(Modifier.width(9.dp))
            }
            Column(Modifier.widthIn(max = 280.dp)) {
                Text(
                    showing.text,
                    style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, shadow = shadow),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                showing.detail?.let {
                    Text(
                        it,
                        style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.8f), fontSize = 12.sp, shadow = shadow),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Posts a notice when the redirect is paused or comes back. */
@Composable
private fun RedirectNotices() {
    val pausedUntil by RedirectStatus.pausedUntilWall.collectAsState()
    var wasPaused by remember { mutableStateOf(pausedUntil > System.currentTimeMillis()) }
    LaunchedEffect(pausedUntil) {
        val now = System.currentTimeMillis()
        val paused = pausedUntil > now
        if (paused && !wasPaused) {
            val minutes = ((pausedUntil - now + 30_000) / 60_000).toInt()
            val text = if (minutes >= 1) "Redirect paused for $minutes min" else "Redirect paused for ${((pausedUntil - now) / 1000).toInt()} s"
            Notices.post(NoticeKind.Redirect, text, "The system launcher stays when you go home")
        } else if (!paused && wasPaused) {
            Notices.post(NoticeKind.Redirect, "Redirect is back on")
        }
        wasPaused = paused
    }
}
