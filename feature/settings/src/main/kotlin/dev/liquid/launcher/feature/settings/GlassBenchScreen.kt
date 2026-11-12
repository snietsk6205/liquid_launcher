package dev.liquid.launcher.feature.settings

import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.liquid.launcher.core.designsystem.GlassQuality
import dev.liquid.launcher.core.designsystem.WallpaperStore
import dev.liquid.launcher.core.designsystem.liquidGlass
import kotlinx.coroutines.delay

/** Collects per-frame render times of a window. */
private class FrameCollector {
    private val durationsMs = ArrayList<Float>()

    val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        val ms = metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000f
        synchronized(durationsMs) { durationsMs += ms }
    }

    fun reset() = synchronized(durationsMs) { durationsMs.clear() }

    fun result(quality: GlassQuality, seconds: Float, budgetMs: Float): GlassResult {
        val sorted = synchronized(durationsMs) { durationsMs.sorted() }
        if (sorted.isEmpty()) return GlassResult(quality, 0, seconds, 0f, 0f, 0f, budgetMs)
        val p95 = sorted[((sorted.size - 1) * 0.95f).toInt()]
        val slow = sorted.count { it > budgetMs } * 100f / sorted.size
        return GlassResult(quality, sorted.size, seconds, sorted.average().toFloat(), p95, slow, budgetMs)
    }
}

private const val WARM_UP_MS = 700L
private const val MEASURE_MS = 4000L

/**
 * Animates content behind three glass surfaces and measures frame times for each quality
 * tier. The content changes every frame, which is the worst case for glass.
 */
@Composable
internal fun GlassBenchScreen(window: Window, wallpaper: WallpaperStore, onBack: () -> Unit) {
    val image by wallpaper.image.collectAsStateCompat()
    var quality by remember { mutableStateOf(GlassQuality.Full) }
    var running by remember { mutableStateOf(true) }
    var results by remember { mutableStateOf<List<GlassResult>>(emptyList()) }
    val collector = remember { FrameCollector() }

    DisposableEffect(window) {
        val thread = HandlerThread("frame-metrics").apply { start() }
        window.addOnFrameMetricsAvailableListener(collector.listener, Handler(thread.looper))
        onDispose {
            window.removeOnFrameMetricsAvailableListener(collector.listener)
            thread.quitSafely()
        }
    }

    LaunchedEffect(Unit) {
        val refresh = window.decorView.display?.refreshRate?.takeIf { it > 1f } ?: 60f
        val budgetMs = 1000f / refresh
        val collected = ArrayList<GlassResult>()
        for (tier in listOf(GlassQuality.Full, GlassQuality.Frosted, GlassQuality.Tint)) {
            quality = tier
            delay(WARM_UP_MS)
            collector.reset()
            delay(MEASURE_MS)
            collected += collector.result(tier, MEASURE_MS / 1000f, budgetMs)
            results = collected.toList()
        }
        DiagnosticsState.glass.value = collected
        running = false
    }

    val transition = rememberInfiniteTransition(label = "bench")
    val scroll by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "scroll",
    )
    val slide by transition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1900), RepeatMode.Reverse),
        label = "slide",
    )

    val backdrop = rememberLayerBackdrop()
    val tint = Color.White.copy(alpha = 0.16f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            val picture = image
            if (picture != null) {
                Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF3B5BDB), Color(0xFFE64980), Color(0xFFF59F00), Color(0xFF12B886))
                            )
                        )
                )
            }
            // A grid of tiles that keeps moving, standing in for icons under the glass.
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = -scroll * 600f }
                    .padding(top = 80.dp),
            ) {
                repeat(14) { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                    ) {
                        repeat(5) { col ->
                            Box(
                                Modifier
                                    .size(52.dp)
                                    .background(TILE_COLORS[(row + col) % TILE_COLORS.size], RoundedCornerShape(14.dp))
                            )
                        }
                    }
                }
            }
        }

        if (running) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(x = (slide * 90).dp)
                    .size(width = 200.dp, height = 130.dp)
                    .liquidGlass(backdrop, RoundedCornerShape(32.dp), quality, tint)
            )
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .systemBarsPadding()
                    .padding(top = 90.dp)
                    .offset(x = (-slide * 70).dp)
                    .size(96.dp)
                    .liquidGlass(backdrop, RoundedCornerShape(48.dp), quality, tint)
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .fillMaxWidth()
                    .height(60.dp)
                    .liquidGlass(backdrop, RoundedCornerShape(30.dp), quality, tint)
            )
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            val label = TextStyle(color = Color.White, fontSize = 13.sp)
            Text(
                if (running) "Measuring ${quality.name}…" else "Benchmark finished",
                style = label.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
            )
            results.forEach { Text(it.line(), style = label) }
            if (!running) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onBack) { Text("Back to diagnostics") }
            }
        }
    }
}

private val TILE_COLORS = listOf(
    Color(0xFFFFFFFF), Color(0xFF212529), Color(0xFFFF6B6B), Color(0xFF4DABF7),
    Color(0xFF69DB7C), Color(0xFFFFD43B), Color(0xFFB197FC),
)
