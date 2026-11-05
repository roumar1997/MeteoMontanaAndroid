import SwiftUI
import Shared
import CoreLocation
import UIKit

// Lista de escuelas — réplica fiel de SchoolListScreen.kt de Android:
// fila de iconos, header "Escuelas" + count + "+ Enviar escuela", banner ☕,
// buscador inline, filtros, y la fila rica (badge tintado + rank + nombre serif
// + estrella + subtítulo + heatmap 10 celdas + tag ● SECA/MOJADA).
// Datos reales del backend vía use cases compartidos (KMP). Filtrado local.

struct SchoolListView: View {
    @StateObject private var vm = SchoolListViewModel()
    /// Pestaña «Vías/Bloques» (separada de la lista el 2026-11-05).
    @StateObject private var explore = ExploreLinesViewModel()
    @Environment(\.scenePhase) private var scenePhase
    /// Escuela pedida desde el asistente: se abre aquí dentro, con la barra de pestañas visible.
    @ObservedObject private var router = ShareLinkRouter.shared
    /// Grados partidos por altura de los resultados del buscador (se piden juntos).
    @ObservedObject private var gradeSplits = GradeSplitsStore.shared
    @EnvironmentObject private var assistant: AssistantViewModel

    /// Abre la escuela que pidió el asistente (si la hay). Se llama al llegar la petición y al aparecer la pestaña
    /// (si aún no se había creado cuando llegó).
    private func consumeAssistantRequest() {
        guard let request = router.tabSchoolRequest, let school = request.school else { return }
        router.tabSchoolRequest = nil
        navTarget = SchoolNavTarget(school: school, via: request.viaId)
    }

    var body: some View {
        NavigationStack {
            listScroll
            .background(Cumbre.bg.ignoresSafeArea())
            .guideHost("schools")
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(item: $navTarget) { target in
                SchoolDetailView(school: target.school, openVia: target.via)
                    // Dentro de una escuela, "¿qué sector tiene más sombra?" ya sabe de cuál hablas.
                    .onAppear { assistant.contextSchoolId = target.school.id }
                    .onDisappear { assistant.contextSchoolId = nil }
            }
            .onChange(of: router.tabSchoolRequest?.id) { _, _ in consumeAssistantRequest() }
            .onAppear { consumeAssistantRequest() }
            .overlay {
                if eligiendoFoto {
                    SubmitBlockPhotoFlow(
                        schools: vm.schools,
                        onOpenSchool: { id in
                            eligiendoFoto = false
                            if let s = vm.schools.first(where: { $0.id == id }) {
                                navTarget = SchoolNavTarget(school: s, via: nil)
                            }
                        },
                        onDismiss: { eligiendoFoto = false })
                }
            }
            .onChange(of: vm.query) { _, _ in dispatchViaSearch() }
            .overlay(alignment: .bottom) {
                if vm.compareSelection.count >= 1 {
                    CompareBar(count: vm.compareSelection.count,
                               canCompare: vm.compareSelection.count >= 2,
                               onClear: { vm.clearCompare() },
                               onCompare: { showCompare = true })
                }
            }
            .sheet(isPresented: $showCompare, onDismiss: { vm.clearCompare() }) {
                CompareView(schools: vm.filtered.filter { vm.compareSelection.contains($0.id) })
            }
            .task { await vm.load() }
            // Lo que «Vías/Bloques» toma de la lista: filtros comunes, ubicación y la nota de cada escuela.
            .onAppear { explore.setContext(exploreContext) }
            .onChange(of: exploreContext) { _, c in explore.setContext(c) }
            // Primera vez tras crear la cuenta: la guía de Escuelas sale sola, una vez (saltable). El resto de guías
            // son opcionales (el "?" de cada pantalla, que parpadea las primeras visitas).
            .task(id: vm.schools.isEmpty) {
                guard !vm.schools.isEmpty, UserDefaults.standard.bool(forKey: "guide_pending_schools") else { return }
                UserDefaults.standard.set(false, forKey: "guide_pending_schools")
                guard GuideStore.enabled else { return }
                try? await Task.sleep(nanoseconds: 1_200_000_000)
                GuideStore.shared.start("schools")
            }
            // Al volver a activo (p. ej. tras aceptar el permiso de ubicación en
            // el primer arranque) reintenta cargar la ubicación si falta, para
            // que el filtro de 50 km se aplique sin tener que reabrir la app.
            .onChange(of: scenePhase) { _, phase in
                if phase == .active { Task { await vm.refreshLocationIfNeeded() } }
            }
            .refreshable { await vm.refresh() }
        }
    }

    /// El ScrollView con la cabecera, los filtros y la lista. Aparte de `body` para no saturar el type-checker.
    @ViewBuilder private var listScroll: some View {
            ScrollView {
                // VStack NO perezoso a propósito: las 6 muertes por watchdog
                // (jul-2026) ocurrieron DENTRO de la maquinaria de LazyVStack
                // (prefetch, placement, motionVectors) recolocando estas ~191
                // filas al reordenar/filtrar. Componerlas todas cuesta ~decenas
                // de ms en Release y elimina esa maquinaria entera (mismo
                // movimiento «piedras fluidas» que en Android).
                VStack(spacing: 0) {
                    HeaderEscuelas(unreadCount: vm.unreadNotifications,
                                   chatUnread: vm.unreadChats,
                                   onNotificationsClosed: { Task { await vm.refreshUnread() } },
                                   onSubmitBlockPhoto: { eligiendoFoto = true })
                    SearchField(text: $vm.query)
                    if vm.query.trimmingCharacters(in: .whitespaces).count >= 2 {
                        viaHitsSection
                    }

                    MapToggleAndPanel(vm: vm, onOpen: { navTarget = SchoolNavTarget(school: $0, via: nil) })

                    FilterChips(vm: vm, explore: explore)
                    Divider().overlay(Cumbre.rule)

                    // Hint de comparar — justo antes de la lista
                    if favoritesCompare && explore.tab == .schools && !explore.active {
                        FavoritesCompareCard { id in
                            if let s = vm.schools.first(where: { $0.id == id }) { navTarget = SchoolNavTarget(school: s, via: nil) }
                        }
                        .padding(.horizontal, 12).padding(.top, 8)
                        .transition(.opacity.combined(with: .move(edge: .top)))
                    }

                    FirstTimeHint(
                        hintKey: "schools_compare",
                        text: L("Mantén pulsada una escuela para compararla con otras (hasta 3). También puedes abrir HOY para ver un tramo de varios días.")
                    )

                    if vm.loading {
                        SkeletonList(count: 6) { SkeletonRow() }
                    } else if let err = vm.errorText {
                        ErrorRow(message: err) { Task { await vm.refresh() } }
                    } else if explore.active {
                        exploreResultsSection
                    } else {
                        let items = vm.filtered
                        if items.isEmpty {
                            EmptyRow(canClear: vm.activeFilters || !vm.query.isEmpty) { vm.clearFilters() }
                        } else {
                            schoolsSection(items)
                        }
                    }
                }
            }
    }

    /// La lista moderna (Álvaro, 2026-10-19, propuesta A): cada escuela en su tarjeta con el anillo de la nota.
    @ViewBuilder private func schoolsSection(_ items: [School]) -> some View {
        VStack(spacing: 10) {
            ForEach(Array(items.enumerated()), id: \.element.id) { idx, school in
                schoolCard(school, isFirst: idx == 0)
            }
        }
        .padding(12)
    }

    private func schoolCard(_ school: School, isFirst: Bool) -> some View {
        SchoolCardView(
            school: school,
            score: vm.scores[school.id],
            range: vm.rangeMode ? vm.rangeScores[school.id] : nil,
            distanceKm: vm.distanceKm(school),
            isFavorite: vm.favoriteIds.contains(school.id),
            isSelected: vm.compareSelection.contains(school.id),
            processionaryAlertActive: vm.processionaryAlertActive(for: school),
            onToggleFavorite: { vm.toggleFavorite(school.id) },
            isFirst: isFirst
        )
        .modifier(SchoolTapGestures(open: { openSchool(school) }, compare: { vm.toggleCompare(school.id) }))
    }

    /// Tocar: si hay selección de comparar activa, la cambia; si no, abre la escuela.
    /// Filtros comunes, ubicación y nota (de hoy o del tramo) que necesita la pestaña «Vías/Bloques».
    private var exploreContext: ExploreContext {
        ExploreContext(style: vm.style, rock: vm.rock, maxDistanceKm: vm.maxDistanceKm, lat: vm.userLat, lon: vm.userLon,
                       scoreBySchool: vm.rangeMode ? vm.rangeScores.mapValues { Int($0.combinedScore) }
                                                   : vm.scores.mapValues { Int($0.todayScore) })
    }

    private func openSchool(_ school: School) {
        if vm.compareSelection.isEmpty { navTarget = SchoolNavTarget(school: school, via: nil) }
        else { vm.toggleCompare(school.id) }
    }

    @State private var showCompare = false
    /// Destino de navegación: escuela + vía EN EL MISMO valor. Antes eran dos
    /// @State sueltos y `navigationDestination(item:)` captura su closure ANTES
    /// de que el body se reevalúe: el primer toque construía el detalle con la
    /// vía TODAVÍA nil (abría la escuela a secas) y el segundo funcionaba
    /// porque navVia conservaba el valor del intento anterior. Con un único
    /// item la carrera desaparece.
    /// Hashable A MANO por `id`: navigationDestination(item:) exige Hashable y
    /// la síntesis automática no es fiable con `School` (clase de Kotlin).
    struct SchoolNavTarget: Identifiable, Hashable {
        let school: School
        let via: String?
        var id: String { school.id + "|" + (via ?? "") }
        static func == (a: SchoolNavTarget, b: SchoolNavTarget) -> Bool { a.id == b.id }
        func hash(into hasher: inout Hasher) { hasher.combine(id) }
    }
    @State private var navTarget: SchoolNavTarget?
    @AppStorage(favoritesCompareKey) private var favoritesCompare = false
    /// "Enviar piedra": el selector de fotos está abierto.
    @State private var eligiendoFoto = false
    /// Grupos de escuela DESPLEGADOS en el modo "explorar" (§8.3) — empiezan
    /// todos plegados (Álvaro, 2026-10-01: "que directamente salga Zarzalejo
    /// con la puntuación y el número... pero que puedas verlos todos rápido");
    /// no persiste, se resetea al salir de la pantalla.
    @State private var expandedGroups: Set<String> = []
    // Buscador global de vías/bloques: vía a abrir al navegar + resultados.
    @State private var viaHits: [LineSearchHit] = []   // modelo de DOMINIO (via use case)
    @State private var viaSearchTask: Task<Void, Never>?

    /// Relanza la busqueda global (solo en modo vias/bloques), con debounce.
    private func dispatchViaSearch() {
        viaSearchTask?.cancel()
        let trimmed = vm.query.trimmingCharacters(in: .whitespaces)
        guard trimmed.count >= 2 else { viaHits = []; return }
        viaSearchTask = Task {
            try? await Task.sleep(nanoseconds: 350_000_000)
            guard !Task.isCancelled else { return }
            // Regla DI: por el use case del container, no la API directa.
            let hits = (try? await AppDependencies.shared.container.searchLines.invoke(query: trimmed)) ?? []
            if !Task.isCancelled { viaHits = hits }
        }
    }

    /// Resultados del buscador UNICO en DOS secciones (estilo Spotlight):
    /// ESCUELAS (top 5, acceso directo) y VIAS Y BLOQUES (global + mini-topo).
    /// Las cabeceras salen SIEMPRE al escribir: se aprende que busca ambas.
    private var viaHitsSection: some View {
        VStack(alignment: .leading, spacing: 4) {
            VStack(alignment: .leading, spacing: 0) {
                Text("ESCUELAS").font(Cumbre.mono(10, .bold)).tracking(1.2)
                    .foregroundStyle(Cumbre.ink3)
                    .padding(.horizontal, 12).padding(.vertical, 6)
                let schoolMatches = Array(vm.filtered.prefix(5))
                if schoolMatches.isEmpty {
                    Text("Sin resultados").font(.system(size: 12))
                        .foregroundStyle(Cumbre.ink3)
                        .padding(.horizontal, 12).padding(.bottom, 8)
                } else {
                    ForEach(schoolMatches, id: \.id) { school in
                        Button { navTarget = SchoolNavTarget(school: school, via: nil) } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(school.name).font(.system(size: 14))
                                        .foregroundStyle(Cumbre.ink).lineLimit(1)
                                    if let r = school.region, !r.isEmpty {
                                        Text(regionLabel(r)).font(.system(size: 12))
                                            .foregroundStyle(Cumbre.ink3).lineLimit(1)
                                    }
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                            }
                            .padding(.horizontal, 12).padding(.vertical, 8)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
                Divider().overlay(Cumbre.rule)
                Text("VÍAS Y BLOQUES").font(Cumbre.mono(10, .bold)).tracking(1.2)
                    .foregroundStyle(Cumbre.ink3)
                    .padding(.horizontal, 12).padding(.vertical, 6)
                if viaHits.isEmpty {
                    Text("Sin resultados").font(.system(size: 12))
                        .foregroundStyle(Cumbre.ink3)
                        .padding(.horizontal, 12).padding(.bottom, 8)
                }
                ForEach(viaHits, id: \.stableId) { h in
                    Button {
                        // (el .onAppear con los grados partidos está al final de la fila)
                        if let school = vm.schools.first(where: { $0.id == h.schoolId }) {
                            navTarget = SchoolNavTarget(
                                school: school, via: h.lineId ?? h.lineName ?? h.blockName)
                        }
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 1) {
                                if let g = h.grade, let split = h.lineId.flatMap({ gradeSplits.splits[$0] }) {
                                    // La altura parte la opinión: el grado va en dos colores.
                                    HStack(spacing: 6) {
                                        Text(h.lineName ?? h.blockName)
                                            .font(.system(size: 14)).foregroundStyle(Cumbre.ink).lineLimit(1)
                                        GradePill(grade: g, small: true, split: split)
                                    }
                                } else {
                                    Text((h.lineName ?? h.blockName) + (h.grade.map { " · \($0)" } ?? ""))
                                        .font(.system(size: 14)).foregroundStyle(Cumbre.ink).lineLimit(1)
                                }
                                Text([h.lineName != nil ? h.blockName : nil, h.sectorName, h.schoolName]
                                        .compactMap { $0 }.filter { !$0.isEmpty }
                                        .joined(separator: " · "))
                                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3).lineLimit(1)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                        }
                        .padding(.horizontal, 12).padding(.vertical, 9)
                        .contentShape(Rectangle())
                        .onAppear { gradeSplits.want(h.lineId) }
                    }
                    .buttonStyle(.plain)
                    // Mini-topo: foto de la cara con la linea dibujada (si el
                    // backend mando foto; las piedras salen sin trazo).
                    if let photo = h.photoPath, !photo.isEmpty {
                        // P8: dedup de puntos casi identicos (trazos antiguos
                        // fusionaban los guiones -> linea continua) y la FOTO
                        // tambien abre la piedra (paridad Android).
                        let pts = dedupPoints(TopoParse.points(h.linePath))
                        Button {
                            if let school = vm.schools.first(where: { $0.id == h.schoolId }) {
                                navTarget = SchoolNavTarget(
                                    school: school, via: h.lineId ?? h.lineName ?? h.blockName)
                            }
                        } label: {
                            TopoPhotoView(photoUrl: photo, lines: pts.count >= 2 ? [
                                TopoLineVM(id: h.lineId ?? "hit", name: h.lineName,
                                           grade: h.grade, startType: h.startType, points: pts)
                            ] : [])
                        }
                        .buttonStyle(.plain)
                        .padding(.horizontal, 12).padding(.bottom, 10)
                    }
                }
            }
            .background(Cumbre.paper)
            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }
        .padding(.horizontal, 16).padding(.vertical, 4)
    }

    // MARK: - Modo "explorar por grado" (§4.1/§8) — resultados

    private var exploreResultsSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(explore.loading ? L("Buscando…") : L("%@ vías/bloques encontrados", explore.hits.count))
                    .font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                Spacer()
                Button { explore.grouped.toggle() } label: {
                    Image(systemName: explore.grouped ? "rectangle.grid.1x2" : "square.grid.2x2")
                        .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 16).padding(.vertical, 8)

            if explore.loading && explore.hits.isEmpty {
                SkeletonList(count: 4) { SkeletonRow() }
            } else if explore.hits.isEmpty {
                EmptyRow(canClear: true) { explore.clearCriteria() }
            } else if explore.grouped {
                ForEach(explore.groups, id: \.schoolId) { group in
                    exploreGroupHeader(group)
                    if expandedGroups.contains(group.schoolId) {
                        ForEach(group.hits, id: \.stableId) { h in
                            exploreLineRow(h)
                            Divider().overlay(Cumbre.rule)
                        }
                    }
                }
            } else {
                ForEach(explore.sortedHits, id: \.stableId) { h in
                    exploreLineRow(h, showSchool: true)
                    Divider().overlay(Cumbre.rule)
                }
            }
        }
    }

    /// Cabecera de grupo: nombre de la escuela + su índice de escalabilidad de
    /// HOY (el mismo score que en la lista de Escuelas) + aviso MOJADA — una
    /// sola vez por grupo en vez de repetirlo vía a vía (§8.3). Pulsable: se
    /// minimiza para poder ver el resto de escuelas sin tanto scroll (Álvaro,
    /// 2026-10-01: "que se minimice y pueda ver el resto").
    private func exploreGroupHeader(_ g: (schoolId: String, schoolName: String, hits: [LineSearchHit])) -> some View {
        // Si hay un tramo de días elegido (DaySelectorRow), mostrar el score
        // COMBINADO del tramo, igual que ya hace la lista de Escuelas — si no,
        // el índice se quedaba siempre fijo en el de HOY aunque el usuario
        // marcara varios días (Álvaro, 2026-10-01).
        let score = vm.scores[g.schoolId]
        let scoreInt = vm.rangeMode
            ? vm.rangeScores[g.schoolId].map { Int($0.combinedScore) }
            : score.map { Int($0.todayScore) }
        let color = scoreInt.map { Cumbre.score($0) } ?? Cumbre.ink3
        let collapsed = !expandedGroups.contains(g.schoolId)
        return VStack(alignment: .leading, spacing: 0) {
            Button {
                if collapsed { expandedGroups.insert(g.schoolId) } else { expandedGroups.remove(g.schoolId) }
            } label: {
                HStack(spacing: 10) {
                    Text(scoreInt.map(String.init) ?? "—")
                        .font(Cumbre.serif(17, .bold))
                        .foregroundStyle(color)
                        .frame(width: 34, height: 30)
                        .background(color.opacity(0.12))
                        .overlay(Rectangle().stroke(color, lineWidth: 1))
                    VStack(alignment: .leading, spacing: 1) {
                        HStack(spacing: 7) {
                            Text(g.schoolName).font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink)
                            let kind = SchoolKind(style: vm.schools.first(where: { $0.id == g.schoolId })?.style)
                            if !kind.isEmpty { SchoolKindIcons(kind: kind, height: 15) }
                        }
                        if score?.dryRock == false {
                            Text(L("● MOJADA")).font(.system(size: 10, weight: .semibold)).tracking(0.6)
                                .foregroundStyle(Cumbre.bad)
                        }
                    }
                    Spacer()
                    Text(g.hits.count == 1 ? L("1 vía/bloque") : L("%@ vías/bloques", g.hits.count))
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    Image(systemName: collapsed ? "chevron.down" : "chevron.up")
                        .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                }
                .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, vm.rangeMode ? 4 : 8)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            // Tramo de días: una puntuación por día (J/V/S…), no la combinada
            // sola — mismo componente que ya usa la lista de Escuelas (Álvaro,
            // 2026-10-01: "que salgan las tres puntuaciones... no la suma").
            if vm.rangeMode, let range = vm.rangeScores[g.schoolId] {
                DayRangeRow(range: range)
                    .padding(.horizontal, 16).padding(.bottom, 8)
            }
        }
        .background(Cumbre.paper)
    }

    /// Fila de vía: miniatura (§8.4), nombre + grado coloreado, piedra/escuela,
    /// orientación votada o "SIN ORIENTACIÓN ASIGNADA" (§8.1), distancia.
    private func exploreLineRow(_ h: LineSearchHit, showSchool: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 0) {
        Button {
            if let school = vm.schools.first(where: { $0.id == h.schoolId }) {
                navTarget = SchoolNavTarget(school: school, via: h.lineId ?? h.lineName ?? h.blockName)
            }
        } label: {
            HStack(spacing: 10) {
                // (el .onAppear está al final de la fila)
                if let photo = h.photoPath, !photo.isEmpty {
                    // Trazo fino SIN badge de número/tipo de inicio — el badge
                    // de TopoPhotoView está pensado para el visor grande; a 44pt
                    // tapaba la foto entera (Álvaro, 2026-10-01: "se ve fatal").
                    MiniTopoThumbnail(photoUrl: photo, points: dedupPoints(TopoParse.points(h.linePath)),
                                      grade: h.grade)
                        .frame(width: 44, height: 44)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                        .overlay(RoundedRectangle(cornerRadius: 4).stroke(Cumbre.rule, lineWidth: 1))
                }
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(h.lineName ?? h.blockName).font(.system(size: 14, weight: .medium))
                            .foregroundStyle(Cumbre.ink).lineLimit(1)
                        if let g = h.grade {
                            if let split = h.lineId.flatMap({ gradeSplits.splits[$0] }) {
                                // La altura parte la opinión: los dos grados, cada uno con su color.
                                GradePill(grade: g, small: true, split: split)
                            } else {
                                Text(g).font(Cumbre.mono(11, .bold)).foregroundStyle(GradeColor.color(g))
                            }
                        }
                        // Restricción en vigor (cierre por nidificación…): el detalle se ve al abrir la escuela.
                        if let k = h.restrictionKind { RestrictionMark(kind: k, size: 14) }
                        // Vía o bloque: la modalidad del propio resultado manda (la
                        // trae el backend); si no llega (servidor viejo), el filtro
                        // activo y, por último, el estilo de la escuela — que solo vale
                        // si es de un único tipo (en mixtas no se sabe).
                        let rowKind = SchoolKind(style: h.discipline ?? vm.style
                            ?? vm.schools.first(where: { $0.id == h.schoolId })?.style)
                        if rowKind.isSingle { SchoolKindIcons(kind: rowKind, height: 13) }
                    }
                    let subtitle = [h.lineName != nil ? h.blockName : nil, showSchool ? h.schoolName : nil]
                        .compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
                    if !subtitle.isEmpty {
                        Text(subtitle).font(.system(size: 12)).foregroundStyle(Cumbre.ink3).lineLimit(1)
                    }
                    if let o = h.orientation, !o.isEmpty {
                        Text(o).font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink3)
                    } else {
                        Text(L("SIN ORIENTACIÓN ASIGNADA"))
                            .font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.ink3.opacity(0.7))
                    }
                }
                Spacer()
                if let la = vm.userLat, let lo = vm.userLon,
                   let hlat = h.lat?.doubleValue, let hlon = h.lon?.doubleValue {
                    let km = Geo.shared.haversineKm(lat1: la, lon1: lo, lat2: hlat, lon2: hlon)
                    Text("\(Int(km.rounded())) km").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 9)
            .contentShape(Rectangle())
            .onAppear { gradeSplits.want(h.lineId) }
        }
        .buttonStyle(.plain)
        // Vídeos de beta (por altura) y quién la ha hecho: se ve por qué sale con el filtro de ALTURA. Fuera del botón.
        CommunityBadgesRow(hit: h)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.leading, 70).padding(.trailing, 16).padding(.bottom, h.communityBadges.isEmpty ? 0 : 8)
        }
    }
}

/// Id estable de un hit del buscador (id: \.offset invalidaba las filas y
/// sus TopoPhotoView en cada tecla).
extension LineSearchHit {
    var stableId: String { (lineId ?? "") + "|" + blockName + "|" + (schoolId ?? "") }
}

// School (clase Kotlin) Identifiable por su id — para navigationDestination(item:).
extension School: Identifiable {}


/// P8: quita puntos consecutivos casi identicos (trazos antiguos fusionaban
/// los guiones y la linea salia continua en el buscador).
func dedupPoints(_ raw: [CGPoint]) -> [CGPoint] {
    var pts: [CGPoint] = []
    for pt in raw {
        if let last = pts.last, abs(pt.x - last.x) + abs(pt.y - last.y) < 0.004 { continue }
        pts.append(pt)
    }
    return pts
}

/// Miniatura de resultado: foto + trazo fino, SIN el badge de número/tipo de
/// inicio que usa TopoPhotoView (pensado para el visor grande) — a 44pt ese
/// badge tapaba la foto entera. Solo "se intuye" el trazo, como pidió Álvaro.
struct MiniTopoThumbnail: View {
    let photoUrl: String
    let points: [CGPoint]
    let grade: String?
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            Cumbre.rule.opacity(0.15)
            if let image { Image(uiImage: image).resizable().scaledToFill() }
            if points.count >= 2 {
                GeometryReader { geo in
                    Path { path in
                        let pts = points.map { CGPoint(x: $0.x * geo.size.width, y: $0.y * geo.size.height) }
                        path.move(to: pts[0])
                        for p in pts.dropFirst() { path.addLine(to: p) }
                    }
                    .stroke(GradeColor.color(grade), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                }
            }
        }
        .task(id: photoUrl) { image = await ImageCache.image(photoUrl) }
    }
}
