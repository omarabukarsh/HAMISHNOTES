package com.mynotes.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MindMapTest {
    private val yellow = Color(0xFFFFEB3B)
    private val green = Color(0xFF8BC34A)

    private fun mark(id: String, text: String, top: Float, color: Color = yellow, page: Int = 0, level: Int = 1, emphasis: List<String> = emptyList()) =
        TextMark(id, page, listOf(MarkRect(0.1f, top, 0.9f, top + 0.02f)), color, text, 1L, level, emphasis)

    private fun build(marks: List<TextMark>, edits: MapEdits = MapEdits(), byColor: Boolean = false) =
        MindMap.build("T", marks, edits, byColor) { "c" + Summary.argbOf(it) }

    @Test
    fun colonLabelsBecomeBranchesAndOthersPoints() {
        val m = build(
            listOf(
                mark("a", "المناعة الفطرية: خط الدفاع الأول", 0.1f),
                mark("b", "تشمل الجلد والأغشية المخاطية", 0.2f),
                mark("c", "المناعة المكتسبة: متخصصة ولها ذاكرة", 0.3f),
                mark("d", "الخلايا B تنتج الأجسام المضادة", 0.4f)
            )
        )
        assertEquals(listOf("المناعة الفطرية", "المناعة المكتسبة"), m.branches.map { it.label })
        assertEquals("خط الدفاع الأول", m.branches[0].sub)
        assertEquals(listOf("b#0"), m.branches[0].children.map { it.key })
        assertEquals(listOf("d#0"), m.branches[1].children.map { it.key })
    }

    @Test
    fun withoutStructureGroupsByColor() {
        val m = build(
            listOf(
                mark("a", "نص أول بلا عنوان", 0.1f, yellow),
                mark("b", "نص ثانٍ بلا عنوان", 0.2f, green),
                mark("c", "نص ثالث", 0.3f, yellow)
            )
        )
        assertEquals(2, m.branches.size)
        assertEquals(listOf("a#0", "c#0"), m.branches[0].children.map { it.key })
        assertNull(m.branches[0].mark)
    }

    @Test
    fun userEditsOverrideDraftAndBlankShowsOnlyChosen() {
        val marks = listOf(
            mark("a", "فرع أول: شرح", 0.1f), mark("b", "نقطة", 0.2f), mark("c", "فرع ثانٍ: شرح", 0.3f), mark("d", "نقطة ثانية", 0.4f)
        )
        // نقل d إلى الفرع الأول
        val moved = build(marks, MapEdits(nodes = mapOf("d#0" to MapNodeEdit(MapRole.POINT, "a#0"))))
        assertEquals(listOf("b#0", "d#0"), moved.branches[0].children.map { it.key })
        assertTrue(moved.branches[1].children.isEmpty())
        assertTrue(!MindMap.find(moved, "d#0")!!.auto)

        // «ابدأ من فارغ»: يظهر فقط ما اختاره المستخدم
        val blank = build(marks, MapEdits(blank = true, nodes = mapOf("c#0" to MapNodeEdit(MapRole.BRANCH), "b#0" to MapNodeEdit(MapRole.POINT, "c#0"))))
        assertEquals(listOf("c#0"), blank.branches.map { it.key })
        assertEquals(listOf("b#0"), blank.branches[0].children.map { it.key })

        // إخفاء
        val hidden = build(marks, MapEdits(nodes = mapOf("b#0" to MapNodeEdit(MapRole.HIDDEN))))
        assertNull(MindMap.find(hidden, "b#0"))
    }

    @Test
    fun pointUnderPointIsThirdLevel() {
        val marks = listOf(mark("a", "فرع أول: شرح", 0.1f), mark("b", "نقطة", 0.2f), mark("c", "تفصيل", 0.3f))
        val m = build(marks, MapEdits(nodes = mapOf("c#0" to MapNodeEdit(MapRole.POINT, "b#0"))))
        val b = MindMap.find(m, "b#0")
        assertNotNull(b)
        assertEquals(listOf("c#0"), b!!.children.map { it.key })
    }

    @Test
    fun shortLabelRules() {
        assertEquals("المناعة الفطرية", MindMap.shortLabel("المناعة الفطرية: خط الدفاع الأول", emptyList()).first)
        val long = MindMap.shortLabel("الخلايا البلعمية تبتلع الميكروبات وتهضمها ثم تعرض أجزاءها على سطحها", emptyList())
        assertEquals("الخلايا البلعمية تبتلع الميكروبات وتهضمها ثم…", long.first)
        val emph = MindMap.shortLabel("تجعل الذاكرة المناعية الاستجابة أسرع", listOf("الذاكرة المناعية"))
        assertEquals("الذاكرة المناعية", emph.first)
        assertTrue(emph.third)
    }

    @Test
    fun mergeMovesAssignmentsToNewMark() {
        val e = MapEdits(nodes = mapOf("x#0" to MapNodeEdit(MapRole.POINT, "a#0"), "a#0" to MapNodeEdit(MapRole.BRANCH)), labels = mapOf("x#0" to "اسم"))
        val m = MindMap.migrate(e, listOf("x", "y"), "z")
        assertEquals(MapNodeEdit(MapRole.POINT, "a#0"), m.nodes["z#0"])
        assertEquals("اسم", m.labels["z#0"])
        assertNull(m.nodes["x#0"])
    }
}
