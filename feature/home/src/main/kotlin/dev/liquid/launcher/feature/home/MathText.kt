package dev.liquid.launcher.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.common.MathFont
import dev.liquid.launcher.core.common.MathKind
import dev.liquid.launcher.core.common.MathNode
import dev.liquid.launcher.core.common.MathSegment
import dev.liquid.launcher.core.common.MathTex

/*
 * 0.12.0 (D174): formulas drawn offline, without a web page or a library. MathTex reads the LaTeX subset Claude writes on
 * the flashcards; this lays it out the way TeX would in spirit (variables in italic, numbers and operators upright,
 * relations and binary operators spaced, scripts raised and lowered, fractions and roots built, big operators with their
 * bounds under and over in a display) with a serif face, and draws it on a canvas. A formula inside the text is an
 * inline piece of the paragraph whose baseline sits on the text's; a formula on its own line is centred and scrolls
 * sideways when it is wider than the card.
 */

/** [text] with its `$…$` and `$$…$$` formulas drawn; text without them is an ordinary [Text]. */
@Composable
internal fun MathText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val segments = remember(text) { MathTex.split(text) }
    if (segments.none { it !is MathSegment.Plain }) {
        Text(text, style = style, modifier = modifier)
        return
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Paragraphs: plain text and inline formulas together; a display formula breaks them.
    val blocks = remember(segments) {
        val out = ArrayList<List<MathSegment>>()
        var current = ArrayList<MathSegment>()
        for (s in segments) {
            if (s is MathSegment.Display) {
                if (current.isNotEmpty()) out += current
                out += listOf(s)
                current = ArrayList()
            } else current += s
        }
        if (current.isNotEmpty()) out += current
        out.map { block -> block.map { if (it is MathSegment.Plain) MathSegment.Plain(it.text.trim('\n')) else it } }
    }
    Column(modifier) {
        for (block in blocks) {
            val display = block.singleOrNull() as? MathSegment.Display
            if (display != null) {
                DisplayFormula(display.tex, style, measurer, density)
            } else {
                InlineParagraph(block, style, measurer, density)
            }
        }
    }
}

@Composable
private fun InlineParagraph(block: List<MathSegment>, style: TextStyle, measurer: TextMeasurer, density: Density) {
    val color = style.color.takeOrElse(Color.White)
    val built = remember(block, style, density) {
        val layout = MathLayout(measurer, color, with(density) { style.fontSize.toPx() }, density, style.fontFamily)
        // Where the text's centre lies above its baseline: a formula is padded so its baseline lands on the text's.
        val reference = measurer.measure(AnnotatedString("x"), style.copy(lineHeight = TextUnit.Unspecified))
        val centre = reference.firstBaseline - reference.size.height / 2f
        val content = HashMap<String, InlineTextContent>()
        val annotated = buildAnnotatedString {
            block.forEachIndexed { i, segment ->
                when (segment) {
                    is MathSegment.Plain -> append(segment.text)
                    is MathSegment.Inline -> {
                        val box = layout.formula(segment.tex, display = false)
                        val ascent = maxOf(box.ascent, box.descent + 2f * centre)
                        val descent = ascent - 2f * centre
                        val id = "math$i"
                        content[id] = InlineTextContent(
                            Placeholder(
                                with(density) { box.width.toSp() }, with(density) { (ascent + descent).toSp() },
                                PlaceholderVerticalAlign.TextCenter,
                            ),
                        ) {
                            Canvas(Modifier.fillMaxSize()) { box.draw(this, 0f, ascent) }
                        }
                        appendInlineContent(id, segment.tex)
                    }
                    is MathSegment.Display -> Unit
                }
            }
        }
        annotated to content
    }
    Text(built.first, style = style, inlineContent = built.second)
}

@Composable
private fun DisplayFormula(tex: String, style: TextStyle, measurer: TextMeasurer, density: Density) {
    val color = style.color.takeOrElse(Color.White)
    val box = remember(tex, style, density) {
        MathLayout(measurer, color, with(density) { style.fontSize.toPx() } * 1.08f, density, style.fontFamily).formula(tex, display = true)
    }
    Box(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).horizontalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        with(density) {
            Canvas(Modifier.size(box.width.toDp(), (box.ascent + box.descent).toDp())) { box.draw(this, 0f, box.ascent) }
        }
    }
}

private fun Color.takeOrElse(other: Color): Color = if (this == Color.Unspecified) other else this

/** A laid-out part of a formula: [width], [ascent] above its baseline and [descent] below; [draw] at (x, baseline). */
internal class MathBox(
    val width: Float,
    val ascent: Float,
    val descent: Float,
    val kind: MathKind = MathKind.Ordinary,
    /** An operator name or a big operator: a thin space before what follows it. */
    val operator: Boolean = false,
    /** It ends with an italic letter leaning this far (px) over what follows (TeX's italic correction); 0: upright. */
    val lean: Float = 0f,
    private val painter: DrawScope.(Float, Float) -> Unit,
) {
    fun draw(scope: DrawScope, x: Float, baseline: Float) = scope.painter(x, baseline)
}

/** Lays a formula out at a text size of [basePx]. */
internal class MathLayout(
    private val measurer: TextMeasurer,
    private val color: Color,
    private val basePx: Float,
    private val density: Density,
    private val textFamily: FontFamily?,
) {
    private val serif = FontFamily.Serif

    private data class Style(val size: Float, val display: Boolean, val level: Int, val font: MathFont)

    fun formula(tex: String, display: Boolean): MathBox =
        node(MathTex.parse(tex), Style(basePx, display, 0, MathFont.Normal))

    private fun script(st: Style) = st.copy(size = st.size * (if (st.level == 0) 0.71f else 0.8f), display = false, level = minOf(2, st.level + 1))

    // ---- glyphs ----

    private fun glyph(text: String, st: Style, italic: Boolean, kind: MathKind, family: FontFamily? = serif, scale: Float = 1f): MathBox {
        val size = st.size * scale
        val bold = st.font == MathFont.Bold
        val textStyle = TextStyle(
            color = color,
            fontSize = with(density) { size.toSp() },
            fontFamily = family,
            fontStyle = if (italic && st.font == MathFont.Normal) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
        val result = measurer.measure(AnnotatedString(text), textStyle, softWrap = false, maxLines = 1)
        val baseline = result.firstBaseline
        // The ink, not the font's whole line: TeX places scripts and bars by the glyphs' own heights.
        val ascent = inkAscent(text) * size
        val descent = inkDescent(text) * size
        // f and j lean furthest (TeX's italic correction is largest for them).
        val lean = if (!italic || st.font != MathFont.Normal) 0f else if (text.last() in "fjJ") 0.15f * size else 0.07f * size
        return MathBox(result.size.width.toFloat(), ascent, descent, kind, lean = lean) { x, y ->
            drawText(result, topLeft = Offset(x, y - baseline))
        }
    }

    private fun inkAscent(text: String): Float = when {
        text.any { it in TALL } -> 0.75f
        text.any { it.isUpperCase() || it.isDigit() || it in ASCENDERS } -> 0.7f
        text.any { it in OPERATORS } -> 0.55f
        else -> 0.47f
    }

    private fun inkDescent(text: String): Float = when {
        text.any { it in TALL } -> 0.25f
        text.any { it in DESCENDERS } -> 0.22f
        text.any { it in OPERATORS } -> 0.02f
        else -> 0f
    }

    // ---- nodes ----

    private fun node(n: MathNode, st: Style): MathBox = when (n) {
        is MathNode.Row -> row(n.items, st)
        is MathNode.Symbol -> symbol(n, st)
        is MathNode.Name -> glyph(n.text, st, italic = false, kind = MathKind.Ordinary).let { b ->
            MathBox(b.width, b.ascent, b.descent, MathKind.Ordinary, operator = true) { x, y -> b.draw(this, x, y) }
        }
        is MathNode.BigOp -> bigOp(n, st)
        is MathNode.Scripts -> scripts(n, st)
        is MathNode.Frac -> frac(n, st)
        is MathNode.Root -> root(n, st)
        is MathNode.Accent -> accent(n, st)
        is MathNode.Delimited -> delimited(n, st)
        is MathNode.Space -> MathBox(n.em * st.size, 0f, 0f) { _, _ -> }
        is MathNode.Styled -> node(n.body, st.copy(font = n.font))
        is MathNode.Text -> glyph(n.text, st, italic = false, kind = MathKind.Ordinary, family = textFamily)
    }

    private fun symbol(n: MathNode.Symbol, st: Style): MathBox {
        val italic = n.kind == MathKind.Variable && st.font != MathFont.Upright
        return glyph(n.text, st, italic, n.kind)
    }

    private fun row(items: List<MathNode>, st: Style): MathBox {
        if (items.isEmpty()) return MathBox(0f, 0f, 0f) { _, _ -> }
        val boxes = items.map { node(it, st) }
        val gaps = FloatArray(boxes.size)
        val em = st.size
        for (k in 1 until boxes.size) {
            val prev = boxes[k - 1]
            val cur = boxes[k]
            // An italic letter leans into an upright neighbour ("f|", "x)", "f ="): TeX's italic correction.
            val lean = if (prev.lean > 0f && cur.lean == 0f && cur.kind != MathKind.Binary) prev.lean else 0f
            gaps[k] = lean + when {
                st.level > 0 -> if (prev.operator) 0.1f * em else 0f
                cur.kind == MathKind.Relation || prev.kind == MathKind.Relation -> if (prev.kind == MathKind.Open || cur.kind == MathKind.Close) 0f else 0.28f * em
                cur.kind == MathKind.Binary && prev.kind !in UNARY_BEFORE -> 0.22f * em
                prev.kind == MathKind.Binary && k >= 2 && boxes[k - 2].kind !in UNARY_BEFORE -> 0.22f * em
                prev.kind == MathKind.Punctuation -> 0.17f * em
                prev.operator && cur.kind != MathKind.Open && cur.kind != MathKind.Punctuation -> 0.17f * em
                else -> 0f
            }
        }
        val width = boxes.sumOf { it.width.toDouble() }.toFloat() + gaps.sum()
        val ascent = boxes.maxOf { it.ascent }
        val descent = boxes.maxOf { it.descent }
        val kind = if (boxes.size == 1) boxes[0].kind else MathKind.Ordinary
        return MathBox(width, ascent, descent, kind, operator = boxes.size == 1 && boxes[0].operator, lean = boxes.last().lean) { x, y ->
            var cx = x
            boxes.forEachIndexed { k, b ->
                cx += gaps[k]
                b.draw(this, cx, y)
                cx += b.width
            }
        }
    }

    private fun bigOp(n: MathNode.BigOp, st: Style): MathBox {
        val integral = !n.limits
        val scale = when {
            st.display && integral -> 1.75f
            st.display -> 1.45f
            integral -> 1.25f
            else -> 1.1f
        }
        val g = glyph(n.symbol, st, italic = false, kind = MathKind.Ordinary, scale = scale)
        // Centred on the math axis, as TeX centres its big operators.
        val axis = AXIS * st.size
        val shift = (g.ascent - g.descent) / 2f - axis
        return MathBox(g.width, g.ascent - shift, g.descent + shift, MathKind.Ordinary, operator = true) { x, y ->
            g.draw(this, x, y + shift)
        }
    }

    private fun scripts(n: MathNode.Scripts, st: Style): MathBox {
        val base = node(n.base, st)
        val small = script(st)
        val sub = n.sub?.let { node(it, small) }
        val sup = n.sup?.let { node(it, small) }
        val em = st.size
        val limits = st.display && ((n.base is MathNode.BigOp && (n.base as MathNode.BigOp).limits) || (n.base is MathNode.Name && (n.base as MathNode.Name).limits))
        if (limits) {
            // Bounds under and over the operator, centred.
            val gap = 0.12f * em
            val width = maxOf(base.width, sub?.width ?: 0f, sup?.width ?: 0f)
            val ascent = base.ascent + (sup?.let { gap + it.descent + it.ascent } ?: 0f)
            val descent = base.descent + (sub?.let { gap + it.ascent + it.descent } ?: 0f)
            return MathBox(width, ascent, descent, MathKind.Ordinary, operator = true) { x, y ->
                base.draw(this, x + (width - base.width) / 2f, y)
                sup?.let { it.draw(this, x + (width - it.width) / 2f, y - base.ascent - gap - it.descent) }
                sub?.let { it.draw(this, x + (width - it.width) / 2f, y + base.descent + gap + it.ascent) }
            }
        }
        val overArrow = (n.base as? MathNode.Symbol)?.text?.let { it == "⟶" || it == "⟵" } == true && n.sub == null
        if (overArrow && sup != null) {
            // \xrightarrow{label}: the arrow stretches under its label, centred.
            val axis = AXIS * em
            val rule = maxOf(0.05f * em, 1f)
            val width = maxOf(sup.width + 0.5f * em, 1.2f * em)
            val gap = 0.08f * em
            val ascent = axis + gap + sup.descent + sup.ascent
            return MathBox(width, ascent, 0.1f * em, MathKind.Relation) { x, y ->
                val ay = y - axis
                val right = (n.base as MathNode.Symbol).text == "⟶"
                drawLine(color, Offset(x, ay), Offset(x + width, ay), strokeWidth = rule)
                val tip = if (right) x + width else x
                val back = if (right) -0.14f * em else 0.14f * em
                drawLine(color, Offset(tip, ay), Offset(tip + back, ay - 0.09f * em), strokeWidth = rule, cap = StrokeCap.Round)
                drawLine(color, Offset(tip, ay), Offset(tip + back, ay + 0.09f * em), strokeWidth = rule, cap = StrokeCap.Round)
                sup.draw(this, x + (width - sup.width) / 2f, ay - gap - sup.descent)
            }
        }
        val integral = n.base is MathNode.BigOp && !(n.base as MathNode.BigOp).limits
        var supShift = sup?.let { maxOf(0.36f * em, base.ascent - 0.25f * em, it.descent + 0.25f * em) } ?: 0f
        var subShift = sub?.let { maxOf(if (sup != null) 0.24f * em else 0.16f * em, base.descent + 0.05f * em, it.ascent - 0.38f * em) } ?: 0f
        if (sub != null && sup != null) {
            // Keep the two apart.
            val clash = 0.1f * em - ((supShift - sup.descent) - (sub.ascent - subShift))
            if (clash > 0f) {
                supShift += clash / 2f
                subShift += clash / 2f
            }
        }
        // A superscript clears the top of an italic letter (k², π²); an integral's leans further.
        val supX = when {
            integral -> 0.14f * em
            base.lean > 0f -> maxOf(0.1f * em, base.lean)
            else -> 0.03f * em
        }
        val subX = if (integral) -0.08f * em else 0f
        val scriptWidth = maxOf((sup?.width ?: 0f) + supX, (sub?.width ?: 0f) + subX)
        val width = base.width + scriptWidth + 0.04f * em
        val ascent = maxOf(base.ascent, sup?.let { supShift + it.ascent } ?: 0f)
        val descent = maxOf(base.descent, sub?.let { subShift + it.descent } ?: 0f)
        return MathBox(width, ascent, descent, base.kind, operator = base.operator) { x, y ->
            base.draw(this, x, y)
            sup?.draw(this, x + base.width + supX, y - supShift)
            sub?.draw(this, x + base.width + subX, y + subShift)
        }
    }

    private fun frac(n: MathNode.Frac, st: Style): MathBox {
        val inner = when {
            n.size == MathNode.FracSize.Display -> st.copy(display = true)
            n.size == MathNode.FracSize.Text -> script(st)
            st.display -> st.copy(display = false)
            else -> st.copy(size = st.size * 0.8f, display = false, level = minOf(2, st.level + 1))
        }
        val num = node(n.num, inner)
        val den = node(n.den, inner)
        val em = st.size
        val rule = maxOf(0.05f * em, 1f)
        val axis = AXIS * em
        val gap = (if (st.display) 0.16f else 0.1f) * em
        val up = axis + rule / 2f + gap + num.descent
        val down = den.ascent + gap + rule / 2f - axis
        val pad = 0.1f * em
        val width = maxOf(num.width, den.width) + 2 * pad
        return MathBox(width, up + num.ascent, down + den.descent, MathKind.Ordinary) { x, y ->
            num.draw(this, x + (width - num.width) / 2f, y - up)
            den.draw(this, x + (width - den.width) / 2f, y + down)
            if (n.bar) drawLine(color, Offset(x + pad * 0.5f, y - axis), Offset(x + width - pad * 0.5f, y - axis), strokeWidth = rule)
        }
    }

    private fun root(n: MathNode.Root, st: Style): MathBox {
        val body = node(n.body, st)
        val em = st.size
        val rule = maxOf(0.05f * em, 1f)
        val clear = 0.12f * em
        val sign = 0.55f * em
        val index = n.index?.let { node(it, script(script(st))) }
        val lead = index?.let { maxOf(0f, it.width - sign * 0.45f) } ?: 0f
        val top = body.ascent + clear + rule
        val bottom = body.descent + 0.06f * em
        val width = lead + sign + body.width + 0.12f * em
        val ascent = maxOf(top + rule, index?.let { top * 0.45f + it.ascent + it.descent } ?: 0f)
        return MathBox(width, ascent, bottom, MathKind.Ordinary) { x, y ->
            val left = x + lead
            val path = Path().apply {
                moveTo(left, y - top * 0.32f)
                lineTo(left + sign * 0.22f, y - top * 0.42f)
                lineTo(left + sign * 0.5f, y + bottom)
                lineTo(left + sign, y - top)
                lineTo(left + sign + body.width + 0.12f * em, y - top)
            }
            drawPath(path, color, style = Stroke(width = rule * 1.15f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            index?.draw(this, x, y - top * 0.45f - index.descent)
            body.draw(this, left + sign + 0.06f * em, y)
        }
    }

    private fun accent(n: MathNode.Accent, st: Style): MathBox {
        val body = node(n.body, st)
        val em = st.size
        val rule = maxOf(0.05f * em, 1f)
        return when {
            n.accent == "_" -> MathBox(body.width, body.ascent, body.descent + 0.15f * em, body.kind) { x, y ->
                body.draw(this, x, y)
                drawLine(color, Offset(x, y + body.descent + 0.1f * em), Offset(x + body.width, y + body.descent + 0.1f * em), strokeWidth = rule)
            }
            n.accent == "¯" || (n.wide && n.accent != "→") -> MathBox(body.width, body.ascent + 0.16f * em, body.descent, body.kind) { x, y ->
                body.draw(this, x, y)
                val lineY = y - body.ascent - 0.1f * em
                drawLine(color, Offset(x + 0.04f * em, lineY), Offset(x + body.width - 0.02f * em, lineY), strokeWidth = rule)
            }
            n.accent == "→" -> MathBox(body.width, body.ascent + 0.24f * em, body.descent, body.kind) { x, y ->
                body.draw(this, x, y)
                val ay = y - body.ascent - 0.14f * em
                val x1 = x + 0.04f * em
                val x2 = x + maxOf(body.width, 0.45f * em)
                drawLine(color, Offset(x1, ay), Offset(x2, ay), strokeWidth = rule)
                drawLine(color, Offset(x2, ay), Offset(x2 - 0.12f * em, ay - 0.08f * em), strokeWidth = rule)
                drawLine(color, Offset(x2, ay), Offset(x2 - 0.12f * em, ay + 0.08f * em), strokeWidth = rule)
            }
            else -> MathBox(body.width, body.ascent + 0.24f * em, body.descent, body.kind, lean = body.lean) { x, y ->
                // Drawn, not taken from a font: the accents of fonts sit at different heights.
                body.draw(this, x, y)
                val cx = x + body.width / 2f + (if (body.lean > 0f) 0.06f * em else 0f)
                val base = y - body.ascent - 0.07f * em
                val h = 0.11f * em
                val w = 0.16f * em
                val stroke = Stroke(width = rule, cap = StrokeCap.Round, join = StrokeJoin.Round)
                when (n.accent) {
                    "ˆ" -> drawPath(Path().apply { moveTo(cx - w, base); lineTo(cx, base - h); lineTo(cx + w, base) }, color, style = stroke)
                    "ˇ" -> drawPath(Path().apply { moveTo(cx - w, base - h); lineTo(cx, base); lineTo(cx + w, base - h) }, color, style = stroke)
                    "˜" -> drawPath(Path().apply {
                        moveTo(cx - w, base - h * 0.3f)
                        cubicTo(cx - w * 0.5f, base - h * 1.2f, cx + w * 0.1f, base + h * 0.4f, cx + w, base - h * 0.8f)
                    }, color, style = stroke)
                    "˙" -> drawCircle(color, rule * 1.3f, Offset(cx, base - h * 0.5f))
                    "¨" -> {
                        drawCircle(color, rule * 1.3f, Offset(cx - w * 0.55f, base - h * 0.5f))
                        drawCircle(color, rule * 1.3f, Offset(cx + w * 0.55f, base - h * 0.5f))
                    }
                    else -> drawPath(Path().apply { moveTo(cx - w, base - h); quadraticTo(cx, base + h * 0.4f, cx + w, base - h) }, color, style = stroke)
                }
            }
        }
    }

    private fun delimited(n: MathNode.Delimited, st: Style): MathBox {
        val body = node(n.body, st)
        val em = st.size
        val axis = AXIS * em
        val half = maxOf(body.ascent - axis, body.descent + axis, 0.5f * em) + 0.08f * em
        fun fence(text: String): MathBox? {
            if (text.isEmpty()) return null
            val g = glyph(text, st, italic = false, kind = MathKind.Ordinary)
            val natural = (g.ascent + g.descent).coerceAtLeast(1f)
            val k = (2f * half / natural).coerceAtLeast(1f)
            if (k < 1.12f) return g
            // Stretched to the body's height around the axis (a scaled glyph, as a phone has no extensible fences).
            val glyphMid = (g.ascent - g.descent) / 2f
            return MathBox(g.width * minOf(1.25f, 1f + (k - 1f) * 0.12f), axis + half, half - axis, MathKind.Ordinary) { x, y ->
                // The glyph's ink centred on the axis, then stretched around that point.
                withTransform({ scale(1f, k, pivot = Offset(x, y - axis)) }) {
                    g.draw(this, x, y - axis + glyphMid)
                }
            }
        }
        val left = fence(n.left)
        val right = fence(n.right)
        val pad = 0.06f * em
        val width = (left?.width ?: 0f) + body.width + (right?.width ?: 0f) + 2 * pad
        val ascent = maxOf(body.ascent, left?.ascent ?: 0f, right?.ascent ?: 0f)
        val descent = maxOf(body.descent, left?.descent ?: 0f, right?.descent ?: 0f)
        return MathBox(width, ascent, descent, MathKind.Ordinary) { x, y ->
            var cx = x
            left?.let { it.draw(this, cx, y); cx += it.width }
            cx += pad
            body.draw(this, cx, y)
            cx += body.width + pad
            right?.draw(this, cx, y)
        }
    }

    private companion object {
        /** The math axis (where fraction bars and the middle of big operators sit), above the baseline, in em. */
        const val AXIS = 0.25f
        const val TALL = "()[]{}|‖⟨⟩⌊⌋⌈⌉∫∬∭∮∑∏∐⋃⋂√/"
        const val ASCENDERS = "bdfhklt∂ℓβδζθλξϑ∞ℝℕℤℚℂℙ∀∃∄∇ΓΔΘΛΞΠΣΥΦΨΩ′"
        const val DESCENDERS = "gjpqyfβγζημξρςφχψϕ,;"
        const val OPERATORS = "+−=<>≤≥≠≈∼≃≡≅∝∈∉∋⊂⊆⊃⊇∪∩×·÷±∓∘→←⇒⇐⇔↔⟹⟸⟺↦⟶↪"
        val UNARY_BEFORE = setOf(MathKind.Open, MathKind.Binary, MathKind.Relation, MathKind.Punctuation)
    }
}
