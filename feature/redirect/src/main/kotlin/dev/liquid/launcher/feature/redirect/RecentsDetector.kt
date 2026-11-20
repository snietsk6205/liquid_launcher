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
    /** Full resource names of which any one is visible when the system launcher's app drawer is showing. */
    val drawerIds: List<String> = emptyList(),
)

/** Tells the system launcher's home screen from recents by looking for known view ids. */
internal object RecentsDetector {

    private const val SUBSTRING_WALK_LIMIT = 600

    /** [screen] is the display's full size, for [shrunkInScreen] (0.10.4); without it only the window is compared. */
    fun probe(root: AccessibilityNodeInfo?, config: DetectConfig, screen: android.graphics.Rect? = null): Probe {
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
        var home = config.homeIds.isNotEmpty()
        var animating = false
        for ((index, id) in config.homeIds.withIndex()) {
            val node = first(root, id)
            if (node == null) {
                home = false
                break
            }
            // The first home id is the home screen itself: is it drawn smaller than its window?
            if (index == 0) animating = isShrunk(root, node, screen)
        }
        val drawer = !home && config.drawerIds.any { has(root, it) }
        return Probe(
            pkg, isSystemLauncher = true, recents = false,
            homeSignature = home, drawerSignature = drawer, homeAnimating = home && animating,
        )
    }

    private fun first(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? = try {
        root.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()
    } catch (e: Exception) {
        null
    }

    /**
     * True when [node] is narrower than the window by more than [SHRINK]: the system launcher
     * draws its home screen at 0.9 scale and lets it grow while its "going home" animation
     * runs. At rest, during a recents gesture and in recents it fills the window.
     */
    private fun isShrunk(root: AccessibilityNodeInfo, node: AccessibilityNodeInfo, screen: android.graphics.Rect?): Boolean {
        val window = android.graphics.Rect()
        val view = android.graphics.Rect()
        root.getBoundsInScreen(window)
        node.getBoundsInScreen(view)
        if (view.width() <= 0 || view.height() <= 0) return false
        if (window.width() > 0 && view.width() < window.width() * (1f - SHRINK) && view.height() < window.height() * (1f - SHRINK)) return true
        return screen != null && shrunkInScreen(view.left, view.top, view.right, view.bottom, screen.width(), screen.height())
    }

    /**
     * 0.10.4: the home screen at ([left], [top], [right], [bottom]) is drawn smaller than the screen in both directions
     * and centred on it. MagicOS shrinks the system launcher's whole window with its home screen while going home, so
     * against the window it never looked smaller: in Med's log the home screen was at [54,119][1026,2269] on a
     * 1080 × 2388 screen in about 40 redirects, and the window check caught 6 of them. At rest, during a recents
     * gesture and in recents it fills the screen. Centred, so a window that only leaves room for the system bars
     * (smaller in one direction, and not centred) never counts.
     */
    internal fun shrunkInScreen(left: Int, top: Int, right: Int, bottom: Int, screenWidth: Int, screenHeight: Int): Boolean {
        if (screenWidth <= 0 || screenHeight <= 0) return false
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) return false
        if (width >= screenWidth * (1f - SHRINK) || height >= screenHeight * (1f - SHRINK)) return false
        // Scaled about the middle: the gaps on opposite sides match (within 2 % of the screen).
        val sideGap = kotlin.math.abs(left - (screenWidth - right))
        val endGap = kotlin.math.abs(top - (screenHeight - bottom))
        return sideGap <= screenWidth * CENTRED && endGap <= screenHeight * CENTRED
    }

    /** How far off the middle a shrunk home screen may sit, as a share of the screen (0.10.4). */
    private const val CENTRED = 0.02f

    /** Fraction of the window the home screen must be smaller by to count as animating. */
    private const val SHRINK = 0.012f

    private fun has(root: AccessibilityNodeInfo, id: String): Boolean = try {
        !root.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()
    } catch (e: Exception) {
        false
    }

    /** Screen bounds of the first node with each id, for the most detailed log level. */
    fun bounds(root: AccessibilityNodeInfo?, ids: List<String>): String {
        if (root == null) return "no window"
        val rect = android.graphics.Rect()
        return ids.joinToString { id ->
            val node = try {
                root.findAccessibilityNodeInfosByViewId(id).firstOrNull()
            } catch (e: Exception) {
                null
            }
            val short = id.substringAfter(":id/")
            if (node == null) "$short -" else {
                node.getBoundsInScreen(rect)
                "$short ${rect.toShortString()}"
            }
        }
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
