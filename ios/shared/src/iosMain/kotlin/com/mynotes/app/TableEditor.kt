package com.mynotes.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties

/**
 * محرر الجدول: تعديل كل خلية، إضافة/حذف/تحريك الصفوف والأعمدة، تبديل الصفوف بالأعمدة،
 * وتحديد هل الصف الأول عناوين أعمدة.
 */
@Composable
fun TableEditorDialog(
    initial: List<List<String>>,
    initialHeader: Boolean,
    onSave: (List<List<String>>, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val start = remember {
        val cols = initial.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 2
        val r = initial.map { row -> row + List(cols - row.size) { "" } }
        if (r.isEmpty()) listOf(List(cols) { "" }) else r
    }
    var rows by remember { mutableStateOf(start) }
    var header by remember { mutableStateOf(initialHeader) }
    val cols = rows.maxOfOrNull { it.size } ?: 1

    fun setCell(r: Int, c: Int, v: String) {
        rows = rows.mapIndexed { i, row -> if (i == r) row.mapIndexed { j, x -> if (j == c) v else x } else row }
    }

    fun addRow(at: Int) {
        val n = rows.toMutableList()
        n.add(at.coerceIn(0, n.size), List(cols) { "" })
        rows = n
    }

    fun addCol(at: Int) {
        rows = rows.map { row -> row.toMutableList().also { it.add(at.coerceIn(0, it.size), "") } }
    }

    fun moveRow(r: Int, d: Int) {
        val t = r + d
        if (t !in rows.indices) return
        val n = rows.toMutableList()
        val x = n[r]
        n[r] = n[t]
        n[t] = x
        rows = n
    }

    fun moveCol(c: Int, d: Int) {
        val t = c + d
        if (t !in 0 until cols) return
        rows = rows.map { row ->
            val n = row.toMutableList()
            val x = n[c]
            n[c] = n[t]
            n[t] = x
            n
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.92f).heightIn(max = 680.dp),
            shape = RoundedCornerShape(22.dp),
            color = Palette.Ivory
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        L("تحرير الجدول", "Edit table"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold,
                        fontSize = 20.sp, color = Palette.Navy, modifier = Modifier.weight(1f)
                    )
                    Text(L("الصف الأول عناوين", "First row is headers"), fontSize = 13.sp, color = Palette.TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = header, onCheckedChange = { header = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Palette.Navy, checkedBorderColor = Palette.Navy)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolChip(L("+ صف", "+ Row"), R.drawable.ph_plus) { addRow(rows.size) }
                    ToolChip(L("+ عمود", "+ Column"), R.drawable.ph_plus) { addCol(cols) }
                    ToolChip(L("تبديل الصفوف والأعمدة", "Swap rows and columns"), R.drawable.ph_redo) {
                        rows = (0 until cols).map { c -> rows.map { it.getOrElse(c) { "" } } }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // الشبكة: شريط أعمدة (لكل عمود قائمة) ثم الصفوف (لكل صف قائمة)
                val hs = rememberScrollState()
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    Row(Modifier.horizontalScroll(hs)) {
                        Spacer(Modifier.width(36.dp))
                        for (c in 0 until cols) {
                            Box(Modifier.width(180.dp), contentAlignment = Alignment.Center) {
                                AxisMenu(
                                    label = L("العمود ${c + 1}", "Column ${c + 1}"),
                                    items = listOfNotNull(
                                        if (c > 0) L("تحريك يسارًا", "Move left") to { moveCol(c, -1) } else null,
                                        if (c < cols - 1) L("تحريك يمينًا", "Move right") to { moveCol(c, 1) } else null,
                                        L("إدراج عمود بعده", "Insert column after") to { addCol(c + 1) },
                                        if (cols > 1) L("حذف العمود", "Delete column") to {
                                            rows = rows.map { row -> row.filterIndexed { j, _ -> j != c } }
                                        } else null
                                    )
                                )
                            }
                        }
                    }
                    rows.forEachIndexed { r, row ->
                        Row(Modifier.horizontalScroll(hs), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                                AxisMenu(
                                    label = "${r + 1}",
                                    items = listOfNotNull(
                                        if (r > 0) L("تحريك لأعلى", "Move up") to { moveRow(r, -1) } else null,
                                        if (r < rows.lastIndex) L("تحريك لأسفل", "Move down") to { moveRow(r, 1) } else null,
                                        L("إدراج صف بعده", "Insert row after") to { addRow(r + 1) },
                                        if (rows.size > 1) L("حذف الصف", "Delete row") to {
                                            rows = rows.filterIndexed { i, _ -> i != r }
                                        } else null
                                    )
                                )
                            }
                            for (c in 0 until cols) {
                                val isHead = header && r == 0
                                BasicTextField(
                                    value = row.getOrElse(c) { "" },
                                    onValueChange = { setCell(r, c, it) },
                                    textStyle = TextStyle(
                                        fontFamily = Fonts.Sans, fontSize = 14.sp,
                                        fontWeight = if (isHead) FontWeight.Bold else FontWeight.Normal,
                                        color = Palette.TextPrimary
                                    ),
                                    cursorBrush = SolidColor(Palette.Navy),
                                    modifier = Modifier.width(180.dp).heightIn(min = 44.dp)
                                        .background(if (isHead) Palette.NavySoft else Color.White)
                                        .border(0.5.dp, Palette.Divider)
                                        .padding(horizontal = 8.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        L("اضغط رقم الصف أو اسم العمود لتحريكه أو حذفه", "Tap a row number or column name to move or delete it"),
                        fontSize = 12.sp, color = Palette.TextSecondary, modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text(L("إلغاء", "Cancel")) }
                    TextButton(onClick = {
                        // تجاهل الصفوف الفارغة تمامًا
                        val clean = rows.map { r -> r.map { it.trim() } }.filter { r -> r.any { it.isNotEmpty() } }
                        onSave(clean.ifEmpty { rows }, header)
                    }) { Text(L("حفظ", "Save"), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun ToolChip(label: String, icon: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.clip(shape).border(1.dp, Palette.Divider, shape).background(Color.White)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PhIcon(icon, Palette.Navy, 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, color = Palette.TextPrimary)
    }
}

@Composable
private fun AxisMenu(label: String, items: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            label, fontSize = 12.sp, color = Palette.GoldDeep, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = true }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (t, f) ->
                DropdownMenuItem(text = { Text(t, fontSize = 14.sp) }, onClick = {
                    open = false
                    f()
                })
            }
        }
    }
}
