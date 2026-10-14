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

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if model.loading {
                ProgressView().frame(maxWidth: .infinity, minHeight: 160)
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
                filters
                results
            }
        }
        .task { await model.loadIfNeeded(schoolId: school.id) }
        .task(id: model.betas) { await model.loadBetaIfNeeded(schoolId: school.id) }
    }

    // MARK: - Filtros

    private var filters: some View {
        VStack(alignment: .leading, spacing: 10) {
            TextField(L("Buscar por nombre…"), text: $model.query)
                .textFieldStyle(.plain)
                .font(.system(size: 14))
                .padding(.horizontal, 12).padding(.vertical, 9)
                .background(Cumbre.paper)
                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                .dismissKeyboardOnSubmit()
                .guideTarget("detail_blocks.search")
            filterToggle
            if model.filtersOpen { filterPanel }
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
    }

    /// Cuántos filtros se han tocado (grado, beta, orden distinto del de siempre).
    private var activeCount: Int {
        (model.grades.isEmpty ? 0 : 1) + (model.betas.isEmpty ? 0 : 1) + (model.order == .repeated ? 0 : 1)
    }

    private var summary: String {
        var parts: [String] = []
        if !model.grades.isEmpty {
            // gradeOptions ya viene de más fácil a más difícil.
            parts.append(model.gradeOptions.filter { model.grades.contains($0) }.joined(separator: " "))
        }
        if !model.betas.isEmpty {
            parts.append(BetaHeights.shared.ALL.filter { model.betas.contains($0) }
                .map(SchoolBlocksExplorerModel.betaLabel).joined(separator: " "))
        }
        parts.append(model.order.label.capitalized)
        return parts.joined(separator: " · ")
    }

    private var filterToggle: some View {
        Button { withAnimation(.easeInOut(duration: 0.2)) { model.filtersOpen.toggle() } } label: {
            HStack(spacing: 8) {
                HStack(spacing: 6) {
                    Image(systemName: "slider.horizontal.3").font(.system(size: 12, weight: .semibold))
                    Text(activeCount > 0 ? L("FILTROS (%@)", activeCount) : L("FILTROS")).font(Cumbre.mono(11, .bold)).tracking(1)
                }
                .foregroundStyle(activeCount > 0 ? Color.white : Cumbre.ink2)
                .padding(.horizontal, 12).padding(.vertical, 7)
                .background(activeCount > 0 ? Cumbre.terraFill : Cumbre.paper, in: Capsule())
                .overlay(Capsule().stroke(activeCount > 0 ? Cumbre.terraFill : Cumbre.rule, lineWidth: 1))
                Text(summary).font(.system(size: 12)).foregroundStyle(Cumbre.ink3).lineLimit(1)
                Spacer(minLength: 0)
                Image(systemName: model.filtersOpen ? "chevron.up" : "chevron.down")
                    .font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.ink3)
            }
            .contentShape(Rectangle())
        }.buttonStyle(.plain)
        .guideTarget("detail_blocks.filters")
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
            label(L("ORDENAR"))
            wrapChips {
                ForEach(SchoolBlocksExplorerModel.Order.allCases, id: \.self) { o in
                    chip(o.label, on: model.order == o) { model.order = o }
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
            ProgressView().frame(maxWidth: .infinity, minHeight: 100)
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
                    GradePill(grade: h.grade, small: true)
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
                        GradePill(grade: h.grade)
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
