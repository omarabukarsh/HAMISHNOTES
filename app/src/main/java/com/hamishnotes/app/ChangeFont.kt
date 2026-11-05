package com.hamishnotes.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

/** يجمع كلمات خط اليد المقروءة في نص: كل سطر في سطر، والكلمات بترتيب القراءة. */
internal fun handwritingText(words: List<OcrWord>): String =
    words.groupBy { it.line }.toSortedMap().values.joinToString("\n") { line -> line.joinToString(" ") { it.text } }

/**
 * «تغيير الخط»: تُقرأ الكتابة المحددة (عربي وإنجليزي)، ثم تختار خطًا من المكتبة،
 * فيحل نص بهذا الخط محل الكتابة في نفس المكان واللون. «رجوع إلى الكتابة اليدوية» يعيدها.
 */
@Composable
fun ChangeFontDialog(s: DocSession, onDismiss: () -> Unit) {
    val sel = s.lassoSel ?: return
    val strokes = remember(sel) { sel.strokes.filter { HandwritingLayout.eligible(it) } }
    var text by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var font by remember { mutableStateOf(FontStore.recent.firstOrNull() ?: "Patrick Hand") }
    var library by remember { mutableStateOf(false) }

    LaunchedEffect(sel) {
        if (!Premium.confirm()) {
            error = L("هذه الميزة من مزايا Premium", "This is a Premium feature")
            busy = false
            return@LaunchedEffect
        }
        val words = try {
            HandwritingReader.read(strokes, PenPrefs.hwLang, 0)
        } catch (t: Throwable) {
            null
        }
        busy = false
        when {
            words == null -> error = L(
                "حزمة قراءة خط اليد غير جاهزة. اتصل بالإنترنت مرة واحدة ثم أعد المحاولة.",
                "The handwriting pack isn’t ready. Connect to the internet once, then try again."
            )
            words.isEmpty() -> error = L("لم يُتعرّف على كتابة في التحديد", "No handwriting recognized in the selection")
            else -> {
                text = handwritingText(words)
                lines = words.map { it.line }.distinct().size.coerceAtLeast(1)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("تغيير الخط", "Change font"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, color = Palette.Navy) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(L("جارٍ قراءة الكتابة…", "Reading your handwriting…"), fontSize = 14.sp)
                    }
                    error != null -> Text(error!!, fontSize = 14.sp, color = Palette.TextSecondary)
                    else -> {
                        Text(L("النص المقروء (صحّحه إن لزم)", "Recognized text (fix it if needed)"), fontSize = 12.sp, color = Palette.TextSecondary)
                        OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp))
                        Spacer(Modifier.height(10.dp))
                        // المعاينة بالخط المختار
                        val ready = FontStore.state[font] == true || FontStore.isReady(font)
                        if (!ready) LaunchedEffect(font) { FontStore.ensure(font) }
                        Text(
                            text.ifBlank { "…" }, fontFamily = if (ready) FontStore.family(font) else null, fontSize = 24.sp,
                            lineHeight = 32.sp, color = sel.strokes.firstOrNull()?.color ?: Palette.Navy,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Ivory)
                                .border(1.dp, Palette.Divider, RoundedCornerShape(12.dp)).padding(12.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        // اختيار سريع: المستعملة مؤخرًا ثم المثبتة
                        val quick = (FontStore.recent + FontStore.catalog.filter { it.bundled != null }.map { it.name }).distinct().take(14)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            quick.forEach { n ->
                                val on = n == font
                                Text(
                                    n, fontFamily = FontStore.family(n), fontSize = 15.sp,
                                    color = if (on) androidx.compose.ui.graphics.Color.White else Palette.TextPrimary,
                                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                        .background(if (on) Palette.Navy else Palette.NavySoft)
                                        .clickable { font = n }.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                        TextButton(onClick = { library = true }) {
                            Text(L("مكتبة الخطوط كاملة (${FontStore.catalog.size})…", "Full font library (${FontStore.catalog.size})…"), color = Palette.GoldDeep, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && error == null && text.isNotBlank(), onClick = {
                val bb = s.lassoBounds()
                val size = if (bb != null) ((bb[3] - bb[1]) / lines * 0.6f).coerceIn(0.012f, 0.06f) else 0.024f
                val box = TextBox(
                    id = UUID.randomUUID().toString(), page = sel.page,
                    x = bb?.get(0) ?: 0.1f, y = bb?.get(1) ?: 0.1f,
                    w = ((bb?.let { it[2] - it[0] } ?: 0.5f) * 1.15f).coerceIn(0.15f, 0.95f),
                    text = text.trim(), color = sel.strokes.firstOrNull()?.color ?: Palette.Navy, size = size,
                    font = font, ink = sel.strokes
                )
                FontStore.markUsed(font)
                s.inkToTextBox(sel.page, sel.strokes, box)
                onDismiss()
            }) { Text(L("تطبيق", "Apply"), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = dialogClose(onDismiss)) { Text(L("إلغاء", "Cancel")) } }
    )
    if (library) FontLibraryDialog(text, font, onPick = { if (it.isNotEmpty()) font = it }, onDismiss = { library = false })
}
