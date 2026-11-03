import SwiftUI
import Shared

/// Modo GUÍA de la pestaña BLOQUES: la escuela como una guía de papel. Sector a sector (tarjetas plegables con tu
/// progreso) y piedra a piedra, en el orden en que te las encuentras andando (lo calcula `SchoolGuide`, shared).
/// Cada piedra: foto grande con sus líneas numeradas, sus vías con tu estado (hecha / proyecto / sin hacer),
/// y "Ver en mapa" / "Cómo llegar".
///
/// Esta vista SOLO pinta; los filtros (los mismos que la lista) y el estado viven en `SchoolBlocksExplorerModel`.
struct SchoolGuideView: View {
    let schoolId: String
    let guide: GuideView
    @ObservedObject var model: SchoolBlocksExplorerModel
    /// Abre la vía en el mapa de la ficha (con su piedra).
    let onOpen: (String) -> Void
    @Environment(\.schoolRestrictions) private var restrictionsUI
    @State private var zoomed: GuideBlock?

    var body: some View {
        LazyVStack(alignment: .leading, spacing: 10) {
            if guide.sectors.isEmpty {
                Text(L("Ninguna vía cumple esos filtros."))
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    .frame(maxWidth: .infinity).padding(.vertical, 24)
            }
            ForEach(guide.sectors, id: \.key) { s in sectorCard(s) }
        }
        .padding(.horizontal, 14).padding(.bottom, 16)
        .sheet(item: Binding(get: { zoomed.map(ZoomItem.init) }, set: { if $0 == nil { zoomed = nil } })) { item in
            GuideZoomSheet(block: item.block)
        }
    }

    // MARK: - Sector

    private func sectorCard(_ s: GuideSector) -> some View {
        let open = model.guideOpen.contains(s.key)
        return VStack(spacing: 0) {
            Button {
                withAnimation(.snappy(duration: 0.25)) {
                    if open { model.guideOpen.remove(s.key) } else { model.guideOpen.insert(s.key) }
                }
            } label: { sectorHeader(s, open: open) }
            .buttonStyle(.plain)
            if open {
                ForEach(Array(s.blocks.enumerated()), id: \.element.blockId) { i, b in
                    blockView(b, index: i + 1)
                }
            }
        }
        .background(Cumbre.paper)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14)
            .stroke(Cumbre.rule, style: StrokeStyle(lineWidth: 1, dash: s.unknownFace ? [4, 3] : [])))
    }

    private func sectorHeader(_ s: GuideSector, open: Bool) -> some View {
        let done = Int(s.doneCount(doneIds: model.doneIds))
        let total = Int(s.lineCount)
        let pieces = s.blocks.count == 1 ? L("1 piedra") : L("%@ piedras", s.blocks.count)
        let lines = total == 1 ? L("1 vía") : L("%@ vías", total)
        return HStack(spacing: 10) {
            Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.ink3)
                .rotationEffect(.degrees(open ? 90 : 0))
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(s.unknownFace ? L("Sin orientación votada") : (s.sector ?? L("Sin sector")))
                        .font(.system(size: 16, weight: .semibold)).foregroundStyle(Cumbre.ink)
                    if !s.unknownFace { RestrictionMarkButton(items: restrictionsUI?.allInSectorNamed(s.sector) ?? []) }
                }
                Text("\(pieces) · \(lines) · " + L("%@/%@ hechas", done, total))
                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
            }
            Spacer(minLength: 6)
            ProgressBar(value: total == 0 ? 0 : Double(done) / Double(total)).frame(width: 46, height: 5)
        }
        .padding(12)
        .contentShape(Rectangle())
    }

    // MARK: - Piedra

    private func blockView(_ b: GuideBlock, index: Int) -> some View {
        VStack(spacing: 0) {
            ZStack(alignment: .top) {
                GuideTopo(block: b, maxHeight: 240)
                HStack {
                    Text("\(index) · \(b.blockName.uppercased())")
                        .font(Cumbre.mono(10.5, .bold)).foregroundStyle(.white)
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .background(Color.black.opacity(0.6), in: Capsule())
                    RestrictionMarkButton(items: restrictionsUI?.forBlockId(b.blockId) ?? [])
                    Spacer()
                    if b.photoPath != nil {
                        Button { zoomed = b } label: {
                            Image(systemName: "arrow.up.left.and.arrow.down.right")
                                .font(.system(size: 12, weight: .bold)).foregroundStyle(.white)
                                .frame(width: 30, height: 30).background(Color.black.opacity(0.6), in: Circle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(L("Ver foto en grande"))
                    }
                }
                .padding(10)
            }
            .frame(maxWidth: .infinity)
            .background(Cumbre.rule.opacity(0.18))

            ForEach(Array(b.lines.enumerated()), id: \.element.lineId) { n, h in
                lineRow(h, number: n + 1)
            }

            HStack(spacing: 8) {
                actionButton(L("Ver en mapa"), icon: "map", filled: false) {
                    if let id = b.lines.first?.lineId { onOpen(id) }
                }
                if let lat = b.lat?.doubleValue, let lon = b.lon?.doubleValue {
                    actionButton(L("Cómo llegar"), icon: "arrow.triangle.turn.up.right.diamond", filled: true) {
                        openDirections(lat: lat, lon: lon)
                    }
                }
            }
            .padding(12)
            .overlay(alignment: .top) { Divider().overlay(Cumbre.rule) }
        }
        .overlay(alignment: .top) { Divider().overlay(Cumbre.rule) }
    }

    private func lineRow(_ h: LineSearchHit, number: Int) -> some View {
        let id = h.lineId ?? ""
        return HStack(spacing: 9) {
            Button { onOpen(id) } label: {
                HStack(spacing: 9) {
                    Text("\(number)").font(Cumbre.mono(10.5, .bold)).foregroundStyle(.white)
                        .frame(width: 20, height: 20).background(GradeColor.color(h.grade), in: Circle())
                    VStack(alignment: .leading, spacing: 2) {
                        Text(h.lineName ?? h.blockName).font(.system(size: 14.5, weight: .semibold))
                            .foregroundStyle(Cumbre.ink).lineLimit(2).multilineTextAlignment(.leading)
                        if let extra = meta(h) {
                            Text(extra).font(.system(size: 11.5)).foregroundStyle(Cumbre.ink3)
                        }
                    }
                    Spacer(minLength: 4)
                    GradePill(grade: h.grade, split: model.splits[id])
                }
                .contentShape(Rectangle())
            }.buttonStyle(.plain)
            tickButton(id)
        }
        .padding(.horizontal, 12).padding(.vertical, 9)
        .overlay(alignment: .top) { Divider().overlay(Cumbre.rule.opacity(0.6)) }
    }

    /// ✓ verde = hecha · ◐ naranja = proyecto · vacío = sin hacer. Tocar abre la piedra con la hoja de ascenso de esa
    /// vía ya lanzada (se guarda en tu diario con la lógica de siempre). Si ya está hecha, solo abre la piedra.
    private func tickButton(_ id: String) -> some View {
        let done = model.doneIds.contains(id)
        let project = model.projectIds.contains(id)
        return Button {
            if !done { GuideTickRequest.shared.request(id) }
            onOpen(id)
        } label: {
            ZStack {
                Circle().fill(done ? Cumbre.ok : Color.clear)
                Circle().stroke(done ? Cumbre.ok : (project ? Cumbre.warn : Cumbre.rule), lineWidth: 1.5)
                if done {
                    Image(systemName: "checkmark").font(.system(size: 12, weight: .bold)).foregroundStyle(.white)
                } else if project {
                    Image(systemName: "circle.lefthalf.filled").font(.system(size: 13)).foregroundStyle(Cumbre.warn)
                }
            }
            .frame(width: 28, height: 28)
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(done ? L("Hecha") : project ? L("Proyecto") : L("Marcar como hecha"))
    }

    private func actionButton(_ t: String, icon: String, filled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 12, weight: .semibold))
                Text(t).font(.system(size: 13, weight: .semibold))
            }
            .foregroundStyle(filled ? Color.white : Cumbre.ink)
            .frame(maxWidth: .infinity).padding(.vertical, 9)
            .background(filled ? Cumbre.terraFill : Color.clear, in: Capsule())
            .overlay(Capsule().stroke(filled ? Cumbre.terraFill : Cumbre.rule, lineWidth: 1))
        }.buttonStyle(.plain)
    }

    /// "★ 4.6 · ✓ 12" solo si hay algo que contar.
    private func meta(_ h: LineSearchHit) -> String? {
        var parts: [String] = []
        if let avg = h.rating?.doubleValue, (h.ratingCount?.intValue ?? 0) > 0 { parts.append("★ \(String(format: "%.1f", avg))") }
        if let r = h.repetitions?.intValue, r > 0 { parts.append("✓ \(r)") }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    private struct ZoomItem: Identifiable {
        let block: GuideBlock
        var id: String { block.blockId }
    }
}

/// Pide a la hoja de la piedra que, al abrirse, lance la hoja de ascenso de una vía (marcar desde la guía).
/// Un solo uso: quien lo recoge lo borra, así que abrir la piedra otra vez por otro camino no marca nada.
@MainActor
final class GuideTickRequest {
    static let shared = GuideTickRequest()
    private var lineId: String?
    func request(_ id: String) { lineId = id }
    func take() -> String? { defer { lineId = nil }; return lineId }
}

private struct ProgressBar: View {
    let value: Double
    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Cumbre.rule)
                Capsule().fill(Cumbre.ok).frame(width: geo.size.width * min(max(value, 0), 1))
            }
        }
    }
}

/// Foto de la piedra con TODAS sus líneas de esa cara, cada una con su número en el pie (como un topo impreso).
/// El marco toma la proporción de la foto para que los trazos (normalizados 0–1 sobre la foto) caigan en su sitio.
struct GuideTopo: View {
    let block: GuideBlock
    var maxHeight: CGFloat = 240
    var lineWidth: CGFloat = 3
    @State private var image: UIImage?

    var body: some View {
        let photo = block.photoPath ?? ""
        Group {
            if let image {
                ZStack {
                    Image(uiImage: image).resizable()
                    lines
                }
                .aspectRatio(image.size.width / max(image.size.height, 1), contentMode: .fit)
                .frame(maxHeight: maxHeight)
            } else {
                Cumbre.rule.opacity(0.25).frame(height: min(180, maxHeight))
            }
        }
        .frame(maxWidth: .infinity)
        .task(id: photo) { if !photo.isEmpty { image = await ImageCache.image(photo) } }
    }

    private var lines: some View {
        GeometryReader { geo in
            ForEach(Array(block.lines.enumerated()), id: \.element.lineId) { n, h in
                if h.photoPath == block.photoPath {
                    let pts = dedupPoints(TopoParse.points(h.linePath))
                        .map { CGPoint(x: $0.x * geo.size.width, y: $0.y * geo.size.height) }
                    if pts.count >= 2 {
                        Path { p in p.move(to: pts[0]); for q in pts.dropFirst() { p.addLine(to: q) } }
                            .stroke(GradeColor.color(h.grade),
                                    style: StrokeStyle(lineWidth: lineWidth, lineCap: .round, lineJoin: .round))
                            .shadow(color: .black.opacity(0.45), radius: 1)
                        // El pie de la vía es el punto más bajo del trazo.
                        let foot = pts.max(by: { $0.y < $1.y }) ?? pts[0]
                        Text("\(n + 1)").font(Cumbre.mono(10, .bold)).foregroundStyle(.white)
                            .frame(width: 18, height: 18)
                            .background(GradeColor.color(h.grade), in: Circle())
                            .overlay(Circle().stroke(.white, lineWidth: 1))
                            .position(x: foot.x, y: min(foot.y, geo.size.height - 10))
                    }
                }
            }
        }
    }
}

/// La foto de la piedra en grande, con zoom de pellizco.
private struct GuideZoomSheet: View {
    let block: GuideBlock
    @Environment(\.dismiss) private var dismiss
    @State private var scale: CGFloat = 1
    @State private var base: CGFloat = 1

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            GuideTopo(block: block, maxHeight: .infinity, lineWidth: 3.5)
                .scaleEffect(scale)
                .gesture(MagnifyGesture()
                    .onChanged { scale = min(max(base * $0.magnification, 1), 5) }
                    .onEnded { _ in base = scale })
                .onTapGesture(count: 2) { withAnimation { scale = 1; base = 1 } }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            Button { dismiss() } label: {
                Image(systemName: "xmark").font(.system(size: 14, weight: .bold)).foregroundStyle(.white)
                    .frame(width: 34, height: 34).background(Color.white.opacity(0.2), in: Circle())
            }
            .padding(16)
            .accessibilityLabel(L("Cerrar"))
        }
    }
}
