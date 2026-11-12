import SwiftUI

enum LegalDoc: String, Identifiable {
    case privacy, terms, licenses
    var id: String { rawValue }
}

/// مكوّن مستخدم في التطبيق وترخيصه.
private struct OssItem: Identifiable {
    let id = UUID()
    let name: String
    let owner: String
    let license: String
    let asset: String?
}

private let ossItems: [OssItem] = [
    OssItem(name: "Almarai font", owner: "Boutros Fonts", license: "SIL Open Font License 1.1", asset: "ofl-1.1"),
    OssItem(name: "Caveat font", owner: "The Caveat Project Authors", license: "SIL Open Font License 1.1", asset: "ofl-caveat"),
    OssItem(name: "Phosphor Icons", owner: "Phosphor Icons", license: "MIT License", asset: "mit-phosphor"),
    OssItem(name: "PDFKit, PencilKit, Vision, StoreKit", owner: "Apple Inc.", license: L("أطر نظام iPadOS", "iPadOS system frameworks"), asset: nil)
]

func privacyText() -> String {
    L("""
سياسة الخصوصية — especial notes لـ iPad

المطوّر: Hamish Notes · للتواصل: noteshamish@gmail.com

الخلاصة: ملفاتك وملاحظاتك على جهازك فقط ولا نطّلع عليها. لا إعلانات، ولا بيع للبيانات، ولا أدوات تتبّع أو تحليلات.

• على جهازك فقط: ملفات PDF، والكتابة، والتمييز، والملخصات، والبطاقات، والتسجيلات الصوتية، وإحصاءات الدراسة، والإعدادات. لا تُرسل إلينا.

• تسجيل الدخول بـ Google أو Apple (اختياري): يبقى الاسم والبريد على جهازك لعرضهما في التطبيق.

• الاشتراك: يديره App Store بالكامل. لا نحصل على بيانات بطاقتك أبدًا.

• قراءة النص وتحويل الكتابة إلى نص والترجمة: تعمل بأطر Apple على جهازك، ولا يُرسل محتوى ملفاتك.

• الأذونات: الميكروفون فقط لتسجيل المحاضرة (يبقى على جهازك)، والكاميرا لمسح المستندات، وFace ID يتحقق منه النظام ولا نحصل عليه، والصور لإضافتها إلى صفحاتك.

• الحذف: حذف التطبيق يحذف ملفاتك من جهازك.

• الأطفال: التطبيق أداة دراسة عامة، غير موجّهة للأطفال دون 13 عامًا.

للتواصل: noteshamish@gmail.com
""", """
Privacy Policy — especial notes for iPad

Developer: Hamish Notes · Contact: noteshamish@gmail.com

Summary: your files and notes stay on your device and we never see them. No ads, no selling of data, no tracking or analytics tools.

• On your device only: PDFs, handwriting, highlights, summaries, cards, audio recordings, study stats and settings. Never sent to us.

• Sign in with Google or Apple (optional): your name and email stay on your device to show them in the app.

• Subscription: handled entirely by the App Store. We never receive your card details.

• Text reading, handwriting to text and translation run on your device with Apple frameworks; your files’ content is not sent.

• Permissions: microphone only for lecture recording (stays on your device), camera to scan documents, Face ID is verified by the system and never reaches us, photos to add them to your pages.

• Deletion: deleting the app deletes your files from your device.

• Children: the app is a general study tool, not directed at children under 13.

Contact: noteshamish@gmail.com
""")
}

func termsText() -> String {
    L("""
شروط الاستخدام — especial notes

• التطبيق أداة دراسة شخصية. أنت مسؤول عن الملفات التي تضيفها وعن حقك في استخدامها.

• ملفاتك على جهازك، فاحتفظ بنسخة منها (صدّرها إلى «الملفات» أو iCloud) قبل حذف التطبيق أو تغيير الجهاز.

• الاشتراك السنوي يتجدد تلقائيًا عبر App Store ما لم تلغه قبل 24 ساعة من نهاية الفترة، وتديره من إعدادات حسابك.

• يُقدَّم التطبيق «كما هو»، ولا نتحمل مسؤولية فقدان بيانات أو أخطاء في قراءة النص أو الترجمة.

للتواصل: noteshamish@gmail.com
""", """
Terms of use — especial notes

• The app is a personal study tool. You are responsible for the files you add and your right to use them.

• Your files live on your device, so keep a copy (export to Files or iCloud) before deleting the app or changing devices.

• The yearly subscription renews automatically through the App Store unless cancelled at least 24 hours before the end of the period. Manage it in your account settings.

• The app is provided “as is”. We are not liable for data loss or for mistakes in text reading or translation.

Contact: noteshamish@gmail.com
""")
}

/// نافذة نص قانوني (الخصوصية، الشروط، التراخيص).
struct LegalSheet: View {
    let doc: LegalDoc
    @Environment(\.dismiss) private var dismiss
    @State private var shown: OssItem? = nil

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    switch doc {
                    case .privacy: Text(privacyText()).font(Fonts.sans(14)).lineSpacing(5)
                    case .terms: Text(termsText()).font(Fonts.sans(14)).lineSpacing(5)
                    case .licenses:
                        Text(L("يستخدم especial notes المكوّنات التالية وفق تراخيصها. انقر على أي منها لقراءة نص الترخيص.",
                               "especial notes uses the following components under their licenses. Tap one to read its license text."))
                            .font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                        ForEach(ossItems) { it in
                            Button { if it.asset != nil { shown = it } } label: {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(it.name).font(Fonts.sans(14, .bold)).foregroundColor(Palette.textPrimary)
                                    Text(it.owner).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
                                    Text(it.license).font(Fonts.sans(12)).foregroundColor(Palette.navy)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.vertical, 6)
                            }
                        }
                    }
                }
                .foregroundColor(Palette.textPrimary)
                .padding(22)
            }
            .background(Palette.ivory)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L("تم", "Done")) { dismiss() } } }
            .sheet(item: $shown) { it in
                NavigationStack {
                    ScrollView {
                        Text(licenseText(it.asset ?? "")).font(.system(size: 11, design: .monospaced)).padding(18)
                    }
                    .navigationTitle(it.name)
                    .navigationBarTitleDisplayMode(.inline)
                }
            }
        }
        .environment(\.layoutDirection, .leftToRight)
    }

    private var title: String {
        switch doc {
        case .privacy: return L("سياسة الخصوصية", "Privacy policy")
        case .terms: return L("شروط الاستخدام", "Terms of use")
        case .licenses: return L("تراخيص المكتبات المفتوحة", "Open-source licenses")
        }
    }

    private func licenseText(_ name: String) -> String {
        if let p = Bundle.main.path(forResource: name, ofType: "txt", inDirectory: "licenses"),
           let s = try? String(contentsOfFile: p) { return s }
        return name
    }
}

/// رقم الإصدار.
var appVersion: String {
    Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
}
