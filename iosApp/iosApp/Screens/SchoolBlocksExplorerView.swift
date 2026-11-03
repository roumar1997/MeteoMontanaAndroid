import SwiftUI
import Shared

/// Pestaña BLOQUES/VÍAS de la ficha de una escuela, en forma de GUÍA: arriba los filtros (buscador, cara, sin hacer,
/// y en "Más" grados —varios— y beta por altura), luego las DESTACADAS (más repetidas / mejor valoradas) y debajo la
/// guía sector a sector. Los filtros valen para las dos.
///
/// "Repetida" = personas distintas que la tienen como HECHA en su diario. El servidor devuelve solo el total.
///
/// Esta vista SOLO pinta: el estado vive en `SchoolBlocksExplorerModel`; filtrar, ordenar y agrupar lo calculan
/// `SchoolLinesExplorer` y `SchoolGuide` (shared).
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
                highlights
                if let g = model.guideView {
                    SchoolGuideView(schoolId: school.id, guide: g, model: model, onOpen: onOpen)
                } else {
                    SkeletonList(count: 3) { SkeletonGradeRow() }
                            .frame(maxWidth: .infinity, minHeight: 100, alignment: .top)
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

    /// "14 VÍAS · 6 PIEDRAS · 2 SECTORES" (lo que queda tras los filtros).
    private var countLine: String {
        if let g = model.guideView {
            let lines = g.lineCount == 1 ? L("1 VÍA") : L("%@ VÍAS", g.lineCount)
            let blocks = g.blockCount == 1 ? L("1 PIEDRA") : L("%@ PIEDRAS", g.blockCount)
            let sectors = g.sectorCount == 1 ? L("1 SECTOR") : L("%@ SECTORES", g.sectorCount)
            return "\(lines) · \(blocks) · \(sectors)"
        }
        return ""
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

    // MARK: - Destacadas (más repetidas / mejor valoradas), con los mismos filtros

    @ViewBuilder private var highlights: some View {
        let items = model.highlights ?? []
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 2) {
                ForEach(SchoolBlocksExplorerModel.Order.allCases, id: \.self) { o in
                    let on = model.order == o
                    Button { withAnimation(.snappy(duration: 0.25)) { model.order = o } } label: {
                        Text(o.tab).font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(on ? Cumbre.ink : Cumbre.ink3)
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(on ? Cumbre.paper : Color.clear, in: Capsule())
                            .overlay(Capsule().stroke(on ? Cumbre.rule : Color.clear, lineWidth: 0.5))
                    }.buttonStyle(.plain)
                }
            }
            .padding(3)
            .background(Cumbre.rule.opacity(0.45), in: Capsule())
            .padding(.horizontal, 16)

            if items.isEmpty {
                Text(model.order == .repeated ? L("Nadie ha apuntado todavía ninguna de estas vías.")
                                              : L("Nadie ha valorado todavía ninguna de estas vías."))
                    .font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                    .padding(.horizontal, 16)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(alignment: .top, spacing: 10) {
                        ForEach(Array(items.enumerated()), id: \.element.lineId) { i, h in highlightCard(h, rank: i + 1) }
                    }
                    .padding(.horizontal, 16)
                }
            }
        }
        .padding(.bottom, 14)
        .guideTarget("detail_blocks.podium")
    }

    private func highlightCard(_ h: LineSearchHit, rank: Int) -> some View {
        Button { if let id = h.lineId { onOpen(id) } } label: {
            VStack(alignment: .leading, spacing: 0) {
                ZStack(alignment: .topLeading) {
                    thumbnail(h).frame(width: 156, height: 104).clipped()
                    Text("\(rank)").font(Cumbre.mono(11, .bold)).foregroundStyle(.white)
                        .frame(width: 22, height: 22).background(Color.black.opacity(0.6), in: Circle())
                        .padding(6)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text(h.lineName ?? h.blockName).font(.system(size: 13.5, weight: .semibold))
                        .foregroundStyle(Cumbre.ink).lineLimit(1)
                    Text(h.sectorName ?? h.blockName).font(.system(size: 11.5)).foregroundStyle(Cumbre.ink3).lineLimit(1)
                    HStack(spacing: 6) {
                        GradePill(grade: h.grade, small: true, split: h.lineId.flatMap { model.splits[$0] })
                        Spacer(minLength: 0)
                        if model.order == .repeated {
                            Text("✓ \(h.repetitions?.intValue ?? 0)").font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ok)
                        } else if let avg = h.rating?.doubleValue {
                            Text("★ \(String(format: "%.1f", avg))").font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.ink2)
                        }
                    }
                }
                .padding(9)
            }
            .frame(width: 156)
            .background(Cumbre.paper)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.rule, lineWidth: 1))
        }.buttonStyle(.plain)
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
}
