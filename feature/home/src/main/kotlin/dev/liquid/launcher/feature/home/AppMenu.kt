package dev.liquid.launcher.feature.home

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.data.AppEntry
import dev.liquid.launcher.core.data.MenuEntry
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.liquidGlass
import kotlin.math.roundToInt

private data class MenuRow(val icon: Int, val label: String, val onClick: () -> Unit)

private fun rowsFor(target: MenuTarget, entries: List<MenuEntry>, controller: HomeController): List<MenuRow> {
    val app = target.app
    val apps = controller.data.apps
    // W6: in the drawer, "Select" starts picking apps for one action on all of them.
    val select = if (target.itemId == null && target.folderId == null && controller.drawerOpen) {
        listOf(MenuRow(LauncherIcons.CheckCircle, "Select") { controller.startSelecting(app.ref) })
    } else {
        emptyList()
    }
    return select + entries.mapNotNull { entry ->
        when (entry) {
            MenuEntry.Shortcuts -> null // drawn as its own block, loaded from the app
            MenuEntry.Info -> MenuRow(LauncherIcons.Info, "App info") { apps.showAppInfo(app) }
            MenuEntry.Home -> if (target.folderId != null) {
                MenuRow(LauncherIcons.RemoveCircle, "Take out of the folder") { controller.takeFromFolder(target.folderId, app.ref) }
            } else if (target.itemId != null) {
                MenuRow(LauncherIcons.RemoveCircle, "Remove from home") { controller.removeFromHome(target.itemId) }
            } else {
                MenuRow(LauncherIcons.AddCircle, "Add to home") { controller.addToHome(app.ref) }
            }
            MenuEntry.Rename -> MenuRow(LauncherIcons.Edit, "Rename") { controller.renaming = app }
            MenuEntry.Tags -> MenuRow(LauncherIcons.Label, "Tags") { controller.tagging = app }
            MenuEntry.Icon -> MenuRow(LauncherIcons.Apps, "Change icon") { controller.iconPicking = app }
            MenuEntry.Swipe -> target.itemId?.let { id ->
                MenuRow(LauncherIcons.ArrowUp, "Gestures on this icon") { controller.swipeEditing = id }
            }
            MenuEntry.Hide -> if (app.hidden) {
                MenuRow(LauncherIcons.Visibility, "Show in drawer") { apps.setHidden(app.ref, false) }
            } else {
                MenuRow(LauncherIcons.VisibilityOff, "Hide from drawer") { apps.setHidden(app.ref, true) }
            }
            MenuEntry.Uninstall -> MenuRow(LauncherIcons.Delete, "Uninstall") { apps.uninstall(app) }
        }
    }
}

/** Glass card next to the pressed icon. Tapping outside closes it. */
@Composable
fun AppMenu(
    target: MenuTarget,
    entries: List<MenuEntry>,
    controller: HomeController,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
) {
    val rows = remember(target, entries, target.app.hidden) { rowsFor(target, entries, controller) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val shortcuts by androidx.compose.runtime.produceState(emptyList<dev.liquid.launcher.core.data.StaticShortcut>(), target) {
        if (MenuEntry.Shortcuts in entries && !target.app.isWorkProfile) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                dev.liquid.launcher.core.data.StaticShortcuts.load(context, target.app.component).take(4)
            }
        }
    }
    val color = menuContentColor(glass, lightBackground)
    val shape = RoundedCornerShape(26.dp)
    // Grows out of the pressed icon like a drop (0.8.0, D116): width and height on springs a little
    // out of step, the rows arriving one after another; on closing it shrinks back into the icon.
    val appear = remember(target) { Animatable(0f) }
    val wide = remember(target) { Animatable(0f) }
    val tall = remember(target) { Animatable(0f) }
    LaunchedEffect(target) {
        launch { appear.animateTo(1f, Motion.pop()) }
        launch { wide.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = MotionScale.stiffness(620f))) }
        tall.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = MotionScale.stiffness(430f)))
    }
    val presence = LocalPresence.current
    var above by remember(target) { mutableStateOf(true) }
    var origin by remember(target) { mutableStateOf(TransformOrigin(0.5f, 1f)) }
    fun Modifier.arriving(index: Int): Modifier = graphicsLayer {
        val d = 0.05f * index.coerceAtMost(8)
        val p = ((appear.value - d) / (1f - d)).coerceIn(0f, 1f)
        alpha = p
        translationY = (1f - p) * (if (above) 10.dp.toPx() else -10.dp.toPx())
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(target) { detectTapGestures { controller.menu = null } }
    ) {
        Layout(
            content = {
                Column(
                    Modifier
                        .width(248.dp)
                        .graphicsLayer {
                            val leave = presence.value.coerceIn(0f, 1f)
                            alpha = (appear.value * 3f).coerceIn(0f, 1f)
                            scaleX = (0.16f + 0.84f * wide.value) * (0.3f + 0.7f * leave)
                            scaleY = (0.1f + 0.9f * tall.value) * (0.3f + 0.7f * leave)
                            transformOrigin = origin
                        }
                        .menuSurface(backdrop, shape, glass, lightBackground)
                        .clip(shape)
                        // W2: a long menu (many shortcuts and rows) scrolls inside its card.
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                ) {
                    var rowIndex = 0
                    Row(Modifier.fillMaxWidth().arriving(rowIndex++).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        AppIcon(target.app, false, Modifier.size(28.dp), null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            target.app.label,
                            style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(color.copy(alpha = 0.15f)))
                    for (shortcut in shortcuts) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .arriving(rowIndex++)
                                .squishClickable {
                                    controller.menu = null
                                    controller.openShortcut(shortcut)
                                }
                                .padding(horizontal = 16.dp, vertical = menuRowPadding - 2.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            AppIconImage(shortcut.icon ?: target.app.icon, Modifier.size(22.dp), null)
                            Spacer(Modifier.width(14.dp))
                            Text(shortcut.label, style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (shortcuts.isNotEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(color.copy(alpha = 0.15f)))
                    }
                    for (row in rows) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .arriving(rowIndex++)
                                .squishClickable {
                                    controller.menu = null
                                    row.onClick()
                                }
                                .padding(horizontal = 16.dp, vertical = menuRowPadding),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            LauncherIcon(row.icon, contentDescription = null, tint = color, size = 20.dp)
                            Spacer(Modifier.width(14.dp))
                            Text(row.label, style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 15.sp))
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            // W2: at most 85 % of the screen high; the card scrolls beyond that.
            val card = measurables.first().measure(Constraints(maxHeight = (constraints.maxHeight * SheetMaxFraction).roundToInt()))
            val margin = 12.dp.roundToPx()
            val anchor = target.anchor
            val x = (anchor.center.x - card.width / 2f).roundToInt()
                .coerceIn(margin, (constraints.maxWidth - card.width - margin).coerceAtLeast(margin))
            val spaceAbove = anchor.top - margin
            val fitsAbove = spaceAbove >= card.height + 48.dp.roundToPx()
            above = fitsAbove
            val y = if (fitsAbove) {
                (anchor.top - card.height - 8.dp.roundToPx()).roundToInt()
            } else {
                (anchor.bottom + 8.dp.roundToPx()).roundToInt()
                    .coerceAtMost(constraints.maxHeight - card.height - margin)
            }
            // The card grows from the icon's centre, wherever the card sits.
            origin = TransformOrigin(
                ((anchor.center.x - x) / card.width.coerceAtLeast(1)).coerceIn(-0.5f, 1.5f),
                ((anchor.center.y - y) / card.height.coerceAtLeast(1)).coerceIn(-0.5f, 1.5f),
            )
            layout(constraints.maxWidth, constraints.maxHeight) { card.place(x, y) }
        }
    }
}

@Composable
fun RenameDialog(app: AppEntry, controller: HomeController) {
    var text by remember(app) { mutableStateOf(app.label) }
    AlertDialog(
        onDismissRequest = { controller.renaming = null },
        title = { Text("Rename") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
                if (app.label != app.systemLabel) {
                    Text("Original name: ${app.systemLabel}", modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                controller.data.apps.rename(app.ref, text.takeIf { it.trim() != app.systemLabel })
                controller.renaming = null
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (app.label != app.systemLabel) {
                    TextButton(onClick = {
                        controller.data.apps.rename(app.ref, null)
                        controller.renaming = null
                    }) { Text("Reset") }
                }
                TextButton(onClick = { controller.renaming = null }) { Text("Cancel") }
            }
        },
    )
}

/**
 * Tags of one app: tick existing tags, or type a new one. A new tag can become a drawer tab
 * at once, which is the quickest way to build pages such as Study, Media or Tools.
 *
 * W2 (0.10.0): a sheet instead of a dialog. The list scrolls and the field for a new tag stays fixed
 * under it, above the keyboard, however many tags there are (from about nine, the dialog's field was out
 * of reach). A search narrows the list once there are more than eight.
 */
@Composable
fun AppTagsSheet(app: AppEntry, controller: HomeController, glass: GlassConfig, lightBackground: Boolean, backdrop: Backdrop) {
    val org by controller.data.organisation.value.collectAsState()
    val apps by controller.data.apps.apps.collectAsState()
    val current = apps.firstOrNull { it.ref == app.ref } ?: app
    var newName by remember(app) { mutableStateOf("") }
    var asTab by remember(app) { mutableStateOf(true) }
    var query by remember(app) { mutableStateOf("") }
    fun addNew() {
        val tag = controller.data.organisation.tagNamed(newName) ?: return
        controller.data.apps.setTag(app.ref, tag.id, true)
        if (asTab) {
            controller.data.organisation.update { dev.liquid.launcher.core.data.OrganisationOps.addTab(it, tag.name, "tag:${tag.id}") }
        }
        newName = ""
    }
    val shown = org.tags.sortedBy { it.name.lowercase() }.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    LiquidSheet(
        onDismiss = { controller.tagging = null },
        glass = glass,
        lightBackground = lightBackground,
        backdrop = backdrop,
        title = "Tags for ${app.label}",
        header = if (org.tags.size > 8) ({
            SheetField(query, { query = it }, "Find a tag", Modifier.padding(horizontal = 14.dp).padding(bottom = 6.dp).fillMaxWidth(), icon = LauncherIcons.Search)
        }) else null,
        footer = {
            Row(Modifier.padding(horizontal = 14.dp).padding(top = 10.dp).fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                SheetField(newName, { newName = it }, "New tag, for example Study", Modifier.weight(1f), icon = LauncherIcons.Label, onDone = { addNew() })
                if (newName.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    GlassButton(null, LocalSheetContent.current, icon = LauncherIcons.Add, contentDescription = "Add tag") { addNew() }
                }
            }
            SheetCheckRow("Also add it as a drawer tab", asTab) { asTab = !asTab }
        },
        actions = {
            Spacer(Modifier.weight(1f))
            GlassButton(
                if (newName.isNotBlank()) "Add and close" else "Done", LocalSheetContent.current,
                emphasis = ControlEmphasis.Main,
            ) {
                if (newName.isNotBlank()) addNew()
                controller.tagging = null
            }
        },
    ) {
        if (org.tags.isEmpty()) {
            SheetText("No tags yet. Type one below, for example Study, Media or Tools.", Modifier.padding(vertical = 8.dp))
        }
        for (tag in shown) {
            val on = tag.id in current.tags
            SheetCheckRow(tag.name, on) { controller.data.apps.setTag(app.ref, tag.id, !on) }
        }
    }
}

