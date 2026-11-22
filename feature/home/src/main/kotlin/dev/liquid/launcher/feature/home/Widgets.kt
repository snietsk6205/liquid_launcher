package dev.liquid.launcher.feature.home

import dev.liquid.launcher.core.settings.derive
import androidx.compose.ui.graphics.toArgb
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.SizeF
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.data.AppEntry
import dev.liquid.launcher.core.data.AppWidgets
import dev.liquid.launcher.core.data.HomeItem
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.liquidGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---- the picker (0.7.0, D100) ----

/** The widgets of one app, for the picker. */
private class WidgetApp(val pkg: String, val name: String, val app: AppEntry?, val widgets: List<AppWidgetProviderInfo>)

/**
 * Add a widget: the launcher's own widgets first, then every app that offers widgets, by name, each
 * opening to its widgets with their previews and sizes. A search field narrows both. Opens from the
 * home menu ("Widgets") and from edit mode.
 */
@Composable
internal fun WidgetPicker(glass: GlassConfig, lightBackground: Boolean, backdrop: Backdrop, controller: HomeController) {
    val context = LocalContext.current
    val color = sheetContentColor(glass, lightBackground)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.enter()) }
    val shape = RoundedCornerShape(32.dp)
    val layer = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    var query by remember { mutableStateOf("") }
    var openApp by remember { mutableStateOf<String?>(null) }
    val apps = controller.data.apps.apps.value
    val groups by produceState<List<WidgetApp>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            controller.data.widgets.providers()
                .groupBy { it.provider.packageName }
                .map { (pkg, widgets) ->
                    val name = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    } catch (e: Exception) {
                        pkg
                    }
                    WidgetApp(pkg, name, apps.firstOrNull { it.ref.pkg == pkg }, widgets.sortedBy { it.loadLabel(pm).orEmpty() })
                }
                .sortedBy { it.name.lowercase() }
        }
    }
    fun close() {
        controller.widgetPicker = false
    }
    val toSearch = controller.widgetTarget == "search"
    // Back to adding to pages once the picker is gone, however it was closed.
    DisposableEffect(Unit) { onDispose { controller.widgetTarget = "page" } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.2f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() }
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                // Stays below the status bar when the keyboard pushes it up; the list gives up height first.
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(12.dp)
                .graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * 60.dp.toPx()
                }
                .sheetSurface(backdrop, shape, glass, lightBackground, exported = layer)
                .bloomLight()
                .clip(shape)
                .boardPicture(shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            ProvideControls(controller, glass, layer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text("Widgets", style = pickerText(color, 18, FontWeight.SemiBold))
                        Text(if (toSearch) "Tap one to add it to the search screen" else "Tap one to add it to this page", style = pickerText(color, 12, alpha = 0.7f))
                    }
                    GlassButton(null, color, icon = LauncherIcons.Close, contentDescription = "Close") { close() }
                }
                // Search: narrows the launcher's widgets and the apps.
                Row(
                    Modifier
                        .padding(top = 10.dp, bottom = 6.dp)
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(50))
                        .background(color.copy(alpha = 0.08f))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LauncherIcon(LauncherIcons.Search, contentDescription = null, tint = color.copy(alpha = 0.7f), size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("Search widgets and apps", style = pickerText(color, 14, alpha = 0.55f))
                        BasicTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            textStyle = pickerText(color, 14), cursorBrush = SolidColor(color),
                            // The placeholder under the field is hidden from accessibility (fully covered), so the field says it.
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search widgets and apps" },
                        )
                    }
                }
                val q = query.trim().lowercase()
                val builtIns = WidgetTypes.filter { q.isEmpty() || q in it.name.lowercase() || q in it.about.lowercase() }
                val shownApps = groups.orEmpty().filter { g ->
                    q.isEmpty() || q in g.name.lowercase() || g.widgets.any { q in it.loadLabel(context.packageManager).orEmpty().lowercase() }
                }
                LazyColumn(Modifier.heightIn(max = 520.dp)) {
                    if (builtIns.isNotEmpty()) item { PickerSection("Liquid Launcher", color) }
                    items(builtIns, key = { "built:" + it.type }) { type ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    controller.addWidget(type.type)
                                    close()
                                }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(type.name, style = pickerText(color, 15, FontWeight.Medium))
                                Text(type.about, style = pickerText(color, 12, alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(if (type.w == 0) "full width" else "${type.w} × ${type.h}", style = pickerText(color, 11, alpha = 0.55f))
                        }
                    }
                    item { PickerSection(if (groups == null) "Apps · loading…" else "Apps", color) }
                    items(shownApps, key = { "app:" + it.pkg }) { group ->
                        val open = openApp == group.pkg || (q.isNotEmpty() && shownApps.size <= 3)
                        Column(Modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = 0.85f, stiffness = MotionScale.stiffness(520f)))) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { openApp = if (openApp == group.pkg) null else group.pkg }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(group.app, false, Modifier.size(34.dp), null)
                                Spacer(Modifier.width(12.dp))
                                Text(group.name, style = pickerText(color, 15), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${group.widgets.size}", style = pickerText(color, 12, alpha = 0.6f))
                                val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
                                LauncherIcon(LauncherIcons.ArrowDown, contentDescription = null, tint = color.copy(alpha = 0.6f), size = 18.dp, modifier = Modifier.padding(start = 6.dp).graphicsLayer { rotationZ = turn })
                            }
                            if (open) {
                                for (info in group.widgets) {
                                    WidgetPreviewCard(info, color, controller) {
                                        controller.addAppWidget(info)
                                        close()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerSection(text: String, color: Color) {
    Text(text, style = pickerText(color, 12, FontWeight.SemiBold, alpha = 0.65f), modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 2.dp))
}

private fun pickerText(color: Color, size: Int, weight: FontWeight = FontWeight.Normal, alpha: Float = 1f) =
    TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = alpha), fontSize = size.sp, fontWeight = weight)

/** One widget of an app: its preview (or icon), name and size in cells. */
@Composable
private fun WidgetPreviewCard(info: AppWidgetProviderInfo, color: Color, controller: HomeController, onClick: () -> Unit) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.densityDpi
    val preview by produceState<ImageBitmap?>(null, info) {
        value = withContext(Dispatchers.IO) {
            try {
                val drawable = info.loadPreviewImage(context, density) ?: info.loadIcon(context, density)
                drawable?.let {
                    val w = it.intrinsicWidth.coerceIn(1, 720)
                    val h = (it.intrinsicHeight.toFloat() * w / it.intrinsicWidth.coerceAtLeast(1)).toInt().coerceIn(1, 720)
                    it.toBitmap(w, h).asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    val label = remember(info) { info.loadLabel(context.packageManager).orEmpty() }
    val (columns, rows) = controller.gridFor(controller.currentPageIndex())
    val cells = remember(info, columns, rows) {
        AppWidgets.cells(info, controller.screenWidth / columns, controller.screenHeight * 0.8f / rows, columns, rows)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 46.dp, end = 4.dp, top = 4.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 56.dp, max = 150.dp), contentAlignment = Alignment.Center) {
            val image = preview
            if (image != null) {
                Image(image, contentDescription = label, contentScale = ContentScale.Fit, modifier = Modifier.heightIn(max = 150.dp))
            } else {
                LauncherIcon(LauncherIcons.Widgets, contentDescription = null, tint = color.copy(alpha = 0.4f), size = 32.dp)
            }
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = pickerText(color, 13, FontWeight.Medium), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${cells.first} × ${cells.second}", style = pickerText(color, 11, alpha = 0.6f))
        }
    }
}

// ---- another app's widget on the home screen ----

/**
 * Another app's widget in its cells (0.7.0, D100): the app's own view, clipped to rounded corners,
 * optionally on a glass plate ("plate" option). Its size is reported to the widget whenever it
 * changes (resize in edit mode), so it lays itself out for it. While editing, a cover takes the
 * touches so dragging and resizing work and the widget is not tapped by mistake. When the widget
 * is gone (its app uninstalled, a layout from another phone) a card says so; hold it to remove it.
 */
@Composable
internal fun AppWidgetItem(item: HomeItem, env: PageEnv, controller: HomeController) {
    val id = AppWidgets.idOf(item)
    val widgets = controller.data.widgets
    val info = remember(id) { id?.let { widgets.info(it) } }
    val shape = RoundedCornerShape(22.dp)
    if (id == null || info == null) {
        Box(
            Modifier.fillMaxSize().padding(4.dp).clip(shape).background(env.contentColor.copy(alpha = 0.1f)).padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (item.options?.get("label")?.let { "\"$it\"" } ?: "This widget") + " is not available on this phone. Hold it to remove it.",
                style = TextStyle(fontFamily = HomeTheme.font, color = env.contentColor.copy(alpha = 0.8f), fontSize = 12.sp, textAlign = TextAlign.Center, shadow = env.shadow),
            )
        }
        return
    }
    if (controller.batterySaving && !env.editMode) {
        // A4: on the trip home under 30 %, other apps' widgets rest (they update and draw on their own).
        Box(Modifier.fillMaxSize().padding(4.dp).clip(shape).background(env.contentColor.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
            Text(
                "Resting to save battery",
                style = TextStyle(fontFamily = HomeTheme.font, color = env.contentColor.copy(alpha = 0.7f), fontSize = 12.sp, textAlign = TextAlign.Center, shadow = env.shadow),
            )
        }
        return
    }
    val glassPlate = item.options?.get("plate") == "glass"
    // P7 (0.9.17): in the launcher's look unless switched off for this widget (or for all, in Settings → Widgets).
    val restyleAll by controller.data.settings.derive { get(dev.liquid.launcher.core.settings.LauncherSettings.WidgetRestyle) }
    val restyle = restyleAll && item.options?.get("restyle") != "off"
    val textArgb = env.contentColor.toArgb()
    val density = LocalDensity.current
    var view by remember(id) { mutableStateOf<AppWidgetHostView?>(null) }
    LaunchedEffect(view, restyle, textArgb) {
        val v = view as? dev.liquid.launcher.core.data.LiquidWidgetView ?: return@LaunchedEffect
        v.restyle = if (restyle) ({ root -> WidgetRestyle.apply(root, textArgb) }) else null
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(view, size) {
        val v = view ?: return@LaunchedEffect
        if (size.width <= 0 || size.height <= 0) return@LaunchedEffect
        val w = with(density) { size.width.toDp().value }
        val h = with(density) { size.height.toDp().value }
        try {
            v.updateAppWidgetSize(android.os.Bundle(), listOf(SizeF(w, h)))
        } catch (e: Exception) {
            dev.liquid.launcher.core.common.DebugLog.error("Widgets", "size report failed: $e")
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .padding(4.dp)
            .then(if (glassPlate) Modifier.liquidGlass(env.wallpaperBackdrop, shape, env.glass.style(Surfaces.Clock, env.lightBackground)) else Modifier)
            .clip(shape)
            .onSizeChanged { size = it }
            .then(
                if (env.interactive && !env.editMode) Modifier.pointerInput(id) {
                    // A hold belongs to the launcher (edit and move): once it is a long press, the widget's own
                    // touch is cancelled, so lifting the finger does not also tap the widget (as Launcher3 does).
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        var ended = false
                        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            ended = true
                        }
                        if (!ended) {
                            view?.let { v ->
                                val now = android.os.SystemClock.uptimeMillis()
                                val cancel = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
                                v.dispatchTouchEvent(cancel)
                                cancel.recycle()
                            }
                        }
                    }
                } else Modifier
            ),
    ) {
        AndroidView(
            factory = { ctx ->
                widgets.host.createView(ctx, id, info).apply {
                    setPadding(0, 0, 0, 0)
                    view = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (env.editMode || !env.interactive) {
            // Takes the touches while editing (and in previews) so the widget itself is never tapped.
            Box(Modifier.matchParentSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
        }
    }
}

// ---- built-in widgets added in 0.7.0 ----

/** The day and date in the clock's font and colour, sized to its cells. */
@Composable
internal fun DateWidget(color: Color, shadow: Shadow?, look: WidgetLook = WidgetLook.of(null)) {
    val tick = rememberMinuteTick()
    // P7 (0.9.22): the parts it shows, from its Customise sheet.
    val weekday = look.shows("weekday", true)
    val month = look.shows("month", true)
    val year = look.shows("year", false)
    val pattern = listOfNotNull(if (weekday) "EEEE" else null, "d", if (month) "MMMM" else "MM", if (year) "yyyy" else null)
        .joinToString(" ").let { if (!month) it.replace("d MM", "d/MM") else it }
    val text = remember(tick, pattern) { LocalDate.now().format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault())) }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val size = (maxHeight.value * 0.5f * look.scale).coerceIn(12f, 52f)
        Text(
            text,
            style = TextStyle(fontFamily = look.font(), color = look.color(color), fontSize = size.sp, fontWeight = look.weight, textAlign = TextAlign.Center, shadow = shadow),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The battery as a ring with the level inside, and "charging" while it charges. */
@Composable
internal fun BatteryWidget(color: Color, shadow: Shadow?, look: WidgetLook = WidgetLook.of(null)) {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        fun read(intent: Intent?) {
            if (intent == null) return
            val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            if (raw >= 0) level = raw * 100 / scale
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = read(intent)
        }
        // A sticky broadcast: registering returns the current state at once.
        read(context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val shown by animateFloatAsState(if (level < 0) 0f else level / 100f, spring(dampingRatio = 0.9f, stiffness = MotionScale.stiffness(120f)), label = "battery")
    val accent = when {
        charging -> Color(0xFF6FD08C)
        level in 0..15 -> Color(0xFFE5675C)
        else -> color
    }
    // P7 (0.9.22): its size, weight, colour, font and parts, from its Customise sheet.
    val ring = look.shows("ring", true)
    val percent = look.shows("percent", true)
    val chargingLine = look.shows("charging", true)
    val text = look.color(color)
    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight) * look.scale.coerceAtMost(1f)
        Box(Modifier.size(side), contentAlignment = Alignment.Center) {
            if (ring) androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val stroke = this.size.minDimension * 0.08f
                val inset = stroke / 2f
                drawArc(color.copy(alpha = 0.2f), 0f, 360f, false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke), style = Stroke(stroke))
                drawArc(accent, -90f, 360f * shown, false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (percent) {
                    Text(
                        if (level < 0) "–" else "$level %",
                        style = TextStyle(fontFamily = look.font(), color = text, fontSize = (side.value * 0.2f * (if (look.scale > 1f) look.scale else 1f)).sp, fontWeight = look.weight, shadow = shadow),
                    )
                }
                if (charging && chargingLine) Text("charging", style = TextStyle(fontFamily = HomeTheme.font, color = text.copy(alpha = 0.75f), fontSize = (side.value * 0.08f).coerceAtLeast(10f).sp, shadow = shadow))
            }
        }
    }
}

/** The study block now or next on its own, as under the clock (D93); a quiet line when the companion has nothing. */
@Composable
internal fun NextBlockWidget(controller: HomeController, color: Color, shadow: Shadow?, interactive: Boolean) {
    // 0.13.1 (D181): the same reimagined line as under the clock.
    val line = rememberClockLine(controller)
    val loaded by controller.data.study.loaded.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (line != null) {
            ClockLine(line, controller, color, shadow, interactive)
        } else {
            Text(
                if (loaded) "Nothing planned" else "Study companion off · tap to switch it on",
                style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.7f), fontSize = 14.sp, shadow = shadow),
                modifier = if (interactive && !loaded) Modifier.clickable { controller.setupStudy() } else Modifier,
            )
        }
    }
}
