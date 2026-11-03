import SwiftUI
import Shared

/// Pestaña BLOQUES/VÍAS de la ficha de una escuela: las vías más repetidas de la escuela, filtrables por uno o
/// varios grados y por "tiene beta" (+1,70 / −1,70), con buscador por nombre y el resto agrupado por sector.
///
/// "Repetida" = personas distintas que la tienen como HECHA en su diario. El servidor devuelve solo el total,
/// nunca quién la ha hecho, y lo cuenta en cada consulta (no hay contador guardado que se desfase).
///
/// Esta vista SOLO pinta: el estado vive en `SchoolBlocksExplorerModel` y los filtros, el orden, el podio y los
/// sectores los calcula `SchoolLinesExplorer` (shared).
struct SchoolBlocksExplorerView: View {
    let school: School
    @ObservedObject var model: SchoolBlocksExplorerModel
    /// Toca una vía: la ficha pasa al mapa y abre su piedra.
    let onOpen: (String) -> Void
    /// Restricciones en vigor (las da la ficha): "!" en el sector y en cada vía afectada.
    @Environment(\.schoolRestrictions) private var restrictionsUI

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if model.loading {
                SkeletonList(count: 6) { SkeletonGradeRow() }
                    .frame(maxWidth: .infinity, minHeight: 160, alignment: .top)
            } else if model.failed {
                VStack(spacing: 10) {
                    Text(L("No se han podido cargar los bloques")).font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    Button(L("REINTENTAR")) { Task { await model.loadIfNeeded(schoolId: school.id, force: true) } }
                        .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                }
                .frame(maxWidth: .infinity, minHeight: 160)
            } else if !model.hasLines {
                Text(L("Esta escuela aún no tiene vías dibujadas."))
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    .frame(maxWidth: .infinity, minHeight: 120)
                    .padding(.horizontal, 16)
            } else {
                header
                if model.guideMode {
                    if let g = model.guideView {
                        SchoolGuideView(schoolId: school.id, guide: g, model: model, onOpen: onOpen)
                    } else {
                        SkeletonList(count: 3) { SkeletonGradeRow() }
                            .frame(maxWidth: .infinity, minHeight: 100, alignment: .top)
                    }
                } else {
                    results
                }
            }
        }
        .task { await model.loadIfNeeded(schoolId: school.id) }
        .task(id: model.betas) { await model.loadBetaIfNeeded(schoolId: school.id) }
        // Al volver de la piedra (donde se marca en el diario), los ✓ de la guía y "Sin hacer" se ponen al día.
        .onAppear { model.refreshJournal(schoolId: school.id) }
    }

    // MARK: - Cabecera única: los filtros valen para la LISTA y la GUÍA

    private var header: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                HStack(spacing: 6) {
                    Image(systemName: "magnifyingglass").font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                    TextField(L("Buscar vía o piedra"), text: $model.query)
                        .textFieldStyle(.plain).font(.system(size: 14))
                        .dismissKeyboardOnSubmit()
                    if !model.query.isEmpty {
                        Button { model.query = "" } label: {
                            Image(systemName: "xmark.circle.fill").foregroundStyle(Cumbre.ink3)
                        }.buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(Cumbre.paper, in: Capsule())
                .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
                .guideTarget("detail_blocks.search")
                viewSwitch
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    faceMenu
                    if !model.grades.isEmpty {
                        activeChip(model.gradeOptions.filter { model.grades.contains($0) }.joined(separator: " ")) {
                            model.grades = []
                        }
                    }
                    if !model.betas.isEmpty {
                        activeChip(BetaHeights.shared.ALL.filter { model.betas.contains($0) }
                            .map(SchoolBlocksExplorerModel.betaLabel).joined(separator: " ")) { model.betas = [] }
                    }
                    pill(L("Sin hacer"), icon: "circle.dashed", on: model.onlyNotDone) { model.onlyNotDone.toggle() }
                    pill(L("Más"), icon: "slider.horizontal.3", on: model.filtersOpen) {
                        withAnimation(.easeInOut(duration: 0.2)) { model.filtersOpen.toggle() }
                    }
                    .guideTarget("detail_blocks.filters")
                }
            }
            if model.filtersOpen { filterPanel }
            if !countLine.isEmpty { Text(countLine).font(Cumbre.mono(10, .bold)).tracking(1.4).foregroundStyle(Cumbre.ink3) }
        }
        .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 8)
    }

    /// "14 VÍAS · 6 PIEDRAS · 2 SECTORES" en la guía; "14 VÍAS" en la lista.
    private var countLine: String {
        if model.guideMode, let g = model.guideView {
            let lines = g.lineCount == 1 ? L("1 VÍA") : L("%@ VÍAS", g.lineCount)
            let blocks = g.blockCount == 1 ? L("1 PIEDRA") : L("%@ PIEDRAS", g.blockCount)
            let sectors = g.sectorCount == 1 ? L("1 SECTOR") : L("%@ SECTORES", g.sectorCount)
            return "\(lines) · \(blocks) · \(sectors)"
        }
        return ""   // la lista ya enseña su total encima de los resultados
    }

    /// Cambio de vista: los filtros no cambian, solo cómo se enseña el resultado.
    private var viewSwitch: some View {
        HStack(spacing: 2) {
            switchIcon("list.bullet", on: !model.guideMode, label: L("Lista")) { model.guideMode = false }
            switchIcon("book", on: model.guideMode, label: L("Guía")) { model.guideMode = true }
        }
        .padding(3)
        .background(Cumbre.rule.opacity(0.45), in: Capsule())
    }

    private func switchIcon(_ name: String, on: Bool, label: String, action: @escaping () -> Void) -> some View {
        Button { withAnimation(.snappy(duration: 0.25)) { action() } } label: {
            Image(systemName: name).font(.system(size: 13, weight: .semibold))
                .foregroundStyle(on ? Cumbre.ink : Cumbre.ink3)
                .frame(width: 34, height: 28)
                .background(on ? Cumbre.paper : Color.clear, in: Capsule())
                .overlay(Capsule().stroke(on ? Cumbre.rule : Color.clear, lineWidth: 0.5))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    /// Cara de la piedra (N/E/S/O, varias). Menú que no se cierra en cada toque.
    private var faceMenu: some View {
        Menu {
            ForEach(SchoolLinesExplorer.shared.FACES, id: \.self) { f in
                Button {
                    if model.faces.contains(f) { model.faces.remove(f) } else { model.faces.insert(f) }
                } label: {
                    if model.faces.contains(f) {
                        Label(Self.faceName(f), systemImage: "checkmark")
                    } else {
                        Text(Self.faceName(f))
                    }
                }
            }
            if !model.faces.isEmpty {
                Button(L("Quitar"), role: .destructive) { model.faces = [] }
            }
        } label: {
            pillLabel(model.faces.isEmpty ? L("Cara")
                      : L("Cara %@", SchoolLinesExplorer.shared.FACES.filter { model.faces.contains($0) }.joined(separator: " ")),
                      icon: "safari", on: !model.faces.isEmpty)
        }
        .menuActionDismissBehavior(.disabled)
    }

    static func faceName(_ f: String) -> String {
        switch f {
        case "N": return L("Norte")
        case "E": return L("Este")
        case "S": return L("Sur")
        default: return L("Oeste")
        }
    }

    private func pill(_ t: String, icon: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) { pillLabel(t, icon: icon, on: on) }.buttonStyle(.plain)
    }

    private func pillLabel(_ t: String, icon: String, on: Bool) -> some View {
        HStack(spacing: 5) {
            Image(systemName: icon).font(.system(size: 11, weight: .semibold))
            Text(t).font(.system(size: 13, weight: .medium))
        }
        .foregroundStyle(on ? Cumbre.bg : Cumbre.ink)
        .padding(.horizontal, 11).padding(.vertical, 6)
        .background(on ? Cumbre.ink : Cumbre.paper, in: Capsule())
        .overlay(Capsule().stroke(on ? Cumbre.ink : Cumbre.rule, lineWidth: 1))
        .fixedSize()
    }

    /// Filtro puesto desde "Más" que se quita con un toque.
    private func activeChip(_ t: String, clear: @escaping () -> Void) -> some View {
        Button(action: clear) {
            HStack(spacing: 5) {
                Text(t).font(.system(size: 13, weight: .medium))
                Image(systemName: "xmark").font(.system(size: 9, weight: .bold))
            }
            .foregroundStyle(Cumbre.bg)
            .padding(.horizontal, 11).padding(.vertical, 6)
            .background(Cumbre.ink, in: Capsule())
            .fixedSize()
        }.buttonStyle(.plain)
    }

    private var filterPanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            label(L("GRADO (UNO O VARIOS)"))
            wrapChips {
                chip(L("TODOS"), on: model.grades.isEmpty) { model.grades = [] }
                ForEach(model.gradeOptions, id: \.self) { g in
                    let on = model.grades.contains(g)
                    Button {
                        if on { model.grades.remove(g) } else { model.grades.insert(g) }
                    } label: {
                        GradePill(grade: g, large: true)
                            .opacity(on ? 1 : 0.5)
                            .overlay(Capsule().stroke(Cumbre.ink, lineWidth: on ? 2 : 0))
                    }.buttonStyle(.plain)
                }
            }
            label(L("BETA EN VÍDEO (SIN MARCAR = TODAS)"))
            wrapChips {
                ForEach(BetaHeights.shared.ALL, id: \.self) { h in
                    chip(SchoolBlocksExplorerModel.betaLabel(h), on: model.betas.contains(h)) {
                        if model.betas.contains(h) { model.betas.remove(h) } else { model.betas.insert(h) }
                    }
                }
            }
            // La guía va siempre en el orden en que te encuentras las piedras: ordenar solo tiene sentido en la lista.
            if !model.guideMode {
                label(L("ORDENAR"))
                wrapChips {
                    ForEach(SchoolBlocksExplorerModel.Order.allCases, id: \.self) { o in
                        chip(o.label, on: model.order == o) { model.order = o }
                    }
                }
            }
        }
    }

    private func label(_ t: String) -> some View {
        Text(t).font(Cumbre.mono(9, .bold)).tracking(1.6).foregroundStyle(Cumbre.ink3)
    }

    private func wrapChips<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        // ScrollView horizontal: con 12 grados distintos en una escuela grande no caben en una fila.
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) { content() }
        }
    }

    private func chip(_ text: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text).font(Cumbre.mono(11, .bold))
                .padding(.horizontal, 10).padding(.vertical, 6)
                .foregroundStyle(on ? Color.white : Cumbre.ink2)
                // terraFill, no terra: con texto blanco encima `terra` se queda corto de contraste en oscuro.
                .background(on ? Cumbre.terraFill : Cumbre.paper, in: Capsule())
                .overlay(Capsule().stroke(on ? Cumbre.terraFill : Cumbre.rule, lineWidth: 1))
        }.buttonStyle(.plain)
    }

    // MARK: - Resultados

    @ViewBuilder private var results: some View {
        if let view = model.linesView {
            Divider().overlay(Cumbre.rule)
            HStack {
                Text(view.total == 1 ? L("1 VÍA") : L("%@ VÍAS", view.total))
                    .font(Cumbre.mono(10, .bold)).tracking(1.8).foregroundStyle(Cumbre.ink3)
                Spacer()
                Text(L("✓ = personas que la han hecho")).font(.system(size: 10.5)).foregroundStyle(Cumbre.ink3)
            }
            .padding(.horizontal, 16).padding(.vertical, 8)

            if view.total == 0 {
                Text(L("Ninguna vía cumple esos filtros."))
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    .frame(maxWidth: .infinity).padding(.vertical, 24)
            }
            if !view.podium.isEmpty {
                HStack(alignment: .top, spacing: 8) {
                    ForEach(view.podium, id: \.lineId) { podiumCard($0) }
                    // Si hay menos de 3, se rellenan los huecos para que las tarjetas no se estiren.
                    ForEach(0..<max(0, 3 - view.podium.count), id: \.self) { _ in Color.clear.frame(maxWidth: .infinity) }
                }
                .padding(.horizontal, 16).padding(.bottom, 8)
                .guideTarget("detail_blocks.podium")
            }
            // Por sectores, plegables. Perezoso: un sector abierto con muchas vías no monta todas las miniaturas de golpe.
            LazyVStack(spacing: 0) {
                ForEach(view.groups, id: \.sector) { g in
                    sectorHeader(g)
                    if model.openSectors.contains(g.sector ?? "") {
                        ForEach(g.hits, id: \.lineId) { row($0) }
                    }
                }
            }
        } else {
            SkeletonList(count: 3) { SkeletonGradeRow() }
                .frame(maxWidth: .infinity, minHeight: 100, alignment: .top)
        }
    }

    private func sectorHeader(_ g: SectorGroup) -> some View {
        let key = g.sector ?? ""
        let open = model.openSectors.contains(key)
        return Button {
            if open { model.openSectors.remove(key) } else { model.openSectors.insert(key) }
        } label: {
            HStack {
                Text(g.sector ?? L("Sin sector")).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink)
                Spacer()
                RestrictionMarkButton(items: restrictionsUI?.allInSectorNamed(g.sector) ?? [])
                Text("\(g.hits.count)").font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink2)
                Image(systemName: open ? "chevron.up" : "chevron.down").font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
            }
            .padding(.horizontal, 16).padding(.vertical, 11)
            .background(Cumbre.paper)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .overlay(alignment: .top) { Divider().overlay(Cumbre.rule) }
    }

    // MARK: - Filas

    /// Repeticiones y estrellas SOLO para mostrar (el orden lo decide shared).
    private func repetitions(_ h: LineSearchHit) -> Int { h.repetitions?.intValue ?? 0 }

    private func stars(_ h: LineSearchHit) -> Double? {
        guard let avg = h.rating?.doubleValue, (h.ratingCount?.intValue ?? 0) > 0 else { return nil }
        return avg
    }

    private func thumbnail(_ h: LineSearchHit) -> some View {
        Group {
            if let photo = h.photoPath, !photo.isEmpty {
                MiniTopoThumbnail(photoUrl: photo, points: dedupPoints(TopoParse.points(h.linePath)), grade: h.grade)
            } else {
                Cumbre.rule.opacity(0.25)
            }
        }
    }

    private func open(_ h: LineSearchHit) {
        if let id = h.lineId { onOpen(id) }
    }

    private func podiumCard(_ h: LineSearchHit) -> some View {
        Button { open(h) } label: {
            VStack(alignment: .leading, spacing: 5) {
                thumbnail(h).frame(maxWidth: .infinity).frame(height: 66)
                    .clipped().clipShape(RoundedRectangle(cornerRadius: 3))
                Text(h.lineName ?? h.blockName).font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(Cumbre.ink).lineLimit(2).multilineTextAlignment(.leading)
                if let s = h.sectorName, !s.isEmpty {
                    Text(s).font(.system(size: 11)).foregroundStyle(Cumbre.ink3).lineLimit(1)
                }
                HStack(spacing: 4) {
                    Text("✓ \(repetitions(h))").foregroundStyle(Cumbre.ok)
                    GradePill(grade: h.grade, small: true, split: h.lineId.flatMap { model.splits[$0] })
                }.font(Cumbre.mono(10, .bold))
            }
            .padding(8).frame(maxWidth: .infinity, alignment: .topLeading)
            .background(Cumbre.paper)
            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }.buttonStyle(.plain)
    }

    private func row(_ h: LineSearchHit) -> some View {
        Button { open(h) } label: {
            HStack(spacing: 10) {
                thumbnail(h).frame(width: 46, height: 46)
                    .clipped()
                    .clipShape(RoundedRectangle(cornerRadius: 3))
                    .overlay(RoundedRectangle(cornerRadius: 3).stroke(Cumbre.rule, lineWidth: 1))
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Text(h.lineName ?? h.blockName).font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Cumbre.ink).lineLimit(1)
                        GradePill(grade: h.grade, split: h.lineId.flatMap { model.splits[$0] })
                        RestrictionMarkButton(items: restrictionsUI?.forBlockId(h.blockId) ?? [])
                    }
                    HStack(spacing: 8) {
                        if repetitions(h) > 0 {
                            Text("✓ \(repetitions(h))").font(Cumbre.mono(10.5, .bold)).foregroundStyle(Cumbre.ok)
                        }
                        if let avg = stars(h) {
                            Text("★ \(String(format: "%.1f", avg))").font(.system(size: 11.5)).foregroundStyle(Cumbre.ink2)
                        }
                    }
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right").font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
            }
            .padding(.horizontal, 16).padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .overlay(alignment: .top) { Divider().overlay(Cumbre.rule) }
    }
}
