package com.hamishnotes.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/**
 * قراءة النص من صورة الصفحة (نص رقمي، أو نص داخل صورة، أو خط يد).
 * المحرك: Google ML Kit (يعمل على الجهاز دون إنترنت). يدعم اللاتينية فقط (الإنجليزية وأخواتها)،
 * ولا يدعم العربية.
 */
object OcrEngine {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /** بصمة بسيطة لكتابة المستخدم على الصفحة، لمعرفة هل نتيجة القراءة قديمة. */
    fun versionOf(strokes: List<Ink>): Int =
        // مع موضع كل خط: تحريك الكتابة بالتحديد الحر يعيد قراءتها في مكانها الجديد
        strokes.size * 1_000_003 + strokes.sumOf { it.pts.size } +
            strokes.sumOf { s -> s.pts.firstOrNull()?.let { ((it.x + it.y * 3f) * 4096f).toInt() } ?: 0 } * 7

    /** يدمج كتابة المستخدم (بدون الهايلايتر) فوق صورة الصفحة لتُقرأ معها. */
    fun compose(base: Bitmap, strokes: List<Ink>): Bitmap {
        val drawable = strokes.filter { it.tool != Tool.HIGHLIGHTER }
        if (drawable.isEmpty()) return base
        val bmp = base.copy(Bitmap.Config.ARGB_8888, true) ?: return base
        val canvas = Canvas(bmp)
        val w = bmp.width.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        for (s in drawable) {
            paint.color = s.color.toArgb()
            val pts = s.pts
            if (pts.isEmpty()) continue
            if (pts.size == 1) {
                paint.strokeWidth = max(3f, s.width * w)
                canvas.drawPoint(pts[0].x * w, pts[0].y * w, paint)
                continue
            }
            for (j in 1 until pts.size) {
                val a = pts[j - 1]
                val c = pts[j]
                val factor = if (s.tool == Tool.FOUNTAIN) 0.4f + c.p else 1f
                paint.strokeWidth = max(3f, s.width * w * factor)
                canvas.drawLine(a.x * w, a.y * w, c.x * w, c.y * w, paint)
            }
        }
        return bmp
    }

    /** الإحداثيات الناتجة نسبية لعرض الصورة، بنفس نظام الكتابة اليدوية. */
    suspend fun recognize(bmp: Bitmap): List<OcrWord> {
        val image = InputImage.fromBitmap(bmp, 0)
        val result = suspendCancellableCoroutine<Text> { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        val w = bmp.width.toFloat()
        val out = ArrayList<OcrWord>()
        var lineId = 0
        for (block in result.textBlocks) {
            for (line in block.lines) {
                val lineBox = line.boundingBox
                for (el in line.elements) {
                    val r = el.boundingBox ?: lineBox ?: continue
                    out.add(OcrWord(el.text, r.left / w, r.top / w, r.right / w, r.bottom / w, lineId))
                }
                lineId++
            }
        }
        return out
    }
}
