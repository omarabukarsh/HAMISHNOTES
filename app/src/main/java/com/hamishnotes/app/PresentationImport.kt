package com.hamishnotes.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File

/**
 * ملفات الباوربوينت: MyNotes لا يعرضها مباشرة، فيرسلها إلى أداة تحويل في الجهاز
 * (مثل «Export as PDF» من هواوي أو WPS)، ثم يفتح قائمة ملفات PDF لتختار الملف الناتج فيُضاف ويُفتح.
 */
object Presentations {
    private val mimes = setOf(
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
        "application/vnd.ms-powerpoint.presentation.macroenabled.12"
    )
    private val exts = listOf(".ppt", ".pptx", ".pps", ".ppsx", ".pptm")

    fun isPresentation(mime: String?, name: String?): Boolean =
        (mime != null && mime.lowercase() in mimes) ||
            (name != null && exts.any { name.lowercase().endsWith(it) })

    fun mimeFor(name: String): String =
        if (name.lowercase().endsWith(".ppt") || name.lowercase().endsWith(".pps")) "application/vnd.ms-powerpoint"
        else "application/vnd.openxmlformats-officedocument.presentationml.presentation"

    /** نسخة مؤقتة من العرض يمكن مشاركتها مع أداة التحويل. */
    class Job(val name: String, val file: File, val mime: String)

    fun dir(ctx: Context) = File(ctx.cacheDir, "convert").also { it.mkdirs() }

    fun shareUri(ctx: Context, f: File) = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)

    /** التطبيقات التي تستطيع فتح هذا العرض (عدا MyNotes نفسه). */
    fun converters(ctx: Context, job: Job): List<ResolveInfo> {
        val probe = Intent(Intent.ACTION_VIEW).setDataAndType(shareUri(ctx, job.file), job.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        @Suppress("DEPRECATION")
        val list = ctx.packageManager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
        return list.filter { it.activityInfo.packageName != ctx.packageName }
            .sortedBy { ri ->
                // أدوات التحويل المعروفة أولًا
                val label = ri.loadLabel(ctx.packageManager).toString().lowercase()
                when {
                    "pdf" in label -> 0
                    ri.activityInfo.packageName.startsWith("cn.wps") -> 1
                    else -> 2
                }
            }
    }

    fun launch(ctx: Context, job: Job, pkg: String, cls: String): Boolean = try {
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(shareUri(ctx, job.file), job.mime)
            .setClassName(pkg, cls)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(i)
        true
    } catch (t: Throwable) {
        false
    }
}

/** نافذة تحويل العرض إلى PDF: اختيار الأداة (مرة واحدة)، ثم اختيار ملف PDF الناتج عند الرجوع. */
@Composable
fun PresentationConvertDialog() {
    val c = AppController
    val job = c.pptJob ?: return
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("mynotes_session", Context.MODE_PRIVATE) }
    // 0 اختيار الأداة، 1 بانتظار الرجوع من التحويل، 2 اختيار ملف PDF الناتج
    var stage by remember(job) { mutableStateOf(0) }
    var launchedAt by remember(job) { mutableStateOf(0L) }
    var choosing by remember(job) { mutableStateOf(false) }
    val apps = remember(job) { Presentations.converters(ctx, job) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            c.pptJob = null
            c.openExternal(uri, "application/pdf")
        } else {
            stage = 2
        }
    }

    fun start(pkg: String, cls: String) {
        prefs.edit().putString("ppt_converter", "$pkg/$cls").apply()
        launchedAt = System.currentTimeMillis()
        if (Presentations.launch(ctx, job, pkg, cls)) {
            stage = 1
        } else {
            prefs.edit().remove("ppt_converter").apply()
            choosing = true
            c.say(MsgKind.ERROR, L("تعذر فتح أداة التحويل، اختر أداة أخرى", "Couldn’t open the converter; choose another"))
        }
    }

    // أداة محفوظة من قبل: تُستعمل مباشرة
    LaunchedEffect(job) {
        val saved = prefs.getString("ppt_converter", null)?.split("/")
        if (saved != null && saved.size == 2 && apps.any { it.activityInfo.packageName == saved[0] && it.activityInfo.name == saved[1] }) {
            start(saved[0], saved[1])
        } else {
            choosing = true
        }
    }

    // الرجوع إلى MyNotes بعد التحويل: تُفتح قائمة ملفات PDF مباشرة
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val stageNow by rememberUpdatedState(stage)
    DisposableEffect(lifecycle, job) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME && stageNow == 1 && System.currentTimeMillis() - launchedAt > 600L) {
                stage = 2
                picker.launch(arrayOf("application/pdf"))
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    fun cancel() {
        c.pptJob = null
        job.file.delete()
    }

    AlertDialog(
        onDismissRequest = { },
        title = { Text(L("تحويل عرض باوربوينت", "Convert PowerPoint")) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text(job.name, fontSize = 13.sp, color = Palette.TextSecondary)
                Spacer(Modifier.size(10.dp))
                when {
                    choosing -> {
                        Text(
                            L("اختر أداة تحوّل العرض إلى PDF (مرة واحدة، ويتذكرها «هامش»):",
                                "Choose an app that converts the presentation to PDF (once; Hamish remembers it):"),
                            fontSize = 14.sp, color = Palette.TextPrimary
                        )
                        Spacer(Modifier.size(8.dp))
                        if (apps.isEmpty()) {
                            Text(
                                L("لا توجد أداة تفتح ملفات الباوربوينت في جهازك. ثبّت WPS Office أو حوّل الملف إلى PDF بطريقة أخرى.",
                                    "No app on your device opens PowerPoint files. Install WPS Office or convert the file to PDF another way."),
                                fontSize = 13.sp, color = Palette.Danger
                            )
                        }
                        apps.forEach { ri ->
                            val label = remember(ri) { ri.loadLabel(ctx.packageManager).toString() }
                            val icon = remember(ri) {
                                try {
                                    (ri.loadIcon(ctx.packageManager) as Drawable).toBitmap(96, 96).asImageBitmap()
                                } catch (t: Throwable) {
                                    null
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .clickable { choosing = false; start(ri.activityInfo.packageName, ri.activityInfo.name) }
                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (icon != null) Image(icon, null, Modifier.size(34.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(label, fontSize = 15.sp, color = Palette.TextPrimary)
                            }
                        }
                    }
                    stage == 1 -> Text(
                        L("حوّل العرض إلى PDF في الأداة التي فُتحت (في WPS: القائمة ← تصدير إلى PDF)، ثم ارجع إلى «هامش» وسيفتح لك قائمة ملفات PDF.",
                            "Convert the presentation to PDF in the app that opened (in WPS: menu → Export to PDF), then return to Hamish and it will show your PDF files."),
                        fontSize = 14.sp, lineHeight = 21.sp, color = Palette.TextPrimary
                    )
                    else -> Text(
                        L("اختر ملف PDF الناتج من التحويل (يكون عادة في أعلى قائمة الملفات الحديثة).",
                            "Choose the converted PDF file (usually at the top of recent files)."),
                        fontSize = 14.sp, lineHeight = 21.sp, color = Palette.TextPrimary
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!choosing && stage >= 1) {
                    TextButton(onClick = { picker.launch(arrayOf("application/pdf")) }) {
                        Text(L("اختيار ملف PDF", "Choose PDF"), fontWeight = FontWeight.Bold, color = Palette.Navy)
                    }
                }
            }
        },
        dismissButton = {
            Row {
                if (!choosing) {
                    TextButton(onClick = { choosing = true }) { Text(L("تغيير الأداة", "Change app"), color = Palette.TextSecondary) }
                }
                TextButton(onClick = { cancel() }) { Text(L("إلغاء", "Cancel")) }
            }
        }
    )
}
