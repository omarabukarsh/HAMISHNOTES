package com.mynotes.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * تحويل الكتابة اليدوية المحددة (بالتحديد الحر) إلى نص: سطرًا سطرًا، بالعربية أو الإنجليزية.
 * في iPad: كل سطر يُرسم كصورة ويقرؤه Apple Vision (على الجهاز دون إنترنت).
 */
object InkToText {
    /** يقسم الخطوط إلى أسطر حسب موضعها الرأسي. */
    fun lines(strokes: List<Ink>): List<List<Ink>> {
        val sorted = strokes.filter { it.pts.isNotEmpty() }.sortedBy { s -> s.pts.sumOf { it.y.toDouble() } / s.pts.size }
        val out = ArrayList<MutableList<Ink>>()
        var top = 0f
        var bottom = 0f
        for (s in sorted) {
            val t = s.pts.minOf { it.y }
            val b = s.pts.maxOf { it.y }
            val cy = (t + b) / 2f
            if (out.isEmpty() || cy > bottom + (bottom - top) * 0.15f) {
                out.add(mutableListOf(s))
                top = t
                bottom = b
            } else {
                out.last().add(s)
                if (t < top) top = t
                if (b > bottom) bottom = b
            }
        }
        return out
    }

    /** يرسم سطرًا من الكتابة بخط أسود سميك على خلفية بيضاء (أوضح للقراءة). */
    fun render(line: List<Ink>): android.graphics.Bitmap? {
        val pts = line.flatMap { it.pts }
        if (pts.isEmpty()) return null
        val l = pts.minOf { it.x }; val r = pts.maxOf { it.x }
        val t = pts.minOf { it.y }; val b = pts.maxOf { it.y }
        val h = (b - t).coerceAtLeast(0.004f)
        val k = 160f / h // ارتفاع السطر نحو 160 بكسل
        val pad = 40f
        val w = ((r - l) * k + 2 * pad).toInt().coerceIn(64, 4000)
        val hh = (h * k + 2 * pad).toInt().coerceIn(64, 1200)
        val bmp = android.graphics.Bitmap.createBitmap(w, hh, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(android.graphics.Color.WHITE)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            strokeWidth = 7f
        }
        for (s in line) {
            if (s.pts.size == 1) {
                c.drawPoint((s.pts[0].x - l) * k + pad, (s.pts[0].y - t) * k + pad, p)
                continue
            }
            for (j in 1 until s.pts.size) {
                val a = s.pts[j - 1]; val e = s.pts[j]
                c.drawLine((a.x - l) * k + pad, (a.y - t) * k + pad, (e.x - l) * k + pad, (e.y - t) * k + pad, p)
            }
        }
        return bmp
    }
}

@Composable
fun InkToTextDialog(s: DocSession, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val sel = s.lassoSel ?: return
    var arabic by remember { mutableStateOf(!I18n.english) }
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(true) }
    var removeInk by remember { mutableStateOf(true) }

    LaunchedEffect(arabic) {
        busy = true
        text = ""
        status = L("جارٍ التعرّف على الكتابة…", "Recognizing handwriting…")
        val groups = InkToText.lines(sel.strokes)
        val result = withContext(Dispatchers.IO) {
            groups.mapNotNull { g ->
                val bmp = InkToText.render(g) ?: return@mapNotNull null
                try {
                    OcrEngine.recognizeText(bmp, arabic)
                } finally {
                    bmp.recycle()
                }
            }.filter { it.isNotBlank() }.joinToString("\n")
        }
        text = result
        busy = false
        status = if (text.isBlank()) L("لم يُتعرّف على نص", "No text recognized") else ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("تحويل الكتابة إلى نص", "Handwriting to text")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("العربية", arabic) { arabic = true }
                    ChoiceChip("English", !arabic) { arabic = false }
                }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(status, fontSize = 14.sp)
                    }
                } else {
                    if (status.isNotEmpty()) Text(status, fontSize = 13.sp, color = Palette.TextSecondary)
                    OutlinedTextField(
                        value = text, onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = removeInk, onCheckedChange = { removeInk = it })
                        Text(L("احذف الكتابة اليدوية بعد الإدراج", "Remove the handwriting after inserting"), fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(enabled = !busy && text.isNotBlank(), onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("MyNotes", text))
                    AppController.messages.tryEmit(L("تم النسخ", "Copied"))
                }) { Text(L("نسخ", "Copy")) }
                TextButton(enabled = !busy && text.isNotBlank(), onClick = {
                    val bb = s.lassoBounds()
                    val color = sel.strokes.firstOrNull()?.color ?: Palette.Navy
                    val lineCount = text.lines().size.coerceAtLeast(1)
                    val size = if (bb != null) ((bb[3] - bb[1]) / lineCount * 0.55f).coerceIn(0.012f, 0.05f) else 0.024f
                    if (removeInk) s.lassoDelete()
                    s.addTextBox(
                        TextBox(
                            id = UUID.randomUUID().toString(), page = sel.page,
                            x = bb?.get(0) ?: 0.1f, y = bb?.get(1) ?: 0.1f,
                            w = ((bb?.let { it[2] - it[0] } ?: 0.5f) * 1.15f).coerceIn(0.2f, 0.9f),
                            text = text.trim(), color = color, size = size
                        )
                    )
                    onDismiss()
                }) { Text(L("إدراج كنص", "Insert as text")) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("إلغاء", "Cancel")) } }
    )
}
