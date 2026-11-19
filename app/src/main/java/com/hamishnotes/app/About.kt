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
    OssItem("Amiri font", "The Amiri Project Authors", "SIL Open Font License 1.1", "ofl-amiri.txt"),
    OssItem("Architects Daughter font", "Kimberly Geswein", "SIL Open Font License 1.1", "ofl-architectsdaughter.txt"),
    OssItem("Aref Ruqaa font", "The Aref Ruqaa Project Authors", "SIL Open Font License 1.1", "ofl-arefruqaa.txt"),
    OssItem("Covered By Your Grace font", "Kimberly Geswein", "SIL Open Font License 1.1", "ofl-coveredbyyourgrace.txt"),
    OssItem("Dancing Script font", "The Dancing Script Project Authors", "SIL Open Font License 1.1", "ofl-dancingscript.txt"),
    OssItem("Gloria Hallelujah font", "Kimberly Geswein", "SIL Open Font License 1.1", "ofl-gloriahallelujah.txt"),
    OssItem("Indie Flower font", "The Indie Flower Authors", "SIL Open Font License 1.1", "ofl-indieflower.txt"),
    OssItem("Patrick Hand font", "Patrick Wagesreiter", "SIL Open Font License 1.1", "ofl-patrickhand.txt"),
    OssItem("Rakkas font", "The Rakkas Project Authors", "SIL Open Font License 1.1", "ofl-rakkas.txt"),
    OssItem("Reem Kufi font", "The Reem Kufi Project Authors", "SIL Open Font License 1.1", "ofl-reemkufi.txt"),
    OssItem("Shadows Into Light font", "Kimberly Geswein", "SIL Open Font License 1.1", "ofl-shadowsintolight.txt"),
    OssItem("Tajawal font", "Boutros International", "SIL Open Font License 1.1", "ofl-tajawal.txt"),
    OssItem("Vibes font", "The Vibes Project Authors", "SIL Open Font License 1.1", "ofl-vibes.txt"),
    OssItem("Google Fonts library (downloaded on request)", "Each font's authors", "SIL Open Font License 1.1 or Apache License 2.0 (each font's own license)", "ofl-1.1.txt"),
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
                    L("يستخدم «Special notes» المكوّنات التالية وفق تراخيصها. انقر على أي منها لقراءة نص الترخيص.",
                        "Special notes uses the following components under their licenses. Tap one to read its license text."),
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
    """سياسة الخصوصية — Special notes

آخر تحديث: 19 نوفمبر 2026
التطبيق: Special notes (com.hamishnotes.app) · للتواصل: noteshamish@gmail.com

الخلاصة: ملفاتك وملاحظاتك على جهازك ولا نطّلع عليها. نحفظ على خادمنا الحد الأدنى اللازم لحسابك واشتراكك فقط. لا إعلانات، ولا بيع للبيانات، ولا أدوات تتبّع أو تحليلات.

• على جهازك: ملفات PDF، والكتابة، والتمييز، والملخصات، والبطاقات، والتسجيلات الصوتية، والصور التي أضفتها، وإحصاءات الدراسة، والإعدادات. لا تُرسل إلينا أبدًا.

• النسخ الاحتياطي: إن فعّلت النسخ الاحتياطي في جهازك أو نقلت بياناتك إلى جهاز جديد، قد يضمّن نظام Android بيانات التطبيق في نسختك الاحتياطية على حسابك في Google. هذه النسخة تخصك وتديرها Google، ولا نصل إليها.

• الصور: تختار الصور من أداة اختيار الصور في Android، فلا يرى التطبيق إلا ما تختاره، ولا نطلب إذنًا للوصول إلى صورك. نسخة من كل صورة اخترتها تبقى في «صور استخدمتها» على جهازك حتى تزيلها.

• حساب Google (اختياري، ولازم للاشتراك): البريد الإلكتروني، والاسم، ومعرّف حسابك في Google، ورابط صورة حسابك. لإنشاء حسابك وربط اشتراكك به. وتسجّل خدمة الحسابات أوقات الدخول وعنوان IP في سجل أمني.

• الاشتراك: رمز الشراء، ونوع الخطة، وحالتها، وتاريخ انتهائها، وفترة التجربة، للتحقق من اشتراكك. لا نحصل على بيانات بطاقتك أبدًا، فالدفع تديره Google.

• معرّف الجهاز: رمز مستعار مشتق من معرّف التطبيق على جهازك بعد تشفيره (تجزئة)، لا يظهر فيه اسمك، ويُعامل كبيانات شخصية. لربط Premium بجهازك.

• فحص سلامة التطبيق (Google Play Integrity): يؤكد أن التطبيق أصلي وأن الجهاز سليم. إن فشل نحفظ نتيجته مع معرّف الجهاز لمنع النسخ المعدّلة والاحتيال.

• سجلات تقنية: يحفظ مزوّد الخادم سجلات قصيرة المدة (مثل وقت الطلب وعنوان IP) للأمان وتشخيص الأعطال.

• الخدمات: Supabase (الخادم، في فرانكفورت، ألمانيا)، وGoogle (تسجيل الدخول والدفع وPlay Integrity) وفق سياسة خصوصية Google: https://policies.google.com/privacy

• Google ML Kit: قراءة النص والترجمة وتحويل الكتابة إلى نص تعمل على جهازك، ولا يُرسل محتوى ملفاتك. تنزّل حزم اللغات من Google مرة واحدة، وترسل المكتبة بيانات تشخيصية تقنية لا تتضمن اسمك أو محتواك.

• مكتبة الخطوط: عند تنزيل خط يتصل التطبيق بـ GitHub (raw.githubusercontent.com) أو jsDelivr (cdn.jsdelivr.net) لتنزيل ملفه من مكتبة Google Fonts العامة. يصلهم عنوان IP ووقت الطلب كأي تنزيل، ولا نرسل أي بيانات أخرى.

• تطبيقات أخرى تختارها: عند المشاركة أو التصدير أو تحويل عرض باوربوينت، يُرسل الملف إلى التطبيق الذي تختاره أنت، ويخضع لسياسته.

• الأذونات: الميكروفون فقط لتسجيل المحاضرة (يبقى على جهازك)، والإشعارات للتذكير اليومي، والبصمة يتحقق منها نظام Android ولا نحصل عليها، والإنترنت للحساب والاشتراك وحزم اللغات والخطوط.

• الأساس القانوني: تنفيذ طلبك (الحساب والاشتراك)، ومصلحتنا المشروعة في الأمان ومنع الاحتيال (فحص السلامة والسجلات)، والتزاماتنا القانونية.

• مدة الحفظ: الحساب والاشتراك حتى تحذف حسابك. نتائج فحص السلامة الفاشلة 90 يومًا ثم تُحذف تلقائيًا. السجلات التقنية لفترة قصيرة وفق إعدادات المزوّد. فواتير الشراء تحتفظ بها Google وفق سياستها.

• مكان المعالجة: تُخزَّن بياناتك في الاتحاد الأوروبي (ألمانيا)، ويديرها المطوّر من خارجه، وقد تعالج Google بعض البيانات في دول أخرى وفق ضماناتها.

• حقوقك: يحق لك الاطلاع على بياناتك، وتصحيحها، وحذفها، ونقلها، والاعتراض على معالجتها أو تقييدها. راسلنا ونرد خلال 30 يومًا. وإن كنت في الاتحاد الأوروبي أو بلد مشابه يحق لك تقديم شكوى إلى جهة حماية البيانات في بلدك.

• الحذف: احذف حسابك من الإعدادات ← الحساب ← حذف حسابي، أو اطلبه بالبريد. نحذف حسابك وبيانات اشتراكك وسجلات الفحص، ونلغي تجديد اشتراكك في Google Play. حذف التطبيق يحذف ملفاته من جهازك، إلا ما في نسختك الاحتياطية إن كانت مفعّلة.

• الأمان: اتصال مشفّر (HTTPS)، ووصول مقيّد لقاعدة البيانات، وتفعيل Premium موقّع رقميًا.

• الأطفال: التطبيق أداة دراسة عامة، غير موجّهة للأطفال دون 13 عامًا.

• التغييرات: نحدّث تاريخ «آخر تحديث» عند أي تغيير، ونبلغك داخل التطبيق بالتغييرات الجوهرية.

للتواصل أو طلب بياناتك أو حذفها: noteshamish@gmail.com""",
    """Privacy Policy — Special notes

Last updated: November 19, 2026
App: Special notes (com.hamishnotes.app) · Contact: noteshamish@gmail.com

Summary: your files and notes stay on your device and we never see them. Our server keeps only the minimum needed for your account and subscription. No ads, no selling of data, no tracking or analytics tools.

• On your device: PDFs, handwriting, highlights, summaries, cards, audio recordings, photos you added, study stats and settings. Never sent to us.

• Backup: if you turn on device backup or move your data to a new phone, Android may include the app's data in your own backup in your Google account. That backup is yours and managed by Google; we cannot access it.

• Photos: you pick photos with the Android photo picker, so the app only sees the photos you choose, and we never ask for access to your photo library. A copy of each photo you pick stays in “Recently used” on your device until you remove it.

• Google account (optional, required to subscribe): email address, name, Google account identifier and profile picture link, to create your account and link your subscription to it. The account service also records sign-in times and IP addresses in a security log.

• Subscription: purchase token, plan, status, expiry date and free-trial state, to verify your subscription. We never receive your card details; Google handles payment.

• Device identifier: a pseudonymous code made by hashing the app's identifier on your device. It does not contain your name, and we treat it as personal data. Used to bind Premium to your device.

• App integrity check (Google Play Integrity): confirms the app is genuine and the device is not compromised. If it fails we store the result with the device identifier to prevent modified copies and fraud.

• Technical logs: our hosting provider keeps short-lived logs (such as request time and IP address) for security and troubleshooting.

• Services: Supabase (server, in Frankfurt, Germany) and Google (sign-in, billing and Play Integrity) under the Google Privacy Policy: https://policies.google.com/privacy

• Google ML Kit: text reading, translation and handwriting-to-text run on your device; your files' content is not sent. Language packs are downloaded from Google once, and the library sends technical diagnostics that do not include your name or content.

• Font library: when you download a font, the app connects to GitHub (raw.githubusercontent.com) or jsDelivr (cdn.jsdelivr.net) to fetch it from the public Google Fonts library. They receive your IP address and the request time like any download; we send nothing else.

• Other apps you choose: when you share, export or convert a PowerPoint file, the file goes to the app you pick, under that app's policy.

• Permissions: microphone only for lecture recording (stays on your device); notifications for the daily study reminder; fingerprint is verified by Android and never reaches us; internet for your account, subscription, language packs and fonts.

• Legal bases: performing what you ask for (account and subscription), our legitimate interest in security and fraud prevention (integrity checks and logs), and our legal obligations.

• Retention: account and subscription data until you delete your account. Failed integrity results for 90 days, then deleted automatically. Technical logs for a short period set by the provider. Purchase receipts are kept by Google under its own policy.

• Where data is processed: your data is stored in the European Union (Germany). The developer manages it from outside the EU, and Google may process some data in other countries under its own safeguards.

• Your rights: you can access, correct, delete or export your data, and object to or restrict its processing. Email us and we will reply within 30 days. If you are in the EU or a similar jurisdiction, you can also complain to your data protection authority.

• Deletion: delete your account in Settings → Account → Delete my account, or request it by email. We delete your account, subscription data and integrity records, and cancel your Google Play renewal. Uninstalling the app deletes its files from your device, except anything in your own backup if it is turned on.

• Security: encrypted connections (HTTPS), restricted database access, and digitally signed Premium activation.

• Children: the app is a general study tool, not directed at children under 13.

• Changes: we update the “Last updated” date whenever this policy changes, and tell you in the app about important changes.

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
    """شروط الاستخدام — Special notes

آخر تحديث: 19 نوفمبر 2026
التطبيق: Special notes (com.hamishnotes.app) · للتواصل: noteshamish@gmail.com

1. التطبيق أداة لقراءة ملفات PDF والكتابة عليها وتنظيم المذاكرة، ويُقدَّم «كما هو» دون ضمان صريح أو ضمني، في حدود ما يسمح به القانون. لا يُنقص هذا من حقوقك الإلزامية كمستهلك في بلدك.

2. أنت مسؤول عن الملفات التي تفتحها في التطبيق وعن حقك في استخدامها (مثل المحاضرات والكتب والصور). لا يملك المطوّر أي حقوق على ملفاتك ولا يطّلع عليها.

3. تسجيل المحاضرات: إن سجّلت محاضرة أو حديثًا فأنت مسؤول عن الحصول على إذن من سجّلتهم، واحترام أنظمة جامعتك والقوانين في بلدك.

4. الترجمة، وقراءة النص من الصفحات، وتحويل الكتابة اليدوية إلى نص، والملخصات تُنتج تلقائيًا وقد تحتوي أخطاء. لا تعتمد عليها وحدها في معلومات مهمة؛ راجع دائمًا المصدر الأصلي.

5. ملفاتك وملاحظاتك محفوظة على جهازك. أنت مسؤول عن عمل نسخ احتياطية منها؛ قد تضيع عند حذف التطبيق أو تعطل الجهاز.

6. Special notes Premium اشتراك سنوي اختياري عبر Google Play:
   • يظهر السعر بعملتك قبل الشراء، ويُخصم من حسابك في Google Play.
   • للمشتركين الجدد تجربة مجانية 7 أيام. ألغِ قبل انتهائها حتى لا يُخصم أي مبلغ.
   • يتجدد الاشتراك تلقائيًا كل سنة ما لم تلغِه. يمكنك الإلغاء في أي وقت من Google Play ← الاشتراكات، وتبقى المزايا حتى نهاية المدة المدفوعة.
   • طلبات الاسترداد تخضع لسياسة الاسترداد في Google Play، ولا تُنقص من حقوقك القانونية.
   • Premium مرتبط بحسابك، ويحتاج اتصالًا بالإنترنت من حين لآخر للتحقق منه.
   • نطوّر التطبيق ونضيف مزايا باستمرار. لن نزيل ميزة أساسية من Premium خلال مدة دفعت ثمنها؛ وإن اضطررنا لتغيير جوهري نبلغك مسبقًا ويحق لك إلغاء الاشتراك.

7. الاستخدام المسموح: لا يجوز تعديل التطبيق أو فك شفرته أو إعادة توزيعه، ولا التحايل على الاشتراك أو فحص السلامة. قد نوقف Premium أو الحساب عند مخالفة ذلك.

8. الملكية: التطبيق واسمه وشعاره وتصميمه ملك للمطوّر. ما تكتبه وتضيفه ملكك.

9. يمكنك حذف حسابك في أي وقت من الإعدادات ← الحساب ← حذف حسابي. حذف الحساب يلغي تجديد اشتراكك.

10. يستخدم التطبيق مكوّنات مفتوحة المصدر وخدمات Google ML Kit وفق تراخيصها وشروطها (انظر «تراخيص المكتبات المفتوحة»).

11. في حدود ما يسمح به القانون، لا يتحمل المطوّر مسؤولية أي خسارة غير مباشرة أو فقدان بيانات ينتج عن استخدام التطبيق. لا يستبعد هذا أي مسؤولية لا يجوز استبعادها قانونًا.

12. تخضع هذه الشروط لقوانين دولة ليبيا، دون أن يحرمك ذلك من الحماية الإلزامية لقانون المستهلك في بلد إقامتك. نحاول حل أي خلاف وديًا أولًا عبر البريد.

13. قد نحدّث هذه الشروط. نبلغك داخل التطبيق بالتغييرات الجوهرية قبل سريانها، واستمرارك في الاستخدام بعدها يعني قبولها.

للتواصل: noteshamish@gmail.com""",
    """Terms of Use — Special notes

Last updated: November 19, 2026
App: Special notes (com.hamishnotes.app) · Contact: noteshamish@gmail.com

1. The app is a tool for reading and annotating PDF files and organizing study, provided “as is” without warranty of any kind, express or implied, to the extent permitted by law. This does not affect your mandatory consumer rights in your country.

2. You are responsible for the files you open in the app and for your right to use them (such as lectures, books and images). The developer has no rights to your files and never sees them.

3. Lecture recording: if you record a lecture or conversation, you are responsible for getting permission from the people you record and for following your university's rules and the law in your country.

4. Translation, page text reading, handwriting-to-text and summaries are produced automatically and may contain errors. Don’t rely on them alone for important information; always check the original source.

5. Your files and notes are stored on your device. You are responsible for backing them up; they may be lost if the app is uninstalled or the device fails.

6. Special notes Premium is an optional yearly subscription through Google Play:
   • The price is shown in your currency before purchase and charged to your Google Play account.
   • New subscribers get a 7-day free trial. Cancel before it ends to avoid being charged.
   • The subscription renews automatically every year unless you cancel. You can cancel anytime in Google Play → Subscriptions, and keep Premium until the end of the paid period.
   • Refund requests follow the Google Play refund policy, without limiting your legal rights.
   • Premium is tied to your account and needs an internet connection from time to time to verify it.
   • We keep improving the app and adding features. We will not remove a core Premium feature during a period you have paid for; if a major change is unavoidable, we will tell you in advance and you may cancel.

7. Acceptable use: you may not modify, reverse engineer or redistribute the app, or circumvent the subscription or the integrity check. We may suspend Premium or the account if you do.

8. Ownership: the app, its name, logo and design belong to the developer. What you write and add is yours.

9. You can delete your account anytime in Settings → Account → Delete my account. Deleting your account cancels your subscription renewal.

10. The app uses open-source components and Google ML Kit under their licenses and terms (see “Open-source licenses”).

11. To the extent permitted by law, the developer is not liable for indirect loss or data loss resulting from use of the app. This does not exclude any liability that cannot be excluded by law.

12. These terms are governed by the laws of Libya, without depriving you of the mandatory consumer protections of your country of residence. We will first try to resolve any dispute amicably by email.

13. We may update these terms. We will tell you in the app about important changes before they take effect; continuing to use the app afterwards means you accept them.

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
