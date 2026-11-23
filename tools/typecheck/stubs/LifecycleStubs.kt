@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.lifecycle

/** 0.15.3: what the board camera (CameraX) needs of androidx.lifecycle; the real ComponentActivity is a LifecycleOwner. */
interface LifecycleOwner

fun interface Observer<T> {
    fun onChanged(value: T)
}

open class LiveData<T> {
    open val value: T? get() = null
    fun observe(owner: LifecycleOwner, observer: Observer<in T>) {}
    fun removeObserver(observer: Observer<in T>) {}
}
