package com.hamishnotes.app

import androidx.compose.ui.graphics.Color

/**
 * منطق إضافة تمييز ملتقط ودمجه مع التمييزات الموجودة في الصفحة، وترتيب التمييزات للملخص.
 * نُقل كما هو من DocSession (دون حالة Compose) حتى يمكن اختباره مباشرة.
 */
object MarkMerge {
    /** marks = تمييزات الصفحة بعد الإضافة، message = رسالة للمستخدم إن وُجدت. */
    class Result(val marks: List<TextMark>, val message: String?)

    /**
     * يضيف تمييزًا ملتقطًا. إن ظُلّل نفس النص مرة ثانية لا يُكرَّر في الملخص؛ بل يصبح «مهمًا»:
     *  - نفس المساحة تقريبًا  → التمييز القديم كله مهم (خط عريض وتظليل أقوى)
     *  - جزء داخل تمييز قديم → هذا الجزء وحده يُبرز داخل التمييز القديم
     *  - تمييز أكبر يحتوي القديم → يحلّ محلّه، ويبقى الجزء القديم بارزًا
     * يعيد null إن لم يكن تحت التمييز نص.
     */
    fun add(
        existing: List<TextMark>,
        page: Int,
        words: List<OcrWord>,
        idx: List<Int>,
        color: Color,
        now: Long,
        inSummary: Boolean = true,
        newId: () -> String
    ): Result? {
        // التمييز «بدون ملخص» لا يُدمج مع تمييزات الملخص (ولا العكس)
        val others = existing.filter { it.inSummary != inSummary }
        val same = existing.filter { it.inSummary == inSummary }
        val res = addSame(same, page, words, idx, color, now, inSummary, newId) ?: return null
        return Result(others + res.marks, res.message)
    }

    private fun addSame(
        existing: List<TextMark>,
        page: Int,
        words: List<OcrWord>,
        idx: List<Int>,
        color: Color,
        now: Long,
        inSummary: Boolean,
        newId: () -> String
    ): Result? {
        val text = TextLayout.buildText(words, idx).trim()
        if (text.isEmpty()) return null
        val rects = TextLayout.rectsFor(words, idx)
        val newArea = TextLayout.area(rects).coerceAtLeast(1e-9f)

        var next: List<TextMark>? = null
        var message: String? = null
        fun addStrong(list: List<MarkRect>, add: List<MarkRect>): List<MarkRect> =
            if (TextLayout.overlap(add, list) / newArea >= 0.6f) list else list + add

        // 1) نفس المساحة أو جزء داخل تمييز قديم
        for (old in existing) {
            val ov = TextLayout.overlap(rects, old.rects)
            if (ov <= 0f) continue
            val oldArea = TextLayout.area(old.rects).coerceAtLeast(1e-9f)
            val ofNew = ov / newArea
            val ofOld = ov / oldArea
            if (ofNew >= 0.6f && ofOld >= 0.6f) {
                next = existing.map { if (it.id == old.id) it.copy(level = 2, strongRects = it.rects) else it }
                message = L("ظُلّل النص مرة ثانية، فصار مهمًا في الملخص", "Highlighted twice, so it is now important in the summary")
                break
            }
            if (ofNew >= 0.6f) {
                val flat = TextTidy.clean(text.replace("# ", "").replace(" | ", " "))
                next = existing.map {
                    if (it.id == old.id) it.copy(
                        emphasis = (it.emphasis + flat).distinct(),
                        strongRects = if (it.level >= 2) it.strongRects else addStrong(it.strongRects, rects)
                    ) else it
                }
                message = L("أُبرز هذا الجزء كمهم في الملخص", "This part is now marked important in the summary")
                break
            }
        }
        // 2) تمييز أكبر يحتوي تمييزات قديمة: يحلّ محلّها كلها ويحتفظ بما كان مهمًا فيها
        if (next == null) {
            val contained = existing.filter { old ->
                val oldArea = TextLayout.area(old.rects).coerceAtLeast(1e-9f)
                TextLayout.overlap(rects, old.rects) / oldArea >= 0.6f
            }
            if (contained.isNotEmpty()) {
                var emphasis = emptyList<String>()
                var strong = emptyList<MarkRect>()
                for (old in contained) {
                    val oldFlat = TextTidy.clean(old.text.replace("# ", "").replace(" | ", " "))
                    emphasis = if (old.level >= 2) emphasis + oldFlat else emphasis + old.emphasis
                    strong = strong + (if (old.level >= 2) old.rects else old.strongRects)
                }
                val merged = TextMark(
                    id = newId(), page = page, rects = rects, color = color,
                    text = text, createdAt = contained.minOf { it.createdAt },
                    emphasis = emphasis.distinct(), strongRects = strong.distinct(), inSummary = inSummary
                )
                val ids = contained.map { it.id }.toSet()
                next = existing.filter { it.id !in ids } + merged
            }
        }
        // 3) تمييز ملاصق لتمييز سابق (جزء ثم الجزء الذي يليه): يُدمجان في تمييز واحد يُعاد بناء نصه
        //    من كلمات الصفحة بترتيبها الصحيح، بدل أن يظهرا مقطعين معكوسين في الملخص
        if (next == null) {
            // ترتيب القراءة لكل كلمات الصفحة، لمعرفة هل الكلمات متتالية فعلًا (لا مجرد قريبة)
            val order = TextLayout.visualRows(words, words.indices.toList()).flatten()
            val pos = IntArray(words.size).also { p -> order.forEachIndexed { k, i -> p[i] = k } }
            fun wordsIn(m: TextMark): List<Int> = words.indices.filter { i ->
                val w = words[i]
                val cx = (w.l + w.r) / 2f
                val cyw = (w.t + w.b) / 2f
                m.rects.any { r -> cx >= r.l && cx <= r.r && cyw >= r.t && cyw <= r.b }
            }
            val newMin = idx.minOf { pos[it] }
            val newMax = idx.maxOf { pos[it] }
            fun touches(old: TextMark): Boolean {
                if (old.color != color) return false
                val o = wordsIn(old)
                if (o.isEmpty()) return false
                val oMin = o.minOf { pos[it] }
                val oMax = o.maxOf { pos[it] }
                return oMax + 1 >= newMin && newMax + 1 >= oMin
            }
            val adjacent = existing.filter { touches(it) }
            if (adjacent.isNotEmpty()) {
                val union = HashSet<Int>(idx)
                for (old in adjacent) union.addAll(wordsIn(old))
                val first = adjacent.minByOrNull { it.createdAt }!!
                val merged = TextMark(
                    id = newId(),
                    page = page,
                    rects = TextLayout.rectsFor(words, union),
                    color = first.color,
                    text = TextLayout.buildText(words, union).trim(),
                    createdAt = first.createdAt,
                    // ما كان مهمًا قبل الدمج يبقى مهمًا كجزء بارز داخل التمييز الجديد
                    emphasis = adjacent.flatMap {
                        if (it.level >= 2) listOf(TextTidy.clean(it.text.replace("# ", "").replace(" | ", " ")))
                        else it.emphasis
                    }.distinct(),
                    strongRects = adjacent.flatMap { if (it.level >= 2) it.rects else it.strongRects }.distinct(),
                    inSummary = inSummary
                )
                val ids = adjacent.map { it.id }.toSet()
                next = existing.filter { it.id !in ids } + merged
            }
        }
        val list = next ?: (existing + TextMark(
            id = newId(),
            page = page,
            rects = rects,
            color = color,
            text = text,
            createdAt = now,
            inSummary = inSummary
        ))
        return Result(list, message)
    }

    /** كل التمييزات مرتبة حسب الصفحة ثم موضعها في الصفحة. */
    fun readingOrder(marks: Collection<List<TextMark>>): List<TextMark> =
        marks.flatten().sortedWith(
            compareBy<TextMark>({ it.page }, { m -> m.rects.minOfOrNull { it.t } ?: 0f }, { m -> m.rects.minOfOrNull { it.l } ?: 0f })
        )
}
