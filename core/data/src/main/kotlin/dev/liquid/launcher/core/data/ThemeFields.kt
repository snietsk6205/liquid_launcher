package dev.liquid.launcher.core.data

/*
 * W3 (0.10.0): every option of a WM Keyboard theme that the launcher draws, described once. The Theme lab is built from
 * this list (its groups, sections, sliders, choices and colours), "Reset group" puts a group's paths back from the
 * default theme, and the AI export lists them with their meaning. Paths are WM Keyboard's JSON keys (dotted for nested
 * ones); paths under "liquidLauncher." are the launcher's additions, which WM Keyboard ignores.
 *
 * Ranges and choices are WM Keyboard's own (ThemeSpec.kt, Frost.kt, ThemeLooks.kt). Options of the format that have no
 * meaning in a launcher are kept in the file but not shown: see [ThemeFields.notShown].
 */

enum class ThemeGroup(val title: String, val summary: String) {
    FROST("Glass", "See-through panes over a blurred view of what is behind"),
    MATERIAL("Material", "What buttons look made of: caps, metal, paper, neon…"),
    TEXTURES("Textures", "A picture on each kind of button, and while pressed"),
    COLOURS("Colours", "Fill, text, pressed, borders, shadows of every control"),
    SHAPE("Shape and size", "Outlines, corners, heights, gaps, font"),
    PRESS("Press effect", "What a button does under your finger"),
    MOVING("Moving background", "Something moving over the home wallpaper"),
    BACKGROUND("Background image", "A picture behind sheets and panels"),
    EXTRAS("Extras", "Stickers on sheets, and other looks of this theme"),
}

enum class ThemeFieldKind { COLOR, FLOAT, INT, BOOL, CHOICE, IMAGE, IMAGES, GRADIENT, COLORS, SURFACES, TEXT }

/**
 * One option. [needs] is a condition on another field that must hold for this one to matter: "path" (true), "path=V"
 * (equals one of V|W), "path!=V", "path?" (set at all); the lab greys the row with [needsReason] otherwise.
 */
data class ThemeField(
    val path: String,
    val title: String,
    val group: ThemeGroup,
    val section: String,
    val kind: ThemeFieldKind,
    val summary: String,
    val min: Float = 0f,
    val max: Float = 1f,
    val step: Float = 0f,
    val unit: String = "",
    val options: List<Pair<String, String>> = emptyList(),
    val needs: String? = null,
    val needsReason: String? = null,
    /** True for colours that may be Auto (null: derived from the others, as in WM Keyboard). */
    val auto: Boolean = true,
)

object ThemeFields {
    val surfaces = listOf(
        "KEYS" to "Buttons", "MODIFIER_KEYS" to "Secondary buttons", "ENTER_KEY" to "Main buttons", "SPACE_BAR" to "Pills",
        "BARS" to "Bars", "TOOLS" to "Round buttons", "CHIPS" to "Chips", "CARDS" to "Cards", "POPUPS" to "Sheets", "MENUS" to "Menus",
    )

    val shapes = listOf(
        "ROUNDED" to "Rounded", "PILL" to "Pill", "SHARP" to "Square", "CUT" to "Cut corners", "SQUIRCLE" to "Squircle",
        "ARCH" to "Arch", "LEAF" to "Leaf", "SLANT" to "Slant", "HEXAGON" to "Hexagon", "SCALLOP" to "Scallop", "TICKET" to "Ticket",
    )

    val materials = listOf(
        "CUSTOM" to "Custom (flat)", "CAP" to "Key cap", "METAL" to "Metal", "WOOD" to "Wood", "STONE" to "Stone",
        "STITCHED" to "Stitched cloth", "LEATHER" to "Leather", "CARBON" to "Carbon fibre", "PAPER" to "Paper cut-out",
        "LETTERPRESS" to "Letterpress", "NEON" to "Neon", "HOLO" to "Holographic", "TERMINAL" to "Terminal", "CARTOON" to "Cartoon",
        "GLASS" to "Art glass", "CANDY" to "Candy", "CERAMIC" to "Ceramic", "RESIN" to "Resin", "ARCADE" to "Arcade button",
        "TYPEWRITER" to "Typewriter", "INSTRUMENT" to "Instrument panel", "MOLTEN" to "Molten", "NEUMORPH" to "Soft (neumorphic)",
        "PIXEL" to "Pixel block", "DOT_MATRIX" to "Dot matrix",
    )

    /** Moving kinds play over the home wallpaper; splash kinds (WM Keyboard's onPress) are thrown by a press. */
    val movingEffects = listOf(
        "SNOW" to "Snow", "RAIN" to "Rain", "CODE_RAIN" to "Code rain", "BOKEH" to "Bokeh", "STARFIELD" to "Stars",
        "WARP" to "Warp speed", "FIREFLIES" to "Fireflies", "MOTES" to "Dust", "EMBERS" to "Embers", "BUBBLES" to "Bubbles",
        "CONFETTI" to "Confetti", "FIREWORKS" to "Fireworks", "AURORA" to "Aurora", "NEBULA" to "Nebula", "LAVA" to "Lava lamp",
        "WAVES" to "Waves", "SMOKE" to "Smoke", "DOT_GRID" to "Dot grid", "CONTOUR" to "Contour lines", "EQUALIZER" to "Equaliser",
        "SCAN" to "Scanner", "SHEEN" to "Sheen", "RIPPLES" to "Ripples", "ORBS" to "Colour orbs",
    )
    val splashEffects = listOf(
        "PAINT" to "Paint splash", "INK" to "Ink blot", "LIGHT_POOL" to "Light pool", "FLOOD" to "Colour flood", "PUFF" to "Smoke puff",
        "GLINT" to "Glint", "POP" to "Pop", "SWOOSH" to "Swoosh", "SPECKS" to "Specks", "MELD" to "Meld",
    )

    val motions = listOf(
        "SINK" to "Sink", "GROW" to "Grow", "SHRINK" to "Shrink", "RISE" to "Rise", "TILT" to "Tilt",
        "JELLY" to "Jelly", "POP" to "Pop", "SQUISH" to "Squish", "FADE" to "Fade",
    )

    val fonts = listOf(
        "" to "The launcher's", "light" to "Light", "medium" to "Medium", "condensed" to "Condensed", "serif" to "Serif",
        "mono" to "Monospace", "casual" to "Casual", "cursive" to "Cursive",
    )

    /** WM Keyboard options with no launcher meaning: kept in the theme file (export gives them back), not shown. */
    val notShown = listOf(
        "suggestionBarBackground", "toolbarBackground", "oneHandedPanelBackground", "oneHandedPanelIcon", "navigationBarBackground",
        "backgroundImageLandscape", "backgroundImageLandscapeBase64", "backgroundAnimated", "backgroundPhoto", "backgroundPhotoLandscape",
        "popupPlacement", "popupFloatingHeightDp", "toolbarHeightDp", "sidePadLeftScale", "sidePadRightScale", "suggestionText",
        "secondarySuggestionText", "secondarySuggestionScale", "suggestionPillBackground", "primarySuggestionPillBackground",
        "emojiSearchBackground", "emojiSearchText", "clipboardChipBackground", "clipboardChipText", "gestureTrailWidthDp",
        "gestureTrailOpacity", "soundStyle", "soundCustomId", "ruleSheet", "keyLighting", "keySkin", "frost.wallpaperBand",
        "frost.blurBoard", "frost.accentBoard", "frost.cutout", "followWallpaper", "wallpaperRoles", "keyOverrides",
    )

    val all: List<ThemeField> = buildList {
        val g = ThemeGroup.FROST
        add(ThemeField("frost.enabled", "Glass", g, "Glass", ThemeFieldKind.BOOL, "See-through panes over a blurred view of what is behind them; off draws the colours alone"))
        add(ThemeField("frost.surfaces", "Glass on", g, "Glass", ThemeFieldKind.SURFACES, "Which kinds of control and surface are glass", options = surfaces, needs = "frost.enabled", needsReason = "Needs glass on"))
        add(ThemeField("frost.style", "Kind of glass", g, "Glass", ThemeFieldKind.CHOICE, "Frosted: a blurred pane with a rim of light. Liquid: the edge also bends the light and splits colours", options = listOf("FROSTED" to "Frosted", "LIQUID" to "Liquid"), needs = "frost.enabled", needsReason = "Needs glass on"))
        add(ThemeField("frost.backdropBlur", "Blur", g, "Glass", ThemeFieldKind.FLOAT, "How much what is behind the glass is blurred", 0f, 25f, 0.5f, "dp", needs = "frost.enabled", needsReason = "Needs glass on"))
        add(ThemeField("frost.saturation", "Colour", g, "Glass", ThemeFieldKind.FLOAT, "How vivid what is seen through the glass is (1 = as it is)", 0.5f, 2f, 0.05f, "×", needs = "frost.enabled", needsReason = "Needs glass on"))
        add(ThemeField("frost.brightness", "Brightness", g, "Glass", ThemeFieldKind.FLOAT, "Lighter or darker than what is behind", -0.3f, 0.3f, 0.01f, needs = "frost.enabled", needsReason = "Needs glass on"))
        add(ThemeField("frost.refraction", "Bend", g, "Glass", ThemeFieldKind.FLOAT, "How far the rounded edge bends the light", 0f, 1f, 0.05f, needs = "frost.style=LIQUID", needsReason = "Needs Kind of glass: Liquid"))
        add(ThemeField("frost.dispersion", "Colour split", g, "Glass", ThemeFieldKind.FLOAT, "Rainbow fringes where the edge bends", 0f, 1f, 0.05f, needs = "frost.style=LIQUID", needsReason = "Needs Kind of glass: Liquid"))
        add(ThemeField("frost.rim", "Rim of light", g, "Glass", ThemeFieldKind.FLOAT, "Light on the edge facing the light, shade on the other", 0f, 1f, 0.02f, needs = "frost.enabled", needsReason = "Needs glass on"))
        add(ThemeField("frost.rimColor", "Rim colour", g, "Glass", ThemeFieldKind.COLOR, "The colour of the rim of light (Auto: white)", needs = "frost.enabled", needsReason = "Needs glass on"))
        add(ThemeField("frost.grain", "Grain", g, "Glass", ThemeFieldKind.FLOAT, "A fine still grain over the pane", 0f, 0.25f, 0.01f, needs = "frost.enabled", needsReason = "Needs glass on"))
        add(ThemeField("frost.backdrop", "Behind the glass", g, "Behind", ThemeFieldKind.CHOICE, "What the glass shows blurred: the wallpaper, the theme's picture (on sheets that have it), or nothing (plain colours)", options = listOf("AUTO" to "Auto", "WALLPAPER" to "The wallpaper", "IMAGE" to "The theme's picture", "NONE" to "Nothing"), needs = "frost.enabled", needsReason = "Needs glass on"))
        add(ThemeField("frost.windowBlur", "Blur behind sheets", g, "Behind", ThemeFieldKind.BOOL, "While a sheet or panel is open, the home screen behind it is blurred"))
        add(ThemeField("frost.windowBlurRadiusDp", "Its strength", g, "Behind", ThemeFieldKind.INT, "How much the home screen behind an open sheet is blurred", 4f, 64f, 1f, "dp", needs = "frost.windowBlur", needsReason = "Needs Blur behind sheets on"))
        add(ThemeField("frost.fallbackOpacity", "Opacity without glass", g, "Behind", ThemeFieldKind.FLOAT, "How solid surfaces are where glass is not drawn (battery saving, the lowest glass quality)", 0.5f, 1f, 0.01f))

        val m = ThemeGroup.MATERIAL
        val needMat = "keyMaterial?"
        val needMatReason = "Needs a material"
        add(ThemeField("keyMaterial.kind", "Material", m, "Material", ThemeFieldKind.CHOICE, "What buttons and pills look made of; each is a preset of the parts below, which you can change", options = listOf("" to "None") + materials))
        add(ThemeField("keyMaterial.bevelDp", "Cap height", m, "Raised cap", ThemeFieldKind.FLOAT, "How high the button stands on its darker skirt; it sinks when pressed", 0f, 8f, 0.25f, "dp", needs = needMat, needsReason = needMatReason))
        add(ThemeField("keyMaterial.topInsetDp", "Top face inset", m, "Raised cap", ThemeFieldKind.FLOAT, "How far the top face sits inside the skirt", 0f, 6f, 0.25f, "dp", needs = needMat, needsReason = needMatReason))
        add(ThemeField("keyMaterial.skirtShade", "Cap side shade", m, "Raised cap", ThemeFieldKind.FLOAT, "How much darker the skirt is than the face", 0f, 0.6f, 0.02f, needs = needMat, needsReason = needMatReason))
        add(ThemeField("keyMaterial.gloss", "Gloss", m, "Light", ThemeFieldKind.FLOAT, "A highlight across the face", 0f, 1f, 0.02f, needs = needMat, needsReason = needMatReason))
        add(ThemeField("keyMaterial.lightAngleDeg", "Light direction", m, "Light", ThemeFieldKind.FLOAT, "Where the light comes from, from the left (−60°) to the right (60°)", -60f, 60f, 1f, "°", needs = needMat, needsReason = needMatReason))
        add(ThemeField("keyMaterial.texture", "Surface texture", m, "Surface", ThemeFieldKind.CHOICE, "A pattern drawn on the face", options = listOf(
            "NONE" to "None", "NOISE" to "Grain", "LINEN" to "Linen", "DOTS" to "Dots", "SCANLINES" to "Scan lines", "BRUSHED" to "Brushed",
            "WOOD" to "Wood grain", "STONE" to "Stone", "PAPER" to "Paper", "CARBON" to "Carbon weave", "LEATHER" to "Leather", "PIXELS" to "Pixels", "PLANKS" to "Planks",
        ), needs = needMat, needsReason = needMatReason))
        add(ThemeField("keyMaterial.textureOpacity", "Texture strength", m, "Surface", ThemeFieldKind.FLOAT, "How strongly the pattern shows", 0f, 1f, 0.02f, needs = needMat, needsReason = needMatReason))
        add(ThemeField("keyMaterial.edge", "Edge", m, "Edge", ThemeFieldKind.CHOICE, "What happens at the button's edge", options = listOf(
            "NONE" to "None", "STITCH" to "Stitching", "GLOW" to "Glow", "INK" to "Ink outline", "RIM" to "Glazed rim", "INSET" to "Pressed-in top edge",
            "DOUBLE" to "Double ring", "DOTTED" to "Dotted outline", "BEVEL" to "Pixel bevel",
        ), needs = needMat, needsReason = needMatReason))
        add(ThemeField("keyMaterial.edgeColor", "Edge colour", m, "Edge", ThemeFieldKind.COLOR, "The edge's colour (Auto: from the text or the accent)", needs = needMat, needsReason = needMatReason))
        add(ThemeField("keyMaterial.edgeWidthDp", "Edge width", m, "Edge", ThemeFieldKind.FLOAT, "How wide the edge is", 0.1f, 6f, 0.1f, "dp", needs = needMat, needsReason = needMatReason))
        add(ThemeField("keyMaterial.label", "Label effect", m, "Label", ThemeFieldKind.CHOICE, "How the text is set on the material", options = listOf(
            "NONE" to "None", "GLOW" to "Glow", "DEBOSS" to "Pressed in", "EMBOSS" to "Raised", "SHADOW" to "Shadow", "DOTS" to "Dot matrix",
        ), needs = needMat, needsReason = needMatReason))
        add(ThemeField("keyMaterial.labelColor", "Label effect colour", m, "Label", ThemeFieldKind.COLOR, "The colour of the label's glow or shadow", needs = needMat, needsReason = needMatReason))
        add(ThemeField("keyMaterial.iridescence", "Rainbow sheen", m, "Light", ThemeFieldKind.FLOAT, "A holographic shimmer across the face", 0f, 1f, 0.02f, needs = needMat, needsReason = needMatReason))
        add(ThemeField("keyMaterial.softShadow", "Soft shadow", m, "Light", ThemeFieldKind.FLOAT, "The neumorphic pair: light on the side of the light, dark on the other", 0f, 1f, 0.02f, needs = needMat, needsReason = needMatReason))

        val t = ThemeGroup.TEXTURES
        add(ThemeField("keyTexture", "Buttons", t, "Pictures", ThemeFieldKind.IMAGE, "A picture on every button, over its colour; the others fall back to it"))
        add(ThemeField("keyTextureModifier", "Secondary buttons", t, "Pictures", ThemeFieldKind.IMAGE, "A picture on secondary buttons (Cancel, Edit…); Auto: the buttons' picture"))
        add(ThemeField("keyTextureEnter", "Main buttons", t, "Pictures", ThemeFieldKind.IMAGE, "A picture on main buttons (Apply, Mark done); Auto: the secondary buttons' picture"))
        add(ThemeField("keyTextureSpace", "Pills", t, "Pictures", ThemeFieldKind.IMAGE, "A picture on the three pills; Auto: the buttons' picture"))
        add(ThemeField("keyTexturePressed", "While pressed", t, "Pictures", ThemeFieldKind.IMAGE, "A picture shown while a button is held; Auto: the pressed colour over the button's picture"))
        add(ThemeField("popupTexture", "Menus", t, "Pictures", ThemeFieldKind.IMAGE, "A picture behind the rows of menus"))
        add(ThemeField("keyTextureScale", "Fit", t, "How", ThemeFieldKind.CHOICE, "How a picture fits its button", options = listOf("crop" to "Crop", "stretch" to "Stretch", "tile" to "Tile")))
        add(ThemeField("keyTextureOpacity", "Opacity", t, "How", ThemeFieldKind.FLOAT, "How strongly the pictures cover the colours", 0f, 1f, 0.02f))

        val c = ThemeGroup.COLOURS
        add(ThemeField("accent", "Accent", c, "Accent", ThemeFieldKind.COLOR, "Highlights: rings, the selected tab, the index bubble, the current layout, glows", auto = false))
        add(ThemeField("liquidLauncher.mainColor", "Main button colour", c, "Accent", ThemeFieldKind.CHOICE, "The main button of a screen in the colour of the study block, the accent, or the theme's main button colour", options = listOf("block" to "The block's", "accent" to "Accent", "plain" to "The theme's")))
        add(ThemeField("dark", "Dark theme", c, "Accent", ThemeFieldKind.BOOL, "Colours left on Auto are worked out for a dark or a light theme"))
        fun role(prefix: String, sec: String, fill: String, text: String, pressed: String?, pressedText: String?) {
            add(ThemeField(fill, "Fill", c, sec, ThemeFieldKind.COLOR, "$prefix: the colour (its opacity is how much shows through)", auto = fill != "keyBackground"))
            add(ThemeField(text, "Text", c, sec, ThemeFieldKind.COLOR, "$prefix: the text and icons"))
            if (pressed != null) add(ThemeField(pressed, "Pressed fill", c, sec, ThemeFieldKind.COLOR, "$prefix: the colour while held"))
            if (pressedText != null) add(ThemeField(pressedText, "Text when pressed", c, sec, ThemeFieldKind.COLOR, "$prefix: the text while held"))
        }
        role("Buttons", "Buttons", "keyBackground", "keyText", "pressedKeyBackground", "pressedKeyText")
        add(ThemeField("keyGradient", "Gradient", c, "Buttons", ThemeFieldKind.GRADIENT, "A sheen of two or more colours over buttons; stops may be see-through"))
        add(ThemeField("keyBorderColor", "Border", c, "Buttons", ThemeFieldKind.COLOR, "An outline around buttons (Auto: none)"))
        add(ThemeField("keyBorderWidthDp", "Border width", c, "Buttons", ThemeFieldKind.FLOAT, "How wide the outline is", 0f, 3f, 0.1f, "dp", needs = "keyBorderColor?", needsReason = "Needs a border colour"))
        add(ThemeField("keyElevationDp", "Elevation", c, "Buttons", ThemeFieldKind.FLOAT, "A shadow under buttons, as if they stood off the sheet", 0f, 8f, 0.25f, "dp"))
        add(ThemeField("keyShadowColor", "Shadow colour", c, "Buttons", ThemeFieldKind.COLOR, "The shadow's colour (Auto: black)"))
        add(ThemeField("liquidLauncher.glow.key", "Glow", c, "Buttons", ThemeFieldKind.FLOAT, "A soft light around buttons", 0f, 16f, 0.5f, "dp"))
        add(ThemeField("liquidLauncher.glowColor.key", "Glow colour", c, "Buttons", ThemeFieldKind.COLOR, "The glow's colour (Auto: the accent)"))
        add(ThemeField("hintText", "Hint text", c, "Buttons", ThemeFieldKind.COLOR, "Small extra text on buttons and chips, such as counts (Auto: the text, fainter)"))
        role("Secondary buttons (Cancel, Edit, Shuffle…)", "Secondary buttons", "modifierKeyBackground", "modifierKeyText", null, null)
        role("Main buttons (Apply, Mark done)", "Main buttons", "enterKeyBackground", "enterKeyText", null, "pressedEnterKeyText")
        role("Pills (home, drawer, search bar)", "Pills", "liquidLauncher.space.background", "liquidLauncher.space.text", "liquidLauncher.space.pressed", null)
        role("Chips", "Chips", "chipBackground", "chipText", null, null)
        add(ThemeField("chipActiveBackground", "Selected fill", c, "Chips", ThemeFieldKind.COLOR, "Chips: the colour of a selected chip"))
        add(ThemeField("chipActiveText", "Selected text", c, "Chips", ThemeFieldKind.COLOR, "Chips: the text of a selected chip"))
        add(ThemeField("chipBorderColor", "Border", c, "Chips", ThemeFieldKind.COLOR, "Chips: an outline"))
        add(ThemeField("chipBorderWidthDp", "Border width", c, "Chips", ThemeFieldKind.FLOAT, "Chips: how wide the outline is", 0f, 3f, 0.1f, "dp", needs = "chipBorderColor?", needsReason = "Needs a border colour"))
        add(ThemeField("toolCircleBackground", "Fill", c, "Round buttons", ThemeFieldKind.COLOR, "Round icon buttons: the colour"))
        add(ThemeField("toolbarIcon", "Icon", c, "Round buttons", ThemeFieldKind.COLOR, "Round icon buttons: the icon"))
        add(ThemeField("toolCircleActiveBackground", "Active fill", c, "Round buttons", ThemeFieldKind.COLOR, "Round icon buttons: the colour while held or on"))
        add(ThemeField("toolCircleActiveIcon", "Active icon", c, "Round buttons", ThemeFieldKind.COLOR, "Round icon buttons: the icon while held or on"))
        add(ThemeField("toolBorderColor", "Border", c, "Round buttons", ThemeFieldKind.COLOR, "Round icon buttons: an outline"))
        add(ThemeField("toolBorderWidthDp", "Border width", c, "Round buttons", ThemeFieldKind.FLOAT, "Round icon buttons: how wide the outline is", 0f, 3f, 0.1f, "dp", needs = "toolBorderColor?", needsReason = "Needs a border colour"))
        add(ThemeField("toolElevationDp", "Elevation", c, "Round buttons", ThemeFieldKind.FLOAT, "Round icon buttons: a shadow under them", 0f, 8f, 0.25f, "dp"))
        add(ThemeField("liquidLauncher.card.background", "Fill", c, "Cards", ThemeFieldKind.COLOR, "Cards (search page, hub): the colour"))
        add(ThemeField("liquidLauncher.card.text", "Text", c, "Cards", ThemeFieldKind.COLOR, "Cards: the text"))
        add(ThemeField("liquidLauncher.card.border", "Border", c, "Cards", ThemeFieldKind.COLOR, "Cards: an outline"))
        add(ThemeField("liquidLauncher.card.borderWidthDp", "Border width", c, "Cards", ThemeFieldKind.FLOAT, "Cards: how wide the outline is", 0f, 3f, 0.1f, "dp", needs = "liquidLauncher.card.border?", needsReason = "Needs a border colour"))
        add(ThemeField("cardElevationDp", "Elevation", c, "Cards", ThemeFieldKind.FLOAT, "Cards: a shadow under them", 0f, 8f, 0.25f, "dp"))
        add(ThemeField("boardBackground", "Fill", c, "Sheets and panels", ThemeFieldKind.COLOR, "Sheets and panels (hub, wallpaper, tags, layouts): the colour over their glass", auto = false))
        add(ThemeField("boardGradient", "Gradient", c, "Sheets and panels", ThemeFieldKind.GRADIENT, "Sheets and panels: a gradient instead of the plain colour"))
        add(ThemeField("secondaryText", "Secondary text", c, "Sheets and panels", ThemeFieldKind.COLOR, "Sheets: headings and details (Auto: the text, fainter)"))
        add(ThemeField("dividerColor", "Dividers", c, "Sheets and panels", ThemeFieldKind.COLOR, "Sheets and menus: the lines between rows"))
        add(ThemeField("popupBackground", "Fill", c, "Menus", ThemeFieldKind.COLOR, "Menus (an app's long press, the home menu, a tab's menu): the colour"))
        add(ThemeField("popupText", "Text", c, "Menus", ThemeFieldKind.COLOR, "Menus: the text and icons"))
        add(ThemeField("popupSelectedBackground", "Selected fill", c, "Menus", ThemeFieldKind.COLOR, "Menus: the row under the finger or chosen"))
        add(ThemeField("popupSelectedText", "Selected text", c, "Menus", ThemeFieldKind.COLOR, "Menus: the text of that row"))
        add(ThemeField("popupBorderColor", "Border", c, "Menus", ThemeFieldKind.COLOR, "Menus: an outline"))
        add(ThemeField("popupBorderWidthDp", "Border width", c, "Menus", ThemeFieldKind.FLOAT, "Menus: how wide the outline is", 0f, 3f, 0.1f, "dp", needs = "popupBorderColor?", needsReason = "Needs a border colour"))
        add(ThemeField("popupElevationDp", "Elevation", c, "Menus", ThemeFieldKind.FLOAT, "Menus: a shadow under them", 0f, 12f, 0.5f, "dp"))
        add(ThemeField("gestureTrailColor", "Trail colour", c, "Other", ThemeFieldKind.COLOR, "The colour particles take with Colour: Trail"))

        val s = ThemeGroup.SHAPE
        add(ThemeField("keyShape", "Button shape", s, "Shapes", ThemeFieldKind.CHOICE, "The outline of buttons and pills; None leaves only the label (a face shows while held)", options = listOf("NONE" to "None") + shapes))
        add(ThemeField("keyCornerRadiusDp", "Button corners", s, "Shapes", ThemeFieldKind.INT, "Corner radius of rounded, cut and squircle buttons", 0f, 40f, 1f, "dp"))
        add(ThemeField("modifierKeyCornerRadiusDp", "Secondary corners", s, "Shapes", ThemeFieldKind.INT, "Corner radius of secondary buttons (Auto: the buttons')", 0f, 40f, 1f, "dp"))
        add(ThemeField("actionKeyCornerRadiusDp", "Main button corners", s, "Shapes", ThemeFieldKind.INT, "Corner radius of main buttons (Auto: the buttons')", 0f, 40f, 1f, "dp"))
        add(ThemeField("chipShape", "Chip shape", s, "Shapes", ThemeFieldKind.CHOICE, "The outline of chips", options = shapes))
        add(ThemeField("chipCornerRadiusDp", "Chip corners", s, "Shapes", ThemeFieldKind.INT, "Corner radius of chips", 0f, 40f, 1f, "dp"))
        add(ThemeField("toolShape", "Round button shape", s, "Shapes", ThemeFieldKind.CHOICE, "The outline of icon buttons", options = listOf("CIRCLE" to "Circle") + shapes))
        add(ThemeField("toolCircleRadiusDp", "Round button corners", s, "Shapes", ThemeFieldKind.INT, "Corner radius of icon buttons that are not circles", 0f, 40f, 1f, "dp"))
        add(ThemeField("popupShape", "Sheet shape", s, "Shapes", ThemeFieldKind.CHOICE, "The outline of sheets and panels", options = shapes))
        add(ThemeField("popupCornerRadiusDp", "Sheet corners", s, "Shapes", ThemeFieldKind.INT, "Corner radius of sheets and menus", 0f, 48f, 1f, "dp"))
        add(ThemeField("menuShape", "Menu shape", s, "Shapes", ThemeFieldKind.CHOICE, "The outline of menus", options = shapes))
        add(ThemeField("cardShape", "Card shape", s, "Shapes", ThemeFieldKind.CHOICE, "The outline of cards", options = shapes))
        add(ThemeField("liquidLauncher.card.cornerDp", "Card corners", s, "Shapes", ThemeFieldKind.INT, "Corner radius of cards", 0f, 40f, 1f, "dp"))
        add(ThemeField("keyHeightDp", "Button height", s, "Sizes", ThemeFieldKind.INT, "How tall buttons and chips are (Auto: from Settings → Buttons)", 26f, 60f, 1f, "dp"))
        add(ThemeField("toolWidthDp", "Round button size", s, "Sizes", ThemeFieldKind.INT, "How big round icon buttons are (Auto: as tall as buttons)", 26f, 64f, 1f, "dp"))
        add(ThemeField("popupHeightDp", "Menu row height", s, "Sizes", ThemeFieldKind.INT, "How tall the rows of menus are", 36f, 64f, 1f, "dp"))
        add(ThemeField("keyGapScale", "Gaps", s, "Sizes", ThemeFieldKind.FLOAT, "Space between buttons in a row", 0.5f, 2f, 0.05f, "×"))
        add(ThemeField("sidePadScale", "Padding", s, "Sizes", ThemeFieldKind.FLOAT, "Space inside buttons around their label", 0.5f, 2f, 0.05f, "×"))
        add(ThemeField("fontId", "Font", s, "Text", ThemeFieldKind.CHOICE, "The font of buttons, chips, pills and menus", options = fonts))
        add(ThemeField("fontScale", "Text size", s, "Text", ThemeFieldKind.FLOAT, "Labels larger or smaller", 0.8f, 1.4f, 0.02f, "×"))
        add(ThemeField("boldKeyLabels", "Bold labels", s, "Text", ThemeFieldKind.BOOL, "Labels in bold"))
        add(ThemeField("hintFontScale", "Hint size", s, "Text", ThemeFieldKind.FLOAT, "Small extra text (counts) larger or smaller", 0.6f, 1.4f, 0.02f, "×"))

        val p = ThemeGroup.PRESS
        add(ThemeField("touchBloom.motion", "Motion", p, "Touch bloom", ThemeFieldKind.CHOICE, "How a button moves under the finger. Sink moves every button near the finger; the others move the one pressed", options = listOf("" to "None") + motions))
        add(ThemeField("touchBloom.light", "Light strength", p, "Touch bloom", ThemeFieldKind.FLOAT, "A soft light spreading from the finger over the sheet (0: only the motion)", 0f, 1f, 0.02f, needs = "touchBloom?", needsReason = "Needs a motion or Touch bloom on"))
        add(ThemeField("touchBloom.color", "Light colour", p, "Touch bloom", ThemeFieldKind.COLOR, "The light's colour (Auto: the accent)", needs = "touchBloom?", needsReason = "Needs a motion or Touch bloom on"))
        add(ThemeField("touchBloom.radiusDp", "Reach", p, "Touch bloom", ThemeFieldKind.FLOAT, "How far the light spreads, and with Sink how far buttons move", 40f, 240f, 2f, "dp", needs = "touchBloom?", needsReason = "Needs a motion or Touch bloom on"))
        add(ThemeField("touchBloom.dip", "Key dip", p, "Touch bloom", ThemeFieldKind.FLOAT, "How far buttons move (0: only the light)", 0f, 1f, 0.02f, needs = "touchBloom?", needsReason = "Needs a motion or Touch bloom on"))
        add(ThemeField("touchBloom.wobble", "Wobble", p, "Touch bloom", ThemeFieldKind.FLOAT, "How much they bounce on the way back", 0f, 1f, 0.02f, needs = "touchBloom?", needsReason = "Needs a motion or Touch bloom on"))
        add(ThemeField("keyEffect", "Particles", p, "Particles", ThemeFieldKind.CHOICE, "A small burst from every button pressed", options = listOf(
            "" to "None", "STARS" to "Stars", "HEARTS" to "Hearts", "SPARKLE" to "Sparkle", "CONFETTI" to "Confetti", "EMOJI" to "Emoji", "CUSTOM_IMAGE" to "Your pictures",
        )))
        val needFx = "keyEffect?"
        val needFxReason = "Needs particles on"
        add(ThemeField("keyEffectParam", "Emoji", p, "Particles", ThemeFieldKind.TEXT, "The emoji to throw, one or more", needs = "keyEffect=EMOJI", needsReason = "Needs Particles: Emoji"))
        add(ThemeField("keyEffectImages", "Pictures", p, "Particles", ThemeFieldKind.IMAGES, "Up to six pictures to throw (transparent PNGs look best)", needs = "keyEffect=CUSTOM_IMAGE", needsReason = "Needs Particles: Your pictures"))
        add(ThemeField("keyEffectColor", "Colour", p, "Particles", ThemeFieldKind.CHOICE, "Where the particles' colour comes from", options = listOf(
            "NATURAL" to "Natural", "KEY_TEXT" to "Text colour", "ACCENT" to "Accent", "GESTURE_TRAIL" to "Trail colour", "CUSTOM" to "Custom", "RANDOM" to "Random",
        ), needs = needFx, needsReason = needFxReason))
        add(ThemeField("keyEffectCustomColor", "Custom colour", p, "Particles", ThemeFieldKind.COLOR, "The colour with Colour: Custom", needs = "keyEffectColor=CUSTOM", needsReason = "Needs Colour: Custom"))
        add(ThemeField("keyEffectIntensity", "Intensity", p, "Particles", ThemeFieldKind.FLOAT, "How many particles a press throws", 0.2f, 3f, 0.05f, "×", needs = needFx, needsReason = needFxReason))
        add(ThemeField("keyEffectSize", "Size", p, "Particles", ThemeFieldKind.FLOAT, "How big they are", 0.4f, 3f, 0.05f, "×", needs = needFx, needsReason = needFxReason))
        add(ThemeField("keyEffectSpeed", "Speed", p, "Particles", ThemeFieldKind.FLOAT, "How fast they fly", 0.3f, 2.5f, 0.05f, "×", needs = needFx, needsReason = needFxReason))
        add(ThemeField("keyEffectSpread", "Spread", p, "Particles", ThemeFieldKind.FLOAT, "How wide they fan out", 0.1f, 1f, 0.02f, needs = needFx, needsReason = needFxReason))
        add(ThemeField("keyEffectGravity", "Gravity", p, "Particles", ThemeFieldKind.FLOAT, "How fast they fall; 0 drifts, below 0 they float up", -1f, 3f, 0.05f, needs = needFx, needsReason = needFxReason))
        add(ThemeField("keyEffectDurationMs", "Duration", p, "Particles", ThemeFieldKind.INT, "How long they live", 200f, 2500f, 50f, "ms", needs = needFx, needsReason = needFxReason))

        val mv = ThemeGroup.MOVING
        add(ThemeField("boardEffect.kind", "Effect", mv, "Effect", ThemeFieldKind.CHOICE, "Moving kinds play over the home wallpaper; splashes are thrown by every press on a sheet or button", options = listOf("" to "None") + movingEffects + splashEffects))
        val needEffect = "boardEffect?"
        val needEffectReason = "Needs an effect"
        add(ThemeField("boardEffect.colors", "Colours", mv, "Effect", ThemeFieldKind.COLORS, "Up to four colours (none: the accent and the text colour)", needs = needEffect, needsReason = needEffectReason))
        add(ThemeField("boardEffect.density", "Amount", mv, "Effect", ThemeFieldKind.FLOAT, "How much of it there is", 0f, 1f, 0.02f, needs = needEffect, needsReason = needEffectReason))
        add(ThemeField("boardEffect.speed", "Speed", mv, "Effect", ThemeFieldKind.FLOAT, "How fast it moves (0: still)", 0f, 3f, 0.05f, "×", needs = needEffect, needsReason = needEffectReason))
        add(ThemeField("boardEffect.intensity", "Brightness", mv, "Effect", ThemeFieldKind.FLOAT, "How bright it is", 0f, 1f, 0.02f, needs = needEffect, needsReason = needEffectReason))
        add(ThemeField("boardEffect.scale", "Size", mv, "Effect", ThemeFieldKind.FLOAT, "How big its parts are", 0.5f, 2f, 0.05f, "×", needs = needEffect, needsReason = needEffectReason))
        add(ThemeField("boardEffect.reactive", "Reacts to touch", mv, "Effect", ThemeFieldKind.FLOAT, "Each touch speeds it up and brightens it for a moment", 0f, 1f, 0.02f, needs = needEffect, needsReason = needEffectReason))
        add(ThemeField("boardEffect.halfLifeMs", "Fade time", mv, "Effect", ThemeFieldKind.INT, "How long a touch's push (or a splash) lasts", 300f, 4000f, 50f, "ms", needs = needEffect, needsReason = needEffectReason))
        add(ThemeField("animation", "Theme animation", mv, "Theme animation", ThemeFieldKind.CHOICE, "Sheets' gradients flow, or the theme's colours slowly turn round the colour wheel", options = listOf("NONE" to "None", "FLOW" to "Flowing gradient", "HUE_CYCLE" to "Colour cycle")))
        add(ThemeField("animationSpeed", "Animation speed", mv, "Theme animation", ThemeFieldKind.FLOAT, "How fast the theme animation runs", 0.25f, 3f, 0.05f, "×", needs = "animation!=NONE", needsReason = "Needs a theme animation"))

        val b = ThemeGroup.BACKGROUND
        add(ThemeField("backgroundImage", "Picture", b, "Picture", ThemeFieldKind.IMAGE, "A picture behind sheets and panels"))
        add(ThemeField("backgroundImageOpacity", "Opacity", b, "Picture", ThemeFieldKind.FLOAT, "How strongly the picture shows", 0f, 1f, 0.02f, needs = "backgroundImage?", needsReason = "Needs a picture"))
        add(ThemeField("backgroundImageBlur", "Blur", b, "Picture", ThemeFieldKind.FLOAT, "How blurred the picture is", 0f, 25f, 0.25f, "dp", needs = "backgroundImage?", needsReason = "Needs a picture"))
    }

    private val byPath = all.associateBy { it.path }

    fun field(path: String): ThemeField? = byPath[path]

    fun of(group: ThemeGroup): List<ThemeField> = all.filter { it.group == group }

    /** Whether [field]'s condition holds in [doc]; true when it has none. */
    fun applies(field: ThemeField, doc: ThemeDoc): Boolean {
        val need = field.needs ?: return true
        return when {
            need.endsWith("?") -> doc.element(need.dropLast(1)).let { it != null && it !is kotlinx.serialization.json.JsonNull }
            "!=" in need -> {
                // An option left out is at its default, which is the value excluded here (NONE).
                val (path, value) = need.split("!=", limit = 2)
                val v = doc.string(path) ?: return false
                v !in value.split('|')
            }
            "=" in need -> {
                val (path, value) = need.split("=", limit = 2)
                (doc.string(path) ?: "") in value.split('|')
            }
            else -> doc.bool(need) == true
        }
    }
}
