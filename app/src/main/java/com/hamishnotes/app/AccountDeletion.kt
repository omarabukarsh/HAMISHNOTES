package com.hamishnotes.app

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException

/**
 * حذف الحساب (شرط Google Play و Apple): يحذف الخادم المستخدم من Supabase مع صفوف اشتراكه،
 * ويلغي تجديد اشتراكه في Google Play أولًا. المستندات على الجهاز لا تُلمس.
 */
internal object AccountDeletion {
    /** null عند النجاح، وإلا رسالة الخطأ. */
    suspend fun delete(activity: Activity?): String? {
        // الحذف يحتاج جلسة Supabase: إن لم تُربط بعد نطلب الدخول بـ Google مرة واحدة
        if (Cloud.userId == null && activity != null) {
            GoogleAuth.signIn(activity).onSuccess { (email, name) -> AppController.setAccount(email, name) }
        }
        if (Cloud.userId == null) return L("سجّل الدخول بحساب Google مرة أخرى ثم أعد المحاولة", "Sign in with Google again, then try again")
        try {
            Cloud.function("delete-account", JSONObject())
        } catch (e: Cloud.HttpError) {
            return when {
                e.body?.optString("error") == "cancel_failed" -> L(
                    "تعذّر إلغاء تجديد اشتراكك تلقائيًا. ألغه من Google Play ثم أعد المحاولة",
                    "Couldn’t cancel your subscription automatically. Cancel it in Google Play, then try again"
                )
                e.code == 401 -> L("انتهت جلستك. سجّل الدخول مرة أخرى ثم أعد المحاولة", "Your session expired. Sign in again, then try again")
                e.code == 404 -> L("حذف الحساب غير متاح الآن، حاول لاحقًا", "Account deletion isn’t available right now, try later")
                else -> L("تعذّر حذف الحساب، حاول مرة أخرى", "Couldn’t delete the account, please try again")
            }
        } catch (e: IOException) {
            return L("تحقق من اتصالك بالإنترنت", "Check your internet connection")
        } catch (e: Throwable) {
            return L("تعذّر حذف الحساب، حاول مرة أخرى", "Couldn’t delete the account, please try again")
        }
        GoogleAuth.signOut(activity)
        AppController.signOutAccount()
        return null
    }
}

/** صف «حذف حسابي» في الإعدادات ← الحساب (يظهر لمن سجّل الدخول فقط). */
@Composable
fun DeleteAccountRow() {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF8E7E6)),
            contentAlignment = Alignment.Center
        ) { PhIcon(R.drawable.ph_trash, Lib.exam, 18.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(L("حذف حسابي", "Delete my account"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Lib.exam)
            Text(
                L("يحذف حسابك وبيانات اشتراكك من خوادم هامش", "Deletes your account and subscription data from Hamish servers"),
                fontSize = 12.sp, lineHeight = 17.sp, color = Palette.TextSecondary
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(if (I18n.english) "›" else "‹", fontSize = 20.sp, color = Color(0xFFB0B5BF))
    }
    if (open) DeleteAccountDialog(onDismiss = { open = false })
}

@Composable
private fun DeleteAccountDialog(onDismiss: () -> Unit) {
    val activity = LocalContext.current.findActivity()
    val scope = rememberCoroutineScope()
    val email = AppController.accountEmail.orEmpty()
    var understood by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val red = Lib.exam

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            Modifier.widthIn(max = 470.dp).clip(RoundedCornerShape(26.dp)).background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(start = 26.dp, end = 26.dp, top = 26.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(62.dp).clip(CircleShape).background(Color(0xFFF8E7E6)),
                contentAlignment = Alignment.Center
            ) { PhIcon(R.drawable.ph_trash, red, 30.dp) }
            Spacer(Modifier.height(14.dp))
            Text(
                L("حذف حسابك نهائيًا؟", "Delete your account for good?"), fontFamily = Fonts.Display,
                fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Palette.TextPrimary, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                buildAnnotatedString {
                    append(L("سيُحذف حساب ", "The account "))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Palette.TextPrimary)) { append(email) }
                    append(L(" من هامش.\nلا يمكن التراجع عن الحذف.", " will be deleted from Hamish.\nThis can’t be undone."))
                },
                fontSize = 13.5.sp, lineHeight = 21.sp, color = Palette.TextSecondary, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            val listShape = RoundedCornerShape(14.dp)
            Column(Modifier.fillMaxWidth().clip(listShape).border(1.dp, Color(0xFFEFE9DC), listShape)) {
                DeletionLine(false, L("حسابك في هامش (البريد والاسم)", "Your Hamish account (email and name)"))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF3EEE3)))
                DeletionLine(false, L("سجل اشتراكك وبيانات التحقق في خوادم هامش", "Your subscription record and verification data on Hamish servers"))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF3EEE3)))
                DeletionLine(true, L("مستنداتك وملاحظاتك تبقى على هذا الجهاز", "Your documents and notes stay on this device"))
            }
            // للمشترك فقط: نلغي التجديد في Google Play تلقائيًا قبل الحذف
            if (Premium.grant != null) {
                Spacer(Modifier.height(12.dp))
                val warnShape = RoundedCornerShape(14.dp)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(L("لديك اشتراك Premium فعّال. ", "You have an active Premium subscription. ")) }
                        append(
                            L(
                                "سنلغي تجديده التلقائي في Google Play فلا تُخصم منك مبالغ جديدة، وتتوقف المزايا فور الحذف. فواتير الشراء تحتفظ بها Google.",
                                "We’ll cancel its renewal in Google Play so you won’t be charged again, and Premium stops right away. Google keeps the purchase receipts."
                            )
                        )
                    },
                    fontSize = 12.5.sp, lineHeight = 19.sp, color = Color(0xFF5B4A1A),
                    modifier = Modifier.fillMaxWidth().clip(warnShape).background(Color(0xFFFFF8E4))
                        .border(1.dp, Color(0xFFF1DFAE), warnShape).padding(horizontal = 14.dp, vertical = 11.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = !busy) { understood = !understood }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
                        .background(if (understood) red else Color.White)
                        .border(1.5.dp, if (understood) red else Color(0xFFC9CDD6), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) { if (understood) PhIcon(R.drawable.ph_check, Color.White, 13.dp) }
                Spacer(Modifier.width(9.dp))
                Text(L("أفهم أن حذف الحساب نهائي", "I understand deleting my account is permanent"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 12.5.sp, lineHeight = 18.sp, color = red, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val pill = RoundedCornerShape(50)
                Box(
                    Modifier.weight(1f).height(46.dp).clip(pill).background(Color(0xFFF3F4F8)).clickable(enabled = !busy, onClick = dialogClose(onDismiss)),
                    contentAlignment = Alignment.Center
                ) { Text(L("إلغاء", "Cancel"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary) }
                val ready = understood && !busy
                Box(
                    Modifier.weight(1f).height(46.dp).clip(pill).background(if (ready) red else red.copy(alpha = 0.35f))
                        .clickable(enabled = ready) {
                            busy = true
                            error = null
                            scope.launch {
                                val e = AccountDeletion.delete(activity)
                                busy = false
                                if (e == null) {
                                    AppController.messages.tryEmit(L("حُذف حسابك من هامش. ملاحظاتك باقية على هذا الجهاز", "Your Hamish account was deleted. Your notes stay on this device"))
                                    onDismiss()
                                } else error = e
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (busy) L("جارٍ الحذف…", "Deleting…") else L("حذف الحساب", "Delete account"),
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun DeletionLine(kept: Boolean, text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(20.dp).clip(CircleShape).background(if (kept) Color(0xFFE3F3EB) else Color(0xFFF8E7E6)),
            contentAlignment = Alignment.Center
        ) {
            if (kept) PhIcon(R.drawable.ph_check, Color(0xFF2E9D6B), 12.dp)
            else Text("✕", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Lib.exam)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.5.sp, lineHeight = 19.sp, color = Palette.TextPrimary)
    }
}
