package com.hamishnotes.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min

/** اختيار الورقة: القالب، لون الورقة، المقاس، والاتجاه. */
data class PaperChoice(val kind: String, val color: Int, val size: String, val landscape: Boolean) {
    /** نسبة العرض إلى الارتفاع. */
    fun ratio(): Float {
        val s = Templates.sizeOf(size)
        return if (landscape) s.h / s.w else s.w / s.h
    }

    /** مقاس الصفحة بالنقاط (لإنشاء ملف PDF). */
    fun points(): Pair<Int, Int> {
        val s = Templates.sizeOf(size)
        return if (landscape) Pair(s.h.toInt(), s.w.toInt()) else Pair(s.w.toInt(), s.h.toInt())
    }
}

/** قوالب الصفحات بأقسامها، مع لون الورقة والمقاس. */
object Templates {
    class Kind(val id: String, val label: () -> String)
    class Category(val title: () -> String, val kinds: List<Kind>)
    class Size(val id: String, val label: String, val w: Float, val h: Float)

    val categories: List<Category> = listOf(
        Category({ L("الأساسية", "Essentials") }, listOf(
            Kind("plain") { L("فارغة", "Blank") },
            Kind("dots") { L("منقّطة", "Dotted") },
            Kind("lined_n") { L("مسطّرة ضيقة", "Ruled narrow") },
            Kind("lined") { L("مسطّرة عريضة", "Ruled wide") },
            Kind("grid") { L("مربعات", "Squared") },
            Kind("grid_s") { L("مربعات صغيرة", "Small squares") }
        )),
        Category({ L("أوراق الكتابة", "Writing papers") }, listOf(
            Kind("cornell") { L("كورنيل", "Cornell") },
            Kind("legal") { L("بهامش", "Legal") },
            Kind("header") { L("بعنوان", "Single column") },
            Kind("columns2") { L("عمودان", "Two columns") },
            Kind("columns3") { L("ثلاثة أعمدة", "Three columns") }
        )),
        Category({ L("الدراسة والتخطيط", "Study & planner") }, listOf(
            Kind("table") { L("جدول", "Table") },
            Kind("qa") { L("سؤال وجواب", "Q & A") },
            Kind("todo") { L("مهام", "To-dos") },
            Kind("weekly") { L("أسبوعي", "Weekly") },
            Kind("monthly") { L("شهري", "Monthly") }
        )),
        Category({ L("موسيقى", "Music") }, listOf(
            Kind("music") { L("نوتة", "Music paper") }
        ))
    )

    val sizes: List<Size> = listOf(
        Size("a4", "A4", 595f, 842f),
        Size("a5", "A5", 420f, 595f),
        Size("b5", "B5", 499f, 709f),
        Size("letter", "Letter", 612f, 792f),
        Size("legal", "Legal", 612f, 1008f),
        Size("tabloid", "Tabloid", 792f, 1224f),
        Size("square", L("مربعة", "Square"), 700f, 700f)
    )

    fun sizeOf(id: String): Size = sizes.firstOrNull { it.id == id } ?: sizes[0]

    fun labelOf(kind: String): String =
        categories.flatMap { it.kinds }.firstOrNull { it.id == kind }?.label?.invoke() ?: L("فارغة", "Blank")

    /** للتوافق مع الشاشات القديمة: (المعرّف، الاسم). */
    val options: List<Pair<String, String>> get() = categories.flatMap { c -> c.kinds.map { it.id to it.label() } }

    /** ألوان الورق الجاهزة (ARGB، الاسم). */
    val paperColors: List<Pair<Int, String>> get() = listOf(
        0xFFFFFFFF.toInt() to L("أبيض", "White"),
        0xFFFFF8E1.toInt() to L("كريمي", "Cream"),
        0xFFFFF59D.toInt() to L("أصفر", "Yellow"),
        0xFFE3F2FD.toInt() to L("أزرق فاتح", "Light blue"),
        0xFFE8F5E9.toInt() to L("أخضر فاتح", "Light green"),
        0xFFFCE4EC.toInt() to L("وردي", "Pink"),
        0xFFECEFF1.toInt() to L("رمادي", "Gray"),
        0xFF1E232B.toInt() to L("داكن", "Dark")
    )

    // ---------- القالب الافتراضي ----------

    private fun prefs() = AppController.appContext.getSharedPreferences("mynotes_templates", Context.MODE_PRIVATE)

    fun defaultChoice(): PaperChoice = try {
        val p = prefs()
        PaperChoice(
            p.getString("kind", "lined") ?: "lined",
            p.getInt("color", paperColors[1].first),
            p.getString("size", "a4") ?: "a4",
            p.getBoolean("landscape", false)
        )
    } catch (t: Throwable) {
        PaperChoice("lined", paperColors[1].first, "a4", false)
    }

    fun saveDefault(c: PaperChoice) {
        prefs().edit().putString("kind", c.kind).putInt("color", c.color)
            .putString("size", c.size).putBoolean("landscape", c.landscape).apply()
    }

    /** يبني نص مصدر الصفحة الفارغة، مثل: blank:lined:FFFFF8E1 */
    fun src(template: String, bg: Int): String = "blank:$template:" + String.format("%08X", bg)

    /** يقرأ نص المصدر ويعيد (القالب، لون الورقة). */
    fun parse(src: String): Pair<String, Int> {
        val parts = src.removePrefix("blank:").split(":")
        val kind = parts.getOrNull(0) ?: "plain"
        val bg = parts.getOrNull(1)?.toLongOrNull(16)?.toInt() ?: 0xFFFFFFFF.toInt()
        return Pair(kind, bg)
    }

    /**
     * مواضع الأسطر الأفقية في قالب الصفحة (بنفس وحدات w و h)، لوضع النص على السطر.
     * فارغة للقوالب التي بلا أسطر كتابة (سادة، نقاط، ...).
     */
    fun lineYs(kind: String, w: Float, h: Float): List<Float> {
        val unit = min(w, h * 0.707f)
        val step = unit / 24f
        val m = step * 0.8f
        fun r(y0: Float, y1: Float, st: Float): List<Float> {
            val out = ArrayList<Float>()
            var y = y0
            while (y < y1) { out.add(y); y += st }
            return out
        }
        return when (kind) {
            "lined" -> r(step * 2.5f, h - step, step)
            "lined_n" -> r(step * 2.5f, h - step, step * 0.75f)
            "grid" -> r(step, h, step)
            "grid_s" -> r(step * 0.6f, h, step * 0.6f)
            "cornell" -> r(h * 0.085f + step * 1.2f, h * 0.80f - step * 0.3f, step) + r(h * 0.80f + step * 1.2f, h - step * 0.5f, step)
            "legal" -> r(step * 3f, h - step * 0.5f, step)
            "header" -> r(h * 0.10f + step * 1.2f, h - step * 0.5f, step)
            "columns2", "columns3" -> r(h * 0.08f + step * 1.2f, h - step * 0.5f, step)
            "table" -> r(h * 0.08f + step * 2.8f, h - m, step * 1.4f)
            else -> emptyList()
        }
    }

    // ---------- الرسم ----------

    private fun ruled(canvas: Canvas, x0: Float, x1: Float, y0: Float, y1: Float, step: Float, p: Paint) {
        var y = y0
        while (y < y1) {
            canvas.drawLine(x0, y, x1, y, p)
            y += step
        }
    }

    private fun grid(canvas: Canvas, w: Float, h: Float, step: Float, p: Paint) {
        var x = step
        while (x < w) {
            canvas.drawLine(x, 0f, x, h, p)
            x += step
        }
        var y = step
        while (y < h) {
            canvas.drawLine(0f, y, w, y, p)
            y += step
        }
    }

    fun draw(canvas: Canvas, w: Float, h: Float, kind: String, bg: Int = 0xFFFFFFFF.toInt()) {
        canvas.drawColor(bg)
        if (kind == "plain") return
        val lum = (0.299 * ((bg shr 16) and 0xFF) + 0.587 * ((bg shr 8) and 0xFF) + 0.114 * (bg and 0xFF)) / 255.0
        val dark = lum < 0.4
        val lineColor = if (dark) 0x55FFFFFF else 0xFFB9C5D8.toInt()
        val accent = if (dark) 0x88E57373.toInt() else 0xFFE8A0A0.toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = lineColor
        // المقياس من البعد الأصغر، لتبقى الأسطر مناسبة في الاتجاه الأفقي أيضًا
        val unit = min(w, h * 0.707f)
        paint.strokeWidth = max(1f, unit / 650f)
        val strong = Paint(paint).apply { strokeWidth = max(2f, unit / 330f) }
        val step = unit / 24f
        val m = step * 0.8f
        when (kind) {
            "lined" -> ruled(canvas, step, w - step, step * 2.5f, h - step, step, paint)
            "lined_n" -> ruled(canvas, step, w - step, step * 2.5f, h - step, step * 0.75f, paint)
            "grid" -> grid(canvas, w, h, step, paint)
            "grid_s" -> grid(canvas, w, h, step * 0.6f, paint)
            "dots" -> {
                paint.style = Paint.Style.FILL
                val r = max(1.5f, unit / 420f)
                var y = step
                while (y < h) {
                    var x = step
                    while (x < w) {
                        canvas.drawCircle(x, y, r, paint)
                        x += step
                    }
                    y += step
                }
            }
            // قالب كورنيل: عمود الكلمات المفتاحية، مساحة الشرح بأسطر، وخلاصة في الأسفل
            "cornell" -> {
                val top = h * 0.085f
                val cueX = w * 0.30f
                val sumY = h * 0.80f
                canvas.drawLine(0f, top, w, top, strong)
                canvas.drawLine(cueX, top, cueX, sumY, strong)
                canvas.drawLine(0f, sumY, w, sumY, strong)
                ruled(canvas, cueX + step * 0.5f, w - step * 0.6f, top + step * 1.2f, sumY - step * 0.3f, step, paint)
                ruled(canvas, step * 0.6f, w - step * 0.6f, sumY + step * 1.2f, h - step * 0.5f, step, paint)
            }
            // ورقة بهامش: أسطر مع خط هامش ملون
            "legal" -> {
                ruled(canvas, 0f, w, step * 3f, h - step * 0.5f, step, paint)
                val mp = Paint(strong).apply { color = accent }
                canvas.drawLine(w * 0.12f, 0f, w * 0.12f, h, mp)
            }
            // عنوان في الأعلى ثم أسطر
            "header" -> {
                val top = h * 0.10f
                canvas.drawLine(m, top, w - m, top, strong)
                ruled(canvas, m, w - m, top + step * 1.2f, h - step * 0.5f, step, paint)
            }
            "columns2", "columns3" -> {
                val n = if (kind == "columns2") 2 else 3
                val top = h * 0.08f
                canvas.drawLine(m, top, w - m, top, strong)
                val colW = (w - 2 * m) / n
                for (k in 0 until n) {
                    val x0 = m + k * colW
                    if (k > 0) canvas.drawLine(x0, top, x0, h - m, strong)
                    ruled(canvas, x0 + step * 0.4f, x0 + colW - step * 0.4f, top + step * 1.2f, h - step * 0.5f, step, paint)
                }
            }
            // جدول بأربعة أعمدة وصف عناوين
            "table" -> {
                val top = h * 0.08f
                val bottom = h - m
                val colW = (w - 2 * m) / 4f
                canvas.drawRect(m, top, w - m, bottom, Paint(strong).apply { style = Paint.Style.STROKE })
                for (k in 1 until 4) canvas.drawLine(m + k * colW, top, m + k * colW, bottom, paint)
                canvas.drawLine(m, top + step * 1.4f, w - m, top + step * 1.4f, strong)
                ruled(canvas, m, w - m, top + step * 2.8f, bottom, step * 1.4f, paint)
            }
            // سؤال وجواب: كتل متكررة، سطر للسؤال بخط قوي ثم أسطر للجواب
            "qa" -> {
                var y = h * 0.06f
                val block = step * 6f
                val qp = Paint(strong).apply { color = accent }
                while (y + block < h) {
                    canvas.drawLine(m, y + step, w - m, y + step, qp)
                    ruled(canvas, m + step, w - m, y + step * 2.2f, y + block - step * 0.4f, step, paint)
                    y += block
                }
            }
            // قائمة مهام: مربع اختيار في كل سطر
            "todo" -> {
                val top = h * 0.10f
                canvas.drawLine(m, top, w - m, top, strong)
                val box = Paint(paint).apply { style = Paint.Style.STROKE }
                var y = top + step * 1.4f
                while (y < h - step) {
                    canvas.drawLine(m, y, w - m, y, paint)
                    val bx = m + step * 0.2f
                    canvas.drawRect(bx, y - step * 0.8f, bx + step * 0.6f, y - step * 0.2f, box)
                    y += step * 1.2f
                }
            }
            // أسبوعي: سبعة أيام وخانة ملاحظات
            "weekly" -> {
                val top = h * 0.08f
                canvas.drawLine(m, top, w - m, top, strong)
                val rows = 4
                val cellW = (w - 2 * m) / 2f
                val cellH = (h - m - top) / rows
                for (r in 0 until rows) for (k in 0 until 2) {
                    val x0 = m + k * cellW
                    val y0 = top + r * cellH
                    canvas.drawRect(x0 + step * 0.2f, y0 + step * 0.2f, x0 + cellW - step * 0.2f, y0 + cellH - step * 0.2f,
                        Paint(paint).apply { style = Paint.Style.STROKE })
                    canvas.drawLine(x0 + step * 0.2f, y0 + step * 1.2f, x0 + cellW - step * 0.2f, y0 + step * 1.2f, strong)
                }
            }
            // شهري: شبكة 7 × 6
            "monthly" -> {
                val top = h * 0.12f
                canvas.drawLine(m, top, w - m, top, strong)
                val cellW = (w - 2 * m) / 7f
                val gridTop = top + step * 1.6f
                val cellH = min((h - m - gridTop) / 6f, cellW * 1.6f)
                for (r in 0..6) canvas.drawLine(m, gridTop + r * cellH, w - m, gridTop + r * cellH, paint)
                for (k in 0..7) canvas.drawLine(m + k * cellW, gridTop, m + k * cellW, gridTop + 6 * cellH, paint)
            }
            // نوتة موسيقية: مجموعات من خمسة أسطر
            "music" -> {
                val gap = step * 0.45f
                var y = h * 0.08f
                while (y + gap * 4 < h - m) {
                    for (k in 0 until 5) canvas.drawLine(m, y + k * gap, w - m, y + k * gap, paint)
                    y += gap * 4 + step * 2.2f
                }
            }
        }
    }
}
