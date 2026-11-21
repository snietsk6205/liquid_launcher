@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.activity.result.contract

import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.PickVisualMediaRequest

abstract class ActivityResultContract<I, O>

class ActivityResultContracts private constructor() {
    class StartActivityForResult : ActivityResultContract<Intent, ActivityResult>()
    class RequestPermission : ActivityResultContract<String, Boolean>()
    class RequestMultiplePermissions : ActivityResultContract<Array<String>, Map<String, Boolean>>()
    class TakePicture : ActivityResultContract<Uri, Boolean>()
    open class GetContent : ActivityResultContract<String, Uri?>()
    open class OpenDocument : ActivityResultContract<Array<String>, Uri?>()
    open class OpenDocumentTree : ActivityResultContract<Uri?, Uri?>()
    open class CreateDocument(mimeType: String) : ActivityResultContract<String, Uri?>()
    open class PickVisualMedia : ActivityResultContract<PickVisualMediaRequest, Uri?>() {
        sealed interface VisualMediaType
        object ImageOnly : VisualMediaType
        object VideoOnly : VisualMediaType
        object ImageAndVideo : VisualMediaType
    }
    open class PickMultipleVisualMedia(maxItems: Int = 0) : ActivityResultContract<PickVisualMediaRequest, List<Uri>>()
}
