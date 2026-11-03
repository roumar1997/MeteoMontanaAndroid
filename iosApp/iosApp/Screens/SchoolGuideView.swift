import SwiftUI
import Shared

/// Modo GUÍA de la pestaña BLOQUES: la escuela como una guía de papel. Sector a sector y piedra a piedra, en el orden
/// en que te las encuentras andando (lo calcula `SchoolGuide`, shared). Cada piedra: foto mediana con sus líneas
/// numeradas a la izquierda y sus vías a la derecha. Tocar una vía abre su piedra en el mapa, como en la lista.
///
/// Esta vista SOLO pinta; los sectores ya ordenados vienen de `SchoolBlocksExplorerModel.guideSectors`.
struct SchoolGuideView: View {
    let sectors: [GuideSector]
    let splits: [String: GradeSplitInfo]
    let onOpen: (String) -> Void
    @Environment(\.schoolRestrictions) private var restrictionsUI

    var body: some View {
        LazyVStack(alignment: .leading, spacing: 0, pinnedViews: [.sectionHeaders]) {
            ForEach(sectors, id: \.sector) { s in
                Section {
                    ForEach(Array(s.blocks.enumerated()), id: \.element.blockId) { i, b in
                        blockRow(b, index: i + 1)
                    }
                } header: { header(s) }
            }
        }
    }

    private func header(_ s: GuideSector) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(s.sector ?? L("Sin sector")).font(Cumbre.serif(18, .semibold)).foregroundStyle(Cumbre.ink)
            RestrictionMarkButton(items: restrictionsUI?.allInSectorNamed(s.sector) ?? [])
            Spacer()
            Text(s.blocks.count == 1 ? L("1 PIEDRA") : L("%@ PIEDRAS", s.blocks.count))
                .font(Cumbre.mono(10, .bold)).tracking(1.4).foregroundStyle(Cumbre.ink3)
        }
        .padding(.horizontal, 16).padding(.top, 14).padding(.bottom, 8)
        .background(Cumbre.bg)
        .overlay(alignment: .top) { Rectangle().fill(Cumbre.ink).frame(height: 2) }
    }

    private func blockRow(_ b: GuideBlock, index: Int) -> some View {
        HStack(alignment: .top, spacing: 12) {
            GuideTopo(block: b)
                .frame(width: 150, height: 112)
                .clipped().clipShape(RoundedRectangle(cornerRadius: 3))
                .overlay(RoundedRectangle(cornerRadius: 3).stroke(Cumbre.rule, lineWidth: 1))
            VStack(alignment: .leading, spacing: 7) {
                HStack(spacing: 6) {
                    Text("\(index) · \(b.blockName.uppercased())").font(Cumbre.mono(10, .bold)).tracking(1)
                        .foregroundStyle(Cumbre.ink3).lineLimit(2)
                    RestrictionMarkButton(items: restrictionsUI?.forBlockId(b.blockId) ?? [])
                }
                ForEach(Array(b.lines.enumerated()), id: \.element.lineId) { n, h in
                    Button { if let id = h.lineId { onOpen(id) } } label: {
                        HStack(spacing: 7) {
                            Text("\(n + 1)").font(Cumbre.mono(10, .bold)).foregroundStyle(.white)
                                .frame(width: 18, height: 18).background(GradeColor.color(h.grade), in: Circle())
                            Text(h.lineName ?? h.blockName).font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(Cumbre.ink).lineLimit(2).multilineTextAlignment(.leading)
                            Spacer(minLength: 4)
                            GradePill(grade: h.grade, split: h.lineId.flatMap { splits[$0] })
                        }
                        .contentShape(Rectangle())
                    }.buttonStyle(.plain)
                    if let extra = meta(h) {
                        Text(extra).font(.system(size: 11)).foregroundStyle(Cumbre.ink3).padding(.leading, 25)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .overlay(alignment: .top) { Divider().overlay(Cumbre.rule) }
    }

    /// "★ 4.6 · ✓ 12" solo si hay algo que contar.
    private func meta(_ h: LineSearchHit) -> String? {
        var parts: [String] = []
        if let avg = h.rating?.doubleValue, (h.ratingCount?.intValue ?? 0) > 0 { parts.append("★ \(String(format: "%.1f", avg))") }
        if let r = h.repetitions?.intValue, r > 0 { parts.append("✓ \(r)") }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }
}

/// Foto de la piedra con TODAS sus líneas de esa cara, cada una con su número en el pie (como un topo impreso).
private struct GuideTopo: View {
    let block: GuideBlock
    @State private var image: UIImage?

    var body: some View {
        let photo = block.photoPath ?? ""
        ZStack {
            Cumbre.rule.opacity(0.15)
            if let image { Image(uiImage: image).resizable().scaledToFill() }
            GeometryReader { geo in
                ForEach(Array(block.lines.enumerated()), id: \.element.lineId) { n, h in
                    if h.photoPath == photo {
                        let pts = dedupPoints(TopoParse.points(h.linePath))
                            .map { CGPoint(x: $0.x * geo.size.width, y: $0.y * geo.size.height) }
                        if pts.count >= 2 {
                            Path { p in p.move(to: pts[0]); for q in pts.dropFirst() { p.addLine(to: q) } }
                                .stroke(GradeColor.color(h.grade), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                            // El pie de la vía es el punto más bajo del trazo.
                            let foot = pts.max(by: { $0.y < $1.y }) ?? pts[0]
                            Text("\(n + 1)").font(Cumbre.mono(9, .bold)).foregroundStyle(.white)
                                .frame(width: 15, height: 15).background(GradeColor.color(h.grade), in: Circle())
                                .position(x: foot.x, y: min(foot.y, geo.size.height - 8))
                        }
                    }
                }
            }
        }
        .task(id: photo) { if !photo.isEmpty { image = await ImageCache.image(photo) } }
    }
}
