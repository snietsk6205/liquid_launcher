package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppFilterTest {
    @Test
    fun componentsAreReadInBothForms() {
        assertEquals("com.app/com.app.Main", AppFilter.component("ComponentInfo{com.app/com.app.Main}"))
        assertEquals("com.app/com.app.ui.Start", AppFilter.component("ComponentInfo{com.app/.ui.Start}"))
        assertNull(AppFilter.component("ComponentInfo{broken}"))
        assertNull(AppFilter.component(":LAUNCHER_ACTION_APP_DRAWER"))
    }

    @Test
    fun builderCollectsIconsAndTheFrame() {
        val b = AppFilterBuilder()
        fun el(name: String, vararg attrs: Pair<String, String>) = b.element(name) { key -> attrs.toMap()[key] }
        el("iconback", "img1" to "back1", "img2" to "back2")
        el("iconmask", "img1" to "mask")
        el("iconupon", "img1" to "upon")
        el("scale", "factor" to "0.8")
        el("item", "component" to "ComponentInfo{com.a/com.a.Main}", "drawable" to "a")
        el("item", "component" to "ComponentInfo{com.a/com.a.Main}", "drawable" to "a_alt")
        el("item", "component" to "ComponentInfo{com.b/com.b.Main}", "drawable" to "")
        val f = b.build()
        assertEquals(mapOf("com.a/com.a.Main" to "a"), f.icons)
        assertEquals(listOf("back1", "back2"), f.backs)
        assertEquals("mask", f.mask)
        assertEquals("upon", f.upon)
        assertEquals(0.8f, f.scale, 0.001f)
    }
}
