package dev.liquid.launcher.feature.settings

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.common.WallpaperPalette
import dev.liquid.launcher.core.common.WallpaperTones
import dev.liquid.launcher.core.data.KeyboardTheme
import dev.liquid.launcher.core.data.ThemeDoc
import dev.liquid.launcher.core.data.ThemeStore
import dev.liquid.launcher.core.data.ThemedColors
import dev.liquid.launcher.core.data.WmKeyboard
import dev.liquid.launcher.core.designsystem.WallpaperStore
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.observe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/*
 * 0.12.0 (D171, D172): Settings › Drawer and search › Keyboard, Med's keyboard (WM Keyboard) and the launcher. Each part
 * was checked against WM Keyboard's own documentation: its automation intents switch layouts (LIST_LAYOUTS and
 * SET_LAYOUT), it imports a .wmtheme.json opened with it (one confirm dialog with a miniature keyboard), and its plugins
 * can hand text to another app through the share sheet but cannot start one.
 */

private fun shareDir(context: Context) = File(context.cacheDir, "share").apply { mkdirs() }

private fun shareUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.updates", file)

/** Opens [file] in WM Keyboard (its import dialog); false when it would not open it. */
private fun openInWmKeyboard(context: Context, file: File, type: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(shareUri(context, file), type)
        .setPackage(WmKeyboard.PACKAGE)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent.resolveActivity(context.packageManager) == null) return false
    return runCatching { context.startActivity(intent) }.isSuccess
}

/** The keyboard's layouts, to pick the one used while searching (Behaviour). */
@Composable
internal fun KeyboardLayoutPicker(store: SettingsStore) {
    val context = LocalContext.current
    val chosen by store.observe(LauncherSettings.KeyboardSearchLayout)
    val scope = rememberCoroutineScope()
    var layouts by remember { mutableStateOf<List<WmKeyboard.Layout>?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val installed = remember { WmKeyboard.installed(context) }

    InfoCard {
        Text("Layout while searching", style = MaterialTheme.typography.titleMedium)
        if (!installed) {
            BodyText("WM Keyboard is not on this phone.", Modifier.padding(top = 4.dp))
            return@InfoCard
        }
        BodyText(
            "Asks WM Keyboard for your layouts. It answers only with WM Keyboard › Privacy › Let other apps control the " +
                "keyboard on (\"Switch language and layout\" is on by default under it).",
            Modifier.padding(top = 4.dp, bottom = 6.dp),
        )
        OutlinedButton(onClick = {
            status = "Asking WM Keyboard…"
            scope.launch {
                val answer = WmKeyboard.send(context, "LIST_LAYOUTS")
                layouts = if (answer.ok) WmKeyboard.parseLayouts(answer.text) else null
                status = if (answer.ok) null else "WM Keyboard: " + WmKeyboard.describe(answer)
                DebugLog.event("Keyboard", "layouts asked: ${WmKeyboard.describe(answer).take(120)}")
            }
        }) { Text(if (layouts == null) "Read my layouts" else "Read them again") }
        status?.let { BodyText(it, Modifier.padding(top = 6.dp)) }
        layouts?.let { list ->
            ChoiceLine("Off: the keyboard stays as it is", chosen.isBlank()) { store.reset(LauncherSettings.KeyboardSearchLayout) }
            for (layout in list) {
                val label = layout.name + (if (layout.language.isNotEmpty()) " · ${layout.language}" else "") + (if (layout.inUse) " (in use now)" else "")
                ChoiceLine(label, chosen.equals(layout.id, ignoreCase = true)) { store.set(LauncherSettings.KeyboardSearchLayout, layout.id) }
            }
        }
        if (chosen.isNotBlank() && !WmKeyboard.inUse(context)) {
            BodyText("WM Keyboard is not the keyboard in use: nothing is switched.", Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun ChoiceLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The launcher's theme as a WM Keyboard theme: a preview, then open it in WM Keyboard, save it or share it (Look). */
@Composable
internal fun KeyboardThemeEditor(store: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wallpaperStore = remember { WallpaperStore.get(context) }
    val wallpaper by wallpaperStore.image.collectAsState()
    val tone by WallpaperTones.tone.collectAsState()
    var withWallpaper by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    val palette: WallpaperPalette? = remember(tone) {
        tone?.roles ?: runCatching { WallpaperPalette.fromSeed(ThemedColors.seed(context)) }.getOrNull()
    }
    val name = remember { "Liquid · " + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("d MMM")) }
    val doc = remember(palette) { KeyboardTheme.build(ThemeStore.doc(store), palette, name, System.currentTimeMillis()) }
    val canOpen = remember { WmKeyboard.installed(context) }

    /** The file, with the wallpaper embedded when asked (on a background thread). */
    suspend fun file(): File? = withContext(Dispatchers.IO) {
        runCatching {
            val jpeg = if (withWallpaper) wallpaperStore.currentFile?.let { jpegBase64(it) } else null
            val built = if (jpeg != null) {
                KeyboardTheme.build(ThemeStore.doc(store), palette, name, System.currentTimeMillis(), jpeg, doc.float("backgroundImageBlur") ?: 3f)
            } else doc
            val out = ThemeStore.embed(built)
            File(shareDir(context), KeyboardTheme.fileName(name)).apply { writeText(out.encode()) }
        }.onFailure { DebugLog.error("Keyboard", "keyboard theme not written: $it") }.getOrNull()
    }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val f = file()
            val ok = f != null && withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(f.readBytes()) } }.isSuccess
            }
            message = if (ok) "Saved. Open it with WM Keyboard to add it." else "Could not write the file."
        }
    }

    InfoCard {
        Text("A keyboard theme in your colours", style = MaterialTheme.typography.titleMedium)
        BodyText(
            "Your theme as WM Keyboard draws it: the colours that follow the wallpaper as they are now, your keys, glass and " +
                "material. WM Keyboard shows it once more before it adds it.",
            Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        KeyboardPreview(doc, if (withWallpaper) wallpaper else null)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Your wallpaper behind the keys", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = withWallpaper, onCheckedChange = { withWallpaper = it })
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            if (canOpen) {
                Button(onClick = {
                    scope.launch {
                        val f = file()
                        val ok = f != null && openInWmKeyboard(context, f, "application/json")
                        message = if (ok) null else "WM Keyboard did not open the theme: save it and open it from your files."
                        DebugLog.event("Keyboard", if (ok) "keyboard theme \"$name\" opened in WM Keyboard" else "WM Keyboard would not open the keyboard theme")
                    }
                }) { Text("Open in WM Keyboard") }
                Spacer(Modifier.width(8.dp))
            }
            OutlinedButton(onClick = { save.launch(KeyboardTheme.fileName(name)) }) { Text("Save") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                scope.launch {
                    val f = file() ?: return@launch
                    val send = Intent(Intent.ACTION_SEND).setType("application/json")
                        .putExtra(Intent.EXTRA_STREAM, shareUri(context, f))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { context.startActivity(Intent.createChooser(send, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }) { Text("Share") }
        }
        if (!canOpen) BodyText("WM Keyboard is not on this phone: save or share the file.", Modifier.padding(top = 6.dp))
        message?.let { BodyText(it, Modifier.padding(top = 6.dp)) }
    }
}

/** A small keyboard in the theme's colours, over the wallpaper (blurred as the keyboard blurs it) or its board colour. */
@Composable
private fun KeyboardPreview(doc: ThemeDoc, wallpaper: androidx.compose.ui.graphics.ImageBitmap?) {
    val board = Color(KeyboardTheme.argb(doc, "boardBackground", 0xFF15171AL)).copy(alpha = 1f)
    val key = Color(KeyboardTheme.argb(doc, "keyBackground", 0x33FFFFFFL))
    val keyText = Color(KeyboardTheme.argb(doc, "keyText", 0xFFEEEEEEL))
    val modifier = Color(KeyboardTheme.argb(doc, "modifierKeyBackground", KeyboardTheme.argb(doc, "keyBackground", 0x33FFFFFFL).toLong() and 0xFFFFFFFFL))
    val enter = Color(KeyboardTheme.argb(doc, "enterKeyBackground", KeyboardTheme.argb(doc, "accent", 0xFF5577FFL).toLong() and 0xFFFFFFFFL))
    val pill = doc.string("keyShape") == "PILL"
    val blurDp = ((doc.float("backgroundImageBlur") ?: 3f) * 3f).coerceIn(0f, 24f)
    Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)).background(board)) {
        if (wallpaper != null) {
            Image(wallpaper, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(blurDp.dp))
        }
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val rows = listOf(10, 9, 9, 5)
            val gap = 4.dp.toPx()
            val rowH = (size.height - gap * (rows.size - 1)) / rows.size
            val unit = (size.width - gap * 9) / 10f
            rows.forEachIndexed { r, count ->
                val y = r * (rowH + gap)
                val widths: List<Float> = when (r) {
                    2 -> listOf(1.4f) + List(7) { 1f } + listOf(1.4f)
                    3 -> listOf(1.4f, 1f, 4.6f, 1f, 1.4f)
                    else -> List(count) { 1f }
                }
                val total = widths.sum() * unit + gap * (widths.size - 1)
                var x = (size.width - total) / 2f
                widths.forEachIndexed { i, w ->
                    val wpx = w * unit
                    val isEnter = r == 3 && i == widths.lastIndex
                    val isModifier = (r == 2 && (i == 0 || i == widths.lastIndex)) || (r == 3 && !isEnter && i != 2)
                    val fill = when {
                        isEnter -> enter
                        isModifier -> modifier
                        else -> key
                    }
                    val radius = if (pill) minOf(wpx, rowH) / 2f else 6.dp.toPx()
                    drawRoundRect(fill, Offset(x, y), Size(wpx, rowH), CornerRadius(radius, radius))
                    if (!isEnter && !isModifier && r < 3) {
                        drawCircle(keyText.copy(alpha = 0.85f), radius = rowH * 0.09f, center = Offset(x + wpx / 2f, y + rowH / 2f))
                    }
                    x += wpx + gap
                }
            }
        }
    }
}

/** The wallpaper as a JPEG of at most 1440 px across, base64, as WM Keyboard embeds its background pictures. */
private fun jpegBase64(source: File): String? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(source.path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 1440) sample *= 2
    val bitmap = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val out = ByteArrayOutputStream()
    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
    bitmap.recycle()
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

/** The Liquid plugin for WM Keyboard (More). */
@Composable
internal fun KeyboardPluginEditor() {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    val installed = remember { WmKeyboard.installed(context) }
    InfoCard {
        Text("Liquid in WM Keyboard", style = MaterialTheme.typography.titleMedium)
        BodyText(
            "A plugin for WM Keyboard's Plugins tool: type or paste in its box and press Search or Ask Claude, then pick " +
                "\"Search in Liquid\" in the share sheet (WM Keyboard's plugins cannot open an app themselves). Turn on WM " +
                "Keyboard › Tools › Plugins › Manage plugins › Allow plugins first.",
            Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        if (installed) {
            Button(onClick = {
                val ok = runCatching {
                    val f = File(shareDir(context), "liquid.wmplugin")
                    context.assets.open("wmkeyboard/liquid.wmplugin").use { input -> f.outputStream().use { input.copyTo(it) } }
                    openInWmKeyboard(context, f, "application/octet-stream")
                }.getOrDefault(false)
                message = if (ok) null else "WM Keyboard did not open the plugin. Its install steps are in the launcher's " +
                    "repository, integrations/wmkeyboard."
                DebugLog.event("Keyboard", if (ok) "Liquid plugin opened in WM Keyboard" else "WM Keyboard would not open the Liquid plugin")
            }) { Text("Install the Liquid plugin") }
        } else {
            BodyText("WM Keyboard is not on this phone.")
        }
        message?.let { BodyText(it, Modifier.padding(top = 6.dp)) }
    }
}
