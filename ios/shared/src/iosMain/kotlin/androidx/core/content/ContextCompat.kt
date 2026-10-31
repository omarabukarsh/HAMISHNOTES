package androidx.core.content

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted

object ContextCompat {
    /** الميكروفون: حالة إذن iOS الفعلية. الإشعارات: يُطلب الإذن دائمًا (iOS يتجاهل الطلب المكرر). */
    fun checkSelfPermission(@Suppress("UNUSED_PARAMETER") ctx: Context, permission: String): Int = when (permission) {
        Manifest.permission.RECORD_AUDIO ->
            if (AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted) PackageManager.PERMISSION_GRANTED
            else PackageManager.PERMISSION_DENIED
        else -> PackageManager.PERMISSION_DENIED
    }
}
