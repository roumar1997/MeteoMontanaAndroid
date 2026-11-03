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
    /// "Ver en mapa": el mapa de la ficha centrado en la piedra (id), sin abrirla.
    let onShowOnMap: (String) -> Void
    /// Abre la vía en el mapa de la ficha (con su piedra).
    let onOpen: (String) -> Void
    @Environment(\.schoolRestrictions) private var restrictionsUI
    @State private var zoomed: GuideFace?
    /// Vía a la que se le está apuntando el ascenso (hoja de ascenso abierta aquí mismo).
    @State private var ticking: LineSearchHit?
    /// Vía hecha que se quiere quitar del diario (se pide confirmación).
    @State private var unmarking: LineSearchHit?

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
            GuideZoomSheet(face: item.face)
        }
        .sheet(item: Binding(get: { ticking.map(TickItem.init) }, set: { if $0 == nil { ticking = nil } })) { item in
            GuideAscentSheet(hit: item.hit, wasProject: model.projectIds.contains(item.id)) { choice in
                ticking = nil
                Task { await saveAscent(item.hit, choice: choice) }
            }
        }
        .confirmationDialog(L("¿Quitar esta vía de tu diario?"),
                            isPresented: Binding(get: { unmarking != nil }, set: { if !$0 { unmarking = nil } }),
                            titleVisibility: .visible) {
            Button(L("Quitar del diario"), role: .destructive) {
                if let h = unmarking { Task { await JournalTicker.unmarkDone(ref(h)); model.refreshJournal(schoolId: schoolId) } }
            }
            Button(L("Cancelar"), role: .cancel) {}
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
                // Como el PDF: foto del sector, todas sus vías por grado (tocar = ir a su piedra) y una tarjeta por piedra.
                VStack(alignment: .leading, spacing: 12) {
                    sectorIntro(s)
                    ForEach(Array(s.blocks.enumerated()), id: \.element.blockId) { i, b in
                        blockView(b, index: i + 1)
                            .background(Color.white)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Cumbre.rule, lineWidth: 1))
                    }
                }
                .padding(.horizontal, 10).padding(.bottom, 12)
            }
        }
        .background(Cumbre.paper)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14)
            .stroke(Cumbre.rule, style: StrokeStyle(lineWidth: 1, dash: s.unknownFace ? [4, 3] : [])))
        .id(GuideAnchor.sector(s.key))
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

    // MARK: - Inicio del sector (como el PDF)

    /// Foto grande del sector y TODAS sus vías agrupadas por grado, de fácil a difícil, con su color.
    /// Tocar una vía = la guía baja hasta la tarjeta de su piedra.
    @ViewBuilder private func sectorIntro(_ s: GuideSector) -> some View {
        if let map = model.sectorMaps[s.key] {
            // Foto aérea del sector con sus piedras (como en el PDF).
            Image(uiImage: map).resizable().aspectRatio(contentMode: .fit)
                .frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 14))
        } else if let photo = s.blocks.lazy.flatMap({ $0.faces }).compactMap({ $0.photo }).first {
            GuideHeroPhoto(path: photo)
                .task(id: model.overviewBlocks.count) { await model.loadSectorMap(schoolId: schoolId, sector: s) }
                .frame(height: 160).frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        let pairs = s.blocks.flatMap { b in b.lines.map { GuideChip(block: b, hit: $0) } }
        let grades = Array(Set(pairs.map { $0.hit.grade ?? "?" })).sorted { GradeOrder.score($0) < GradeOrder.score($1) }
        VStack(alignment: .leading, spacing: 8) {
            Text(L("TODAS LAS VÍAS · TOCA UNA PARA IR A SU PIEDRA")).eyebrow()
            ForEach(grades, id: \.self) { g in
                HStack(alignment: .top, spacing: 8) {
                    GradePill(grade: g, split: nil).frame(width: 46, alignment: .leading)
                    GuideFlowLayout(spacing: 5) {
                        ForEach(pairs.filter { ($0.hit.grade ?? "?") == g }) { c in
                            let b = c.block, h = c.hit
                            Button {
                                mark(b.blockId)
                                model.guideOpen.insert(s.key)
                                model.scrollToBlock = b.blockId
                            } label: {
                                HStack(spacing: 5) {
                                    Circle().fill(GradeColor.chip(h.grade)).frame(width: 7, height: 7)
                                        .overlay(Circle().stroke(Cumbre.rule, lineWidth: GradeColor.chipStyle(h.grade).dark ? 0.8 : 0))
                                    Text(h.lineName ?? b.blockName).font(.system(size: 12, weight: .semibold))
                                        .foregroundStyle(Cumbre.ink).lineLimit(1)
                                    Text(b.blockName).font(Cumbre.mono(9.5)).foregroundStyle(Cumbre.ink3).lineLimit(1)
                                }
                                .padding(.horizontal, 9).padding(.vertical, 5)
                                .background(Color.white, in: Capsule())
                                .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
    }

    // MARK: - Piedra

    private func blockView(_ b: GuideBlock, index: Int) -> some View {
        VStack(spacing: 0) {
            // Una foto por cara, cada una con SUS vías debajo (numeradas como en la foto, igual que la ficha).
            ForEach(Array(b.faces.enumerated()), id: \.element.id) { fi, face in
                ZStack(alignment: .top) {
                    GuideTopo(face: face, maxHeight: 240)
                        // Tocar la foto = abrir la piedra de verdad; al cerrarla se vuelve aquí (marcapáginas).
                        .contentShape(Rectangle())
                        .onTapGesture {
                            mark(b.blockId)
                            onOpen(face.lines.first?.lineId ?? b.blockId)
                        }
                    HStack {
                        Text(L("PIEDRA %@", b.blockName.uppercased()))
                            .font(Cumbre.mono(10.5, .bold)).foregroundStyle(.white)
                            .padding(.horizontal, 9).padding(.vertical, 5)
                            .background(Color.black.opacity(0.75), in: Capsule())
                        if b.faces.count > 1 {
                            Text(L("Cara %@ de %@", fi + 1, b.faces.count))
                                .font(.system(size: 11, weight: .semibold)).foregroundStyle(Cumbre.ink)
                                .padding(.horizontal, 9).padding(.vertical, 5)
                                .background(Color.white.opacity(0.9), in: Capsule())
                        }
                        if fi == 0 { RestrictionMarkButton(items: restrictionsUI?.forBlockId(b.blockId) ?? []) }
                        Spacer()
                        if face.photo != nil {
                            Button { mark(b.blockId); zoomed = face } label: {
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

                ForEach(Array(face.lines.enumerated()), id: \.element.lineId) { i, h in
                    lineRow(h, number: i + 1)
                }
            }

            HStack(spacing: 8) {
                actionButton(L("Ver en mapa"), icon: "map", filled: false) {
                    mark(b.blockId)
                    onShowOnMap(b.blockId)
                }
                if let lat = b.lat?.doubleValue, let lon = b.lon?.doubleValue {
                    actionButton(L("Cómo llegar"), icon: "arrow.triangle.turn.up.right.diamond", filled: true) {
                        mark(b.blockId)
                        openDirections(lat: lat, lon: lon)
                    }
                }
            }
            .padding(12)
            .overlay(alignment: .top) { Divider().overlay(Cumbre.rule) }
        }
        .overlay(alignment: .top) { Divider().overlay(Cumbre.rule) }
        .id(GuideAnchor.id(b.blockId))
    }

    private func lineRow(_ h: LineSearchHit, number: Int) -> some View {
        let id = h.lineId ?? ""
        return HStack(spacing: 9) {
            Button { mark(h.blockId); onOpen(id) } label: {
                HStack(spacing: 9) {
                    GuideLineNumber(number: number, grade: h.grade)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(h.lineName ?? h.blockName).font(.system(size: 14.5, weight: .semibold))
                            .foregroundStyle(Cumbre.ink).lineLimit(2).multilineTextAlignment(.leading)
                        if let extra = meta(h) {
                            Text(extra).font(.system(size: 11.5)).foregroundStyle(Cumbre.ink3)
                        }
                        // El comentario de quien la subió (beta, salida, avisos), como en las guías de papel.
                        if let d = h.lineDescription, !d.isEmpty {
                            Text(d).font(.system(size: 12.5)).italic().foregroundStyle(Cumbre.ink2)
                                .lineLimit(4).multilineTextAlignment(.leading)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    Spacer(minLength: 4)
                    GradePill(grade: h.grade, split: model.splits[id])
                }
                .contentShape(Rectangle())
            }.buttonStyle(.plain)
            tickButton(h)
        }
        .padding(.horizontal, 12).padding(.vertical, 9)
        .overlay(alignment: .top) { Divider().overlay(Cumbre.rule.opacity(0.6)) }
    }

    /// ✓ verde = hecha · ◐ naranja = proyecto · vacío = sin hacer.
    /// Tocar (sin hacer o proyecto) = la hoja de ascenso AQUÍ MISMO; tocar una hecha = quitarla (con confirmación).
    /// Mantener pulsado = marcar / quitar como proyecto.
    private func tickButton(_ h: LineSearchHit) -> some View {
        let id = h.lineId ?? ""
        let done = model.doneIds.contains(id)
        let project = model.projectIds.contains(id)
        return ZStack {
            Circle().fill(done ? Cumbre.ok : Color.clear)
            Circle().stroke(done ? Cumbre.ok : (project ? Cumbre.warn : Cumbre.rule), lineWidth: 1.5)
            if done {
                Image(systemName: "checkmark").font(.system(size: 12, weight: .bold)).foregroundStyle(.white)
            } else if project {
                Image(systemName: "circle.lefthalf.filled").font(.system(size: 13)).foregroundStyle(Cumbre.warn)
            }
        }
        .frame(width: 30, height: 30)
        .contentShape(Circle())
        .onTapGesture {
            mark(h.blockId)
            if done { unmarking = h } else { ticking = h }
        }
        .onLongPressGesture(minimumDuration: 0.4) {
            guard !done else { return }
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            Task {
                if project { await JournalTicker.removeProject(ref(h)) } else { await JournalTicker.markProject(ref(h)) }
                model.refreshJournal(schoolId: schoolId)
            }
        }
        .accessibilityElement()
        .accessibilityLabel(done ? L("Hecha") : project ? L("Proyecto") : L("Marcar como hecha"))
        .accessibilityAddTraits(.isButton)
    }

    /// Apunta la piedra como marcapáginas de la guía.
    private func mark(_ blockId: String) { model.setBookmark(schoolId: schoolId, blockId: blockId) }

    /// La vía tal como la necesita el diario.
    private func ref(_ h: LineSearchHit) -> JournalLineRef {
        JournalLineRef(
            schoolId: h.schoolId, schoolName: h.schoolName, sectorName: h.sectorName,
            discipline: h.discipline ?? "BOULDER", blockId: h.blockId, lineId: h.lineId ?? "",
            viaName: h.lineName ?? h.blockName, grade: h.grade)
    }

    /// Lo mismo que hace la ficha de la piedra al guardar la hoja de ascenso: diario primero (el voto de grado solo lo
    /// acepta el servidor de quien ya la tiene en él), luego Comunidad y el voto.
    private func saveAscent(_ h: LineSearchHit, choice: AscentChoice) async {
        let r = ref(h)
        let wasProject = model.projectIds.contains(r.lineId)
        await JournalTicker.markDone(r, wasProject: wasProject, sessionDate: choice.sessionDate,
                                     aVista: choice.aVista, alFlash: choice.alFlash)
        model.refreshJournal(schoolId: schoolId)
        if choice.publish {
            JournalTicker.publish(r, wasProject: wasProject, caption: choice.caption, photo: choice.photo)
        }
        if let g = choice.gradeVote, !r.lineId.isEmpty {
            _ = await CommunityVoteStore().voteGrade(lineId: r.lineId, grade: g)
        }
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

    /// "Sentado · ★ 4.6 · ✓ 12" solo si hay algo que contar.
    private func meta(_ h: LineSearchHit) -> String? {
        var parts: [String] = []
        if let start = feedStartTypeLabel(h.startType) { parts.append(start) }
        if let avg = h.rating?.doubleValue, (h.ratingCount?.intValue ?? 0) > 0 { parts.append("★ \(String(format: "%.1f", avg))") }
        if let r = h.repetitions?.intValue, r > 0 { parts.append("✓ \(r)") }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    private struct ZoomItem: Identifiable {
        let face: GuideFace
        var id: String { face.id }
    }

    private struct TickItem: Identifiable {
        let hit: LineSearchHit
        var id: String { hit.lineId ?? hit.blockId }
    }
}

/// La hoja de ascenso de siempre, abierta desde la guía con su propia votación de grado (necesita saber tu altura).
private struct GuideAscentSheet: View {
    let hit: LineSearchHit
    let wasProject: Bool
    let onSave: (AscentChoice) -> Void
    @StateObject private var votes = CommunityVoteStore()

    var body: some View {
        let name = hit.lineName ?? hit.blockName
        AscentSheet(
            lineLabel: hit.grade.map { "\(name) · \($0)" } ?? name,
            lineName: name,
            setterGrade: hit.grade,
            wasProject: wasProject,
            votes: votes,
            onSave: onSave)
        .task {
            await votes.loadMyHeight()
            if let id = hit.lineId { await votes.loadGrade(lineId: id) }
        }
    }
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

/// Una cara de la piedra: una foto y las vías dibujadas sobre ELLA (numeradas 1, 2, 3… como en la ficha).
struct GuideFace: Identifiable {
    let blockId: String
    let photo: String?
    let lines: [LineSearchHit]
    var id: String { "\(blockId)|\(photo ?? "")" }
}

extension GuideBlock {
    /// Las vías agrupadas por la foto en la que están dibujadas, en el orden en que aparecen.
    var faces: [GuideFace] {
        var order: [String] = []
        var byPhoto: [String: [LineSearchHit]] = [:]
        for h in lines {
            let k = h.photoPath ?? ""
            if byPhoto[k] == nil { order.append(k) }
            byPhoto[k, default: []].append(h)
        }
        return order.map { GuideFace(blockId: blockId, photo: $0.isEmpty ? nil : $0, lines: byPhoto[$0] ?? []) }
    }
}

extension GuideFace {
    /// Las líneas como las pinta la ficha de la piedra: número al inicio y tipo de salida al final.
    var topoVias: [TopoVia] {
        lines.enumerated().map { i, h in
            TopoVia(number: i + 1, grade: h.grade, startType: h.startType, points: TopoParse.points(h.linePath))
        }
    }
}

/// Foto de una cara con sus líneas, pintadas por el MISMO pintor que la ficha de la piedra (`TopoPainter`).
/// El marco toma la proporción de la foto para que los trazos (0–1 sobre la foto) caigan en su sitio.
struct GuideTopo: View {
    let face: GuideFace
    var maxHeight: CGFloat = 240
    @State private var image: UIImage?

    var body: some View {
        let photo = face.photo ?? ""
        Group {
            if let image {
                Image(uiImage: image).resizable()
                    .overlay {
                        Canvas { ctx, size in
                            TopoPainter.paint(GraphicsContextTarget(ctx: ctx), vias: face.topoVias, size: size,
                                              style: .photo)
                        }
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
}

/// Número de la vía en su cara, como en la ficha de la piedra (el mismo que lleva la línea en la foto).
struct GuideLineNumber: View {
    let number: Int
    let grade: String?
    var body: some View {
        Text("\(number)").font(Cumbre.mono(11, .bold))
            .foregroundStyle(GradeColor.chipStyle(grade).dark ? .black : .white)
            .frame(width: 22, height: 22)
            .background(Circle().fill(GradeColor.chip(grade)))
    }
}

/// La foto de la piedra en grande, con zoom de pellizco.
private struct GuideZoomSheet: View {
    let face: GuideFace
    @Environment(\.dismiss) private var dismiss
    @State private var scale: CGFloat = 1
    @State private var base: CGFloat = 1

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            GuideTopo(face: face, maxHeight: .infinity)
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

/// Id de scroll de cada piedra de la guía (para saltar al marcapáginas).
enum GuideAnchor {
    static func id(_ blockId: String) -> String { "guide-\(blockId)" }
    static func sector(_ key: String) -> String { "guide-sector-\(key)" }
}

/// Una vía en el listado del inicio de sector (con su piedra, para saltar a ella).
struct GuideChip: Identifiable {
    let block: GuideBlock
    let hit: LineSearchHit
    var id: String { hit.lineId ?? "\(block.blockId)|\(hit.lineName ?? "")" }
}

/// Orden de dificultad de un grado francés (3 … 9c+); lo que no se entiende ("?", proyecto), al final.
enum GradeOrder {
    static func score(_ g: String) -> Int {
        let chars = Array((GradeRangeUI.base(g) ?? g).uppercased())
        guard let first = chars.first, let num = first.wholeNumberValue else { return 10_000 }
        var score = num * 100
        if chars.count > 1, let l = "ABCD".firstIndex(of: chars[1]) {
            score += "ABCD".distance(from: "ABCD".startIndex, to: l) * 10
        }
        if chars.last == "+" { score += 1 }
        return score
    }
}

/// Foto del sector, sin líneas, rellenando su marco.
struct GuideHeroPhoto: View {
    let path: String
    @State private var image: UIImage?
    var body: some View {
        Cumbre.rule.opacity(0.25)
            .overlay {
                if let image { Image(uiImage: image).resizable().scaledToFill() }
            }
            .clipped()
            .task(id: path) { image = await ImageCache.image(path) }
    }
}

/// Coloca las etiquetas en filas, pasando a la siguiente cuando no caben (como un texto).
struct GuideFlowLayout: Layout {
    var spacing: CGFloat = 6

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxW = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0, widest: CGFloat = 0
        for v in subviews {
            let s = v.sizeThatFits(.unspecified)
            if x > 0 && x + s.width > maxW { x = 0; y += rowH + spacing; rowH = 0 }
            x += s.width + spacing
            rowH = max(rowH, s.height)
            widest = max(widest, x - spacing)
        }
        return CGSize(width: min(widest, maxW), height: y + rowH)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowH: CGFloat = 0
        for v in subviews {
            let s = v.sizeThatFits(.unspecified)
            if x > bounds.minX && x + s.width > bounds.maxX { x = bounds.minX; y += rowH + spacing; rowH = 0 }
            v.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(width: min(s.width, bounds.width), height: s.height))
            x += s.width + spacing
            rowH = max(rowH, s.height)
        }
    }
}
