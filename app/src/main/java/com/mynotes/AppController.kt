package com.mynotes.app

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * حالة التطبيق كاملة. كائن وحيد يعيش مع العملية، لذلك تبقى التبويبات وسجل التراجع
 * عند تدوير الشاشة. وتُحفظ قائمة التبويبات في SharedPreferences لاستعادتها بعد إغلاق التطبيق.
 */
object AppController {
    lateinit var repo: LibraryRepository
        private set
    private lateinit var prefs: SharedPreferences
    private var initialized = false

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    val library = mutableStateListOf<DocEntry>()
    val tabs = mutableStateListOf<DocSession>()

    /** null يعني أن المكتبة هي الشاشة الظاهرة. */
    var activeId by mutableStateOf<String?>(null)
        private set
    var libraryLoaded by mutableStateOf(false)
        private set
    var importing by mutableStateOf(false)
        private set

    var libraryFilter by mutableStateOf(LibraryFilter.RECENT)
    var librarySort by mutableStateOf(SortMode.LAST_OPENED)
    var libraryQuery by mutableStateOf("")

    var tool by mutableStateOf(Tool.PEN)
    val penColors = listOf(
        Color(0xFF000000),
        Color(0xFF1565C0),
        Color(0xFFC62828),
        Color(0xFF2E7D32),
        Color(0xFF6A1B9A)
    )
    var penColor by mutableStateOf(penColors[0])
    val penWidths = listOf(0.0018f, 0.003f, 0.0055f)
    var widthIndex by mutableStateOf(1)

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        repo = LibraryRepository(app)
        prefs = app.getSharedPreferences("mynotes_session", Context.MODE_PRIVATE)
        scope.launch {
            val list = withContext(Dispatchers.IO) { repo.loadIndex() }
            library.clear()
            library.addAll(list)
            val ids = (prefs.getString("open", "") ?: "").split(",").filter { it.isNotBlank() }
            val active = prefs.getString("active", null)
            ids.forEach { id ->
                library.firstOrNull { it.id == id }?.let { openSession(it, activate = false) }
            }
            activeId = active?.takeIf { a -> tabs.any { it.id == a } }
            libraryLoaded = true
        }
    }

    // ---------- التبويبات ----------

    fun openDocument(id: String) {
        if (tabs.any { it.id == id }) {
            activate(id)
            return
        }
        val entry = library.firstOrNull { it.id == id } ?: return
        openSession(entry, activate = true)
        updateEntry(id) { it.copy(lastOpened = System.currentTimeMillis()) }
        saveLibrary()
    }

    private fun openSession(entry: DocEntry, activate: Boolean) {
        val s = DocSession(entry)
        tabs.add(s)
        if (activate) activeId = entry.id
        scope.launch { s.load(repo.file(entry), repo) }
        saveSession()
    }

    fun activate(id: String?) {
        if (id != null && tabs.none { it.id == id }) return
        recordPage(activeId)
        activeId = id
        saveLibrary()
        saveSession()
    }

    fun closeTab(id: String) {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx < 0) return
        val s = tabs[idx]
        recordPage(id)
        tabs.removeAt(idx)
        s.dispose()
        if (activeId == id) {
            activeId = tabs.getOrNull(minOf(idx, tabs.lastIndex))?.id
        }
        saveLibrary()
        saveSession()
    }

    private fun recordPage(id: String?) {
        if (id == null) return
        val s = tabs.firstOrNull { it.id == id } ?: return
        if (s.status !is SessionStatus.Ready) return
        val page = s.listState.firstVisibleItemIndex
        updateEntry(id) { it.copy(lastPage = page) }
    }

    fun flush() {
        tabs.forEach { recordPage(it.id) }
        saveLibrary()
        saveSession()
    }

    // ---------- المكتبة ----------

    private fun updateEntry(id: String, transform: (DocEntry) -> DocEntry) {
        val i = library.indexOfFirst { it.id == id }
        if (i < 0) return
        library[i] = transform(library[i])
    }

    private fun saveLibrary() {
        if (!libraryLoaded) return
        val snap = library.toList()
        scope.launch { repo.saveIndex(snap) }
    }

    private fun saveSession() {
        if (!libraryLoaded && tabs.isEmpty()) return
        prefs.edit()
            .putString("open", tabs.joinToString(",") { it.id })
            .putString("active", activeId)
            .apply()
    }

    fun importDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            importing = true
            var ok = 0
            var failed = 0
            for (u in uris) {
                try {
                    val e = withContext(Dispatchers.IO) { repo.importFile(u) }
                    library.add(0, e)
                    ok++
                } catch (t: Throwable) {
                    failed++
                }
            }
            saveLibrary()
            importing = false
            val msg = when {
                failed == 0 && ok == 1 -> "تم استيراد المستند"
                failed == 0 -> "تم استيراد $ok مستندات"
                ok == 0 -> "تعذر الاستيراد. تأكد أن الملف PDF سليم."
                else -> "تم استيراد $ok وتعذر استيراد $failed"
            }
            messages.tryEmit(msg)
        }
    }

    fun toggleFavorite(id: String) {
        updateEntry(id) { it.copy(favorite = !it.favorite) }
        saveLibrary()
    }

    fun rename(id: String, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        updateEntry(id) { it.copy(name = clean) }
        tabs.firstOrNull { it.id == id }?.title = clean
        saveLibrary()
    }

    fun duplicate(entry: DocEntry) {
        scope.launch {
            try {
                val copy = withContext(Dispatchers.IO) { repo.duplicate(entry, " (نسخة)") }
                library.add(0, copy)
                saveLibrary()
                messages.tryEmit("تم إنشاء نسخة من المستند")
            } catch (t: Throwable) {
                messages.tryEmit("تعذر نسخ المستند")
            }
        }
    }

    fun delete(entry: DocEntry) {
        closeTab(entry.id)
        library.removeAll { it.id == entry.id }
        saveLibrary()
        scope.launch { withContext(Dispatchers.IO) { repo.delete(entry) } }
        messages.tryEmit("تم حذف المستند")
    }
}
