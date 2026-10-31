package android.media

import android.content.Context
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetooth
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSURL
import platform.darwin.NSObject

/*
 * بدائل MediaRecorder و MediaPlayer لتسجيل المحاضرة في iPad (AVAudioRecorder / AVAudioPlayer)،
 * بنفس الدوال التي يستخدمها AudioNotes.
 */

@OptIn(ExperimentalForeignApi::class)
class MediaRecorder() {
    constructor(@Suppress("UNUSED_PARAMETER") ctx: Context) : this()

    object AudioSource { const val MIC = 1 }
    object OutputFormat { const val MPEG_4 = 2 }
    object AudioEncoder { const val AAC = 3 }

    private var path = ""
    private var bitRate = 64_000
    private var sampleRate = 44_100
    private var rec: AVAudioRecorder? = null

    fun setAudioSource(@Suppress("UNUSED_PARAMETER") s: Int) {}
    fun setOutputFormat(@Suppress("UNUSED_PARAMETER") f: Int) {}
    fun setAudioEncoder(@Suppress("UNUSED_PARAMETER") e: Int) {}
    fun setAudioEncodingBitRate(b: Int) { bitRate = b }
    fun setAudioSamplingRate(r: Int) { sampleRate = r }
    fun setOutputFile(p: String) { path = p }

    fun prepare() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(
            AVAudioSessionCategoryPlayAndRecord,
            withOptions = AVAudioSessionCategoryOptionDefaultToSpeaker or AVAudioSessionCategoryOptionAllowBluetooth,
            error = null
        )
        session.setActive(true, null)
        val settings = mapOf<Any?, Any?>(
            AVFormatIDKey to kAudioFormatMPEG4AAC.toInt(),
            AVSampleRateKey to sampleRate.toDouble(),
            AVNumberOfChannelsKey to 1,
            AVEncoderBitRateKey to bitRate
        )
        val r = AVAudioRecorder(uRL = NSURL.fileURLWithPath(path), settings = settings, error = null)
        if (!r.prepareToRecord()) throw IllegalStateException("recorder")
        rec = r
    }

    fun start() {
        if (rec?.record() != true) throw IllegalStateException("record")
    }

    fun stop() {
        rec?.stop()
    }

    fun release() {
        rec = null
    }
}

@OptIn(ExperimentalForeignApi::class)
class MediaPlayer {
    private var player: AVAudioPlayer? = null
    private var path = ""
    private var onDone: (() -> Unit)? = null
    private val delegate = object : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            onDone?.invoke()
        }
    }

    fun setDataSource(p: String) { path = p }

    fun prepare() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setActive(true, null)
        val p = AVAudioPlayer(contentsOfURL = NSURL.fileURLWithPath(path), error = null)
        p.delegate = delegate
        if (!p.prepareToPlay()) throw IllegalStateException("player")
        player = p
    }

    fun seekTo(ms: Int) { player?.setCurrentTime(ms / 1000.0) }
    fun start() { player?.play() }
    fun pause() { player?.pause() }
    fun stop() { player?.stop() }
    fun release() { player = null }

    val currentPosition: Int get() = ((player?.currentTime ?: 0.0) * 1000.0).toInt()
    val duration: Int get() = ((player?.duration ?: 0.0) * 1000.0).toInt()

    fun setOnCompletionListener(l: (MediaPlayer) -> Unit) {
        onDone = { l(this) }
    }
}
