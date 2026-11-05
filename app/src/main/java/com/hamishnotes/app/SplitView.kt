package com.hamishnotes.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** مستندان جنبًا إلى جنب بفاصل قابل للسحب. */
@Composable
fun SplitReaders(first: DocSession, second: DocSession) {
    val c = AppController
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val total = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(c.splitFrac).fillMaxHeight()) { key(first.id) { ReaderScreen(first) } }
            Box(
                Modifier.width(14.dp).fillMaxHeight().background(Palette.Desk)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { ch, dx ->
                            ch.consume()
                            val d = (if (rtl) -dx else dx) / total
                            c.splitFrac = (c.splitFrac + d).coerceIn(0.25f, 0.75f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.width(4.dp).heightIn(min = 48.dp).size(4.dp, 48.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF98A2B3)))
            }
            Box(Modifier.weight(1f - c.splitFrac).fillMaxHeight()) { key(second.id) { ReaderScreen(second) } }
        }
    }
}

/** اختيار المستند الذي يُعرض بجانب المستند الحالي. */
@Composable
fun SplitPickerDialog(current: DocSession, onDismiss: () -> Unit) {
    val c = AppController
    val docs = c.library.filter { it.id != current.id && !it.locked }.sortedByDescending { it.lastOpened }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("عرض مستند بجانبه", "Open side by side")) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                if (docs.isEmpty()) Text(L("لا توجد مستندات أخرى", "No other documents"), color = Palette.TextSecondary)
                docs.forEach { d ->
                    Text(
                        d.name, fontSize = 15.sp, color = Palette.TextPrimary, maxLines = 1,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { c.openSplit(d.id); onDismiss() }
                            .padding(vertical = 10.dp, horizontal = 8.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = dialogClose(onDismiss)) { Text(L("إلغاء", "Cancel")) } }
    )
}
