import SwiftUI
import AVFoundation
import Vision
import PencilKit

/// تسجيل المحاضرة: كل ما يُكتب أثناء التسجيل مربوط بلحظته (وقت إنشاء الخط في PencilKit).
final class AudioNotes: NSObject, ObservableObject, AVAudioPlayerDelegate {
    static let shared = AudioNotes()

    @Published private(set) var recordingDoc: String? = nil
    @Published private(set) var elapsed: Int64 = 0
    @Published var playing: (String, AudioRec)? = nil
    @Published var position: TimeInterval = 0
    @Published var isPaused = false

    private var recorder: AVAudioRecorder?
    private var player: AVAudioPlayer?
    private var startedAt: Int64 = 0
    private var file = ""
    private var timer: Timer?
    private weak var recSession: DocSession?

    func start(_ s: DocSession) {
        AVAudioSession.sharedInstance().requestRecordPermission { ok in
            DispatchQueue.main.async {
                guard ok else {
                    AppController.shared.message(L("اسمح باستخدام الميكروفون من إعدادات الجهاز", "Allow microphone access in the device settings"))
                    return
                }
                self.begin(s)
            }
        }
    }

    private func begin(_ s: DocSession) {
        stopPlayback()
        let av = AVAudioSession.sharedInstance()
        try? av.setCategory(.playAndRecord, mode: .spokenAudio, options: [.defaultToSpeaker, .allowBluetooth])
        try? av.setActive(true)
        file = "rec-\(UUID().uuidString).m4a"
        let url = Repository.shared.mediaDir(s.id).appendingPathComponent(file)
        let settings: [String: Any] = [
            AVFormatIDKey: Int(kAudioFormatMPEG4AAC), AVSampleRateKey: 32000, AVNumberOfChannelsKey: 1,
            AVEncoderAudioQualityKey: AVAudioQuality.medium.rawValue
        ]
        guard let r = try? AVAudioRecorder(url: url, settings: settings), r.record() else {
            AppController.shared.message(L("تعذر بدء التسجيل", "Could not start recording"))
            return
        }
        recorder = r
        recSession = s
        recordingDoc = s.id
        startedAt = nowMs()
        elapsed = 0
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            self.elapsed = nowMs() - self.startedAt
        }
    }

    func stop() {
        guard let r = recorder else { return }
        r.stop()
        timer?.invalidate()
        let rec = AudioRec(id: UUID().uuidString, start: startedAt, durationMs: nowMs() - startedAt, file: file)
        recSession?.edit { $0.recordings.append(rec) }
        recSession?.flush()
        recorder = nil
        recordingDoc = nil
        AppController.shared.message(L("حُفظ التسجيل. تجده في درج الصفحات ← التسجيلات", "Recording saved. Find it in the pages drawer → Recordings"))
    }

    func play(_ s: DocSession, _ rec: AudioRec, at offset: TimeInterval = 0) {
        stopPlayback()
        let url = Repository.shared.mediaDir(s.id).appendingPathComponent(rec.file)
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
        guard let p = try? AVAudioPlayer(contentsOf: url) else {
            AppController.shared.message(L("تعذر تشغيل التسجيل", "Could not play the recording"))
            return
        }
        p.delegate = self
        p.currentTime = max(0, min(p.duration - 0.5, offset))
        p.play()
        player = p
        playing = (s.id, rec)
        isPaused = false
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
            self?.position = self?.player?.currentTime ?? 0
        }
    }

    func togglePause() {
        guard let p = player else { return }
        if p.isPlaying { p.pause(); isPaused = true } else { p.play(); isPaused = false }
    }

    func seek(_ t: TimeInterval) {
        player?.currentTime = t
        position = t
    }

    var duration: TimeInterval { player?.duration ?? 0 }

    func stopPlayback() {
        player?.stop()
        player = nil
        if recorder == nil { timer?.invalidate() }
        playing = nil
    }

    func delete(_ s: DocSession, _ rec: AudioRec) {
        if playing?.1.id == rec.id { stopPlayback() }
        try? FileManager.default.removeItem(at: Repository.shared.mediaDir(s.id).appendingPathComponent(rec.file))
        s.edit { $0.recordings.removeAll { $0.id == rec.id } }
    }

    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        DispatchQueue.main.async { self.stopPlayback() }
    }

    /// يبدأ التشغيل من لحظة كتابة أحدث خط في الصفحة الحالية.
    func playFromInk(_ s: DocSession) {
        guard let pid = s.pageId(s.currentPage), let last = s.drawing(for: pid).strokes.last else { return }
        let t = Int64(last.path.creationDate.timeIntervalSince1970 * 1000)
        if let rec = s.data.recordings.first(where: { t >= $0.start && t <= $0.start + $0.durationMs }) {
            play(s, rec, at: TimeInterval(t - rec.start) / 1000 - 2)
        } else {
            AppController.shared.message(L("لم يُكتب هذا أثناء تسجيل", "This wasn’t written during a recording"))
        }
    }
}

/// نافذة التسجيل.
struct AudioSheet: View {
    @ObservedObject var session: DocSession
    @ObservedObject var audio = AudioNotes.shared
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let recHere = audio.recordingDoc == session.id
        SheetScaffold(title: L("تسجيل المحاضرة", "Lecture recording")) {
            VStack(spacing: 16) {
                Text(recHere ? mmss(audio.elapsed) : L("سجّل المحاضرة واكتب كالمعتاد؛ كل سطر تكتبه يُربط بلحظته في التسجيل.", "Record the lecture and write as usual; every line you write is linked to its moment in the recording."))
                    .font(recHere ? Fonts.display(40) : Fonts.sans(14)).foregroundColor(recHere ? Palette.danger : Palette.textSecondary)
                    .multilineTextAlignment(.center)
                HStack(spacing: 12) {
                    if recHere {
                        NavyButton(label: L("إيقاف وحفظ", "Stop and save"), danger: true) { audio.stop(); dismiss() }
                    } else if audio.recordingDoc != nil {
                        Text(L("يجري تسجيل في مستند آخر", "A recording is running in another document")).font(Fonts.sans(14)).foregroundColor(Palette.textSecondary)
                    } else {
                        NavyButton(label: L("ابدأ التسجيل", "Start recording")) { audio.start(session); dismiss() }
                    }
                    if !session.data.recordings.isEmpty {
                        NavyButton(label: L("اسمع ما قيل عند آخر سطر", "Hear what was said at the last line"), filled: false) { audio.playFromInk(session); dismiss() }
                    }
                }
            }
            .padding(.horizontal, 22).padding(.bottom, 20)
        }
        .presentationDetents([.height(260)])
    }
}

/// مشغّل التسجيل أسفل القارئ.
struct AudioPlayerBar: View {
    let docId: String
    let rec: AudioRec
    @ObservedObject var audio = AudioNotes.shared
    var body: some View {
        HStack(spacing: 12) {
            Button { audio.togglePause() } label: { PhIcon(audio.isPaused ? "ph_play" : "ph_pause", .white, 20).frame(width: 40, height: 40).background(Circle().fill(Palette.navy)) }
            Text(mmss(Int64(audio.position * 1000))).font(Fonts.sans(13)).foregroundColor(Palette.textPrimary)
            Slider(value: Binding(get: { audio.position }, set: { audio.seek($0) }), in: 0...max(1, audio.duration)).tint(Palette.navy).frame(width: 260)
            Text(mmss(rec.durationMs)).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
            Button { audio.stopPlayback() } label: { PhIcon("ph_close", Palette.textSecondary, 16).frame(width: 32, height: 32) }
        }
        .padding(.horizontal, 14).padding(.vertical, 8)
        .background(Capsule().fill(Color.white))
        .shadow(color: .black.opacity(0.18), radius: 10, y: 4)
    }
}

/// تحويل الكتابة اليدوية في الصفحة الحالية إلى نص (Vision على الجهاز).
struct HandwritingSheet: View {
    @ObservedObject var session: DocSession
    var lassoOnly = false
    @EnvironmentObject var app: AppController
    @State private var text = ""
    @State private var busy = true
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        SheetScaffold(title: L("تحويل الكتابة إلى نص", "Handwriting to text")) {
            VStack(alignment: .leading, spacing: 12) {
                if busy {
                    ProgressView().frame(maxWidth: .infinity).padding(30)
                } else if text.isEmpty {
                    Text(L("لم يُعثر على كتابة واضحة في هذه الصفحة.", "No clear handwriting was found on this page.")).font(Fonts.sans(14)).foregroundColor(Palette.textSecondary)
                } else {
                    TextEditor(text: $text).font(Fonts.sans(16)).frame(minHeight: 200)
                        .padding(8).background(RoundedRectangle(cornerRadius: 12).fill(Color.white)).roundedBorder(Palette.divider, radius: 12)
                    HStack {
                        NavyButton(label: L("نسخ", "Copy"), filled: false) { UIPasteboard.general.string = text; app.message(L("تم النسخ", "Copied")) }
                        Spacer()
                        NavyButton(label: L("إضافة كنص على الصفحة", "Add as text on the page")) {
                            guard let pid = session.pageId(session.currentPage) else { return }
                            let size = session.pageSize(session.currentPage)
                            session.upsertBox(TextBox(id: UUID().uuidString, page: pid, x: size.width * 0.08, y: size.height * 0.08,
                                                      w: size.width * 0.84, text: text, size: max(12, size.width * 0.026)))
                            dismiss()
                        }
                    }
                }
            }
            .padding(.horizontal, 22).padding(.bottom, 20)
        }
        .onAppear(perform: recognize)
    }

    private func recognize() {
        guard let pid = session.pageId(session.currentPage) else { busy = false; return }
        let d = lassoOnly ? (session.lassoDrawing() ?? PKDrawing()) : session.drawing(for: pid)
        guard !d.strokes.isEmpty else { busy = false; return }
        Task { @MainActor in
            text = await HandwritingReader.read(d.strokes, lang: nil).joined(separator: "\n")
            busy = false
        }
    }
}
