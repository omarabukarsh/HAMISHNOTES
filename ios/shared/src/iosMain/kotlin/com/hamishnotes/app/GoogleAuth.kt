package com.hamishnotes.app

import android.content.Context

/** في iPad لا يوجد Activity؛ يبقى النوع حتى يطابق كود الإعدادات نسخة أندرويد. */
class Activity

fun Context.findActivity(): Activity? = Activity()

/**
 * تسجيل الدخول بحساب Google: غير متاح بعد في نسخة iPad (يحتاج معرّف عميل iOS من Google Cloud).
 * التطبيق يعمل كاملًا دونه، والملاحظات تبقى على الجهاز.
 */
object GoogleAuth {
    class NotConfigured : Exception()

    val configured: Boolean get() = false

    /** يعيد (البريد، الاسم). */
    suspend fun signIn(@Suppress("UNUSED_PARAMETER") activity: Activity): Result<Pair<String, String?>> =
        Result.failure(NotConfigured())

    suspend fun signOut(@Suppress("UNUSED_PARAMETER") activity: Activity?) {}

    fun messageFor(@Suppress("UNUSED_PARAMETER") e: Throwable): String =
        L("تسجيل الدخول بحساب Google غير متاح بعد في نسخة iPad", "Google sign-in isn’t available on iPad yet")
}
