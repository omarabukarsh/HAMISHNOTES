package com.mynotes.app

import androidx.compose.ui.graphics.Color

enum class Tool { PEN, ERASER }

data class Pt(val x: Float, val y: Float, val p: Float)

data class Ink(val pts: List<Pt>, val color: Color, val width: Float)

/**
 * أنواع المستندات. للإضافة لاحقًا (DOCX / PPTX) يكفي تحويل supported إلى true
 * وتوفير مسار فتح/تحويل للنوع الجديد في المستودع.
 */
enum class DocType(val ext: String, val mime: String, val supported: Boolean) {
    PDF("pdf", "application/pdf", true),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", false),
    PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", false);

    companion object {
        fun fromExt(ext: String): DocType? =
            values().firstOrNull { it.ext.equals(ext, ignoreCase = true) }
    }
}

data class DocEntry(
    val id: String,
    val name: String,
    val type: DocType,
    val fileName: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val addedAt: Long,
    val lastOpened: Long,
    val favorite: Boolean,
    val lastPage: Int
)

fun DocEntry.activityAt(): Long = maxOf(lastOpened, addedAt)

enum class LibraryFilter { RECENT, ALL, FAVORITES }

enum class SortMode { LAST_OPENED, NAME, DATE_ADDED }
