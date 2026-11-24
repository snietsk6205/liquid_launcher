package dev.liquid.launcher.core.common

import dev.liquid.launcher.core.common.MathNode.BigOp
import dev.liquid.launcher.core.common.MathNode.Frac
import dev.liquid.launcher.core.common.MathNode.Name
import dev.liquid.launcher.core.common.MathNode.Root
import dev.liquid.launcher.core.common.MathNode.Row
import dev.liquid.launcher.core.common.MathNode.Scripts
import dev.liquid.launcher.core.common.MathNode.Symbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MathTexTest {
    @Test
    fun textWithoutDollarsStaysPlain() {
        val text = "Aucune domination n'est requise (coût : 5 \$ ?)"
        assertEquals(listOf(MathSegment.Plain(text)), MathTex.split(text))
        assertFalse(MathTex.hasMath(text))
    }

    @Test
    fun inlineAndDisplayFormulasAreFound() {
        // Med's card of 2026-11-21, as Claude would now write it.
        val card = "Si \$(f_n)\$ est croissante et \$f = \\lim f_n\$, alors\n\$\$\\int f_n \\, d\\mu \\to \\int f \\, d\\mu\$\$ (la limite peut valoir \$+\\infty\$)."
        val parts = MathTex.split(card)
        assertEquals(MathSegment.Plain("Si "), parts[0])
        assertEquals(MathSegment.Inline("(f_n)"), parts[1])
        assertEquals(MathSegment.Inline("f = \\lim f_n"), parts[3])
        assertEquals(MathSegment.Display("\\int f_n \\, d\\mu \\to \\int f \\, d\\mu"), parts[5])
        assertEquals(MathSegment.Inline("+\\infty"), parts[7])
        assertEquals(MathSegment.Plain(")."), parts.last())
    }

    @Test
    fun otherDelimitersAndEscapedDollars() {
        assertEquals(listOf(MathSegment.Plain("a "), MathSegment.Inline("x^2")), MathTex.split("a \\(x^2\\)"))
        assertEquals(listOf(MathSegment.Display("x")), MathTex.split("\\[x\\]"))
        assertEquals(listOf(MathSegment.Plain("5\$ and 6\$")), MathTex.split("5\\\$ and 6\\\$"))
        assertEquals(listOf(MathSegment.Plain("one \$ alone")), MathTex.split("one \$ alone"))
    }

    @Test
    fun subscriptsSuperscriptsAndPrimes() {
        assertEquals(Scripts(Symbol("f", MathKind.Variable), Symbol("n", MathKind.Variable), null), MathTex.parse("f_n"))
        assertEquals(Scripts(Symbol("x", MathKind.Variable), Symbol("i", MathKind.Variable), Symbol("2", MathKind.Ordinary)), MathTex.parse("x_i^2"))
        val prime = MathTex.parse("f'") as Scripts
        assertEquals(Symbol("′", MathKind.Ordinary), prime.sup)
        val group = MathTex.parse("e^{-x^2}") as Scripts
        assertTrue(group.sup is Row)
    }

    @Test
    fun fractionsRootsAndBigOperators() {
        assertEquals(Frac(Symbol("1", MathKind.Ordinary), Symbol("n", MathKind.Variable)), MathTex.parse("\\frac{1}{n}"))
        assertEquals(Root(Symbol("x", MathKind.Variable), Symbol("3", MathKind.Ordinary)), MathTex.parse("\\sqrt[3]{x}"))
        val sum = MathTex.parse("\\sum_{k=1}^{\\infty} a_k") as Row
        val bounds = sum.items[0] as Scripts
        assertEquals(BigOp("∑", true), bounds.base)
        assertTrue(bounds.sup == Symbol("∞", MathKind.Ordinary))
        val integral = MathTex.parse("\\int_0^1 f") as Row
        assertEquals(BigOp("∫", false), (integral.items[0] as Scripts).base)
        val lim = MathTex.parse("\\lim_{n \\to \\infty}") as Scripts
        assertEquals(Name("lim", true), lim.base)
        assertEquals(Name("lim inf", true), (MathTex.parse("\\liminf_n") as Scripts).base)
    }

    @Test
    fun lettersSymbolsAndText() {
        assertEquals(Symbol("ℝ", MathKind.Ordinary), MathTex.parse("\\mathbb{R}"))
        assertEquals(Symbol("μ", MathKind.Variable), MathTex.parse("\\mu"))
        assertEquals(Symbol("→", MathKind.Relation), MathTex.parse("\\to"))
        assertEquals(Symbol("≤", MathKind.Relation), MathTex.parse("\\le"))
        assertEquals(MathNode.Text("presque partout"), MathTex.parse("\\text{presque partout}"))
        assertEquals(Symbol("𝔼", MathKind.Ordinary), MathTex.parse("\\mathbb E"))
        // An unknown command keeps its name.
        assertEquals(MathNode.Text("foo"), MathTex.parse("\\foo"))
    }

    @Test
    fun leftRightAndEnvironmentsDoNotLoseContent() {
        val d = MathTex.parse("\\left( \\frac{a}{b} \\right)") as MathNode.Delimited
        assertEquals("(", d.left)
        assertEquals(")", d.right)
        assertTrue(d.body is Frac)
        val cases = MathTex.parse("\\begin{cases} 1 & x>0 \\\\ 0 & \\text{sinon} \\end{cases}") as Row
        assertTrue(cases.items.any { it == MathNode.Text("sinon") })
    }
}
