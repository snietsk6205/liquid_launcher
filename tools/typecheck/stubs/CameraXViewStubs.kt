@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.camera.view

import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview

/** 0.15.3: CameraX 1.6.2's PreviewView (Java there). */
class PreviewView(context: android.content.Context) : android.widget.FrameLayout(context) {
    enum class ImplementationMode { PERFORMANCE, COMPATIBLE }
    enum class ScaleType { FILL_START, FILL_CENTER, FILL_END, FIT_START, FIT_CENTER, FIT_END }
    enum class StreamState { IDLE, STREAMING }

    var implementationMode: ImplementationMode = ImplementationMode.PERFORMANCE
    var scaleType: ScaleType = ScaleType.FILL_CENTER
    val surfaceProvider: Preview.SurfaceProvider get() = error("stub")
    val meteringPointFactory: MeteringPointFactory get() = error("stub")
    val bitmap: android.graphics.Bitmap? get() = null
    val previewStreamState: androidx.lifecycle.LiveData<StreamState> get() = error("stub")
}
