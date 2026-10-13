import SwiftUI
import Shared

/// Estado de la pestaña BLOQUES. Vive en la ficha de la escuela (no en la pestaña) para que al ir al mapa o al tiempo
/// y volver no se pierdan los filtros ni se vuelva a bajar la lista entera.
@MainActor
final class SchoolBlocksExplorerModel: ObservableObject {
    enum Beta: String, CaseIterable {
        case any = "ANY", tall = "TALL", short = "SHORT"
        var label: String {
            switch self {
            case .any: return L("CON BETA")
            case .tall: return "▶ +1,70"
            case .short: return "▶ −1,70"
            }
        }
    }

    enum Order: CaseIterable {
        case repeated, rated
        var label: String { self == .repeated ? L("MÁS REPETIDOS") : L("MEJOR VALORADOS") }
    }

    /// Tamaño de página del servidor (PAGE_SIZE de SearchLinesService). Si llega una página llena hay que pedir la siguiente.
    private let serverPage = 300
    /// Tope de páginas: 1.500 vías por escuela, muy por encima de cualquier escuela real.
    private let maxPages = 5

    @Published var all: [LineSearchHit] = []
    @Published var loading = true
    @Published var failed = false
    @Published var grades: Set<String> = []
    @Published var beta: Beta?
    @Published var betaLines: [Beta: Set<String>] = [:]
    @Published var order: Order = .repeated
    @Published var showAll = false

    private var loadedSchool: String?

    /// Baja las vías de la escuela UNA vez (los grados se filtran en local). `force` para reintentar.
    func loadIfNeeded(schoolId: String, force: Bool = false) async {
        if loadedSchool == schoolId && !force && !failed { return }
        loading = true; failed = false
        do {
            var out: [LineSearchHit] = []
            for page in 0..<maxPages {
                let criteria = LineExploreCriteria(
                    gradeMin: nil, gradeMax: nil, discipline: nil, rockTypes: nil,
                    schoolIds: [schoolId], orientations: nil, lat: nil, lon: nil, maxDistanceKm: nil,
                    sort: "MOST_REPEATED", offset: Int32(page * serverPage), withRatings: true, beta: nil,
                    startType: nil, withTopo: false, withRepetitions: true)
                let hits = try await AppDependencies.shared.container.exploreLines.invoke(criteria: criteria)
                out.append(contentsOf: hits)
                if hits.count < serverPage { break }
            }
            all = out
            loadedSchool = schoolId
        } catch is CancellationError {
            // La vista se fue a medias: no es un fallo, se reintenta al volver.
        } catch {
            failed = true
        }
        loading = false
    }

    /// Los enlaces de beta viven en el servidor: se piden por modo (ANY/TALL/SHORT) y se guardan.
    func loadBetaIfNeeded(schoolId: String) async {
        guard let b = beta, betaLines[b] == nil else { return }
        do {
            let criteria = LineExploreCriteria(
                gradeMin: nil, gradeMax: nil, discipline: nil, rockTypes: nil,
                schoolIds: [schoolId], orientations: nil, lat: nil, lon: nil, maxDistanceKm: nil,
                sort: "DISTANCE", offset: 0, withRatings: false, beta: b.rawValue, startType: nil,
                withTopo: false, withRepetitions: false)
            let hits = try await AppDependencies.shared.container.exploreLines.invoke(criteria: criteria)
            betaLines[b] = Set(hits.compactMap { $0.lineId })
        } catch is CancellationError {
            // Cambió de chip mientras bajaba: la tarea nueva se encarga; NO se toca su selección.
        } catch {
            // Sin conexión a medias: se quita el filtro en vez de dejar la lista vacía sin explicación.
            if beta == b { beta = nil }
        }
    }
}

/// Pestaña BLOQUES de la ficha de una escuela (opción B del rediseño, 2026-10-13): las vías más repetidas de la
/// escuela, filtrables por uno o varios grados y por "tiene beta" (todas, +1,70 o −1,70).
///
/// "Repetida" = personas distintas que la tienen como HECHA en su diario. El servidor devuelve solo el total,
/// nunca quién la ha hecho, y lo cuenta en cada consulta (no hay contador guardado que se desfase).
struct SchoolBlocksExplorerView: View {
    let school: School
    @ObservedObject var model: SchoolBlocksExplorerModel
    /// Toca una vía: la ficha pasa al mapa y abre su piedra.
    let onOpen: (String) -> Void

    /// Cuántas filas se ven antes de pedir "VER TODAS".
    private let pageSize = 20

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
            } else if lines.isEmpty {
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
        .task(id: model.beta) { await model.loadBetaIfNeeded(schoolId: school.id) }
    }

    // MARK: - Datos

    private var lines: [LineSearchHit] { model.all.filter { $0.lineId != nil } }

    /// Grados que de verdad hay en esta escuela, de más fácil a más difícil (los que no se pueden puntuar, al final).
    private var gradeOptions: [String] {
        let present = Set(lines.compactMap { $0.grade?.uppercased() }.filter { !$0.isEmpty })
        return present.sorted { (gradeScore($0) ?? Int.max, $0) < (gradeScore($1) ?? Int.max, $1) }
    }

    private func repetitions(_ h: LineSearchHit) -> Int { h.repetitions?.intValue ?? 0 }

    private func stars(_ h: LineSearchHit) -> Double {
        guard let avg = h.rating?.doubleValue, (h.ratingCount?.intValue ?? 0) > 0 else { return 0 }
        return avg
    }

    /// nil = todavía bajando los enlaces de beta de ese modo.
    private var visible: [LineSearchHit]? {
        var h = lines
        if !model.grades.isEmpty { h = h.filter { model.grades.contains(($0.grade ?? "").uppercased()) } }
        if let b = model.beta {
            guard let ids = model.betaLines[b] else { return nil }
            h = h.filter { ids.contains($0.lineId ?? "") }
        }
        return h.sorted { a, b in
            let (ra, rb) = (repetitions(a), repetitions(b))
            let (sa, sb) = (stars(a), stars(b))
            switch model.order {
            case .repeated:
                if ra != rb { return ra > rb }
                if sa != sb { return sa > sb }
            case .rated:
                if sa != sb { return sa > sb }
                if ra != rb { return ra > rb }
            }
            return (a.lineName ?? "") < (b.lineName ?? "")
        }
    }

    // MARK: - Filtros

    private var filters: some View {
        VStack(alignment: .leading, spacing: 10) {
            label(L("GRADO (UNO O VARIOS)"))
            wrapChips {
                chip(L("TODOS"), on: model.grades.isEmpty) { model.grades = []; model.showAll = false }
                ForEach(gradeOptions, id: \.self) { g in
                    chip(g, on: model.grades.contains(g)) {
                        if model.grades.contains(g) { model.grades.remove(g) } else { model.grades.insert(g) }
                        model.showAll = false
                    }
                }
            }
            label(L("BETA EN VÍDEO"))
            wrapChips {
                chip(L("TODAS"), on: model.beta == nil) { model.beta = nil; model.showAll = false }
                ForEach(SchoolBlocksExplorerModel.Beta.allCases, id: \.self) { b in
                    chip(b.label, on: model.beta == b) { model.beta = (model.beta == b ? nil : b); model.showAll = false }
                }
            }
            label(L("ORDENAR"))
            HStack(spacing: 0) {
                ForEach(SchoolBlocksExplorerModel.Order.allCases, id: \.self) { o in
                    Button { model.order = o } label: {
                        Text(o.label).font(Cumbre.mono(10, .bold)).tracking(0.8)
                            .frame(maxWidth: .infinity).padding(.vertical, 9)
                            .foregroundStyle(model.order == o ? Color.white : Cumbre.ink2)
                            // inkButton, no ink: `ink` es de TEXTO y en oscuro se vuelve casi blanco bajo el blanco.
                            .background(model.order == o ? Cumbre.inkButton : Cumbre.paper)
                    }.buttonStyle(.plain)
                }
            }
            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
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
                .background(on ? Cumbre.terraFill : Cumbre.paper)
                .overlay(RoundedRectangle(cornerRadius: 2).stroke(on ? Cumbre.terraFill : Cumbre.rule, lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 2))
        }.buttonStyle(.plain)
    }

    // MARK: - Resultados

    @ViewBuilder private var results: some View {
        if let hits = visible {
            let podium = model.order == .repeated ? Array(hits.prefix(3).filter { repetitions($0) > 0 }) : []
            let rest = Array(hits.dropFirst(podium.count))
            let shown = model.showAll ? rest : Array(rest.prefix(pageSize))

            Divider().overlay(Cumbre.rule)
            HStack {
                Text(hits.count == 1 ? L("1 VÍA") : L("%@ VÍAS", hits.count))
                    .font(Cumbre.mono(10, .bold)).tracking(1.8).foregroundStyle(Cumbre.ink3)
                Spacer()
                Text(L("✓ = personas que la han hecho")).font(.system(size: 10.5)).foregroundStyle(Cumbre.ink3)
            }
            .padding(.horizontal, 16).padding(.vertical, 8)

            if hits.isEmpty {
                Text(L("Ninguna vía cumple esos filtros."))
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    .frame(maxWidth: .infinity).padding(.vertical, 24)
            }
            if !podium.isEmpty {
                HStack(alignment: .top, spacing: 8) {
                    ForEach(podium, id: \.lineId) { podiumCard($0) }
                    if podium.count < 3 { ForEach(0..<(3 - podium.count), id: \.self) { _ in Color.clear.frame(maxWidth: .infinity) } }
                }
                .padding(.horizontal, 16).padding(.bottom, 8)
            }
            // Perezoso: "VER LAS N" en una escuela grande no debe montar cientos de miniaturas de golpe.
            LazyVStack(spacing: 0) {
                ForEach(shown, id: \.lineId) { row($0) }
            }
            if !model.showAll && rest.count > pageSize {
                Button { model.showAll = true } label: {
                    Text(L("VER LAS %@ →", rest.count)).font(Cumbre.mono(11, .bold)).tracking(1)
                        .foregroundStyle(Cumbre.terra).frame(maxWidth: .infinity).padding(.vertical, 12)
                }.buttonStyle(.plain)
            }
        } else {
            ProgressView().frame(maxWidth: .infinity, minHeight: 100)
        }
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
                HStack(spacing: 4) {
                    Text("✓ \(repetitions(h))").foregroundStyle(Cumbre.ok)
                    // chip, no color: `color` es el de SOBRE FOTO (blanco en grados bajos) y aquí el fondo es papel.
                    Text(h.grade ?? "").foregroundStyle(GradeColor.chip(h.grade))
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
                        Text(h.grade ?? "").font(Cumbre.mono(11, .bold)).foregroundStyle(GradeColor.chip(h.grade))
                    }
                    HStack(spacing: 8) {
                        if repetitions(h) > 0 {
                            Text("✓ \(repetitions(h))").font(Cumbre.mono(10.5, .bold)).foregroundStyle(Cumbre.ok)
                        }
                        if stars(h) > 0 {
                            Text("★ \(String(format: "%.1f", stars(h)))").font(.system(size: 11.5)).foregroundStyle(Cumbre.ink2)
                        }
                        if let s = h.sectorName, !s.isEmpty {
                            Text(s).font(.system(size: 11.5)).foregroundStyle(Cumbre.ink3).lineLimit(1)
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
