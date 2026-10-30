package com.mynotes.app

import android.content.Context
import androidx.compose.runtime.Composable
import java.io.File

/**
 * عروض الباوربوينت: في iPad لا توجد أداة تحويل يُرسل إليها الملف، فيُطلب من المستخدم
 * تصديره PDF من Keynote أو PowerPoint ثم فتحه هنا.
 */
object Presentations {
    private val exts = listOf(".ppt", ".pptx", ".pps", ".ppsx", ".pptm", ".key")

    fun isPresentation(@Suppress("UNUSED_PARAMETER") mime: String?, name: String?): Boolean =
        name != null && exts.any { name.lowercase().endsWith(it) }

    fun mimeFor(name: String): String =
        if (name.lowercase().endsWith(".ppt") || name.lowercase().endsWith(".pps")) "application/vnd.ms-powerpoint"
        else "application/vnd.openxmlformats-officedocument.presentationml.presentation"

    class Job(val name: String, val file: File, val mime: String)

    fun dir(ctx: Context) = File(ctx.cacheDir, "convert").also { it.mkdirs() }
}

@Composable
fun PresentationConvertDialog() {
    val c = AppController
    if (c.pptJob != null) {
        c.pptJob = null
        c.messages.tryEmit(
            L("لفتح عرض تقديمي: صدّره PDF من Keynote أو PowerPoint، ثم افتحه في هامش",
                "To open a presentation: export it as PDF from Keynote or PowerPoint, then open it in Hamish")
        )
    }
}
