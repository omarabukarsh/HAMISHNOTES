package com.mynotes.app

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { NoteScreen() } }
    }
}

enum class Tool { PEN, ERASER }
data class Pt(val x: Float, val y: Float, val p: Float)
data class Ink(val pts: List<Pt>, val color: Color, val width: Float)

class PdfDoc(private val pfd: ParcelFileDescriptor) {
    private val renderer = PdfRenderer(pfd)
    private var closed = false
    val count: Int = renderer.pageCount
    val ratios: List<Float> = (0 until count).map { i ->
        val p = renderer.openPage(i)
        val r = p.width.toFloat() / p.height.toFloat()
        p.close()
        r
    }

    fun render(index: Int, widthPx: Int): Bitmap? = synchronized(this) {
        if (closed) {
            null
        } else {
            val page = renderer.openPage(index)
            val h = (widthPx * page.height.toFloat() / page.width).toInt()
            val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bmp
        }
    }

    fun close() {
        synchronized(this) {
            if (!closed) {
                closed = true
                renderer.close()
                pfd.close()
            }
        }
    }
}

fun eraseAt(list: List<Ink>, at: Offset): List<Ink> =
    list.filterNot { s -> s.pts.any { hypot(it.x - at.x, it.y - at.y) < 0.015f } }

@Composable
fun PageView(
    index: Int,
    doc: PdfDoc?,
    ratio: Float,
    strokes: List<Ink>,
    tool: Tool,
    color: Color,
    onBegin: () -> Unit,
    onErase: (Offset) -> Unit,
    onFinish: (Ink) -> Unit
) {
    val bmpState = produceState<Bitmap?>(initialValue = null, doc, index) {
        value = if (doc == null) null else withContext(Dispatchers.IO) {
            try { doc.render(index, 1400) } catch (e: Throwable) { null }
        }
    }
    var live by remember { mutableStateOf<Ink?>(null) }

    Box(Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.White)) {
        val b = bmpState.value
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }
        Canvas(
            Modifier.fillMaxSize().pointerInput(tool, color) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        val isPen = down.type == PointerType.Stylus ||
                            down.type == PointerType.Eraser ||
                            down.type == PointerType.Mouse
                        if (!isPen) continue
                        val w = size.width.toFloat()
                        val erasing = tool == Tool.ERASER || down.type == PointerType.Eraser
                        onBegin()
                        var pts = listOf(
                            Pt(down.position.x / w, down.position.y / w,
                                if (down.pressure > 0f) down.pressure else 0.5f)
                        )
                        if (erasing) {
                            onErase(Offset(down.position.x / w, down.position.y / w))
                        } else {
                            live = Ink(pts, color, 0.003f)
                        }
                        down.consume()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            if (erasing) {
                                onErase(Offset(ch.position.x / w, ch.position.y / w))
                            } else {
                                pts = pts + Pt(ch.position.x / w, ch.position.y / w,
                                    if (ch.pressure > 0f) ch.pressure else 0.5f)
                                live = Ink(pts, color, 0.003f)
                            }
                            ch.consume()
                        }
                        val done = live
                        if (done != null) onFinish(done)
                        live = null
                    }
                }
            }
        ) {
            val w = size.width
            (strokes + listOfNotNull(live)).forEach { s ->
                if (s.pts.size == 1) {
                    val a = s.pts[0]
                    drawCircle(s.color, s.width * w / 2, Offset(a.x * w, a.y * w))
                }
                for (j in 1 until s.pts.size) {
                    val a = s.pts[j - 1]
                    val c = s.pts[j]
                    drawLine(
                        s.color, Offset(a.x * w, a.y * w), Offset(c.x * w, c.y * w),
                        strokeWidth = s.width * w * (0.4f + c.p),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
fun NoteScreen() {
    val context = LocalContext.current
    var doc by remember { mutableStateOf<PdfDoc?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var all by remember { mutableStateOf(mapOf<Int, List<Ink>>()) }
    var tool by remember { mutableStateOf(Tool.PEN) }
    var color by remember { mutableStateOf(Color.Black) }
    val undo = remember { mutableStateListOf<Map<Int, List<Ink>>>() }
    val redo = remember { mutableStateListOf<Map<Int, List<Ink>>>() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")!!
                val newDoc = PdfDoc(pfd)
                doc?.close()
                doc = newDoc
                all = mapOf()
                undo.clear()
                redo.clear()
                error = null
            } catch (e: Throwable) {
                error = "تعذر فتح الملف: " + e.message
            }
        }
    }

    val pageCount = doc?.count ?: 1

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { picker.launch(arrayOf("application/pdf")) }) { Text("فتح PDF") }
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
                    redo.add(all)
                    all = undo.removeAt(undo.lastIndex)
                }
            }) { Text("تراجع") }
            OutlinedButton(onClick = {
                if (redo.isNotEmpty()) {
                    undo.add(all)
                    all = redo.removeAt(redo.lastIndex)
                }
            }) { Text("إعادة") }
        }

        val msg = error
        if (msg != null) Text(msg, color = Color.Red, modifier = Modifier.padding(8.dp))

        LazyColumn(
            Modifier.fillMaxSize().background(Color(0xFFDDDDDD)),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pageCount) { i ->
                PageView(
                    index = i,
                    doc = doc,
                    ratio = doc?.ratios?.get(i) ?: 0.707f,
                    strokes = all[i] ?: emptyList(),
                    tool = tool,
                    color = color,
                    onBegin = { undo.add(all); redo.clear() },
                    onErase = { at -> all = all + (i to eraseAt(all[i] ?: emptyList(), at)) },
                    onFinish = { ink -> all = all + (i to ((all[i] ?: emptyList()) + ink)) }
                )
            }
        }
    }
}
