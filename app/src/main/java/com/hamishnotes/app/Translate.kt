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
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

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

    /** يترجم الأجزاء واحدًا تلو الآخر ثم يجمعها. */
    fun translateAll(tr: Translator, parts: List<String>, done: (String?) -> Unit) {
        val out = ArrayList<String>()
        fun next(i: Int) {
            if (i >= parts.size) {
                done(out.joinToString(" "))
                return
            }
            tr.translate(parts[i])
                .addOnSuccessListener { out.add(it); next(i + 1) }
                .addOnFailureListener { done(null) }
        }
        next(0)
    }

    fun isArabic(text: String) = text.count { it in '؀'..'ۿ' } * 2 >= text.count { it.isLetter() }

    fun client(text: String): Translator {
        val ar = isArabic(text)
        return Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(if (ar) TranslateLanguage.ARABIC else TranslateLanguage.ENGLISH)
                .setTargetLanguage(if (ar) TranslateLanguage.ENGLISH else TranslateLanguage.ARABIC)
                .build()
        )
    }
}

/** نافذة الترجمة: تُنزّل حزمة اللغة أول مرة (نحو 30 ميغابايت)، ثم تترجم دون إنترنت. */
@Composable
fun TranslateDialog(text: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(true) }
    DisposableEffect(text) {
        val tr = Translate.client(text)
        var alive = true
        tr.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                if (!alive) return@addOnSuccessListener
                downloading = false
                if (!Premium.confirm()) {
                    error = L("الترجمة من مزايا Premium", "Translation is a Premium feature")
                    return@addOnSuccessListener
                }
                Translate.translateAll(tr, Translate.prepare(text)) { r ->
                    if (alive) {
                        if (r != null) result = r else error = L("تعذرت الترجمة", "Translation failed")
                    }
                }
            }
            .addOnFailureListener {
                if (alive) error = L(
                    "تعذر تنزيل حزمة الترجمة. تحتاج اتصالًا بالإنترنت أول مرة، وقد لا تعمل على أجهزة بدون خدمات Google.",
                    "Could not download the translation pack. It needs internet the first time and may not work on devices without Google services."
                )
            }
        onDispose {
            alive = false
            tr.close()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("ترجمة", "Translate")) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(text, fontSize = 13.sp, color = Palette.TextSecondary)
                Spacer(Modifier.height(12.dp))
                when {
                    error != null -> Text(error!!, fontSize = 14.sp, color = Palette.TextPrimary)
                    result != null -> Column {
                        Text(result!!, fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, color = Palette.Navy)
                        Spacer(Modifier.height(8.dp))
                        // نسبة الترجمة إلى Google (شرط استخدام ML Kit للترجمة)
                        Text(L("مترجم بواسطة Google", "Translated by Google"), fontSize = 11.sp, color = Palette.TextSecondary)
                    }
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (downloading) L("جارٍ تجهيز حزمة الترجمة…", "Preparing translation pack…")
                            else L("جارٍ الترجمة…", "Translating…"),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = result != null, onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("MyNotes", result ?: ""))
                AppController.say(MsgKind.DONE, L("تم النسخ", "Copied"))
            }) { Text(L("نسخ", "Copy")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("إغلاق", "Close")) } }
    )
}
