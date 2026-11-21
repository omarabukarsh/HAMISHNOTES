package com.hamishnotes.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * إنشاء مستند واحد من عدة عناصر: ملاحظة من صور المعرض (كل صورة صفحة)،
 * أو عدة ملفات PDF تُجمع في مستند واحد (أو يُستورد كل ملف وحده).
 */
object ComposeImport {
    /** معرض الصور مفتوح لاختيار صور ملاحظة جديدة. */
    var photoPick by mutableStateOf(false)
    /** الصور المختارة تنتظر نافذة المراجعة. */
    var photos by mutableStateOf<List<Uri>?>(null)
    /** ملفات PDF مختارة (أكثر من ملف) تنتظر نافذة المراجعة. */
    var pdfs by mutableStateOf<List<Uri>?>(null)
}

/** معرض الصور ونافذتا المراجعة؛ توضع في شاشة المكتبة. */
@Composable
fun ComposeImportHost(initialFolder: String) {
    if (ComposeImport.photoPick) {
        PhotoGalleryDialog(
            multi = true,
            title = L("اختر صور الملاحظة", "Choose photos for the note"),
            onPicked = { if (it.isNotEmpty()) ComposeImport.photos = it },
            onDismiss = { ComposeImport.photoPick = false }
        )
    }
    ComposeImport.photos?.let { list ->
        ComposeReviewDialog(pdf = false, uris = list, initialFolder = initialFolder) { ComposeImport.photos = null }
    }
    ComposeImport.pdfs?.let { list ->
        ComposeReviewDialog(pdf = true, uris = list, initialFolder = initialFolder) { ComposeImport.pdfs = null }
    }
}

private class ItemInfo(
    val name: String,
    /** عدد صفحات الملف (0 = لم يُقرأ بعد، -1 = تعذرت قراءته). للصور دائمًا 1. */
    val pages: Int,
    val size: Long,
    val thumb: ImageBitmap?
)

/**
 * نافذة المراجعة بشكل نافذة الإعدادات وحركتها: الاسم، والعناصر بترتيبها (تحريك وحذف)، والمادة.
 * pdf = ملفات PDF (مع اختيار «في مستند واحد» أو «كل ملف وحده»)، وإلا صور.
 */
@Composable
private fun ComposeReviewDialog(pdf: Boolean, uris: List<Uri>, initialFolder: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val c = AppController
    val items = remember { mutableStateListOf<Uri>().apply { addAll(uris.distinct()) } }
    val info = remember { mutableStateMapOf<Uri, ItemInfo>() }
    var combine by remember { mutableStateOf(true) }
    var folder by remember { mutableStateOf(initialFolder.takeIf { it in c.folders }.orEmpty()) }
    val dateName = remember {
        SimpleDateFormat("d MMMM", Locale(if (I18n.english) "en" else "ar")).format(Date())
    }
    var name by remember { mutableStateOf(if (pdf) "" else L("صور $dateName", "Photos $dateName")) }
    var nameEdited by remember { mutableStateOf(false) }
    // الاسم المقترح لملفات PDF: اسم أول ملف بالترتيب الحالي، ما لم يكتب المستخدم اسمًا
    val firstName = items.firstOrNull()?.let { info[it]?.name }
    androidx.compose.runtime.LaunchedEffect(firstName) {
        if (pdf && !nameEdited && firstName != null) name = firstName
    }

    val totalPages = items.sumOf { (info[it]?.pages ?: 0).coerceAtLeast(0) }
    val merge = !pdf || combine

    OriginDialog(onDismiss = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) { close ->
        Surface(
            Modifier.fillMaxWidth(0.94f).widthIn(max = 640.dp).fillMaxHeight(0.9f),
            shape = RoundedCornerShape(28.dp),
            color = Lib.side,
            border = androidx.compose.foundation.BorderStroke(1.dp, Lib.line)
        ) {
            Column(Modifier.fillMaxSize()) {
                // العنوان وزر الإغلاق كما في نوافذ المكتبة
                Row(
                    Modifier.fillMaxWidth().padding(start = 28.dp, end = 20.dp, top = 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (pdf) L("استيراد ملفات PDF", "Import PDF files") else L("ملاحظة من الصور", "Note from photos"),
                        fontFamily = Fonts.Display, fontWeight = FontWeight.Bold,
                        fontSize = 26.sp, color = Lib.text, maxLines = 1, modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    IconCircleButton(R.drawable.ph_close, L("إغلاق", "Close"), onClick = close)
                }
                Text(
                    if (pdf) L("اخترت ${items.size} من الملفات. اجمعها في مستند واحد، أو استورد كل ملف وحده.", "You picked ${items.size} files. Combine them into one document, or import each one on its own.")
                    else L("كل صورة تصبح صفحة على مقاسها، بالترتيب الظاهر هنا.", "Each photo becomes a page in its own shape, in the order shown here."),
                    fontSize = 14.sp, lineHeight = 20.sp, color = Lib.muted,
                    modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 6.dp)
                )

                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (pdf) item(key = "mode") {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            ReviewSegmented(
                                listOf(L("في مستند واحد", "One document"), L("كل ملف وحده", "Each file separately")),
                                if (combine) 0 else 1
                            ) { combine = it == 0 }
                        }
                    }
                    item(key = "name") {
                        AnimatedVisibility(visible = merge, enter = Motion.barEnter, exit = Motion.barExit) {
                            Column {
                                ReviewLabel(L("الاسم", "Name"))
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it; nameEdited = true },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Lib.accent,
                                        unfocusedBorderColor = Lib.line,
                                        focusedContainerColor = Lib.card,
                                        unfocusedContainerColor = Lib.card,
                                        focusedTextColor = Lib.text,
                                        unfocusedTextColor = Lib.text,
                                        cursorColor = Lib.accentDeep,
                                        selectionColors = androidx.compose.foundation.text.selection.TextSelectionColors(Lib.accentDeep, Lib.accent.copy(alpha = 0.4f))
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    item(key = "listLabel") {
                        ReviewLabel(
                            when {
                                !pdf -> L("الصفحات بالترتيب (${items.size})", "Pages in order (${items.size})")
                                merge && totalPages > 0 -> L("الملفات بالترتيب (${totalPages} صفحة)", "Files in order (${totalPages} pages)")
                                merge -> L("الملفات بالترتيب", "Files in order")
                                else -> L("الملفات", "Files")
                            }
                        )
                    }
                    itemsIndexed(items, key = { _, u -> u.toString() }) { i, u ->
                        ReviewRow(
                            pdf = pdf, uri = u, index = i, count = items.size, showOrder = merge,
                            info = info[u],
                            onInfo = { info[u] = it },
                            onUp = { if (i > 0) items.add(i - 1, items.removeAt(i)) },
                            onDown = { if (i < items.size - 1) items.add(i + 1, items.removeAt(i)) },
                            onRemove = {
                                items.removeAt(i)
                                if (items.isEmpty()) close()
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                    item(key = "subject") {
                        Column {
                            ReviewLabel(L("المادة", "Subject"))
                            ReviewSubjects(folder) { folder = it }
                        }
                    }
                }

                // الزر الرئيسي في أسفل النافذة
                Box(Modifier.fillMaxWidth().height(1.dp).background(Lib.line))
                Row(
                    Modifier.fillMaxWidth().padding(start = 28.dp, end = 20.dp, top = 14.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val unreadable = pdf && items.isNotEmpty() && items.all { info[it]?.pages == -1 }
                    val enabled = items.isNotEmpty() && !unreadable && (!merge || name.isNotBlank())
                    Text(
                        when {
                            !pdf -> L("${items.size} صفحة", "${items.size} pages")
                            merge && totalPages > 0 -> L("$totalPages صفحة في مستند واحد", "$totalPages pages in one document")
                            merge -> ""
                            else -> L("${items.size} مستندات منفصلة", "${items.size} separate documents")
                        },
                        fontSize = 14.sp, color = Lib.muted, modifier = Modifier.weight(1f)
                    )
                    // زر رئيسي أصفر كزر «جديد» في المكتبة
                    Row(
                        Modifier.height(46.dp).pressScale(0.95f).alpha(if (enabled) 1f else 0.45f).clip(RoundedCornerShape(23.dp))
                            .background(Lib.accent)
                            .clickable(enabled = enabled) {
                                val list = items.toList()
                                val n = name.trim()
                                close()
                                when {
                                    !pdf -> c.createImageNote(list, n, folder)
                                    combine -> c.importMerged(list, n, folder)
                                    else -> c.importDocuments(list, folder)
                                }
                            }
                            .padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PhIcon(if (pdf) R.drawable.ph_export else R.drawable.ph_plus, OnAccent, 18.dp, modifier = if (pdf) Modifier.rotate(180f) else Modifier)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (pdf) L("استيراد", "Import") else L("إنشاء الملاحظة", "Create note"),
                            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OnAccent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewRow(
    pdf: Boolean,
    uri: Uri,
    index: Int,
    count: Int,
    showOrder: Boolean,
    info: ItemInfo?,
    onInfo: (ItemInfo) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier
) {
    val ctx = LocalContext.current
    if (info == null) {
        androidx.compose.runtime.LaunchedEffect(uri) {
            onInfo(
                if (pdf) withContext(Dispatchers.IO) { pdfInfo(ctx, uri) }
                else ItemInfo("", 1, 0L, PhotoLibrary.thumbnail(ctx, uri))
            )
        }
    }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(Lib.card).border(1.dp, Lib.line, shape)
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showOrder) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(Lib.accentSoft),
                contentAlignment = Alignment.Center
            ) { Text("${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabelGold) }
            Spacer(Modifier.width(10.dp))
        }
        // المصغّرة: الصورة بشكلها، أو أول صفحة من الملف
        val thumb = info?.thumb
        val tw = if (thumb != null && thumb.width > thumb.height) 52.dp else 40.dp
        val th = if (thumb != null && thumb.width > thumb.height) 38.dp else 52.dp
        Box(
            Modifier.size(tw, th).clip(RoundedCornerShape(6.dp)).background(Lib.bg)
                .border(1.dp, Lib.line, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else if (pdf) Text("PDF", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Lib.exam)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (pdf) info?.name ?: "…" else L("صفحة ${index + 1}", "Page ${index + 1}"),
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Lib.text,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            val sub = when {
                !pdf -> if (thumb == null) "" else if (thumb.width > thumb.height) L("صورة أفقية", "Landscape photo") else L("صورة عمودية", "Portrait photo")
                info == null -> L("قراءة الملف…", "Reading the file…")
                info.pages < 0 -> L("تعذرت قراءة الملف", "Could not read the file")
                else -> L("${info.pages} صفحة", "${info.pages} pages") +
                    if (info.size > 0) " · " + android.text.format.Formatter.formatShortFileSize(ctx, info.size) else ""
            }
            if (sub.isNotEmpty()) {
                Text(
                    sub, fontSize = 12.sp, lineHeight = 17.sp,
                    color = if (info != null && info.pages < 0) Lib.exam else Lib.muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        if (showOrder) {
            ReviewIconButton(R.drawable.ph_caret_up, L("تقديم", "Move up"), index > 0, onUp)
            ReviewIconButton(R.drawable.ph_caret_down, L("تأخير", "Move down"), index < count - 1, onDown)
        }
        ReviewIconButton(R.drawable.ph_trash, L("حذف", "Remove"), true, onRemove, Lib.exam)
    }
}

/** اسم ملف PDF وعدد صفحاته وحجمه ومصغّرة أول صفحة. خارج الخيط الرئيسي. */
private fun pdfInfo(ctx: Context, uri: Uri): ItemInfo {
    var name = L("مستند", "Document")
    var size = -1L
    try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cur ->
            if (cur.moveToFirst()) {
                cur.getString(0)?.let { n -> name = n.substringBeforeLast('.', n) }
                if (!cur.isNull(1)) size = cur.getLong(1)
            }
        }
    } catch (t: Throwable) {
    }
    var pages = -1
    var thumb: ImageBitmap? = null
    try {
        ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val r = PdfRenderer(pfd)
            try {
                pages = r.pageCount
                if (pages > 0) {
                    val p = r.openPage(0)
                    try {
                        val w = 120
                        val h = (w * p.height.toFloat() / p.width.coerceAtLeast(1)).toInt().coerceIn(1, 480)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        thumb = bmp.asImageBitmap()
                    } finally {
                        p.close()
                    }
                } else {
                    pages = -1
                }
            } finally {
                r.close()
            }
        }
    } catch (t: Throwable) {
        pages = -1
    }
    return ItemInfo(name, pages, size, thumb)
}

/** لون العناوين الصغيرة: ذهبي غامق في الفاتح، وأصفر المكتبة في الداكن كي يُقرأ على الأسود. */
private val LabelGold: Color get() = if (ThemeState.light) Lib.accentDeep else Lib.accent

/** النص على الأصفر (كزر «جديد»). */
private val OnAccent = Color(0xFF1E2A44)

@Composable
private fun ReviewLabel(text: String) {
    Text(
        text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabelGold,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun ReviewIconButton(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit, tint: Color = Lib.muted) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick).alpha(if (enabled) 1f else 0.3f),
        contentAlignment = Alignment.Center
    ) { PhIcon(icon, tint, 19.dp, description) }
}

/** اختيار من اثنين بألوان المكتبة: المختار أصفر كزر «جديد». */
@Composable
private fun ReviewSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val outer = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(outer).background(Lib.bg).border(1.dp, Lib.line, outer).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by androidx.compose.animation.animateColorAsState(if (on) Lib.accent else Color.Transparent, label = "segBg")
            Box(
                Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(12.dp)).background(bg)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (on) OnAccent else Lib.muted, maxLines = 1
                )
            }
        }
    }
}

/** المادة: «بلا مادة» ثم مواد المكتبة، بشكل أزرار المكتبة. */
@Composable
private fun ReviewSubjects(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReviewChip(L("بلا مادة", "No subject"), selected.isEmpty()) { onSelect("") }
        AppController.folders.toList().forEach { f -> ReviewChip(f, selected == f) { onSelect(f) } }
    }
}

@Composable
private fun ReviewChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.height(40.dp).clip(shape)
            .background(if (selected) Lib.accent else Lib.card)
            .border(1.dp, if (selected) Lib.accent else Lib.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = 14.sp, maxLines = 1,
            color = if (selected) OnAccent else Lib.text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
