package com.mynotes.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val c = AppController
    val listState = rememberLazyListState()
    val pager = rememberPagerState(pageCount = { ToolCatalog.tour.size })
    val scope = rememberCoroutineScope()
    var toolbarOpen by remember { mutableStateOf(false) }
    var templateOpen by remember { mutableStateOf(false) }
    var templateVer by remember { mutableStateOf(0) }
    var licensesOpen by remember { mutableStateOf(false) }
    var privacyOpen by remember { mutableStateOf(false) }
    var termsOpen by remember { mutableStateOf(false) }
    fun showTour(id: String) {
        val i = ToolCatalog.tour.indexOfFirst { it.id == id }
        if (i >= 0) scope.launch {
            listState.animateScrollToItem(0)
            pager.animateScrollToPage(i)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.94f).widthIn(max = 780.dp).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(24.dp),
            color = Palette.Background
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PhIcon(R.drawable.ph_sliders, Palette.Navy, 24.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        L("الإعدادات", "Settings"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold,
                        fontSize = 22.sp, color = Palette.Navy, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) { PhIcon(R.drawable.ph_close, Palette.TextSecondary, 22.dp, L("إغلاق", "Close")) }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 22.dp, end = 22.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { SectionTitle(L("الذكاء الاصطناعي (على جهازك، دون إنترنت)", "AI (on your device, offline)")) }
                    item { AiSettings() }

                    item { SectionTitle(L("إعدادات المستندات", "Document settings")) }
                    item {
                        SettingSwitch(
                            L("المتابعة من حيث توقفت", "Continue where you left off"),
                            L("عند فتح التطبيق تُفتح المستندات التي كانت مفتوحة وآخر مستند استخدمته", "When the app starts, the documents you had open and the last one you used are reopened"),
                            c.resumeLast
                        ) { c.toggleResumeLast() }
                    }
                    item {
                        SettingSwitch(
                            L("فتح المستندات في تبويبات", "Open documents as tabs"),
                            L("كل مستند تفتحه يظهر في تبويب جديد. أوقفه ليحل المستند الجديد محل المفتوح", "Each document opens in a new tab. Turn off to replace the open document"),
                            c.openAsTabs
                        ) { c.toggleOpenAsTabs() }
                    }
                    item {
                        NavRow(
                            L("تخصيص شريط الأدوات", "Toolbar customization"),
                            L("شكل الشريط، والأدوات الظاهرة فيه، واستعادة الشريط الافتراضي", "Toolbar style, which tools it shows, and restoring the default toolbar"),
                            R.drawable.ph_sliders
                        ) { toolbarOpen = true }
                    }
                    item {
                        val d = templateVer.let { Templates.defaultChoice() }
                        NavRow(
                            L("القالب الافتراضي للملاحظات", "Default notebook template"),
                            Templates.labelOf(d.kind) + " · " + Templates.sizeOf(d.size).label + if (d.landscape) L(" · أفقي", " · landscape") else "",
                            R.drawable.ph_pages
                        ) { templateOpen = true }
                    }

                    item { SectionTitle("اللغة / Language") }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChoiceChip("العربية", !I18n.english) { c.setEnglish(false) }
                            ChoiceChip("English", I18n.english) { c.setEnglish(true) }
                        }
                    }
                    item { SectionTitle(L("القلم والأصابع", "Pen and fingers")) }
                    item {
                        SettingSwitch(
                            L("النقر المزدوج على القلم", "Pen double-tap"),
                            L("يبدّل بين الممحاة وآخر قلم استخدمته", "Switches between the eraser and your last pen"),
                            c.penDoubleTapEnabled
                        ) { c.togglePenDoubleTap() }
                    }
                    item { PenKeyLearnRow() }
                    item {
                        SettingSwitch(
                            L("نقرة بإصبعين للتراجع، وبثلاثة للإعادة", "Two-finger tap to undo, three to redo"),
                            L("انقر الصفحة بإصبعين معًا للتراجع، وبثلاثة أصابع لإعادة ما تراجعت عنه", "Tap the page with two fingers to undo, with three fingers to redo"),
                            c.fingerUndo
                        ) { c.toggleFingerUndo() }
                    }

                    item { SectionTitle(L("مؤقت الدراسة", "Study timer")) }
                    item { PomodoroSettings() }

                    item { SectionTitle(L("قفل المستندات", "Document lock")) }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                L("اقفل مستندًا من قائمته في المكتبة؛ يُفتح بالرمز أو البصمة.", "Lock a document from its menu in the library; it opens with your PIN or fingerprint."),
                                fontSize = 12.sp, lineHeight = 18.sp, color = Palette.TextSecondary, modifier = Modifier.weight(1f)
                            )
                            if (c.hasPin()) {
                                androidx.compose.material3.TextButton(onClick = { c.setPinFor = "" }) {
                                    Text(L("تغيير الرمز", "Change PIN"), color = Palette.GoldDeep, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    item { SectionTitle(L("التذكير اليومي", "Daily reminder")) }
                    item { ReminderRow() }

                    item { SectionTitle(L("التمييز والملخص", "Highlights and summary")) }
                    item {
                        SettingSwitch(
                            L("تفعيل الملخصات", "Enable summaries"),
                            L("أوقفه لإخفاء زر الملخصات وعدم إضافة التمييز إلى الملخص", "Turn off to hide the summaries button and stop adding highlights to the summary"),
                            UiPrefs.summariesOn
                        ) { UiPrefs.changeSummariesOn(!UiPrefs.summariesOn) }
                    }
                    item {
                        SettingSwitch(L("التقاط النص", "Text snap"), L("يلتصق الهايلايتر بالكلمات ويُضاف نصها إلى الملخص", "The highlighter snaps to words and adds their text to the summary"), c.highlightSnap) {
                            c.toggleHighlightSnap()
                        }
                    }
                    item {
                        SettingSwitch(
                            L("إضافة التمييز إلى الملخص", "Add highlights to summary"),
                            L("أوقفه لتظلّل على الصفحة فقط دون أن يظهر النص في الملخصات", "Turn off to highlight on the page only, without adding text to summaries"),
                            c.highlightToSummary
                        ) { c.toggleHighlightToSummary() }
                    }
                    item {
                        SettingSwitch(L("ترتيب نص الملخص", "Tidy summary text"), L("تنظيف المسافات وإكمال الجمل المقطوعة بين الأسطر", "Clean spacing and join sentences broken across lines"), c.summaryTidy) {
                            c.toggleSummaryTidy()
                        }
                    }
                    item {
                        SettingSwitch(L("ارسم وأبقِ: تحويل الرسم إلى شكل", "Draw and hold: make a shape"), L("ارسم ثم أبقِ القلم ضاغطًا دون حركة لحظة فيصبح الرسم شكلًا مرتبًا", "Draw, then hold the pen still for a moment to turn it into a neat shape"), PenPrefs.drawAndHold) {
                            PenPrefs.changeDrawAndHold(!PenPrefs.drawAndHold)
                        }
                    }

                    item { SectionTitle(L("المظهر", "Appearance")) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChoiceChip(L("كحلي", "Navy"), ThemeState.theme == AppTheme.NAVY) { c.setTheme(AppTheme.NAVY) }
                            ChoiceChip(L("فاتح", "Light"), ThemeState.theme == AppTheme.LIGHT) { c.setTheme(AppTheme.LIGHT) }
                        }
                    }
                    item { Text(L("حجم شريط الأدوات", "Toolbar size"), fontSize = 14.sp, color = Palette.TextSecondary) }
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(0.85f to L("صغير", "Small"), 1f to L("عادي", "Normal"), 1.15f to L("كبير", "Large"), 1.3f to L("أكبر", "Larger")).forEach { (v, label) ->
                                ChoiceChip(label, kotlin.math.abs(UiPrefs.toolbarScale - v) < 0.01f) { UiPrefs.changeToolbarScale(v) }
                            }
                        }
                    }
                    item { Text(L("حجم الكتابة في التطبيق", "App text size"), fontSize = 14.sp, color = Palette.TextSecondary) }
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(0.9f to L("صغير", "Small"), 1f to L("عادي", "Normal"), 1.15f to L("كبير", "Large"), 1.3f to L("أكبر", "Larger")).forEach { (v, label) ->
                                ChoiceChip(label, kotlin.math.abs(UiPrefs.textScale - v) < 0.01f) { UiPrefs.changeTextScale(v) }
                            }
                        }
                    }

                    item { SectionTitle(L("حول التطبيق", "About")) }
                    item {
                        NavRow(L("تراخيص المكتبات المفتوحة", "Open-source licenses"), L("المكوّنات المستخدمة في التطبيق وتراخيصها", "Components used in the app and their licenses"), R.drawable.ph_list) { licensesOpen = true }
                    }
                    item {
                        NavRow(L("سياسة الخصوصية", "Privacy policy"), L("ما يحفظه التطبيق وأين", "What the app stores and where"), R.drawable.ph_lock) { privacyOpen = true }
                    }
                    item {
                        NavRow(L("شروط الاستخدام", "Terms of use"), L("حدود المسؤولية واستخدام الملفات", "Liability and use of your files"), R.drawable.ph_note) { termsOpen = true }
                    }
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

/** نافذة «تخصيص شريط الأدوات»: الشكل، الأدوات الأساسية، والأدوات التي تُضاف أو تُخفى. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ToolbarCustomizeDialog(onDismiss: () -> Unit) {
    val c = AppController
    // «تعرّف على الأدوات» هنا، وزر المعلومات بجانب كل أداة يعرض شرحها في الأعلى
    val listState = rememberLazyListState()
    val pager = rememberPagerState(pageCount = { ToolCatalog.tour.size })
    val scope = rememberCoroutineScope()
    fun onInfo(id: String) {
        val i = ToolCatalog.tour.indexOfFirst { it.id == id }
        if (i >= 0) scope.launch {
            listState.animateScrollToItem(0)
            pager.animateScrollToPage(i)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.9f).widthIn(max = 720.dp).fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            color = Palette.Background
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        L("تخصيص شريط الأدوات", "Toolbar customization"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold,
                        fontSize = 20.sp, color = Palette.Navy, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) { PhIcon(R.drawable.ph_close, Palette.TextSecondary, 22.dp, L("إغلاق", "Close")) }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 22.dp, end = 22.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { SectionTitle(L("تعرّف على الأدوات", "Get to know the tools")) }
                    item { ToolTour(pager) }
                    item { SectionTitle(L("شكل شريط الأدوات", "Toolbar style")) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StyleOption(
                                Modifier.weight(1f), ToolbarStyle.PEN_BAR, L("شريط الأقلام", "Pen bar"),
                                L("زر قلم واحد؛ الضغط عليه يفتح شريطًا ثانيًا فيه كل الأقلام والهايلايتر والألوان والحجم.", "One pen button; tapping it opens a second bar with all pens, the highlighter, colors and size.")
                            )
                            StyleOption(
                                Modifier.weight(1f), ToolbarStyle.CLASSIC, L("الكلاسيكي", "Classic"),
                                L("القلم والهايلايتر زرّان منفصلان؛ نقرة ثانية على الأداة تفتح إعداداتها.", "Pen and highlighter are separate buttons; tapping a tool again opens its settings.")
                            )
                        }
                    }

                    item { SectionTitle(L("الأدوات الأساسية (ظاهرة دائمًا)", "Essential tools (always shown)")) }
                    ToolCatalog.essentials.forEach { t ->
                        item(key = "e_" + t.id) {
                            ToolRow(t, onInfo = { onInfo(t.id) }) {
                                Text(L("أساسية", "Essential"), fontSize = 12.sp, color = Palette.TextSecondary)
                            }
                        }
                    }

                    item { SectionTitle(L("أدوات اختيارية: أظهرها أو أخفها من الشريط", "Optional tools: show or hide them in the toolbar")) }
                    item {
                        androidx.compose.material3.TextButton(onClick = { c.resetToolbar() }) {
                            Text(L("استعادة الشريط الافتراضي", "Restore default toolbar"), color = Palette.GoldDeep, fontWeight = FontWeight.Bold)
                        }
                    }
                    ToolCatalog.optional.forEach { t ->
                        item(key = "o_" + t.id) {
                            ToolRow(t, onInfo = { onInfo(t.id) }) {
                                NavySwitch(c.toolVisible(t.id)) { c.setToolVisible(t.id, it) }
                            }
                        }
                    }

                }
            }
        }
    }
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
private fun SettingSwitch(title: String, desc: String, checked: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, Palette.Divider, shape)
            .clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
            Text(desc, fontSize = 12.sp, lineHeight = 18.sp, color = Palette.TextSecondary)
        }
        Spacer(Modifier.width(10.dp))
        NavySwitch(checked) { onToggle() }
    }
}

@Composable
private fun ToolRow(t: ToolInfo, onInfo: () -> Unit, trailing: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, Palette.Divider, shape)
            .clickable(onClick = onInfo).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Background),
            contentAlignment = Alignment.Center
        ) { PhIcon(t.icon, Palette.Navy, 22.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
            Text(t.benefit, fontSize = 12.sp, lineHeight = 18.sp, color = Palette.TextSecondary, maxLines = 2)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun StyleOption(modifier: Modifier, style: ToolbarStyle, title: String, desc: String) {
    val c = AppController
    val sel = c.toolbarStyle == style
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).background(Color.White)
            .border(if (sel) 2.dp else 1.dp, if (sel) Palette.Gold else Palette.Divider, shape)
            .clickable { c.updateToolbarStyle(style) }.padding(12.dp)
    ) {
        StylePreview(style)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(16.dp).clip(CircleShape)
                    .border(2.dp, if (sel) Palette.Gold else Palette.Divider, CircleShape)
                    .background(if (sel) Palette.Gold else Color.Transparent)
            )
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
        }
        Spacer(Modifier.height(4.dp))
        Text(desc, fontSize = 12.sp, lineHeight = 18.sp, color = Palette.TextSecondary)
    }
}

/** رسم متحرك صغير يوضح شكل الشريط. */
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
@Composable
private fun ToolTour(pager: androidx.compose.foundation.pager.PagerState) {
    val tools = ToolCatalog.tour
    // تقدّم تلقائي كل 5 ثوانٍ ما دام المستخدم لا يسحب
    LaunchedEffect(pager) {
        while (true) {
            delay(5000)
            if (!pager.isScrollInProgress) {
                pager.animateScrollToPage((pager.currentPage + 1) % tools.size)
            }
        }
    }
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, Palette.Divider, shape)
            .padding(vertical = 14.dp)
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { i ->
            val t = tools[i]
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ToolDemo(t.demo, Modifier.fillMaxWidth().height(170.dp))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PhIcon(t.icon, Palette.Navy, 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(t.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    t.benefit, fontSize = 14.sp, lineHeight = 22.sp, color = Palette.TextPrimary,
                    textAlign = TextAlign.Center, minLines = 2
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            tools.indices.forEach { k ->
                val cur = k == pager.currentPage
                Box(
                    Modifier.padding(horizontal = 2.dp).size(if (cur) 14.dp else 6.dp, 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (cur) Palette.Gold else Palette.Divider)
                )
            }
        }
    }
}

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
        androidx.compose.material3.AlertDialog(
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
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) Ai.importFile(uri) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, Palette.Divider, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            L("نموذج لغوي مجاني يعمل داخل الجهاز: يرتّب الملخصات ويشرح النصوص. نزّله مرة واحدة (يحتاج إنترنت للتنزيل فقط)، ثم يعمل دون إنترنت.",
                "A free language model running inside the device: organizes summaries and explains text. Download it once (internet needed only for the download); then it works offline."),
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
                        m.url != null -> androidx.compose.material3.TextButton(onClick = {
                            Ai.select(m.id)
                            Ai.download(m)
                        }) { Text(L("تنزيل", "Download"), color = Palette.Navy, fontWeight = FontWeight.Bold) }
                        else -> androidx.compose.material3.TextButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                            Text(L("اختيار ملف", "Choose file"), color = Palette.Navy)
                        }
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
