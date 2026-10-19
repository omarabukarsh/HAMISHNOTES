package com.mynotes.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

class MainActivity : ComponentActivity() {
override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)
setContent { MaterialTheme { NoteApp() } }
}
}

enum class Tool { PEN, ERASER }
data class Pt(val x: Float, val y: Float, val p: Float)
data class Ink(val pts: List<Pt>, val color: Color, val width: Float)
data class Page(val id: Int, var strokes: List<Ink> = emptyList())

fun eraseAt(list: List<Ink>, at: Offset): List<Ink> =
list.filterNot { s -> s.pts.any { hypot(it.x - at.x, it.y - at.y) < 30f } }

@Composable
fun NoteApp() {
var pages by remember { mutableStateOf(listOf(Page(id = 1))) }
var currentPageIndex by remember { mutableIntStateOf(0) }

val currentPage = pages.getOrNull(currentPageIndex) ?: Page(id = 1)  
  
Column(Modifier.fillMaxSize().statusBarsPadding()) {  
    // شريط الأدوات العلوي  
    Row(  
        Modifier.fillMaxWidth().padding(8.dp),  
        horizontalArrangement = Arrangement.spacedBy(8.dp),  
        verticalAlignment = Alignment.CenterVertically  
    ) {  
        Text("صفحة ${currentPageIndex + 1} من ${pages.size}", style = MaterialTheme.typography.titleMedium)  
          
        IconButton(  
            onClick = { if (currentPageIndex > 0) currentPageIndex-- },  
            enabled = currentPageIndex > 0  
        ) { Text("<") }  
          
        IconButton(  
            onClick = { if (currentPageIndex < pages.size - 1) currentPageIndex++ },  
            enabled = currentPageIndex < pages.size - 1  
        ) { Text(">") }  

        Button(onClick = {  
            pages = pages + Page(id = pages.size + 1)  
            currentPageIndex = pages.size - 1  
        }) { Text("+ صفحة") }  

        Spacer(Modifier.weight(1f))  
    }  

    // لوحة الرسم للصفحة الحالية  
    NoteCanvas(  
        page = currentPage,  
        onPageUpdate = { updatedPage ->  
            pages = pages.toMutableList().apply {  
                set(currentPageIndex, updatedPage)  
            }  
        }  
    )  
}

}

@Composable
fun NoteCanvas(page: Page, onPageUpdate: (Page) -> Unit) {
var strokes by remember(http://page.id) { mutableStateOf(page.strokes) }
var live by remember { mutableStateOf<Ink?>(null) }
var tool by remember { mutableStateOf(Tool.PEN) }
var color by remember { mutableStateOf(Color.Black) }
val undo = remember(http://page.id) { mutableStateListOf<List<Ink>>() }
val redo = remember(http://page.id) { mutableStateListOf<List<Ink>>() }

fun updateStrokes(newStrokes: List<Ink>) {  
    strokes = newStrokes  
    onPageUpdate(page.copy(strokes = newStrokes))  
}  

Column(Modifier.fillMaxSize()) {  
    Row(  
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),  
        horizontalArrangement = Arrangement.spacedBy(8.dp),  
        verticalAlignment = Alignment.CenterVertically  
    ) {  
        Button(onClick = { tool = Tool.PEN }) { Text("قلم") }  
        Button(onClick = { tool = Tool.ERASER }) { Text("ممحاة") }  
        listOf(Color.Black, Color(0xFF1565C0), Color(0xFFC62828)).forEach { c ->  
            Box(  
                Modifier.size(36.dp).clip(CircleShape).background(c)  
                    .clickable { color = c; tool = Tool.PEN }  
            )  
        }  
        Spacer(Modifier.weight(1f))  
        OutlinedButton(onClick = {  
            if (undo.isNotEmpty()) {  
                redo.add(strokes)  
                updateStrokes(undo.removeAt(undo.lastIndex))  
            }  
        }) { Text("تراجع") }  
        OutlinedButton(onClick = {  
            if (redo.isNotEmpty()) {  
                undo.add(strokes)  
                updateStrokes(redo.removeAt(redo.lastIndex))  
            }  
        }) { Text("إعادة") }  
    }  

    Canvas(  
        Modifier.fillMaxSize().background(Color.White)  
            .pointerInput(http://page.id, tool, color) {  
                awaitPointerEventScope {  
                    while (true) {  
                        val down = awaitFirstDown()  
                        val erasing = tool == Tool.ERASER || down.type == PointerType.Eraser  
                        undo.add(strokes)  
                        redo.clear()  
                        var pts = listOf(  
                            Pt(down.position.x, down.position.y,  
                                if (down.pressure > 0f) down.pressure else 0.5f)  
                        )  
                        if (erasing) updateStrokes(eraseAt(strokes, down.position))  
                        else live = Ink(pts, color, 6f)  
                        down.consume()  
                        while (true) {  
                            val ev = awaitPointerEvent()  
                            val ch = ev.changes.first()  
                            if (!ch.pressed) break  
                            if (erasing) {  
                                updateStrokes(eraseAt(strokes, ch.position))  
                            } else {  
                                pts = pts + Pt(ch.position.x, ch.position.y,  
                                    if (ch.pressure > 0f) ch.pressure else 0.5f)  
                                live = Ink(pts, color, 6f)  
                            }  
                            ch.consume()  
                        }  
                        live?.let { updateStrokes(strokes + it) }  
                        live = null  
                    }  
                }  
            }  
    ) {  
        (strokes + listOfNotNull(live)).forEach { s ->  
            if (s.pts.size == 1) {  
                val a = s.pts[0]  
                drawCircle(s.color, s.width / 2, Offset(a.x, a.y))  
            }  
            for (i in 1 until s.pts.size) {  
                val a = s.pts[i - 1]  
                val b = s.pts[i]  
                drawLine(  
                    s.color, Offset(a.x, a.y), Offset(b.x, b.y),  
                    strokeWidth = s.width * (0.4f + b.p),  
                    cap = StrokeCap.Round  
                )  
            }  
        }  
    }  
}

}
