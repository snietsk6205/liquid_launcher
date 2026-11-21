package dev.liquid.launcher.feature.settings

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.liquid.launcher.core.data.GlassPresets
import dev.liquid.launcher.core.designsystem.GlassEngine
import dev.liquid.launcher.core.designsystem.GlassQuality
import dev.liquid.launcher.core.designsystem.GlassStyle
import dev.liquid.launcher.core.designsystem.WallpaperStore
import dev.liquid.launcher.core.designsystem.liquidGlass
import dev.liquid.launcher.core.settings.BoolSetting
import dev.liquid.launcher.core.settings.ChoiceSetting
import dev.liquid.launcher.core.settings.ColorSetting
import dev.liquid.launcher.core.settings.IntSetting
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.observe
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Counts the frames a window draws and how many of them missed their deadline. */
private class FrameCollector {
    private var frames = 0
    private var late = 0

    val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        val deadline = metrics.getMetric(FrameMetrics.DEADLINE)
        synchronized(this) {
            frames++
            if (deadline > 0 && total > deadline) late++
        }
    }

    fun reset() = synchronized(this) {
        frames = 0
        late = 0
    }

    fun result(quality: GlassQuality, seconds: Float, refreshHz: Float): GlassResult = synchronized(this) {
        GlassResult(quality, frames, seconds, refreshHz, if (frames == 0) 0f else late * 100f / frames)
    }
}

private const val WARM_UP_MS = 700L
private const val MEASURE_MS = 4000L

/**
 * Tune the glass, and measure it (P4, 0.9.12). Three glass surfaces over the wallpaper and moving tiles, drawn with
 * your glass settings the way the home screen draws them; below them every value of the real glass, live, and the
 * presets. "Measure" times the frames of each quality tier while the content moves (the worst case for glass) and
 * remembers the best tier this phone holds, for the automatic fallback. [measureFirst]: start by measuring, as
 * Diagnostics does.
 */
@Composable
internal fun GlassBenchScreen(store: SettingsStore, window: Window, wallpaper: WallpaperStore, measureFirst: Boolean, onBack: () -> Unit) {
    val image by wallpaper.image.collectAsStateCompat()
    var measuring by remember { mutableStateOf(measureFirst) }
    var tier by remember { mutableStateOf<GlassQuality?>(null) }
    var results by remember { mutableStateOf(DiagnosticsState.glass.value) }
    // Still unless measuring or asked to move: a moving picture never lets the screen settle (and the tuning is
    // easier to judge on a still one).
    var moving by remember { mutableStateOf(measureFirst) }
    val collector = remember { FrameCollector() }

    DisposableEffect(window) {
        val thread = HandlerThread("frame-metrics").apply { start() }
        window.addOnFrameMetricsAvailableListener(collector.listener, Handler(thread.looper))
        onDispose {
            window.removeOnFrameMetricsAvailableListener(collector.listener)
            thread.quitSafely()
        }
    }

    // While measuring, ask for the display's fastest mode; otherwise the phone may drop to 60 Hz part-way through
    // and the tiers are not comparable.
    if (measuring) {
        DisposableEffect(window) {
            val display = window.decorView.display
            val current = display?.mode
            val fastest = display?.supportedModes
                ?.filter { current == null || (it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight) }
                ?.maxByOrNull { it.refreshRate }
            val before = window.attributes.preferredDisplayModeId
            if (fastest != null) {
                window.attributes = window.attributes.apply { preferredDisplayModeId = fastest.modeId }
            }
            onDispose {
                window.attributes = window.attributes.apply { preferredDisplayModeId = before }
            }
        }
        LaunchedEffect(Unit) {
            moving = true
            val collected = ArrayList<GlassResult>()
            for (quality in listOf(GlassQuality.Full, GlassQuality.Frosted, GlassQuality.Tint)) {
                tier = quality
                delay(WARM_UP_MS)
                collector.reset()
                val started = SystemClock.uptimeMillis()
                delay(MEASURE_MS)
                val seconds = (SystemClock.uptimeMillis() - started) / 1000f
                val refresh = window.decorView.display?.refreshRate?.takeIf { it > 1f } ?: 60f
                collected += collector.result(quality, seconds, refresh)
                results = collected.toList()
            }
            DiagnosticsState.glass.value = collected
            // Remember the best tier that held the frame rate, for the automatic fallback.
            val cap = collected.firstOrNull { it.holdsFrameRate }?.quality ?: GlassQuality.Tint
            store.set(LauncherSettings.GlassCap, cap.name.lowercase())
            collected.firstOrNull { it.quality == GlassQuality.Full }?.let { full ->
                if (full.refreshHz > 0f) store.set(LauncherSettings.GlassFullPercent, (full.fps / full.refreshHz * 100f).toInt().coerceIn(1, 200))
            }
            tier = null
            moving = false
            measuring = false
        }
    }

    val transition = rememberInfiniteTransition(label = "bench")
    // Read only inside draw and layout lambdas, so the animation does not recompose the screen.
    val scroll = transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "scroll",
    )
    val slide = transition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1900), RepeatMode.Reverse),
        label = "slide",
    )

    // Every glass value, live: any change in the store redraws the glass at once.
    val values by store.values.collectAsStateCompat()
    val quality = tier ?: GlassQuality.fromSetting(store.get(LauncherSettings.GlassQuality))
    val style = remember(values, quality) { benchStyle(store, quality) }
    val backdrop = rememberLayerBackdrop()

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
                    .graphicsLayer { translationY = if (moving) -scroll.value * 600f else -300f }
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

        // The three surfaces: a sheet, a round button and the pill.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(top = 150.dp)
                .offset { IntOffset(if (moving) (slide.value * 70.dp.toPx()).toInt() else 0, 0) }
                .size(width = 220.dp, height = 140.dp)
                .liquidGlass(backdrop, RoundedCornerShape(32.dp), style)
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(top = 60.dp)
                .offset { IntOffset(if (moving) (-slide.value * 90.dp.toPx()).toInt() else 0, 0) }
                .size(76.dp)
                .liquidGlass(backdrop, RoundedCornerShape(38.dp), style)
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(top = 310.dp, start = 24.dp, end = 24.dp)
                .fillMaxWidth()
                .height(60.dp)
                .liquidGlass(backdrop, RoundedCornerShape(30.dp), style)
        )

        if (measuring) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .systemBarsPadding()
                    .padding(16.dp)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.66f), RoundedCornerShape(20.dp))
                    .padding(16.dp),
            ) {
                Text("Measuring ${tier?.name ?: ""}…", style = benchLabel.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp))
                results.forEach { Text(it.line(), style = benchLabel) }
            }
        } else {
            TunePanel(
                store, results, moving,
                onMoving = { moving = it },
                onMeasure = { measuring = true },
                onBack = onBack,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.52f),
            )
        }
    }
}

private val benchLabel = TextStyle(color = Color.White, fontSize = 13.sp)

/** The glass the home screen would draw for [quality], from the settings (the tint in white, as on a dark wallpaper). */
private fun benchStyle(store: SettingsStore, quality: GlassQuality): GlassStyle {
    val s = LauncherSettings
    return GlassStyle(
        quality = quality,
        blur = store.get(s.GlassDefault.blur).dp,
        refraction = store.get(s.GlassDefault.refraction) / 100f,
        tint = Color.White.copy(alpha = store.get(s.GlassDefault.tint) / 100f),
        rim = store.get(s.GlassRim) / 100f,
        saturation = store.get(s.GlassSaturation) / 100f,
        brightness = store.get(s.GlassBrightness) / 100f,
        dispersion = store.get(s.GlassDispersion),
        depth = store.get(s.GlassDepth),
        rimColor = ColorSetting.parse(store.get(s.GlassRimColour))?.let { Color(it) } ?: Color.White,
        lightAngle = store.get(s.GlassLightAngle).toFloat(),
        shadow = store.get(s.GlassShadow) / 100f,
        glow = store.get(s.GlassGlow) / 100f,
        engine = GlassEngine.fromSetting(store.get(s.GlassEngine)),
        bevel = store.get(s.GlassBevel).dp,
        thickness = GlassStyle.thicknessOf(store.get(s.GlassThickness)),
        dispersionAmount = store.get(s.GlassDispersionAmount) / 100f,
        swell = store.get(s.GlassSwell) / 100f,
        specular = store.get(s.GlassSpecular) / 100f,
        shade = store.get(s.GlassShade) / 100f,
        grain = store.get(s.GlassGrain) / 100f,
    )
}

/** The presets and a live slider for every value of the glass; the last frame rates and the measure button. */
@Composable
private fun TunePanel(
    store: SettingsStore,
    results: List<GlassResult>,
    moving: Boolean,
    onMoving: (Boolean) -> Unit,
    onMeasure: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LauncherSettings
    val values by store.values.collectAsStateCompat()
    val current = remember(values) { GlassPresets.current(store) }
    val real = store.get(s.GlassEngine) == "real"
    Column(
        modifier
            .background(Color(0xE6101114), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .navigationBarsPadding()
            .padding(top = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Tune the glass", style = benchLabel.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp), modifier = Modifier.weight(1f))
            BenchChip(if (moving) "Pause" else "Move", false) { onMoving(!moving) }
            Spacer(Modifier.width(6.dp))
            BenchChip("Measure", false, onMeasure)
            Spacer(Modifier.width(6.dp))
            BenchChip("Done", false, onBack)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        ) {
            GlassPresets.presets.forEach { preset ->
                BenchChip(preset.name, preset == current) { GlassPresets.apply(store, preset) }
            }
        }
        current?.let { Text(it.about, style = benchLabel.copy(color = Color.White.copy(alpha = 0.7f)), modifier = Modifier.padding(horizontal = 16.dp)) }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
        ) {
            if (results.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    results.forEach { Text(it.line(), style = benchLabel.copy(color = Color.White.copy(alpha = 0.75f))) }
                }
            }
            LiveChoice(s.GlassEngine, store)
            LiveSlider(s.GlassDefault.blur, store, "Blur")
            LiveSlider(s.GlassDefault.refraction, store, "Bend")
            LiveSlider(s.GlassDefault.tint, store, "Tint")
            if (real) {
                LiveSlider(s.GlassBevel, store)
                LiveSlider(s.GlassThickness, store)
                LiveSlider(s.GlassSpecular, store)
                LiveSlider(s.GlassShade, store)
                LiveSlider(s.GlassGrain, store)
            }
            LiveSwitch(s.GlassDispersion, store)
            if (real && store.get(s.GlassDispersion)) LiveSlider(s.GlassDispersionAmount, store)
            LiveSwitch(s.GlassDepth, store)
            if (real && store.get(s.GlassDepth)) LiveSlider(s.GlassSwell, store)
            LiveSlider(s.GlassLightAngle, store)
            LiveSlider(s.GlassRim, store)
            LiveSlider(s.GlassSaturation, store)
            LiveSlider(s.GlassBrightness, store)
            LiveSlider(s.GlassGlow, store)
            LiveSlider(s.GlassShadow, store)
            Text(
                "These are the overall values (Settings → Liquid glass); a surface with its own values keeps them. " +
                    "The quality tier is set there too.",
                style = benchLabel.copy(color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun BenchChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, style = benchLabel.copy(color = if (selected) Color.Black else Color.White, fontWeight = FontWeight.Medium))
    }
}

/** A slider that writes as it moves, step by step, so the glass above follows the finger. */
@Composable
private fun LiveSlider(setting: IntSetting, store: SettingsStore, label: String = setting.title) {
    val value by store.observe(setting)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = benchLabel, modifier = Modifier.weight(1f))
            Text("$value${if (setting.unit.isEmpty()) "" else " " + setting.unit}", style = benchLabel.copy(color = Color.White.copy(alpha = 0.7f)))
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { v ->
                val steps = ((v - setting.min) / setting.step).roundToInt()
                val snapped = (setting.min + steps * setting.step).coerceIn(setting.min, setting.max)
                if (snapped != value) store.set(setting, snapped)
            },
            valueRange = setting.min.toFloat()..setting.max.toFloat(),
            modifier = Modifier.height(32.dp),
        )
    }
}

@Composable
private fun LiveSwitch(setting: BoolSetting, store: SettingsStore) {
    val value by store.observe(setting)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { store.set(setting, !value) }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(setting.title, style = benchLabel, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = { store.set(setting, it) })
    }
}

@Composable
private fun LiveChoice(setting: ChoiceSetting, store: SettingsStore) {
    val value by store.observe(setting)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        setting.options.forEach { (option, label) ->
            BenchChip(label, option == value) { store.set(setting, option) }
        }
    }
}

private val TILE_COLORS = listOf(
    Color(0xFFFFFFFF), Color(0xFF212529), Color(0xFFFF6B6B), Color(0xFF4DABF7),
    Color(0xFF69DB7C), Color(0xFFFFD43B), Color(0xFFB197FC),
)
