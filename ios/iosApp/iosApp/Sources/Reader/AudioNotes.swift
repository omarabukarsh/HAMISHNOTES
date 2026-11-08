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
                    AppController.shared.say(.error, L("اسمح باستخدام الميكروفون من إعدادات الجهاز", "Allow microphone access in the device settings"))
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
            AppController.shared.say(.error, L("تعذر بدء التسجيل", "Could not start recording"))
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
        AppController.shared.say(.tip, L("بدأ التسجيل — اكتب وسيُربط كل خط بلحظته", "Recording — every stroke is linked to its moment"))
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
        AppController.shared.say(.done, L("حُفظ التسجيل. تجده في درج الصفحات ← التسجيلات", "Recording saved. Find it in the pages drawer → Recordings"))
    }

    func play(_ s: DocSession, _ rec: AudioRec, at offset: TimeInterval = 0) {
        stopPlayback()
        let url = Repository.shared.mediaDir(s.id).appendingPathComponent(rec.file)
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
        guard let p = try? AVAudioPlayer(contentsOf: url) else {
            AppController.shared.say(.error, L("تعذر تشغيل التسجيل", "Could not play the recording"))
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
            AppController.shared.say(.done, L("لم يُكتب هذا أثناء تسجيل", "This wasn’t written during a recording"))
        }
    }
}

/// نافذة التسجيل.
struct AudioDialog: View {
    @ObservedObject var session: DocSession
    @ObservedObject var audio = AudioNotes.shared
    let dismiss: () -> Void

    var body: some View {
        let recHere = audio.recordingDoc == session.id
        let busyElsewhere = audio.recordingDoc != nil && !recHere
        let recs = session.data.recordings.sorted { $0.start > $1.start }
        AlertDialogCard(title: L("تسجيل المحاضرة", "Lecture recording"), width: 460) {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Text(L("سجّل الصوت أثناء الكتابة. لاحقًا حدّد أي كتابة بأداة التحديد الحر واضغط «▶ الصوت» لتسمع ما قيل لحظة كتابتها.",
                           "Record audio while you write. Later, lasso any writing and tap “▶ Audio” to hear what was said when you wrote it."))
                        .font(Fonts.sans(13)).foregroundColor(Palette.textSecondary).lineSpacing(4)
                        .fixedSize(horizontal: false, vertical: true)
                    if busyElsewhere {
                        Text(L("يوجد تسجيل يعمل في مستند آخر", "A recording is running in another document")).font(Fonts.sans(13)).foregroundColor(Palette.danger)
                    }
                    if recs.isEmpty {
                        Text(L("لا توجد تسجيلات بعد", "No recordings yet")).font(Fonts.sans(14)).foregroundColor(Palette.textSecondary).padding(.top, 4)
                    }
                    ForEach(recs) { r in
                        HStack(spacing: 4) {
                            Text(AudioDialog.fmt.string(from: Date(timeIntervalSince1970: Double(r.start) / 1000)) + "  ·  " + mmss(r.durationMs))
                                .font(Fonts.sans(14)).foregroundColor(Palette.textPrimary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            DialogButton(label: "▶") { audio.play(session, r); dismiss() }
                            DialogButton(label: L("حذف", "Delete"), color: Palette.danger) { audio.delete(session, r) }
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(maxHeight: 420)
            .fixedSize(horizontal: false, vertical: true)
        } buttons: {
            DialogButton(label: L("إغلاق", "Close")) { dismiss() }
            if recHere {
                DialogButton(label: L("إيقاف التسجيل", "Stop recording") + "  " + mmss(audio.elapsed), color: Palette.danger, bold: true) {
                    audio.stop(); dismiss()
                }
            } else {
                DialogButton(label: L("● بدء التسجيل", "● Start recording"), color: Palette.danger, bold: true, enabled: !busyElsewhere) {
                    audio.start(session); dismiss()
                }
            }
        }
    }

    static let fmt: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "d/M  HH:mm"
        return f
    }()
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
    @State private var removeInk = true
    @State private var source: [PKStroke] = []
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        SheetScaffold(title: L("تحويل الكتابة إلى نص", "Handwriting to text")) {
            VStack(alignment: .leading, spacing: 12) {
                if busy {
                    ProgressView().frame(maxWidth: .infinity).padding(30)
                } else if text.isEmpty {
                    Text(L("لم يُعثر على كتابة واضحة في هذه الصفحة.", "No clear handwriting was found on this page.")).font(Fonts.sans(14)).foregroundColor(Palette.textSecondary)
                } else {
                    TextEditor(text: $text).font(Fonts.sans(16)).frame(minHeight: 200).scrollContentBackground(.hidden)
                        .padding(8).background(RoundedRectangle(cornerRadius: 12).fill(Color.white)).roundedBorder(Palette.divider, radius: 12)
                    // كما في أندرويد: «إدراج كنص» في مكان الكتابة، مع حذف الكتابة اليدوية بعد الإدراج
                    Toggle(isOn: $removeInk) { Text(L("حذف الكتابة اليدوية بعد الإدراج", "Remove the handwriting after inserting")).font(Fonts.sans(13)) }
                        .tint(Palette.navy)
                    HStack {
                        NavyButton(label: L("نسخ", "Copy"), filled: false) { UIPasteboard.general.string = text; app.say(.done, L("تم النسخ", "Copied")) }
                        Spacer()
                        NavyButton(label: L("إدراج كنص", "Insert as text")) { insert(); dismiss() }
                    }
                }
            }
            .padding(.horizontal, 22).padding(.bottom, 20)
        }
        .environment(\.colorScheme, .light)
        .onAppear(perform: recognize)
    }

    /// يضع النص مكان الكتابة (بخط وحجم يناسبانها)، ويحذف الكتابة إن طُلب، خطوةً واحدة في التراجع.
    private func insert() {
        guard let pid = session.pageId(session.currentPage), !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        let page = session.currentPage
        let lines = text.components(separatedBy: "\n").filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        let alive = session.aliveStrokes(pid, source)
        if alive.isEmpty {
            let size = session.pageSize(page)
            session.upsertBox(TextBox(id: UUID().uuidString, page: pid, x: size.width * 0.08, y: size.height * 0.08,
                                      w: size.width * 0.84, text: text, size: max(12, size.width * 0.026)))
            return
        }
        let f = session.fitText(page: page, strokes: alive, lines: lines, font: "", snap: false)
        let color = alive.first.map { $0.ink.color.argb } ?? 0xFF1A2230
        let box = TextBox(id: UUID().uuidString, page: pid, x: f.x, y: f.y, w: f.w, text: lines.joined(separator: "\n"), color: color, size: f.size)
        if removeInk { session.inkToTextBox(pageId: pid, strokes: alive, box: box) } else { session.upsertBox(box) }
    }

    private func recognize() {
        guard let pid = session.pageId(session.currentPage) else { busy = false; return }
        let d = lassoOnly ? (session.lassoDrawing() ?? PKDrawing()) : session.drawing(for: pid)
        guard !d.strokes.isEmpty else { busy = false; return }
        source = d.strokes.filter(HandwritingWords.eligible)
        Task { @MainActor in
            text = await HandwritingReader.read(d.strokes, lang: nil).joined(separator: "\n")
            busy = false
        }
    }
}
