package dev.liquid.launcher.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
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

/**
 * 0.13.0 (D177): a search mode in WM Keyboard (Behaviour), in place of 0.12.0's layout switch: a mode bound to the
 * launcher applies before the keyboard draws, where switching layouts through its intents re-drew the keyboard while it
 * slid in (and needed "Let other apps control the keyboard"). Search already asks for no autocorrection and a Search key.
 */
@Composable
internal fun KeyboardModeGuide() {
    val context = LocalContext.current
    val installed = remember { WmKeyboard.installed(context) }
    var message by remember { mutableStateOf<String?>(null) }
    InfoCard {
        Text("A keyboard made for search", style = MaterialTheme.typography.titleMedium)
        BodyText(
            "Search already asks your keyboard for no autocorrection and a Search key. To give it its own look and keys " +
                "in search (a numbers row, no emoji row, no suggestions…), make a mode in WM Keyboard once:\n" +
                "1. Keyboard modes › New mode, and name it Search.\n" +
                "2. Under What this mode changes, set what you want for search.\n" +
                "3. Under Activate automatically for, add the app Liquid Launcher.\n" +
                "It then applies before the keyboard appears, every time search opens.",
            Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        if (installed) {
            OutlinedButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("wmkeyboard://settings/modes"))
                    .setPackage(WmKeyboard.PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                message = if (runCatching { context.startActivity(intent) }.isSuccess) null
                else "WM Keyboard did not open its modes: open WM Keyboard › Keyboard modes."
            }) { Text("Open WM Keyboard's modes") }
        } else {
            BodyText("WM Keyboard is not on this phone.")
        }
        message?.let { BodyText(it, Modifier.padding(top = 6.dp)) }
    }
}

/** The base theme: a file chosen in Settings, else the one Med made in WM Keyboard (bundled). */
private fun baseTheme(context: Context): ThemeDoc? {
    val own = File(context.filesDir, "keyboard-base.wmtheme.json")
    val text = if (own.isFile) runCatching { own.readText() }.getOrNull()
    else runCatching { context.assets.open("wmkeyboard/template.wmtheme.json").bufferedReader().use { it.readText() } }.getOrNull()
    return text?.let { ThemeDoc.parse(it) }
}

/** The launcher's keys on Med's own WM Keyboard theme, following the wallpaper: a preview, open, save, share (Look). */
@Composable
internal fun KeyboardThemeEditor(store: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wallpaperStore = remember { WallpaperStore.get(context) }
    val wallpaper by wallpaperStore.image.collectAsState()
    val tone by WallpaperTones.tone.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    var baseVersion by remember { mutableStateOf(0) }
    val ownBase = remember(baseVersion) { File(context.filesDir, "keyboard-base.wmtheme.json").isFile }
    val palette: WallpaperPalette? = remember(tone) {
        tone?.roles ?: runCatching { WallpaperPalette.fromSeed(ThemedColors.seed(context)) }.getOrNull()
    }
    val name = "Liquid"
    val base = remember(baseVersion) { baseTheme(context) }
    val doc = remember(palette, base) { base?.let { KeyboardTheme.build(it, ThemeStore.doc(store), palette, name, System.currentTimeMillis()) } }
    val canOpen = remember { WmKeyboard.installed(context) }

    /** The theme file, ready to hand over (on a background thread). */
    suspend fun file(): File? = withContext(Dispatchers.IO) {
        val built = doc ?: return@withContext null
        runCatching {
            File(shareDir(context), KeyboardTheme.fileName(name)).apply { writeText(ThemeStore.embed(built).encode()) }
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
    val pickBase = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            val parsed = text?.let { ThemeDoc.parse(it) }
            if (parsed == null || !KeyboardTheme.recognisable(parsed)) {
                message = "That file is not a WM Keyboard theme (.wmtheme.json)."
            } else {
                withContext(Dispatchers.IO) { File(context.filesDir, "keyboard-base.wmtheme.json").writeText(text) }
                baseVersion++
                message = "\"${parsed.name}\" is the base now."
                DebugLog.event("Keyboard", "keyboard theme base: ${parsed.name}")
            }
        }
    }

    InfoCard {
        Text("A keyboard theme that follows your wallpaper", style = MaterialTheme.typography.titleMedium)
        BodyText(
            "Your own WM Keyboard theme as the base (its board, toolbar and chips), with the launcher's keys and popups. " +
                "Follow wallpaper colors is on: WM Keyboard recolours it from your wallpaper by itself at every change, so " +
                "you open it in WM Keyboard once.",
            Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        if (doc != null) KeyboardPreview(doc, wallpaper)
        BodyText(
            "Base: " + (base?.name?.trim()?.ifEmpty { null } ?: "your theme") + if (ownBase) " (chosen here)" else " (the one you sent)",
            Modifier.padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            OutlinedButton(onClick = { pickBase.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) { Text("Use another file") }
            if (ownBase) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    File(context.filesDir, "keyboard-base.wmtheme.json").delete()
                    baseVersion++
                }) { Text("Back to yours") }
            }
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
