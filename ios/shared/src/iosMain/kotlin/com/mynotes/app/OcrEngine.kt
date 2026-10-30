package com.mynotes.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSMakeRange
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import kotlin.math.max

/**
 * قراءة النص من صورة الصفحة (نص رقمي، أو نص داخل صورة، أو خط يد).
 * المحرك في iPad: Apple Vision (على الجهاز دون إنترنت)، بالعربية والإنجليزية حيث يدعمها النظام.
 */
@OptIn(ExperimentalForeignApi::class)
object OcrEngine {

    /** بصمة بسيطة لكتابة المستخدم على الصفحة، لمعرفة هل نتيجة القراءة قديمة. */
    fun versionOf(strokes: List<Ink>): Int =
        strokes.size * 1_000_003 + strokes.sumOf { it.pts.size }

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
    suspend fun recognize(bmp: Bitmap): List<OcrWord> = recognize(bmp, arabicFirst = !I18n.english)

    /** نص الصورة كسطر واحد (لتحويل الكتابة اليدوية). */
    suspend fun recognizeText(bmp: Bitmap, arabicFirst: Boolean): String =
        recognize(bmp, arabicFirst).joinToString(" ") { it.text }

    suspend fun recognize(bmp: Bitmap, arabicFirst: Boolean): List<OcrWord> {
        val cg = IosPdf.cgImage(bmp) ?: return emptyList()
        val out = ArrayList<OcrWord>()
        try {
            val req = VNRecognizeTextRequest()
            req.recognitionLevel = VNRequestTextRecognitionLevelAccurate
            req.usesLanguageCorrection = true
            val supported = req.supportedRecognitionLanguagesAndReturnError(null)?.map { it.toString() } ?: emptyList()
            val order = if (arabicFirst) listOf("ar-SA", "ar", "en-US") else listOf("en-US", "ar-SA", "ar")
            val wanted = order.filter { it in supported }
            if (wanted.isNotEmpty()) req.recognitionLanguages = wanted
            val handler = VNImageRequestHandler(cGImage = cg, options = emptyMap<Any?, Any?>())
            if (!handler.performRequests(listOf(req), null)) return emptyList()
            val ratio = bmp.height.toFloat() / bmp.width.toFloat()
            var lineId = 0
            for (o in req.results ?: emptyList<Any?>()) {
                val obs = o as? VNRecognizedTextObservation ?: continue
                val cand = obs.topCandidates(1u).firstOrNull() as? VNRecognizedText ?: continue
                val text = cand.string
                // كل كلمة بموضعها (الصندوق بإحداثيات 0..1 والأصل أسفل اليسار)
                Regex("\\S+").findAll(text).forEach { m ->
                    val box = cand.boundingBoxForRange(NSMakeRange(m.range.first.toULong(), (m.range.last - m.range.first + 1).toULong()), null)
                        ?: obs
                    box.boundingBox.useContents {
                        val l = origin.x.toFloat()
                        val r = (origin.x + size.width).toFloat()
                        val t = (1.0 - origin.y - size.height).toFloat() * ratio
                        val b = (1.0 - origin.y).toFloat() * ratio
                        out.add(OcrWord(m.value, l, t, r, b, lineId))
                    }
                }
                lineId++
            }
        } catch (t: Throwable) {
        } finally {
            IosPdf.release(cg)
        }
        return out
    }
}
