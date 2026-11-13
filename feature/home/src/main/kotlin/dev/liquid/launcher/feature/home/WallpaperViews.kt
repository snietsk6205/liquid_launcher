package dev.liquid.launcher.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.common.Frame
import dev.liquid.launcher.core.data.WallpaperItem
import dev.liquid.launcher.core.data.WallpaperLibraryOps
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.liquidGlass
import dev.liquid.launcher.core.settings.LauncherSettings
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Draws a wallpaper filling the screen. [frame] is the part of [image] the wallpaper canvas
 * covers (null = all of it); when that part is wider than the screen, [offset] (0 = first page,
 * 1 = last) slides the visible window across it: parallax. Everything happens in the draw
 * phase, so paging and placing redraw without recomposing.
 */
@Composable
internal fun WallpaperImage(image: ImageBitmap, frame: () -> Frame?, offset: () -> Float, modifier: Modifier) {
    Spacer(
        modifier
            .fillMaxSize()
            .drawBehind {
                val f = frame() ?: Frame(0f, 0f, image.width.toFloat(), image.height.toFloat())
                if (size.width <= 0f || size.height <= 0f || f.width <= 0f || f.height <= 0f) return@drawBehind
                val aspect = size.width / size.height
                var w = f.height * aspect
                var h = f.height
                if (w > f.width) {
                    w = f.width
                    h = f.width / aspect
                }
                val left = f.left + (f.width - w) * offset().coerceIn(0f, 1f)
                val top = f.top + (f.height - h) / 2f
                // Whole source pixels, with the remainder moved on screen, so slow pans stay smooth.
                val scale = size.width / w
                val srcX = floor(left).toInt().coerceIn(0, image.width - 1)
                val srcY = floor(top).toInt().coerceIn(0, image.height - 1)
                val srcW = (w + 2f).roundToInt().coerceAtMost(image.width - srcX).coerceAtLeast(1)
                val srcH = (h + 2f).roundToInt().coerceAtMost(image.height - srcY).coerceAtLeast(1)
                val dx = (srcX - left) * scale
                val dy = (srcY - top) * scale
                clipRect {
                    drawImage(
                        image,
                        srcOffset = IntOffset(srcX, srcY),
                        srcSize = IntSize(srcW, srcH),
                        dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()),
                        dstSize = IntSize((srcW * scale).roundToInt(), (srcH * scale).roundToInt()),
                        filterQuality = FilterQuality.Medium,
                    )
                }
            }
    )
}

/** The picture of the old screen melting away over the new one (layout or wallpaper change). */
@Composable
internal fun TransitionOverlay(controller: HomeController, style: String) {
    val shot = controller.transitionShot ?: return
    Image(
        bitmap = shot,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = controller.transitionProgress.value
                when (style) {
                    "slide" -> translationX = -p * size.width
                    "depth" -> {
                        alpha = 1f - p
                        val scale = 1f - 0.08f * p
                        scaleX = scale
                        scaleY = scale
                    }
                    "fade" -> alpha = 1f - p
                    else -> {
                        // Liquid: blurs as it fades, swelling very slightly.
                        alpha = (1f - p * 1.15f).coerceIn(0f, 1f)
                        val radius = 26.dp.toPx() * p
                        renderEffect = if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Clamp) else null
                        val scale = 1f + 0.025f * p
                        scaleX = scale
                        scaleY = scale
                    }
                }
            }
            // Takes no touches: the new screen underneath is already live.
    )
}

/**
 * The wallpaper strip: favourites, then recent wallpapers, in one glass row above the bottom
 * edge. A tap switches with the transition; the buttons shuffle or open the wallpaper screen.
 */
@Composable
internal fun WallpaperStrip(
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    controller: HomeController,
) {
    val library by controller.data.wallpapers.state.collectAsState()
    val recentCount = remember { controller.data.settings.get(LauncherSettings.WallpaperHistory) }
    val items = remember(library) {
        val favourites = WallpaperLibraryOps.favourites(library)
        val recent = WallpaperLibraryOps.recent(library).filter { !it.favourite }.take(recentCount)
        favourites + recent
    }
    val color = if (glass.lightGlass(Surfaces.Menu, lightBackground)) Color(0xFF16181C) else Color.White
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 480f)) }
    val shape = RoundedCornerShape(30.dp)

    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controller.wallpaperStrip = false },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 24.dp)
                .graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * 40.dp.toPx()
                }
                .liquidGlass(backdrop, shape, glass.style(Surfaces.Menu, lightBackground))
                .clip(shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(vertical = 12.dp),
        ) {
            if (items.isEmpty()) {
                Text(
                    "No wallpapers yet. Open the wallpaper screen to add photos or link a folder.",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 14.sp),
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        StripThumb(item, item.id == library.current, color, controller) {
                            controller.wallpaperStrip = false
                            controller.applyWallpaper(WallpaperPick.Library(item))
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StripButton(LauncherIcons.Shuffle, "Shuffle", color, Modifier.weight(1f)) {
                    controller.wallpaperStrip = false
                    controller.shuffleWallpaper()
                }
                StripButton(LauncherIcons.Image, "Library and more", color, Modifier.weight(1.4f)) {
                    controller.wallpaperStrip = false
                    controller.wallpaperStudio = true
                }
            }
        }
    }
}

@Composable
private fun StripThumb(item: WallpaperItem, current: Boolean, color: Color, controller: HomeController, onClick: () -> Unit) {
    val thumb by produceState<ImageBitmap?>(null, item.id) { value = controller.data.wallpapers.thumbnail(item) }
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(width = 58.dp, height = 104.dp)
            .clip(shape)
            .background(color.copy(alpha = 0.1f))
            .border(if (current) 2.dp else 0.5.dp, color.copy(alpha = if (current) 0.95f else 0.25f), shape)
            .clickable(onClick = onClick),
    ) {
        thumb?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (item.favourite) {
            LauncherIcon(
                LauncherIcons.Heart, contentDescription = "Favourite", tint = Color.White, size = 14.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
            )
        }
    }
}

@Composable
private fun StripButton(icon: Int, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LauncherIcon(icon, contentDescription = null, tint = color, size = 18.dp)
        Spacer(Modifier.size(8.dp))
        Text(label, style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

/** The layout's name, briefly, in a small glass pill at the top after a switch. */
@Composable
internal fun FlashLabel(controller: HomeController, glass: GlassConfig, lightBackground: Boolean, backdrop: Backdrop, modifier: Modifier) {
    val text = controller.flashText
    val shown by androidx.compose.animation.core.animateFloatAsState(if (text != null) 1f else 0f, label = "flash")
    var last by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    if (text != null) last = text
    if (shown <= 0.01f) return
    val color = if (glass.lightGlass(Surfaces.Menu, lightBackground)) Color(0xFF16181C) else Color.White
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .graphicsLayer {
                alpha = shown
                val scale = 0.9f + 0.1f * shown
                scaleX = scale
                scaleY = scale
                translationY = (1f - shown) * -12.dp.toPx()
            }
            .liquidGlass(backdrop, shape, glass.style(Surfaces.Menu, lightBackground))
            .clip(shape)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(last, style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}
