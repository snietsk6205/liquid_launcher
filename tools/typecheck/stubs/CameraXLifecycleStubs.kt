@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.camera.lifecycle

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.UseCase

/** 0.15.3: CameraX 1.6.2's ProcessCameraProvider (Kotlin there) and its awaitInstance extension. */
class ProcessCameraProvider private constructor() {
    fun bindToLifecycle(lifecycleOwner: androidx.lifecycle.LifecycleOwner, cameraSelector: CameraSelector, vararg useCases: UseCase?): Camera = error("stub")
    fun unbindAll() {}
    fun hasCamera(cameraSelector: CameraSelector): Boolean = false
    companion object
}

suspend fun ProcessCameraProvider.Companion.awaitInstance(context: android.content.Context): ProcessCameraProvider = error("stub")
