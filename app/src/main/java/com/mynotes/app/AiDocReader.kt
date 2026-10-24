package com.mynotes.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MyNotes Pro: قراءة المستند كاملًا بالذكاء الاصطناعي في الخلفية، مثل القراءة الضوئية.
 * بالنموذج الذي على الجهاز فقط (مجاني دائمًا، ولا يُستعمل Claude/ChatGPT هنا حتى لو اختيرا).
 * كل صفحة تُقرأ مرة واحدة وتُحفظ («قراءة الصفحة بالذكاء»)، وتُستعمل في عناوين الملخص وفي الشرح.
 * تتنحى أثناء الكتابة بالقلم وأثناء أي مهمة ذكاء أخرى.
 */
object AiDocReader {
    private fun prefs() = AppController.appContext.getSharedPreferences("mynotes_ai", Context.MODE_PRIVATE)

    var enabled by mutableStateOf(prefs().getBoolean("bg_read", true))
        private set

    fun toggle(on: Boolean) {
        enabled = on
        prefs().edit().putBoolean("bg_read", on).apply()
        if (on) AppController.tabs.forEach { enqueue(it.id) } else worker?.cancel()
    }

    /** تقدّم القراءة لكل مستند: (المقروء، المجموع). */
    val progress = mutableStateMapOf<String, Pair<Int, Int>>()
    var currentDoc by mutableStateOf<String?>(null)
        private set

    private val queue = ArrayDeque<String>()
    private var worker: Job? = null

    /** النموذج الذي على الجهاز منزّل (القراءة في الخلفية لا تستعمل الإنترنت). */
    private fun localReady() = Ai.installed(Ai.selected)

    fun enqueue(docId: String) {
        if (!enabled || docId in queue || currentDoc == docId) return
        queue.addLast(docId)
        if (worker?.isActive == true) return
        worker = AppController.scope.launch {
            while (queue.isNotEmpty()) {
                val id = queue.removeFirst()
                try {
                    process(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                }
            }
        }
    }

    /** ينتظر حتى يتفرغ الذكاء والقلم. */
    private suspend fun waitIdle() {
        while (AiOrganizer.runningDoc != null || AiPageReader.runningKey != null || AiExplain.running ||
            System.currentTimeMillis() - AppController.lastInkAt < 8_000L
        ) delay(2_000)
    }

    private suspend fun process(id: String) {
        val entry = AppController.library.firstOrNull { it.id == id } ?: return
        if (entry.isNote || !localReady()) return
        val open = AppController.tabs.firstOrNull { it.id == id }
        val s = open ?: DocSession(entry).also { it.load(AppController.repo.file(entry), AppController.repo) }
        currentDoc = id
        try {
            if (s.status !is SessionStatus.Ready) return
            val n = s.pdf?.count ?: return
            var read = (0 until n).count { AiPageReader.result(id, it) != null }
            progress[id] = Pair(read, n)
            for (p in 0 until n) {
                if (!enabled || !localReady()) return
                if (AiPageReader.result(id, p) != null) continue
                // صفحة المستند المفتوح التي أمام المستخدم أولًا
                waitIdle()
                if (open != null && open.status is SessionStatus.Ready) {
                    val cur = open.listState.firstVisibleItemIndex
                    if (AiPageReader.result(id, cur) == null && cur != p && cur in 0 until n) {
                        readOne(s, cur)
                        read++
                        progress[id] = Pair(read, n)
                        waitIdle()
                    }
                }
                if (AiPageReader.result(id, p) != null) continue
                readOne(s, p)
                read++
                progress[id] = Pair(read, n)
            }
        } finally {
            currentDoc = null
            if (open == null) s.dispose()
        }
    }

    private suspend fun readOne(s: DocSession, page: Int) {
        try {
            // من النص المقروء فقط (أسرع)؛ الصورة فقط للصفحات التي بلا نص
            if (AiPageReader.read(s, page, useImage = false, local = true) == null) {
                AiPageReader.save(s.id, page, "\u200B")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
        }
    }

    /** سياق من المستند للشرح: قراءة الصفحة (والسابقة إن احتاج) بحدود حجم النموذج. */
    fun context(docId: String, page: Int, max: Int): String {
        val parts = listOfNotNull(
            AiPageReader.result(docId, page)?.takeIf { it.isNotBlank() },
            AiPageReader.result(docId, page - 1)?.takeIf { it.isNotBlank() && page > 0 }
        )
        val sb = StringBuilder()
        for (p in parts) {
            if (sb.length + p.length > max) {
                if (sb.isEmpty()) sb.append(p.take(max))
                break
            }
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append(p)
        }
        return sb.toString()
    }
}

/** حالة «اشرح» (لتتنحى القراءة في الخلفية أثناءه). */
object AiExplain {
    @Volatile
    var running = false
}
