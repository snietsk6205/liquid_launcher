package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyFormatEntitiesTest {
    @Test
    fun numericReferencesOfTheWebPageAreDecoded() {
        assertEquals(listOf("a: b", "c:d ' & é"), StudyFormat.lines("<p>a&#58; b<br>c&#x3A;d &#39; &amp; &#233;</p>").filter { it.isNotEmpty() })
        // Not a reference: kept as written.
        assertEquals(listOf("&#xZZ; &#;"), StudyFormat.lines("<p>&#xZZ; &#;</p>").filter { it.isNotEmpty() })
    }

    @Test
    fun launcherDataWrittenAsHtmlReadsAsJson() {
        val html = "<p>{\"v\"&#58;1,\"flashcards\"&#58;[{\"id\"&#58;\"x\",\"q\"&#58;\"\$f_n \\\\ge 0\$\",\"a\"&#58;\"ok\"}]}</p>"
        val feed = StudyFeedParser.parse(html)!!
        assertEquals("\$f_n \\ge 0\$", feed.cards.single().q)
    }
}
