package com.mynotes.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * تسجيل الدخول بحساب Google عبر Credential Manager.
 * في نسخة المتجر يُبدَّل رمز Google بجلسة Supabase، فيُربط الاشتراك بالحساب.
 */
object GoogleAuth {
    class NotConfigured : Exception()

    val configured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /** يعيد (البريد، الاسم). */
    suspend fun signIn(activity: Activity): Result<Pair<String, String?>> {
        if (!configured) return Result.failure(NotConfigured())
        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val cred = CredentialManager.create(activity).getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val g = GoogleIdTokenCredential.createFrom(cred.data)
                // فشل الربط بالخادم لا يمنع الدخول: يُعاد عند الاشتراك
                if (Cloud.configured) Cloud.signInWithGoogle(g.idToken)
                Result.success(g.id to g.displayName)
            } else {
                Result.failure(IllegalStateException("unsupported credential"))
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun signOut(activity: Activity?) {
        if (activity == null) return
        try {
            CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Throwable) {
        }
    }

    fun messageFor(e: Throwable): String = when (e) {
        is NotConfigured -> L("تسجيل الدخول غير مُعدّ بعد في هذه النسخة", "Sign-in is not set up in this build yet")
        is GetCredentialCancellationException -> L("أُلغي تسجيل الدخول", "Sign-in cancelled")
        is NoCredentialException -> L("لا يوجد حساب Google على هذا الجهاز", "No Google account on this device")
        else -> L("تعذّر تسجيل الدخول، حاول مرة أخرى", "Sign-in failed, please try again")
    }
}
