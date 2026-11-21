@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.activity.result

import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

fun interface ActivityResultCallback<O> { fun onActivityResult(result: O) }

abstract class ActivityResultLauncher<I> {
    open fun launch(input: I) {}
    open fun unregister() {}
    abstract val contract: ActivityResultContract<I, *>
}

fun ActivityResultLauncher<Unit>.launch() {}
@JvmName("launchVoid") fun ActivityResultLauncher<Void?>.launch() {}

class ActivityResult(val resultCode: Int, val data: android.content.Intent?)

class PickVisualMediaRequest internal constructor()
fun PickVisualMediaRequest(mediaType: ActivityResultContracts.PickVisualMedia.VisualMediaType = ActivityResultContracts.PickVisualMedia.ImageAndVideo): PickVisualMediaRequest = error("stub")
