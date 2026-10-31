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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import java.util.UUID
import com.google.mlkit.vision.digitalink.recognition.Ink as MlInk

/**
 * تحويل الكتابة اليدوية المحددة (بالتحديد الحر) إلى نص: سطرًا سطرًا، بالعربية أو الإنجليزية.
 * يحتاج تنزيل حزمة اللغة مرة واحدة (نحو 20 ميغابايت)، ثم يعمل دون إنترنت.
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

    DisposableEffect(arabic) {
        var alive = true
        busy = true
        text = ""
        status = L("جارٍ تجهيز حزمة اللغة…", "Preparing language pack…")
        val id = DigitalInkRecognitionModelIdentifier.fromLanguageTag(if (arabic) "ar" else "en-US")
        if (id == null) {
            status = L("هذه اللغة غير مدعومة", "This language isn’t supported")
            busy = false
        } else {
            val model = DigitalInkRecognitionModel.builder(id).build()
            val recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
            RemoteModelManager.getInstance().download(model, DownloadConditions.Builder().build())
                .addOnSuccessListener {
                    if (!alive) return@addOnSuccessListener
                    status = L("جارٍ التعرّف على الكتابة…", "Recognizing handwriting…")
                    val groups = InkToText.lines(sel.strokes)
                    val results = arrayOfNulls<String>(groups.size)
                    var left = groups.size
                    if (left == 0) {
                        busy = false
                        status = ""
                    }
                    groups.forEachIndexed { i, g ->
                        val b = MlInk.builder()
                        g.forEach { st ->
                            val sb = MlInk.Stroke.builder()
                            st.pts.forEach { p -> sb.addPoint(MlInk.Point.create(p.x * 1000f, p.y * 1000f)) }
                            b.addStroke(sb.build())
                        }
                        recognizer.recognize(b.build())
                            .addOnSuccessListener { r -> results[i] = r.candidates.firstOrNull()?.text ?: "" }
                            .addOnFailureListener { results[i] = "" }
                            .addOnCompleteListener {
                                left--
                                if (left == 0 && alive) {
                                    text = results.filterNotNull().filter { it.isNotBlank() }.joinToString("\n")
                                    busy = false
                                    status = if (text.isBlank()) L("لم يُتعرّف على نص", "No text recognized") else ""
                                }
                            }
                    }
                }
                .addOnFailureListener {
                    if (!alive) return@addOnFailureListener
                    busy = false
                    status = L(
                        "تعذر تنزيل حزمة اللغة. تحتاج إنترنت أول مرة، وقد لا تعمل على أجهزة بدون خدمات Google.",
                        "Could not download the language pack. It needs internet the first time and may not work without Google services."
                    )
                }
            return@DisposableEffect onDispose {
                alive = false
                recognizer.close()
            }
        }
        onDispose { alive = false }
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
