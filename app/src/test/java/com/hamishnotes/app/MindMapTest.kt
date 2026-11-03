package com.hamishnotes.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MindMapTest {
    private val yellow = Color(0xFFFFEB3B)

    private fun mark(id: String, text: String, top: Float) =
        TextMark(id, 0, listOf(MarkRect(0.1f, top, 0.9f, top + 0.02f)), yellow, text, 1L)

    private val marks = listOf(
        mark("a", "المناعة الفطرية: خط الدفاع الأول، وغير متخصصة", 0.1f),
        mark("b", "تشمل الجلد والأغشية المخاطية", 0.2f),
        mark("c", "المناعة المكتسبة: متخصصة ولها ذاكرة", 0.3f),
        mark("d", "الخلايا B تنتج الأجسام المضادة", 0.4f),
        mark("e", "الخلايا T المساعدة", 0.5f)
    )
    private val items = MindMap.items(marks)

    private fun build(e: MapEdits) = MindMap.build("T", marks, e)

    @Test
    fun nothingIsAutomatic() {
        assertTrue(build(MapEdits()).branches.isEmpty())
    }

    @Test
    fun placeBranchesPointsAndKeepFullText() {
        var e = MindMap.place(MapEdits(), items, listOf("a#0"), MapTarget(null, null))
        e = MindMap.place(e, items, listOf("c#0"), MapTarget(null, null))
        e = MindMap.place(e, items, listOf("b#0"), MapTarget("a#0", null))
        val m = build(e)
        assertEquals(listOf("a#0", "c#0"), m.branches.map { it.key })
        // النص كاملًا، بلا اختصار
        assertEquals("المناعة الفطرية: خط الدفاع الأول، وغير متخصصة", m.branches[0].text)
        assertEquals(listOf("b#0"), m.branches[0].children.map { it.key })
    }

    @Test
    fun severalTextsKeepTheirOrderAndInsertBefore() {
        var e = MindMap.place(MapEdits(), items, listOf("c#0"), MapTarget(null, null))
        e = MindMap.place(e, items, listOf("d#0", "e#0"), MapTarget("c#0", null))
        // b قبل e بالضبط
        e = MindMap.place(e, items, listOf("b#0"), MapTarget("c#0", "e#0"))
        assertEquals(listOf("d#0", "b#0", "e#0"), build(e).branches[0].children.map { it.key })
    }

    @Test
    fun moveUpDownAndSubPoint() {
        var e = MindMap.place(MapEdits(), items, listOf("c#0"), MapTarget(null, null))
        e = MindMap.place(e, items, listOf("d#0", "e#0"), MapTarget("c#0", null))
        e = MindMap.move(e, items, "e#0", -1)
        assertEquals(listOf("e#0", "d#0"), build(e).branches[0].children.map { it.key })
        e = MindMap.place(e, items, listOf("b#0"), MapTarget("d#0", null))
        assertEquals(listOf("b#0"), MindMap.find(build(e), "d#0")!!.children.map { it.key })
    }

    @Test
    fun movingABranchTakesItsPointsAndRemoveTakesSubtree() {
        var e = MindMap.place(MapEdits(), items, listOf("a#0", "c#0"), MapTarget(null, null))
        e = MindMap.place(e, items, listOf("d#0"), MapTarget("c#0", null))
        // الفرع c يصبح نقطة تحت a، ونقطته d تنتقل معه
        e = MindMap.place(e, items, listOf("c#0"), MapTarget("a#0", null))
        val m = build(e)
        assertEquals(listOf("a#0"), m.branches.map { it.key })
        assertEquals(listOf("d#0"), MindMap.find(m, "c#0")!!.children.map { it.key })
        val (after, n) = MindMap.remove(e, items, "c#0")
        assertEquals(2, n)
        assertNull(MindMap.find(build(after), "d#0"))
    }

    @Test
    fun orphanPointBecomesBranchWhenParentHighlightIsDeleted() {
        var e = MindMap.place(MapEdits(), items, listOf("a#0"), MapTarget(null, null))
        e = MindMap.place(e, items, listOf("b#0"), MapTarget("a#0", null))
        val without = MindMap.build("T", marks.filter { it.id != "a" }, e)
        assertEquals(listOf("b#0"), without.branches.map { it.key })
    }

    @Test
    fun mergeMovesPlacementToNewMark() {
        val e = MapEdits(nodes = mapOf("x#0" to MapNodeEdit(MapRole.POINT, "a#0", 1.0), "a#0" to MapNodeEdit(MapRole.BRANCH, null, 0.0)))
        val m = MindMap.migrate(e, listOf("x", "y"), "z")
        assertEquals(MapNodeEdit(MapRole.POINT, "a#0", 1.0), m.nodes["z#0"])
        assertNull(m.nodes["x#0"])
    }

    @Test
    fun branchesGoDeeperThanThreeLevels() {
        // a ← b ← c ← d ← e: خمسة مستويات بلا رفع لأي نص
        var e = MindMap.place(MapEdits(), items, listOf("a#0"), MapTarget(null, null))
        e = MindMap.place(e, items, listOf("b#0"), MapTarget("a#0", null))
        e = MindMap.place(e, items, listOf("c#0"), MapTarget("b#0", null))
        e = MindMap.place(e, items, listOf("d#0"), MapTarget("c#0", null))
        e = MindMap.place(e, items, listOf("e#0"), MapTarget("d#0", null))
        val m = build(e)
        assertEquals(listOf("e#0"), MindMap.find(m, "d#0")!!.children.map { it.key })
        assertEquals(1, m.branches.size)
        assertEquals(4, m.branches[0].count())
    }

    @Test
    fun editedTextIsForTheMapOnlyAndCanBeRestored() {
        var e = MindMap.place(MapEdits(), items, listOf("a#0"), MapTarget(null, null))
        e = MindMap.setText(e, items, "a#0", "  المناعة الفطرية  ")
        val n = build(e).branches[0]
        assertEquals("المناعة الفطرية", n.text)
        assertEquals("المناعة الفطرية: خط الدفاع الأول، وغير متخصصة", n.original)
        // الملخص (نصوص التمييز) لا يتغيّر
        assertEquals("المناعة الفطرية: خط الدفاع الأول، وغير متخصصة", MindMap.items(marks).first { it.key == "a#0" }.text)
        // نقل العقدة يحفظ نصها المعدَّل
        e = MindMap.place(e, items, listOf("c#0"), MapTarget(null, null))
        e = MindMap.move(e, items, "c#0", -1)
        assertEquals("المناعة الفطرية", MindMap.find(build(e), "a#0")!!.text)
        // إرجاع الأصل أو نص فارغ يحذف التعديل
        assertTrue(MindMap.setText(e, items, "a#0", n.original).texts.isEmpty())
        assertTrue(MindMap.setText(e, items, "a#0", " ").texts.isEmpty())
    }

    @Test
    fun removingANodeDropsItsEditedText() {
        var e = MindMap.place(MapEdits(), items, listOf("a#0"), MapTarget(null, null))
        e = MindMap.setText(e, items, "a#0", "x")
        assertTrue(MindMap.remove(e, items, "a#0").first.texts.isEmpty())
    }
}
