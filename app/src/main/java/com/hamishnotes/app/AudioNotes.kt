package com.hamishnotes.app

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * تسجيل صوت المحاضرة مربوطًا بالكتابة: كل خط يُحفظ معه وقت كتابته،
 * فالنقر على خط كُتب أثناء التسجيل يشغّل الصوت من تلك اللحظة.
 */
object AudioNotes {
    class Rec(val id: String, val docId: String, val start: Long, val durationMs: Long)

    private lateinit var ctx: Context
    private val recs = mutableStateMapOf<String, List<Rec>>()

    var recordingDoc by mutableStateOf<String?>(null)
        private set
    var recElapsed by mutableStateOf(0L)
        private set
    private var recStart = 0L
    private var recId = ""
    private var recorder: MediaRecorder? = null
    private var tickJob: Job? = null

    var playing by mutableStateOf<Rec?>(null)
        private set
    var playPos by mutableStateOf(0L)
        private set
    var paused by mutableStateOf(false)
        private set
    private var player: MediaPlayer? = null
    private var playJob: Job? = null

    fun init(context: Context) {
        ctx = context.applicationContext
    }

    private fun dir(docId: String) = File(File(ctx.filesDir, "audio"), docId).also { it.mkdirs() }
    private fun fileOf(r: Rec) = File(dir(r.docId), r.id + ".m4a")

    /** مجلد تسجيلات المستند دون إنشائه (للفحص والحذف). */
    private fun dirOrNull(docId: String): File? = File(File(ctx.filesDir, "audio"), docId).takeIf { it.isDirectory }

    fun list(docId: String): List<Rec> {
        if (!recs.containsKey(docId)) {
            val d = dirOrNull(docId)
            val f = if (d != null) File(d, "index.json") else null
            val loaded = try {
                if (f == null || !f.exists()) emptyList() else {
                    val a = JSONArray(f.readText())
                    (0 until a.length()).map { i ->
                        val o = a.getJSONObject(i)
                        Rec(o.getString("id"), docId, o.getLong("s"), o.getLong("d"))
                    }
                }
            } catch (t: Throwable) {
                emptyList()
            }
            // تسجيل بلا مدة: بدأ ولم يُوقَف (أُغلق التطبيق أثناءه). نقرأ مدته من الملف إن كان سليمًا، وإلا يُحذف
            val open = loaded.filter { it.durationMs <= 0L && !(recordingDoc == docId && it.id == recId) }
            if (open.isEmpty()) recs[docId] = loaded
            else {
                val fixed = loaded.mapNotNull { r ->
                    if (r !in open) r
                    else {
                        val dur = durationOf(fileOf(r))
                        if (dur > 1500) Rec(r.id, docId, r.start, dur) else { fileOf(r).delete(); null }
                    }
                }
                save(docId, fixed)
            }
        }
        return recs[docId] ?: emptyList()
    }

    private fun durationOf(f: File): Long {
        if (!f.exists() || f.length() == 0L) return 0L
        val mmr = android.media.MediaMetadataRetriever()
        return try {
            mmr.setDataSource(f.absolutePath)
            mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (t: Throwable) {
            0L
        } finally {
            runCatching { mmr.release() }
        }
    }

    /** هل للمستند تسجيلات محفوظة؟ (لسؤال المستخدم عنها عند حذف المستند) */
    fun count(docId: String): Int = if (dirOrNull(docId) == null) 0 else list(docId).size

    /**
     * عند حذف المستند: keep=false تُحذف تسجيلاته؛ keep=true تُنقل إلى مجلد الموسيقى في الجهاز
     * (Music/especial notes) فتبقى في متناول المستخدم. يعيد عدد ما نُقل. خارج الخيط الرئيسي.
     */
    fun removeFor(docId: String, docName: String, keep: Boolean): Int {
        val d = dirOrNull(docId) ?: return 0
        val all = list(docId)
        var moved = 0
        var left = 0
        if (keep) {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH-mm", java.util.Locale.US)
            val base = docName.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60).ifBlank { "recording" }
            for (r in all) {
                val f = fileOf(r)
                if (!f.exists()) continue
                if (exportToMusic(f, base + " " + fmt.format(java.util.Date(r.start)) + ".m4a")) moved++ else left++
            }
        }
        recs.remove(docId)
        // ما تعذّر نقله يبقى في مكانه بدل أن يضيع
        if (left == 0) d.deleteRecursively()
        return moved
    }

    private fun exportToMusic(src: File, name: String): Boolean {
        // قبل Android 10 تحتاج الكتابة في مجلد الموسيقى إذن تخزين لا يطلبه التطبيق: يبقى الملف في مكانه
        if (Build.VERSION.SDK_INT < 29) return false
        val resolver = ctx.contentResolver
        var uri: android.net.Uri? = null
        return try {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_MUSIC + "/especial notes")
                put(android.provider.MediaStore.Audio.Media.IS_PENDING, 1)
            }
            uri = resolver.insert(
                android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY), values
            ) ?: return false
            (resolver.openOutputStream(uri) ?: throw java.io.IOException("output")).use { out -> src.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            true
        } catch (t: Throwable) {
            uri?.let { u -> runCatching { resolver.delete(u, null, null) } }
            false
        }
    }

    private fun save(docId: String, l: List<Rec>) {
        recs[docId] = l
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("s", it.start).put("d", it.durationMs)) }
        try {
            File(dir(docId), "index.json").writeText(a.toString())
        } catch (t: Throwable) {
        }
    }

    // ---------- التسجيل ----------

    fun start(docId: String): Boolean {
        if (recordingDoc != null || !Premium.confirm()) return false
        stopPlayback()
        recId = UUID.randomUUID().toString()
        val out = File(dir(docId), "$recId.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            recStart = System.currentTimeMillis()
            recordingDoc = docId
            recElapsed = 0L
            // يُسجَّل في الفهرس من الآن (بلا مدة): إن أُغلق التطبيق قبل الإيقاف يُستعاد التسجيل عند الفتح التالي
            save(docId, list(docId) + Rec(recId, docId, recStart, 0L))
            // خدمة أمامية: يبقى الميكروفون يعمل والشاشة مطفأة أو التطبيق في الخلفية، مع زر إيقاف في الإشعار
            RecordingService.start(ctx)
            tickJob = AppController.scope.launch {
                while (isActive) {
                    recElapsed = System.currentTimeMillis() - recStart
                    delay(500)
                }
            }
            true
        } catch (t: Throwable) {
            try {
                r.release()
            } catch (e: Throwable) {
            }
            out.delete()
            false
        }
    }

    fun stop() {
        val docId = recordingDoc ?: return
        tickJob?.cancel()
        val dur = System.currentTimeMillis() - recStart
        try {
            recorder?.stop()
        } catch (t: Throwable) {
        }
        try {
            recorder?.release()
        } catch (t: Throwable) {
        }
        recorder = null
        recordingDoc = null
        tickJob = null
        val others = list(docId).filter { it.id != recId }
        if (dur > 1500) save(docId, others + Rec(recId, docId, recStart, dur))
        else {
            File(dir(docId), "$recId.m4a").delete()
            save(docId, others)
        }
        RecordingService.stop(ctx)
    }

    fun delete(r: Rec) {
        if (playing?.id == r.id) stopPlayback()
        fileOf(r).delete()
        save(r.docId, list(r.docId).filter { it.id != r.id })
    }

    // ---------- التشغيل ----------

    /** التسجيل الذي كان يعمل وقت كتابة خط (t)، إن وُجد. */
    fun recAt(docId: String, t: Long): Rec? =
        if (t <= 0) null else list(docId).firstOrNull { t >= it.start && t <= it.start + it.durationMs }

    fun play(r: Rec, fromMs: Long) {
        stopPlayback()
        val p = MediaPlayer()
        try {
            p.setDataSource(fileOf(r).absolutePath)
            p.prepare()
            p.seekTo(fromMs.coerceIn(0L, (r.durationMs - 500).coerceAtLeast(0L)).toInt())
            p.start()
            p.setOnCompletionListener { stopPlayback() }
            player = p
            playing = r
            paused = false
            playJob = AppController.scope.launch {
                while (isActive) {
                    playPos = try {
                        p.currentPosition.toLong()
                    } catch (t: Throwable) {
                        0L
                    }
                    delay(400)
                }
            }
        } catch (t: Throwable) {
            p.release()
            AppController.say(MsgKind.ERROR, L("تعذر تشغيل التسجيل", "Could not play the recording"))
        }
    }

    fun togglePause() {
        val p = player ?: return
        if (paused) p.start() else p.pause()
        paused = !paused
    }

    fun seekBy(ms: Long) {
        val p = player ?: return
        p.seekTo((p.currentPosition + ms).coerceAtLeast(0L).toInt())
    }

    fun stopPlayback() {
        playJob?.cancel()
        try {
            player?.stop()
        } catch (t: Throwable) {
        }
        player?.release()
        player = null
        playing = null
        paused = false
    }
}

fun mmss(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

@androidx.compose.runtime.Composable
fun AudioPlayerBar(rec: AudioNotes.Rec, modifier: androidx.compose.ui.Modifier) {
    androidx.compose.material3.Surface(
        modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        color = androidx.compose.ui.graphics.Color.White, shadowElevation = 8.dp
    ) {
        androidx.compose.foundation.layout.Row(
            androidx.compose.ui.Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            androidx.compose.material3.TextButton(onClick = { AudioNotes.seekBy(-10_000) }) { androidx.compose.material3.Text("−10") }
            androidx.compose.material3.TextButton(onClick = { AudioNotes.togglePause() }) {
                androidx.compose.material3.Text(if (AudioNotes.paused) "▶" else "❚❚", color = Palette.Navy, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            androidx.compose.material3.TextButton(onClick = { AudioNotes.seekBy(10_000) }) { androidx.compose.material3.Text("+10") }
            androidx.compose.material3.Text(
                mmss(AudioNotes.playPos) + " / " + mmss(rec.durationMs),
                fontSize = 13.sp, color = Palette.TextSecondary,
                modifier = androidx.compose.ui.Modifier.padding(horizontal = 6.dp)
            )
            androidx.compose.material3.TextButton(onClick = { AudioNotes.stopPlayback() }) {
                androidx.compose.material3.Text(L("إيقاف", "Stop"), color = Palette.Danger)
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun AudioDialog(s: DocSession, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val recHere = AudioNotes.recordingDoc == s.id
    val busyElsewhere = AudioNotes.recordingDoc != null && !recHere
    fun begin() {
        if (AudioNotes.start(s.id)) {
            AppController.say(MsgKind.TIP, L("بدأ التسجيل — اكتب وسيُربط كل خط بلحظته", "Recording — every stroke is linked to its moment"))
            onDismiss()
        } else AppController.say(MsgKind.ERROR, L("تعذر بدء التسجيل", "Could not start recording"))
    }
    val perm = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { ok ->
        if (ok) begin() else AppController.say(MsgKind.ERROR, L("يلزم إذن الميكروفون للتسجيل", "Microphone permission is required"))
    }
    val recs = AudioNotes.list(s.id)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text(L("تسجيل المحاضرة", "Lecture recording")) },
        text = {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.heightIn(max = 420.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                androidx.compose.material3.Text(
                    L("سجّل الصوت أثناء الكتابة. لاحقًا حدّد أي كتابة بأداة التحديد الحر واضغط «▶ الصوت» لتسمع ما قيل لحظة كتابتها.",
                        "Record audio while you write. Later, lasso any writing and tap “▶ Audio” to hear what was said when you wrote it."),
                    fontSize = 13.sp, lineHeight = 19.sp, color = Palette.TextSecondary
                )
                if (busyElsewhere) {
                    androidx.compose.material3.Text(L("يوجد تسجيل يعمل في مستند آخر", "A recording is running in another document"), fontSize = 13.sp, color = Palette.Danger)
                }
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.size(10.dp))
                if (recs.isEmpty()) {
                    androidx.compose.material3.Text(L("لا توجد تسجيلات بعد", "No recordings yet"), fontSize = 14.sp, color = Palette.TextSecondary)
                }
                val fmt = java.text.SimpleDateFormat("d/M  HH:mm", java.util.Locale.getDefault())
                recs.sortedByDescending { it.start }.forEach { r ->
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        androidx.compose.material3.Text(
                            fmt.format(java.util.Date(r.start)) + "  ·  " + mmss(r.durationMs),
                            fontSize = 14.sp, color = Palette.TextPrimary, modifier = androidx.compose.ui.Modifier.weight(1f)
                        )
                        androidx.compose.material3.TextButton(onClick = { AudioNotes.play(r, 0L); onDismiss() }) { androidx.compose.material3.Text("▶") }
                        androidx.compose.material3.TextButton(onClick = { AudioNotes.delete(r) }) {
                            androidx.compose.material3.Text(L("حذف", "Delete"), color = Palette.Danger)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (recHere) {
                androidx.compose.material3.TextButton(onClick = { AudioNotes.stop(); onDismiss() }) {
                    androidx.compose.material3.Text(L("إيقاف التسجيل", "Stop recording") + "  " + mmss(AudioNotes.recElapsed), color = Palette.Danger, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            } else {
                androidx.compose.material3.TextButton(enabled = !busyElsewhere, onClick = {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) begin() else perm.launch(android.Manifest.permission.RECORD_AUDIO)
                }) { androidx.compose.material3.Text(L("● بدء التسجيل", "● Start recording"), color = Palette.Danger, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text(L("إغلاق", "Close")) } }
    )
}
