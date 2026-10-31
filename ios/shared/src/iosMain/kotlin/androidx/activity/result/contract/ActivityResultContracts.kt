package androidx.activity.result.contract

import android.net.Uri

abstract class ActivityResultContract<I, O>

object ActivityResultContracts {
    class OpenDocument : ActivityResultContract<Array<String>, Uri?>()
    class OpenMultipleDocuments : ActivityResultContract<Array<String>, List<Uri>>()
    class CreateDocument(val mimeType: String = "*/*") : ActivityResultContract<String, Uri?>()
    class RequestPermission : ActivityResultContract<String, Boolean>()
}
