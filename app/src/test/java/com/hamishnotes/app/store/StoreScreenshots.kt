package com.hamishnotes.app.store

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.hamishnotes.app.AppController
import com.hamishnotes.app.AppTheme
import com.hamishnotes.app.DocSession
import com.hamishnotes.app.Ink
import com.hamishnotes.app.MindMapDialog
import com.hamishnotes.app.MyNotesApp
import com.hamishnotes.app.MyNotesTheme
import com.hamishnotes.app.Premium
import com.hamishnotes.app.Pt
import com.hamishnotes.app.SessionStatus
import com.hamishnotes.app.StatsDialog
import com.hamishnotes.app.StickyNote
import com.hamishnotes.app.StudyDialog
import com.hamishnotes.app.Tool
import com.hamishnotes.app.golden.AntibioticsCases
import com.hamishnotes.app.golden.Pipeline
import com.hamishnotes.app.golden.stroke
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.LocalDate

/**
 * Store listing screenshots drawn by the app itself (real Compose screens, real pixels), not mock-ups.
 * Off by default. Inputs: a folder of lecture PDFs (antibiotics.pdf from the test assets plus any others)
 * and optional handwriting files "notes@<pdf name>.txt" (page;TOOL;AARRGGBB;width;x,y,p x,y,p ...).
 *   ./gradlew :app:testDebugUnitTest --tests '*StoreScreenshots.tabletEn' -PstoreShots=true \
 *     -PstoreShotsInput=/path/to/pdfs -PstoreShotsDir=/path/to/out
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], shadows = [HostPdfRenderer::class, HostPdfPage::class])
class StoreScreenshots {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val input get() = File(System.getProperty("storeShotsInput").orEmpty())
    private val out get() = File(System.getProperty("storeShotsDir").orEmpty().ifEmpty { "build/store-shots" }).apply { mkdirs() }
    private val ctx: Context get() = rule.activity.applicationContext
    private val c = AppController

    /** Overlays the test opens on top of the app (dialogs the library or drawer would open). */
    private var overlay by mutableStateOf<String?>(null)
    private var prefix = ""

    @Before fun only() = assumeTrue(System.getProperty("storeShots") == "true")

    @Test @Config(qualifiers = "en-w1280dp-h800dp-land-xhdpi") fun tabletEn() = run("tablet-en", english = true, phone = false)
    @Test @Config(qualifiers = "ar-w1280dp-h800dp-land-xhdpi") fun tabletAr() = run("tablet-ar", english = false, phone = false)
    @Test @Config(qualifiers = "en-w800dp-h1280dp-port-xhdpi") fun tabletPortraitEn() = run("tabletp-en", english = true, phone = false, portrait = true)
    @Test @Config(qualifiers = "ar-w800dp-h1280dp-port-xhdpi") fun tabletPortraitAr() = run("tabletp-ar", english = false, phone = false, portrait = true)
    @Test @Config(qualifiers = "en-w412dp-h915dp-port-xxhdpi") fun phoneEn() = run("phone-en", english = true, phone = true)
    @Test @Config(qualifiers = "ar-w412dp-h915dp-port-xxhdpi") fun phoneAr() = run("phone-ar", english = false, phone = true)

    // ---------- helpers ----------

    private fun pump(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            rule.runOnUiThread { ShadowLooper.idleMainLooper() }
            rule.mainClock.advanceTimeBy(80)
            Thread.sleep(30)
        }
    }

    private fun waitFor(timeoutMs: Long = 60_000, what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!rule.runOnUiThread<Boolean> { cond() }) {
            check(System.currentTimeMillis() < end) { "timed out waiting for $what" }
            pump(150)
        }
    }

    /** Draws one window with Robolectric's hardware renderer (Compose layers need a GPU canvas). */
    private fun render(v: android.view.View): Bitmap {
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        val m = Class.forName("org.robolectric.shadows.HardwareRenderingScreenshot")
            .getDeclaredMethod("takeScreenshot", android.view.View::class.java, Bitmap::class.java)
        m.isAccessible = true
        m.invoke(null, v, b)
        return b
    }

    /** Every window on screen (activity, then dialogs/popups), drawn in order like the device compositor. */
    private fun shot(name: String) {
        pump(600)
        val bmp = rule.runOnUiThread<Bitmap> {
            val wmg = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
            @Suppress("UNCHECKED_CAST")
            val names = (wmg.javaClass.getMethod("getViewRootNames").invoke(wmg) as Array<String>)
            val views = names.map { wmg.javaClass.getMethod("getRootView", String::class.java).invoke(wmg, it) as android.view.View }
                .filter { it.isShown && it.width > 0 }
            val main = rule.activity.window.decorView
            val b = Bitmap.createBitmap(main.width, main.height, Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(b)
            (listOf(main) + views.filter { it !== main }).forEach { v ->
                val loc = IntArray(2)
                if (v !== main) {
                    // Robolectric does not place dialog windows: centre them and dim behind like the device
                    cv.drawColor(android.graphics.Color.argb(115, 0, 0, 0))
                    loc[0] = (main.width - v.width) / 2
                    loc[1] = (main.height - v.height) / 2
                }
                cv.drawBitmap(render(v), loc[0].toFloat(), loc[1].toFloat(), null)
            }
            b
        }
        File(out, "$prefix-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SHOT $prefix-$name ${bmp.width}x${bmp.height}")
    }

    private fun session(id: String): DocSession = c.tabs.first { it.id == id }

    private fun open(id: String): DocSession {
        rule.runOnUiThread { c.openDocument(id) }
        waitFor(60_000, "document $id") { c.tabs.firstOrNull { it.id == id }?.status is SessionStatus.Ready }
        pump(1500)
        return session(id)
    }

    private fun handwriting(s: DocSession, pdfName: String) {
        val f = File(input, "notes@$pdfName.txt")
        if (!f.exists()) return
        val now = System.currentTimeMillis()
        val strokes = f.readLines().filter { it.isNotBlank() }.map { line ->
            val (page, tool, color, width, pts) = line.split(';')
            page.toInt() to Ink(
                pts.trim().split(' ').map { p -> p.split(',').let { Pt(it[0].toFloat(), it[1].toFloat(), it[2].toFloat()) } },
                Color(color.toLong(16).toInt()), width.toFloat(), Tool.valueOf(tool), t = now
            )
        }
        rule.runOnUiThread {
            s.beginEdit()
            strokes.forEach { (p, ink) -> s.addInk(p, ink) }
            s.endEdit()
        }
    }

    private fun highlight(s: DocSession, page: Int, pts: List<Pt>, color: Color) {
        val old = s.marks[page]
        rule.runOnUiThread { s.snapHighlight(page, pts, color, Pipeline.WIDTH) }
        waitFor(60_000, "highlight on page $page") { s.marks[page] !== old }
    }

    private fun scrollTo(s: DocSession, page: Int, fraction: Float) {
        rule.runOnUiThread { runBlocking { s.listState.scrollToItem(page) } }
        pump(400)
        val item = rule.runOnUiThread<Int> { s.listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == page }?.size ?: 0 }
        rule.runOnUiThread { runBlocking { s.listState.scrollToItem(page, (item * fraction).toInt()) } }
        pump(1500)
    }

    /** A mind map placed the way a student would: highlights as points, plus a few typed branches. */
    private fun buildMindMap(s: DocSession, english: Boolean) = rule.runOnUiThread {
        val items = com.hamishnotes.app.MindMap.allItems(s.allMarks(), com.hamishnotes.app.MapEdits()).filter { it.mark != null }
        val nodes = LinkedHashMap<String, com.hamishnotes.app.MapNodeEdit>()
        val texts = HashMap<String, String>()
        val free = LinkedHashMap<String, String>()
        fun branch(key: String, text: String, pos: Double) { free[key] = text; nodes[key] = com.hamishnotes.app.MapNodeEdit(com.hamishnotes.app.MapRole.BRANCH, null, pos) }
        fun point(key: String, parent: String, pos: Double, text: String? = null, isFree: Boolean = false) {
            if (isFree) free[key] = text!! else if (text != null) texts[key] = text
            nodes[key] = com.hamishnotes.app.MapNodeEdit(com.hamishnotes.app.MapRole.POINT, parent, pos)
        }
        branch("f:pk", if (english) "Pharmacokinetics" else "الحركية الدوائية", 1.0)
        branch("f:peng", "Penicillin G", 2.0)
        branch("f:uses", if (english) "Clinical uses" else "الاستخدامات", 3.0)
        branch("f:res", if (english) "Resistance" else "المقاومة", 4.0)
        val short = listOf("Oral absorption varies; food delays it", "No intrathecal Pen G — convulsions",
            "Doesn't cross BBB unless meninges inflamed", "Rapid renal excretion (tubular secretion)")
        val yellow = items.filter { !it.text.startsWith("1.4") && !it.text.startsWith("60") }
        yellow.take(4).forEachIndexed { i, it -> point(it.key, "f:pk", i + 1.0, short[i]) }
        items.firstOrNull { it.text.startsWith("60") }?.let { point(it.key, "f:peng", 1.0, "60–70 % destroyed by gastric acid") }
        point("f:half", "f:peng", 2.0, "t½ ≈ 30 min", isFree = true)
        point("f:ivim", "f:peng", 3.0, "Give IV or IM", isFree = true)
        listOf("Strep throat", "Syphilis (DOC)", "Meningitis", "Gas gangrene").forEachIndexed { i, t -> point("f:u$i", "f:uses", i + 1.0, t, isFree = true) }
        listOf("β-lactamases", "PBP2a in MRSA", "Porin loss", "Efflux pumps").forEachIndexed { i, t -> point("f:r$i", "f:res", i + 1.0, t, isFree = true) }
        s.summaryEdits = s.summaryEdits.copy(map = com.hamishnotes.app.MapEdits(nodes = nodes, texts = texts, free = free))
    }

    // ---------- the scenes ----------

    private var pageFraction = 0.3f

    private fun run(tag: String, english: Boolean, phone: Boolean, portrait: Boolean = phone) {
        prefix = tag
        pageFraction = if (portrait) 0.18f else 0.37f
        ctx.getSharedPreferences("mynotes_session", Context.MODE_PRIVATE).edit()
            .putBoolean("login_seen", true).putBoolean("lang_en", english).putString("theme", "LIGHT")
            .putBoolean("resume_last", false).commit()
        rule.runOnUiThread {
            c.initialize(ctx)
            Premium.init(ctx)
            Premium.activateTest()
        }
        rule.setContent {
            MyNotesTheme {
                MyNotesApp()
                val s = c.tabs.firstOrNull { it.id == c.activeId }
                when (overlay) {
                    "stats" -> StatsDialog(onDismiss = {})
                    "cards" -> if (s != null) StudyDialog(s, s.customCards, onDismiss = {})
                    "map" -> if (s != null) MindMapDialog(s, onJump = {}, onDismiss = {})
                }
            }
        }
        waitFor(30_000, "library") { c.libraryLoaded }

        // ---- library: real lecture PDFs imported through the app's own repository ----
        val pdfs = input.listFiles { f -> f.name.endsWith(".pdf") }!!.sortedBy { it.name }
        val names = mapOf(
            "antibiotics" to "Antibiotics — Exam Summary",
            "Cardiology - Heart Failure" to "Heart Failure — Lecture 4",
            "Physiology - Renal" to "Renal Physiology",
            "Microbiology - Bacteria" to "Bacterial Structure",
            "Anatomy - Upper Limb" to "The Upper Limb",
            "Organic Chemistry - Reactions" to "Substitution & Elimination",
            "Calculus II - Series" to "Infinite Series"
        )
        val subject = mapOf(
            "antibiotics" to ("Pharmacology" to "علم الأدوية"),
            "Microbiology - Bacteria" to ("Pharmacology" to "علم الأدوية"),
            "Cardiology - Heart Failure" to ("Cardiology" to "أمراض القلب"),
            "Physiology - Renal" to ("Physiology" to "علم وظائف الأعضاء"),
            "Anatomy - Upper Limb" to ("Anatomy" to "التشريح"),
            "Organic Chemistry - Reactions" to ("Chemistry" to "الكيمياء"),
            "Calculus II - Series" to ("Maths" to "الرياضيات")
        )
        val order = listOf("antibiotics", "Cardiology - Heart Failure", "Physiology - Renal", "Microbiology - Bacteria",
            "Anatomy - Upper Limb", "Organic Chemistry - Reactions", "Calculus II - Series")
        val covers = mapOf(
            "antibiotics" to 0xFF2F4B7C, "Cardiology - Heart Failure" to 0xFF9C3D3D, "Physiology - Renal" to 0xFF2F7A78,
            "Microbiology - Bacteria" to 0xFFB8762E, "Anatomy - Upper Limb" to 0xFF6B4E8C,
            "Organic Chemistry - Reactions" to 0xFF3F7D4E, "Calculus II - Series" to 0xFF3D4A5C
        )
        rule.runOnUiThread {
            order.mapNotNull { subject[it] }.map { if (english) it.first else it.second }.distinct()
                .forEach { if (it !in c.folders) c.addFolder(it) }
        }
        val ids = HashMap<String, String>()
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        for (f in pdfs.sortedBy { order.indexOf(it.nameWithoutExtension).let { i -> if (i < 0) 99 else i } }.reversed()) {
            val key = f.nameWithoutExtension
            val e = c.repo.importFile(Uri.fromFile(f))
            val rank = order.indexOf(key).coerceAtLeast(0)
            val folder = subject[key]?.let { if (english) it.first else it.second } ?: ""
            val entry = e.copy(
                name = names[key] ?: key,
                folder = folder,
                addedAt = now - (rank + 2) * 26 * hour,
                lastOpened = if (rank < 4) now - (rank + 1) * 5 * hour else 0L,
                readMs = (90 - rank * 11).coerceAtLeast(8) * 60_000L,
                favorite = rank == 0 || rank == 2,
                lastPage = if (rank == 0) 4 else 1,
                coverColor = (covers[key] ?: 0L).toInt()
            )
            ids[key] = entry.id
            rule.runOnUiThread {
                if (folder.isNotEmpty() && folder !in c.folders) c.addFolder(folder)
                c.library.add(0, entry)
            }
        }
        rule.runOnUiThread {
            val pharm = if (english) "Pharmacology" else "علم الأدوية"
            val cardio = if (english) "Cardiology" else "أمراض القلب"
            c.setExamDate(pharm, LocalDate.now().plusDays(12).toEpochDay())
            c.setExamDate(cardio, LocalDate.now().plusDays(26).toEpochDay())
            // two weeks of study time for the stats screen and the goal ring
            val minutes = listOf(35, 62, 48, 0, 75, 90, 54, 41, 66, 80, 0, 58, 72, 47)
            minutes.forEachIndexed { i, m ->
                val day = LocalDate.now().minusDays((minutes.size - i).toLong()).toString()
                if (m > 0) {
                    c.stats[day] = m * 60_000L
                    c.stats["$day|${ids["antibiotics"]}"] = m * 36_000L
                    c.stats["$day|${ids["Cardiology - Heart Failure"]}"] = m * 24_000L
                }
            }
            c.stats[LocalDate.now().toString()] = 46 * 60_000L
        }
        pump(2500)
        shot("library")

        // ---- reading and writing on a real lecture page ----
        val anti = ids.getValue("antibiotics")
        val s = open(anti)
        // the page's text words, exactly as the app's text layer reads them (golden fixture); handwriting comes after
        val words = com.hamishnotes.app.golden.PdfFixture.load()
        rule.runOnUiThread {
            val ver = (com.hamishnotes.app.OcrEngine.versionOf(emptyList()) * 31 + 3) * 4 + com.hamishnotes.app.PenPrefs.hwLang
            s.ocr[4] = com.hamishnotes.app.OcrPage(words.getValue(4), ver)
        }
        for (st in AntibioticsCases.headingAndBullets.strokes) highlight(s, st.page, st.pts, st.color)
        highlight(s, 4, stroke(0.803f, 0.075f, 0.318f), Pipeline.GREEN)
        handwriting(s, "antibiotics")
        rule.runOnUiThread {
            s.selectedNoteId = null
            s.addNote(
                StickyNote(
                    "store-note", 4, 0.56f, 1.10f, 0.36f, 0.17f, 0xFFFFF0A8.toInt(), style = 1,
                    text = if (english) "Exam tip: Pen G + probenecid → higher blood levels" else "للامتحان: Pen G مع probenecid ← مستوى أعلى في الدم"
                )
            )
            s.addCustomCard("Drug of choice for syphilis?", "Benzathine penicillin G (IM)")
            s.addCustomCard("Why is Pen G given IV or IM?", "60–70 % is destroyed by gastric acid")
            s.addCustomCard("Half-life of penicillin G", "≈ 30 minutes")
        }
        rule.runOnUiThread { s.selectedNoteId = null; s.selectedTextId = null }
        buildMindMap(s, english)
        pump(6000) // let the "card added" message go away
        scrollTo(s, 4, pageFraction)
        shot("reader")

        // ---- highlights become a summary automatically ----
        rule.runOnUiThread { s.summaryOpen = true }
        pump(1500)
        shot("summary")
        rule.runOnUiThread { s.summaryOpen = false }

        // ---- mind map from the highlights ----
        rule.runOnUiThread { overlay = "map" }
        pump(2500)
        shot("mindmap")

        // ---- flashcards ----
        rule.runOnUiThread { overlay = "cards" }
        pump(2000)
        shot("flashcards")
        rule.runOnUiThread { overlay = null }
        pump(500)

        // ---- two documents side by side ----
        if (!phone) {
            val cardio = ids["Cardiology - Heart Failure"]
            if (cardio != null) {
                rule.runOnUiThread { c.openSplit(cardio) }
                waitFor(60_000, "split") { c.tabs.firstOrNull { it.id == cardio }?.status is SessionStatus.Ready }
                pump(1500)
                handwriting(session(cardio), "Cardiology - Heart Failure")
                rule.runOnUiThread { runBlocking { session(cardio).listState.scrollToItem(1) } }
                scrollTo(s, 4, pageFraction)
                shot("split")
                rule.runOnUiThread { c.closeSplit() }
                pump(800)
            }
        }

        // ---- dark mode ----
        rule.runOnUiThread { c.setTheme(AppTheme.NAVY) }
        scrollTo(s, 4, pageFraction)
        shot("reader-dark")
        rule.runOnUiThread { c.activate(null) }
        pump(1500)
        shot("library-dark")
        rule.runOnUiThread { c.setTheme(AppTheme.LIGHT) }
        pump(800)

        // ---- study stats ----
        rule.runOnUiThread { overlay = "stats" }
        pump(2000)
        shot("stats")
    }
}
