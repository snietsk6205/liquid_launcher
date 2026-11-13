package dev.liquid.launcher.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
    return entries.mapNotNull { entry ->
        when (entry) {
            MenuEntry.Info -> MenuRow(LauncherIcons.Info, "App info") { apps.showAppInfo(app) }
            MenuEntry.Home -> if (target.itemId != null) {
                MenuRow(LauncherIcons.RemoveCircle, "Remove from home") { controller.removeFromHome(target.itemId) }
            } else {
                MenuRow(LauncherIcons.AddCircle, "Add to home") { controller.addToHome(app.ref) }
            }
            MenuEntry.Rename -> MenuRow(LauncherIcons.Edit, "Rename") { controller.renaming = app }
            MenuEntry.Tags -> MenuRow(LauncherIcons.Label, "Tags") { controller.tagging = app }
            MenuEntry.Icon -> MenuRow(LauncherIcons.Apps, "Change icon") { controller.iconPicking = app }
            MenuEntry.Swipe -> target.itemId?.let { id ->
                MenuRow(LauncherIcons.ArrowUp, "Swipe actions") { controller.swipeEditing = id }
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
    val color = if (glass.lightGlass(Surfaces.Menu, lightBackground)) Color(0xFF16181C) else Color.White
    val shape = RoundedCornerShape(26.dp)
    val appear = remember(target) { Animatable(0f) }
    LaunchedEffect(target) { appear.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 700f)) }
    var above by remember(target) { mutableStateOf(true) }

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
                            val p = appear.value
                            alpha = p.coerceIn(0f, 1f)
                            scaleX = 0.85f + 0.15f * p
                            scaleY = 0.85f + 0.15f * p
                            transformOrigin = TransformOrigin(0.5f, if (above) 1f else 0f)
                        }
                        .liquidGlass(backdrop, shape, glass.style(Surfaces.Menu, lightBackground))
                        .clip(shape)
                        .padding(vertical = 8.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        AppIconImage(target.app.icon, Modifier.size(28.dp), null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            target.app.label,
                            style = TextStyle(color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(color.copy(alpha = 0.15f)))
                    for (row in rows) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    controller.menu = null
                                    row.onClick()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            LauncherIcon(row.icon, contentDescription = null, tint = color, size = 20.dp)
                            Spacer(Modifier.width(14.dp))
                            Text(row.label, style = TextStyle(color = color, fontSize = 15.sp))
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val card = measurables.first().measure(Constraints())
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
            Column {
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
 */
@Composable
fun TagsDialog(app: AppEntry, controller: HomeController) {
    val org by controller.data.organisation.value.collectAsState()
    val apps by controller.data.apps.apps.collectAsState()
    val current = apps.firstOrNull { it.ref == app.ref } ?: app
    var newName by remember(app) { mutableStateOf("") }
    var asTab by remember(app) { mutableStateOf(true) }
    fun addNew() {
        val tag = controller.data.organisation.tagNamed(newName) ?: return
        controller.data.apps.setTag(app.ref, tag.id, true)
        if (asTab) {
            controller.data.organisation.update { dev.liquid.launcher.core.data.OrganisationOps.addTab(it, tag.name, "tag:${tag.id}") }
        }
        newName = ""
    }
    AlertDialog(
        onDismissRequest = { controller.tagging = null },
        title = { Text("Tags for ${app.label}") },
        text = {
            Column {
                if (org.tags.isEmpty()) {
                    Text("No tags yet. Type one below, for example Study, Media or Tools.")
                }
                for (tag in org.tags) {
                    val on = tag.id in current.tags
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { controller.data.apps.setTag(app.ref, tag.id, !on) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(checked = on, onCheckedChange = { controller.data.apps.setTag(app.ref, tag.id, it) })
                        Text(tag.name)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("New tag") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { addNew() }),
                )
                Row(
                    Modifier.fillMaxWidth().clickable { asTab = !asTab },
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Checkbox(checked = asTab, onCheckedChange = { asTab = it })
                    Text("Also add it as a drawer tab")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (newName.isNotBlank()) addNew()
                controller.tagging = null
            }) { Text(if (newName.isNotBlank()) "Add and close" else "Done") }
        },
    )
}

