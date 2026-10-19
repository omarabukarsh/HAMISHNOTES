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
        setContent { MaterialTheme { NoteScreen() } }
    }
}

enum class Tool { PEN, ERASER }
data class Pt(val x: Float, val y: Float, val p: Float)
data class Ink(val pts: List<Pt>, val color: Color, val width: Float)

fun eraseAt(list: List<Ink>, at: Offset): List<Ink> =
    list.filterNot { s -> s.pts.any { hypot(it.x - at.x, it.y - at.y) < 30f } }

@Composable
fun NoteScreen() {
    var strokes by remember { mutableStateOf(listOf<Ink>()) }
    var live by remember { mutableStateOf<Ink?>(null) }
    var tool by remember { mutableStateOf(Tool.PEN) }
    var color by remember { mutableStateOf(Color.Black) }
    val undo = remember { mutableStateListOf<List<Ink>>() }
    val redo = remember { mutableStateListOf<List<Ink>>() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
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
                    strokes = undo.removeAt(undo.lastIndex)
                }
            }) { Text("تراجع") }
            OutlinedButton(onClick = {
                if (redo.isNotEmpty()) {
                    undo.add(strokes)
                    strokes = redo.removeAt(redo.lastIndex)
                }
            }) { Text("إعادة") }
        }

        Canvas(
            Modifier.fillMaxSize().background(Color.White)
                .pointerInput(tool, color) {
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
                            if (erasing) strokes = eraseAt(strokes, down.position)
                            else live = Ink(pts, color, 6f)
                            down.consume()
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.first()
                                if (!ch.pressed) break
                                if (erasing) {
