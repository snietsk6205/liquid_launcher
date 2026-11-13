package dev.liquid.launcher.core.data

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/** A name apps can be given, for filtering the drawer and search. */
@Serializable
data class Tag(val id: String, val name: String)

/**
 * One page of the drawer. [source] says which apps it shows:
 * "personal" (the main list), "work", "untagged", or "tag:<id>".
 */
@Serializable
data class DrawerTabDef(
    val id: String,
    val name: String,
    val source: String,
)

/** Tags and drawer tabs, stored together in `organisation.json`. */
@Serializable
data class Organisation(
    val tags: List<Tag> = emptyList(),
    val tabs: List<DrawerTabDef> = DEFAULT_TABS,
) {
    fun tag(id: String): Tag? = tags.firstOrNull { it.id == id }

    companion object {
        val DEFAULT_TABS = listOf(
            DrawerTabDef("apps", "Apps", "personal"),
            DrawerTabDef("work", "Work", "work"),
        )
    }
}

/** Pure operations on [Organisation], kept free of Android for unit tests. */
object OrganisationOps {

    fun addTag(org: Organisation, name: String): Pair<Organisation, Tag>? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        org.tags.firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return org to it }
        val tag = Tag(LayoutOps.newId(), clean)
        return org.copy(tags = org.tags + tag) to tag
    }

    fun renameTag(org: Organisation, id: String, name: String): Organisation {
        val clean = name.trim().ifEmpty { return org }
        return org.copy(
            tags = org.tags.map { if (it.id == id) it.copy(name = clean) else it },
            // A tab that still carries the tag's old name follows the rename.
            tabs = org.tabs.map { tab ->
                val old = org.tag(id)?.name
                if (tab.source == "tag:$id" && tab.name == old) tab.copy(name = clean) else tab
            },
        )
    }

    /** Removes the tag and any tab built from it. Apps keep a dangling id, which is ignored. */
    fun deleteTag(org: Organisation, id: String): Organisation =
        org.copy(tags = org.tags.filter { it.id != id }, tabs = org.tabs.filter { it.source != "tag:$id" })

    fun addTab(org: Organisation, name: String, source: String): Organisation {
        if (org.tabs.any { it.source == source }) return org
        return org.copy(tabs = org.tabs + DrawerTabDef(LayoutOps.newId(), name.trim().ifEmpty { "Tab" }, source))
    }

    fun renameTab(org: Organisation, id: String, name: String): Organisation {
        val clean = name.trim().ifEmpty { return org }
        return org.copy(tabs = org.tabs.map { if (it.id == id) it.copy(name = clean) else it })
    }

    /** The main tab ("personal") cannot be removed: the drawer always has a list of everything. */
    fun removeTab(org: Organisation, id: String): Organisation =
        org.copy(tabs = org.tabs.filter { it.id != id || it.source == "personal" })

    fun moveTab(org: Organisation, from: Int, to: Int): Organisation {
        if (from !in org.tabs.indices || to !in org.tabs.indices) return org
        val tabs = org.tabs.toMutableList()
        tabs.add(to, tabs.removeAt(from))
        return org.copy(tabs = tabs)
    }

    /**
     * Which items a tab shows. [isWork] and [tagsOf] describe each item; [workMode] is the
     * drawer's work-profile setting ("tab", "merged" or "hidden").
     */
    fun <T> filter(
        tab: DrawerTabDef,
        items: List<T>,
        workMode: String,
        isWork: (T) -> Boolean,
        tagsOf: (T) -> List<String>,
        knownTags: Set<String>,
    ): List<T> {
        val visible = if (workMode == "hidden") items.filter { !isWork(it) } else items
        return when {
            tab.source == "personal" -> if (workMode == "merged") visible else visible.filter { !isWork(it) }
            tab.source == "work" -> if (workMode == "tab") visible.filter(isWork) else emptyList()
            tab.source == "untagged" -> visible.filter { item -> tagsOf(item).none { it in knownTags } }
            tab.source.startsWith("tag:") -> {
                val id = tab.source.removePrefix("tag:")
                visible.filter { id in tagsOf(it) }
            }
            else -> emptyList()
        }
    }
}

/** Tags and drawer tabs with persistence. */
class OrganisationRepository(private val store: JsonStore<Organisation>) {
    val value: StateFlow<Organisation> = store.value

    fun update(change: (Organisation) -> Organisation) = store.update(change)

    /** Creates the tag if needed and returns it. */
    fun tagNamed(name: String): Tag? {
        var result: Tag? = null
        store.update { org ->
            val (updated, tag) = OrganisationOps.addTag(org, name) ?: return@update org
            result = tag
            updated
        }
        return result
    }
}
