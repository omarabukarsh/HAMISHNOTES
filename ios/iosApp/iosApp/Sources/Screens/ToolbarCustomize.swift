import SwiftUI

// تخصيص شريط الأدوات و«تعرّف على الأدوات» (نفس ToolbarCustomizeDialog وToolCatalog وToolDemo في أندرويد).

enum ToolDemoKind { case pen, highlight, snap, noSummary, summary, eraser, laser, shapes, nav, bookmark, timer, image, text, addPage, ocr, search, export, undo, zoom, fit, focus, cards, lasso, sticky, zoomWin, night }

struct ToolInfo: Identifiable {
    let id: String
    let name: String
    let benefit: String
    let icon: String
    let demo: ToolDemoKind
    var essential = false
    var tourOnly = false
}

enum ToolCatalog {
    static var all: [ToolInfo] { [
        ToolInfo(id: "pen", name: L("الأقلام", "Pens"), benefit: L("اكتب وارسم بقلم جاف أو حبر أو رصاص، بأي لون وحجم. الضغط على القلم يغيّر سماكة الخط.", "Write and draw with a ballpoint, fountain pen or pencil in any color and size. Pen pressure changes the line width."), icon: "ph_pen_nib", demo: .pen, essential: true),
        ToolInfo(id: "highlighter", name: L("الهايلايتر", "Highlighter"), benefit: L("ظلّل الجمل المهمة. بحواف دائرية ناعمة ويظهر خلف النص فيبقى النص واضحًا.", "Highlight important sentences. Soft rounded edges, drawn behind the text so it stays clear."), icon: "ph_highlighter", demo: .highlight, essential: true),
        ToolInfo(id: "eraser", name: L("الممحاة", "Eraser"), benefit: L("تمسح الكتابة أو الكلمات المظللة التي تلمسها فقط، والباقي يبقى كما هو.", "Erases only the writing or highlighted words it touches; everything else stays."), icon: "ph_eraser", demo: .eraser, essential: true),
        ToolInfo(id: "laser", name: L("مؤشر الليزر", "Laser pointer"), benefit: L("للشرح أمام غيرك: نقطة متوهجة أو خط يختفي وحده دون أن يُحفظ.", "For presenting: a glowing dot or a line that fades by itself and is never saved."), icon: "ph_laser", demo: .laser, essential: true),
        ToolInfo(id: "snap", name: L("التقاط النص", "Text snap"), benefit: L("مرّر الهايلايتر على السطر فيلتصق التمييز بالكلمات بدقة ويُضاف نصها إلى الملخص.", "Run the highlighter over a line and it snaps precisely to the words and adds their text to the summary."), icon: "ph_snap", demo: .snap),
        ToolInfo(id: "nosummary", name: L("تمييز بدون ملخص", "Highlight without summary"), benefit: L("خيار في الهايلايتر: ظلّل للتذكير على الصفحة فقط دون أن يدخل النص في الملخص.", "A highlighter option: highlight on the page as a reminder without adding the text to the summary."), icon: "ph_highlighter", demo: .noSummary, tourOnly: true),
        ToolInfo(id: "shapes", name: L("الأشكال", "Shapes"), benefit: L("ارسم خطًا أو سهمًا أو مستطيلًا أو دائرة باليد فيصبح شكلًا مرتبًا. يُفعّل من إعدادات القلم.", "Draw a line, arrow, rectangle or circle by hand and it becomes a neat shape. Enable it in pen settings."), icon: "ph_shapes", demo: .shapes, tourOnly: true),
        ToolInfo(id: "nav", name: L("التنقل بين الصفحات", "Page navigation"), benefit: L("السهمان ورقم الصفحة: اضغط الرقم لتنتقل مباشرة إلى أي صفحة.", "Arrows and page number: tap the number to jump to any page."), icon: "ph_pages", demo: .nav),
        ToolInfo(id: "bookmark", name: L("العلامة المرجعية", "Bookmark"), benefit: L("علّم الصفحات المهمة بشريط ذهبي، وتجدها في «العلامات» داخل درج الصفحات.", "Mark important pages with a gold ribbon; find them under “Bookmarks” in the pages drawer."), icon: "ph_bookmark", demo: .bookmark),
        ToolInfo(id: "timer", name: L("مؤقت الدراسة", "Study timer"), benefit: L("جلسة دراسة ثم راحة بالمدة التي تختارها في الإعدادات. أوقفه مؤقتًا أو أنهه متى شئت، ووقته يُحسب في إحصاءاتك.", "A study session then a break, with durations you choose in Settings. Pause or stop it anytime; its time counts in your stats."), icon: "ph_timer", demo: .timer),
        ToolInfo(id: "image", name: L("إضافة صورة", "Add image"), benefit: L("أضف صورة فوق الصفحة، ثم حرّكها وغيّر حجمها.", "Place an image on the page, then move and resize it."), icon: "ph_image", demo: .image),
        ToolInfo(id: "stickers", name: L("الملصقات", "Stickers"), benefit: L("ضع ملصقًا جاهزًا على الصفحة، أو اصنع ملصقاتك من صورة أو من رسمك فتبقى محفوظة في التطبيق.", "Put a ready-made sticker on the page, or make your own from a photo or your drawing; they stay saved in the app."), icon: "ph_sticker", demo: .image),
        ToolInfo(id: "lasso", name: L("التحديد الحر", "Lasso"), benefit: L("أحِط بالقلم حول كتابتك لتحديدها، ثم اسحبها أو كبّرها بالمقبض أو غيّر لونها أو انسخها أو حوّلها إلى نص مكتوب أو احذفها.", "Loop around your writing to select it, then drag it, resize with the handle, recolor, duplicate, convert to typed text or delete."), icon: "ph_lasso", demo: .lasso),
        ToolInfo(id: "autofont", name: L("Aa تحويل الكتابة تلقائياً", "Aa Auto-convert handwriting"), benefit: L("اكتب بالقلم كالعادة؛ عند إنهاء الجملة تتحول في مكانها إلى الخط الذي تختاره، والتراجع يعيدها لخط يدك.", "Write as usual; when you finish a sentence it turns into the font you chose, in place. Undo brings your handwriting back."), icon: "ph_text", demo: .text),
        ToolInfo(id: "shapetool", name: L("أداة الأشكال", "Shapes tool"), benefit: L("ارسم مستطيلًا أو دائرة أو مثلثًا أو معينًا أو خطًا أو سهمًا أو موصلًا بالسحب، مع نمط الخط وسماكته ورؤوس الأسهم.", "Drag to draw rectangles, circles, triangles, diamonds, lines, arrows or connectors, with line style, thickness and arrowheads."), icon: "ph_shapes", demo: .shapes),
        ToolInfo(id: "sticky", name: L("ملاحظة لاصقة", "Sticky note"), benefit: L("اختر لونًا وانقر على الصفحة لتضع ملاحظة تكتب عليها؛ اسحبها لتحريكها، واطوها لتصبح مربعًا صغيرًا.", "Pick a color and tap the page to place a note you write on; drag to move it, and collapse it to a small square."), icon: "ph_note", demo: .sticky),
        ToolInfo(id: "link", name: L("رابط إلى صفحة", "Page link"), benefit: L("ضع على الصفحة رابطًا إلى صفحة أخرى أو مستند آخر (مثل «جدول المضادات ص 12»)؛ انقر عليه لتنتقل، ويظهر زر للرجوع.", "Place a link to another page or document (e.g. “antibiotics table p. 12”); tap it to jump, with a Back button."), icon: "ph_link", demo: .nav),
        ToolInfo(id: "record", name: L("تسجيل المحاضرة", "Lecture recording"), benefit: L("سجّل الصوت أثناء الكتابة؛ كل خط يُربط بلحظته، فتحدد الكتابة لاحقًا وتسمع ما قيل وقتها.", "Record audio while writing; every stroke is linked to its moment, so you can lasso writing later and hear what was said."), icon: "ph_mic", demo: .nav),
        ToolInfo(id: "split", name: L("مستندان جنبًا إلى جنب", "Split view"), benefit: L("اعرض مستندًا آخر بجانب الحالي (مثل الكتاب والملخص) واسحب الفاصل لتغيير العرض.", "Show another document next to this one (e.g. book and notes) and drag the divider to resize."), icon: "ph_split", demo: .nav),
        ToolInfo(id: "zoomwin", name: L("نافذة الكتابة المكبّرة", "Zoom writing window"), benefit: L("اكتب بخط كبير في نافذة أسفل الشاشة فيظهر صغيرًا ومرتبًا على الصفحة. انقر الصفحة لنقل المنطقة.", "Write large in a window at the bottom; it appears small and neat on the page. Tap the page to move the area."), icon: "ph_zoom_write", demo: .zoomWin),
        ToolInfo(id: "night", name: L("القراءة الليلية", "Night reading"), benefit: L("صفحات داكنة ونص فاتح للمذاكرة ليلًا، مع بقاء ألوان كتابتك وتظليلك.", "Dark pages with light text for night study; your ink and highlight colors are kept."), icon: "ph_moon", demo: .night),
        ToolInfo(id: "text", name: L("مربع نص", "Text box"), benefit: L("اكتب بلوحة المفاتيح في أي مكان من الصفحة.", "Type with the keyboard anywhere on the page."), icon: "ph_text", demo: .text),
        ToolInfo(id: "addpage", name: L("إضافة صفحات", "Add pages"), benefit: L("أضف صفحة فارغة (مسطرة أو مربعات) أو صفحات من ملف PDF آخر.", "Add a blank page (lined or grid) or pages from another PDF."), icon: "ph_add_page", demo: .addPage),
        ToolInfo(id: "ocr", name: L("قراءة النص", "Read text"), benefit: L("يقرأ نص الصفحات الممسوحة ليعمل البحث والتقاط النص.", "Reads the text of scanned pages so search and text snap work."), icon: "ph_scan", demo: .ocr),
        ToolInfo(id: "search", name: L("البحث", "Search"), benefit: L("ابحث عن كلمة داخل المستند، حتى في خط يدك، وتنقّل بين النتائج.", "Find a word in the document, even in your handwriting, and move between results."), icon: "ph_search", demo: .search),
        ToolInfo(id: "export", name: L("الحفظ PDF", "Save as PDF"), benefit: L("احفظ المستند مع كتابتك وتظليلك، أو احفظ الملخص وحده كملف PDF.", "Save the document with your writing and highlights, or save the summary alone as a PDF."), icon: "ph_export", demo: .export),
        ToolInfo(id: "undo", name: L("التراجع والإعادة", "Undo and redo"), benefit: L("تراجع عن آخر تعديل أو أعده. أو انقر الصفحة بإصبعين للتراجع وبثلاثة للإعادة.", "Undo or redo the last change. Or tap the page with two fingers to undo and three to redo."), icon: "ph_undo", demo: .undo),
        ToolInfo(id: "zoom", name: L("التكبير والتصغير", "Zoom"), benefit: L("كبّر الصفحة لقراءة التفاصيل أو صغّرها لرؤية أكثر. يعمل أيضًا بإصبعين.", "Zoom in for details or out to see more. Also works with two fingers."), icon: "ph_zoom_in", demo: .zoom),
        ToolInfo(id: "fit", name: L("ملاءمة الصفحة", "Fit page"), benefit: L("اعرض الصفحة كاملة على الشاشة، ونقرة ثانية تعيد ملء العرض.", "Show the whole page on screen; tap again to fill the width."), icon: "ph_fit", demo: .fit),
        ToolInfo(id: "focus", name: L("وضع التركيز", "Focus mode"), benefit: L("يخفي كل الأشرطة لتبقى مع الصفحة فقط، والمؤقت يبقى معك.", "Hides all bars so only the page remains; the timer stays with you."), icon: "ph_eye", demo: .focus),
        ToolInfo(id: "readtimer", name: L("عداد وقت القراءة", "Reading time counter"), benefit: L("يظهر في الزاوية كم قضيت في قراءة هذا المستند.", "Shows in the corner how long you have spent reading this document."), icon: "ph_timer", demo: .timer),
        ToolInfo(id: "summary", name: L("الملخصات", "Summaries"), benefit: L("كل ما تلتقطه بالهايلايتر يُجمع مرتبًا في درج «ملخصات»: عناوين وفقرات وجداول تعدّلها بنفسك.", "Everything you snap with the highlighter is collected in the “Summaries” drawer: headings, paragraphs and tables you can edit."), icon: "ph_summary", demo: .summary, tourOnly: true),
        ToolInfo(id: "presets", name: L("أقلامي المفضلة", "Favorite pens"), benefit: L("في شريط الأقلام: النجمة تحفظ القلم الحالي بلونه وحجمه، ونقرة واحدة تعيده. اضغط مطولًا لحذفه.", "In the pen bar: the star saves the current pen with its color and size; one tap brings it back. Long-press to remove."), icon: "ph_star_plus", demo: .pen, tourOnly: true),
        ToolInfo(id: "outline", name: L("فهرس المحاضرة", "Lecture contents"), benefit: L("إن كان في ملف PDF فهرس عناوين يظهر في درج الصفحات تحت «الفهرس»، والنقر ينقلك للفصل.", "If the PDF has a table of contents it appears under “Contents” in the pages drawer; tap to jump."), icon: "ph_list", demo: .nav, tourOnly: true),
        ToolInfo(id: "pages", name: L("ترتيب الصفحات", "Arrange pages"), benefit: L("اضغط مطولًا على صفحة في درج الصفحات لتحريكها أو نسخها أو حذفها.", "Long-press a page in the pages drawer to move, duplicate or delete it."), icon: "ph_pages", demo: .addPage, tourOnly: true),
        ToolInfo(id: "translate", name: L("الترجمة", "Translation"), benefit: L("حدد نصًا واختر «ترجمة»: إنجليزي ↔ عربي على الجهاز.", "Select text and choose “Translate”: English ↔ Arabic on the device."), icon: "ph_translate", demo: .text, tourOnly: true),
        ToolInfo(id: "exam", name: L("موعد الامتحان", "Exam countdown"), benefit: L("اضغط مطولًا على المادة واختر «موعد الامتحان»: ترى الأيام المتبقية وكم صفحة تحتاج يوميًا.", "Long-press a subject and choose “Exam date”: see days left and pages needed per day."), icon: "ph_timer", demo: .timer, tourOnly: true),
        ToolInfo(id: "cards", name: L("البطاقات", "Cards"), benefit: L("اصنع بطاقة بعنوان ومحتوى تكتبهما بنفسك، واقلبها للمراجعة. من «بطاقات» في درج الملخصات.", "Make cards with your own title and content and flip them to review. From “Cards” in the summaries drawer."), icon: "ph_cards", demo: .cards, tourOnly: true)
    ] }
    static var essentials: [ToolInfo] { all.filter { $0.essential } }
    static var optional: [ToolInfo] { all.filter { !$0.essential && !$0.tourOnly } }
}

struct ToolbarCustomizeSheet: View {
    @EnvironmentObject var app: AppController
    @Environment(\.dismiss) private var dismiss
    @State private var learn = false
    @State private var learnIndex = 0

    var body: some View {
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(L("شريط الأدوات", "Toolbar")).font(Fonts.display(24)).foregroundColor(Palette.textPrimary)
                        Text(L("اختر شكل شريطك والأدوات التي تظهر فيه.", "Choose how your toolbar looks and which tools it shows.")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                    }
                    Spacer()
                    Button { app.resetToolbar() } label: {
                        Text(L("↺ استعادة الافتراضي", "↺ Restore default")).font(Fonts.sans(13, .bold)).foregroundColor(Palette.goldDeep)
                            .padding(.horizontal, 14).padding(.vertical, 8).background(Capsule().fill(Color(hex: 0xFFFBF4E2)))
                    }
                    CloseButton { dismiss() }.padding(.leading, 10)
                }
                HStack(spacing: 26) {
                    tab(L("تخصيص", "Customize"), !learn) { learn = false }
                    tab(L("تعرّف على الأدوات", "Learn the tools"), learn) { learn = true }
                }
            }
            .padding(.leading, 26).padding(.trailing, 18).padding(.top, 20)
            .background(Color.white)
            Rectangle().fill(Palette.divider).frame(height: 1)
            GeometryReader { g in
                let wide = g.size.width >= 640
                if learn {
                    LearnTools(index: $learnIndex, wide: wide)
                } else {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 0) {
                            label(L("معاينة", "Preview"))
                            livePreview
                            label(L("شكل الشريط", "Toolbar style"))
                            HStack(spacing: 14) {
                                styleOption(.penBar, L("شريط الأقلام", "Pen bar"), L("زر قلم واحد؛ الضغط عليه يفتح شريطًا ثانيًا فيه كل الأقلام والهايلايتر والألوان والحجم.", "One pen button. Tapping it opens a second bar with all pens, the highlighter, colors and size."))
                                styleOption(.classic, L("الكلاسيكي", "Classic"), L("القلم والهايلايتر زرّان منفصلان؛ نقرة ثانية على الأداة تفتح إعداداتها.", "Pen and highlighter are separate buttons. Tap a tool again to open its settings."))
                            }
                            let cols = wide ? 3 : 2
                            label(L("الأدوات الأساسية", "Essential tools"))
                            toolGrid(ToolCatalog.essentials, cols: cols, essential: true)
                            label(L("أدوات إضافية · اضغط لإظهارها أو إخفائها", "More tools · tap to show or hide"))
                            toolGrid(ToolCatalog.optional, cols: cols, essential: false)
                        }
                        .padding(.horizontal, 26).padding(.bottom, 28)
                    }
                }
            }
        }
        .background(Palette.background)
    }

    private func tab(_ t: String, _ on: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 10) {
                Text(t).font(Fonts.sans(14, .bold)).foregroundColor(on ? Palette.navy : Palette.textSecondary)
                RoundedRectangle(cornerRadius: 2).fill(on ? Palette.gold : Color.clear).frame(height: 3)
            }
            .fixedSize()
        }
    }

    private func label(_ t: String) -> some View {
        Text(t).font(Fonts.sans(13, .bold)).foregroundColor(Palette.goldDeep).padding(.top, 22).padding(.bottom, 10)
    }

    private var livePreview: some View {
        let shown = ToolCatalog.essentials + ToolCatalog.optional.filter { app.toolVisible($0.id) }
        return VStack(spacing: 10) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 4) {
                    ForEach(Array(shown.enumerated()), id: \.element.id) { i, t in
                        if i == ToolCatalog.essentials.count { Rectangle().fill(Color(hex: 0xFFE3E6EC)).frame(width: 1, height: 24).padding(.horizontal, 4) }
                        PhIcon(t.icon, i == 0 ? .white : Palette.navy, 20).frame(width: 38, height: 38)
                            .background(RoundedRectangle(cornerRadius: 11).fill(i == 0 ? Palette.navy : Color.clear))
                    }
                }
                .padding(.horizontal, 8).padding(.vertical, 6)
                .background(RoundedRectangle(cornerRadius: 16).fill(Color.white).shadow(color: .black.opacity(0.12), radius: 6, y: 2))
            }
            Text(L("معاينة حية · \(shown.count) أداة في شريطك", "Live preview · \(shown.count) tools in your toolbar")).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
        }
        .padding(.vertical, 20).padding(.horizontal, 16)
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: 20).fill(LinearGradient(colors: [Color(hex: 0xFFE9EDF3), Color(hex: 0xFFDDE3EC)], startPoint: .top, endPoint: .bottom)))
    }

    private func styleOption(_ s: ToolbarStyle, _ t: String, _ d: String) -> some View {
        let sel = app.toolbarStyle == s
        return Button { app.toolbarStyle = s } label: {
            VStack(alignment: .leading, spacing: 6) {
                StylePreview(style: s).frame(height: 70)
                HStack {
                    Text(t).font(Fonts.sans(15, .bold)).foregroundColor(sel ? Palette.navy : Palette.textPrimary)
                    Spacer()
                    Circle().stroke(sel ? Palette.navy : Color(hex: 0xFFCDD2DA), lineWidth: sel ? 6 : 2).frame(width: 20, height: 20)
                }
                Text(d).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary).multilineTextAlignment(.leading).fixedSize(horizontal: false, vertical: true)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color.white))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(sel ? Palette.navy : Palette.divider, lineWidth: sel ? 2 : 1))
        }
    }

    private func toolGrid(_ tools: [ToolInfo], cols: Int, essential: Bool) -> some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: cols), spacing: 10) {
            ForEach(tools) { t in
                let on = essential || app.toolVisible(t.id)
                HStack(spacing: 11) {
                    PhIcon(t.icon, on ? Palette.navy : Color(hex: 0xFF9AA1AD), 20).frame(width: 38, height: 38)
                        .background(RoundedRectangle(cornerRadius: 11).fill(on ? Palette.navySoft : Color(hex: 0xFFF3F4F6)))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(t.name).font(Fonts.sans(13.5, .bold)).foregroundColor(Palette.textPrimary).lineLimit(2)
                        Button {
                            if let i = ToolCatalog.all.firstIndex(where: { $0.id == t.id }) { learnIndex = i; learn = true }
                        } label: { Text(L("ⓘ كيف تعمل", "ⓘ How it works")).font(Fonts.sans(11)).foregroundColor(Palette.textSecondary) }
                    }
                    Spacer(minLength: 4)
                    if essential {
                        Text(L("دائمًا ظاهرة", "Always on")).font(Fonts.sans(10.5, .bold)).foregroundColor(Palette.goldDeep)
                            .padding(.horizontal, 7).padding(.vertical, 3).background(RoundedRectangle(cornerRadius: 8).fill(Color(hex: 0xFFFBF4E2)))
                    } else {
                        ZStack {
                            if on { Circle().fill(Palette.navy); PhIcon("ph_check", .white, 14) } else { Circle().stroke(Color(hex: 0xFFCDD2DA), lineWidth: 2) }
                        }
                        .frame(width: 24, height: 24)
                    }
                }
                .padding(12)
                .background(RoundedRectangle(cornerRadius: 16).fill(Color.white))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Palette.divider, lineWidth: 1))
                .contentShape(Rectangle())
                .onTapGesture { if !essential { app.setToolVisible(t.id, !on) } }
            }
        }
    }
}

/// معاينة مصغّرة لشكل الشريط.
private struct StylePreview: View {
    let style: ToolbarStyle
    var body: some View {
        VStack(spacing: 6) {
            HStack(spacing: 6) {
                ForEach(0..<(style == .penBar ? 5 : 6), id: \.self) { i in
                    RoundedRectangle(cornerRadius: 6).fill(i == 0 ? Palette.navy : Color(hex: 0xFFE3E6EC)).frame(width: 22, height: 22)
                }
            }
            .padding(6).background(Capsule().fill(Color.white).shadow(color: .black.opacity(0.1), radius: 3, y: 1))
            if style == .penBar {
                HStack(spacing: 5) {
                    ForEach([0xFF000000, 0xFF1565C0, 0xFFC62828, 0xFF2E7D32, 0xFFFFEB3B] as [UInt32], id: \.self) { c in Circle().fill(Color(hex: c)).frame(width: 14, height: 14) }
                }
                .padding(5).background(Capsule().fill(Color.white).shadow(color: .black.opacity(0.1), radius: 3, y: 1))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color(hex: 0xFFEDF0F5)))
    }
}

/// «تعرّف على الأدوات»: قائمة الأدوات وبجانبها شرح الأداة المختارة مع رسم متحرك.
private struct LearnTools: View {
    @EnvironmentObject var app: AppController
    @Binding var index: Int
    let wide: Bool

    var body: some View {
        let tools = ToolCatalog.all
        Group {
            if wide {
                HStack(spacing: 16) {
                    list(tools).frame(width: 240)
                    explainer(tools)
                }
                .padding(.horizontal, 26).padding(.top, 18).padding(.bottom, 26)
            } else {
                VStack(spacing: 14) {
                    explainer(tools)
                    list(tools)
                }
                .padding(20)
            }
        }
    }

    private func list(_ tools: [ToolInfo]) -> some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 0) {
                    ForEach(Array(tools.enumerated()), id: \.element.id) { i, t in
                        Button { index = i } label: {
                            HStack(spacing: 10) {
                                PhIcon(t.icon, Palette.navy, 16).frame(width: 28, height: 28).background(RoundedRectangle(cornerRadius: 8).fill(Palette.background))
                                Text(t.name).font(Fonts.sans(13, .bold)).foregroundColor(i == index ? Palette.navy : Palette.textPrimary).lineLimit(1)
                                Spacer(minLength: 0)
                            }
                            .padding(.horizontal, 10).padding(.vertical, 8)
                            .background(RoundedRectangle(cornerRadius: 11).fill(i == index ? Palette.navySoft : Color.clear))
                        }
                        .id(i)
                    }
                }
                .padding(8)
            }
            .background(RoundedRectangle(cornerRadius: 18).fill(Color.white))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Palette.divider, lineWidth: 1))
            .onAppear { proxy.scrollTo(max(0, index - 2), anchor: .top) }
        }
    }

    private func explainer(_ tools: [ToolInfo]) -> some View {
        let t = tools[index]
        let inBar = t.essential || (!t.tourOnly && app.toolVisible(t.id))
        return ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                ToolDemo(kind: t.demo).frame(height: 220)
                HStack(spacing: 12) {
                    PhIcon(t.icon, Palette.navy, 22).frame(width: 40, height: 40).background(RoundedRectangle(cornerRadius: 11).fill(Palette.navySoft))
                    Text(t.name).font(Fonts.display(20)).foregroundColor(Palette.textPrimary)
                    Spacer()
                    if !t.tourOnly {
                        Text(inBar ? L("في شريطك", "In your toolbar") : L("غير ظاهرة", "Hidden")).font(Fonts.sans(11, .bold))
                            .foregroundColor(inBar ? Color(hex: 0xFF1F7A50) : Palette.textSecondary)
                            .padding(.horizontal, 9).padding(.vertical, 4)
                            .background(RoundedRectangle(cornerRadius: 9).fill(inBar ? Color(hex: 0xFFE5F4EC) : Color(hex: 0xFFF1F2F5)))
                    }
                }
                .padding(.top, 16)
                Text(t.benefit).font(Fonts.sans(14)).foregroundColor(Palette.textSecondary).lineSpacing(6).padding(.top, 8)
                HStack {
                    arrow(I18n.english ? "‹" : "›", index > 0) { index -= 1 }
                    Spacer()
                    Text(L("\(index + 1) من \(tools.count)", "\(index + 1) of \(tools.count)")).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
                    Spacer()
                    arrow(I18n.english ? "›" : "‹", index < tools.count - 1) { index += 1 }
                }
                .padding(.top, 16)
            }
            .padding(18)
        }
        .background(RoundedRectangle(cornerRadius: 18).fill(Color.white))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Palette.divider, lineWidth: 1))
    }

    private func arrow(_ g: String, _ enabled: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(g).font(.system(size: 20)).foregroundColor(enabled ? Palette.navy : Palette.divider)
                .frame(width: 38, height: 38).overlay(Circle().stroke(Palette.divider, lineWidth: 1))
        }
        .disabled(!enabled)
    }
}

// ---------------------------------------------------------------- الرسوم المتحركة

private let demoYellow = Color(hex: 0xFFFFE066)
private let demoGreen = Color(hex: 0xFF9BE7B4)
private let demoInk = Color(hex: 0xFF1F3A5F)
private let demoRed = Color(hex: 0xFFE5484D)
private let demoLine = Color(hex: 0xFFCBD2DC)

private func ease(_ x: CGFloat) -> CGFloat { let v = min(max(x, 0), 1); return v * v * (3 - 2 * v) }
private func phase(_ t: CGFloat, _ a: CGFloat, _ b: CGFloat) -> CGFloat { ease((t - a) / (b - a)) }

/// رسم متحرك يشرح الأداة، يتكرر كل 3.2 ثانية.
struct ToolDemo: View {
    let kind: ToolDemoKind
    var body: some View {
        TimelineView(.animation) { tl in
            let t = CGFloat(tl.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 3.2) / 3.2)
            Canvas { ctx, size in draw(&ctx, size, t) }
        }
        .background(RoundedRectangle(cornerRadius: 14).fill(Palette.ivory))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private func page(_ ctx: inout GraphicsContext, _ size: CGSize, cx: CGFloat? = nil, scale: CGFloat = 1, fill: Color = .white) -> CGRect {
        let ph = size.height * 0.84 * scale, pw = ph * 0.74
        let r = CGRect(x: (cx ?? size.width / 2) - pw / 2, y: (size.height - ph) / 2, width: pw, height: ph)
        ctx.fill(Path(roundedRect: r, cornerRadius: 6), with: .color(fill))
        ctx.stroke(Path(roundedRect: r, cornerRadius: 6), with: .color(demoLine.opacity(0.6)), lineWidth: 1.5)
        return r
    }

    @discardableResult
    private func lines(_ ctx: inout GraphicsContext, _ f: CGRect, n: Int = 6, color: Color = demoLine) -> [(CGPoint, CGFloat)] {
        var out: [(CGPoint, CGFloat)] = []
        let lh = f.height / CGFloat(n + 2)
        for i in 0..<n {
            let y = f.minY + lh * (CGFloat(i) + 1.2)
            let w = f.width * (i % 3 == 2 ? 0.55 : 0.82)
            let x = f.minX + f.width * 0.09
            ctx.fill(Path(roundedRect: CGRect(x: x, y: y, width: w, height: lh * 0.32), cornerRadius: lh * 0.16), with: .color(color))
            out.append((CGPoint(x: x, y: y), w))
        }
        return out
    }

    private func wave(_ x0: CGFloat, _ x1: CGFloat, _ y: CGFloat, _ amp: CGFloat, upto: CGFloat) -> Path {
        var p = Path()
        let n = 60, k = max(1, Int(CGFloat(n) * upto))
        for i in 0...k {
            let f = CGFloat(i) / CGFloat(n)
            let pt = CGPoint(x: x0 + (x1 - x0) * f, y: y + amp * sin(f * 4 * .pi))
            if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        return p
    }

    private func hl(_ ctx: inout GraphicsContext, _ r: CGRect, _ c: Color) {
        var g = ctx
        g.blendMode = .multiply
        g.fill(Path(roundedRect: r, cornerRadius: r.height * 0.32), with: .color(c))
    }

    private func draw(_ ctx: inout GraphicsContext, _ size: CGSize, _ t: CGFloat) {
        switch kind {
        case .pen:
            let f = page(&ctx, size); lines(&ctx, f, n: 3)
            let prog = phase(t, 0.05, 0.75), y = f.minY + f.height * 0.72
            ctx.stroke(wave(f.minX + f.width * 0.1, f.maxX - f.width * 0.1, y, f.height * 0.06, upto: prog), with: .color(demoInk), style: StrokeStyle(lineWidth: 4.5, lineCap: .round))
            let x = f.minX + f.width * (0.1 + 0.8 * prog), yy = y + f.height * 0.06 * sin(prog * 4 * .pi)
            var pen = Path(); pen.move(to: CGPoint(x: x, y: yy)); pen.addLine(to: CGPoint(x: x + 26, y: yy - 34))
            ctx.stroke(pen, with: .color(Palette.gold), style: StrokeStyle(lineWidth: 7, lineCap: .round))
        case .highlight, .snap, .noSummary, .summary:
            let cx = kind == .summary || kind == .noSummary ? size.width * 0.32 : nil
            let f = page(&ctx, size, cx: cx)
            let ls = lines(&ctx, f)
            let lh = f.height / 8, hh = lh * 0.62
            let (o, w) = ls[kind == .snap ? 3 : 2]
            let prog = phase(t, 0.1, 0.6)
            if kind == .snap {
                let drawP = phase(t, 0.05, 0.45), snap = phase(t, 0.5, 0.68)
                if snap < 1 {
                    var g = ctx; g.blendMode = .multiply
                    g.stroke(wave(o.x, o.x + w, o.y + hh * 0.15, hh * 0.35, upto: drawP), with: .color(demoYellow.opacity(0.9 * (1 - snap))), style: StrokeStyle(lineWidth: hh * 0.9, lineCap: .round))
                }
                if snap > 0 { hl(&ctx, CGRect(x: o.x - 4, y: o.y - hh * 0.32, width: w + 8, height: hh), demoYellow.opacity(snap)) }
            } else {
                hl(&ctx, CGRect(x: o.x - 4, y: o.y - hh * 0.32, width: (w + 8) * prog, height: hh), kind == .noSummary ? demoGreen : demoYellow)
            }
            if kind == .summary || kind == .noSummary {
                let d = CGRect(x: size.width * 0.58, y: size.height * 0.12, width: size.width * 0.36, height: size.height * 0.76)
                ctx.fill(Path(roundedRect: d, cornerRadius: 10), with: .color(Palette.drawerBg))
                ctx.stroke(Path(roundedRect: d, cornerRadius: 10), with: .color(demoLine), lineWidth: 1)
                let fly = phase(t, 0.55, 0.85)
                if kind == .summary && fly > 0 {
                    let target = CGRect(x: d.minX + 12, y: d.minY + 18, width: d.width * 0.8, height: 10)
                    let from = CGRect(x: o.x, y: o.y, width: w, height: 10)
                    let r = CGRect(x: from.minX + (target.minX - from.minX) * fly, y: from.minY + (target.minY - from.minY) * fly,
                                   width: from.width + (target.width - from.width) * fly, height: 10)
                    ctx.fill(Path(roundedRect: r, cornerRadius: 5), with: .color(demoYellow))
                }
                if kind == .noSummary {
                    ctx.draw(Text("✕").font(.system(size: 28, weight: .bold)).foregroundColor(demoRed.opacity(phase(t, 0.6, 0.75))), at: CGPoint(x: d.midX, y: d.midY))
                }
            }
        case .eraser:
            let f = page(&ctx, size); lines(&ctx, f, n: 3)
            let y = f.minY + f.height * 0.7
            let er = phase(t, 0.35, 0.8)
            ctx.stroke(wave(f.minX + f.width * 0.1 + f.width * 0.8 * er, f.maxX - f.width * 0.1, y, f.height * 0.05, upto: 1), with: .color(demoInk), style: StrokeStyle(lineWidth: 4.5, lineCap: .round))
            let ex = f.minX + f.width * (0.1 + 0.8 * er)
            ctx.fill(Path(roundedRect: CGRect(x: ex - 14, y: y - 18, width: 28, height: 36), cornerRadius: 6), with: .color(Color(hex: 0xFFF4A6B6)))
        case .laser:
            let f = page(&ctx, size); lines(&ctx, f)
            let a = t * 2 * .pi
            let p = CGPoint(x: f.midX + cos(a) * f.width * 0.3, y: f.midY + sin(a * 2) * f.height * 0.2)
            ctx.fill(Path(ellipseIn: CGRect(x: p.x - 16, y: p.y - 16, width: 32, height: 32)), with: .color(demoRed.opacity(0.25)))
            ctx.fill(Path(ellipseIn: CGRect(x: p.x - 7, y: p.y - 7, width: 14, height: 14)), with: .color(demoRed))
        case .shapes:
            let f = page(&ctx, size)
            let snap = phase(t, 0.45, 0.6)
            let r = CGRect(x: f.minX + f.width * 0.2, y: f.minY + f.height * 0.25, width: f.width * 0.6, height: f.height * 0.35)
            if snap < 1 {
                var p = Path()
                for i in 0...40 { let a = CGFloat(i) / 40 * 2 * .pi; let pt = CGPoint(x: r.midX + cos(a) * r.width / 2 * (1 + 0.08 * sin(a * 5)), y: r.midY + sin(a) * r.height / 2 * (1 + 0.06 * cos(a * 3))); if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) } }
                ctx.stroke(p.trimmedPath(from: 0, to: phase(t, 0, 0.4)), with: .color(demoInk.opacity(1 - snap)), lineWidth: 4)
            }
            if snap > 0 { ctx.stroke(Path(ellipseIn: r), with: .color(demoInk.opacity(snap)), lineWidth: 4) }
        case .nav:
            let f = page(&ctx, size, cx: size.width / 2 - CGFloat(Int(t * 3)) * 0)
            lines(&ctx, f)
            let n = Int(t * 3) + 1
            ctx.draw(Text("\(n) / 3").font(Fonts.sans(16, .bold)).foregroundColor(Palette.navy), at: CGPoint(x: f.midX, y: f.maxY - 18))
        case .bookmark:
            let f = page(&ctx, size); lines(&ctx, f)
            let drop = phase(t, 0.2, 0.5)
            ctx.fill(Path(CGRect(x: f.maxX - 34, y: f.minY, width: 18, height: 44 * drop)), with: .color(Palette.gold))
        case .timer:
            let c = CGPoint(x: size.width / 2, y: size.height / 2), r = size.height * 0.32
            ctx.stroke(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)), with: .color(demoLine), lineWidth: 8)
            var arc = Path(); arc.addArc(center: c, radius: r, startAngle: .degrees(-90), endAngle: .degrees(-90 + 360 * Double(t)), clockwise: false)
            ctx.stroke(arc, with: .color(Palette.gold), style: StrokeStyle(lineWidth: 8, lineCap: .round))
            ctx.draw(Text(String(format: "%02d:%02d", 24 - Int(t * 24), 60 - Int(t * 59) % 60)).font(Fonts.sans(22, .bold)).foregroundColor(Palette.navy), at: c)
        case .image:
            let f = page(&ctx, size); lines(&ctx, f, n: 3)
            let s = 0.6 + 0.4 * phase(t, 0.2, 0.6)
            let r = CGRect(x: f.midX - f.width * 0.3 * s, y: f.minY + f.height * 0.5, width: f.width * 0.6 * s, height: f.height * 0.3 * s)
            ctx.fill(Path(roundedRect: r, cornerRadius: 6), with: .linearGradient(Gradient(colors: [Color(hex: 0xFF8EC5FC), Color(hex: 0xFFE0C3FC)]), startPoint: r.origin, endPoint: CGPoint(x: r.maxX, y: r.maxY)))
            ctx.stroke(Path(r.insetBy(dx: -3, dy: -3)), with: .color(Palette.navy), style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
        case .text:
            let f = page(&ctx, size); lines(&ctx, f, n: 3)
            let s = "Aa ما تكتبه"
            let n = Int(CGFloat(s.count) * phase(t, 0.1, 0.7))
            ctx.draw(Text(String(s.prefix(n))).font(Fonts.sans(18, .bold)).foregroundColor(demoInk), at: CGPoint(x: f.midX, y: f.minY + f.height * 0.7))
        case .addPage:
            let slide = phase(t, 0.3, 0.7)
            _ = page(&ctx, size, cx: size.width / 2 - size.width * 0.18 * slide, scale: 0.9)
            if slide > 0 {
                let f2 = page(&ctx, size, cx: size.width / 2 + size.width * 0.18 * slide, scale: 0.9)
                var g = ctx; g.opacity = slide
                for i in 0..<6 { let y = f2.minY + f2.height * (0.15 + CGFloat(i) * 0.13); var l = Path(); l.move(to: CGPoint(x: f2.minX + 8, y: y)); l.addLine(to: CGPoint(x: f2.maxX - 8, y: y)); g.stroke(l, with: .color(demoLine), lineWidth: 1) }
            }
        case .ocr:
            let f = page(&ctx, size); lines(&ctx, f, color: demoInk.opacity(0.35))
            let y = f.minY + f.height * phase(t, 0.05, 0.9)
            var l = Path(); l.move(to: CGPoint(x: f.minX, y: y)); l.addLine(to: CGPoint(x: f.maxX, y: y))
            ctx.stroke(l, with: .color(Palette.gold), lineWidth: 3)
        case .search:
            let f = page(&ctx, size); let ls = lines(&ctx, f)
            let i = min(5, Int(t * 3) * 2)
            hl(&ctx, CGRect(x: ls[i].0.x, y: ls[i].0.y - 4, width: ls[i].1 * 0.35, height: 14), Color(hex: 0xFFFFAB40))
        case .export:
            let f = page(&ctx, size); lines(&ctx, f)
            let up = phase(t, 0.3, 0.8)
            var a = Path(); a.move(to: CGPoint(x: f.midX, y: f.midY + 20 - 30 * up)); a.addLine(to: CGPoint(x: f.midX, y: f.midY - 30 - 30 * up))
            a.move(to: CGPoint(x: f.midX - 12, y: f.midY - 18 - 30 * up)); a.addLine(to: CGPoint(x: f.midX, y: f.midY - 30 - 30 * up)); a.addLine(to: CGPoint(x: f.midX + 12, y: f.midY - 18 - 30 * up))
            ctx.stroke(a, with: .color(Palette.navy.opacity(1 - up * 0.5)), style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round))
        case .undo:
            let f = page(&ctx, size); lines(&ctx, f, n: 3)
            let gone = phase(t, 0.45, 0.6), back = phase(t, 0.75, 0.9)
            ctx.stroke(wave(f.minX + f.width * 0.1, f.maxX - f.width * 0.1, f.minY + f.height * 0.7, f.height * 0.05, upto: 1), with: .color(demoInk.opacity(1 - gone + back)), style: StrokeStyle(lineWidth: 4.5, lineCap: .round))
        case .zoom, .fit:
            let s = kind == .zoom ? 1 + 0.5 * sin(t * .pi) : 1.3 - 0.3 * phase(t, 0.3, 0.6)
            let f = page(&ctx, size, scale: s); lines(&ctx, f)
        case .focus:
            let f = page(&ctx, size); lines(&ctx, f)
            let hide = phase(t, 0.3, 0.55)
            ctx.fill(Path(roundedRect: CGRect(x: size.width * 0.2, y: 8 - 40 * hide, width: size.width * 0.6, height: 22), cornerRadius: 11), with: .color(Color.white))
            ctx.stroke(Path(roundedRect: CGRect(x: size.width * 0.2, y: 8 - 40 * hide, width: size.width * 0.6, height: 22), cornerRadius: 11), with: .color(demoLine), lineWidth: 1)
        case .cards:
            let flip = phase(t, 0.3, 0.6)
            let w = size.height * 0.9 * abs(cos(flip * .pi)), h = size.height * 0.6
            let r = CGRect(x: size.width / 2 - w / 2, y: size.height / 2 - h / 2, width: w, height: h)
            ctx.fill(Path(roundedRect: r, cornerRadius: 12), with: .color(flip > 0.5 ? Palette.ivory : .white))
            ctx.stroke(Path(roundedRect: r, cornerRadius: 12), with: .color(flip > 0.5 ? Palette.gold : demoLine), lineWidth: 1.5)
            if w > 40 { ctx.draw(Text(flip > 0.5 ? "Azithromycin" : L("دواء من الماكروليدات؟", "A macrolide?")).font(Fonts.sans(15, .bold)).foregroundColor(Palette.navy), at: CGPoint(x: r.midX, y: r.midY)) }
        case .lasso:
            let f = page(&ctx, size); lines(&ctx, f, n: 3)
            let y = f.minY + f.height * 0.68, dx = f.width * 0.15 * phase(t, 0.6, 0.85)
            ctx.stroke(wave(f.minX + f.width * 0.2 + dx, f.minX + f.width * 0.65 + dx, y, f.height * 0.04, upto: 1), with: .color(demoInk), style: StrokeStyle(lineWidth: 4, lineCap: .round))
            var loop = Path(); loop.addEllipse(in: CGRect(x: f.minX + f.width * 0.13 + dx, y: y - f.height * 0.1, width: f.width * 0.6, height: f.height * 0.2))
            ctx.stroke(loop.trimmedPath(from: 0, to: phase(t, 0.05, 0.5)), with: .color(Palette.navy), style: StrokeStyle(lineWidth: 1.5, dash: [6, 5]))
        case .sticky:
            let f = page(&ctx, size); lines(&ctx, f)
            let drop = phase(t, 0.2, 0.45)
            let r = CGRect(x: f.maxX - f.width * 0.5, y: f.minY + f.height * 0.5 - 20 * (1 - drop), width: f.width * 0.4, height: f.width * 0.4)
            var g = ctx; g.opacity = drop
            g.fill(Path(roundedRect: r, cornerRadius: 4), with: .color(Color(hex: 0xFFFFF59D)))
            g.stroke(wave(r.minX + 8, r.maxX - 8, r.midY, 4, upto: phase(t, 0.5, 0.85)), with: .color(demoInk), lineWidth: 2.5)
        case .zoomWin:
            let f = page(&ctx, size, cx: size.width * 0.3, scale: 0.9); lines(&ctx, f, n: 3)
            let box = CGRect(x: f.minX + f.width * 0.2, y: f.minY + f.height * 0.62, width: f.width * 0.6, height: f.height * 0.12)
            ctx.stroke(Path(box), with: .color(Palette.gold), lineWidth: 1.5)
            let win = CGRect(x: size.width * 0.55, y: size.height * 0.3, width: size.width * 0.4, height: size.height * 0.4)
            ctx.fill(Path(roundedRect: win, cornerRadius: 10), with: .color(.white))
            ctx.stroke(Path(roundedRect: win, cornerRadius: 10), with: .color(Palette.gold), lineWidth: 1.5)
            let p = phase(t, 0.1, 0.8)
            ctx.stroke(wave(win.minX + 12, win.maxX - 12, win.midY, 10, upto: p), with: .color(demoInk), style: StrokeStyle(lineWidth: 4, lineCap: .round))
            ctx.stroke(wave(box.minX + 4, box.maxX - 4, box.midY, 2.5, upto: p), with: .color(demoInk), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
        case .night:
            let dark = phase(t, 0.3, 0.5)
            let f = page(&ctx, size, fill: dark > 0.5 ? Color(hex: 0xFF1E232B) : .white)
            lines(&ctx, f, color: dark > 0.5 ? Color.white.opacity(0.55) : demoLine)
        }
    }
}
