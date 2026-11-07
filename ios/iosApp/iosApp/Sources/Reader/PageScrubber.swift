import SwiftUI
import PDFKit

// شريط التمرير بالصفحات على حافة القارئ (نفس PageScrubber في أندرويد):
// مقبض تسحبه لتمر بالصفحات بسرعة، مع فقاعة معاينة فيها صورة الصفحة ورقمها.

struct PageScrubber: View {
    @Environment(\.colorScheme) private var themeTick // يعيد الرسم عند تغيير الثيم
    @ObservedObject var session: DocSession
    @State private var dragging = false
    @State private var dragY: CGFloat = 0
    @State private var dragPage = 0
    @State private var startY: CGFloat = 0

    private let handleH: CGFloat = 54

    var body: some View {
        let n = session.pageCount
        if n >= 2 {
            GeometryReader { geo in
                let range = max(1, geo.size.height - handleH)
                let last = CGFloat(n - 1)
                let y = dragging ? dragY : range * CGFloat(session.currentPage) / last
                ZStack(alignment: .topTrailing) {
                    if dragging {
                        preview(page: dragPage, n: n)
                            .offset(x: -34, y: max(0, y - handleH))
                    }
                    RoundedRectangle(cornerRadius: 8)
                        .fill(dragging ? Palette.navy : Palette.navy.opacity(0.35))
                        .frame(width: 18, height: handleH)
                        .padding(.trailing, 4)
                        .frame(width: 44, height: handleH, alignment: .trailing)
                        .contentShape(Rectangle())
                        .offset(y: y)
                        .gesture(
                            DragGesture(minimumDistance: 2, coordinateSpace: .local)
                                .onChanged { g in
                                    if !dragging {
                                        dragging = true
                                        startY = range * CGFloat(session.currentPage) / last
                                    }
                                    dragY = min(max(0, startY + g.translation.height), range)
                                    let p = Int((dragY / range * last).rounded())
                                    if p != dragPage {
                                        dragPage = p
                                        session.goTo(page: p)
                                    }
                                }
                                .onEnded { _ in
                                    session.goTo(page: dragPage)
                                    withAnimation(Motion.exit) { dragging = false }
                                }
                        )
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
            }
            .frame(width: 150)
            .padding(.vertical, 10)
        }
    }

    private func preview(page: Int, n: Int) -> some View {
        let size = session.pageSize(page)
        let img = session.pdf?.page(at: page)?.thumbnail(of: CGSize(width: 184, height: 184 * size.height / max(1, size.width)), for: .cropBox)
        return VStack(spacing: 4) {
            if let img = img {
                Image(uiImage: img).resizable().aspectRatio(size.width / max(1, size.height), contentMode: .fit)
            } else {
                Rectangle().fill(Palette.background).aspectRatio(size.width / max(1, size.height), contentMode: .fit)
            }
            Text("\(page + 1) / \(n)").font(Fonts.sans(13, .bold)).foregroundColor(Palette.navy)
        }
        .padding(6)
        .frame(width: 104)
        .background(RoundedRectangle(cornerRadius: 10).fill(Color.white).shadow(color: .black.opacity(0.2), radius: 8, y: 3))
    }
}

/// عداد وقت القراءة لهذا المستند، في زاوية القارئ.
struct ReaderTimerBadge: View {
    @EnvironmentObject var app: AppController
    let docId: String
    var body: some View {
        ReadingTimeBadge(ms: app.entry(docId)?.readMs ?? 0, tint: Palette.navy, size: 12)
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(RoundedRectangle(cornerRadius: 14).fill(Color.white).shadow(color: .black.opacity(0.12), radius: 3, y: 1))
            .padding(14)
    }
}
