package com.hamishnotes.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.URLQueryAllowedCharacterSet
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.UIKit.UIApplication

/** ترجمة على الجهاز: إنجليزي ← عربي، وعربي ← إنجليزي (حسب لغة النص المحدد). */
object Translate {
    /**
     * تهيئة النص قبل الترجمة: فك الاختصارات الشائعة في المحاضرات (i.e، e.g، vit…)
     * ثم تقسيمه إلى جمل، لأن الترجمة على الجهاز تخطئ كثيرًا مع الاختصارات والجمل الطويلة.
     */
    fun prepare(text: String): List<String> {
        if (isArabic(text)) return listOf(text)
        var t = text.replace(Regex("\\s+"), " ").trim()
        val rules = listOf(
            "\\(?\\bi\\.\\s?e\\.?,?\\)?(?=\\s)" to ". That is,",
            "\\be\\.\\s?g\\.?,?(?=\\s)" to "for example,",
            "\\bvs\\.?(?=\\s)" to "versus",
            "\\betc\\.?(?=\\s|$)" to "and so on",
            "\\bapprox\\.?(?=\\s)" to "approximately",
            "\\bvits?\\.?(?=\\s)" to "vitamin",
            "\\bw/o\\b" to "without",
            "\\bw/(?=\\s)" to "with",
            "\\bwith out\\b" to "without",
            "\\ba prolong\\b" to "a prolonged",
            "\\bpts\\b" to "patients",
            "\\bpt\\b" to "patient",
            "\\bdx\\b" to "diagnosis",
            "\\btx\\b" to "treatment",
            "\\bhx\\b" to "history",
            "\\bsx\\b" to "symptoms",
            "\\bb/c\\b" to "because",
            "\\bdec\\.?(?=\\s)" to "decreased",
            "\\binc\\.?(?=\\s)" to "increased"
        )
        for ((re, rep) in rules) t = t.replace(Regex(re, RegexOption.IGNORE_CASE), rep)
        t = t.replace(Regex("\\s*\\.\\s*\\.\\s*"), ". ").replace(Regex("^\\.\\s*"), "").replace(Regex("\\s+\\."), ".")
        // «vitamin k» → «vitamin K»
        t = t.replace(Regex("\\bvitamin ([a-z])\\b")) { "vitamin " + it.groupValues[1].uppercase() }
        return t.split(Regex("(?<=[.!?;])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun isArabic(text: String) = text.count { it in '؀'..'ۿ' } * 2 >= text.count { it.isLetter() }

    /** رابط Google Translate للنص (لا توجد ترجمة على الجهاز في iPad بعد). */
    @Suppress("CAST_NEVER_SUCCEEDS")
    fun webUrl(text: String): NSURL? {
        val ar = isArabic(text)
        val q = (prepare(text).joinToString(" ") as NSString)
            .stringByAddingPercentEncodingWithAllowedCharacters(NSCharacterSet.URLQueryAllowedCharacterSet)
            ?.replace("&", "%26") ?: return null
        val tl = if (ar) "en" else "ar"
        return NSURL.URLWithString("https://translate.google.com/?sl=auto&tl=$tl&op=translate&text=$q")
    }
}

/** نافذة الترجمة: في iPad تفتح Google Translate (تطبيقه أو موقعه) بالنص المحدد. */
@Composable
fun TranslateDialog(text: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("ترجمة", "Translate")) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(text, fontSize = 15.sp, lineHeight = 22.sp, color = Palette.TextPrimary)
                Spacer(Modifier.height(12.dp))
                Text(
                    L("الترجمة على الجهاز غير متاحة بعد في نسخة iPad. اضغط «ترجمة» لفتحها في Google Translate.",
                        "On-device translation isn’t available on iPad yet. Tap “Translate” to open it in Google Translate."),
                    fontSize = 13.sp, color = Palette.TextSecondary
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("MyNotes", text))
                    AppController.messages.tryEmit(L("تم النسخ", "Copied"))
                }) { Text(L("نسخ", "Copy")) }
                TextButton(onClick = {
                    if (!Premium.confirm()) {
                        AppController.messages.tryEmit(L("الترجمة من مزايا Premium", "Translation is a Premium feature"))
                        return@TextButton
                    }
                    Translate.webUrl(text)?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any?>(), null) }
                    onDismiss()
                }) { Text(L("ترجمة", "Translate"), fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("إغلاق", "Close")) } }
    )
}
