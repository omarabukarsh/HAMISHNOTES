package com.mynotes.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** مكتبة مفتوحة المصدر أو مكوّن مستخدم في التطبيق، وترخيصه. */
private class OssItem(val name: String, val owner: String, val license: String, val asset: String?)

private val ossItems = listOf(
    OssItem("Jetpack Compose, AndroidX (Activity, Core, Fragment, Biometric, Profile Installer)", "The Android Open Source Project / Google", "Apache License 2.0", "apache-2.0.txt"),
    OssItem("Kotlin, kotlinx.coroutines", "JetBrains s.r.o.", "Apache License 2.0", "apache-2.0.txt"),
    OssItem("PdfBox-Android (based on Apache PDFBox)", "Tom Roush; The Apache Software Foundation", "Apache License 2.0", "apache-2.0.txt"),
    OssItem("Almarai font", "Boutros Fonts", "SIL Open Font License 1.1", "ofl-1.1.txt"),
    OssItem("Caveat font", "The Caveat Project Authors", "SIL Open Font License 1.1", "ofl-caveat.txt"),
    OssItem("Phosphor Icons", "Phosphor Icons", "MIT License", "mit-phosphor.txt"),
    OssItem(
        "Google ML Kit (text recognition, translation, digital ink recognition)", "Google LLC",
        "Google APIs Terms of Service and ML Kit Terms (not open source; free to use)", null
    )
)

/** «تراخيص المكتبات المفتوحة»: قائمة بالمكوّنات وتراخيصها مع النص الكامل للترخيص. */
@Composable
fun LicensesDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var shown by remember { mutableStateOf<Pair<String, String>?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("تراخيص المكتبات المفتوحة", "Open-source licenses")) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(
                    L("يستخدم «هامش» المكوّنات التالية وفق تراخيصها. انقر على أي منها لقراءة نص الترخيص.",
                        "Hamish uses the following components under their licenses. Tap one to read its license text."),
                    fontSize = 13.sp, color = Palette.TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                ossItems.forEach { it ->
                    Column(
                        Modifier.fillMaxWidth().clickable(enabled = it.asset != null) {
                            val txt = try {
                                ctx.assets.open("licenses/" + it.asset).bufferedReader().readText()
                            } catch (t: Throwable) {
                                it.license
                            }
                            shown = it.name to txt
                        }.padding(vertical = 8.dp)
                    ) {
                        Text(it.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
                        Text(it.owner, fontSize = 12.sp, color = Palette.TextSecondary)
                        Text(it.license, fontSize = 12.sp, color = Palette.Navy)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L("إغلاق", "Close")) } }
    )
    shown?.let { (name, txt) ->
        AlertDialog(
            onDismissRequest = { shown = null },
            title = { Text(name, fontSize = 16.sp) },
            text = {
                Text(
                    txt, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())
                )
            },
            confirmButton = { TextButton(onClick = { shown = null }) { Text(L("رجوع", "Back")) } }
        )
    }
}

/** نص سياسة الخصوصية (نفس محتوى PRIVACY_POLICY.md في المستودع). */
fun privacyText(): String = L(
    """سياسة الخصوصية — هامش (Hamish)

آخر تحديث: أكتوبر 2026

• ملفاتك تبقى على جهازك: ملفات PDF والكتابة والتمييز والملخصات والتسجيلات الصوتية تُحفظ داخل التطبيق على جهازك فقط. لا يرسل «هامش» ملفاتك أو ملاحظاتك إلى أي خادم، ولا يملك المطوّر أي خادم يجمع بياناتك.

• لا حسابات ولا إعلانات: لا يطلب التطبيق تسجيل الدخول، ولا يحتوي إعلانات، ولا يضيف المطوّر أي أدوات تحليلات أو تتبّع.

• الميكروفون: يُطلب إذن الميكروفون فقط عند استخدامك «تسجيل المحاضرة»، والتسجيل يُحفظ على جهازك فقط.

• البصمة/قفل الجهاز: عند قفل مستند يستخدم التطبيق نظام أندرويد للتحقق من هويتك؛ لا يحصل التطبيق على بصمتك. الرقم السري يُحفظ مشفّرًا (تجزئة) على جهازك.

• خدمات Google ML Kit: قراءة النص من الصفحات، والترجمة، وتحويل الكتابة اليدوية إلى نص تعمل على جهازك، ولا يُرسل نص ملفاتك أو صورها أو كتابتك. أول استخدام للترجمة أو تحويل الكتابة ينزّل حزمة لغة من Google مرة واحدة. ترسل مكتبة ML Kit إلى Google بيانات تشخيصية تقنية (مثل طراز الجهاز ونظامه، ونسخة التطبيق، وسرعة الأداء، واللغات المختارة، ومعرّف تثبيت لا يحدد هويتك) لتحسين الخدمة وتشخيص الأعطال، مشفّرة أثناء النقل، وفق سياسة خصوصية Google: https://policies.google.com/privacy

• المشاركة والتصدير: عندما تشارك أو تصدّر ملفًا، يذهب إلى التطبيق أو المكان الذي تختاره أنت.

• حذف البيانات: حذف مستند من المكتبة يحذف بياناته، وإلغاء تثبيت التطبيق يحذف كل بياناته من جهازك.

• الأطفال: التطبيق أداة دراسة عامة ولا يجمع بيانات من أي مستخدم.

للتواصل: [ضع بريدك الإلكتروني هنا]""",
    """Privacy Policy — Hamish

Last updated: October 2026

• Your files stay on your device: PDFs, handwriting, highlights, summaries and audio recordings are stored inside the app on your device only. Hamish does not send your files or notes to any server, and the developer runs no server that collects your data.

• No accounts or ads: the app needs no sign-in, contains no ads, and the developer adds no analytics or tracking tools.

• Microphone: microphone permission is requested only when you use “Lecture recording”; recordings are stored on your device only.

• Biometrics/device lock: when you lock a document, the app uses Android to verify you; the app never receives your fingerprint. The PIN is stored hashed on your device.

• Google ML Kit: reading page text, translation and handwriting-to-text run on your device; the text, images and handwriting of your files are not sent. The first use of translation or handwriting recognition downloads a language pack from Google once. The ML Kit library sends Google technical diagnostic data (such as device model and OS, app version, performance, selected languages and a per-installation identifier that does not identify you) to improve the service and diagnose problems, encrypted in transit, under Google’s privacy policy: https://policies.google.com/privacy

• Sharing and export: when you share or export a file, it goes to the app or place you choose.

• Deleting data: deleting a document from the library deletes its data, and uninstalling the app deletes all its data from your device.

• Children: the app is a general study tool and collects no data from any user.

Contact: [put your email here]"""
)

@Composable
fun PrivacyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("سياسة الخصوصية", "Privacy policy")) },
        text = {
            Text(privacyText(), fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()))
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L("إغلاق", "Close")) } }
    )
}


/** نص شروط الاستخدام (نفس محتوى TERMS.md في المستودع). */
fun termsText(): String = L(
    """شروط الاستخدام — هامش (Hamish)

آخر تحديث: أكتوبر 2026

1. التطبيق أداة لقراءة ملفات PDF والكتابة عليها وتنظيم المذاكرة، ويُقدَّم «كما هو» دون أي ضمان صريح أو ضمني.

2. أنت مسؤول عن الملفات التي تفتحها في التطبيق وعن حقك في استخدامها (مثل المحاضرات والكتب). لا يملك المطوّر أي حقوق على ملفاتك ولا يطّلع عليها.

3. الترجمة، وقراءة النص من الصفحات، وتحويل الكتابة اليدوية إلى نص، والملخصات تُنتج تلقائيًا وقد تحتوي أخطاء. لا تعتمد عليها وحدها في معلومات مهمة؛ راجع دائمًا المصدر الأصلي.

4. بياناتك محفوظة على جهازك فقط. أنت مسؤول عن عمل نسخ احتياطية منها؛ قد تضيع البيانات عند حذف التطبيق أو تعطل الجهاز.

5. يستخدم التطبيق مكوّنات مفتوحة المصدر وخدمات Google ML Kit وفق تراخيصها وشروطها (انظر «تراخيص المكتبات المفتوحة»).

6. لا يتحمل المطوّر مسؤولية أي خسارة أو ضرر ينتج عن استخدام التطبيق، في حدود ما يسمح به القانون.

7. قد تُحدَّث هذه الشروط؛ استمرارك في استخدام التطبيق يعني قبولك للنسخة الأحدث.

للتواصل: [ضع بريدك الإلكتروني هنا]""",
    """Terms of Use — Hamish

Last updated: October 2026

1. The app is a tool for reading and annotating PDF files and organizing study, provided “as is” without warranty of any kind, express or implied.

2. You are responsible for the files you open in the app and for your right to use them (such as lectures and books). The developer has no rights to your files and never sees them.

3. Translation, page text reading, handwriting-to-text and summaries are produced automatically and may contain errors. Don’t rely on them alone for important information; always check the original source.

4. Your data is stored on your device only. You are responsible for backing it up; data may be lost if the app is uninstalled or the device fails.

5. The app uses open-source components and Google ML Kit under their licenses and terms (see “Open-source licenses”).

6. To the extent permitted by law, the developer is not liable for any loss or damage resulting from use of the app.

7. These terms may be updated; continuing to use the app means you accept the latest version.

Contact: [put your email here]"""
)

@Composable
fun TermsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("شروط الاستخدام", "Terms of use")) },
        text = {
            Text(termsText(), fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()))
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L("إغلاق", "Close")) } }
    )
}
