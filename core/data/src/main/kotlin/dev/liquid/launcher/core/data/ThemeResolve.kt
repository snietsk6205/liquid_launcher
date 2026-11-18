package dev.liquid.launcher.core.data

import dev.liquid.launcher.core.common.WallpaperPalette
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/*
 * W3 (0.10.0): from a theme document to what the launcher draws ([LiquidTheme]), the way WM Keyboard resolves its
 * ThemeSpec (KbTheme, resolveMaterial): colours a theme leaves on Auto are derived from the others, a material is its
 * kind's preset with the theme's own parts over it. A colour set to follow the wallpaper takes that role's colour from
 * the wallpaper's palette and keeps its own opacity, so a see-through button stays see-through.
 */
object ThemeResolve {

    fun resolve(doc: ThemeDoc, palette: WallpaperPalette? = null, fallbackName: String = "Imported theme"): LiquidTheme {
        val r = Reader(doc, palette)
        val dark = r.bool("dark") ?: true
        // 0.10.2: with colours that follow the wallpaper, a text that follows it too or is left on Auto is checked
        // against what it sits on (its fill over the wallpaper's average colour) and becomes a dark or a light ink where
        // it would not read. A text colour you fixed stays as you set it.
        val adaptive = palette != null && doc.followed.isNotEmpty()
        val behind = palette?.let { ThemeColors.opaque(it.average.toLong() and 0xFFFFFFFFL) } ?: if (dark) 0xFF1C1D22 else 0xFFE8E8EC
        val inkDark = palette?.let { p ->
            val d = p.dark.toLong() and 0xFFFFFFFFL
            if (ThemeColors.luminance(d) <= 0.035f) ThemeColors.opaque(d) else WallpaperPalette.withHsl(p.dark, l = 0.12f).toLong() and 0xFFFFFFFFL
        } ?: 0xFF16181C
        val inkLight = palette?.let { p ->
            val l = p.light.toLong() and 0xFFFFFFFFL
            if (ThemeColors.luminance(l) >= 0.72f) ThemeColors.opaque(l) else WallpaperPalette.withHsl(p.light, l = 0.94f).toLong() and 0xFFFFFFFFL
        } ?: 0xFFF2F2F5
        /** The first of [paths] the theme sets (a text colour's source), or null when they are all on Auto. */
        fun source(vararg paths: String): String? = paths.firstOrNull { doc.color(it) != null }
        /** [text] (from [path], null: worked out) made readable on [fill], unless it is a colour you fixed. */
        fun ink(text: Long, fill: Long, path: String?): Long =
            if (!adaptive || (path != null && doc.follow(path) == null)) text
            else ThemeColors.readable(text, fill, behind, inkDark, inkLight)
        val board = r.color("boardBackground") ?: 0xFF17181C
        val key = r.color("keyBackground") ?: 0xFF303338
        val keyText = r.color("keyText") ?: 0xFFE9E9EE
        val accent = r.color("accent") ?: 0xFF8AB4F8
        val pressed = r.color("pressedKeyBackground") ?: ThemeColors.lerp(key, keyText, 0.25f)
        val stripText = r.color("suggestionText") ?: ThemeColors.legibleOn(board, listOf(keyText))
        val secondary = ThemeColors.withAlpha(stripText, 0.65f)
        val toolActive = r.color("toolCircleActiveBackground") ?: pressed
        val chipActive = r.color("chipActiveBackground") ?: toolActive
        val modifier = r.color("modifierKeyBackground") ?: 0xFF222428
        val enter = r.color("enterKeyBackground") ?: 0xFF4C8DF6
        val enterText = r.color("enterKeyText") ?: 0xFF0B1220
        val keyShape = LiquidThemes.shapeName(r.string("keyShape"), "ROUNDED", allowCircle = false, allowNone = true)
        val keyCorner = r.int("keyCornerRadiusDp") ?: 12
        val frostOn = doc.element("frost") is JsonObject && (r.bool("frost.enabled") ?: true)
        val surfaces = if (!frostOn) emptySet() else doc.strings("frost.surfaces").map { it.uppercase() }.toSet()
            .ifEmpty { if (doc.element("frost.surfaces") is JsonArray) emptySet() else ThemeFrost().surfaces }
        val frost = ThemeFrost(
            style = r.string("frost.style")?.uppercase() ?: "FROSTED",
            blurDp = (r.float("frost.backdropBlur") ?: 14f).coerceIn(0f, 25f),
            saturation = (r.float("frost.saturation") ?: 1.25f).coerceIn(0.5f, 2f),
            brightness = (r.float("frost.brightness") ?: 0f).coerceIn(-0.3f, 0.3f),
            rim = (r.float("frost.rim") ?: 0.5f).coerceIn(0f, 1f),
            rimColor = r.color("frost.rimColor"),
            grain = (r.float("frost.grain") ?: 0.06f).coerceIn(0f, 0.25f),
            refraction = (r.float("frost.refraction") ?: 0.6f).coerceIn(0f, 1f),
            dispersion = (r.float("frost.dispersion") ?: 0.25f).coerceIn(0f, 1f),
            enabled = frostOn,
            surfaces = surfaces,
            backdrop = r.string("frost.backdrop")?.uppercase() ?: "AUTO",
            fallbackOpacity = (r.float("frost.fallbackOpacity") ?: 0.92f).coerceIn(0.5f, 1f),
        )
        val scale = r.string("keyTextureScale")?.lowercase() ?: "crop"
        val texOpacity = (r.float("keyTextureOpacity") ?: 1f).coerceIn(0f, 1f)
        val keyTexture = r.string("keyTexture")
        val modTexture = r.string("keyTextureModifier") ?: keyTexture
        val pressedTexture = r.string("keyTexturePressed")
        val height = r.int("keyHeightDp")?.coerceIn(20, 72)
        val hint = r.color("hintText")
        fun glow(kind: String) = (r.float("${ThemeDoc.EXT}.glow.$kind") ?: 0f).coerceIn(0f, 16f)
        fun glowColor(kind: String) = r.color("${ThemeDoc.EXT}.glowColor.$kind")
        val material = r.material()

        val buttonText = ink(keyText, key, "keyText")
        // While pressed: your pressed text, else the text made readable on the pressed fill when that differs.
        fun pressedInk(restText: Long, textPath: String, explicit: String): Long? = r.color(explicit)
            ?: ink(r.color(textPath) ?: restText, pressed, if (doc.follow("pressedKeyBackground") != null) null else textPath).takeIf { it != restText }
        val chipActiveText = r.color("chipActiveText")?.let { ink(it, chipActive, "chipActiveText") }
            ?: ink(ThemeColors.legibleOn(chipActive, listOf(accent, keyText)), chipActive, null)
        val button = ControlTheme(
            fill = key, text = buttonText, pressed = pressed, pressedText = pressedInk(buttonText, "keyText", "pressedKeyText"),
            active = chipActive, activeText = chipActiveText,
            gradient = r.gradient("keyGradient"), border = r.color("keyBorderColor"), borderWidthDp = r.float("keyBorderWidthDp") ?: 0f,
            glowDp = glow("key"), glowColor = glowColor("key"),
            shadowDp = (r.float("keyElevationDp") ?: 0f).coerceIn(0f, 8f), shadowColor = r.color("keyShadowColor"),
            shape = keyShape, cornerDp = keyCorner, glass = "KEYS" in surfaces,
            texture = keyTexture, pressedTexture = pressedTexture, textureScale = scale, textureOpacity = texOpacity,
            heightDp = height, hint = hint, material = material != null,
        )
        val modifierTheme = button.copy(
            fill = modifier, text = ink(r.color("modifierKeyText") ?: keyText, modifier, source("modifierKeyText", "keyText")),
            cornerDp = r.int("modifierKeyCornerRadiusDp") ?: keyCorner, glass = "MODIFIER_KEYS" in surfaces,
            texture = modTexture, gradient = null,
        )
        val mainText = ink(enterText, enter, source("enterKeyText"))
        val main = ControlTheme(
            // WM Keyboard presses every key, the enter key included, in the pressed key colour.
            fill = enter, text = mainText, pressed = pressed, pressedText = pressedInk(mainText, "enterKeyText", "pressedEnterKeyText"),
            active = chipActive, activeText = button.activeText,
            border = r.color("keyBorderColor"), borderWidthDp = r.float("keyBorderWidthDp") ?: 0f,
            glowDp = glow("enter"), glowColor = glowColor("enter"),
            shadowDp = (r.float("keyElevationDp") ?: 0f).coerceIn(0f, 8f), shadowColor = r.color("keyShadowColor"),
            shape = keyShape, cornerDp = r.int("actionKeyCornerRadiusDp") ?: keyCorner, glass = "ENTER_KEY" in surfaces,
            texture = r.string("keyTextureEnter") ?: modTexture, pressedTexture = pressedTexture, textureScale = scale,
            textureOpacity = texOpacity, heightDp = height, hint = hint, material = material != null,
        )
        val chipFill = r.color("chipBackground") ?: modifier
        val chip = ControlTheme(
            fill = chipFill,
            text = ink(r.color("chipText") ?: r.color("modifierKeyText") ?: keyText, chipFill, source("chipText", "modifierKeyText", "keyText")),
            pressed = pressed, active = chipActive,
            activeText = chipActiveText,
            border = r.color("chipBorderColor"), borderWidthDp = r.float("chipBorderWidthDp") ?: 0f,
            glowDp = glow("chip"), glowColor = glowColor("chip"),
            shape = LiquidThemes.shapeName(r.string("chipShape"), "ROUNDED", allowCircle = false), cornerDp = r.int("chipCornerRadiusDp") ?: 12,
            glass = "CHIPS" in surfaces, heightDp = height, hint = hint,
        )
        val toolFill = r.color("toolCircleBackground") ?: ThemeColors.blendOver(keyText, board, 0.14f)
        val tool = ControlTheme(
            fill = toolFill,
            text = ink(r.color("toolbarIcon") ?: secondary, toolFill, source("toolbarIcon")),
            pressed = toolActive, active = toolActive,
            activeText = ink(r.color("toolCircleActiveIcon") ?: ThemeColors.legibleOn(toolActive, listOf(accent, keyText)), toolActive, source("toolCircleActiveIcon")),
            border = r.color("toolBorderColor"), borderWidthDp = r.float("toolBorderWidthDp") ?: 0f,
            glowDp = glow("tool"), glowColor = glowColor("tool"),
            shadowDp = (r.float("toolElevationDp") ?: 0f).coerceIn(0f, 8f),
            shape = LiquidThemes.shapeName(r.string("toolShape"), "CIRCLE", allowCircle = true), cornerDp = r.int("toolCircleRadiusDp") ?: 12,
            glass = "TOOLS" in surfaces, heightDp = r.int("toolWidthDp")?.coerceIn(20, 72),
        )
        val ext = ThemeDoc.EXT
        val spaceFill = r.color("$ext.space.background") ?: key
        /** The pills' text on [fill]: yours, else readable on a fill of their own, else the buttons' text. */
        fun pillText(fill: Long, ownFill: Boolean): Long {
            r.color("$ext.space.text")?.let { return ink(it, fill, "$ext.space.text") }
            return if (ownFill) ink(ThemeColors.legibleOn(ThemeColors.opaque(fill), listOf(keyText)), fill, null) else ink(keyText, fill, "keyText")
        }
        val space = button.copy(
            fill = spaceFill,
            text = pillText(spaceFill, r.color("$ext.space.background") != null),
            pressed = r.color("$ext.space.pressed") ?: pressed,
            glass = "SPACE_BAR" in surfaces, texture = r.string("keyTextureSpace") ?: keyTexture,
            glowDp = glow("space"), glowColor = glowColor("space"), heightDp = null,
            // 0.10.2: the pills are clear liquid glass; the material dresses them only when "Material on the pills" is on.
            material = button.material && (r.bool("$ext.pillMaterial") ?: false),
        )
        // 0.10.2: the search page's field keeps a light tint of its own over the pills' glass, so its text reads over the page.
        val search = r.color("$ext.space.searchBackground")?.let { f -> space.copy(fill = f, text = pillText(f, true)) }
        // Text on a mostly see-through surface reads against the wallpaper: light for a dark theme, dark for a light one;
        // 0.10.2: with colours that follow the wallpaper, against the wallpaper's own colour behind the fill.
        fun textOn(fill: Long, preferred: Long): Long =
            if (adaptive) ThemeColors.readable(preferred, fill, behind, inkDark, inkLight)
            else if (ThemeColors.alpha(fill) >= 0.55f) ThemeColors.legibleOn(ThemeColors.opaque(fill), listOf(preferred))
            else if (dark) (if (ThemeColors.luminance(preferred) > 0.4f) preferred else 0xFFF2F2F5) else (if (ThemeColors.luminance(preferred) < 0.3f) preferred else 0xFF16181C)
        val cardFill = r.color("$ext.card.background") ?: ThemeColors.withAlpha(board, 0.42f)
        val card = ControlTheme(
            fill = cardFill, text = r.color("$ext.card.text")?.let { ink(it, cardFill, "$ext.card.text") } ?: textOn(cardFill, keyText),
            pressed = pressed, active = chipActive, activeText = button.activeText,
            border = r.color("$ext.card.border"), borderWidthDp = r.float("$ext.card.borderWidthDp") ?: 0f,
            shadowDp = (r.float("cardElevationDp") ?: 0f).coerceIn(0f, 8f),
            shape = LiquidThemes.shapeName(r.string("cardShape"), "ROUNDED", allowCircle = false), cornerDp = r.int("$ext.card.cornerDp") ?: 22,
            glass = "CARDS" in surfaces,
        )
        val popupFill = r.color("popupBackground") ?: ThemeColors.blendOver(keyText, ThemeColors.opaque(board), 0.08f)
        val popupSelected = r.color("popupSelectedBackground") ?: pressed
        val popupText = r.color("popupText")?.let { ink(it, popupFill, "popupText") } ?: textOn(popupFill, keyText)
        val popup = ControlTheme(
            fill = popupFill, text = popupText, pressed = popupSelected,
            active = popupSelected, activeText = r.color("popupSelectedText") ?: ThemeColors.legibleOn(ThemeColors.opaque(popupSelected), listOf(popupText, keyText)),
            border = r.color("popupBorderColor"), borderWidthDp = r.float("popupBorderWidthDp") ?: 0f,
            shadowDp = (r.float("popupElevationDp") ?: 0f).coerceIn(0f, 12f),
            shape = LiquidThemes.shapeName(r.string("menuShape"), "ROUNDED", allowCircle = false), cornerDp = r.int("popupCornerRadiusDp") ?: 26,
            glass = "MENUS" in surfaces, texture = r.string("popupTexture"), textureScale = scale, textureOpacity = texOpacity,
            heightDp = r.int("popupHeightDp")?.coerceIn(32, 72),
        )
        val boardText = textOn(board, keyText)
        val boardTheme = ThemeBoard(
            background = board, gradient = r.gradient("boardGradient"), text = boardText,
            secondaryText = r.color("secondaryText") ?: ThemeColors.withAlpha(boardText, 0.66f),
            divider = r.color("dividerColor") ?: ThemeColors.withAlpha(boardText, 0.16f),
            image = r.string("backgroundImage"), imageOpacity = (r.float("backgroundImageOpacity") ?: 1f).coerceIn(0f, 1f),
            imageBlurDp = (r.float("backgroundImageBlur") ?: 0f).coerceIn(0f, 25f),
            shape = LiquidThemes.shapeName(r.string("popupShape"), "ROUNDED", allowCircle = false), cornerDp = r.int("popupCornerRadiusDp") ?: 32,
            glass = "POPUPS" in surfaces,
        )
        val bloom = (doc.element("touchBloom") as? JsonObject)?.let {
            ThemeBloom(
                color = r.color("touchBloom.color"),
                light = (r.float("touchBloom.light") ?: 0.6f).coerceIn(0f, 1f),
                radiusDp = (r.float("touchBloom.radiusDp") ?: 96f).coerceIn(40f, 240f),
                dip = (r.float("touchBloom.dip") ?: 0.5f).coerceIn(0f, 1f),
                wobble = (r.float("touchBloom.wobble") ?: 0.5f).coerceIn(0f, 1f),
                motion = r.string("touchBloom.motion")?.uppercase()?.takeIf { m -> ThemeFields.motions.any { it.first == m } },
            )
        }
        val particles = r.string("keyEffect")?.uppercase()?.let { kind ->
            ThemeParticles(
                kind = kind, emoji = r.string("keyEffectParam"), images = doc.strings("keyEffectImages"),
                colorMode = r.string("keyEffectColor")?.uppercase() ?: "NATURAL",
                color = r.color("keyEffectCustomColor") ?: accent,
                intensity = (r.float("keyEffectIntensity") ?: 1f).coerceIn(0.2f, 3f),
                size = (r.float("keyEffectSize") ?: 1f).coerceIn(0.4f, 3f),
                speed = (r.float("keyEffectSpeed") ?: 1f).coerceIn(0.3f, 2.5f),
                spread = (r.float("keyEffectSpread") ?: 1f).coerceIn(0.1f, 1f),
                gravity = (r.float("keyEffectGravity") ?: 1f).coerceIn(-1f, 3f),
                durationMs = (r.int("keyEffectDurationMs") ?: 650).coerceIn(200, 2500),
            )
        }
        val effect = r.string("boardEffect.kind")?.uppercase()?.let { kind ->
            ThemeEffect(
                kind = kind,
                colors = r.colors("boardEffect.colors").take(4),
                density = (r.float("boardEffect.density") ?: 0.5f).coerceIn(0f, 1f),
                speed = (r.float("boardEffect.speed") ?: 1f).coerceIn(0f, 3f),
                intensity = (r.float("boardEffect.intensity") ?: 0.6f).coerceIn(0f, 1f),
                scale = (r.float("boardEffect.scale") ?: 1f).coerceIn(0.5f, 2f),
                reactive = (r.float("boardEffect.reactive") ?: 0f).coerceIn(0f, 1f),
                halfLifeMs = (r.int("boardEffect.halfLifeMs") ?: 1200).coerceIn(300, 4000),
                orbs = (doc.element("boardEffect.orbSpots") as? JsonArray)?.mapNotNull { e ->
                    val o = e as? JsonObject ?: return@mapNotNull null
                    fun f(k: String, d: Float) = (o[k] as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: d
                    ThemeOrb(f("x", 0.5f).coerceIn(-0.2f, 1.2f), f("y", 0.5f).coerceIn(-0.2f, 1.2f), f("radius", 0.3f).coerceIn(0.05f, 0.8f))
                }.orEmpty(),
            )
        }
        val decals = (doc.element("decals") as? JsonArray)?.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val image = (o["image"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            fun f(k: String, d: Float) = (o[k] as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: d
            ThemeDecal(image, f("x", 0.5f), f("y", 0.5f), f("scale", 0.25f).coerceIn(0.05f, 1f), f("rotationDeg", 0f), f("opacity", 1f).coerceIn(0f, 1f))
        }.orEmpty().take(6)
        val name = doc.string("name")?.takeIf { it.isNotBlank() }
        val pills = if (doc.pillLook != "individual") emptyMap() else
            ThemePill.all.mapNotNull { which -> doc.pill(which)?.let { which to resolve(it, palette, fallbackName) } }.toMap()
        return LiquidTheme(
            name = name ?: fallbackName,
            dark = dark,
            accent = accent,
            glassTint = ThemeColors.opaque(key),
            button = button,
            main = main,
            chip = chip,
            tool = tool,
            frost = frost,
            source = doc.string("${ThemeDoc.EXT}.source") ?: ("WM Keyboard: " + (name ?: "unnamed theme")),
            modifier = modifierTheme,
            space = space,
            card = card,
            popup = popup,
            board = boardTheme,
            material = material,
            bloom = bloom,
            particles = particles,
            effect = effect,
            decals = decals,
            fontId = r.string("fontId").orEmpty(),
            fontScale = (r.float("fontScale") ?: 1f).coerceIn(0.8f, 1.4f),
            boldLabels = r.bool("boldKeyLabels") ?: false,
            hintScale = (r.float("hintFontScale") ?: 1f).coerceIn(0.6f, 1.4f),
            gapScale = (r.float("keyGapScale") ?: 1f).coerceIn(0.5f, 2f),
            padScale = (r.float("sidePadScale") ?: 1f).coerceIn(0.5f, 2f),
            toolSizeDp = r.int("toolWidthDp")?.coerceIn(20, 72),
            menuRowDp = r.int("popupHeightDp")?.coerceIn(32, 72),
            trail = r.color("gestureTrailColor"),
            animation = r.string("animation")?.uppercase() ?: "NONE",
            animationSpeed = (r.float("animationSpeed") ?: 1f).coerceIn(0.25f, 3f),
            mainColor = r.string("$ext.mainColor") ?: "block",
            windowBlurDp = if (r.bool("frost.windowBlur") == true) (r.int("frost.windowBlurRadiusDp") ?: 4).coerceIn(4, 64) else 0,
            pills = pills,
            search = search,
            adaptive = adaptive,
            inkDark = inkDark,
            inkLight = inkLight,
        )
    }

    /** WM Keyboard's material presets (ThemeLooks.kt, MIT): each kind is a preset of the same parts. */
    fun materialPreset(kind: String): ThemeMaterial {
        val base = ThemeMaterial(
            kind = kind, bevelDp = 0f, topInsetDp = 0f, skirtShade = 0.25f, gloss = 0f, lightAngleDeg = 0f, texture = "NONE",
            textureOpacity = 0f, edge = "NONE", edgeColor = null, edgeWidthDp = 1.5f, label = "NONE", labelColor = null,
            iridescence = 0f, softShadow = 0f,
        )
        return when (kind) {
            "CAP" -> base.copy(bevelDp = 3f, topInsetDp = 1.5f, gloss = 0.18f)
            "METAL" -> base.copy(bevelDp = 1f, gloss = 0.32f, texture = "BRUSHED", textureOpacity = 0.4f, edge = "RIM", label = "EMBOSS")
            "WOOD" -> base.copy(bevelDp = 1.5f, gloss = 0.08f, texture = "WOOD", textureOpacity = 0.5f, edge = "RIM", label = "DEBOSS")
            "STONE" -> base.copy(bevelDp = 2f, topInsetDp = 1f, skirtShade = 0.3f, texture = "STONE", textureOpacity = 0.5f, label = "DEBOSS")
            "STITCHED" -> base.copy(texture = "LINEN", textureOpacity = 0.35f, edge = "STITCH", edgeWidthDp = 1.2f)
            "LEATHER" -> base.copy(bevelDp = 1f, gloss = 0.1f, texture = "LEATHER", textureOpacity = 0.45f, edge = "STITCH", edgeWidthDp = 1.2f)
            "CARBON" -> base.copy(gloss = 0.2f, texture = "CARBON", textureOpacity = 0.5f, edge = "RIM")
            "PAPER" -> base.copy(texture = "PAPER", textureOpacity = 0.4f, edge = "INK", edgeWidthDp = 0.8f)
            "LETTERPRESS" -> base.copy(texture = "PAPER", textureOpacity = 0.35f, edge = "INSET", label = "DEBOSS")
            "NEON" -> base.copy(edge = "GLOW", edgeWidthDp = 1.5f, label = "GLOW")
            "HOLO" -> base.copy(gloss = 0.25f, iridescence = 0.55f, edge = "RIM")
            "TERMINAL" -> base.copy(texture = "SCANLINES", textureOpacity = 0.35f, label = "GLOW")
            "CARTOON" -> base.copy(gloss = 0.2f, edge = "INK", edgeWidthDp = 2.5f)
            "GLASS" -> base.copy(gloss = 0.35f, edge = "RIM", edgeWidthDp = 1.5f)
            "CANDY" -> base.copy(bevelDp = 2f, topInsetDp = 1f, skirtShade = 0.2f, gloss = 0.55f, edge = "RIM")
            "CERAMIC" -> base.copy(bevelDp = 1.5f, gloss = 0.4f, texture = "NOISE", textureOpacity = 0.08f, edge = "RIM")
            "RESIN" -> base.copy(gloss = 0.45f, texture = "NOISE", textureOpacity = 0.15f, edge = "RIM")
            "ARCADE" -> base.copy(bevelDp = 4f, topInsetDp = 3f, skirtShade = 0.35f, gloss = 0.3f, edge = "DOUBLE")
            "TYPEWRITER" -> base.copy(bevelDp = 3f, topInsetDp = 2f, gloss = 0.22f, edge = "DOUBLE", edgeWidthDp = 2f, texture = "NOISE", textureOpacity = 0.1f)
            "INSTRUMENT" -> base.copy(bevelDp = 2f, topInsetDp = 1f, gloss = 0.2f, texture = "BRUSHED", textureOpacity = 0.25f, edge = "DOUBLE", edgeWidthDp = 1f)
            "MOLTEN" -> base.copy(
                bevelDp = 1.5f, texture = "STONE", textureOpacity = 0.4f, edge = "GLOW", edgeColor = 0xFFFF7A1A, edgeWidthDp = 2f,
                label = "GLOW", labelColor = 0xFFFFA040,
            )
            "NEUMORPH" -> base.copy(lightAngleDeg = 45f, softShadow = 0.7f)
            "PIXEL" -> base.copy(texture = "PIXELS", textureOpacity = 0.55f, edge = "BEVEL", edgeWidthDp = 3f, label = "SHADOW", labelColor = 0xFF3F3F3F)
            "DOT_MATRIX" -> base.copy(edge = "DOTTED", edgeWidthDp = 1.2f, label = "DOTS")
            else -> base.copy(kind = "CUSTOM")
        }
    }

    private class Reader(val doc: ThemeDoc, val palette: WallpaperPalette?) {
        /** A colour, or the wallpaper's colour for its role when it follows the wallpaper (keeping its own opacity). */
        fun color(path: String): Long? {
            val stored = doc.color(path)
            val role = doc.follow(path) ?: return stored
            val p = palette ?: return stored
            val c = p.of(role).toLong() and 0x00FFFFFFL
            val alpha = ((stored ?: 0xFF000000L) ushr 24) and 0xFFL
            return (alpha shl 24) or c
        }

        fun colors(path: String): List<Long> {
            val list = doc.colors(path)
            val role = doc.follow(path) ?: return list
            val p = palette ?: return list
            // A followed colour list takes the wallpaper's role colour first, then its other tones.
            return listOf(p.of(role), p.vibrant, p.muted, p.light).distinct().take(maxOf(1, list.size)).map { it.toLong() and 0xFFFFFFFFL }
        }

        fun float(path: String) = doc.float(path)
        fun int(path: String) = doc.int(path)
        fun bool(path: String) = doc.bool(path)
        fun string(path: String) = doc.string(path)?.takeIf { it.isNotBlank() }

        fun gradient(path: String): ThemeGradient? {
            val g = doc.element(path) as? JsonObject ?: return null
            val colors = (g["colors"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let { c -> c.toLongOrNull() ?: ThemeDoc.parseColor(c) } }.orEmpty()
            if (colors.size < 2) return null
            return ThemeGradient(colors, (g["type"] as? JsonPrimitive)?.contentOrNull ?: "LINEAR", (g["angleDeg"] as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: 45f)
        }

        /** The material: its kind's preset with the theme's own parts over it, ranges enforced; null when there is none. */
        fun material(): ThemeMaterial? {
            if (doc.element("keyMaterial") !is JsonObject) return null
            val kind = string("keyMaterial.kind")?.uppercase() ?: "CAP"
            val preset = materialPreset(kind)
            val m = ThemeMaterial(
                kind = preset.kind,
                bevelDp = (float("keyMaterial.bevelDp") ?: preset.bevelDp).coerceIn(0f, 8f),
                topInsetDp = (float("keyMaterial.topInsetDp") ?: preset.topInsetDp).coerceIn(0f, 6f),
                skirtShade = (float("keyMaterial.skirtShade") ?: preset.skirtShade).coerceIn(0f, 0.6f),
                gloss = (float("keyMaterial.gloss") ?: preset.gloss).coerceIn(0f, 1f),
                lightAngleDeg = (float("keyMaterial.lightAngleDeg") ?: preset.lightAngleDeg).coerceIn(-60f, 60f),
                texture = string("keyMaterial.texture")?.uppercase() ?: preset.texture,
                textureOpacity = (float("keyMaterial.textureOpacity") ?: preset.textureOpacity).coerceIn(0f, 1f),
                edge = string("keyMaterial.edge")?.uppercase() ?: preset.edge,
                edgeColor = color("keyMaterial.edgeColor") ?: preset.edgeColor,
                edgeWidthDp = (float("keyMaterial.edgeWidthDp") ?: preset.edgeWidthDp).coerceIn(0.1f, 6f),
                label = string("keyMaterial.label")?.uppercase() ?: preset.label,
                labelColor = color("keyMaterial.labelColor") ?: preset.labelColor,
                iridescence = (float("keyMaterial.iridescence") ?: preset.iridescence).coerceIn(0f, 1f),
                softShadow = (float("keyMaterial.softShadow") ?: preset.softShadow).coerceIn(0f, 1f),
            )
            return if (m.plain && kind == "CUSTOM") null else m
        }
    }
}

/** Built-in theme documents (W3). */
object ThemeDocs {
    /** The blue you marked to keep: Google Calendar's Blueberry, the colour of your Proba 2 blocks. */
    const val BLUE = 0xFF5484EDL

    private fun obj(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.associate { (k, v) -> k to element(v) })

    private fun element(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is JsonElement -> v
        is Boolean -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        is String -> JsonPrimitive(v)
        is List<*> -> JsonArray(v.map { element(it) })
        else -> JsonPrimitive(v.toString())
    }

    /** The pills' dark fill and its text that 0.10.0 and 0.10.1 added to themes that did not say (0.10.2 takes them out again). */
    const val DARK_PILL = 0xA6141218L
    const val DARK_PILL_TEXT = 0xFFF2F2F5L

    /** The search bar's tint since 0.10.2: a third of [DARK_PILL]'s, so the liquid shows through it. */
    const val SEARCH_TINT = 0x37141218L

    /**
     * Your WM Keyboard theme "wallpaper stoped" (docs/reference/wallpaper-stoped.wmtheme.json), field for field as the
     * keyboard wrote it (its background picture removed).
     */
    private const val KEYBOARD = """
        {
            "id": "custom_1793966705811",
            "name": "wallpaper stoped",
            "dark": true,
            "boardBackground": 1381915,
            "boardGradient": null,
            "backgroundImage": null,
            "backgroundImageLandscape": null,
            "backgroundImageOpacity": 1.0,
            "backgroundImageBlur": 3.3675797,
            "backgroundAnimated": false,
            "backgroundImageBase64": null,
            "backgroundImageLandscapeBase64": null,
            "backgroundPhoto": {"provider":"unsplash","photoId":"coy_MhYMLHs","photographer":"meow meow","photographerUrl":"https://unsplash.com/@vegito988","photoUrl":"https://unsplash.com/photos/winged-person-with-halo-in-sky-coy_MhYMLHs","altText":"A person with wings and a halo looking toward a cloudy sky","avgColor":"#8c8c8c"},
            "backgroundPhotoLandscape": null,
            "suggestionBarBackground": null,
            "toolbarBackground": null,
            "oneHandedPanelBackground": null,
            "oneHandedPanelIcon": null,
            "navigationBarBackground": null,
            "keyShape": "PILL",
            "keyTexture": null,
            "keyTextureModifier": null,
            "keyTextureEnter": null,
            "keyTextureSpace": null,
            "keyTexturePressed": null,
            "keyTextureScale": null,
            "keyTextureOpacity": 0.96,
            "keyGradient": null,
            "keyBackground": 472657461,
            "keyText": 4293519854,
            "modifierKeyBackground": 471933737,
            "modifierKeyText": null,
            "hintText": null,
            "enterKeyBackground": 4281809215,
            "enterKeyText": 4294243576,
            "pressedKeyBackground": 4281414457,
            "keyBorderColor": null,
            "keyBorderWidthDp": 0.0,
            "keyElevationDp": 0.0,
            "keyShadowColor": null,
            "accent": 4285362036,
            "gestureTrailColor": null,
            "popupBackground": 4281611838,
            "popupText": null,
            "popupPlacement": null,
            "popupBorderColor": null,
            "popupBorderWidthDp": 0.0,
            "popupElevationDp": null,
            "popupSelectedBackground": null,
            "popupSelectedText": null,
            "popupTexture": null,
            "menuShape": null,
            "toolbarIcon": null,
            "toolCircleBackground": 1647062581,
            "toolCircleActiveBackground": 2318480185,
            "toolCircleActiveIcon": 4286808965,
            "toolBorderColor": null,
            "toolBorderWidthDp": 0.0,
            "toolElevationDp": 0.0,
            "chipBackground": 4280690734,
            "suggestionText": null,
            "secondarySuggestionText": null,
            "secondarySuggestionScale": null,
            "suggestionPillBackground": null,
            "primarySuggestionPillBackground": null,
            "secondaryText": null,
            "dividerColor": null,
            "emojiSearchBackground": null,
            "emojiSearchText": null,
            "clipboardChipBackground": null,
            "clipboardChipText": null,
            "chipText": null,
            "chipActiveBackground": null,
            "chipActiveText": null,
            "chipBorderColor": null,
            "chipBorderWidthDp": 0.0,
            "cardElevationDp": 0.0,
            "chipShape": null,
            "chipCornerRadiusDp": null,
            "cardShape": null,
            "keyCornerRadiusDp": null,
            "modifierKeyCornerRadiusDp": null,
            "actionKeyCornerRadiusDp": null,
            "popupCornerRadiusDp": null,
            "toolCircleRadiusDp": null,
            "popupShape": null,
            "toolShape": "PILL",
            "toolWidthDp": null,
            "toolbarHeightDp": null,
            "popupHeightDp": null,
            "popupFloatingHeightDp": null,
            "keyHeightDp": null,
            "keyGapScale": null,
            "sidePadScale": null,
            "sidePadLeftScale": null,
            "sidePadRightScale": null,
            "fontScale": null,
            "boldKeyLabels": null,
            "hintFontScale": null,
            "gestureTrailWidthDp": null,
            "gestureTrailOpacity": null,
            "animation": "NONE",
            "animationSpeed": 1.0,
            "fontId": null,
            "scriptFontIds": {},
            "soundStyle": null,
            "soundCustomId": null,
            "decals": [],
            "keyEffect": null,
            "keyEffectParam": null,
            "keyEffectIntensity": 1.0,
            "keyEffectImages": [],
            "keyEffectColor": null,
            "keyEffectCustomColor": null,
            "keyEffectSize": 1.0,
            "keyEffectSpeed": 1.0,
            "keyEffectSpread": 1.0,
            "keyEffectGravity": 1.0,
            "keyEffectDurationMs": 650,
            "keyOverrides": {},
            "keyIcons": {},
            "textureSlices": {},
            "ruleSheet": null,
            "assets": {},
            "followWallpaper": false,
            "wallpaperRoles": {},
            "frost": {"enabled":true,"windowBlur":false,"windowBlurRadiusDp":4,"fallbackOpacity":0.92,"surfaces":["KEYS","MODIFIER_KEYS","ENTER_KEY","SPACE_BAR","BARS","TOOLS","CHIPS","CARDS","POPUPS","MENUS"],"backdropBlur":9.0,"saturation":1.25,"brightness":0.0,"rim":0.86,"rimColor":null,"grain":0.0,"backdrop":"NONE","wallpaperBand":"BOTTOM","style":"FROSTED","refraction":0.6,"dispersion":0.25,"blurBoard":false,"accentBoard":true,"cutout":false},
            "keyMaterial": {"kind":"LETTERPRESS","bevelDp":null,"topInsetDp":null,"skirtShade":null,"gloss":null,"lightAngleDeg":null,"texture":null,"textureOpacity":null,"edge":null,"edgeColor":null,"edgeWidthDp":null,"label":null,"labelColor":null,"iridescence":null,"softShadow":null},
            "boardEffect": {"kind":"SWOOSH","colors":[],"density":0.5,"speed":0.4,"intensity":0.4,"scale":1.0,"reactive":0.4,"halfLifeMs":1200,"orbSpots":[]},
            "keyLighting": null,
            "touchBloom": {"color":null,"light":0.5,"radiusDp":100.0,"dip":0.4,"wobble":0.5,"motion":"SQUISH"},
            "keySkin": null,
            "familyName": null,
            "variants": [],
            "basedOn": null
        }
    """

    /** What follows the wallpaper in [Default], by role (0.10.2). */
    val defaultFollow: Map<String, String> = linkedMapOf(
        "keyBackground" to WallpaperRole.DARK,
        "modifierKeyBackground" to WallpaperRole.DARK,
        "toolCircleBackground" to WallpaperRole.DARK,
        "popupBackground" to WallpaperRole.DARK,
        "boardBackground" to WallpaperRole.DARK,
        "${ThemeDoc.EXT}.card.background" to WallpaperRole.DARK,
        "${ThemeDoc.EXT}.space.searchBackground" to WallpaperRole.DARK,
        "chipBackground" to WallpaperRole.MUTED,
        "enterKeyBackground" to WallpaperRole.VIBRANT,
        "accent" to WallpaperRole.VIBRANT,
        "pressedKeyBackground" to WallpaperRole.VIBRANT,
        "toolCircleActiveBackground" to WallpaperRole.VIBRANT,
        "keyText" to WallpaperRole.LIGHT,
        "enterKeyText" to WallpaperRole.LIGHT,
        "toolCircleActiveIcon" to WallpaperRole.LIGHT,
    )

    /**
     * The launcher's default theme (0.10.2): your keyboard theme "wallpaper stoped" for every button, chip, round button,
     * sheet, menu, card and the pills. Every colour follows the wallpaper and keeps its own opacity: buttons, secondary
     * and round buttons, menus, sheets and cards take the wallpaper's dark colour, chips its muted one, the main button,
     * the accent and pressed controls its vibrant one, text its light one (a dark ink where that would not read). The
     * pills are its clear frosted glass without a fill of their own; the search bar keeps a light tint.
     *
     * Three things differ from the keyboard's file: sheets show the wallpaper through their see-through board (the
     * keyboard's "Behind the glass: Nothing" would make them solid in the launcher), the keyboard's swoosh splash is
     * left out (your own touch effect stays), and it has an id of its own (an export does not replace the keyboard's).
     */
    val Default: ThemeDoc by lazy {
        val ext = ThemeDoc.EXT
        var d = ThemeDoc.parse(KEYBOARD.trim()) ?: error("the default theme does not parse")
        d = d.withString("id", "liquid_default")
            .withString("frost.backdrop", "AUTO")
            .with("boardEffect", null)
            .withInt("$ext.format", 1)
            .withString("$ext.source", "Liquid Launcher: default, your WM Keyboard theme \"wallpaper stoped\" in the wallpaper's colours")
            .withString("$ext.mainColor", "plain")
            .withString("$ext.pillLook", "shared")
            .withBool("$ext.pillMaterial", false)
            .withColor("$ext.space.searchBackground", SEARCH_TINT)
            .withColor("$ext.card.background", 0x59141218L)
            .withInt("$ext.card.cornerDp", 22)
        for ((field, role) in defaultFollow) d = d.withFollow(field, role)
        d
    }

    /**
     * The default theme of 0.10.0 and 0.10.1: your first keyboard theme's buttons, chips and round buttons as 0.9.14
     * drew them, its frost on every surface, sheets and menus as dark glass, dark pills, and the fixed blue accent.
     * Kept, unchanged, as the base a 0.9.13 theme is converted onto ([fromV1]), so that conversion never changes.
     */
    val Legacy010: ThemeDoc by lazy {
        ThemeDoc(
            obj(
                "id" to "liquid_default",
                "name" to "Liquid default",
                "dark" to true,
                "boardBackground" to 0x66121016L,
                "keyShape" to "PILL",
                "keyBackground" to 0x1C3C323EL,
                "keyText" to 0xFFE9E9EEL,
                "modifierKeyBackground" to 0x1C3C323EL,
                "enterKeyBackground" to 0x1C3C323EL,
                "enterKeyText" to 0xFFE9E9EEL,
                "pressedKeyBackground" to 0xFF723F61L,
                "keyBorderWidthDp" to 0.0,
                "keyElevationDp" to 0.0,
                "accent" to BLUE,
                "popupBackground" to 0x8C1B1720L,
                "toolCircleBackground" to 0x433D323FL,
                "toolCircleActiveIcon" to 0xFF838385L,
                "toolShape" to "PILL",
                "chipBackground" to 0xFF322B35L,
                "chipShape" to "ROUNDED",
                "chipCornerRadiusDp" to 12,
                "keyCornerRadiusDp" to 12,
                "popupCornerRadiusDp" to 30,
                "frost" to obj(
                    "enabled" to true, "windowBlur" to false, "windowBlurRadiusDp" to 4, "fallbackOpacity" to 0.92,
                    "surfaces" to listOf("KEYS", "MODIFIER_KEYS", "ENTER_KEY", "SPACE_BAR", "BARS", "TOOLS", "CHIPS", "CARDS", "POPUPS", "MENUS"),
                    "backdropBlur" to 9.0, "saturation" to 1.25, "brightness" to 0.0, "rim" to 0.86, "grain" to 0.0,
                    "backdrop" to "AUTO", "style" to "FROSTED", "refraction" to 0.6, "dispersion" to 0.25,
                ),
                "touchBloom" to obj("light" to 0.0, "radiusDp" to 100.0, "dip" to 0.4, "wobble" to 0.5, "motion" to "SQUISH"),
                ThemeDoc.EXT to obj(
                    "format" to 1,
                    "source" to "Liquid Launcher: default (your keyboard theme's controls)",
                    "mainColor" to "block",
                    "pillLook" to "shared",
                    "space" to obj("background" to 0xA6141218L, "text" to 0xFFF2F2F5L),
                    "card" to obj("background" to 0x59141218L, "cornerDp" to 22),
                ),
            )
        )
    }

    /**
     * A 0.9.13 / 0.9.14 launcher theme as a document: its buttons as the keys, its main button as the enter key, its
     * chips and round buttons, its frost; what that format did not have (sheets, menus, pills, cards) from [Legacy010].
     */
    fun fromV1(t: LiquidTheme): ThemeDoc {
        var d = Legacy010
        fun c(path: String, v: Long?) { d = d.withColor(path, v) }
        d = d.withString("name", t.name).withBool("dark", t.dark).withColor("accent", t.accent)
        c("keyBackground", t.button.fill); c("keyText", t.button.text); c("pressedKeyBackground", t.button.pressed)
        c("pressedKeyText", t.button.pressedText); c("keyBorderColor", t.button.border)
        d = d.withFloat("keyBorderWidthDp", t.button.borderWidthDp).withFloat("keyElevationDp", t.button.shadowDp)
        c("keyShadowColor", t.button.shadowColor)
        d = d.withString("keyShape", t.button.shape).withInt("keyCornerRadiusDp", t.button.cornerDp)
        t.button.gradient?.let { g -> d = d.with("keyGradient", obj("colors" to g.colors, "type" to g.type, "angleDeg" to g.angleDeg)) }
        c("modifierKeyBackground", t.button.fill)
        c("enterKeyBackground", t.main.fill); c("enterKeyText", t.main.text); c("pressedEnterKeyText", t.main.pressedText)
        d = d.withInt("actionKeyCornerRadiusDp", t.main.cornerDp)
        c("chipBackground", t.chip.fill); c("chipText", t.chip.text); c("chipActiveBackground", t.chip.active); c("chipActiveText", t.chip.activeText)
        c("chipBorderColor", t.chip.border)
        d = d.withFloat("chipBorderWidthDp", t.chip.borderWidthDp).withString("chipShape", t.chip.shape).withInt("chipCornerRadiusDp", t.chip.cornerDp)
        c("toolCircleBackground", t.tool.fill); c("toolbarIcon", t.tool.text); c("toolCircleActiveBackground", t.tool.active)
        c("toolCircleActiveIcon", t.tool.activeText); c("toolBorderColor", t.tool.border)
        d = d.withFloat("toolBorderWidthDp", t.tool.borderWidthDp).withFloat("toolElevationDp", t.tool.shadowDp)
            .withString("toolShape", t.tool.shape).withInt("toolCircleRadiusDp", t.tool.cornerDp)
        for ((kind, ct) in listOf("key" to t.button, "enter" to t.main, "chip" to t.chip, "tool" to t.tool)) {
            if (ct.glowDp > 0f) d = d.withFloat("${ThemeDoc.EXT}.glow.$kind", ct.glowDp).withColor("${ThemeDoc.EXT}.glowColor.$kind", ct.glowColor)
        }
        val f = t.frost
        val surfaces = buildList {
            if (t.button.glass) { add("KEYS"); add("MODIFIER_KEYS") }
            if (t.main.glass) add("ENTER_KEY")
            if (t.chip.glass) add("CHIPS")
            if (t.tool.glass) add("TOOLS")
            addAll(listOf("SPACE_BAR", "BARS", "CARDS", "POPUPS", "MENUS"))
        }
        d = d.withString("frost.style", f.style).withFloat("frost.backdropBlur", f.blurDp).withFloat("frost.saturation", f.saturation)
            .withFloat("frost.brightness", f.brightness).withFloat("frost.rim", f.rim).withColor("frost.rimColor", f.rimColor)
            .withFloat("frost.grain", f.grain).withFloat("frost.refraction", f.refraction).withFloat("frost.dispersion", f.dispersion)
            .withStrings("frost.surfaces", surfaces)
        return d.withString("${ThemeDoc.EXT}.source", t.source ?: "Liquid Launcher 0.9 theme")
    }
}
