package com.hamishnotes.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/*
 * ملصقات أغلفة الدفاتر: رسومات مسطّحة بخط كحلي، مرسومة في مربع 100×100.
 * المفتاح هو ما يُحفظ في فهرس المستند. أي قيمة أخرى (إيموجي قديم) تُعرض كنص كما هي.
 */

internal class StickerPiece(val d: String, val fill: Long?, val stroke: Long?, val w: Float)

/** مفاتيح الملصقات بالترتيب الذي تظهر به في نافذة الغلاف. */
val CoverStickers: List<String> = listOf(
    "pill",
    "ribbon",
    "brain",
    "heart",
    "lungs",
    "dna",
    "germ",
    "tooth",
    "bone",
    "syringe",
    "microscope",
    "flask",
    "book",
    "pencil",
    "star",
    "leaf",
    "bolt",
    "target",
    "trophy",
    "globe"
)

private val StickerArt: Map<String, List<StickerPiece>> = mapOf(
    "pill" to listOf(
        StickerPiece("M42.3,40.8 L30,51.1 L30,51.1 L27.6,53.9 L26.1,57.4 L25.8,61.1 L26.6,64.8 L28.6,68 L31.4,70.5 L34.8,71.9 L38.6,72.3 L42.2,71.4 L45.5,69.5 L57.7,59.2 Z", 0xFFEE7B5E, 0xFF1E2A44, 3.6f),
        StickerPiece("M42.3,40.8 L54.5,30.5 L54.5,30.5 L57.8,28.6 L61.4,27.7 L65.2,28.1 L68.6,29.5 L71.4,32 L73.4,35.2 L74.2,38.9 L73.9,42.6 L72.4,46.1 L70,48.9 L57.7,59.2 Z", 0xFFFFFFFF, 0xFF1E2A44, 3.6f),
        StickerPiece("M36.6,52.7 L42.8,47.6", null, 0xFFFFFFFF, 3f)
    ),
    "ribbon" to listOf(
        StickerPiece("M42,56 L26,88 L42,81 L47,92 L56,60 Z", 0xFFEC8FA3, 0xFF1E2A44, 3.6f),
        StickerPiece("M58,56 L74,88 L58,81 L53,92 L44,60 Z", 0xFFEC8FA3, 0xFF1E2A44, 3.6f),
        StickerPiece("M50,12 C28,12 26,40 40,54 C46,60 54,60 60,54 C74,40 72,12 50,12 Z M50,26 C42,26 42,38 50,46 C58,38 58,26 50,26 Z", 0xFFEC8FA3, 0xFF1E2A44, 3.6f)
    ),
    "brain" to listOf(
        StickerPiece("M50,20 C42,12 26,16 26,30 C16,32 12,46 20,54 C14,64 22,78 36,76 C42,84 58,84 64,76 C78,78 86,64 80,54 C88,46 84,32 74,30 C74,16 58,12 50,20 Z", 0xFFEC8FA3, 0xFF1E2A44, 3.6f),
        StickerPiece("M50,22 L50,78", null, 0xFF1E2A44, 3.6f),
        StickerPiece("M34,40 C42,40 44,48 36,52", null, 0xFF1E2A44, 3f),
        StickerPiece("M66,40 C58,40 56,48 64,52", null, 0xFF1E2A44, 3f),
        StickerPiece("M34,64 C42,62 44,68 40,72", null, 0xFF1E2A44, 3f),
        StickerPiece("M66,64 C58,62 56,68 60,72", null, 0xFF1E2A44, 3f)
    ),
    "heart" to listOf(
        StickerPiece("M50,86 C10,58 14,24 34,22 C44,21 50,29 50,34 C50,29 56,21 66,22 C86,24 90,58 50,86 Z", 0xFFEE7B5E, 0xFF1E2A44, 3.6f),
        StickerPiece("M26,34 C27,30 31,28 35,29", null, 0xFFFFFFFF, 3f)
    ),
    "lungs" to listOf(
        StickerPiece("M43,34 C30,30 16,56 18,76 C20,86 36,86 42,76 C46,66 46,48 43,34 Z", 0xFFEC8FA3, 0xFF1E2A44, 3.6f),
        StickerPiece("M57,34 C70,30 84,56 82,76 C80,86 64,86 58,76 C54,66 54,48 57,34 Z", 0xFFEC8FA3, 0xFF1E2A44, 3.6f),
        StickerPiece("M50,14 L50,42", null, 0xFF1E2A44, 3.6f),
        StickerPiece("M50,42 L42,52", null, 0xFF1E2A44, 3f),
        StickerPiece("M50,42 L58,52", null, 0xFF1E2A44, 3f)
    ),
    "dna" to listOf(
        StickerPiece("M33,14 C33,36 67,36 67,50 C67,64 33,64 33,86", null, 0xFF5E86D6, 4.2f),
        StickerPiece("M67,14 C67,36 33,36 33,50 C33,64 67,64 67,86", null, 0xFFEE7B5E, 4.2f),
        StickerPiece("M41,25 L59,25", null, 0xFF1E2A44, 3f),
        StickerPiece("M41,75 L59,75", null, 0xFF1E2A44, 3f),
        StickerPiece("M44,38 L56,38", null, 0xFF1E2A44, 3f),
        StickerPiece("M44,62 L56,62", null, 0xFF1E2A44, 3f)
    ),
    "germ" to listOf(
        StickerPiece("M70.3,58.4 L81.4,63", null, 0xFF1E2A44, 3.4f),
        StickerPiece("M85,63 L84.9,63.8 L84.7,64.6 L84.2,65.3 L83.7,65.8 L83,66.3 L82.2,66.5 L81.4,66.6 L80.6,66.5 L79.8,66.3 L79.2,65.8 L78.6,65.3 L78.2,64.6 L77.9,63.8 L77.8,63 L77.9,62.2 L78.2,61.4 L78.6,60.8 L79.2,60.2 L79.8,59.8 L80.6,59.5 L81.4,59.4 L82.2,59.5 L83,59.8 L83.7,60.2 L84.2,60.8 L84.7,61.4 L84.9,62.2 L85,63 Z", 0xFF1E2A44, null, 3.6f),
        StickerPiece("M58.4,70.3 L63,81.4", null, 0xFF1E2A44, 3.4f),
        StickerPiece("M66.6,81.4 L66.5,82.2 L66.3,83 L65.8,83.7 L65.3,84.2 L64.6,84.7 L63.8,84.9 L63,85 L62.2,84.9 L61.4,84.7 L60.8,84.2 L60.2,83.7 L59.8,83 L59.5,82.2 L59.4,81.4 L59.5,80.6 L59.8,79.8 L60.2,79.2 L60.8,78.6 L61.4,78.2 L62.2,77.9 L63,77.8 L63.8,77.9 L64.6,78.2 L65.3,78.6 L65.8,79.2 L66.3,79.8 L66.5,80.6 L66.6,81.4 Z", 0xFF1E2A44, null, 3.6f),
        StickerPiece("M41.6,70.3 L37,81.4", null, 0xFF1E2A44, 3.4f),
        StickerPiece("M40.6,81.4 L40.5,82.2 L40.2,83 L39.8,83.7 L39.2,84.2 L38.6,84.7 L37.8,84.9 L37,85 L36.2,84.9 L35.4,84.7 L34.7,84.2 L34.2,83.7 L33.7,83 L33.5,82.2 L33.4,81.4 L33.5,80.6 L33.7,79.8 L34.2,79.2 L34.7,78.6 L35.4,78.2 L36.2,77.9 L37,77.8 L37.8,77.9 L38.6,78.2 L39.2,78.6 L39.8,79.2 L40.2,79.8 L40.5,80.6 L40.6,81.4 Z", 0xFF1E2A44, null, 3.6f),
        StickerPiece("M29.7,58.4 L18.6,63", null, 0xFF1E2A44, 3.4f),
        StickerPiece("M22.2,63 L22.1,63.8 L21.8,64.6 L21.4,65.3 L20.8,65.8 L20.2,66.3 L19.4,66.5 L18.6,66.6 L17.8,66.5 L17,66.3 L16.3,65.8 L15.8,65.3 L15.3,64.6 L15.1,63.8 L15,63 L15.1,62.2 L15.3,61.4 L15.8,60.8 L16.3,60.2 L17,59.8 L17.8,59.5 L18.6,59.4 L19.4,59.5 L20.2,59.8 L20.8,60.2 L21.4,60.8 L21.8,61.4 L22.1,62.2 L22.2,63 Z", 0xFF1E2A44, null, 3.6f),
        StickerPiece("M29.7,41.6 L18.6,37", null, 0xFF1E2A44, 3.4f),
        StickerPiece("M22.2,37 L22.1,37.8 L21.8,38.6 L21.4,39.2 L20.8,39.8 L20.2,40.2 L19.4,40.5 L18.6,40.6 L17.8,40.5 L17,40.2 L16.3,39.8 L15.8,39.2 L15.3,38.6 L15.1,37.8 L15,37 L15.1,36.2 L15.3,35.4 L15.8,34.7 L16.3,34.2 L17,33.7 L17.8,33.5 L18.6,33.4 L19.4,33.5 L20.2,33.7 L20.8,34.2 L21.4,34.7 L21.8,35.4 L22.1,36.2 L22.2,37 Z", 0xFF1E2A44, null, 3.6f),
        StickerPiece("M41.6,29.7 L37,18.6", null, 0xFF1E2A44, 3.4f),
        StickerPiece("M40.6,18.6 L40.5,19.4 L40.2,20.2 L39.8,20.8 L39.2,21.4 L38.6,21.8 L37.8,22.1 L37,22.2 L36.2,22.1 L35.4,21.8 L34.7,21.4 L34.2,20.8 L33.7,20.2 L33.5,19.4 L33.4,18.6 L33.5,17.8 L33.7,17 L34.2,16.3 L34.7,15.8 L35.4,15.3 L36.2,15.1 L37,15 L37.8,15.1 L38.6,15.3 L39.2,15.8 L39.8,16.3 L40.2,17 L40.5,17.8 L40.6,18.6 Z", 0xFF1E2A44, null, 3.6f),
        StickerPiece("M58.4,29.7 L63,18.6", null, 0xFF1E2A44, 3.4f),
        StickerPiece("M66.6,18.6 L66.5,19.4 L66.3,20.2 L65.8,20.8 L65.3,21.4 L64.6,21.8 L63.8,22.1 L63,22.2 L62.2,22.1 L61.4,21.8 L60.8,21.4 L60.2,20.8 L59.8,20.2 L59.5,19.4 L59.4,18.6 L59.5,17.8 L59.8,17 L60.2,16.3 L60.8,15.8 L61.4,15.3 L62.2,15.1 L63,15 L63.8,15.1 L64.6,15.3 L65.3,15.8 L65.8,16.3 L66.3,17 L66.5,17.8 L66.6,18.6 Z", 0xFF1E2A44, null, 3.6f),
        StickerPiece("M70.3,41.6 L81.4,37", null, 0xFF1E2A44, 3.4f),
        StickerPiece("M85,37 L84.9,37.8 L84.7,38.6 L84.2,39.2 L83.7,39.8 L83,40.2 L82.2,40.5 L81.4,40.6 L80.6,40.5 L79.8,40.2 L79.2,39.8 L78.6,39.2 L78.2,38.6 L77.9,37.8 L77.8,37 L77.9,36.2 L78.2,35.4 L78.6,34.7 L79.2,34.2 L79.8,33.7 L80.6,33.5 L81.4,33.4 L82.2,33.5 L83,33.7 L83.7,34.2 L84.2,34.7 L84.7,35.4 L84.9,36.2 L85,37 Z", 0xFF1E2A44, null, 3.6f),
        StickerPiece("M74,50 L73.4,55.3 L71.6,60.4 L68.8,65 L65,68.8 L60.4,71.6 L55.3,73.4 L50,74 L44.7,73.4 L39.6,71.6 L35,68.8 L31.2,65 L28.4,60.4 L26.6,55.3 L26,50 L26.6,44.7 L28.4,39.6 L31.2,35 L35,31.2 L39.6,28.4 L44.7,26.6 L50,26 L55.3,26.6 L60.4,28.4 L65,31.2 L68.8,35 L71.6,39.6 L73.4,44.7 L74,50 Z", 0xFF86C79A, 0xFF1E2A44, 3.6f),
        StickerPiece("M45,46 L44.9,46.7 L44.7,47.3 L44.3,47.9 L43.9,48.3 L43.3,48.7 L42.7,48.9 L42,49 L41.3,48.9 L40.7,48.7 L40.1,48.3 L39.7,47.9 L39.3,47.3 L39.1,46.7 L39,46 L39.1,45.3 L39.3,44.7 L39.7,44.1 L40.1,43.7 L40.7,43.3 L41.3,43.1 L42,43 L42.7,43.1 L43.3,43.3 L43.9,43.7 L44.3,44.1 L44.7,44.7 L44.9,45.3 L45,46 Z", 0xFF1E2A44, null, 3.6f),
        StickerPiece("M61,46 L60.9,46.7 L60.7,47.3 L60.3,47.9 L59.9,48.3 L59.3,48.7 L58.7,48.9 L58,49 L57.3,48.9 L56.7,48.7 L56.1,48.3 L55.7,47.9 L55.3,47.3 L55.1,46.7 L55,46 L55.1,45.3 L55.3,44.7 L55.7,44.1 L56.1,43.7 L56.7,43.3 L57.3,43.1 L58,43 L58.7,43.1 L59.3,43.3 L59.9,43.7 L60.3,44.1 L60.7,44.7 L60.9,45.3 L61,46 Z", 0xFF1E2A44, null, 3.6f),
        StickerPiece("M42,57 C46,62 54,62 58,57", null, 0xFF1E2A44, 3f)
    ),
    "tooth" to listOf(
        StickerPiece("M30,20 C40,12 46,22 50,22 C54,22 60,12 70,20 C82,30 72,50 69,64 C67,78 63,88 58,88 C53,88 54,70 50,70 C46,70 47,88 42,88 C37,88 33,78 31,64 C28,50 18,30 30,20 Z", 0xFFFFFFFF, 0xFF1E2A44, 3.6f),
        StickerPiece("M34,28 C36,24 40,23 43,25", null, 0xFFE7EBF2, 3f)
    ),
    "bone" to listOf(
        StickerPiece("M55.7,36.2 L53.7,34.2 L52.5,31.7 L52.1,29 L52.5,26.2 L53.7,23.7 L55.7,21.8 L58.1,20.5 L60.8,20 L63.6,20.3 L66.1,21.5 L68.1,23.4 L69.5,25.8 L70.1,28.5 L69.8,31.2 L68.7,33.8 L66.9,35.9 L66.9,35.9 L69.2,34.4 L71.9,33.8 L74.7,34 L77.2,35 L79.4,36.8 L80.9,39.1 L81.6,41.8 L81.5,44.5 L80.5,47.1 L78.8,49.3 L76.5,50.9 L73.9,51.7 L71.1,51.6 L68.5,50.7 L66.2,49.1 L64.6,46.8 L44.4,63.9 L46.3,65.9 L47.5,68.3 L47.9,71.1 L47.5,73.8 L46.3,76.3 L44.3,78.2 L41.9,79.5 L39.2,80 L36.4,79.7 L33.9,78.5 L31.9,76.6 L30.5,74.2 L29.9,71.5 L30.2,68.8 L31.3,66.2 L33.1,64.1 L33.1,64.1 L30.8,65.6 L28.1,66.2 L25.3,66 L22.8,65 L20.6,63.2 L19.1,60.9 L18.4,58.2 L18.5,55.5 L19.5,52.9 L21.2,50.7 L23.5,49.1 L26.1,48.3 L28.9,48.4 L31.5,49.3 L33.7,50.9 L35.4,53.1 Z", 0xFFFFFFFF, 0xFF1E2A44, 3.6f)
    ),
    "syringe" to listOf(
        StickerPiece("M24.5,69.4 L33.7,61.7 L34.4,62.5 L25.2,70.2 Z", 0xFF1E2A44, null, 3.6f),
        StickerPiece("M59,35.9 L69.8,26.9 L74.9,33 L64.2,42 Z", 0xFFFFFFFF, 0xFF1E2A44, 3.6f),
        StickerPiece("M65.3,21.5 L68.3,19 L82.5,35.8 L79.4,38.4 Z", 0xFFFFFFFF, 0xFF1E2A44, 3.6f),
        StickerPiece("M28.9,56 L56.5,32.8 L66.8,45.1 L39.2,68.2 Z", 0xFFFFFFFF, 0xFF1E2A44, 3.6f),
        StickerPiece("M28.9,56 L39.6,47 L49.9,59.2 L39.2,68.2 Z", 0xFFEE7B5E, null, 3.6f),
        StickerPiece("M28.9,56 L56.5,32.8 L66.8,45.1 L39.2,68.2 Z", null, 0xFF1E2A44, 3.6f),
        StickerPiece("M45.7,41.8 L49,45.7", null, 0xFF1E2A44, 2.6f),
        StickerPiece("M50.3,38 L53.6,41.8", null, 0xFF1E2A44, 2.6f),
        StickerPiece("M21.8,72.4 L34,62.1", null, 0xFF1E2A44, 3.2f)
    ),
    "microscope" to listOf(
        StickerPiece("M49.2,12.5 L62.4,17.2 L52.8,43.5 L39.6,38.8 Z", 0xFFD6E2F6, 0xFF1E2A44, 3.6f),
        StickerPiece("M47.3,40.5 L56.7,43.9 L54.7,49.5 L45.3,46.1 Z", 0xFFFFFFFF, 0xFF1E2A44, 3.6f),
        StickerPiece("M62,36 C78,46 78,70 58,78", null, 0xFF1E2A44, 6f),
        StickerPiece("M30,80 L72,80 L72,88 L30,88 Z", 0xFF9CB8E8, 0xFF1E2A44, 3.6f),
        StickerPiece("M26,66 L48,66 L48,70 L26,70 Z", 0xFFFFFFFF, 0xFF1E2A44, 3.6f),
        StickerPiece("M44,70 L52,80", null, 0xFF1E2A44, 3f)
    ),
    "flask" to listOf(
        StickerPiece("M42,14 L58,14 L58,38 L78,76 C82,84 78,88 70,88 L30,88 C22,88 18,84 22,76 L42,38 Z", 0xFFFFFFFF, 0xFF1E2A44, 3.6f),
        StickerPiece("M33,60 L67,60 L78,76 C82,84 78,88 70,88 L30,88 C22,88 18,84 22,76 Z", 0xFF86C79A, 0xFF1E2A44, 3.6f),
        StickerPiece("M38,14 L62,14", null, 0xFF1E2A44, 3.6f),
        StickerPiece("M47,74 L46.9,74.7 L46.7,75.3 L46.3,75.9 L45.9,76.3 L45.3,76.7 L44.7,76.9 L44,77 L43.3,76.9 L42.7,76.7 L42.1,76.3 L41.7,75.9 L41.3,75.3 L41.1,74.7 L41,74 L41.1,73.3 L41.3,72.7 L41.7,72.1 L42.1,71.7 L42.7,71.3 L43.3,71.1 L44,71 L44.7,71.1 L45.3,71.3 L45.9,71.7 L46.3,72.1 L46.7,72.7 L46.9,73.3 L47,74 Z", 0xFFFFFFFF, 0xFF1E2A44, 2.4f),
        StickerPiece("M58.4,68 L58.3,68.5 L58.2,69 L57.9,69.5 L57.5,69.9 L57,70.2 L56.5,70.3 L56,70.4 L55.5,70.3 L55,70.2 L54.5,69.9 L54.1,69.5 L53.8,69 L53.7,68.5 L53.6,68 L53.7,67.5 L53.8,67 L54.1,66.5 L54.5,66.1 L55,65.8 L55.5,65.7 L56,65.6 L56.5,65.7 L57,65.8 L57.5,66.1 L57.9,66.5 L58.2,67 L58.3,67.5 L58.4,68 Z", 0xFFFFFFFF, 0xFF1E2A44, 2.4f)
    ),
    "book" to listOf(
        StickerPiece("M50,26 C40,19 26,19 14,24 L14,76 C26,71 40,71 50,78 Z", 0xFFFFFFFF, 0xFF1E2A44, 3.6f),
        StickerPiece("M50,26 C60,19 74,19 86,24 L86,76 C74,71 60,71 50,78 Z", 0xFFF4C95D, 0xFF1E2A44, 3.6f),
        StickerPiece("M22,34 C30,32 38,33 43,36", null, 0xFF1E2A44, 2.6f),
        StickerPiece("M22,46 C30,44 38,45 43,48", null, 0xFF1E2A44, 2.6f),
        StickerPiece("M57,36 C62,33 70,32 78,34", null, 0xFF1E2A44, 2.6f),
        StickerPiece("M57,48 C62,45 70,44 78,46", null, 0xFF1E2A44, 2.6f)
    ),
    "pencil" to listOf(
        StickerPiece("M78.2,11.4 L87.8,18.6 L56.6,60.2 L47,53 Z", 0xFFF4C95D, 0xFF1E2A44, 3.6f),
        StickerPiece("M47,53 L56.6,60.2 L41,71 Z", 0xFFF3DDB3, 0xFF1E2A44, 3.6f),
        StickerPiece("M78.2,11.4 L87.8,18.6 L82.4,25.8 L72.8,18.6 Z", 0xFFEC8FA3, 0xFF1E2A44, 3.6f),
        StickerPiece("M42.8,64.2 L47,67.4 L41,71 Z", 0xFF1E2A44, null, 3.6f)
    ),
    "star" to listOf(
        StickerPiece("M50,16 L58.8,39.9 L84.2,40.9 L64.3,56.6 L71.2,81.1 L50,67 L28.8,81.1 L35.7,56.6 L15.8,40.9 L41.2,39.9 Z", 0xFFF4C95D, 0xFF1E2A44, 3.6f)
    ),
    "leaf" to listOf(
        StickerPiece("M18,82 C16,42 42,18 84,16 C86,56 62,84 22,84 Z", 0xFF86C79A, 0xFF1E2A44, 3.6f),
        StickerPiece("M22,80 C40,58 56,40 78,22", null, 0xFF1E2A44, 3f)
    ),
    "bolt" to listOf(
        StickerPiece("M58,10 L26,54 L46,54 L40,90 L74,44 L54,44 Z", 0xFFF4C95D, 0xFF1E2A44, 3.6f)
    ),
    "target" to listOf(
        StickerPiece("M86,50 L85.1,58 L82.4,65.6 L78.1,72.4 L72.4,78.1 L65.6,82.4 L58,85.1 L50,86 L42,85.1 L34.4,82.4 L27.6,78.1 L21.9,72.4 L17.6,65.6 L14.9,58 L14,50 L14.9,42 L17.6,34.4 L21.9,27.6 L27.6,21.9 L34.4,17.6 L42,14.9 L50,14 L58,14.9 L65.6,17.6 L72.4,21.9 L78.1,27.6 L82.4,34.4 L85.1,42 L86,50 Z", 0xFFFFFFFF, 0xFF1E2A44, 3.6f),
        StickerPiece("M75,50 L74.4,55.6 L72.5,60.8 L69.5,65.6 L65.6,69.5 L60.8,72.5 L55.6,74.4 L50,75 L44.4,74.4 L39.2,72.5 L34.4,69.5 L30.5,65.6 L27.5,60.8 L25.6,55.6 L25,50 L25.6,44.4 L27.5,39.2 L30.5,34.4 L34.4,30.5 L39.2,27.5 L44.4,25.6 L50,25 L55.6,25.6 L60.8,27.5 L65.6,30.5 L69.5,34.4 L72.5,39.2 L74.4,44.4 L75,50 Z", 0xFFEE7B5E, 0xFF1E2A44, 3.6f),
        StickerPiece("M64,50 L63.6,53.1 L62.6,56.1 L60.9,58.7 L58.7,60.9 L56.1,62.6 L53.1,63.6 L50,64 L46.9,63.6 L43.9,62.6 L41.3,60.9 L39.1,58.7 L37.4,56.1 L36.4,53.1 L36,50 L36.4,46.9 L37.4,43.9 L39.1,41.3 L41.3,39.1 L43.9,37.4 L46.9,36.4 L50,36 L53.1,36.4 L56.1,37.4 L58.7,39.1 L60.9,41.3 L62.6,43.9 L63.6,46.9 L64,50 Z", 0xFFFFFFFF, 0xFF1E2A44, 3.6f),
        StickerPiece("M55,50 L54.9,51.1 L54.5,52.2 L53.9,53.1 L53.1,53.9 L52.2,54.5 L51.1,54.9 L50,55 L48.9,54.9 L47.8,54.5 L46.9,53.9 L46.1,53.1 L45.5,52.2 L45.1,51.1 L45,50 L45.1,48.9 L45.5,47.8 L46.1,46.9 L46.9,46.1 L47.8,45.5 L48.9,45.1 L50,45 L51.1,45.1 L52.2,45.5 L53.1,46.1 L53.9,46.9 L54.5,47.8 L54.9,48.9 L55,50 Z", 0xFFEE7B5E, null, 3.6f)
    ),
    "trophy" to listOf(
        StickerPiece("M32,28 C16,28 16,48 34,52", null, 0xFF1E2A44, 3.6f),
        StickerPiece("M68,28 C84,28 84,48 66,52", null, 0xFF1E2A44, 3.6f),
        StickerPiece("M30,16 L70,16 L70,40 C70,56 58,64 50,64 C42,64 30,56 30,40 Z", 0xFFF4C95D, 0xFF1E2A44, 3.6f),
        StickerPiece("M50,64 L50,76", null, 0xFF1E2A44, 3.6f),
        StickerPiece("M34,76 L66,76 L66,86 L34,86 Z", 0xFFF4C95D, 0xFF1E2A44, 3.6f),
        StickerPiece("M38,24 L38,38", null, 0xFFFFFFFF, 3f)
    ),
    "globe" to listOf(
        StickerPiece("M84,50 L83.1,57.6 L80.6,64.8 L76.6,71.2 L71.2,76.6 L64.8,80.6 L57.6,83.1 L50,84 L42.4,83.1 L35.2,80.6 L28.8,76.6 L23.4,71.2 L19.4,64.8 L16.9,57.6 L16,50 L16.9,42.4 L19.4,35.2 L23.4,28.8 L28.8,23.4 L35.2,19.4 L42.4,16.9 L50,16 L57.6,16.9 L64.8,19.4 L71.2,23.4 L76.6,28.8 L80.6,35.2 L83.1,42.4 L84,50 Z", 0xFF9CB8E8, 0xFF1E2A44, 3.6f),
        StickerPiece("M50,16 C32,30 32,70 50,84 C68,70 68,30 50,16 Z", null, 0xFF1E2A44, 3f),
        StickerPiece("M16,50 L84,50", null, 0xFF1E2A44, 3f),
        StickerPiece("M22,32 L78,32", null, 0xFF1E2A44, 3f),
        StickerPiece("M22,68 L78,68", null, 0xFF1E2A44, 3f)
    )
)

/** يرسم الملصق بحجم مربع (size × size). */
@Composable
fun StickerIcon(key: String, size: Dp, modifier: Modifier = Modifier) {
    val pieces = StickerArt[key]
    if (pieces == null) {
        Text(key, fontSize = (size.value * 0.85f).sp, modifier = modifier)
        return
    }
    val paths = remember(key) { pieces.map { PathParser().parsePathString(it.d).toPath() } }
    Canvas(modifier.size(size)) {
        val k = this.size.width / 100f
        scale(scale = k, pivot = Offset.Zero) {
            pieces.forEachIndexed { i, p ->
                p.fill?.let { drawPath(paths[i], Color(it)) }
                p.stroke?.let {
                    drawPath(paths[i], Color(it), style = Stroke(width = p.w, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

/** ملصق غلاف كصورة شفافة مربعة (لوضعه على الصفحة كملصق). null إن لم يكن من الرسومات. */
internal fun coverStickerBitmap(key: String, px: Int): android.graphics.Bitmap? {
    val pieces = StickerArt[key] ?: return null
    val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.scale(px / 100f, px / 100f)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    for (p in pieces) {
        val path = PathParser().parsePathString(p.d).toPath().asAndroidPath()
        p.fill?.let {
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = it.toInt()
            c.drawPath(path, paint)
        }
        p.stroke?.let {
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = p.w
            paint.color = it.toInt()
            c.drawPath(path, paint)
        }
    }
    return bmp
}
