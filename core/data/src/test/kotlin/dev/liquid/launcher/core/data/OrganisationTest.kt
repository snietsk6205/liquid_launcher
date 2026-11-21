package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrganisationTest {
    private data class App(val name: String, val work: Boolean = false, val tags: List<String> = emptyList())

    private val apps = listOf(
        App("anki", tags = listOf("study")),
        App("maps"),
        App("teams", work = true, tags = listOf("study")),
        App("mail", work = true),
    )

    private fun names(tab: DrawerTabDef, workMode: String = "tab", known: Set<String> = setOf("study")) =
        OrganisationOps.filter(tab, apps, workMode, { it.work }, { it.tags }, known).map { it.name }

    @Test
    fun tabWithSeveralSourcesShowsAppsMatchingAny() {
        val org = OrganisationOps.addTab(Organisation(), "Mix", "tag:study")
        val id = org.tabs.last().id
        val mixed = OrganisationOps.setTabSources(org, id, listOf("tag:study", "untagged"))
        val tab = mixed.tabs.last()
        assertEquals(listOf("tag:study", "untagged"), tab.sources)
        assertEquals(listOf("anki", "maps", "teams", "mail"), names(tab))
        // The main tab keeps its main source.
        val main = OrganisationOps.setTabSources(mixed, "apps", listOf("tag:study"))
        assertEquals(listOf("personal", "tag:study"), main.tabs.first().sources)
    }

    @Test
    fun mainListAndWorkFollowTheWorkSetting() {
        val main = DrawerTabDef("a", "Apps", "personal")
        val work = DrawerTabDef("w", "Work", "work")
        assertEquals(listOf("anki", "maps"), names(main))
        assertEquals(listOf("teams", "mail"), names(work))
        assertEquals(listOf("anki", "maps", "teams", "mail"), names(main, "merged"))
        assertTrue(names(work, "merged").isEmpty())
        assertEquals(listOf("anki", "maps"), names(main, "hidden"))
    }

    @Test
    fun tagTabsListTaggedAppsAndOtherListsTheRest() {
        assertEquals(listOf("anki", "teams"), names(DrawerTabDef("s", "Study", "tag:study")))
        assertEquals(listOf("anki"), names(DrawerTabDef("s", "Study", "tag:study"), "hidden"))
        assertEquals(listOf("maps", "mail"), names(DrawerTabDef("o", "Other", "untagged")))
        // A deleted tag no longer counts, so its apps show under "Other" again.
        assertEquals(listOf("anki", "maps", "teams", "mail"), names(DrawerTabDef("o", "Other", "untagged"), known = emptySet()))
    }

    @Test
    fun tagsAreUniqueByNameAndDeletingOneRemovesItsTab() {
        var org = Organisation()
        val (withTag, tag) = OrganisationOps.addTag(org, " Study ")!!
        org = OrganisationOps.addTab(withTag, tag.name, "tag:${tag.id}")
        assertEquals("Study", tag.name)
        assertEquals(tag, OrganisationOps.addTag(org, "study")!!.second)
        assertEquals(3, org.tabs.size)
        org = OrganisationOps.renameTag(org, tag.id, "Uni")
        assertEquals("Uni", org.tabs.last().name)
        org = OrganisationOps.deleteTag(org, tag.id)
        assertTrue(org.tags.isEmpty())
        assertEquals(Organisation.DEFAULT_TABS, org.tabs)
    }

    @Test
    fun mainTabCannotBeRemovedButCanMove() {
        var org = Organisation()
        org = OrganisationOps.removeTab(org, "apps")
        assertNotNull(org.tabs.firstOrNull { it.id == "apps" })
        org = OrganisationOps.moveTab(org, 0, 1)
        assertEquals(listOf("work", "apps"), org.tabs.map { it.id })
    }
}
