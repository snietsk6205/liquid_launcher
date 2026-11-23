@file:Suppress("unused", "UNUSED_PARAMETER")
package com.google.common.util.concurrent

/** 0.15.3: CameraX's controls answer with these. */
interface ListenableFuture<V> : java.util.concurrent.Future<V> {
    fun addListener(listener: Runnable, executor: java.util.concurrent.Executor)
}
