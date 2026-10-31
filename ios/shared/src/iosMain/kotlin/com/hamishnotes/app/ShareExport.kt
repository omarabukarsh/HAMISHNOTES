package com.hamishnotes.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * مشاركة أو تصدير مستند: نسخة قابلة للتعديل (تُعدَّل كتابتها عند فتحها في التطبيق)
 * أو نسخة نهائية؛ ثم مشاركة مباشرة (تلغرام، واتساب…) أو حفظ على الجهاز. وكذلك الملخصات.
 */
@Composable
fun ShareExportDialog(docId: String, onDismiss: () -> Unit) {
    val c = AppController
    val ctx = LocalContext.current
    val entry = c.library.firstOrNull { it.id == docId } ?: return
    var editable by remember { mutableStateOf(true) }
    val docSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        if (uri != null) c.shareOrSaveDoc(ctx, docId, editable, uri)
    }
    val sumSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        if (uri != null) c.shareOrSaveSummary(ctx, docId, uri)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("مشاركة أو تصدير", "Share or export")) },
        text = {
            Column {
                Text(entry.name, fontSize = 13.sp, color = Palette.TextSecondary, maxLines = 1)
                Spacer(Modifier.height(12.dp))
                Text(L("نوع النسخة", "Copy type"), fontSize = 13.sp, color = Palette.TextSecondary)
                Spacer(Modifier.height(6.dp))
                CopyOption(
                    L("قابلة للتعديل", "Editable"),
                    L("تظهر كتابتك في أي تطبيق، وعند فتحها في هامش تعود الكتابة والتمييز والملاحظات قابلة للتعديل.",
                        "Your writing shows in any app, and when opened in Hamish the writing, highlights and notes become editable again."),
                    editable
                ) { editable = true }
                Spacer(Modifier.height(8.dp))
                CopyOption(
                    L("نهائية", "Final"),
                    L("الكتابة مثبتة في الصفحات ولا يمكن تعديلها؛ مناسبة لإرسالها للآخرين.",
                        "Writing is fixed into the pages and can’t be edited; good for sending to others."),
                    !editable
                ) { editable = false }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onDismiss(); c.shareOrSaveDoc(ctx, docId, editable, null) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy)
                    ) {
                        PhIcon(R.drawable.ph_export, Color.White, 18.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(L("مشاركة", "Share"))
                    }
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            docSaver.launch(entry.name + if (editable) ".pdf" else L(" - نهائي.pdf", " - final.pdf"))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(L("حفظ على الجهاز", "Save to device"), color = Palette.Navy) }
                }
                Text(
                    L("المشاركة تفتح قائمة التطبيقات: تلغرام، واتساب، البريد…", "Share opens the app list: Telegram, WhatsApp, email…"),
                    fontSize = 11.sp, color = Palette.TextSecondary, modifier = Modifier.padding(top = 6.dp)
                )
                if (UiPrefs.summariesOn) {
                    Spacer(Modifier.height(14.dp))
                    Text(L("الملخصات", "Summaries"), fontSize = 13.sp, color = Palette.TextSecondary)
                    Row {
                        TextButton(onClick = { onDismiss(); c.shareOrSaveSummary(ctx, docId, null) }) {
                            Text(L("مشاركة الملخصات", "Share summaries"), color = Palette.Navy)
                        }
                        TextButton(onClick = { onDismiss(); sumSaver.launch(L("ملخص ", "Summary ") + entry.name + ".pdf") }) {
                            Text(L("حفظ الملخصات", "Save summaries"), color = Palette.Navy)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("إغلاق", "Close")) } }
    )
}

@Composable
private fun CopyOption(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) Palette.NavySoft else Color.Transparent)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Palette.Navy else Palette.Divider, shape)
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.padding(top = 10.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
            Text(desc, fontSize = 12.sp, lineHeight = 17.sp, color = Palette.TextSecondary)
        }
    }
}
