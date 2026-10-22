package com.mynotes.app

/**
 * ترتيب النص الملتقط داخل الجهاز، دون إنترنت ودون أي مكتبة إضافية.
 * لا يعيد صياغة المعنى؛ يصلح فقط ما تُفسده القراءة الضوئية وتقطيع الأسطر:
 *  - يدمج الأسطر المقطوعة والكلمات المقسومة بشرطة في نهاية السطر
 *  - يصلح المسافات حول علامات الترقيم (العربية واللاتينية)
 *  - يحذف الرموز الشاردة والكلمة المكررة مباشرة
 *  - يدمج المقاطع المتتالية التي تكمل بعضها، ثم يقسم النص إلى جمل واضحة
 *  - يبدأ كل جملة لاتينية بحرف كبير
 */
object TextTidy {
    private const val TERMINAL = ".!?؟…"
    private val abbreviations = setOf(
        "e.g", "i.e", "etc", "vs", "dr", "mr", "mrs", "ms", "prof", "fig", "no", "vol", "approx", "al"
    )

    /** ينظّف نصًا واحدًا ويعيده فقرة واحدة. */
    fun clean(raw: String): String {
        var s = raw
            .replace('\r', '\n')
            .replace('\t', ' ')
            .replace(' ', ' ')
            .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
        // كلمة مقسومة بشرطة في نهاية السطر: "exam-\nple" ← "example"
        s = s.replace(Regex("(\\p{L})[-‐‑]\\s*\\n\\s*(\\p{Ll})"), "$1$2")
        // دمج الأسطر
        s = s.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        // رموز شاردة تنتج عن القراءة الضوئية
        s = s.split(' ').filterNot { it.matches(Regex("[|_~^`¦•·]+")) }.joinToString(" ")
        // المسافات حول الترقيم
        s = s.replace(Regex("\\s+([,.;:!?،؛؟)\\]}…])"), "$1")
        s = s.replace(Regex("([(\\[{])\\s+"), "$1")
        s = s.replace(Regex("([,;!?،؛؟])(?=[\\p{L}\\p{N}])"), "$1 ")
        s = s.replace(Regex("(?<=\\p{L}{2})([.:])(?=\\p{L}{2})"), "$1 ")
        // تكرار الترقيم
        s = s.replace(Regex("([,،;؛])\\1+"), "$1")
        s = s.replace(Regex("\\.{4,}"), "...")
        // كلمة مكررة مباشرة (شائعة في القراءة الضوئية)
        s = s.replace(Regex("(?i)\\b(\\p{L}{3,})\\s+\\1\\b"), "$1")
        s = s.replace(Regex(" {2,}"), " ")
        return s.trim()
    }

    /** يقسم فقرة نظيفة إلى جمل، مع تجنّب القطع بعد الاختصارات والحروف المفردة. */
    fun sentences(text: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        val words = text.split(' ').filter { it.isNotEmpty() }
        for ((i, w) in words.withIndex()) {
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(w)
            val last = w.lastOrNull() ?: continue
            if (last !in TERMINAL) continue
            val bare = w.trimEnd { it in TERMINAL }.lowercase()
            val isAbbrev = bare in abbreviations || (bare.length == 1 && bare[0].isLetter())
            val next = words.getOrNull(i + 1)
            val nextStartsLower = next != null && next[0].isLowerCase()
            if (!isAbbrev && !nextStartsLower) {
                out.add(cur.toString())
                cur.setLength(0)
            }
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out.map { finishSentence(it) }.filter { it.isNotBlank() }
    }

    private fun finishSentence(s: String): String {
        var t = s.trim()
        if (t.isEmpty()) return t
        val first = t[0]
        if (first.isLowerCase()) t = first.uppercaseChar() + t.substring(1)
        // جملة طويلة بلا نهاية تُختم بنقطة (العناوين القصيرة تبقى كما هي)
        val wordCount = t.count { it == ' ' } + 1
        if (wordCount >= 5 && t.last().isLetterOrDigit()) t += "."
        return t
    }

    /**
     * يرتّب مجموعة مقاطع متتالية (مثل تمييزات صفحة واحدة بالترتيب):
     * يدمج المقطع مع ما قبله إن كان السابق لم ينتهِ بعلامة نهاية جملة والتالي يبدأ بحرف صغير
     * أو بكلمة ربط، ثم يقسم الناتج إلى جمل.
     */
    fun organize(fragments: List<String>): List<String> {
        val merged = ArrayList<String>()
        for (f in fragments) {
            val c = clean(f)
            if (c.isEmpty()) continue
            val prev = merged.lastOrNull()
            if (prev != null && continues(prev, c)) {
                merged[merged.lastIndex] = "$prev $c"
            } else {
                merged.add(c)
            }
        }
        return merged.flatMap { sentences(it) }
    }

    private val connectors = setOf(
        "and", "or", "but", "which", "that", "because", "so", "then", "with", "of", "to", "in", "for",
        "و", "أو", "لكن", "ثم", "التي", "الذي", "لأن", "حيث", "مع", "في", "من", "على"
    )

    private fun continues(prev: String, next: String): Boolean {
        val end = prev.lastOrNull() ?: return false
        if (end in TERMINAL) return false
        val firstWord = next.substringBefore(' ').lowercase()
        return next[0].isLowerCase() || firstWord in connectors || end == ',' || end == '،'
    }

    /** نص واحد مرتب كجمل، سطر لكل جملة. */
    fun tidy(raw: String): String = sentences(clean(raw)).joinToString("\n")
}
