package com.hamishnotes.app

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

آخر تحديث: 1 نوفمبر 2026
المطوّر: Hamish Notes · للتواصل: noteshamish@gmail.com

الخلاصة: ملفاتك وملاحظاتك على جهازك فقط ولا نطّلع عليها. نحفظ على خادمنا الحد الأدنى اللازم لحسابك واشتراكك فقط. لا إعلانات، ولا بيع للبيانات، ولا أدوات تتبّع أو تحليلات.

• على جهازك فقط: ملفات PDF، والكتابة، والتمييز، والملخصات، والبطاقات، والتسجيلات الصوتية، وإحصاءات الدراسة، والإعدادات. لا تُرسل إلينا.

• حساب Google (اختياري، ولازم للاشتراك): البريد الإلكتروني والاسم ومعرّف حسابك في Google، لإنشاء حسابك وربط اشتراكك به.

• الاشتراك: رمز الشراء، ونوع الخطة، وحالتها، وتاريخ انتهائها، وفترة التجربة، للتحقق من اشتراكك. لا نحصل على بيانات بطاقتك أبدًا، فالدفع تديره Google.

• معرّف الجهاز: رمز مشتق ومشفّر لا يكشف هويتك، لربط Premium بجهازك.

• فحص سلامة التطبيق (Google Play Integrity): يؤكد أن التطبيق أصلي وأن الجهاز سليم. إن فشل نحفظ نتيجته لمنع النسخ المعدّلة والاحتيال.

• سجلات تقنية: يحفظ مزوّد الخادم سجلات قصيرة المدة (مثل وقت الطلب وعنوان IP) للأمان وتشخيص الأعطال.

• الخدمات: Supabase (الخادم، في فرانكفورت، ألمانيا)، وGoogle (تسجيل الدخول والدفع وPlay Integrity) وفق سياسة خصوصية Google: https://policies.google.com/privacy

• Google ML Kit: قراءة النص والترجمة وتحويل الكتابة إلى نص تعمل على جهازك، ولا يُرسل محتوى ملفاتك. تنزّل حزم اللغات من Google مرة واحدة، وترسل المكتبة بيانات تشخيصية تقنية لا تحدد هويتك.

• الأذونات: الميكروفون فقط لتسجيل المحاضرة (يبقى على جهازك)، والبصمة يتحقق منها نظام Android ولا نحصل عليها، والإنترنت للحساب والاشتراك وحزم اللغات.

• الحذف: احذف حسابك من الإعدادات ← الحساب ← حذف حسابي، أو اطلبه بالبريد. نحذف حسابك وبيانات اشتراكك وسجلات الفحص، ونلغي تجديد اشتراكك في Google Play. حذف التطبيق يحذف ملفاتك من جهازك.

• الأمان: اتصال مشفّر (HTTPS)، ووصول مقيّد لقاعدة البيانات، وتفعيل Premium موقّع رقميًا.

• الأطفال: التطبيق أداة دراسة عامة، غير موجّهة للأطفال دون 13 عامًا.

للتواصل أو طلب بياناتك أو حذفها: noteshamish@gmail.com""",
    """Privacy Policy — Hamish

Last updated: November 1, 2026
Developer: Hamish Notes · Contact: noteshamish@gmail.com

Summary: your files and notes stay on your device and we never see them. Our server keeps only the minimum needed for your account and subscription. No ads, no selling of data, no tracking or analytics tools.

• On your device only: PDFs, handwriting, highlights, summaries, cards, audio recordings, study stats and settings. Never sent to us.

• Google account (optional, required to subscribe): email address, name and Google account identifier, to create your account and link your subscription to it.

• Subscription: purchase token, plan, status, expiry date and free-trial state, to verify your subscription. We never receive your card details; Google handles payment.

• Device identifier: a hashed code that does not reveal who you are, to bind Premium to your device.

• App integrity check (Google Play Integrity): confirms the app is genuine and the device is not compromised. If it fails we store the result to prevent modified copies and fraud.

• Technical logs: our hosting provider keeps short-lived logs (such as request time and IP address) for security and troubleshooting.

• Services: Supabase (server, in Frankfurt, Germany) and Google (sign-in, billing and Play Integrity) under the Google Privacy Policy: https://policies.google.com/privacy

• Google ML Kit: text reading, translation and handwriting-to-text run on your device; your files' content is not sent. Language packs are downloaded from Google once, and the library sends technical diagnostics that do not identify you.

• Permissions: microphone only for lecture recording (stays on your device); fingerprint is verified by Android and never reaches us; internet for your account, subscription and language packs.

• Deletion: delete your account in Settings → Account → Delete my account, or request it by email. We delete your account, subscription data and integrity records, and cancel your Google Play renewal. Uninstalling the app deletes your files from your device.

• Security: encrypted connections (HTTPS), restricted database access, and digitally signed Premium activation.

• Children: the app is a general study tool, not directed at children under 13.

To contact us, or to access or delete your data: noteshamish@gmail.com"""
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

آخر تحديث: 1 نوفمبر 2026
المطوّر: Hamish Notes · للتواصل: noteshamish@gmail.com

1. التطبيق أداة لقراءة ملفات PDF والكتابة عليها وتنظيم المذاكرة، ويُقدَّم «كما هو» دون أي ضمان صريح أو ضمني.

2. أنت مسؤول عن الملفات التي تفتحها في التطبيق وعن حقك في استخدامها (مثل المحاضرات والكتب). لا يملك المطوّر أي حقوق على ملفاتك ولا يطّلع عليها.

3. الترجمة، وقراءة النص من الصفحات، وتحويل الكتابة اليدوية إلى نص، والملخصات تُنتج تلقائيًا وقد تحتوي أخطاء. لا تعتمد عليها وحدها في معلومات مهمة؛ راجع دائمًا المصدر الأصلي.

4. ملفاتك وملاحظاتك محفوظة على جهازك فقط. أنت مسؤول عن عمل نسخ احتياطية منها؛ قد تضيع عند حذف التطبيق أو تعطل الجهاز.

5. هامش Premium اشتراك سنوي اختياري عبر Google Play:
   • يظهر السعر بعملتك قبل الشراء، ويُخصم من حسابك في Google Play.
   • للمشتركين الجدد تجربة مجانية 7 أيام. ألغِ قبل انتهائها حتى لا يُخصم أي مبلغ.
   • يتجدد الاشتراك تلقائيًا كل سنة ما لم تلغِه. يمكنك الإلغاء في أي وقت من Google Play ← الاشتراكات، وتبقى المزايا حتى نهاية المدة المدفوعة.
   • طلبات الاسترداد تخضع لسياسة الاسترداد في Google Play.
   • Premium مرتبط بحسابك، ويحتاج اتصالًا بالإنترنت من حين لآخر للتحقق منه. قد نوقف Premium عند استخدام نسخة معدّلة من التطبيق أو أي تحايل على الاشتراك.
   • قد نضيف مزايا أو نعدّلها مع تطوير التطبيق.

6. يمكنك حذف حسابك في أي وقت من الإعدادات ← الحساب ← حذف حسابي. حذف الحساب يلغي تجديد اشتراكك.

7. يستخدم التطبيق مكوّنات مفتوحة المصدر وخدمات Google ML Kit وفق تراخيصها وشروطها (انظر «تراخيص المكتبات المفتوحة»).

8. لا يتحمل المطوّر مسؤولية أي خسارة أو ضرر ينتج عن استخدام التطبيق، في حدود ما يسمح به القانون.

9. قد تُحدَّث هذه الشروط؛ استمرارك في استخدام التطبيق يعني قبولك للنسخة الأحدث.

للتواصل: noteshamish@gmail.com""",
    """Terms of Use — Hamish

Last updated: November 1, 2026
Developer: Hamish Notes · Contact: noteshamish@gmail.com

1. The app is a tool for reading and annotating PDF files and organizing study, provided “as is” without warranty of any kind, express or implied.

2. You are responsible for the files you open in the app and for your right to use them (such as lectures and books). The developer has no rights to your files and never sees them.

3. Translation, page text reading, handwriting-to-text and summaries are produced automatically and may contain errors. Don’t rely on them alone for important information; always check the original source.

4. Your files and notes are stored on your device only. You are responsible for backing them up; they may be lost if the app is uninstalled or the device fails.

5. Hamish Premium is an optional yearly subscription through Google Play:
   • The price is shown in your currency before purchase and charged to your Google Play account.
   • New subscribers get a 7-day free trial. Cancel before it ends to avoid being charged.
   • The subscription renews automatically every year unless you cancel. You can cancel anytime in Google Play → Subscriptions, and keep Premium until the end of the paid period.
   • Refund requests follow the Google Play refund policy.
   • Premium is tied to your account and needs an internet connection from time to time to verify it. We may suspend Premium when a modified version of the app or any circumvention of the subscription is used.
   • We may add or change features as the app evolves.

6. You can delete your account anytime in Settings → Account → Delete my account. Deleting your account cancels your subscription renewal.

7. The app uses open-source components and Google ML Kit under their licenses and terms (see “Open-source licenses”).

8. To the extent permitted by law, the developer is not liable for any loss or damage resulting from use of the app.

9. These terms may be updated; continuing to use the app means you accept the latest version.

Contact: noteshamish@gmail.com"""
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
