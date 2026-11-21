package dev.liquid.launcher.feature.home

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.data.ThemeMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * W3 (0.10.0): drawing a WM Keyboard theme's parts on the launcher's controls. Shapes are WM Keyboard's twelve
 * outlines; materials are its parts (a raised cap on a darker skirt that sinks when pressed, gloss from a light
 * direction, a surface texture, an edge, a label effect, a rainbow sheen, a soft neumorphic shadow), drawn here from
 * the same numbers; textures are the theme's pictures fitted to each control; press motions are its nine key motions.
 */

// ---- shapes ----

/** The outline of [shape] (WM Keyboard's names) with [cornerDp] corners. */
internal fun themeOutline(shape: String, cornerDp: Int): Shape = when (shape) {
    "PILL" -> RoundedCornerShape(50)
    "CIRCLE" -> CircleShape
    "SHARP", "NONE_SHARP" -> RoundedCornerShape(0.dp)
    "CUT" -> CutCornerShape(cornerDp.coerceIn(0, 28).dp)
    "SQUIRCLE" -> SquircleShape(cornerDp.coerceIn(0, 40) * 1.6f)
    "ARCH" -> ArchShape
    "LEAF" -> RoundedCornerShape(topStart = (cornerDp * 1.6f).coerceIn(10f, 40f).dp, topEnd = 0.dp, bottomEnd = (cornerDp * 1.6f).coerceIn(10f, 40f).dp, bottomStart = 0.dp)
    "SLANT" -> SlantShape
    "HEXAGON" -> HexagonShape
    "SCALLOP" -> ScallopShape(cornerDp.coerceIn(0, 40).toFloat())
    "TICKET" -> TicketShape(cornerDp.coerceIn(0, 40).toFloat())
    else -> RoundedCornerShape(cornerDp.coerceIn(0, 40).dp)
}

/** The closest rounded outline, for the glass shader (which works on rounded rectangles). */
internal fun glassOutline(shape: String, cornerDp: Int): CornerBasedShape = when (shape) {
    "PILL", "CIRCLE" -> RoundedCornerShape(50)
    "SHARP" -> RoundedCornerShape(0.dp)
    "CUT" -> CutCornerShape(cornerDp.coerceIn(0, 28).dp)
    "SQUIRCLE" -> RoundedCornerShape((cornerDp * 1.4f).coerceIn(0f, 40f).dp)
    "ARCH" -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 0, bottomStartPercent = 0)
    "LEAF" -> RoundedCornerShape(topStart = (cornerDp * 1.6f).coerceIn(10f, 40f).dp, topEnd = 0.dp, bottomEnd = (cornerDp * 1.6f).coerceIn(10f, 40f).dp, bottomStart = 0.dp)
    else -> RoundedCornerShape(cornerDp.coerceIn(0, 40).dp)
}

/** A rounded rectangle whose corners are superellipses (curvature eases in, as a squircle's does). */
private class SquircleShape(private val radiusDp: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = min(with(density) { radiusDp.dp.toPx() }, min(size.width, size.height) / 2f)
        val path = Path()
        val n = 5.0
        val steps = 10
        fun corner(cx: Float, cy: Float, startDeg: Double) {
            for (i in 0..steps) {
                val t = (startDeg + 90.0 * i / steps) * PI / 180.0
                val c = cos(t)
                val s = sin(t)
                val x = cx + r * (abs(c).pow(2.0 / n) * kotlin.math.sign(c)).toFloat()
                val y = cy + r * (abs(s).pow(2.0 / n) * kotlin.math.sign(s)).toFloat()
                if (i == 0 && startDeg == 180.0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }
        corner(r, r, 180.0)
        corner(size.width - r, r, 270.0)
        corner(size.width - r, size.height - r, 0.0)
        corner(r, size.height - r, 90.0)
        path.close()
        return Outline.Generic(path)
    }
}

private val ArchShape = GenericShape { size, _ ->
    val r = min(size.width / 2f, size.height * 0.9f)
    moveTo(0f, size.height)
    lineTo(0f, r)
    arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
    lineTo(size.width - r, 0f)
    arcTo(Rect(size.width - 2 * r, 0f, size.width, 2 * r), 270f, 90f, false)
    lineTo(size.width, size.height)
    close()
}

private val SlantShape = GenericShape { size, _ ->
    val k = min(size.height * 0.28f, size.width * 0.2f)
    moveTo(k, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - k, size.height)
    lineTo(0f, size.height)
    close()
}

private val HexagonShape = GenericShape { size, _ ->
    val k = min(size.height * 0.5f, size.width * 0.25f)
    moveTo(k, 0f)
    lineTo(size.width - k, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width - k, size.height)
    lineTo(k, size.height)
    lineTo(0f, size.height / 2f)
    close()
}

/** A rounded rectangle whose top and bottom edges are a row of small arcs. */
private class ScallopShape(private val cornerDp: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = min(with(density) { cornerDp.dp.toPx() }, size.height / 2f)
        val bump = with(density) { 3.dp.toPx() }
        val count = max(3, ((size.width - 2 * r) / with(density) { 14.dp.toPx() }).toInt())
        val span = (size.width - 2 * r) / count
        val path = Path()
        path.moveTo(0f, r)
        path.arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
        for (i in 0 until count) {
            val x0 = r + i * span
            path.quadraticTo(x0 + span / 2f, -bump, x0 + span, 0f)
        }
        path.arcTo(Rect(size.width - 2 * r, 0f, size.width, 2 * r), 270f, 90f, false)
        path.lineTo(size.width, size.height - r)
        path.arcTo(Rect(size.width - 2 * r, size.height - 2 * r, size.width, size.height), 0f, 90f, false)
        for (i in 0 until count) {
            val x0 = size.width - r - i * span
            path.quadraticTo(x0 - span / 2f, size.height + bump, x0 - span, size.height)
        }
        path.arcTo(Rect(0f, size.height - 2 * r, 2 * r, size.height), 90f, 90f, false)
        path.close()
        return Outline.Generic(path)
    }
}

/** A rounded rectangle with a half-round notch in the middle of each side, as a ticket. */
private class TicketShape(private val cornerDp: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = min(with(density) { cornerDp.dp.toPx() }, size.height / 2f)
        val notch = min(size.height * 0.22f, with(density) { 7.dp.toPx() })
        val body = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(Offset.Zero, size), androidx.compose.ui.geometry.CornerRadius(r)))
        }
        val holes = Path().apply {
            addOval(Rect(Offset(0f, size.height / 2f), notch))
            addOval(Rect(Offset(size.width, size.height / 2f), notch))
        }
        return Outline.Generic(Path().apply { op(body, holes, PathOperation.Difference) })
    }
}

/**
 * Outlines kept between frames (0.11.2): the same shape at the same size is the same Path, so the GPU keeps what it
 * worked out for it. A new Path every frame, for every themed button, pill and material face (its clip, its edge),
 * was rasterised and uploaded again each time: on Med's phone the study hub spent 48 ms a frame there ("sync").
 * The paths are never changed after they are made. Used on the main thread only (drawing).
 */
private object OutlineCache {
    private val paths = object : LinkedHashMap<Any, Path>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, Path>?): Boolean = size > 160
    }

    fun get(key: Any, make: () -> Path): Path = paths[key] ?: make().also { paths[key] = it }
}

private data class OutlineKey(val shape: Shape, val width: Float, val height: Float, val direction: LayoutDirection, val density: Float)
private data class InsetKey(val path: Path, val width: Float, val height: Float, val inset: Float)
private data class HalfKey(val width: Float, val height: Float, val top: Boolean)

/** The outline of [shape] at [size] as a path (the same Path object for the same shape and size; do not change it). */
internal fun DrawScope.outlinePath(shape: Shape, size: Size = this.size): Path =
    OutlineCache.get(OutlineKey(shape, size.width, size.height, layoutDirection, density)) {
        val path = Path()
        when (val o = shape.createOutline(size, layoutDirection, this)) {
            is Outline.Rectangle -> path.addRect(o.rect)
            is Outline.Rounded -> path.addRoundRect(o.roundRect)
            is Outline.Generic -> path.addPath(o.path)
        }
        path
    }

// ---- pictures (textures, sheet backgrounds) ----

private object ThemeImages {
    private val cache = object : LinkedHashMap<String, ImageBitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean = size > 10
    }

    fun cached(path: String): ImageBitmap? = synchronized(cache) { cache[path] }

    /** Decodes a theme picture at most [maxPx] on its long side (pictures can be photos). */
    fun load(path: String, maxPx: Int): ImageBitmap? {
        cached("$path@$maxPx")?.let { return it }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxPx * 2) sample *= 2
            val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            bitmap.asImageBitmap().also { synchronized(cache) { cache["$path@$maxPx"] = it } }
        } catch (e: Exception) {
            null
        }
    }
}

/** The theme picture at [path], decoded off the main thread; null while it loads or when there is none. */
@Composable
internal fun rememberThemeImage(path: String?, maxPx: Int = 512): ImageBitmap? {
    if (path.isNullOrBlank()) return null
    val image by produceState(ThemeImages.cached("$path@$maxPx"), path, maxPx) {
        if (value == null) value = withContext(Dispatchers.IO) { ThemeImages.load(path, maxPx) }
    }
    return image
}

/** Draws [image] inside [shape]: centre-cropped ("crop"), stretched, or repeated at its own size ("tile"). */
internal fun DrawScope.drawTexture(image: ImageBitmap, fit: String, alpha: Float, shape: Shape) {
    if (alpha <= 0.01f) return
    clipPath(outlinePath(shape)) {
        when (fit) {
            "tile" -> drawRect(ShaderBrush(ImageShader(image, TileMode.Repeated, TileMode.Repeated)), alpha = alpha)
            "stretch" -> drawImage(image, dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)), alpha = alpha)
            else -> {
                val scale = max(size.width / image.width, size.height / image.height)
                val w = image.width * scale
                val h = image.height * scale
                drawImage(
                    image,
                    dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - w) / 2f).toInt(), ((size.height - h) / 2f).toInt()),
                    dstSize = IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)),
                    alpha = alpha,
                )
            }
        }
    }
}

// ---- materials ----

/** One tile of each surface texture, drawn once and repeated (cheap at any size). */
private object TextureTiles {
    private val tiles = HashMap<String, ImageBitmap>()

    fun brush(kind: String, density: Float, light: Boolean): ShaderBrush? {
        if (kind == "NONE") return null
        val key = "$kind@$density@$light"
        val tile = synchronized(tiles) { tiles[key] } ?: draw(kind, density, light)?.also { synchronized(tiles) { tiles[key] = it } } ?: return null
        return ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
    }

    /** A deterministic noise so a texture is the same on every button and every frame. */
    private fun hash(x: Int, y: Int, seed: Int = 0): Float {
        var h = x * 374761393 + y * 668265263 + seed * 2147483647
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private fun draw(kind: String, density: Float, light: Boolean): ImageBitmap? {
        val size = (48 * density).toInt().coerceIn(32, 192)
        val bitmap = ImageBitmap(size, size)
        val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
        val ink = if (light) Color.White else Color.Black
        val paint = androidx.compose.ui.graphics.Paint()
        fun dot(x: Float, y: Float, r: Float, a: Float) {
            paint.color = ink.copy(alpha = a.coerceIn(0f, 1f))
            canvas.drawCircle(Offset(x, y), r, paint)
        }
        fun rect(l: Float, t: Float, r: Float, b: Float, a: Float, c: Color = ink) {
            paint.color = c.copy(alpha = a.coerceIn(0f, 1f))
            canvas.drawRect(Rect(l, t, r, b), paint)
        }
        fun line(x0: Float, y0: Float, x1: Float, y1: Float, a: Float, w: Float) {
            paint.color = ink.copy(alpha = a.coerceIn(0f, 1f))
            paint.strokeWidth = w
            canvas.drawLine(Offset(x0, y0), Offset(x1, y1), paint)
        }
        val s = size.toFloat()
        val d = density
        when (kind) {
            "NOISE" -> for (x in 0 until size step 2) for (y in 0 until size step 2) rect(x.toFloat(), y.toFloat(), x + 2f, y + 2f, hash(x, y) * 0.5f)
            "LINEN" -> {
                var i = 0f
                while (i < s) {
                    line(0f, i, s, i, 0.18f + hash(i.toInt(), 1) * 0.25f, d * 0.7f)
                    line(i, 0f, i, s, 0.14f + hash(1, i.toInt()) * 0.2f, d * 0.7f)
                    i += 3f * d
                }
            }
            "DOTS" -> {
                val step = s / 6f
                for (i in 0 until 6) for (j in 0 until 6) dot(i * step + step / 2f, j * step + step / 2f, d * 1.1f, 0.45f)
            }
            "SCANLINES" -> {
                var y = 0f
                while (y < s) {
                    rect(0f, y, s, y + d, 0.55f)
                    y += 3f * d
                }
            }
            "BRUSHED" -> for (y in 0 until size) rect(0f, y.toFloat(), s, y + 1f, hash(0, y, 7) * 0.35f)
            "WOOD" -> {
                for (k in 0 until 8) {
                    val base = k * s / 8f
                    var x = 0f
                    while (x < s) {
                        val y = base + sin((x / s) * 2f * PI.toFloat() + k) * d * 1.6f
                        dot(x, y, d * 0.7f, 0.22f + hash(k, x.toInt()) * 0.2f)
                        x += d * 0.8f
                    }
                }
            }
            "STONE" -> for (i in 0 until 28) dot(hash(i, 1, 3) * s, hash(i, 2, 3) * s, d * (1.5f + hash(i, 3, 3) * 4f), 0.08f + hash(i, 4, 3) * 0.16f)
            "PAPER" -> {
                for (x in 0 until size step 2) for (y in 0 until size step 2) rect(x.toFloat(), y.toFloat(), x + 2f, y + 2f, hash(x, y, 11) * 0.22f)
                for (i in 0 until 10) {
                    val x0 = hash(i, 5, 13) * s
                    val y0 = hash(i, 6, 13) * s
                    line(x0, y0, x0 + d * 5f * (hash(i, 7, 13) - 0.5f) * 2f, y0 + d * 3f, 0.25f, d * 0.5f)
                }
            }
            "CARBON" -> {
                val t = s / 8f
                for (i in 0 until 8) for (j in 0 until 8) {
                    val a = if ((i + j) % 2 == 0) 0.35f else 0.12f
                    rect(i * t, j * t, i * t + t, j * t + t / 2f, a)
                    rect(i * t, j * t + t / 2f, i * t + t, j * t + t, 0.47f - a)
                }
            }
            "LEATHER" -> {
                for (i in 0 until 60) dot(hash(i, 1, 17) * s, hash(i, 2, 17) * s, d * (0.6f + hash(i, 3, 17) * 1.4f), 0.1f + hash(i, 4, 17) * 0.2f)
                for (i in 0 until 6) {
                    val x0 = hash(i, 8, 19) * s
                    val y0 = hash(i, 9, 19) * s
                    line(x0, y0, x0 + d * 6f, y0 + d * 2f * (hash(i, 10, 19) - 0.5f), 0.3f, d * 0.6f)
                }
            }
            "PIXELS" -> {
                val t = s / 8f
                for (i in 0 until 8) for (j in 0 until 8) rect(i * t, j * t, i * t + t, j * t + t, hash(i, j, 23) * 0.4f)
            }
            "PLANKS" -> {
                val w = s / 3f
                for (k in 0 until 3) {
                    rect(k * w, 0f, k * w + d, s, 0.5f)
                    val joint = hash(k, 0, 29) * s
                    rect(k * w, joint, k * w + w, joint + d, 0.4f)
                }
            }
            else -> return null
        }
        return bitmap
    }
}

/** The text's shadow for a material's label effect (glow, pressed in, raised, shadow), or null. */
internal fun labelShadow(material: ThemeMaterial?, text: Color, accent: Color, density: Density): Shadow? {
    val m = material ?: return null
    val px = with(density) { 1.dp.toPx() }
    val c = m.labelColor?.let { Color(it) }
    return when (m.label) {
        "GLOW" -> Shadow(c ?: accent, Offset.Zero, 6f * px)
        "DEBOSS" -> Shadow(c ?: (if (text.luminance() > 0.5f) Color.Black else Color.White).copy(alpha = 0.55f), Offset(0f, -1.2f * px), 0.6f * px)
        "EMBOSS" -> Shadow(c ?: Color.Black.copy(alpha = 0.5f), Offset(0f, 1.3f * px), 0.6f * px)
        "SHADOW" -> Shadow(c ?: Color.Black.copy(alpha = 0.6f), Offset(1.4f * px, 1.4f * px), 1.8f * px)
        else -> null
    }
}

/** Dot matrix labels: the text shows only through a grid of round dots. */
internal fun Modifier.dotMatrix(on: Boolean): Modifier = if (!on) this else this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithDots()

private fun Modifier.drawWithDots(): Modifier = this.then(
    Modifier.drawWithContentCompat { drawContent ->
        drawContent()
        val step = 2.2.dp.toPx()
        val r = step * 0.42f
        val mask = Path()
        var y = step / 2f
        while (y < size.height) {
            var x = step / 2f
            while (x < size.width) {
                mask.addOval(Rect(Offset(x, y), r))
                x += step
            }
            y += step
        }
        drawPath(mask, Color.Black, blendMode = BlendMode.DstIn)
    }
)

private fun Modifier.drawWithContentCompat(block: androidx.compose.ui.graphics.drawscope.ContentDrawScope.(() -> Unit) -> Unit): Modifier =
    this.drawWithContent { block { drawContent() } }

/**
 * The face of a control in [material], filled with [fill]: the skirt and the face that sinks by [press] (0..1), its
 * gloss, surface texture, rainbow sheen and edge. Drawn instead of the plain fill.
 */
internal fun DrawScope.drawMaterialFace(m: ThemeMaterial, fill: Color, text: Color, accent: Color, shape: Shape, press: Float) {
    val bevel = m.bevelDp.dp.toPx()
    val inset = m.topInsetDp.dp.toPx()
    val sink = bevel * 0.75f * press
    // Soft (neumorphic) shadows outside the shape, towards and away from the light.
    if (m.softShadow > 0.01f) {
        val rad = (m.lightAngleDeg - 90f) * PI.toFloat() / 180f
        val dist = 3.dp.toPx() * (1f - press * 0.7f)
        val dx = cos(rad) * dist
        val dy = sin(rad) * dist
        val path = outlinePath(shape)
        for (i in 1..5) {
            val spread = i * 1.2.dp.toPx()
            val a = m.softShadow * 0.11f * (1f - i / 6f)
            scaleAround(spread) {
                translate(dx * (1 + i * 0.2f), dy * (1 + i * 0.2f)) { drawPath(path, Color.White.copy(alpha = a)) }
                translate(-dx * (1 + i * 0.2f), -dy * (1 + i * 0.2f)) { drawPath(path, Color.Black.copy(alpha = a * 1.4f)) }
            }
        }
    }
    val faceSize = Size(size.width - 2 * inset, (size.height - bevel - inset).coerceAtLeast(1f))
    // The skirt: the full shape, darker.
    if (bevel > 0.01f) {
        drawPath(outlinePath(shape), lerp(fill, Color.Black, m.skirtShade).copy(alpha = max(fill.alpha, 0.85f)))
    }
    translate(inset, inset + sink) {
        val face = outlinePath(shape, faceSize)
        drawPath(face, fill)
        clipPath(face) {
            val light = fill.luminance() < 0.5f
            TextureTiles.brush(m.texture, density, light)?.let { brush ->
                drawRect(brush, size = faceSize, alpha = m.textureOpacity * 0.6f)
            }
            if (m.gloss > 0.01f) {
                val rad = (m.lightAngleDeg + 90f) * PI.toFloat() / 180f
                val len = max(faceSize.width, faceSize.height)
                val c = Offset(faceSize.width / 2f, faceSize.height / 2f)
                val dir = Offset(cos(rad), sin(rad)) * (len / 2f)
                drawRect(
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.6f * m.gloss), 0.5f to Color.White.copy(alpha = 0.08f * m.gloss), 1f to Color.Transparent,
                        start = c - dir, end = c + dir,
                    ),
                    size = faceSize,
                )
            }
            if (m.iridescence > 0.01f) {
                drawRect(
                    Brush.sweepGradient(listOf(Color(0xFFFF6FD8), Color(0xFF7AF6FF), Color(0xFFB5FF7A), Color(0xFFFFE26F), Color(0xFFFF6FD8)), Offset(faceSize.width * 0.3f, faceSize.height * 0.2f)),
                    size = faceSize, alpha = 0.32f * m.iridescence,
                )
            }
        }
        drawEdge(m, face, faceSize, text, accent)
    }
}

/** Scales what [block] draws by [spreadPx] around the centre (an outline grown on every side). */
private fun DrawScope.scaleAround(spreadPx: Float, block: DrawScope.() -> Unit) {
    val sx = (size.width + 2 * spreadPx) / size.width.coerceAtLeast(1f)
    val sy = (size.height + 2 * spreadPx) / size.height.coerceAtLeast(1f)
    drawContext.transform.scale(sx, sy, Offset(size.width / 2f, size.height / 2f))
    block()
    drawContext.transform.scale(1f / sx, 1f / sy, Offset(size.width / 2f, size.height / 2f))
}

private fun DrawScope.drawEdge(m: ThemeMaterial, face: Path, faceSize: Size, text: Color, accent: Color) {
    if (m.edge == "NONE") return
    val w = m.edgeWidthDp.dp.toPx()
    val colour = m.edgeColor?.let { Color(it) }
    when (m.edge) {
        "STITCH" -> {
            val inner = outlinePathInset(face, faceSize, w * 2.2f)
            drawPath(inner, colour ?: text.copy(alpha = 0.7f), style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 3f, w * 2.2f)), cap = StrokeCap.Round))
        }
        "GLOW" -> {
            val c = colour ?: accent
            drawPath(face, c.copy(alpha = 0.16f), style = Stroke(w * 4f))
            drawPath(face, c.copy(alpha = 0.32f), style = Stroke(w * 2.2f))
            drawPath(face, c.copy(alpha = 0.95f), style = Stroke(w))
        }
        "INK" -> drawPath(face, colour ?: Color.Black.copy(alpha = 0.85f), style = Stroke(w))
        "RIM" -> drawPath(
            face,
            Brush.verticalGradient(0f to (colour ?: Color.White).copy(alpha = 0.75f), 0.5f to (colour ?: Color.White).copy(alpha = 0.12f), 1f to Color.Black.copy(alpha = 0.35f), endY = faceSize.height),
            style = Stroke(w),
        )
        "INSET" -> {
            clipPath(face) {
                drawPath(face, Color.Black.copy(alpha = 0.38f), style = Stroke(w * 2.4f))
            }
            translate(0f, w * 0.8f) { drawPath(face, (colour ?: Color.White).copy(alpha = 0.18f), style = Stroke(w * 0.8f)) }
        }
        "DOUBLE" -> {
            val c = colour ?: text.copy(alpha = 0.55f)
            drawPath(face, c, style = Stroke(w))
            drawPath(outlinePathInset(face, faceSize, w * 2.5f), c.copy(alpha = c.alpha * 0.7f), style = Stroke(w * 0.8f))
        }
        "DOTTED" -> drawPath(face, colour ?: text.copy(alpha = 0.7f), style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, w * 2.6f)), cap = StrokeCap.Round))
        "BEVEL" -> {
            val half = OutlineCache.get(HalfKey(faceSize.width, faceSize.height, top = true)) {
                Path().apply { addRect(Rect(0f, 0f, faceSize.width, faceSize.height / 2f)) }
            }
            clipPath(half) { drawPath(face, Color.White.copy(alpha = 0.55f), style = Stroke(w)) }
            val low = OutlineCache.get(HalfKey(faceSize.width, faceSize.height, top = false)) {
                Path().apply { addRect(Rect(0f, faceSize.height / 2f, faceSize.width, faceSize.height)) }
            }
            clipPath(low) { drawPath(face, Color.Black.copy(alpha = 0.5f), style = Stroke(w)) }
        }
    }
}

/** [path] (of [size]) shrunk towards its centre by [insetPx] on every side. */
private fun outlinePathInset(path: Path, size: Size, insetPx: Float): Path = OutlineCache.get(InsetKey(path, size.width, size.height, insetPx)) {
    insetOf(path, size, insetPx)
}

private fun insetOf(path: Path, size: Size, insetPx: Float): Path {
    val sx = ((size.width - 2 * insetPx) / size.width.coerceAtLeast(1f)).coerceAtLeast(0.1f)
    val sy = ((size.height - 2 * insetPx) / size.height.coerceAtLeast(1f)).coerceAtLeast(0.1f)
    val m = androidx.compose.ui.graphics.Matrix()
    m.translate(size.width / 2f, size.height / 2f)
    m.scale(sx, sy)
    m.translate(-size.width / 2f, -size.height / 2f)
    return Path().apply {
        addPath(path)
        transform(m)
    }
}

// ---- press motions ----

/**
 * WM Keyboard's key motions on any control: how it moves while [interaction] presses it. [dip] scales the movement,
 * [wobble] how much it bounces back. Read in the layer only, so a press redraws nothing else.
 */
internal fun Modifier.pressMotion(interaction: InteractionSource, motion: String, dip: Float, wobble: Float): Modifier = composed {
    val progress = remember { Animatable(0f) }
    var at by remember { mutableStateOf(Offset.Unspecified) }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> {
                    at = i.pressPosition
                    launch { progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = MotionScale.stiffness(1400f))) }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> launch {
                    progress.animateTo(0f, spring(dampingRatio = (0.9f - 0.55f * wobble).coerceIn(0.3f, 1f), stiffness = MotionScale.stiffness(520f)))
                }
            }
        }
    }
    val amount = dip.coerceIn(0f, 1f)
    graphicsLayer {
        val p = progress.value * amount
        when (motion) {
            "SINK" -> {
                scaleX = 1f - 0.07f * p; scaleY = 1f - 0.07f * p; translationY = 2.dp.toPx() * p
            }
            "GROW" -> {
                scaleX = 1f + 0.12f * p; scaleY = 1f + 0.12f * p
            }
            "SHRINK" -> {
                scaleX = 1f - 0.12f * p; scaleY = 1f - 0.12f * p
            }
            "RISE" -> {
                translationY = -6.dp.toPx() * p; scaleX = 1f + 0.03f * p; scaleY = 1f + 0.03f * p
            }
            "TILT" -> {
                val f = at
                if (f.isSpecified && size.width > 0f) {
                    rotationY = ((f.x / size.width) - 0.5f) * 22f * p
                    rotationX = -((f.y / size.height) - 0.5f) * 22f * p
                    cameraDistance = 12f * density
                }
                scaleX = 1f - 0.03f * p; scaleY = 1f - 0.03f * p
            }
            "JELLY" -> {
                scaleX = 1f + 0.14f * p; scaleY = 1f - 0.12f * p
            }
            "POP" -> {
                scaleX = 1f + 0.12f * p; scaleY = 1f + 0.12f * p; translationY = -4.dp.toPx() * p
            }
            "SQUISH" -> {
                scaleX = 1f + 0.1f * p; scaleY = 1f - 0.14f * p; translationY = 1.5.dp.toPx() * p
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            "FADE" -> {
                alpha = 1f - 0.4f * p; scaleX = 1f - 0.04f * p; scaleY = 1f - 0.04f * p
            }
        }
    }
}

private val Offset.isSpecified: Boolean get() = this != Offset.Unspecified
