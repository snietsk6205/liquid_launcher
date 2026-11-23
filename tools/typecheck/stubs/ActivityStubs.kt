@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.activity

import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract

open class ComponentActivity : android.app.Activity(), androidx.lifecycle.LifecycleOwner {
    val onBackPressedDispatcher: OnBackPressedDispatcher = OnBackPressedDispatcher()
    fun <I, O> registerForActivityResult(contract: ActivityResultContract<I, O>, callback: ActivityResultCallback<O>): ActivityResultLauncher<I> = error("stub")
}

class OnBackPressedDispatcher {
    fun addCallback(owner: Any?, callback: OnBackPressedCallback) {}
    fun addCallback(callback: OnBackPressedCallback) {}
    fun onBackPressed() {}
}

abstract class OnBackPressedCallback(enabled: Boolean) {
    abstract fun handleOnBackPressed()
    var isEnabled: Boolean = enabled
    fun remove() {}
}

fun ComponentActivity.enableEdgeToEdge() {}

class BackEventCompat(val touchX: Float, val touchY: Float, val progress: Float, val swipeEdge: Int) {
    companion object {
        const val EDGE_LEFT = 0
        const val EDGE_RIGHT = 1
    }
}
