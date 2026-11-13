package dev.liquid.launcher.core.data

import kotlinx.serialization.Serializable

/** Identifies one launchable activity in one profile. Stable across restarts. */
@Serializable
data class AppRef(
    val pkg: String,
    val cls: String,
    /** Serial number of the Android user (profile) the app lives in. */
    val profile: Long,
) {
    val key: String get() = "$pkg/$cls#$profile"

    companion object {
        fun parse(key: String): AppRef? {
            val hash = key.lastIndexOf('#')
            val slash = key.indexOf('/')
            if (hash < 0 || slash < 0 || slash > hash) return null
            val profile = key.substring(hash + 1).toLongOrNull() ?: return null
            return AppRef(key.substring(0, slash), key.substring(slash + 1, hash), profile)
        }
    }
}

/** What the user changed about an app. Absent fields mean "as installed". */
@Serializable
data class AppMeta(
    val label: String? = null,
    val hidden: Boolean = false,
)

@Serializable
enum class ItemKind { APP, WIDGET }

/** One thing placed on a home page. Positions and sizes are in grid cells. */
@Serializable
data class HomeItem(
    val id: String,
    val kind: ItemKind,
    val x: Int,
    val y: Int,
    val w: Int = 1,
    val h: Int = 1,
    val app: AppRef? = null,
    /** Built-in widget type, for example "clock". */
    val widget: String? = null,
) {
    fun covers(cx: Int, cy: Int): Boolean = cx in x until x + w && cy in y until y + h

    fun overlaps(ox: Int, oy: Int, ow: Int, oh: Int): Boolean =
        x < ox + ow && ox < x + w && y < oy + oh && oy < y + h
}

@Serializable
data class HomePage(
    val id: String,
    val items: List<HomeItem> = emptyList(),
)

@Serializable
data class HomeLayout(
    val version: Int = 1,
    val pages: List<HomePage> = emptyList(),
    /** Id of the page Home returns to; the first page when null or gone. */
    val defaultPage: String? = null,
) {
    val defaultIndex: Int get() = pages.indexOfFirst { it.id == defaultPage }.coerceAtLeast(0)

    fun find(itemId: String): Pair<Int, HomeItem>? {
        pages.forEachIndexed { index, page ->
            page.items.firstOrNull { it.id == itemId }?.let { return index to it }
        }
        return null
    }

    fun containsApp(ref: AppRef): Boolean = pages.any { page -> page.items.any { it.app == ref } }
}

/** Launch statistics for one app, used for "most used" sorting and search ranking. */
@Serializable
data class Usage(
    val count: Int = 0,
    val last: Long = 0,
    /** Launches per hour of day, 24 entries. */
    val hours: List<Int> = emptyList(),
)
