package dev.liquid.launcher.feature.redirect

import android.view.accessibility.AccessibilityNodeInfo
import dev.liquid.launcher.feature.redirect.engine.Probe

internal data class DetectConfig(
    val systemLauncher: String,
    /** Full resource names, for example "com.hihonor.android.launcher:id/overview_panel_launcher3". */
    val exactIds: List<String>,
    /** Fragments matched against the part after ":id/". Needs a tree walk, so it is a fallback. */
    val substrings: List<String>,
    /** Full resource names that are all visible when the home screen itself is showing. */
    val homeIds: List<String>,
)

/** Tells the system launcher's home screen from recents by looking for known view ids. */
internal object RecentsDetector {

    private const val SUBSTRING_WALK_LIMIT = 600

    fun probe(root: AccessibilityNodeInfo?, config: DetectConfig): Probe {
        if (root == null) return Probe(null, isSystemLauncher = false, recents = false, detail = "no window")
        val pkg = root.packageName?.toString()
        if (pkg != config.systemLauncher) return Probe(pkg, isSystemLauncher = false, recents = false)

        for (id in config.exactIds) {
            val nodes = try {
                root.findAccessibilityNodeInfosByViewId(id)
            } catch (e: Exception) {
                null
            }
            if (!nodes.isNullOrEmpty()) {
                return Probe(pkg, isSystemLauncher = true, recents = true, detail = id.substringAfter(":id/"))
            }
        }

        if (config.substrings.isNotEmpty()) {
            val hit = walk(root, SUBSTRING_WALK_LIMIT) { name ->
                val short = name.substringAfter(":id/")
                config.substrings.any { it in short }
            }
            if (hit != null) {
                return Probe(pkg, isSystemLauncher = true, recents = true, detail = "~" + hit.substringAfter(":id/"))
            }
        }
        val home = config.homeIds.isNotEmpty() && config.homeIds.all { id ->
            try {
                !root.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()
            } catch (e: Exception) {
                false
            }
        }
        return Probe(pkg, isSystemLauncher = true, recents = false, homeSignature = home)
    }

    /** All view ids in the window, for calibration and the most detailed log level. */
    fun collectIds(root: AccessibilityNodeInfo?, limit: Int = 4000): Set<String> {
        val ids = LinkedHashSet<String>()
        if (root == null) return ids
        walk(root, limit) { name ->
            ids += name
            false
        }
        return ids
    }

    /** Breadth-first walk over at most [limit] nodes; returns the first id for which [match] is true. */
    private fun walk(root: AccessibilityNodeInfo, limit: Int, match: (String) -> Boolean): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < limit) {
            val node = queue.removeFirst()
            visited++
            val name = node.viewIdResourceName
            if (name != null && match(name)) return name
            val count = try {
                node.childCount
            } catch (e: Exception) {
                0
            }
            for (i in 0 until count) {
                val child = try {
                    node.getChild(i)
                } catch (e: Exception) {
                    null
                }
                if (child != null) queue.addLast(child)
            }
        }
        return null
    }
}
