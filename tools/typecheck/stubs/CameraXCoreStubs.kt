@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.camera.core

import com.google.common.util.concurrent.ListenableFuture

/** 0.15.3: the CameraX 1.6.2 API the board camera uses (Java classes there; signatures from its sources). */
abstract class UseCase

class Preview private constructor() : UseCase() {
    fun interface SurfaceProvider {
        fun onSurfaceRequested(request: Any)
    }
    fun setSurfaceProvider(surfaceProvider: SurfaceProvider?) {}
    class Builder {
        fun build(): Preview = error("stub")
    }
}

class ImageCaptureException(val imageCaptureError: Int, message: String, cause: Throwable?) : Exception(message, cause)

class ImageCapture private constructor() : UseCase() {
    var targetRotation: Int = 0
    fun takePicture(outputFileOptions: OutputFileOptions, executor: java.util.concurrent.Executor, imageSavedCallback: OnImageSavedCallback) {}

    interface OnImageSavedCallback {
        fun onCaptureStarted() {}
        fun onImageSaved(outputFileResults: OutputFileResults)
        fun onError(exception: ImageCaptureException)
    }

    class OutputFileOptions private constructor() {
        class Builder(contentResolver: android.content.ContentResolver, saveCollection: android.net.Uri, contentValues: android.content.ContentValues) {
            fun build(): OutputFileOptions = error("stub")
        }
    }

    open class OutputFileResults(val savedUri: android.net.Uri?)

    class Builder {
        fun setCaptureMode(captureMode: Int): Builder = this
        fun setTargetRotation(rotation: Int): Builder = this
        fun setJpegQuality(jpegQuality: Int): Builder = this
        fun build(): ImageCapture = error("stub")
    }

    companion object {
        const val CAPTURE_MODE_MAXIMIZE_QUALITY = 0
        const val CAPTURE_MODE_MINIMIZE_LATENCY = 1
    }
}

class CameraSelector private constructor() {
    companion object {
        val DEFAULT_BACK_CAMERA: CameraSelector = CameraSelector()
        val DEFAULT_FRONT_CAMERA: CameraSelector = CameraSelector()
    }
}

interface ZoomState {
    val zoomRatio: Float
    val maxZoomRatio: Float
    val minZoomRatio: Float
}

interface CameraInfo {
    val zoomState: androidx.lifecycle.LiveData<ZoomState>
}

class FocusMeteringResult

open class MeteringPoint

abstract class MeteringPointFactory {
    fun createPoint(x: Float, y: Float): MeteringPoint = error("stub")
}

class FocusMeteringAction private constructor() {
    class Builder(point: MeteringPoint) {
        fun setAutoCancelDuration(duration: Long, timeUnit: java.util.concurrent.TimeUnit): Builder = this
        fun build(): FocusMeteringAction = error("stub")
    }
}

interface CameraControl {
    fun setZoomRatio(ratio: Float): ListenableFuture<Void>
    fun startFocusAndMetering(action: FocusMeteringAction): ListenableFuture<FocusMeteringResult>
}

interface Camera {
    val cameraControl: CameraControl
    val cameraInfo: CameraInfo
}
