
package com.mynotes.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                NoteApp()
            }
        }
    }
}

enum class Tool {
    PEN,
    ERASER
}

data class Pt(
    val x: Float,
    val y: Float,
    val p: Float
)

data class Ink(
    val pts: List<Pt>,
    val color: Color,
    val width: Float
)

data class Page(
    val id: Int,
    val strokes: List<Ink> = emptyList()
)

private fun eraseAt(
    list: List<Ink>,
    at: Offset,
    radius: Float = 30f
): List<Ink> {
    return list.filterNot { stroke ->
        stroke.pts.any { point ->
            hypot(point.x - at.x, point.y - at.y) < radius
        }
    }
}

private fun PointerInputChange.toPt(): Pt {
    return Pt(
        x = position.x,
        y = position.y,
        p = pressure.coerceIn(0f, 1f).let {
            if (it > 0f) it else 0.5f
        }
    )
}

@Composable
fun NoteApp() {
    var pages by remember {
        mutableStateOf(listOf(Page(id = 1)))
    }

    var currentPageIndex by remember {
        mutableIntStateOf(0)
    }

    val currentPage = pages[currentPageIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "صفحة ${currentPageIndex + 1} من ${pages.size}",
                style = MaterialTheme.typography.titleMedium
            )

            IconButton(
                onClick = {
                    if (currentPageIndex > 0) {
                        currentPageIndex--
                    }
                },
                enabled = currentPageIndex > 0
            ) {
                Text("<")
            }

            IconButton(
                onClick = {
                    if (currentPageIndex < pages.lastIndex) {
                        currentPageIndex++
                    }
                },
                enabled = currentPageIndex < pages.lastIndex
            ) {
                Text(">")
            }

            Button(
                onClick = {
                    val nextId = (pages.maxOfOrNull { it.id } ?: 0) + 1
                    pages = pages + Page(id = nextId)
                    currentPageIndex = pages.lastIndex
                }
            ) {
                Text("+ صفحة")
            }

            Spacer(Modifier.weight(1f))
        }

        NoteCanvas(
            page = currentPage,
            onPageUpdate = { updatedPage ->
                pages = pages.map { oldPage ->
                    if (oldPage.id == updatedPage.id) {
                        updatedPage
                    } else {
                        oldPage
                    }
                }
            }
        )
    }
}

@Composable
fun NoteCanvas(
    page: Page,
    onPageUpdate: (Page) -> Unit
) {
    var strokes by remember(page.id) {
        mutableStateOf(page.strokes)
    }

    var live by remember(page.id) {
        mutableStateOf<Ink?>(null)
    }

    var tool by remember(page.id) {
        mutableStateOf(Tool.PEN)
    }

    var color by remember(page.id) {
        mutableStateOf(Color.Black)
    }

    val undo = remember(page.id) {
        mutableStateListOf<List<Ink>>()
    }

    val redo = remember(page.id) {
        mutableStateListOf<List<Ink>>()
    }

    fun updateStrokes(newStrokes: List<Ink>) {
        if (newStrokes == strokes) return

        strokes = newStrokes
        onPageUpdate(page.copy(strokes = newStrokes))
    }

    fun saveUndo(state: SnapshotStateList<List<Ink>>) {
        state.add(strokes.toList())

        // الحد من استهلاك الذاكرة
        if (state.size > 100) {
            state.removeAt(0)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    tool = Tool.PEN
                },
                enabled = tool != Tool.PEN
            ) {
                Text("قلم")
            }

            Button(
                onClick = {
                    tool = Tool.ERASER
                },
                enabled = tool != Tool.ERASER
            ) {
                Text("ممحاة")
            }

            listOf(
                Color.Black,
                Color(0xFF1565C0),
                Color(0xFFC62828)
            ).forEach { selectedColor ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(selectedColor)
                        .clickable {
                            color = selectedColor
                            tool = Tool.PEN
                        }
                )
            }

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = {
                    if (undo.isNotEmpty()) {
                        redo.add(strokes.toList())
                        updateStrokes(undo.removeAt(undo.lastIndex))
                    }
                },
                enabled = undo.isNotEmpty()
            ) {
                Text("تراجع")
            }

            OutlinedButton(
                onClick = {
                    if (redo.isNotEmpty()) {
                        undo.add(strokes.toList())
                        updateStrokes(redo.removeAt(redo.lastIndex))
                    }
                },
                enabled = redo.isNotEmpty()
            ) {
                Text("إعادة")
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .pointerInput(page.id, tool, color) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false
                        )

                        val erasing = tool == Tool.ERASER
                        val beforeGesture = strokes.toList()
                        var savedUndo = false

                        live = null

                        if (!erasing) {
                            saveUndo(undo)
                            redo.clear()
                            savedUndo = true

                            live = Ink(
                                pts = listOf(down.toPt()),
                                color = color,
                                width = 6f
                            )
                        } else {
                            val erased = eraseAt(
                                strokes,
                                down.position
                            )

                            if (erased != strokes) {
                                saveUndo(undo)
                                redo.clear()
                                savedUndo = true
                                updateStrokes(erased)
                            }
                        }

                        down.consume()

                        while (true) {
                            val event = awaitPointerEvent()

                            val change = event.changes.firstOrNull {
                                it.id == down.id
                            } ?: break

                            if (!change.pressed) {
                                if (!erasing) {
                                    live?.let { finishedStroke ->
                                        updateStrokes(
                                            strokes + finishedStroke
                                        )
                                    }
                                    live = null
                                }
                                change.consume()
                                break
                            }

                            if (erasing) {
                                val erased = eraseAt(
                                    strokes,
                                    change.position
                                )

                                if (erased != strokes) {
                                    if (!savedUndo) {
                                        saveUndo(undo)
                                        redo.clear()
                                        savedUndo = true
                                    }

                                    updateStrokes(erased)
                                }
                            } else {
                                live?.let { currentStroke ->
                                    live = currentStroke.copy(
                                        pts = currentStroke.pts +
                                            change.toPt()
                                    )
                                }
                            }

                            change.consume()
                        }

                        // استعادة الحالة السابقة إذا لم تتغير
                        // الرسومات لا تحتاج إلى أي عملية إضافية.
                        if (erasing && savedUndo &&
                            strokes == beforeGesture
                        ) {
                            undo.removeAt(undo.lastIndex)
                        }

                        live = null
                    }
                }
        ) {
            val allStrokes = strokes + listOfNotNull(live)

            allStrokes.forEach { stroke ->
                if (stroke.pts.size == 1) {
                    val point = stroke.pts.first()

                    drawCircle(
                        color = stroke.color,
                        radius = stroke.width / 2f,
                        center = Offset(point.x, point.y)
                    )
                }

                for (i in 1 until stroke.pts.size) {
                    val a = stroke.pts[i - 1]
                    val b = stroke.pts[i]

                    drawLine(
                        color = stroke.color,
                        start = Offset(a.x, a.y),
                        end = Offset(b.x, b.y),
                        strokeWidth = stroke.width *
                            (0.4f + b.p),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
