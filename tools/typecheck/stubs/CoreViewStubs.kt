@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.core.view

object WindowCompat {
    @JvmStatic fun setDecorFitsSystemWindows(window: android.view.Window, decorFitsSystemWindows: Boolean) {}
    @JvmStatic fun getInsetsController(window: android.view.Window, view: android.view.View): WindowInsetsControllerCompat = error("stub")
}

class WindowInsetsControllerCompat {
    var isAppearanceLightStatusBars: Boolean = false
    var isAppearanceLightNavigationBars: Boolean = false
    var systemBarsBehavior: Int = 0
    fun hide(types: Int) {}
    fun show(types: Int) {}
}
