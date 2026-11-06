package com.hamishnotes.app

import android.app.Activity
import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.model.IntegrityDialogTypeCode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Play Integrity: حكم من Google يقول هل هذه نسختنا الأصلية من المتجر، على جهاز حقيقي.
 * التطبيق لا يقرأ الحكم (مشفّر)؛ يرسله إلى الخادم الذي يفك تشفيره ويقرر.
 */
internal object Integrity {
    private var provider: StandardIntegrityManager.StandardIntegrityTokenProvider? = null

    /** آخر رمز طُلب، لعرض نافذة Google لإصلاح المشكلة إن رفضه الخادم. */
    private var last: StandardIntegrityManager.StandardIntegrityToken? = null

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { k ->
        addOnSuccessListener { k.resume(it) }
        addOnFailureListener { k.resumeWithException(it) }
    }

    private suspend fun provider(context: Context): StandardIntegrityManager.StandardIntegrityTokenProvider {
        provider?.let { return it }
        val p = IntegrityManagerFactory.createStandard(context.applicationContext)
            .prepareIntegrityToken(
                StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
                    .setCloudProjectNumber(BuildConfig.CLOUD_PROJECT_NUMBER)
                    .build()
            ).await()
        provider = p
        return p
    }

    /** رمز سلامة مربوط ببصمة الطلب (requestHash)، فلا يصلح لطلب آخر. */
    suspend fun token(context: Context, requestHash: String): String {
        val t = try {
            provider(context).request(
                StandardIntegrityManager.StandardIntegrityTokenRequest.builder().setRequestHash(requestHash).build()
            ).await()
        } catch (e: Throwable) {
            // قد ينتهي المزوّد بعد مدة: نجهّز واحدًا جديدًا مرة واحدة
            provider = null
            provider(context).request(
                StandardIntegrityManager.StandardIntegrityTokenRequest.builder().setRequestHash(requestHash).build()
            ).await()
        }
        last = t
        return t.token()
    }

    /** نافذة Google الرسمية: «احصل على التطبيق من Google Play» للنسخ المثبّتة من خارج المتجر. */
    suspend fun showGetLicensed(activity: Activity) {
        runCatching { last?.showDialog(activity, IntegrityDialogTypeCode.GET_LICENSED)?.await() }
    }

    /** نافذة Google لإصلاح سلامة الجهاز (تحديث خدمات Play مثلًا). */
    suspend fun showGetIntegrity(activity: Activity) {
        runCatching { last?.showDialog(activity, IntegrityDialogTypeCode.GET_INTEGRITY)?.await() }
    }
}
