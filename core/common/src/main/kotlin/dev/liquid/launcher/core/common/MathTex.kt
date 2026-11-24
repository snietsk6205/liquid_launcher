package dev.liquid.launcher.core.common

/*
 * 0.12.0 (D174, Med chose "Claude writes LaTeX, rendered"): the subset of LaTeX the flashcards use, read into a tree that
 * the home screen lays out and draws offline (MathText). `$…$` (or `\(…\)`) is a formula inside the text, `$$…$$` (or
 * `\[…\]`) one on its own line; text without them shows as before. The subset: subscripts and superscripts, \frac
 * (\dfrac, \tfrac, \binom), \sqrt with an index, big operators with their bounds (∫ ∑ ∏ ⋃ ⋂, lim, sup, inf, max, min,
 * liminf, limsup), Greek letters, arrows and relations, ∞, \mathbb, \mathcal, \mathbf, \mathrm, \text, \operatorname,
 * accents (\hat, \bar, \vec, \tilde, \dot, \overline), \left…\right and spacing. An unknown command shows as its name,
 * so nothing written is lost.
 */

/** A piece of a card's text: plain, a formula inside the text, or a formula on its own line. */
sealed interface MathSegment {
    data class Plain(val text: String) : MathSegment
    data class Inline(val tex: String) : MathSegment
    data class Display(val tex: String) : MathSegment
}

/** How a symbol is set: a variable in italic, a number or an upright symbol, a relation or a binary operator (spaced), punctuation. */
enum class MathKind { Variable, Ordinary, Relation, Binary, Punctuation, Open, Close }

/** A font a part of a formula is set in. */
enum class MathFont { Normal, Upright, Bold, Text }

sealed interface MathNode {
    data class Row(val items: List<MathNode>) : MathNode
    data class Symbol(val text: String, val kind: MathKind) : MathNode
    /** An operator name set upright (lim, sup, sin…); [limits]: its scripts go under and over in a display. */
    data class Name(val text: String, val limits: Boolean) : MathNode
    /** ∑ ∫ ∏ ⋃ ⋂: drawn larger; [limits]: the bounds go under and over in a display (not for integrals). */
    data class BigOp(val symbol: String, val limits: Boolean) : MathNode
    data class Scripts(val base: MathNode, val sub: MathNode?, val sup: MathNode?) : MathNode
    data class Frac(val num: MathNode, val den: MathNode, val bar: Boolean = true, val size: FracSize = FracSize.Auto) : MathNode
    data class Root(val body: MathNode, val index: MathNode?) : MathNode
    data class Accent(val body: MathNode, val accent: String, val wide: Boolean) : MathNode
    data class Delimited(val left: String, val body: MathNode, val right: String) : MathNode
    data class Space(val em: Float) : MathNode
    data class Styled(val body: MathNode, val font: MathFont) : MathNode
    data class Text(val text: String) : MathNode

    enum class FracSize { Auto, Display, Text }
}

object MathTex {
    /** Splits a card's text into plain text and formulas. A `$` that does not close stays a plain `$`; `\$` is a dollar. */
    fun split(text: String): List<MathSegment> {
        val out = ArrayList<MathSegment>()
        val plain = StringBuilder()
        fun flush() {
            if (plain.isNotEmpty()) out += MathSegment.Plain(plain.toString())
            plain.clear()
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length && text[i + 1] == '$') {
                plain.append('$'); i += 2; continue
            }
            val opener = when {
                text.startsWith("$$", i) -> "$$"
                text.startsWith("\\[", i) -> "\\["
                text.startsWith("\\(", i) -> "\\("
                c == '$' -> "$"
                else -> null
            }
            if (opener != null) {
                val closer = when (opener) { "\\[" -> "\\]"; "\\(" -> "\\)"; else -> opener }
                val end = findClose(text, i + opener.length, closer)
                if (end > i + opener.length) {
                    val tex = text.substring(i + opener.length, end).trim()
                    flush()
                    out += if (opener == "$$" || opener == "\\[") MathSegment.Display(tex) else MathSegment.Inline(tex)
                    i = end + closer.length
                    continue
                }
            }
            plain.append(c)
            i++
        }
        flush()
        return out
    }

    private fun findClose(text: String, from: Int, closer: String): Int {
        var j = from
        while (j < text.length) {
            if (text[j] == '\\' && closer == "$" && j + 1 < text.length && text[j + 1] == '$') { j += 2; continue }
            if (text.startsWith(closer, j)) {
                // A single $ must not be the first of a $$.
                if (closer == "$" && j + 1 < text.length && text[j + 1] == '$') return -1
                return j
            }
            j++
        }
        return -1
    }

    /** True when [text] holds a formula. */
    fun hasMath(text: String): Boolean = split(text).any { it !is MathSegment.Plain }

    /** Reads the LaTeX of one formula. */
    fun parse(tex: String): MathNode = Parser(tex).parseRow(stopAt = null)

    // ---- symbols ----

    private val greek = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ϵ", "varepsilon" to "ε", "zeta" to "ζ",
        "eta" to "η", "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ", "lambda" to "λ", "mu" to "μ", "nu" to "ν",
        "xi" to "ξ", "pi" to "π", "varpi" to "ϖ", "rho" to "ρ", "varrho" to "ϱ", "sigma" to "σ", "varsigma" to "ς", "tau" to "τ",
        "upsilon" to "υ", "phi" to "ϕ", "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ",
        "Upsilon" to "Υ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
    )

    private val relations = mapOf(
        "to" to "→", "rightarrow" to "→", "leftarrow" to "←", "gets" to "←", "Rightarrow" to "⇒", "Leftarrow" to "⇐",
        "Leftrightarrow" to "⇔", "leftrightarrow" to "↔", "iff" to "⟺", "implies" to "⟹", "impliedby" to "⟸", "mapsto" to "↦",
        "longrightarrow" to "⟶", "longmapsto" to "⟼", "uparrow" to "↑", "downarrow" to "↓", "nearrow" to "↗", "searrow" to "↘",
        "nearrow" to "↗", "hookrightarrow" to "↪", "rightharpoonup" to "⇀", "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥",
        "ne" to "≠", "neq" to "≠", "approx" to "≈", "sim" to "∼", "simeq" to "≃", "equiv" to "≡", "cong" to "≅", "propto" to "∝",
        "in" to "∈", "notin" to "∉", "ni" to "∋", "subset" to "⊂", "subseteq" to "⊆", "supset" to "⊃", "supseteq" to "⊇",
        "subsetneq" to "⊊", "ll" to "≪", "gg" to "≫", "perp" to "⊥", "parallel" to "∥", "mid" to "∣", "vdash" to "⊢",
        "models" to "⊨", "ll" to "≪", "nearrow" to "↗", "uparrow" to "↑", "coloneqq" to "≔", "triangleq" to "≜",
    )

    private val binaries = mapOf(
        "times" to "×", "cdot" to "·", "div" to "÷", "pm" to "±", "mp" to "∓", "circ" to "∘", "bullet" to "•", "ast" to "∗",
        "star" to "⋆", "cup" to "∪", "cap" to "∩", "setminus" to "∖", "oplus" to "⊕", "otimes" to "⊗", "wedge" to "∧",
        "land" to "∧", "vee" to "∨", "lor" to "∨", "triangle" to "△", "smallsetminus" to "∖",
    )

    private val ordinaries = mapOf(
        "infty" to "∞", "partial" to "∂", "nabla" to "∇", "forall" to "∀", "exists" to "∃", "nexists" to "∄", "neg" to "¬",
        "lnot" to "¬", "emptyset" to "∅", "varnothing" to "∅", "aleph" to "ℵ", "ell" to "ℓ", "hbar" to "ℏ", "Re" to "ℜ",
        "Im" to "ℑ", "wp" to "℘", "prime" to "′", "ldots" to "…", "dots" to "…", "cdots" to "⋯", "vdots" to "⋮", "ddots" to "⋱",
        "angle" to "∠", "top" to "⊤", "bot" to "⊥", "checkmark" to "✓", "dagger" to "†", "S" to "§", "P" to "¶", "%" to "%",
        "#" to "#", "&" to "&", "_" to "_", "{" to "{", "}" to "}", "lbrace" to "{", "rbrace" to "}", "|" to "‖", "Vert" to "‖",
        "vert" to "|", "langle" to "⟨", "rangle" to "⟩", "lfloor" to "⌊", "rfloor" to "⌋", "lceil" to "⌈", "rceil" to "⌉",
        "lbrack" to "[", "rbrack" to "]", "backslash" to "∖", "degree" to "°",
    )

    private val bigOps = mapOf(
        "sum" to ("∑" to true), "prod" to ("∏" to true), "coprod" to ("∐" to true), "bigcup" to ("⋃" to true),
        "bigcap" to ("⋂" to true), "bigoplus" to ("⨁" to true), "bigotimes" to ("⨂" to true), "bigvee" to ("⋁" to true),
        "bigwedge" to ("⋀" to true), "int" to ("∫" to false), "iint" to ("∬" to false), "iiint" to ("∭" to false),
        "oint" to ("∮" to false),
    )

    private val names = mapOf(
        "lim" to true, "liminf" to true, "limsup" to true, "sup" to true, "inf" to true, "max" to true, "min" to true,
        "det" to true, "Pr" to true, "gcd" to true, "argmax" to true, "argmin" to true, "esssup" to true,
        "sin" to false, "cos" to false, "tan" to false, "cot" to false, "sec" to false, "csc" to false, "arcsin" to false,
        "arccos" to false, "arctan" to false, "sinh" to false, "cosh" to false, "tanh" to false, "log" to false, "ln" to false,
        "lg" to false, "exp" to false, "dim" to false, "ker" to false, "deg" to false, "arg" to false, "hom" to false,
        "card" to false, "sgn" to false, "Var" to false, "Cov" to false, "tr" to false, "rank" to false, "id" to false,
    )

    private val nameText = mapOf("liminf" to "lim inf", "limsup" to "lim sup", "argmax" to "arg max", "argmin" to "arg min", "esssup" to "ess sup")

    private val spaces = mapOf(
        "," to 0.17f, ":" to 0.22f, ">" to 0.22f, ";" to 0.28f, "!" to -0.17f, " " to 0.25f, "quad" to 1f, "qquad" to 2f,
        "enspace" to 0.5f, "thinspace" to 0.17f,
    )

    private val accents = mapOf(
        "hat" to ("ˆ" to false), "widehat" to ("ˆ" to true), "bar" to ("¯" to false), "overline" to ("¯" to true),
        "vec" to ("→" to false), "overrightarrow" to ("→" to true), "tilde" to ("˜" to false), "widetilde" to ("˜" to true),
        "dot" to ("˙" to false), "ddot" to ("¨" to false), "check" to ("ˇ" to false), "breve" to ("˘" to false),
        "underline" to ("_" to true),
    )

    /** Double-struck capitals (\mathbb); those outside the basic plane come from the math alphanumerics. */
    fun blackboard(c: Char): String = when (c) {
        'C' -> "ℂ"; 'H' -> "ℍ"; 'N' -> "ℕ"; 'P' -> "ℙ"; 'Q' -> "ℚ"; 'R' -> "ℝ"; 'Z' -> "ℤ"
        in 'A'..'Z' -> String(Character.toChars(0x1D538 + (c - 'A')))
        in 'a'..'z' -> String(Character.toChars(0x1D552 + (c - 'a')))
        in '0'..'9' -> String(Character.toChars(0x1D7D8 + (c - '0')))
        else -> c.toString()
    }

    /** Script capitals (\mathcal). */
    fun script(c: Char): String = when (c) {
        'B' -> "ℬ"; 'E' -> "ℰ"; 'F' -> "ℱ"; 'H' -> "ℋ"; 'I' -> "ℐ"; 'L' -> "ℒ"; 'M' -> "ℳ"; 'R' -> "ℛ"
        in 'A'..'Z' -> String(Character.toChars(0x1D49C + (c - 'A')))
        else -> c.toString()
    }

    private class Parser(val s: String) {
        var i = 0

        fun parseRow(stopAt: Char?): MathNode {
            val items = ArrayList<MathNode>()
            while (i < s.length) {
                skipSpaces()
                if (i >= s.length) break
                val c = s[i]
                if (stopAt != null && c == stopAt) break
                if (c == '}' && stopAt == null) { i++; continue }
                if (c == '&' || (c == '\\' && s.startsWith("\\\\", i))) {
                    // Alignment and line breaks of environments are not drawn: a space keeps the parts apart.
                    i += if (c == '&') 1 else 2
                    items += MathNode.Space(0.5f)
                    continue
                }
                if (s.startsWith("\\right", i) && stopAt == RIGHT) break
                val atom = parseAtom() ?: continue
                items += scripts(atom)
            }
            return if (items.size == 1) items[0] else MathNode.Row(items)
        }

        /** Subscripts and superscripts after [base] (and primes). */
        fun scripts(base: MathNode): MathNode {
            var sub: MathNode? = null
            var sup: MathNode? = null
            while (i < s.length) {
                when (s[i]) {
                    '_' -> { i++; sub = parseArgument() }
                    '^' -> { i++; sup = parseArgument() }
                    '\'' -> {
                        var primes = ""
                        while (i < s.length && s[i] == '\'') { primes += "′"; i++ }
                        sup = if (sup == null) MathNode.Symbol(primes, MathKind.Ordinary) else MathNode.Row(listOf(MathNode.Symbol(primes, MathKind.Ordinary), sup))
                    }
                    else -> break
                }
                skipSpaces()
            }
            return if (sub == null && sup == null) base else MathNode.Scripts(base, sub, sup)
        }

        fun skipSpaces() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        /** One argument: {group}, a command, or one character. */
        fun parseArgument(): MathNode {
            skipSpaces()
            if (i >= s.length) return MathNode.Row(emptyList())
            if (s[i] == '{') {
                i++
                val row = parseRow(stopAt = '}')
                if (i < s.length && s[i] == '}') i++
                return row
            }
            return parseAtom() ?: MathNode.Row(emptyList())
        }

        /** A raw group's text ({…}) for \text and the fonts of letters. */
        fun rawGroup(): String {
            skipSpaces()
            if (i >= s.length) return ""
            if (s[i] != '{') return s[i++].toString()
            var depth = 0
            val start = i + 1
            while (i < s.length) {
                when (s[i]) {
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) { i++; return s.substring(start, i - 1) } }
                }
                i++
            }
            return s.substring(start)
        }

        fun optional(): MathNode? {
            skipSpaces()
            if (i >= s.length || s[i] != '[') return null
            i++
            val row = parseRow(stopAt = ']')
            if (i < s.length && s[i] == ']') i++
            return row
        }

        fun delimiter(): String {
            skipSpaces()
            if (i >= s.length) return ""
            if (s[i] == '\\') {
                val name = commandName()
                return when (name) {
                    "{", "lbrace" -> "{"; "}", "rbrace" -> "}"; "|", "Vert" -> "‖"; "vert" -> "|"
                    "langle" -> "⟨"; "rangle" -> "⟩"; "lfloor" -> "⌊"; "rfloor" -> "⌋"; "lceil" -> "⌈"; "rceil" -> "⌉"
                    else -> ""
                }
            }
            val c = s[i++]
            return if (c == '.') "" else c.toString()
        }

        fun commandName(): String {
            // At a backslash: a run of letters, or one other character.
            i++
            if (i >= s.length) return ""
            if (!s[i].isLetter()) return s[i++].toString()
            val start = i
            while (i < s.length && s[i].isLetter()) i++
            return s.substring(start, i)
        }

        fun parseAtom(): MathNode? {
            skipSpaces()
            if (i >= s.length) return null
            val c = s[i]
            when {
                c == '{' -> return parseArgument()
                c == '\\' -> return command(commandName())
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < s.length && (s[i].isDigit() || (s[i] == '.' && i + 1 < s.length && s[i + 1].isDigit()))) i++
                    if (i == start) i++
                    return MathNode.Symbol(s.substring(start, i), MathKind.Ordinary)
                }
                c.isLetter() -> { i++; return MathNode.Symbol(c.toString(), MathKind.Variable) }
                // A script with nothing before it: an empty base, the script itself read by [scripts].
                c == '^' || c == '_' -> return MathNode.Row(emptyList())
            }
            i++
            val kind = when (c) {
                '=', '<', '>', ':' -> MathKind.Relation
                '+', '-', '*', '/' -> if (c == '/') MathKind.Ordinary else MathKind.Binary
                ',', ';' -> MathKind.Punctuation
                '(', '[' -> MathKind.Open
                ')', ']' -> MathKind.Close
                '|' -> MathKind.Ordinary
                '~' -> return MathNode.Space(0.25f)
                else -> MathKind.Ordinary
            }
            val text = when (c) { '-' -> "−"; '*' -> "∗"; else -> c.toString() }
            return MathNode.Symbol(text, kind)
        }

        fun command(name: String): MathNode? {
            greek[name]?.let { return MathNode.Symbol(it, if (name[0].isUpperCase()) MathKind.Ordinary else MathKind.Variable) }
            relations[name]?.let { return MathNode.Symbol(it, MathKind.Relation) }
            binaries[name]?.let { return MathNode.Symbol(it, MathKind.Binary) }
            bigOps[name]?.let { (symbol, limits) -> return MathNode.BigOp(symbol, limits) }
            names[name]?.let { limits -> return MathNode.Name(nameText[name] ?: name, limits) }
            spaces[name]?.let { return MathNode.Space(it) }
            accents[name]?.let { (accent, wide) -> return MathNode.Accent(parseArgument(), accent, wide) }
            return when (name) {
                "frac", "cfrac" -> MathNode.Frac(parseArgument(), parseArgument())
                // An arrow with its label over it (an empty label: the arrow alone).
                "xrightarrow", "xleftarrow" -> {
                    optional()
                    val label = parseArgument()
                    val arrow = MathNode.Symbol(if (name == "xrightarrow") "⟶" else "⟵", MathKind.Relation)
                    if (label is MathNode.Row && label.items.isEmpty()) arrow else MathNode.Scripts(arrow, null, label)
                }
                "dfrac" -> MathNode.Frac(parseArgument(), parseArgument(), size = MathNode.FracSize.Display)
                "tfrac" -> MathNode.Frac(parseArgument(), parseArgument(), size = MathNode.FracSize.Text)
                "binom" -> MathNode.Delimited("(", MathNode.Frac(parseArgument(), parseArgument(), bar = false), ")")
                "sqrt" -> { val index = optional(); MathNode.Root(parseArgument(), index) }
                "left" -> {
                    val left = delimiter()
                    val body = parseRow(stopAt = RIGHT)
                    val right = if (s.startsWith("\\right", i)) { i += "\\right".length; delimiter() } else ""
                    MathNode.Delimited(left, body, right)
                }
                "right" -> null
                "big", "Big", "bigg", "Bigg", "bigl", "bigr", "Bigl", "Bigr", "biggl", "biggr" -> {
                    MathNode.Symbol(delimiter(), MathKind.Ordinary)
                }
                "mathbb" -> MathNode.Symbol(rawGroup().map { blackboard(it) }.joinToString(""), MathKind.Ordinary)
                "mathcal", "mathscr" -> MathNode.Symbol(rawGroup().map { script(it) }.joinToString(""), MathKind.Ordinary)
                "mathbf", "boldsymbol", "bm" -> MathNode.Styled(parseArgument(), MathFont.Bold)
                "mathrm", "mathsf", "mathtt", "rm" -> MathNode.Styled(parseArgument(), MathFont.Upright)
                "mathit" -> parseArgument()
                "text", "textrm", "textit", "textbf", "mbox", "textnormal" -> MathNode.Text(rawGroup())
                "operatorname" -> MathNode.Name(rawGroup(), limits = false)
                "displaystyle", "textstyle", "limits", "nolimits", "begin", "end" -> {
                    if (name == "begin" || name == "end") rawGroup()
                    null
                }
                "not" -> {
                    val next = parseAtom()
                    if (next is MathNode.Symbol) MathNode.Symbol(next.text + "̸", MathKind.Relation) else next
                }
                "pmod" -> MathNode.Row(listOf(MathNode.Space(0.5f), MathNode.Symbol("(mod ", MathKind.Ordinary), parseArgument(), MathNode.Symbol(")", MathKind.Ordinary)))
                "mod", "bmod" -> MathNode.Name("mod", limits = false)
                else -> ordinaries[name]?.let { MathNode.Symbol(it, if (it in OPEN) MathKind.Open else if (it in CLOSE) MathKind.Close else MathKind.Ordinary) }
                    ?: MathNode.Text(name)
            }
        }
    }

    private const val RIGHT = '\u0000'
    private val OPEN = setOf("{", "⟨", "⌊", "⌈", "[")
    private val CLOSE = setOf("}", "⟩", "⌋", "⌉", "]")
}
