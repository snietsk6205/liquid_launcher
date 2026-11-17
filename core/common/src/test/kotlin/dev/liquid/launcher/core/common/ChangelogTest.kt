package dev.liquid.launcher.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChangelogTest {
    private val sample = """
        # Changelog

        Intro text.

        ## 0.10.0 — 2026-11-17

        ### Added
        - A changelog. The Updates screen shows it,
          and a card after an update.
        - Second item.

        ### Removed
        - The old thing.

        ## 0.9.23 — 2026-11-17

        *Not published on its own: it came in 0.10.0.*

        ### Added
        - Calendar options.
    """.trimIndent()

    @Test
    fun parsesVersionsNewestFirst() {
        val versions = Changelog.parse(sample)
        assertEquals(listOf("0.10.0", "0.9.23"), versions.map { it.version })
        assertEquals("2026-11-17", versions[0].date)
        assertEquals(listOf("Added", "Removed"), versions[0].parts.map { it.title })
        assertEquals(3, versions[0].itemCount)
    }

    @Test
    fun joinsWrappedItems() {
        val added = Changelog.parse(sample)[0].parts[0]
        assertEquals("A changelog. The Updates screen shows it, and a card after an update.", added.items[0])
    }

    @Test
    fun readsTheNote() {
        val v = Changelog.find(sample, "v0.9.23")
        assertNotNull(v)
        assertEquals("Not published on its own: it came in 0.10.0.", v!!.note)
        assertNull(Changelog.parse(sample)[0].note)
    }

    @Test
    fun readsReleaseNotesWithTheBuildLine() {
        // GitHub's release body: the section without its heading, then a rule and the build line.
        val body = "### Fixed\n- A fix.\n\n---\nBuild 80 from `abc`. Install the APK below.\n"
        val v = Changelog.parseSection("0.10.0", null, body)
        assertEquals(1, v.parts.size)
        assertEquals(listOf("A fix."), v.parts[0].items)
    }

    @Test
    fun theRealChangelogIsWellFormed() {
        val file = listOf(File("../../CHANGELOG.md"), File("CHANGELOG.md")).firstOrNull { it.exists() } ?: return
        val versions = Changelog.parse(file.readText())
        assertTrue("no versions found", versions.isNotEmpty())
        for (v in versions) {
            assertTrue("${v.version} has no items", v.itemCount > 0)
            for (p in v.parts) assertTrue("${v.version}: unknown part ${p.title}", p.title in setOf("Added", "Changed", "Removed", "Fixed"))
        }
        assertNotNull(versions.firstOrNull { it.version == "0.9.0" })
    }
}
