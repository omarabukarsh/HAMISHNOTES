package com.mynotes.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** نوع الرسم المتحرك الذي يشرح الأداة. */
enum class Demo {
    PEN, HIGHLIGHT, ERASER, LASER, SHAPES, SNAP, NO_SUMMARY, NAV, BOOKMARK, TIMER, IMAGE, TEXT,
    ADD_PAGE, OCR, SEARCH, EXPORT, UNDO, ZOOM, FIT, FOCUS, CARDS, SUMMARY, LASSO, STICKY, ZOOMWIN, NIGHT
}

/** أداة في الشريط: id للإخفاء والإظهار، essential = لا تُخفى. */
data class ToolInfo(
    val id: String,
    val name: String,
    val benefit: String,
    val icon: Int,
    val demo: Demo,
    val essential: Boolean = false,
    /** ميزة تُشرح في الجولة فقط (ليست زرًا في الشريط). */
    val tourOnly: Boolean = false
)

object ToolCatalog {
    val all get() = listOf(
        ToolInfo("pen", L("الأقلام", "Pens"), L("اكتب وارسم بقلم جاف أو حبر أو رصاص، بأي لون وحجم. الضغط على القلم يغيّر سماكة الخط.", "Write and draw with a ballpoint, fountain pen or pencil in any color and size. Pen pressure changes the line width."), R.drawable.ph_pen_nib, Demo.PEN, essential = true),
        ToolInfo("highlighter", L("الهايلايتر", "Highlighter"), L("ظلّل الجمل المهمة. بحواف دائرية ناعمة ويظهر خلف النص فيبقى النص واضحًا.", "Highlight important sentences. Soft rounded edges, drawn behind the text so it stays clear."), R.drawable.ph_highlighter, Demo.HIGHLIGHT, essential = true),
        ToolInfo("eraser", L("الممحاة", "Eraser"), L("تمسح الكتابة أو الكلمات المظللة التي تلمسها فقط، والباقي يبقى كما هو.", "Erases only the writing or highlighted words it touches; everything else stays."), R.drawable.ph_eraser, Demo.ERASER, essential = true),
        ToolInfo("laser", L("مؤشر الليزر", "Laser pointer"), L("للشرح أمام غيرك: نقطة متوهجة أو خط يختفي وحده دون أن يُحفظ.", "For presenting: a glowing dot or a line that fades by itself and is never saved."), R.drawable.ph_laser, Demo.LASER, essential = true),
        ToolInfo("snap", L("التقاط النص", "Text snap"), L("مرّر الهايلايتر على السطر فيلتصق التمييز بالكلمات بدقة ويُضاف نصها إلى الملخص.", "Run the highlighter over a line and it snaps precisely to the words and adds their text to the summary."), R.drawable.ph_snap, Demo.SNAP),
        ToolInfo("nosummary", L("تمييز بدون ملخص", "Highlight without summary"), L("خيار في الهايلايتر: ظلّل للتذكير على الصفحة فقط دون أن يدخل النص في الملخص.", "A highlighter option: highlight on the page as a reminder without adding the text to the summary."), R.drawable.ph_highlighter, Demo.NO_SUMMARY, tourOnly = true),
        ToolInfo("shapes", L("الأشكال", "Shapes"), L("ارسم خطًا أو سهمًا أو مستطيلًا أو دائرة باليد فيصبح شكلًا مرتبًا. يُفعّل من إعدادات القلم.", "Draw a line, arrow, rectangle or circle by hand and it becomes a neat shape. Enable it in pen settings."), R.drawable.ph_shapes, Demo.SHAPES, tourOnly = true),
        ToolInfo("nav", L("التنقل بين الصفحات", "Page navigation"), L("السهمان ورقم الصفحة: اضغط الرقم لتنتقل مباشرة إلى أي صفحة.", "Arrows and page number: tap the number to jump to any page."), R.drawable.ph_pages, Demo.NAV),
        ToolInfo("bookmark", L("العلامة المرجعية", "Bookmark"), L("علّم الصفحات المهمة بشريط ذهبي، وتجدها في «العلامات» داخل درج الصفحات.", "Mark important pages with a gold ribbon; find them under “Bookmarks” in the pages drawer."), R.drawable.ph_bookmark, Demo.BOOKMARK),
        ToolInfo("timer", L("مؤقت الدراسة", "Study timer"), L("جلسة دراسة ثم راحة بالمدة التي تختارها في الإعدادات. أوقفه مؤقتًا أو أنهه متى شئت، ووقته يُحسب في إحصاءاتك.", "A study session then a break, with durations you choose in Settings. Pause or stop it anytime; its time counts in your stats."), R.drawable.ph_timer, Demo.TIMER),
        ToolInfo("image", L("إضافة صورة", "Add image"), L("أضف صورة فوق الصفحة، ثم حرّكها وغيّر حجمها.", "Place an image on the page, then move and resize it."), R.drawable.ph_image, Demo.IMAGE),
        ToolInfo("lasso", L("التحديد الحر", "Lasso"), L("أحِط بالقلم حول كتابتك لتحديدها، ثم اسحبها أو كبّرها بالمقبض أو غيّر لونها أو انسخها أو حوّلها إلى نص مكتوب أو احذفها.", "Loop around your writing to select it, then drag it, resize with the handle, recolor, duplicate, convert to typed text or delete."), R.drawable.ph_lasso, Demo.LASSO),
        ToolInfo("shapetool", L("أداة الأشكال", "Shapes tool"), L("ارسم مستطيلًا أو دائرة أو مثلثًا أو معينًا أو خطًا أو سهمًا أو موصلًا بالسحب، مع نمط الخط وسماكته ورؤوس الأسهم.", "Drag to draw rectangles, circles, triangles, diamonds, lines, arrows or connectors, with line style, thickness and arrowheads."), R.drawable.ph_shapes, Demo.SHAPES),
        ToolInfo("sticky", L("ملاحظة لاصقة", "Sticky note"), L("اختر لونًا وانقر على الصفحة لتضع ملاحظة تكتب عليها بالقلم؛ اسحبها لتحريكها، ومن زاويتها لتكبيرها، واطوها لتصبح مربعًا صغيرًا.", "Pick a color and tap the page to place a note you write on with the pen; drag to move, drag its corner to resize, and collapse it to a small square."), R.drawable.ph_note, Demo.STICKY),
        ToolInfo("link", L("رابط إلى صفحة", "Page link"), L("ضع على الصفحة رابطًا إلى صفحة أخرى أو مستند آخر (مثل «جدول المضادات ص 12»)؛ انقر عليه مرتين لتنتقل، ويظهر زر للرجوع.", "Place a link to another page or document (e.g. “antibiotics table p. 12”); tap it twice to jump, with a Back button."), R.drawable.ph_link, Demo.NAV),
        ToolInfo("record", L("تسجيل المحاضرة", "Lecture recording"), L("سجّل الصوت أثناء الكتابة؛ كل خط يُربط بلحظته، فتحدد الكتابة لاحقًا وتسمع ما قيل وقتها.", "Record audio while writing; every stroke is linked to its moment, so you can lasso writing later and hear what was said."), R.drawable.ph_mic, Demo.NAV),
        ToolInfo("split", L("مستندان جنبًا إلى جنب", "Split view"), L("اعرض مستندًا آخر بجانب الحالي (مثل الكتاب والملخص) واسحب الفاصل لتغيير العرض.", "Show another document next to this one (e.g. book and notes) and drag the divider to resize."), R.drawable.ph_split, Demo.NAV),
        ToolInfo("zoomwin", L("نافذة الكتابة المكبّرة", "Zoom writing window"), L("اكتب بخط كبير في نافذة أسفل الشاشة فيظهر صغيرًا ومرتبًا على الصفحة. انقر الصفحة بإصبعك لنقل المنطقة.", "Write large in a window at the bottom; it appears small and neat on the page. Tap the page with a finger to move the area."), R.drawable.ph_zoom_write, Demo.ZOOMWIN),
        ToolInfo("night", L("القراءة الليلية", "Night reading"), L("صفحات داكنة ونص فاتح للمذاكرة ليلًا، مع بقاء ألوان كتابتك وتظليلك.", "Dark pages with light text for night study; your ink and highlight colors are kept."), R.drawable.ph_moon, Demo.NIGHT),
        ToolInfo("text", L("مربع نص", "Text box"), L("اكتب بلوحة المفاتيح في أي مكان من الصفحة.", "Type with the keyboard anywhere on the page."), R.drawable.ph_text, Demo.TEXT),
        ToolInfo("addpage", L("إضافة صفحات", "Add pages"), L("أضف صفحة فارغة (مسطرة أو مربعات) أو صفحات من ملف PDF آخر.", "Add a blank page (lined or grid) or pages from another PDF."), R.drawable.ph_add_page, Demo.ADD_PAGE),
        ToolInfo("ocr", L("قراءة النص", "Read text"), L("يقرأ نص الصفحات (العربية مدعومة في ملفات PDF الرقمية) ليعمل البحث والتقاط النص.", "Reads page text (Arabic supported in digital PDFs) so search and text snap work."), R.drawable.ph_scan, Demo.OCR),
        ToolInfo("ai", L("✦ الذكاء الاصطناعي", "✦ AI"), L("يفتح تبويب «ذكاء» في الملخصات: ترتيب الملخص، إكماله، قراءة الصفحة، وبطاقات المراجعة.", "Opens the AI tab in summaries: organize and continue the summary, read the page, and review cards."), R.drawable.ph_summary, Demo.SUMMARY),
        ToolInfo("search", L("البحث", "Search"), L("ابحث عن كلمة داخل المستند وتنقّل بين النتائج.", "Find a word in the document and move between results."), R.drawable.ph_search, Demo.SEARCH),
        ToolInfo("export", L("الحفظ PDF", "Save as PDF"), L("احفظ المستند مع كتابتك وتظليلك، أو احفظ الملخص وحده كملف PDF.", "Save the document with your writing and highlights, or save the summary alone as a PDF."), R.drawable.ph_export, Demo.EXPORT),
        ToolInfo("undo", L("التراجع والإعادة", "Undo and redo"), L("تراجع عن آخر تعديل أو أعده. أو انقر الصفحة بإصبعين للتراجع وبثلاثة للإعادة.", "Undo or redo the last change. Or tap the page with two fingers to undo and three to redo."), R.drawable.ph_undo, Demo.UNDO),
        ToolInfo("zoom", L("التكبير والتصغير", "Zoom"), L("كبّر الصفحة لقراءة التفاصيل أو صغّرها لرؤية أكثر. يعمل أيضًا بإصبعين.", "Zoom in for details or out to see more. Also works with two fingers."), R.drawable.ph_zoom_in, Demo.ZOOM),
        ToolInfo("fit", L("ملاءمة الصفحة", "Fit page"), L("اعرض الصفحة كاملة على الشاشة، ونقرة ثانية تعيد ملء العرض.", "Show the whole page on screen; tap again to fill the width."), R.drawable.ph_fit, Demo.FIT),
        ToolInfo("focus", L("وضع التركيز", "Focus mode"), L("يخفي كل الأشرطة لتبقى مع الصفحة فقط، والمؤقت يبقى معك.", "Hides all bars so only the page remains; the timer stays with you."), R.drawable.ph_eye, Demo.FOCUS),
        ToolInfo("readtimer", L("عداد وقت القراءة", "Reading time counter"), L("يظهر في الزاوية كم قضيت في قراءة هذا المستند.", "Shows in the corner how long you have spent reading this document."), R.drawable.ph_timer, Demo.TIMER),
        ToolInfo("summary", L("الملخصات", "Summaries"), L("كل ما تلتقطه بالهايلايتر يُجمع مرتبًا في درج «ملخصات»: عناوين وفقرات وجداول تعدّلها بنفسك.", "Everything you snap with the highlighter is collected in the “Summaries” drawer: headings, paragraphs and tables you can edit."), R.drawable.ph_summary, Demo.SUMMARY, tourOnly = true),
        ToolInfo("presets", L("أقلامي المفضلة", "Favorite pens"), L("في شريط الأقلام: النجمة تحفظ القلم الحالي بلونه وحجمه، ونقرة واحدة تعيده. اضغط مطولًا لحذفه.", "In the pen bar: the star saves the current pen with its color and size; one tap brings it back. Long-press to remove."), R.drawable.ph_star_plus, Demo.PEN, tourOnly = true),
        ToolInfo("outline", L("فهرس المحاضرة", "Lecture contents"), L("إن كان في ملف PDF فهرس عناوين يظهر في درج الصفحات تحت «الفهرس»، والنقر ينقلك للفصل.", "If the PDF has a table of contents it appears under “Contents” in the pages drawer; tap to jump."), R.drawable.ph_list, Demo.NAV, tourOnly = true),
        ToolInfo("pages", L("ترتيب الصفحات", "Arrange pages"), L("اضغط مطولًا على صفحة في درج الصفحات لتحريكها أو نسخها أو حذفها.", "Long-press a page in the pages drawer to move, duplicate or delete it."), R.drawable.ph_pages, Demo.ADD_PAGE, tourOnly = true),
        ToolInfo("translate", L("الترجمة", "Translation"), L("حدد نصًا بالضغط المطول واختر «ترجمة»: إنجليزي ↔ عربي على الجهاز.", "Long-press to select text and choose “Translate”: English ↔ Arabic on the device."), R.drawable.ph_translate, Demo.TEXT, tourOnly = true),
        ToolInfo("exam", L("موعد الامتحان", "Exam countdown"), L("اضغط مطولًا على المادة واختر «موعد الامتحان»: ترى الأيام المتبقية وكم صفحة تحتاج يوميًا.", "Long-press a subject and choose “Exam date”: see days left and pages needed per day."), R.drawable.ph_timer, Demo.TIMER, tourOnly = true),
        ToolInfo("cards", L("البطاقات", "Cards"), L("اصنع بطاقة بعنوان ومحتوى تكتبهما بنفسك، واقلبها للمراجعة. من «بطاقات» في درج الملخصات.", "Make cards with your own title and content and flip them to review. From “Cards” in the summaries drawer."), R.drawable.ph_cards, Demo.CARDS, tourOnly = true)
    )
    val tour get() = all
    val essentials get() = all.filter { it.essential }
    val optional get() = all.filter { !it.essential && !it.tourOnly }
}

// ---------------------------------------------------------------- نافذة الإعدادات

/** أقسام الإعدادات كما تظهر في القائمة الجانبية. */
private enum class SettingsPage { AI, GENERAL, PEN, HIGHLIGHTS, STUDY, APPEARANCE, PRIVACY, ABOUT }

private fun SettingsPage.title(): String = when (this) {
    SettingsPage.AI -> L("✦ الذكاء الاصطناعي", "✦ AI")
    SettingsPage.GENERAL -> L("عام", "General")
    SettingsPage.PEN -> L("القلم والإيماءات", "Pen & gestures")
    SettingsPage.HIGHLIGHTS -> L("التمييز والملخص", "Highlights & summary")
    SettingsPage.STUDY -> L("المذاكرة", "Study")
    SettingsPage.APPEARANCE -> L("المظهر", "Appearance")
    SettingsPage.PRIVACY -> L("الخصوصية والأمان", "Privacy & security")
    SettingsPage.ABOUT -> L("حول", "About")
}

private fun SettingsPage.lead(): String = when (this) {
    SettingsPage.AI -> L("ذكاء اصطناعي على جهازك دون إنترنت، ومحفزاته التلقائية.", "On-device AI, offline, and its automatic triggers.")
    SettingsPage.GENERAL -> L("اللغة والمستندات وطريقة فتح التطبيق.", "Language, documents and how the app opens.")
    SettingsPage.PEN -> L("قلمك، والكتابة بالقلم أو الإصبع، وشريط الأدوات.", "Your pen, pen or finger writing, and the toolbar.")
    SettingsPage.HIGHLIGHTS -> L("كيف يعمل الهايلايتر والملخصات.", "How the highlighter and summaries work.")
    SettingsPage.STUDY -> L("مؤقت الدراسة والتذكير اليومي.", "Study timer and your daily reminder.")
    SettingsPage.APPEARANCE -> L("اجعل هامش يبدو كما تحب.", "Make Hamish look and feel the way you like.")
    SettingsPage.PRIVACY -> L("قفل المستندات وما يحفظه التطبيق.", "Document lock and what the app stores.")
    SettingsPage.ABOUT -> L("الإصدار والتراخيص والشروط.", "Version, licenses and terms.")
}

private fun SettingsPage.icon(): Int = when (this) {
    SettingsPage.AI -> R.drawable.ph_summary
    SettingsPage.GENERAL -> R.drawable.ph_sliders
    SettingsPage.PEN -> R.drawable.ph_pen
    SettingsPage.HIGHLIGHTS -> R.drawable.ph_highlighter
    SettingsPage.STUDY -> R.drawable.ph_timer
    SettingsPage.APPEARANCE -> R.drawable.ph_palette
    SettingsPage.PRIVACY -> R.drawable.ph_lock
    SettingsPage.ABOUT -> R.drawable.ph_info
}

private fun SettingsPage.color(): Color = when (this) {
    SettingsPage.AI -> Palette.Navy
    SettingsPage.GENERAL -> TileGray
    SettingsPage.PEN -> TileBlue
    SettingsPage.HIGHLIGHTS -> TileGold
    SettingsPage.STUDY -> TileGreen
    SettingsPage.APPEARANCE -> TilePurple
    SettingsPage.PRIVACY -> TileRed
    SettingsPage.ABOUT -> TileSlate
}

private val TileGray = Color(0xFF6B7A90)
private val TileBlue = Color(0xFF2F6FDB)
private val TileGold = Color(0xFFD9A21B)
private val TileGreen = Color(0xFF2E9D6B)
private val TilePurple = Color(0xFF8B5CF6)
private val TileRed = Color(0xFFB0413E)
private val TileSlate = Color(0xFF667085)
private val GroupLine = Color(0xFFF1EEE7)

@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val c = AppController
    var toolbarOpen by remember { mutableStateOf(false) }
    var templateOpen by remember { mutableStateOf(false) }
    var templateVer by remember { mutableStateOf(0) }
    var licensesOpen by remember { mutableStateOf(false) }
    var privacyOpen by remember { mutableStateOf(false) }
    var termsOpen by remember { mutableStateOf(false) }

    OriginDialog(onDismiss = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) { close ->
        Surface(
            Modifier.fillMaxWidth(0.94f).widthIn(max = 880.dp).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(24.dp),
            color = Palette.Background
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // شاشة عريضة: قائمة جانبية ومحتوى. شاشة ضيقة: القائمة أولًا ثم القسم في صفحة كاملة
                val wide = maxWidth >= 600.dp
                var page by remember { mutableStateOf<SettingsPage?>(if (wide) SettingsPage.GENERAL else null) }
                val pane: @Composable (SettingsPage, Modifier) -> Unit = { p, m ->
                    SettingsPane(
                        p, m,
                        onClose = close,
                        onBack = if (wide) null else ({ page = null }),
                        templateVer = templateVer,
                        openToolbar = { toolbarOpen = true },
                        openTemplate = { templateOpen = true },
                        openLicenses = { licensesOpen = true },
                        openPrivacy = { privacyOpen = true },
                        openTerms = { termsOpen = true }
                    )
                }
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        SettingsSidebar(page ?: SettingsPage.GENERAL, Modifier.width(250.dp).fillMaxHeight(), onClose = null) { page = it }
                        Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Divider))
                        pane(page ?: SettingsPage.GENERAL, Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    val p = page
                    if (p == null) SettingsSidebar(null, Modifier.fillMaxSize(), onClose = close) { page = it }
                    else pane(p, Modifier.fillMaxSize())
                }
            }
        }
    }
    if (toolbarOpen) ToolbarCustomizeDialog(onDismiss = { toolbarOpen = false })
    if (licensesOpen) LicensesDialog { licensesOpen = false }
    if (privacyOpen) PrivacyDialog { privacyOpen = false }
    if (termsOpen) TermsDialog { termsOpen = false }
    if (templateOpen) TemplatePickerDialog(
        mode = PickerMode.DEFAULT,
        onDismiss = { templateOpen = false },
        onConfirm = { _, _, _ -> templateOpen = false; templateVer++ }
    )
}

/** القائمة الجانبية: العنوان، بطاقة الحساب المصغّرة، الأقسام، ورقم الإصدار. */
@Composable
private fun SettingsSidebar(selected: SettingsPage?, modifier: Modifier, onClose: (() -> Unit)?, onSelect: (SettingsPage) -> Unit) {
    val c = AppController
    Column(modifier.background(Color.White).padding(horizontal = 12.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                L("الإعدادات", "Settings"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold,
                fontSize = 22.sp, color = Palette.Navy, modifier = Modifier.weight(1f)
            )
            if (onClose != null) CloseButton(onClose)
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SettingsPage.values().forEach { p ->
                val on = p == selected
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(shape)
                        .background(if (on) Palette.NavySoft else Color.Transparent)
                        .clickable { onSelect(p) }.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconTile(p.icon(), p.color(), 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        p.title(), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (on) Palette.Navy else Palette.TextPrimary, modifier = Modifier.weight(1f)
                    )
                    if (selected == null) Chevron()
                    if (on) Box(Modifier.size(width = 3.dp, height = 18.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Gold))
                }
            }
        }
        Text(
            L("هامش", "Hamish") + " " + BuildConfig.VERSION_NAME,
            fontSize = 11.sp, color = Palette.TextSecondary, modifier = Modifier.padding(start = 10.dp, top = 8.dp)
        )
    }
}

/** محتوى قسم واحد: العنوان والوصف ثم مجموعات الإعدادات. */
@Composable
private fun SettingsPane(
    page: SettingsPage,
    modifier: Modifier,
    onClose: () -> Unit,
    onBack: (() -> Unit)?,
    templateVer: Int,
    openToolbar: () -> Unit,
    openTemplate: () -> Unit,
    openLicenses: () -> Unit,
    openPrivacy: () -> Unit,
    openTerms: () -> Unit
) {
    val c = AppController
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(start = if (onBack != null) 8.dp else 24.dp, end = 14.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Text(if (I18n.english) "‹" else "›", fontSize = 28.sp, color = Palette.Navy) }
            }
            Text(
                page.title(), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold,
                fontSize = 24.sp, color = Palette.TextPrimary, modifier = Modifier.weight(1f)
            )
            CloseButton(onClose)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp)
        ) {
            Text(page.lead(), fontSize = 13.sp, color = Palette.TextSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
            when (page) {
                SettingsPage.AI -> {
                    AiSettings()
                    GroupLabel(L("البيانات", "Data"))
                    SettingsGroup {
                        GroupRow(
                            R.drawable.ph_folder, TileGreen,
                            L("أين تُحفظ ملاحظاتك", "Where your notes are stored"),
                            L("على هذا الجهاز فقط", "On this device only")
                        )
                    }
                }
                SettingsPage.GENERAL -> {
                    GroupLabel(L("اللغة", "Language"))
                    SettingsGroup {
                        GroupRow(R.drawable.ph_translate, TileBlue, L("لغة التطبيق", "App language")) {
                            Segmented(listOf("العربية", "English"), if (I18n.english) 1 else 0) { c.setEnglish(it == 1) }
                        }
                    }
                    GroupLabel(L("المستندات", "Documents"))
                    SettingsGroup {
                        SwitchRow(
                            R.drawable.ph_recent, TileGreen,
                            L("المتابعة من حيث توقفت", "Continue where you left off"),
                            L("تُفتح المستندات التي كانت مفتوحة عند تشغيل التطبيق", "Reopen your last documents when the app starts"),
                            c.resumeLast
                        ) { c.toggleResumeLast() }
                        GroupDivider()
                        SwitchRow(
                            R.drawable.ph_pages, TileGray,
                            L("فتح المستندات في تبويبات", "Open documents as tabs"),
                            L("كل مستند في تبويب جديد. أوقفه ليحل الجديد محل المفتوح", "Each document opens in a new tab. Turn off to replace the open one"),
                            c.openAsTabs
                        ) { c.toggleOpenAsTabs() }
                        GroupDivider()
                        val d = templateVer.let { Templates.defaultChoice() }
                        GroupRow(
                            R.drawable.ph_note, TileGold,
                            L("القالب الافتراضي للملاحظات", "Default notebook template"),
                            onClick = openTemplate
                        ) {
                            ValueChevron(Templates.labelOf(d.kind) + " · " + Templates.sizeOf(d.size).label + if (d.landscape) L(" · أفقي", " · landscape") else "")
                        }
                    }
                }
                SettingsPage.PEN -> {
                    // القلم المكتشف، وضع الكتابة بالقلم أو الإصبع، وجرّب قلمك
                    PensSection()
                    GroupLabel(L("القلم", "Pen"))
                    SettingsGroup {
                        SwitchRow(
                            R.drawable.ph_pen_nib, TileBlue,
                            L("النقر المزدوج على القلم", "Pen double-tap"),
                            L("يبدّل بين الممحاة وآخر قلم استخدمته", "Switches between the eraser and your last pen"),
                            c.penDoubleTapEnabled
                        ) { c.togglePenDoubleTap() }
                        GroupDivider()
                        SwitchRow(
                            R.drawable.ph_shape_detect, TilePurple,
                            L("ارسم وأبقِ: تحويل الرسم إلى شكل", "Draw and hold: make a shape"),
                            L("ارسم ثم أبقِ القلم ثابتًا لحظة فيصبح الرسم شكلًا مرتبًا", "Draw, then hold the pen still for a moment to turn it into a neat shape"),
                            PenPrefs.drawAndHold
                        ) { PenPrefs.changeDrawAndHold(!PenPrefs.drawAndHold) }
                        GroupDivider()
                        // تُقرأ كتابتك بهذه اللغة لتلتقطها بالهايلايتر وتبحث فيها
                        GroupRow(
                            R.drawable.ph_text_scan, TileBlue,
                            L("لغة خط يدك", "Handwriting language"),
                            L("تُقرأ كتابتك بها لتلتقطها بالهايلايتر وتبحث فيها", "Your writing is read in it, so you can highlight and search it")
                        ) {
                            val opts = listOf(HwLang.BOTH, HwLang.ARABIC, HwLang.ENGLISH)
                            Segmented(
                                listOf(L("الاثنان", "Both"), "عربي", "English"),
                                opts.indexOf(PenPrefs.hwLang).coerceAtLeast(0)
                            ) { PenPrefs.changeHwLang(opts[it]) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    PenKeyLearnRow()
                    GroupLabel(L("الأصابع", "Fingers"))
                    SettingsGroup {
                        SwitchRow(
                            R.drawable.ph_undo, TileGreen,
                            L("نقرة بإصبعين للتراجع، وبثلاثة للإعادة", "Two-finger tap to undo, three to redo"),
                            L("انقر الصفحة بإصبعين للتراجع، وبثلاثة لإعادة ما تراجعت عنه", "Tap the page with two fingers to undo, with three to redo"),
                            c.fingerUndo
                        ) { c.toggleFingerUndo() }
                    }
                    GroupLabel(L("شريط الأدوات", "Toolbar"))
                    SettingsGroup {
                        GroupRow(
                            R.drawable.ph_sliders, TileGray,
                            L("تخصيص شريط الأدوات", "Toolbar customization"),
                            L("الشكل، والأدوات الظاهرة، واستعادة الافتراضي", "Style, which tools it shows, and restoring the default"),
                            onClick = openToolbar
                        ) { Chevron() }
                    }
                }
                SettingsPage.HIGHLIGHTS -> {
                    GroupLabel(L("الملخصات", "Summaries"))
                    SettingsGroup {
                        SwitchRow(
                            R.drawable.ph_summary, TileGold,
                            L("تفعيل الملخصات", "Enable summaries"),
                            L("أوقفه لإخفاء زر الملخصات وعدم إضافة التمييز إليها", "Turn off to hide the summaries button and stop adding highlights"),
                            UiPrefs.summariesOn
                        ) { UiPrefs.changeSummariesOn(!UiPrefs.summariesOn) }
                        GroupDivider()
                        SwitchRow(
                            R.drawable.ph_text, TileGray,
                            L("ترتيب نص الملخص", "Tidy summary text"),
                            L("تنظيف المسافات وإكمال الجمل المقطوعة بين الأسطر", "Clean spacing and join sentences broken across lines"),
                            c.summaryTidy
                        ) { c.toggleSummaryTidy() }
                    }
                    GroupLabel(L("الهايلايتر", "Highlighter"))
                    SettingsGroup {
                        SwitchRow(
                            R.drawable.ph_snap, TileBlue,
                            L("التقاط النص", "Text snap"),
                            L("يلتصق الهايلايتر بالكلمات ويُضاف نصها إلى الملخص", "The highlighter snaps to words and adds their text to the summary"),
                            c.highlightSnap
                        ) { c.toggleHighlightSnap() }
                        GroupDivider()
                        SwitchRow(
                            R.drawable.ph_highlighter, TileGold,
                            L("إضافة التمييز إلى الملخص", "Add highlights to summary"),
                            L("أوقفه لتظلّل على الصفحة فقط دون إضافة النص", "Turn off to highlight on the page only, without adding text"),
                            c.highlightToSummary
                        ) { c.toggleHighlightToSummary() }
                    }
                }
                SettingsPage.STUDY -> {
                    GroupLabel(L("مؤقت الدراسة", "Study timer"))
                    PomodoroSettings()
                    GroupLabel(L("التذكير اليومي", "Daily reminder"))
                    ReminderRow()
                }
                SettingsPage.APPEARANCE -> {
                    GroupLabel(L("السمة", "Theme"))
                    SettingsGroup {
                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            ThemeCard(L("كحلي", "Navy"), ThemeState.theme == AppTheme.NAVY, AppTheme.NAVY, Modifier.weight(1f)) { c.setTheme(AppTheme.NAVY) }
                            ThemeCard(L("فاتح", "Light"), ThemeState.theme == AppTheme.LIGHT, AppTheme.LIGHT, Modifier.weight(1f)) { c.setTheme(AppTheme.LIGHT) }
                        }
                    }
                    GroupLabel(L("الأحجام", "Sizes"))
                    SettingsGroup {
                        val bars = listOf(0.85f, 1f, 1.15f, 1.3f)
                        GroupRow(R.drawable.ph_sliders, TileGray, L("حجم شريط الأدوات", "Toolbar size")) {
                            Segmented(
                                listOf(L("صغير", "S"), L("عادي", "M"), L("كبير", "L"), L("أكبر", "XL")),
                                bars.indexOfFirst { kotlin.math.abs(UiPrefs.toolbarScale - it) < 0.01f }
                            ) { UiPrefs.changeToolbarScale(bars[it]) }
                        }
                        GroupDivider()
                        val texts = listOf(0.9f, 1f, 1.15f, 1.3f)
                        GroupRow(R.drawable.ph_text, TilePurple, L("حجم الكتابة", "Text size")) {
                            Segmented(
                                listOf(L("أ", "Aa"), L("أ", "Aa"), L("أ", "Aa"), L("أ", "Aa")),
                                texts.indexOfFirst { kotlin.math.abs(UiPrefs.textScale - it) < 0.01f },
                                sizes = listOf(11, 13, 15, 17)
                            ) { UiPrefs.changeTextScale(texts[it]) }
                        }
                    }
                }
                SettingsPage.PRIVACY -> {
                    GroupLabel(L("قفل المستندات", "Document lock"))
                    SettingsGroup {
                        GroupRow(
                            R.drawable.ph_lock, TileRed,
                            L("قفل بالرمز أو البصمة", "Lock with PIN or fingerprint"),
                            L("اقفل مستندًا من قائمته في المكتبة", "Lock a document from its menu in the library"),
                            onClick = if (c.hasPin()) ({ c.setPinFor = "" }) else null
                        ) {
                            if (c.hasPin()) ValueChevron(L("تغيير الرمز", "Change PIN"))
                        }
                    }
                    GroupLabel(L("الخصوصية", "Privacy"))
                    SettingsGroup {
                        GroupRow(
                            R.drawable.ph_eye, TileSlate,
                            L("سياسة الخصوصية", "Privacy policy"),
                            L("ما يحفظه التطبيق وأين", "What the app stores and where"),
                            onClick = openPrivacy
                        ) { Chevron() }
                    }
                }
                SettingsPage.ABOUT -> {
                    SettingsGroup {
                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.logo_app), null, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(L("هامش", "Hamish"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Palette.TextPrimary)
                                Text(L("الإصدار", "Version") + " " + BuildConfig.VERSION_NAME, fontSize = 13.sp, color = Palette.TextSecondary)
                            }
                        }
                    }
                    GroupLabel(L("قانوني", "Legal"))
                    SettingsGroup {
                        GroupRow(
                            R.drawable.ph_list, TileSlate,
                            L("تراخيص المكتبات المفتوحة", "Open-source licenses"),
                            L("المكوّنات المستخدمة وتراخيصها", "Components used and their licenses"),
                            onClick = openLicenses
                        ) { Chevron() }
                        GroupDivider()
                        GroupRow(
                            R.drawable.ph_note, TileGray,
                            L("شروط الاستخدام", "Terms of use"),
                            L("حدود المسؤولية واستخدام الملفات", "Liability and use of your files"),
                            onClick = openTerms
                        ) { Chevron() }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFE8EBF0)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { PhIcon(R.drawable.ph_close, Palette.TextSecondary, 18.dp, L("إغلاق", "Close")) }
}

/** مربع ملوّن بأيقونة بيضاء، كأيقونات إعدادات الأنظمة الحديثة. */
@Composable
private fun IconTile(icon: Int, color: Color, size: androidx.compose.ui.unit.Dp = 32.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(color),
        contentAlignment = Alignment.Center
    ) { PhIcon(icon, Color.White, size * 0.58f) }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Palette.GoldDeep,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp)
    )
}

/** بطاقة بيضاء تجمع عدة صفوف بينها خطوط رفيعة. */
@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, Palette.Divider, shape)) { content() }
}

@Composable
private fun GroupDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 60.dp).height(1.dp).background(GroupLine))
}

@Composable
private fun GroupRow(
    icon: Int, color: Color, title: String, desc: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, color)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
            if (desc != null) Text(desc, fontSize = 12.sp, lineHeight = 17.sp, color = Palette.TextSecondary)
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

@Composable
private fun SwitchRow(icon: Int, color: Color, title: String, desc: String, checked: Boolean, onToggle: () -> Unit) {
    GroupRow(icon, color, title, desc, onClick = onToggle) { NavySwitch(checked) { onToggle() } }
}

@Composable
private fun Chevron() {
    Text(if (I18n.english) "›" else "‹", fontSize = 20.sp, color = Color(0xFFB0B5BF))
}

@Composable
private fun ValueChevron(value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(value, fontSize = 13.sp, color = Palette.TextSecondary, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Chevron()
    }
}

/** أزرار اختيار متلاصقة داخل شريط واحد. */
@Composable
private fun Segmented(options: List<String>, selected: Int, sizes: List<Int>? = null, onSelect: (Int) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(11.dp)).background(Color(0xFFEEF0F4)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.clip(RoundedCornerShape(9.dp))
                    .let { if (on) it.shadow(1.dp, RoundedCornerShape(9.dp)).background(Color.White) else it }
                    .clickable { onSelect(i) }.padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label, fontSize = (sizes?.getOrNull(i) ?: 13).sp, fontWeight = FontWeight.Bold,
                    color = if (on) Palette.Navy else Palette.TextSecondary
                )
            }
        }
    }
}

/** بطاقة سمة بمعاينة مصغّرة للشكل. */
@Composable
private fun ThemeCard(label: String, selected: Boolean, theme: AppTheme, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val navy = theme == AppTheme.NAVY
    Column(
        modifier.clip(shape).border(2.dp, if (selected) Palette.Gold else Palette.Divider, shape)
            .clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(9.dp)).background(if (navy) Color(0xFFF3F5F8) else Color(0xFFFFFDF7))) {
            Box(Modifier.fillMaxHeight().weight(0.28f).background(if (navy) Palette.Navy else Color.White))
            Column(Modifier.weight(0.72f).padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                val line = if (navy) Color(0xFFC9D2E0) else Color(0xFFE6E1D4)
                Box(Modifier.fillMaxWidth(0.7f).height(6.dp).clip(CircleShape).background(if (navy) Palette.Navy else Color(0xFF1E2A44)))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(line))
                Box(Modifier.fillMaxWidth(0.8f).height(6.dp).clip(CircleShape).background(line))
                Box(Modifier.fillMaxWidth(0.45f).height(6.dp).clip(CircleShape).background(Lib.accent))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text((if (selected) "✓ " else "") + label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
    }
}

/** إن لم يعمل النقر المزدوج تلقائيًا: يتعلّم التطبيق الإشارة التي يرسلها القلم. */
@Composable
private fun PenKeyLearnRow() {
    val c = AppController
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, Palette.Divider, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(L("تعليم التطبيق نقرتي القلم", "Teach the app your pen double-tap"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
            Text(
                when {
                    c.penKeyLearning -> L("انقر الآن مرتين على القلم…", "Double-tap the pen now…")
                    c.penKeyCode >= 0 -> L("مربوطة. أعد التعليم إن غيّرت القلم", "Linked. Teach again if you change pens")
                    else -> L("إن لم تعمل النقرتان: اضغط «تعليم» ثم انقر على القلم مرتين", "If double-tap doesn’t work: tap “Teach”, then double-tap the pen")
                },
                fontSize = 12.sp, lineHeight = 18.sp,
                color = if (c.penKeyLearning) Palette.GoldDeep else Palette.TextSecondary
            )
        }
        Spacer(Modifier.width(10.dp))
        ChoiceChip(if (c.penKeyLearning) L("إلغاء", "Cancel") else L("تعليم", "Teach"), c.penKeyLearning) {
            if (c.penKeyLearning) {
                c.learnPenKey(false)
            } else {
                // نافذة الإعدادات تحجب إشارات القلم عن الشاشة الرئيسية، فنغلقها أثناء التعليم
                c.learnPenKey(true)
                c.settingsOpen = false
                c.messages.tryEmit(L("انقر الآن على القلم مرتين…", "Double-tap the pen now…"))
            }
        }
    }
}

/** نافذة «شريط الأدوات»: تبويب للتخصيص (معاينة حية، الشكل، الأدوات) وتبويب للتعرّف على الأدوات. */
@Composable
private fun ToolbarCustomizeDialog(onDismiss: () -> Unit) {
    val c = AppController
    var learn by remember { mutableStateOf(false) }
    var learnIndex by remember { mutableStateOf(0) }
    fun onInfo(id: String) {
        val i = ToolCatalog.tour.indexOfFirst { it.id == id }
        if (i >= 0) { learnIndex = i; learn = true }
    }
    OriginDialog(onDismiss = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) { close ->
        Surface(
            Modifier.fillMaxWidth(0.92f).widthIn(max = 900.dp).fillMaxHeight(0.9f),
            shape = RoundedCornerShape(26.dp),
            color = Palette.Background
        ) {
            Column(Modifier.fillMaxSize()) {
                // الرأس: العنوان، الاستعادة، الإغلاق، والتبويبات
                Column(Modifier.fillMaxWidth().background(Color.White).padding(start = 26.dp, end = 18.dp, top = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                L("شريط الأدوات", "Toolbar"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold,
                                fontSize = 24.sp, color = Palette.TextPrimary
                            )
                            Text(
                                L("اختر شكل شريطك والأدوات التي تظهر فيه.", "Choose how your toolbar looks and which tools it shows."),
                                fontSize = 13.sp, color = Palette.TextSecondary
                            )
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFBF4E2)).clickable { c.resetToolbar() }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) { Text(L("↺ استعادة الافتراضي", "↺ Restore default"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.GoldDeep) }
                        Spacer(Modifier.width(10.dp))
                        CloseButton(close)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                        DialogTab(L("تخصيص", "Customize"), !learn) { learn = false }
                        DialogTab(L("تعرّف على الأدوات", "Learn the tools"), learn) { learn = true }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Divider))
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth >= 640.dp
                    if (learn) {
                        LearnTools(learnIndex, wide) { learnIndex = it }
                    } else {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 26.dp, end = 26.dp, bottom = 28.dp)) {
                            GroupLabel(L("معاينة", "Preview"))
                            ToolbarLivePreview()
                            GroupLabel(L("شكل الشريط", "Toolbar style"))
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                StyleOption(
                                    Modifier.weight(1f), ToolbarStyle.PEN_BAR, L("شريط الأقلام", "Pen bar"),
                                    L("زر قلم واحد؛ الضغط عليه يفتح شريطًا ثانيًا فيه كل الأقلام والهايلايتر والألوان والحجم.", "One pen button. Tapping it opens a second bar with all pens, the highlighter, colors and size.")
                                )
                                StyleOption(
                                    Modifier.weight(1f), ToolbarStyle.CLASSIC, L("الكلاسيكي", "Classic"),
                                    L("القلم والهايلايتر زرّان منفصلان؛ نقرة ثانية على الأداة تفتح إعداداتها.", "Pen and highlighter are separate buttons. Tap a tool again to open its settings.")
                                )
                            }
                            val cols = if (wide) 3 else 2
                            GroupLabel(L("الأدوات الأساسية", "Essential tools"))
                            ToolGrid(ToolCatalog.essentials, cols, ::onInfo, essential = true)
                            GroupLabel(L("أدوات إضافية · اضغط لإظهارها أو إخفائها", "More tools · tap to show or hide"))
                            ToolGrid(ToolCatalog.optional, cols, ::onInfo, essential = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(IntrinsicSize.Max).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = if (selected) Palette.Navy else Palette.TextSecondary, modifier = Modifier.padding(bottom = 10.dp)
        )
        Box(
            Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                .background(if (selected) Palette.Gold else Color.Transparent)
        )
    }
}

/** شريط مصغّر يعرض الأدوات الظاهرة الآن ويتغيّر فورًا مع الاختيار. */
@Composable
private fun ToolbarLivePreview() {
    val c = AppController
    val shown = ToolCatalog.essentials + ToolCatalog.optional.filter { c.toolVisible(it.id) }
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFFE9EDF3), Color(0xFFDDE3EC))))
            .padding(vertical = 20.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.shadow(6.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White)
                .horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            shown.forEachIndexed { i, t ->
                if (i == ToolCatalog.essentials.size) {
                    Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(24.dp).background(Color(0xFFE3E6EC)))
                }
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(if (i == 0) Palette.Navy else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) { PhIcon(t.icon, if (i == 0) Color.White else Palette.Navy, 20.dp) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            L("معاينة حية · ${shown.size} أداة في شريطك", "Live preview · ${shown.size} tools in your toolbar"),
            fontSize = 12.sp, color = Palette.TextSecondary
        )
    }
}

/** شبكة بطاقات الأدوات: الأساسية بشارة «دائمًا ظاهرة»، والإضافية تُظهر وتُخفى بالضغط. */
@Composable
private fun ToolGrid(tools: List<ToolInfo>, cols: Int, onInfo: (String) -> Unit, essential: Boolean) {
    val c = AppController
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tools.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    val on = essential || c.toolVisible(t.id)
                    val shape = RoundedCornerShape(16.dp)
                    Row(
                        Modifier.weight(1f).clip(shape).background(Color.White).border(1.dp, Palette.Divider, shape)
                            .let { if (essential) it else it.clickable { c.setToolVisible(t.id, !on) } }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(if (on) Palette.NavySoft else Color(0xFFF3F4F6)),
                            contentAlignment = Alignment.Center
                        ) { PhIcon(t.icon, if (on) Palette.Navy else Color(0xFF9AA1AD), 20.dp) }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary, lineHeight = 17.sp, maxLines = 2)
                            Text(
                                L("ⓘ كيف تعمل", "ⓘ How it works"), fontSize = 11.sp, color = Palette.TextSecondary,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onInfo(t.id) }.padding(vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        if (essential) {
                            Text(
                                L("دائمًا ظاهرة", "Always on"), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Palette.GoldDeep,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFFBF4E2)).padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        } else {
                            Box(
                                Modifier.size(24.dp).clip(CircleShape)
                                    .then(if (on) Modifier.background(Palette.Navy) else Modifier.border(2.dp, Color(0xFFCDD2DA), CircleShape)),
                                contentAlignment = Alignment.Center
                            ) { if (on) PhIcon(R.drawable.ph_check, Color.White, 14.dp) }
                        }
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** تبويب «تعرّف على الأدوات»: قائمة الأدوات وبجانبها شرح الأداة المختارة. */
@Composable
private fun LearnTools(index: Int, wide: Boolean, onSelect: (Int) -> Unit) {
    val tools = ToolCatalog.tour
    val list: @Composable (Modifier) -> Unit = { m ->
        val shape = RoundedCornerShape(18.dp)
        val state = rememberLazyListState(initialFirstVisibleItemIndex = (index - 2).coerceAtLeast(0))
        LazyColumn(
            m.clip(shape).background(Color.White).border(1.dp, Palette.Divider, shape),
            state = state,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
        ) {
            items(tools.size) { i ->
                val t = tools[i]
                val on = i == index
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                        .background(if (on) Palette.NavySoft else Color.Transparent)
                        .clickable { onSelect(i) }.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Palette.Background),
                        contentAlignment = Alignment.Center
                    ) { PhIcon(t.icon, Palette.Navy, 16.dp) }
                    Spacer(Modifier.width(10.dp))
                    Text(t.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) Palette.Navy else Palette.TextPrimary, maxLines = 1)
                }
            }
        }
    }
    if (wide) {
        Row(Modifier.fillMaxSize().padding(start = 26.dp, end = 26.dp, top = 18.dp, bottom = 26.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            list(Modifier.width(240.dp).fillMaxHeight())
            ToolExplainer(index, Modifier.weight(1f).fillMaxHeight(), onSelect)
        }
    } else {
        Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ToolExplainer(index, Modifier.fillMaxWidth(), onSelect)
            list(Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun ToolExplainer(index: Int, modifier: Modifier, onSelect: (Int) -> Unit) {
    val c = AppController
    val tools = ToolCatalog.tour
    val t = tools[index]
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.clip(shape).background(Color.White).border(1.dp, Palette.Divider, shape).verticalScroll(rememberScrollState()).padding(18.dp)) {
        ToolDemo(t.demo, Modifier.fillMaxWidth().height(220.dp))
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(Palette.NavySoft),
                contentAlignment = Alignment.Center
            ) { PhIcon(t.icon, Palette.Navy, 22.dp) }
            Spacer(Modifier.width(12.dp))
            Text(t.name, fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
            val inBar = t.essential || (!t.tourOnly && c.toolVisible(t.id))
            if (!t.tourOnly) {
                Text(
                    if (inBar) L("في شريطك", "In your toolbar") else L("غير ظاهرة", "Hidden"),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = if (inBar) Color(0xFF1F7A50) else Palette.TextSecondary,
                    modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(if (inBar) Color(0xFFE5F4EC) else Color(0xFFF1F2F5))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(t.benefit, fontSize = 14.sp, lineHeight = 22.sp, color = Palette.TextSecondary)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundArrow(if (I18n.english) "‹" else "›", index > 0) { onSelect(index - 1) }
            Text(
                L("${index + 1} من ${tools.size}", "${index + 1} of ${tools.size}"), fontSize = 12.sp, color = Palette.TextSecondary,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
            )
            RoundArrow(if (I18n.english) "›" else "‹", index < tools.size - 1) { onSelect(index + 1) }
        }
    }
}

@Composable
private fun RoundArrow(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).border(1.dp, Palette.Divider, CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(glyph, fontSize = 20.sp, color = if (enabled) Palette.Navy else Palette.Divider) }
}


/** صف ينقل إلى صفحة إعدادات فرعية. */
@Composable
private fun NavRow(title: String, desc: String, icon: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, Palette.Divider, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PhIcon(icon, Palette.Navy, 22.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
            Text(desc, fontSize = 12.sp, lineHeight = 17.sp, color = Palette.TextSecondary)
        }
        Text("›", fontSize = 22.sp, color = Palette.TextSecondary)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.GoldDeep,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)
    )
}

@Composable
private fun NavySwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Palette.Navy,
            checkedBorderColor = Palette.Navy
        )
    )
}

@Composable
private fun StyleOption(modifier: Modifier, style: ToolbarStyle, title: String, desc: String) {
    val c = AppController
    val sel = c.toolbarStyle == style
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.clip(shape).background(Color.White)
            .border(2.dp, if (sel) Palette.Gold else Palette.Divider, shape)
            .clickable { c.updateToolbarStyle(style) }
    ) {
        Column(Modifier.padding(14.dp)) {
            StylePreview(style)
            Spacer(Modifier.height(12.dp))
            Text(title, fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Palette.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(desc, fontSize = 12.sp, lineHeight = 18.sp, color = Palette.TextSecondary)
        }
        if (sel) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(10.dp).size(22.dp).clip(CircleShape).background(Palette.Gold),
                contentAlignment = Alignment.Center
            ) { PhIcon(R.drawable.ph_check, Color.White, 14.dp) }
        }
    }
}

@Composable
private fun StylePreview(style: ToolbarStyle) {
    val t by rememberInfiniteTransition(label = "style").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "t"
    )
    Canvas(Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Ivory)) {
        val w = size.width
        val h = size.height
        val barH = h * 0.26f
        drawRoundRect(Color.White, Offset(w * 0.08f, h * 0.1f), Size(w * 0.84f, barH), CornerRadius(barH / 2))
        val cols = listOf(Color(0xFF1F3A5F), Color(0xFFD4A62A), Color(0xFF9AA4B2), Color(0xFFE5484D))
        if (style == ToolbarStyle.CLASSIC) {
            for (i in 0 until 6) {
                drawCircle(
                    if (i < 2) cols[i] else Palette.Divider, barH * 0.28f,
                    Offset(w * (0.18f + i * 0.13f), h * 0.1f + barH / 2)
                )
            }
            // قائمة تنسدل من الأداة الأولى
            val open = ((t - 0.25f) * 3f).coerceIn(0f, 1f) * (1f - ((t - 0.8f) * 5f).coerceIn(0f, 1f))
            if (open > 0f) {
                drawRoundRect(
                    Color.White, Offset(w * 0.1f, h * 0.42f), Size(w * 0.3f, h * 0.5f * open),
                    CornerRadius(8f)
                )
            }
        } else {
            for (i in 0 until 5) {
                drawCircle(
                    if (i == 0) cols[0] else Palette.Divider, barH * 0.28f,
                    Offset(w * (0.18f + i * 0.15f), h * 0.1f + barH / 2)
                )
            }
            // الشريط الثاني ينزلق تحت الأول
            val open = ((t - 0.2f) * 3f).coerceIn(0f, 1f) * (1f - ((t - 0.85f) * 6f).coerceIn(0f, 1f))
            if (open > 0f) {
                val y = h * 0.44f + (1f - open) * h * 0.2f
                drawRoundRect(
                    Color.White.copy(alpha = open), Offset(w * 0.14f, y), Size(w * 0.72f, barH),
                    CornerRadius(barH / 2)
                )
                for (i in 0 until 4) {
                    drawCircle(
                        cols[i].copy(alpha = open), barH * 0.26f,
                        Offset(w * (0.24f + i * 0.13f), y + barH / 2)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- جولة الأدوات المتحركة

@OptIn(ExperimentalFoundationApi::class)
private val DemoYellow = Color(0xFFFFE066)
private val DemoGreen = Color(0xFF9BE7B4)
private val DemoInk = Color(0xFF1F3A5F)
private val DemoRed = Color(0xFFE5484D)
private val DemoLine = Color(0xFFCBD2DC)

private fun ease(x: Float): Float {
    val v = x.coerceIn(0f, 1f)
    return v * v * (3f - 2f * v)
}

/** مرحلة بين a و b من التقدم t (0..1). */
private fun phase(t: Float, a: Float, b: Float): Float = ease((t - a) / (b - a))

/** رسم متحرك يشرح الأداة، يتكرر كل 3 ثوانٍ. */
@Composable
fun ToolDemo(demo: Demo, modifier: Modifier) {
    val t by rememberInfiniteTransition(label = "demo").animateFloat(
        0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "t"
    )
    Canvas(modifier.clip(RoundedCornerShape(14.dp)).background(Palette.Ivory)) {
        when (demo) {
            Demo.PEN -> demoPen(t)
            Demo.HIGHLIGHT -> demoHighlight(t)
            Demo.SNAP -> demoSnap(t)
            Demo.NO_SUMMARY -> demoToSummary(t, withExcluded = true)
            Demo.SUMMARY -> demoToSummary(t, withExcluded = false)
            Demo.ERASER -> demoEraser(t)
            Demo.LASER -> demoLaser(t)
            Demo.SHAPES -> demoShapes(t)
            Demo.NAV -> demoNav(t)
            Demo.BOOKMARK -> demoBookmark(t)
            Demo.TIMER -> demoTimer(t)
            Demo.IMAGE -> demoImage(t)
            Demo.TEXT -> demoText(t)
            Demo.ADD_PAGE -> demoAddPage(t)
            Demo.OCR -> demoOcr(t)
            Demo.SEARCH -> demoSearch(t)
            Demo.EXPORT -> demoExport(t)
            Demo.UNDO -> demoUndo(t)
            Demo.ZOOM -> demoZoom(t)
            Demo.FIT -> demoFit(t)
            Demo.FOCUS -> demoFocus(t)
            Demo.CARDS -> demoCards(t)
            Demo.LASSO -> demoLasso(t)
            Demo.STICKY -> demoSticky(t)
            Demo.ZOOMWIN -> demoZoomWin(t)
            Demo.NIGHT -> demoNight(t)
        }
    }
}

/** صفحة بيضاء صغيرة في وسط المساحة؛ تعيد إطارها (يسار، أعلى، عرض، ارتفاع). */
private fun DrawScope.page(cx: Float = size.width / 2f, scale: Float = 1f): FloatArray {
    val ph = size.height * 0.84f * scale
    val pw = ph * 0.74f
    val l = cx - pw / 2f
    val tp = (size.height - ph) / 2f
    drawRoundRect(Color.White, Offset(l, tp), Size(pw, ph), CornerRadius(6f))
    drawRoundRect(DemoLine.copy(alpha = 0.6f), Offset(l, tp), Size(pw, ph), CornerRadius(6f), style = Stroke(1.5f))
    return floatArrayOf(l, tp, pw, ph)
}

/** أسطر نص رمادية داخل إطار. */
private fun DrawScope.textLines(f: FloatArray, n: Int = 6, color: Color = DemoLine): List<Pair<Offset, Float>> {
    val out = ArrayList<Pair<Offset, Float>>()
    val lh = f[3] / (n + 2)
    for (i in 0 until n) {
        val y = f[1] + lh * (i + 1.2f)
        val wFrac = if (i % 3 == 2) 0.55f else 0.82f
        val x = f[0] + f[2] * 0.09f
        val w = f[2] * wFrac
        drawRoundRect(color, Offset(x, y), Size(w, lh * 0.32f), CornerRadius(lh * 0.16f))
        out.add(Offset(x, y) to w)
    }
    return out
}

private fun wave(x0: Float, x1: Float, y: Float, amp: Float, upto: Float, n: Int = 60): Path {
    val p = Path()
    val k = (n * upto).toInt().coerceAtLeast(1)
    for (i in 0..k) {
        val f = i / n.toFloat()
        val x = x0 + (x1 - x0) * f
        val yy = y + amp * sin(f * 4f * PI.toFloat())
        if (i == 0) p.moveTo(x, yy) else p.lineTo(x, yy)
    }
    return p
}

private fun DrawScope.demoPen(t: Float) {
    val f = page()
    textLines(f, 3)
    val prog = phase(t, 0.05f, 0.75f)
    val y = f[1] + f[3] * 0.72f
    drawPath(
        wave(f[0] + f[2] * 0.1f, f[0] + f[2] * 0.9f, y, f[3] * 0.06f, prog),
        DemoInk, style = Stroke(4.5f, cap = StrokeCap.Round)
    )
    val x = f[0] + f[2] * (0.1f + 0.8f * prog)
    val yy = y + f[3] * 0.06f * sin(prog * 4f * PI.toFloat())
    drawLine(Palette.Gold, Offset(x, yy), Offset(x + 26f, yy - 34f), 7f, StrokeCap.Round)
}

private fun DrawScope.demoHighlight(t: Float) {
    val f = page()
    val lines = textLines(f)
    val (o, w) = lines[2]
    val lh = f[3] / 8f
    val prog = phase(t, 0.1f, 0.6f)
    val hh = lh * 0.62f
    drawRoundRect(
        DemoYellow, Offset(o.x - 4f, o.y - hh * 0.32f), Size((w + 8f) * prog, hh),
        CornerRadius(hh * 0.32f), blendMode = BlendMode.Multiply
    )
}

private fun DrawScope.demoSnap(t: Float) {
    val f = page()
    val lines = textLines(f)
    val (o, w) = lines[3]
    val lh = f[3] / 8f
    val hh = lh * 0.62f
    // خط يدوي مائل ثم يستقر كمستطيل منتظم على السطر
    val draw = phase(t, 0.05f, 0.45f)
    val snap = phase(t, 0.5f, 0.68f)
    if (snap < 1f) {
        drawPath(
            wave(o.x, o.x + w, o.y + hh * 0.15f, hh * 0.35f, draw),
            DemoYellow.copy(alpha = 0.9f * (1f - snap)),
            style = Stroke(hh * 0.9f, cap = StrokeCap.Round), blendMode = BlendMode.Multiply
        )
    }
    if (snap > 0f) {
        drawRoundRect(
            DemoYellow.copy(alpha = snap), Offset(o.x - 4f, o.y - hh * 0.32f), Size(w + 8f, hh),
            CornerRadius(hh * 0.32f), blendMode = BlendMode.Multiply
        )
    }
}

private fun DrawScope.demoToSummary(t: Float, withExcluded: Boolean) {
    val f = page(cx = size.width * 0.3f)
    val lines = textLines(f)
    val lh = f[3] / 8f
    val hh = lh * 0.62f
    val grow = phase(t, 0.05f, 0.35f)
    val (a, aw) = lines[1]
    val (b, bw) = lines[4]
    drawRoundRect(DemoYellow, Offset(a.x - 4f, a.y - hh * 0.32f), Size((aw + 8f) * grow, hh), CornerRadius(hh * 0.32f), blendMode = BlendMode.Multiply)
    drawRoundRect(
        if (withExcluded) DemoGreen else DemoYellow, Offset(b.x - 4f, b.y - hh * 0.32f), Size((bw + 8f) * grow, hh),
        CornerRadius(hh * 0.32f), blendMode = BlendMode.Multiply
    )
    // درج الملخص على اليمين
    val sx = size.width * 0.58f
    val sw = size.width * 0.34f
    val sy = size.height * 0.12f
    drawRoundRect(Color.White, Offset(sx, sy), Size(sw, size.height * 0.76f), CornerRadius(10f))
    drawRoundRect(Palette.Gold, Offset(sx + 10f, sy + 12f), Size(sw * 0.4f, 7f), CornerRadius(4f))
    // السطر الأول ينتقل إلى الملخص
    val fly = phase(t, 0.42f, 0.7f)
    val tx = sx + 12f
    val ty = sy + 36f
    val x = a.x + (tx - a.x) * fly
    val y = a.y + (ty - a.y) * fly
    val ww = aw + (sw - 24f - aw) * fly
    if (fly > 0f) drawRoundRect(DemoYellow.copy(alpha = 0.95f), Offset(x, y - hh * 0.3f), Size(ww, hh * 0.8f), CornerRadius(hh * 0.3f))
    if (!withExcluded) {
        val fly2 = phase(t, 0.55f, 0.83f)
        val ty2 = ty + hh * 1.4f
        if (fly2 > 0f) {
            drawRoundRect(
                DemoYellow.copy(alpha = 0.95f),
                Offset(b.x + (tx - b.x) * fly2, b.y + (ty2 - b.y) * fly2 - hh * 0.3f),
                Size(bw + (sw - 24f - bw) * fly2, hh * 0.8f), CornerRadius(hh * 0.3f)
            )
        }
    } else {
        // السطر الأخضر يبقى على الصفحة: علامة «لا» بجانبه
        val no = phase(t, 0.55f, 0.7f)
        if (no > 0f) {
            val cx = b.x + bw + 22f
            val cy = b.y + hh * 0.1f
            val r = 11f * no
            drawCircle(DemoRed, r + 3f, Offset(cx, cy), style = Stroke(3f))
            drawLine(DemoRed, Offset(cx - r * 0.7f, cy + r * 0.7f), Offset(cx + r * 0.7f, cy - r * 0.7f), 3f)
        }
    }
}

private fun DrawScope.demoEraser(t: Float) {
    val f = page()
    textLines(f, 3)
    val y = f[1] + f[3] * 0.7f
    val x0 = f[0] + f[2] * 0.1f
    val x1 = f[0] + f[2] * 0.9f
    val pos = phase(t, 0.15f, 0.75f)
    val ex = x0 + (x1 - x0) * pos
    clipRect(left = ex, top = 0f, right = size.width, bottom = size.height) {
        drawPath(wave(x0, x1, y, f[3] * 0.06f, 1f), DemoInk, style = Stroke(4.5f, cap = StrokeCap.Round))
    }
    if (t > 0.1f && t < 0.85f) {
        drawCircle(Color.White, 18f, Offset(ex, y))
        drawCircle(Palette.TextSecondary, 18f, Offset(ex, y), style = Stroke(2.5f))
    }
}

private fun DrawScope.demoLaser(t: Float) {
    page()
    val cx = size.width / 2f
    val cy = size.height / 2f
    fun at(p: Float) = Offset(
        cx + size.height * 0.36f * sin(p * 2f * PI.toFloat()),
        cy + size.height * 0.22f * sin(p * 4f * PI.toFloat())
    )
    for (k in 14 downTo 1) {
        val p = t - k * 0.012f
        drawCircle(DemoRed.copy(alpha = 0.5f * (1f - k / 15f)), 6f * (1f - k / 18f), at(p))
    }
    drawCircle(DemoRed.copy(alpha = 0.25f), 16f, at(t))
    drawCircle(DemoRed, 7f, at(t))
}

private fun DrawScope.demoShapes(t: Float) {
    page()
    val cx = size.width / 2f
    val cy = size.height / 2f
    val hw = size.height * 0.3f
    val hh = size.height * 0.2f
    val neat = listOf(Offset(cx - hw, cy - hh), Offset(cx + hw, cy - hh), Offset(cx + hw, cy + hh), Offset(cx - hw, cy + hh))
    val shaky = listOf(
        Offset(cx - hw - 8f, cy - hh + 6f), Offset(cx + hw + 5f, cy - hh - 9f),
        Offset(cx + hw - 10f, cy + hh + 7f), Offset(cx - hw + 6f, cy + hh - 4f)
    )
    val draw = phase(t, 0.05f, 0.45f)
    val snap = phase(t, 0.52f, 0.66f)
    val pts = neat.indices.map { i ->
        Offset(shaky[i].x + (neat[i].x - shaky[i].x) * snap, shaky[i].y + (neat[i].y - shaky[i].y) * snap)
    }
    val p = Path()
    val total = 4f * draw
    p.moveTo(pts[0].x, pts[0].y)
    for (i in 1..4) {
        val seg = (total - (i - 1)).coerceIn(0f, 1f)
        if (seg <= 0f) break
        val a = pts[i - 1]
        val b = pts[i % 4]
        p.lineTo(a.x + (b.x - a.x) * seg, a.y + (b.y - a.y) * seg)
    }
    drawPath(p, DemoInk, style = Stroke(4f, cap = StrokeCap.Round))
}

private fun DrawScope.demoNav(t: Float) {
    val ph = size.height * 0.7f
    val gap = ph * 1.12f
    val shift = phase(t, 0.2f, 0.7f) * gap
    clipRect {
        for (k in 0..2) {
            val pw = ph * 0.74f
            val l = size.width / 2f - pw / 2f
            val tp = size.height * 0.15f + k * gap - shift
            drawRoundRect(Color.White, Offset(l, tp), Size(pw, ph), CornerRadius(6f))
            for (i in 0 until 4) drawRoundRect(DemoLine, Offset(l + pw * 0.1f, tp + ph * (0.15f + i * 0.17f)), Size(pw * 0.7f, 6f), CornerRadius(3f))
        }
    }
    val chip = Size(64f, 30f)
    drawRoundRect(Palette.Navy, Offset(size.width * 0.82f - chip.width / 2f, size.height * 0.1f), chip, CornerRadius(15f))
}

private fun DrawScope.demoBookmark(t: Float) {
    val f = page()
    textLines(f)
    val drop = phase(t, 0.15f, 0.45f)
    val bw = f[2] * 0.12f
    val bh = f[3] * 0.28f * drop
    val x = f[0] + f[2] * 0.75f
    val y = f[1]
    if (bh > 1f) {
        val p = Path().apply {
            moveTo(x, y); lineTo(x + bw, y); lineTo(x + bw, y + bh)
            lineTo(x + bw / 2f, y + bh - bw * 0.45f); lineTo(x, y + bh); close()
        }
        drawPath(p, Palette.Gold)
    }
}

private fun DrawScope.demoTimer(t: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = size.height * 0.32f
    drawCircle(Color.White, r + 10f, Offset(cx, cy))
    drawCircle(DemoLine, r, Offset(cx, cy), style = Stroke(10f))
    drawArc(
        Palette.Navy, -90f, 360f * t, false, Offset(cx - r, cy - r), Size(2 * r, 2 * r),
        style = Stroke(10f, cap = StrokeCap.Round)
    )
    val a = (-90f + 360f * t) * PI.toFloat() / 180f
    drawCircle(Palette.Gold, 9f, Offset(cx + r * cos(a), cy + r * sin(a)))
    // زر إيقاف مؤقت
    drawRoundRect(Palette.Navy, Offset(cx - 13f, cy - 16f), Size(9f, 32f), CornerRadius(3f))
    drawRoundRect(Palette.Navy, Offset(cx + 4f, cy - 16f), Size(9f, 32f), CornerRadius(3f))
}

private fun DrawScope.demoImage(t: Float) {
    val f = page()
    textLines(f, 2)
    val s = 0.6f + 0.4f * phase(t, 0.1f, 0.5f)
    val a = phase(t, 0.05f, 0.3f)
    val w = f[2] * 0.7f * s
    val h = f[3] * 0.4f * s
    val l = f[0] + (f[2] - w) / 2f
    val tp = f[1] + f[3] * 0.45f
    drawRoundRect(Color(0xFFDDEBF7).copy(alpha = a), Offset(l, tp), Size(w, h), CornerRadius(6f))
    val m = Path().apply {
        moveTo(l + w * 0.1f, tp + h * 0.9f); lineTo(l + w * 0.4f, tp + h * 0.4f)
        lineTo(l + w * 0.6f, tp + h * 0.7f); lineTo(l + w * 0.75f, tp + h * 0.5f)
        lineTo(l + w * 0.92f, tp + h * 0.9f); close()
    }
    drawPath(m, Color(0xFF5B8DB8).copy(alpha = a))
    drawCircle(Palette.Gold.copy(alpha = a), h * 0.12f, Offset(l + w * 0.78f, tp + h * 0.25f))
    drawRoundRect(Palette.Navy.copy(alpha = a), Offset(l, tp), Size(w, h), CornerRadius(6f), style = Stroke(2.5f))
}

private fun DrawScope.demoText(t: Float) {
    val f = page()
    val l = f[0] + f[2] * 0.1f
    val tp = f[1] + f[3] * 0.3f
    val w = f[2] * 0.8f
    val h = f[3] * 0.36f
    drawRoundRect(Palette.Navy, Offset(l, tp), Size(w, h), CornerRadius(6f), style = Stroke(2f))
    val typed = phase(t, 0.1f, 0.8f) * 3f
    for (i in 0 until 3) {
        val frac = (typed - i).coerceIn(0f, 1f)
        if (frac > 0f) drawRoundRect(DemoInk, Offset(l + 12f, tp + 16f + i * h * 0.28f), Size((w - 24f) * frac * (if (i == 2) 0.6f else 1f), 7f), CornerRadius(3f))
    }
}

private fun DrawScope.demoAddPage(t: Float) {
    val ph = size.height * 0.62f
    val pw = ph * 0.74f
    val y = (size.height - ph) / 2f
    for (k in 0..1) {
        drawRoundRect(Color.White, Offset(size.width * 0.18f + k * pw * 1.15f, y), Size(pw, ph), CornerRadius(6f))
    }
    val slide = phase(t, 0.15f, 0.55f)
    val x = size.width + 10f - (size.width + 10f - (size.width * 0.18f + 2 * pw * 1.15f)) * slide
    drawRoundRect(Color.White, Offset(x, y), Size(pw, ph), CornerRadius(6f))
    drawRoundRect(Palette.Gold, Offset(x, y), Size(pw, ph), CornerRadius(6f), style = Stroke(3f))
    drawLine(Palette.Gold, Offset(x + pw / 2f, y + ph * 0.38f), Offset(x + pw / 2f, y + ph * 0.62f), 4f, StrokeCap.Round)
    drawLine(Palette.Gold, Offset(x + pw * 0.34f, y + ph / 2f), Offset(x + pw * 0.66f, y + ph / 2f), 4f, StrokeCap.Round)
}

private fun DrawScope.demoOcr(t: Float) {
    val f = page()
    val lines = textLines(f)
    val scanY = f[1] + f[3] * phase(t, 0.05f, 0.8f)
    for ((o, w) in lines) {
        if (o.y < scanY) drawRoundRect(DemoInk, o, Size(w, f[3] / 8f * 0.32f), CornerRadius(4f))
    }
    drawLine(Palette.Gold, Offset(f[0], scanY), Offset(f[0] + f[2], scanY), 4f)
    drawRect(Palette.Gold.copy(alpha = 0.12f), Offset(f[0], scanY - 24f), Size(f[2], 24f))
}

private fun DrawScope.demoSearch(t: Float) {
    val f = page()
    val lines = textLines(f)
    val (o, w) = lines[3]
    val found = t > 0.55f
    if (found) {
        drawRoundRect(Color(0x99FF9800), Offset(o.x + w * 0.3f - 4f, o.y - 6f), Size(w * 0.3f, 18f), CornerRadius(6f))
    }
    val p = phase(t, 0.05f, 0.55f)
    val cx = f[0] + f[2] * (0.15f + 0.4f * p)
    val cy = f[1] + f[3] * (0.2f + 0.25f * p)
    drawCircle(Palette.Navy, 20f, Offset(cx, cy), style = Stroke(4f))
    drawLine(Palette.Navy, Offset(cx + 14f, cy + 14f), Offset(cx + 30f, cy + 30f), 6f, StrokeCap.Round)
}

private fun DrawScope.demoExport(t: Float) {
    val move = phase(t, 0.3f, 0.7f)
    val f = page(scale = 1f - 0.25f * move)
    textLines(f, 4)
    drawRoundRect(DemoYellow, Offset(f[0] + f[2] * 0.08f, f[1] + f[3] * 0.3f), Size(f[2] * 0.6f, 12f), CornerRadius(6f), blendMode = BlendMode.Multiply)
    val tag = phase(t, 0.55f, 0.75f)
    if (tag > 0f) {
        val w = f[2] * 0.55f
        drawRoundRect(DemoRed.copy(alpha = tag), Offset(f[0] + f[2] - w * 0.8f, f[1] + f[3] * 0.72f), Size(w, 30f), CornerRadius(6f))
    }
    val ay = size.height * (0.2f + 0.5f * move)
    drawLine(Palette.Navy, Offset(size.width * 0.85f, ay - 30f), Offset(size.width * 0.85f, ay + 10f), 5f, StrokeCap.Round)
    drawLine(Palette.Navy, Offset(size.width * 0.85f - 12f, ay - 2f), Offset(size.width * 0.85f, ay + 12f), 5f, StrokeCap.Round)
    drawLine(Palette.Navy, Offset(size.width * 0.85f + 12f, ay - 2f), Offset(size.width * 0.85f, ay + 12f), 5f, StrokeCap.Round)
}

private fun DrawScope.demoUndo(t: Float) {
    val f = page()
    textLines(f, 3)
    val y = f[1] + f[3] * 0.72f
    val prog = if (t < 0.45f) phase(t, 0.05f, 0.4f) else 1f - phase(t, 0.55f, 0.8f)
    drawPath(
        wave(f[0] + f[2] * 0.1f, f[0] + f[2] * 0.9f, y, f[3] * 0.06f, prog),
        DemoInk, style = Stroke(4.5f, cap = StrokeCap.Round)
    )
    if (t > 0.48f && t < 0.85f) {
        val cx = size.width * 0.85f
        val cy = size.height * 0.25f
        drawArc(Palette.Gold, 200f, 260f, false, Offset(cx - 18f, cy - 18f), Size(36f, 36f), style = Stroke(5f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.demoZoom(t: Float) {
    val z = 1f + 0.5f * sin(t * PI.toFloat()).let { it * it }
    withTransform({ scale(z, z, Offset(size.width / 2f, size.height / 2f)) }) {
        val f = page()
        textLines(f)
    }
}

private fun DrawScope.demoFit(t: Float) {
    val fit = phase(t, 0.2f, 0.55f) * (1f - phase(t, 0.8f, 0.95f))
    val s = 1.5f - 0.5f * fit
    clipRect {
        withTransform({ scale(s, s, Offset(size.width / 2f, size.height / 2f)) }) {
            val f = page()
            textLines(f)
        }
    }
    val c = 22f
    val m = 10f
    listOf(
        Offset(m, m) to Offset(1f, 1f), Offset(size.width - m, m) to Offset(-1f, 1f),
        Offset(m, size.height - m) to Offset(1f, -1f), Offset(size.width - m, size.height - m) to Offset(-1f, -1f)
    ).forEach { (p, d) ->
        drawLine(Palette.Gold, p, Offset(p.x + c * d.x, p.y), 4f, StrokeCap.Round)
        drawLine(Palette.Gold, p, Offset(p.x, p.y + c * d.y), 4f, StrokeCap.Round)
    }
}

private fun DrawScope.demoFocus(t: Float) {
    val hide = phase(t, 0.3f, 0.55f) * (1f - phase(t, 0.85f, 0.98f))
    val f = page(scale = 0.86f + 0.14f * hide)
    textLines(f)
    val a = 1f - hide
    if (a > 0f) {
        drawRoundRect(Color.White.copy(alpha = a), Offset(size.width * 0.15f, 6f), Size(size.width * 0.7f, 22f), CornerRadius(11f))
        drawRoundRect(Palette.Navy.copy(alpha = a), Offset(size.width * 0.18f, 13f), Size(size.width * 0.3f, 8f), CornerRadius(4f))
        drawCircle(Color.White.copy(alpha = a), 14f, Offset(22f, size.height / 2f))
        drawCircle(Color.White.copy(alpha = a), 14f, Offset(size.width - 22f, size.height / 2f))
    }
    // المؤقت يبقى ظاهرًا
    drawRoundRect(Color.White, Offset(size.width - 92f, size.height - 34f), Size(80f, 24f), CornerRadius(12f))
    drawRoundRect(Palette.Navy, Offset(size.width - 82f, size.height - 26f), Size(40f, 8f), CornerRadius(4f))
}

private fun DrawScope.demoCards(t: Float) {
    val flip = phase(t, 0.3f, 0.6f) * (1f - phase(t, 0.85f, 0.98f))
    val sx = abs(cos(flip * PI.toFloat()))
    val back = flip > 0.5f
    val w = size.width * 0.5f
    val h = size.height * 0.7f
    val cx = size.width / 2f
    val l = cx - w * sx / 2f
    val tp = (size.height - h) / 2f
    drawRoundRect(if (back) Color(0xFFFFF8E7) else Color.White, Offset(l, tp), Size(w * sx, h), CornerRadius(14f))
    drawRoundRect(if (back) Palette.Gold else DemoLine, Offset(l, tp), Size(w * sx, h), CornerRadius(14f), style = Stroke(2.5f))
    if (sx > 0.3f) {
        if (!back) {
            drawRoundRect(Palette.Navy, Offset(cx - w * sx * 0.3f, tp + h * 0.45f), Size(w * sx * 0.6f, 12f), CornerRadius(6f))
        } else {
            drawRoundRect(Palette.Gold, Offset(cx - w * sx * 0.15f, tp + h * 0.2f), Size(w * sx * 0.3f, 6f), CornerRadius(3f))
            for (i in 0 until 3) drawRoundRect(DemoLine, Offset(cx - w * sx * 0.35f, tp + h * (0.4f + i * 0.14f)), Size(w * sx * 0.7f, 7f), CornerRadius(3f))
        }
    }
}

// ---------------------------------------------------------------- التذكير اليومي

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReminderRow() {
    val c = AppController
    val context = androidx.compose.ui.platform.LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { ok ->
        if (!ok) c.messages.tryEmit(L("لن تظهر التذكيرات دون السماح بالإشعارات", "Reminders won’t show without notification permission"))
    }
    fun hhmm(m: Int) = "%02d:%02d".format(m / 60, m % 60)
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, Palette.Divider, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(L("تذكير بالمذاكرة كل يوم", "Remind me to study every day"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
            Text(
                L("إشعار بما تبقى من هدف اليوم وأقرب امتحان", "A notification with what’s left of today’s goal and your nearest exam"),
                fontSize = 12.sp, lineHeight = 18.sp, color = Palette.TextSecondary
            )
        }
        ChoiceChip(hhmm(c.reminderMinute), false) { picking = true }
        Spacer(Modifier.width(8.dp))
        NavySwitch(c.reminderOn) { on ->
            c.setReminder(on)
            if (on && android.os.Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    if (picking) {
        val state = androidx.compose.material3.rememberTimePickerState(c.reminderMinute / 60, c.reminderMinute % 60, true)
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(L("وقت التذكير", "Reminder time")) },
            text = { androidx.compose.material3.TimePicker(state = state) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    c.setReminder(true, state.hour * 60 + state.minute)
                    picking = false
                }) { Text(L("حفظ", "Save")) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { picking = false }) { Text(L("إلغاء", "Cancel")) } }
        )
    }
}

// ---------------------------------------------------------------- رسوم الأدوات الجديدة

private fun DrawScope.demoLasso(t: Float) {
    val f = page()
    val cx = f[0] + f[2] * 0.5f
    val cy = f[1] + f[3] * 0.45f
    val shift = phase(t, 0.6f, 0.85f) * f[2] * 0.12f
    // كتابة داخل الحلقة
    drawPath(wave(cx - f[2] * 0.22f + shift, cx + f[2] * 0.22f + shift, cy, f[3] * 0.04f, 1f), DemoInk, style = Stroke(4f, cap = StrokeCap.Round))
    // الحلقة ترسم نفسها ثم يظهر الإطار
    val loop = phase(t, 0.05f, 0.45f)
    val p = Path()
    val n = 48
    for (i in 0..(n * loop).toInt()) {
        val a = i / n.toFloat() * 2f * PI.toFloat()
        val x = cx + f[2] * 0.3f * cos(a)
        val y = cy + f[3] * 0.12f * sin(a)
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    if (t < 0.5f) drawPath(p, Palette.Navy, style = Stroke(2.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(9f, 7f))))
    else {
        val l = cx - f[2] * 0.25f + shift
        drawRect(Palette.Navy, Offset(l, cy - f[3] * 0.07f), Size(f[2] * 0.5f, f[3] * 0.14f),
            style = Stroke(2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(9f, 7f))))
        drawCircle(Palette.Navy, 7f, Offset(l + f[2] * 0.5f, cy + f[3] * 0.07f))
    }
}

private fun DrawScope.demoSticky(t: Float) {
    val f = page()
    textLines(f)
    val open = phase(t, 0.3f, 0.5f) * (1f - phase(t, 0.82f, 0.95f))
    val ix = f[0] + f[2] * 0.72f
    val iy = f[1] + f[3] * 0.1f
    val s = f[2] * 0.14f
    drawRoundRect(Color(0xFFFFE27A), Offset(ix, iy), Size(s, s), CornerRadius(6f))
    if (open > 0f) {
        val w = size.width * 0.42f * open
        val h = size.height * 0.5f * open
        val l = size.width * 0.55f - w / 2f
        val tp = size.height * 0.3f
        drawRoundRect(Color(0xFFFFF3B8), Offset(l, tp), Size(w, h), CornerRadius(10f))
        for (i in 0 until 3) {
            drawRoundRect(Color(0xFF6B4E00).copy(alpha = 0.6f * open), Offset(l + 14f, tp + 16f + i * h * 0.26f), Size((w - 28f).coerceAtLeast(0f), 6f), CornerRadius(3f))
        }
    }
}

private fun DrawScope.demoZoomWin(t: Float) {
    val f = page(cx = size.width * 0.25f, scale = 0.9f)
    textLines(f, 3)
    // المنطقة على الصفحة
    val bx = f[0] + f[2] * 0.15f
    val by = f[1] + f[3] * 0.62f
    val bw = f[2] * 0.6f
    val bh = f[3] * 0.12f
    drawRect(Palette.Gold, Offset(bx, by), Size(bw, bh), style = Stroke(2f))
    val prog = phase(t, 0.1f, 0.75f)
    // النافذة المكبّرة
    val px = size.width * 0.5f
    val py = size.height * 0.3f
    val pw = size.width * 0.46f
    val ph = size.height * 0.42f
    drawRoundRect(Color.White, Offset(px, py), Size(pw, ph), CornerRadius(10f))
    drawRoundRect(Palette.Gold, Offset(px, py), Size(pw, ph), CornerRadius(10f), style = Stroke(2.5f))
    drawPath(wave(px + pw * 0.08f, px + pw * 0.92f, py + ph * 0.55f, ph * 0.14f, prog), DemoInk, style = Stroke(5f, cap = StrokeCap.Round))
    drawPath(wave(bx + bw * 0.08f, bx + bw * 0.92f, by + bh * 0.55f, bh * 0.14f, prog), DemoInk, style = Stroke(1.6f, cap = StrokeCap.Round))
}

private fun DrawScope.demoNight(t: Float) {
    val dark = phase(t, 0.25f, 0.45f) * (1f - phase(t, 0.85f, 0.98f))
    val f = page()
    drawRoundRect(Color(0xFF1E232B).copy(alpha = dark), Offset(f[0], f[1]), Size(f[2], f[3]), CornerRadius(6f))
    val lineCol = Color(
        DemoLine.red + (0.85f - DemoLine.red) * dark,
        DemoLine.green + (0.87f - DemoLine.green) * dark,
        DemoLine.blue + (0.9f - DemoLine.blue) * dark
    )
    val lines = textLines(f, 6, lineCol)
    val (o, w) = lines[2]
    val hh = f[3] / 8f * 0.62f
    drawRoundRect(
        Color(0xFFFFE066).copy(alpha = 0.55f + 0.2f * (1f - dark)), Offset(o.x - 4f, o.y - hh * 0.32f), Size(w + 8f, hh),
        CornerRadius(hh * 0.32f), blendMode = if (dark > 0.5f) BlendMode.Screen else BlendMode.Multiply
    )
}

// ---------------------------------------------------------------- MyNotes Pro: إعدادات الذكاء الاصطناعي

@Composable
private fun AiSettings() {
    val c = AppController
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, Palette.Divider, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            L("ذكاء اصطناعي مجاني يعمل داخل الجهاز دون إنترنت: يرتّب الملخصات ويشرح النصوص. نزّله مرة واحدة (الإنترنت للتنزيل فقط). لا يعمل إلا عند طلبك، إلا ما فعّلته أدناه.",
                "A free AI running inside the device, offline: organizes summaries and explains text. Download it once (internet only for the download). It runs only when you ask, except what you turn on below."),
            fontSize = 12.sp, lineHeight = 18.sp, color = Palette.TextSecondary
        )
        Ai.models.forEach { m ->
            val sel = Ai.selectedId == m.id
            val installed = Ai.installed(m)
            val prog = Ai.progress[m.id]
            val rowShape = RoundedCornerShape(12.dp)
            Column(
                Modifier.fillMaxWidth().clip(rowShape)
                    .border(if (sel) 2.dp else 1.dp, if (sel) Palette.Gold else Palette.Divider, rowShape)
                    .clickable(enabled = installed) { Ai.select(m.id) }
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(16.dp).clip(CircleShape)
                            .border(2.dp, if (sel) Palette.Gold else Palette.Divider, CircleShape)
                            .background(if (sel && installed) Palette.Gold else Color.Transparent)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.title(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
                        Text(
                            m.desc() + if (m.sizeMb > 0) L(" · نحو ${m.sizeMb} ميغابايت", " · about ${m.sizeMb} MB") else "",
                            fontSize = 12.sp, lineHeight = 17.sp, color = Palette.TextSecondary
                        )
                    }
                    when {
                        prog != null -> androidx.compose.material3.TextButton(onClick = { Ai.cancelDownload(m) }) {
                            Text(L("إيقاف", "Pause"), color = Palette.Danger)
                        }
                        installed -> androidx.compose.material3.TextButton(onClick = { Ai.delete(m) }) {
                            Text(L("حذف", "Delete"), color = Palette.Danger)
                        }
                        else -> androidx.compose.material3.TextButton(onClick = {
                            Ai.select(m.id)
                            Ai.download(m)
                        }) { Text(L("تنزيل", "Download"), color = Palette.Navy, fontWeight = FontWeight.Bold) }
                    }
                }
                if (prog != null) {
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { prog }, modifier = Modifier.fillMaxWidth(), color = Palette.Gold
                    )
                    Text("${(prog * 100).toInt()}%", fontSize = 11.sp, color = Palette.TextSecondary)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Divider))
        Text(L("المحفزات التلقائية (مطفأة افتراضيًا)", "Automatic triggers (off by default)"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
        Text(
            L("الذكاء لا يعمل وحده إلا إن فعّلت هذه الخيارات. الطلب الصريح من درج الملخصات (تبويب AI) يعمل دائمًا.",
                "The AI does not run by itself unless you turn these on. The explicit request in the summaries drawer (AI tab) always works."),
            fontSize = 12.sp, lineHeight = 17.sp, color = Palette.TextSecondary
        )
        AiToggle(L("إكمال الملخص تلقائيًا بعد التظليل", "Continue the summary automatically after highlighting"), AiOrganizer.autoContinue) {
            AiOrganizer.toggleAutoContinue(!AiOrganizer.autoContinue)
        }
        AiToggle(L("قراءة صفحات التظليل قبل الترتيب (عناوين أدق)", "Read highlighted pages before organizing (better headings)"), AiOrganizer.autoReadPages) {
            AiOrganizer.toggleAutoReadPages(!AiOrganizer.autoReadPages)
        }
        AiToggle(L("قراءة المستند كاملًا في الخلفية", "Read the whole document in the background"), AiDocReader.enabled) {
            AiDocReader.toggle(!AiDocReader.enabled)
        }
        AiToggle(L("بطاقات مراجعة تلقائية", "Automatic review cards"), AiOrganizer.autoCards) {
            AiOrganizer.toggleAutoCards(!AiOrganizer.autoCards)
        }
        AiToggle(L("مراجعة ذاتية لما يكتبه الذكاء", "Self-review of what the AI writes"), AiOrganizer.selfReview) {
            AiOrganizer.toggleSelfReview(!AiOrganizer.selfReview)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Divider))
        Text(L("اختبار قراءة الصور", "Image reading test"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
        Text(
            L("قراءة صفحة التظليل بصورتها لم تُجرَّب على كل الأجهزة. هذا الاختبار يعطيها صورة صغيرة ويعرض ما قرأه النموذج، فتعرف هل يعمل على جهازك. إن فشل يُرتَّب الملخص من نص القراءة الضوئية.",
                "Reading a highlighted page from its image is not proven on every device. This test gives it a small image and shows what it read, so you know whether it works on your device. If it fails, the summary uses the OCR text."),
            fontSize = 12.sp, lineHeight = 17.sp, color = Palette.TextSecondary
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Button(
                enabled = !testing && Ai.ready,
                onClick = {
                    testing = true
                    testResult = null
                    scope.launch {
                        testResult = try { Ai.visionTest() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (t: Throwable) { Ai.errorText(t, L("فشل الاختبار", "Test failed")) }
                        testing = false
                    }
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Palette.Navy)
            ) { Text(if (testing) L("جارٍ الاختبار…", "Testing…") else L("اختبر قراءة الصور", "Test image reading")) }
        }
        testResult?.let { Text(it, fontSize = 12.sp, lineHeight = 17.sp, color = Palette.TextPrimary) }
    }
}

/** مدة الدراسة والراحة: اختيارات جاهزة أو مدة يكتبها المستخدم. */
@Composable
private fun PomodoroSettings() {
    val c = AppController
    var custom by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, Palette.Divider, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            L("دراسة ${c.studyMin} دقيقة، ثم راحة ${c.breakMin} دقائق", "Study ${c.studyMin} min, then break ${c.breakMin} min"),
            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary
        )
        Row(
            Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(25 to 5, 45 to 10, 50 to 10, 90 to 15).forEach { (st, br) ->
                ChoiceChip("$st / $br", c.studyMin == st && c.breakMin == br && !custom) { custom = false; c.setPomodoro(st, br) }
            }
            ChoiceChip(L("مدة أخرى", "Custom"), custom) { custom = true }
        }
        if (custom) {
            var st by remember { mutableStateOf(c.studyMin.toString()) }
            var br by remember { mutableStateOf(c.breakMin.toString()) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.OutlinedTextField(
                    value = st, onValueChange = { st = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text(L("دراسة (د)", "Study (min)")) }, singleLine = true, modifier = Modifier.weight(1f)
                )
                androidx.compose.material3.OutlinedTextField(
                    value = br, onValueChange = { br = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text(L("راحة (د)", "Break (min)")) }, singleLine = true, modifier = Modifier.weight(1f)
                )
                androidx.compose.material3.TextButton(onClick = {
                    c.setPomodoro(st.toIntOrNull() ?: 25, br.toIntOrNull() ?: 5)
                    custom = false
                }) { Text(L("حفظ", "Save"), fontWeight = FontWeight.Bold, color = Palette.Navy) }
            }
        }
    }
}
