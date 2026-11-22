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
import androidx.compose.ui.graphics.asImageBitmap
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
    val base = remember(baseVersion) { baseKeyboardTheme(context) }
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

/**
 * 0.13.1 (D180): Settings › Keyboard › Look, "Your wallpaper behind the keys": a preview of the picture in the look
 * chosen with the keys over it, the three things to do once (WM Keyboard's Photos permission, its remote control, the two
 * themes), each with its state, and a button that does the hand-over now. Shown while the switch above is on.
 */
@Composable
internal fun KeyboardWallpaperEditor(store: SettingsStore) {
    val on by store.observe(LauncherSettings.KeyboardWallpaper)
    if (!on) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val look by store.observe(LauncherSettings.KeyboardWallpaperLook)
    val last by store.observe(LauncherSettings.KeyboardWallpaperLast)
    val wallpaper by remember { WallpaperStore.get(context) }.image.collectAsState()
    val installed = remember { WmKeyboard.installed(context) }
    var canRead by remember { mutableStateOf(KeyboardWallpaper.keyboardCanRead(context)) }
    var control by remember { mutableStateOf<WmKeyboard.Answer?>(null) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // The picture in the look chosen, made the way the hand-over makes it.
    val picture by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, wallpaper, look) {
        value = KeyboardWallpaper.picture(context, store, look)?.asImageBitmap()
    }
    // The permissions are given in another app: checked again every second while this card shows.
    androidx.compose.runtime.LaunchedEffect(installed) {
        if (!installed) return@LaunchedEffect
        while (true) {
            canRead = KeyboardWallpaper.keyboardCanRead(context)
            control = WmKeyboard.send(context, "LIST_LAYOUTS", timeoutMs = 1_500)
            kotlinx.coroutines.delay(3_000)
        }
    }
    // Turned on: both pictures are written at once (each theme has its picture from the start). The look changed: the
    // hand-over, so WM Keyboard shows the new look now.
    val firstLook = remember { arrayOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(look) {
        if (firstLook[0] == null || firstLook[0] == look) {
            firstLook[0] = look
            KeyboardWallpaper.writeBoth(context, store)?.let { message = it }
        } else {
            firstLook[0] = look
            message = KeyboardWallpaper.follow(context, store, "its look changed")
        }
    }

    InfoCard {
        Text("Your wallpaper behind the keys", style = MaterialTheme.typography.titleMedium)
        BodyText(
            "WM Keyboard follows your wallpaper's colours by itself, but Android does not let it read the picture. The " +
                "launcher hands it the part of your wallpaper the keyboard covers, every time the wallpaper changes.",
            Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        val base = remember { baseKeyboardTheme(context) }
        val doc = remember(base, wallpaper) { base?.let { KeyboardTheme.withPicture(KeyboardTheme.build(it, ThemeStore.doc(store), null, "Preview", 0L), "") } }
        if (doc != null) KeyboardPreview(doc, picture, sharp = true)
        if (wallpaper == null) BodyText("Your wallpaper now is not the launcher's own: set one in the launcher's wallpaper screen.", Modifier.padding(top = 6.dp))
        if (!installed) {
            BodyText("WM Keyboard is not on this phone.", Modifier.padding(top = 8.dp))
            return@InfoCard
        }

        Text("Once", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        SetupStep(
            1, canRead,
            if (canRead) "WM Keyboard may read the picture." else "Let WM Keyboard read the picture: its Permissions › Photos and videos › Allow all.",
        ) {
            OutlinedButton(onClick = {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + WmKeyboard.PACKAGE))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }) { Text("Open WM Keyboard's app info") }
        }
        val allowed = control?.ok == true
        SetupStep(
            2, allowed,
            when {
                allowed -> "The launcher may switch WM Keyboard's theme."
                control == null -> "Checking whether the launcher may switch WM Keyboard's theme…"
                else -> "Turn on WM Keyboard › Privacy › Let other apps control the keyboard (and Change the theme under Allowed actions). WM Keyboard says: " + WmKeyboard.describe(control!!)
            },
        ) {
            OutlinedButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("wmkeyboard://settings/privacy"))
                    .setPackage(WmKeyboard.PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (runCatching { context.startActivity(intent) }.isFailure) message = "Open WM Keyboard › Privacy yourself."
            }) { Text("Open WM Keyboard's privacy") }
        }
        SetupStep(
            3, null,
            "Add both themes, \"${KeyboardWallpaper.themeName(0)}\" and \"${KeyboardWallpaper.themeName(1)}\" (WM Keyboard asks to confirm each). " +
                "The launcher switches between them, so WM Keyboard reads each new picture. Add them again after you change the theme above.",
        ) {
            Row {
                for (slot in 0 until KeyboardWallpaper.SLOTS) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            working = true
                            val error = KeyboardWallpaper.writeBoth(context, store)
                            val doc2 = KeyboardWallpaper.theme(context, store, slot)
                            val file = doc2?.let {
                                withContext(Dispatchers.IO) {
                                    runCatching { File(shareDir(context), KeyboardTheme.fileName(KeyboardWallpaper.themeName(slot))).apply { writeText(it.encode()) } }.getOrNull()
                                }
                            }
                            val ok = file != null && openInWmKeyboard(context, file, "application/json")
                            // WM Keyboard shows a theme once it is added.
                            if (ok) store.set(LauncherSettings.KeyboardWallpaperSlot, slot)
                            message = error ?: if (ok) null else "WM Keyboard did not open the theme."
                            DebugLog.event("Keyboard", "wallpaper behind the keys: \"${KeyboardWallpaper.themeName(slot)}\" " + if (ok) "opened in WM Keyboard" else "not opened")
                            working = false
                        }
                    }, enabled = !working) { Text("Add ${slot + 1}") }
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Button(onClick = {
                scope.launch {
                    working = true
                    message = KeyboardWallpaper.follow(context, store, "tried from Settings")
                    working = false
                }
            }, enabled = !working && wallpaper != null) { Text("Try it now") }
        }
        val shown = message ?: last.ifBlank { null }
        shown?.let { BodyText("Last: $it", Modifier.padding(top = 6.dp)) }
    }
}

/** One thing to do once: its number, whether it is done ([done]; null: cannot be checked), what to do and its button. */
@Composable
private fun SetupStep(number: Int, done: Boolean?, text: String, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
        Text(
            when (done) {
                true -> "✓"
                false -> "$number"
                null -> "$number"
            },
            style = MaterialTheme.typography.titleSmall,
            color = if (done == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(22.dp),
        )
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            BodyText(text)
            if (done != true) Box(Modifier.padding(top = 4.dp)) { action() }
        }
    }
}

/**
 * A small keyboard in the theme's colours, over the wallpaper (blurred as the keyboard blurs it) or its board colour.
 * 0.13.1: [sharp] draws [wallpaper] as it is (a picture already made in its look) under the board's own colour.
 */
@Composable
private fun KeyboardPreview(doc: ThemeDoc, wallpaper: androidx.compose.ui.graphics.ImageBitmap?, sharp: Boolean = false) {
    if (sharp) {
        Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF15171A))) {
            if (wallpaper != null) Image(wallpaper, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color(KeyboardTheme.argb(doc, "boardBackground", 0x00000000L))))
            KeyboardKeys(doc)
        }
        return
    }
    val board = Color(KeyboardTheme.argb(doc, "boardBackground", 0xFF15171AL)).copy(alpha = 1f)
    val blurDp = ((doc.float("backgroundImageBlur") ?: 3f) * 3f).coerceIn(0f, 24f)
    Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)).background(board)) {
        if (wallpaper != null) {
            Image(wallpaper, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(blurDp.dp))
        }
        KeyboardKeys(doc)
    }
}

/** The preview's keys, in the theme's colours and shape. */
@Composable
private fun KeyboardKeys(doc: ThemeDoc) {
    val key = Color(KeyboardTheme.argb(doc, "keyBackground", 0x33FFFFFFL))
    val keyText = Color(KeyboardTheme.argb(doc, "keyText", 0xFFEEEEEEL))
    val modifier = Color(KeyboardTheme.argb(doc, "modifierKeyBackground", KeyboardTheme.argb(doc, "keyBackground", 0x33FFFFFFL).toLong() and 0xFFFFFFFFL))
    val enter = Color(KeyboardTheme.argb(doc, "enterKeyBackground", KeyboardTheme.argb(doc, "accent", 0xFF5577FFL).toLong() and 0xFFFFFFFFL))
    val pill = doc.string("keyShape") == "PILL"
    run {
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
