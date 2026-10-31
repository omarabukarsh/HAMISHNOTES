package androidx.activity.compose

import android.Manifest
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.hamishnotes.app.IosFiles
import com.hamishnotes.app.runOnMain
import platform.AVFAudio.AVAudioSession
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter

/** لا يوجد زر رجوع في iPad: أزرار الواجهة نفسها (المكتبة، الإغلاق) تقوم بذلك. */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
}

class ManagedActivityResultLauncher<I, O> internal constructor(private val start: (I) -> Unit) {
    fun launch(input: I) = start(input)
}

/**
 * نفس واجهة أندرويد، والتنفيذ بنوافذ iOS:
 *  OpenDocument / OpenMultipleDocuments  ← منتقي «الملفات»
 *  CreateDocument                        ← ملف مؤقت يُكتب فيه ثم «حفظ في الملفات» (IosFiles.deliver)
 *  RequestPermission                     ← طلب إذن الميكروفون أو الإشعارات
 */
@Suppress("UNCHECKED_CAST")
@Composable
fun <I, O> rememberLauncherForActivityResult(
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit
): ManagedActivityResultLauncher<I, O> {
    val cb = rememberUpdatedState(onResult)
    return remember(contract) {
        ManagedActivityResultLauncher<I, O> { input ->
            when (contract) {
                is ActivityResultContracts.OpenDocument -> IosFiles.pick((input as Array<String>).toList(), false) { l ->
                    runOnMain { cb.value(l.firstOrNull() as O) }
                }
                is ActivityResultContracts.OpenMultipleDocuments -> IosFiles.pick((input as Array<String>).toList(), true) { l ->
                    runOnMain { cb.value(l as O) }
                }
                is ActivityResultContracts.CreateDocument -> {
                    val uri: Uri = IosFiles.exportTarget(input as String)
                    cb.value(uri as O)
                }
                is ActivityResultContracts.RequestPermission -> when (input as String) {
                    Manifest.permission.RECORD_AUDIO -> AVAudioSession.sharedInstance().requestRecordPermission { ok ->
                        runOnMain { cb.value(ok as O) }
                    }
                    else -> UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                        UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
                    ) { ok, _ -> runOnMain { cb.value(ok as O) } }
                }
                else -> {}
            }
        }
    }
}
