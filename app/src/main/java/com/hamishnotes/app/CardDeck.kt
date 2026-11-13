package com.hamishnotes.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** بطاقة بلا ملف (من «جديد» أو من «كل البطاقات»)، ولها مادة اختيارية. */
data class LooseCard(val card: Card, val folder: String = "")

/**
 * بطاقة في مجموعة مراجعة تجمع عدة مصادر: مستند (docId) أو بلا ملف (docId = null).
 * folder = مادة المصدر؛ فارغ = بلا مادة.
 */
data class DeckCard(val card: Card, val docId: String?, val source: String, val folder: String, val starred: Boolean)

/**
 * كل البطاقات في التطبيق: بطاقات المستندات (داخل بيانات كل مستند) والبطاقات بلا ملف (هنا).
 * التعديل والحذف والنجمة تُحفظ في مكان البطاقة الأصلي.
 */
object CardDeck {
    private lateinit var sp: SharedPreferences
    val loose = mutableStateListOf<LooseCard>()
    var looseStarred by mutableStateOf<Set<String>>(emptySet()); private set
    /** نافذة مراجعة مفتوحة من المكتبة: ALL لكل البطاقات، وإلا اسم المادة. null = مغلقة. */
    var openDeck by mutableStateOf<String?>(null)
    const val ALL = "\u0000all"

    /** يزيد مع كل تغيير، فتعيد نوافذ المراجعة جمع البطاقات. */
    var version by mutableStateOf(0); private set

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("hamish_cards", Context.MODE_PRIVATE)
        loose.clear()
        try {
            val arr = JSONArray(sp.getString("loose", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val c = Card(o.getString("id"), o.optString("f"), o.optString("b"))
                if (c.front.isNotBlank()) loose.add(LooseCard(c, o.optString("folder", "")))
            }
            val st = JSONArray(sp.getString("starred", "[]") ?: "[]")
            looseStarred = (0 until st.length()).map { st.getString(it) }.toSet()
        } catch (e: Throwable) {
        }
    }

    private fun save() {
        if (!::sp.isInitialized) return
        val arr = JSONArray()
        loose.forEach { l ->
            arr.put(JSONObject().put("id", l.card.id).put("f", l.card.front).put("b", l.card.back).put("folder", l.folder))
        }
        sp.edit().putString("loose", arr.toString()).putString("starred", JSONArray(looseStarred.toList()).toString()).apply()
        version++
    }

    fun addLoose(front: String, back: String, folder: String): Boolean {
        val f = front.trim()
        val b = back.trim()
        if (f.isEmpty() || b.isEmpty()) return false
        loose.add(LooseCard(Card("l:" + UUID.randomUUID().toString(), f, b), folder))
        save()
        return true
    }

    /** المادة أُعيدت تسميتها أو حُذفت (new = "" للحذف). */
    fun onFolderRenamed(old: String, new: String) {
        var changed = false
        for (i in loose.indices) if (loose[i].folder == old) {
            loose[i] = loose[i].copy(folder = new)
            changed = true
        }
        if (changed) save()
    }

    /** يجمع البطاقات: folder = null للكل، وإلا بطاقات هذه المادة فقط (مستنداتها وبطاقاتها بلا ملف). */
    suspend fun collect(folder: String?): List<DeckCard> {
        val c = AppController
        val out = ArrayList<DeckCard>()
        val docs = c.library.toList().filter { (folder == null || it.folder == folder) && !it.isMap }
        for (e in docs) {
            // المستند المقفل لا تظهر بطاقاته حتى يُفتح قفله
            if (e.locked && e.id !in c.unlocked) continue
            val open = c.tabs.firstOrNull { it.id == e.id && it.status is SessionStatus.Ready }
            val (cards, starred) = if (open != null) open.customCards to open.starred
            else withContext(Dispatchers.IO) {
                try {
                    c.repo.loadDocData(e.id).let { it.cards to it.starred }
                } catch (t: Throwable) {
                    emptyList<Card>() to emptySet()
                }
            }
            cards.forEach { out.add(DeckCard(it, e.id, e.name, e.folder, it.id in starred)) }
        }
        loose.toList().filter { folder == null || it.folder == folder }.forEach {
            out.add(DeckCard(it.card, null, L("بلا ملف", "No file"), it.folder, it.card.id in looseStarred))
        }
        return out
    }

    /** يعدّل بيانات مستند غير مفتوح ويحفظها. */
    private suspend fun editDoc(docId: String, f: (DocData) -> DocData) {
        val c = AppController
        val open = c.tabs.firstOrNull { it.id == docId && it.status is SessionStatus.Ready }
        if (open != null) return
        // على خيط الكتابة (بعد أي حفظ منتظر). بيانات لم تُقرأ (تالفة) لا يُكتب فوقها أبدًا
        val d = c.repo.serialized { c.repo.loadDocDataOrNull(docId) }
        if (d == null) {
            c.say(MsgKind.ERROR, L("تعذّر تعديل البطاقة: افتح المستند أولًا", "Couldn’t change the card: open the document first"))
            return
        }
        c.repo.saveDocData(docId, f(d))
    }

    private fun DocData.with(cards: List<Card> = this.cards, starred: Set<String> = this.starred) =
        DocData(ink, images, marks, texts, edits, cards, starred, bookmarks, notes)

    private fun openSession(docId: String) =
        AppController.tabs.firstOrNull { it.id == docId && it.status is SessionStatus.Ready }

    suspend fun update(dc: DeckCard, front: String, back: String) {
        val f = front.trim()
        val b = back.trim()
        if (f.isEmpty() || b.isEmpty()) return
        val id = dc.docId
        if (id == null) {
            val i = loose.indexOfFirst { it.card.id == dc.card.id }
            if (i >= 0) loose[i] = loose[i].copy(card = loose[i].card.copy(front = f, back = b))
            save()
            return
        }
        openSession(id)?.let { it.updateCustomCard(dc.card.id, f, b); version++; return }
        editDoc(id) { d -> d.with(cards = d.cards.map { if (it.id == dc.card.id) it.copy(front = f, back = b) else it }) }
        version++
    }

    suspend fun delete(dc: DeckCard) {
        val id = dc.docId
        if (id == null) {
            loose.removeAll { it.card.id == dc.card.id }
            looseStarred = looseStarred - dc.card.id
            save()
            return
        }
        openSession(id)?.let { it.deleteCustomCard(dc.card.id); version++; return }
        editDoc(id) { d -> d.with(cards = d.cards.filter { it.id != dc.card.id }, starred = d.starred - dc.card.id) }
        version++
    }

    suspend fun toggleStar(dc: DeckCard) {
        val id = dc.docId
        if (id == null) {
            looseStarred = if (dc.card.id in looseStarred) looseStarred - dc.card.id else looseStarred + dc.card.id
            save()
            return
        }
        openSession(id)?.let { it.toggleStar(dc.card.id); version++; return }
        editDoc(id) { d -> d.with(starred = if (dc.card.id in d.starred) d.starred - dc.card.id else d.starred + dc.card.id) }
        version++
    }
}
