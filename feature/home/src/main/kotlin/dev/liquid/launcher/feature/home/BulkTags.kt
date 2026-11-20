package dev.liquid.launcher.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.data.AppRef
import dev.liquid.launcher.core.data.Tag
import dev.liquid.launcher.core.designsystem.LauncherIcons

/*
 * W6 (0.10.1): tagging many apps at once. In the drawer, an app's long press → Select starts picking: taps pick and
 * unpick, and a bar at the top offers Tag, Untag, Hide and Add to home for all of them. A tag of the drawer's tag
 * list opens its own sheet with every app and a check for each, with a search.
 */

/** The bar over the drawer while apps are picked. */
@Composable
internal fun SelectionBar(controller: HomeController, picked: Set<AppRef>, glass: GlassConfig, lightBackground: Boolean, backdrop: Backdrop) {
    val color = sheetContentColor(glass, lightBackground)
    val shape = RoundedCornerShape(26.dp)
    // 0.11.1: it drops in from the top and goes back up when picking ends (it appeared and vanished in one frame).
    val appear = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { appear.animateTo(1f, Motion.enter()) }
    val presence = LocalPresence.current
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(12.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val p = appear.value * presence.value
                    alpha = p.coerceIn(0f, 1f)
                    translationY = -(1f - p) * 32.dp.toPx()
                }
                .sheetSurface(backdrop, shape, glass, lightBackground)
                .clip(shape)
                .clickable(interactionSource = null, indication = null) { }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            ProvideControls(controller, glass, null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (picked.size == 1) "1 app picked" else "${picked.size} apps picked",
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    )
                    GlassButton(null, color, icon = LauncherIcons.Close, contentDescription = "Stop picking") { controller.endSelecting() }
                }
                Text(
                    "Tap apps to pick or unpick them",
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.7f), fontSize = 12.sp),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy((8 * themeGap).dp)) {
                    GlassButton("Tag", color, icon = LauncherIcons.Label, emphasis = ControlEmphasis.Main, enabled = picked.isNotEmpty()) { controller.bulkTag = true }
                    GlassButton("Untag", color, emphasis = ControlEmphasis.Secondary, enabled = picked.isNotEmpty()) { controller.bulkTag = false }
                    GlassButton("Hide", color, icon = LauncherIcons.VisibilityOff, emphasis = ControlEmphasis.Secondary, enabled = picked.isNotEmpty()) { controller.hideSelection() }
                    GlassButton("Add to home", color, icon = LauncherIcons.Home, emphasis = ControlEmphasis.Secondary, enabled = picked.isNotEmpty()) { controller.addSelectionToHome() }
                }
            }
        }
    }
}

/** Adds a tag to every picked app ([add]), or takes one off all of them. */
@Composable
internal fun BulkTagSheet(controller: HomeController, add: Boolean, picked: Set<AppRef>, glass: GlassConfig, lightBackground: Boolean, backdrop: Backdrop) {
    val org by controller.data.organisation.value.collectAsState()
    val apps by controller.data.apps.apps.collectAsState()
    val pickedApps = apps.filter { it.ref in picked }
    var newName by remember { mutableStateOf("") }
    fun done(text: String) {
        controller.toast(text)
        controller.bulkTag = null
        controller.endSelecting()
    }
    fun addNew() {
        val tag = controller.data.organisation.tagNamed(newName) ?: return
        controller.data.apps.setTag(picked, tag.id, true)
        done("${tag.name}: ${picked.size} app(s) tagged")
    }
    val tags = org.tags.sortedBy { it.name.lowercase() }.filter { add || pickedApps.any { app -> it.id in app.tags } }
    LiquidSheet(
        onDismiss = { controller.bulkTag = null },
        glass = glass,
        lightBackground = lightBackground,
        backdrop = backdrop,
        title = if (add) "Tag ${picked.size} app(s)" else "Take a tag off ${picked.size} app(s)",
        footer = if (add) ({
            Row(Modifier.padding(horizontal = 14.dp).padding(top = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SheetField(newName, { newName = it }, "New tag, for example Study", Modifier.weight(1f), icon = LauncherIcons.Label, onDone = { addNew() })
                if (newName.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    GlassButton(null, LocalSheetContent.current, icon = LauncherIcons.Add, contentDescription = "Add tag") { addNew() }
                }
            }
        }) else null,
        actions = {
            Spacer(Modifier.weight(1f))
            GlassButton("Cancel", LocalSheetContent.current, emphasis = ControlEmphasis.Secondary) { controller.bulkTag = null }
        },
    ) {
        if (tags.isEmpty()) {
            SheetText(if (add) "No tags yet. Type one below." else "None of these apps has a tag.", Modifier.padding(vertical = 8.dp))
        }
        for (tag in tags) {
            val have = pickedApps.count { tag.id in it.tags }
            SheetCheckRow(tag.name, have == pickedApps.size && have > 0, detail = "$have of ${pickedApps.size} have it") {
                controller.data.apps.setTag(picked, tag.id, add)
                done(if (add) "${tag.name}: ${picked.size} app(s) tagged" else "${tag.name} taken off ${picked.size} app(s)")
            }
        }
    }
}

/** Every app with a check for [tag]; a search narrows the list. */
@Composable
internal fun TagAppsSheet(controller: HomeController, tag: Tag, glass: GlassConfig, lightBackground: Boolean, backdrop: Backdrop) {
    val apps by controller.data.apps.apps.collectAsState()
    var query by remember(tag.id) { mutableStateOf("") }
    val shown = remember(apps, query) {
        apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }.sortedBy { it.label.lowercase() }
    }
    val count = apps.count { tag.id in it.tags }
    LiquidSheet(
        onDismiss = { controller.tagApps = null },
        glass = glass,
        lightBackground = lightBackground,
        backdrop = backdrop,
        title = "${tag.name} · $count app(s)",
        header = {
            SheetField(query, { query = it }, "Find an app", Modifier.padding(horizontal = 14.dp).padding(bottom = 6.dp).fillMaxWidth(), icon = LauncherIcons.Search)
        },
        actions = {
            Spacer(Modifier.weight(1f))
            GlassButton("Done", LocalSheetContent.current, emphasis = ControlEmphasis.Main) { controller.tagApps = null }
        },
        scroll = null,
    ) {
        val color = LocalSheetContent.current
        LazyColumn(Modifier.fillMaxWidth()) {
            items(shown, key = { it.ref.key }) { app ->
                val on = tag.id in app.tags
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { controller.data.apps.setTag(app.ref, tag.id, !on) }
                        .padding(horizontal = 18.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app, false, Modifier.size(30.dp), null)
                    Text(
                        app.label,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 15.sp),
                    )
                    ThemeCheck(on, color, 22.dp)
                }
            }
        }
    }
}
