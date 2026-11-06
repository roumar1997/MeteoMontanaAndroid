import SwiftUI
import Shared
import CoreLocation

// Filtros de Escuelas (distancia, roca, estilo, días, Vías/Bloques con grado, altura y orden).

/// Escalera REAL de grados — la MISMA lista que usa el editor de vías
/// (`BOULDER_GRADES` en ProposeFlow.swift), sin inventar nada. "PROY" (proyecto,
/// sin grado) no es un punto de la escalera, así que se excluye del rango.
let EXPLORE_GRADE_LADDER: [String] = BOULDER_GRADES.filter { $0 != "PROY" }

/// Barra de filtros — réplica de SchoolFiltersBar.kt, con el selector
/// Escuelas/Bloques arriba (Álvaro, 2026-10-01: la versión sin pestaña no
/// convencía en uso real — vuelta al mockup con pestaña + selector de
/// escuelas + slider de grado + orientación, docs/BLOCK_SEARCH_DESIGN.md §4/§8).
struct FilterChips: View {
    @ObservedObject var vm: SchoolListViewModel
    @ObservedObject var explore: ExploreLinesViewModel
    /// Plegados por defecto (2026-10-14: "demasiadas cosas" a la vista). Lo activo se resume en una línea.
    @State private var open = false
    /// Estrella «FAVS»: enseña la comparación de tus favoritas encima de la lista (se recuerda).
    @AppStorage(favoritesCompareKey) private var favoritesCompare = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            tabSwitcher
            mapButton
            filterToggleRow
            locationWarning
            if vm.daysOpen { DaySelectorRow(vm: vm) }
            if open { filterSections }
        }
        .padding(.vertical, 8)
    }

    /// Cuántos filtros se han movido respecto a lo de siempre (50 km, todo, todo).
    private var activeCount: Int {
        var n = 0
        if vm.maxDistanceKm != 50 { n += 1 }
        if vm.style != nil { n += 1 }
        if vm.rock != nil { n += 1 }
        if explore.tab == .schools {
            if vm.showMode != .all { n += 1 }
        } else {
            if explore.gradeMin != nil || explore.gradeMax != nil { n += 1 }
            if !explore.orientations.isEmpty { n += 1 }
            if explore.height != nil { n += 1 }
            if explore.sortBy != .distance { n += 1 }
        }
        return n
    }

    /// "Hasta 50 km · Bloque · 6A–7A": lo aplicado, sin tener que abrir el panel.
    private var summary: String {
        var parts: [String] = []
        if !vm.loading { parts.append(L("%@ escuelas", vm.filtered.count)) }
        parts.append(vm.maxDistanceKm.map { L("Hasta %@ km", Int($0)) } ?? L("Cualquier distancia"))
        if let s = vm.style { parts.append(styleLabel(s)) }
        if let r = vm.rock { parts.append(rockLabel(r)) }
        if explore.tab == .schools {
            if vm.showMode != .all { parts.append(vm.showMode.label) }
        } else {
            if explore.gradeMin != nil || explore.gradeMax != nil {
                parts.append("\(explore.gradeMin ?? "…")–\(explore.gradeMax ?? "…")")
            }
            if !explore.orientations.isEmpty { parts.append(explore.orientations.sorted().joined(separator: "/")) }
            if let h = explore.height {
                parts.append((h == "TALL" ? "+1,70" : "−1,70")
                             + (explore.heightSource.isEmpty ? "" : " " + SchoolBlocksExplorerModel.sourceLabel(explore.heightSource).lowercased()))
            }
            if explore.sortBy != .distance { parts.append(explore.sortBy.label) }
        }
        return parts.joined(separator: " · ")
    }

    private var filterToggleRow: some View {
        HStack(spacing: 8) {
            Button { withAnimation(.easeInOut(duration: 0.2)) { open.toggle() } } label: {
                HStack(spacing: 8) {
                    HStack(spacing: 6) {
                        Image(systemName: "slider.horizontal.3").font(.system(size: 12, weight: .semibold))
                        Text(activeCount > 0 ? L("FILTROS (%@)", activeCount) : L("FILTROS")).font(Cumbre.mono(11, .bold)).tracking(1)
                    }
                    .foregroundStyle(activeCount > 0 ? Color.white : Cumbre.ink2)
                    .padding(.horizontal, 12).padding(.vertical, 7)
                    .background(activeCount > 0 ? Cumbre.terraFill : Cumbre.paper, in: Capsule())
                    .overlay(Capsule().stroke(activeCount > 0 ? Cumbre.terraFill : Cumbre.rule, lineWidth: 1))
                    // Nunca se parte («FILTR / OS / (1)» con BORRAR al lado, captura del 302): cede «N escuelas».
                    .lineLimit(1).fixedSize()
                    if !vm.loading {
                        Text(L("%@ escuelas", vm.filtered.count)).font(.system(size: 12)).foregroundStyle(Cumbre.ink3).lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                    Spacer(minLength: 0)
                    Image(systemName: open ? "chevron.up" : "chevron.down")
                        .font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.ink3)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            clearButton
            if explore.tab == .schools { favoritesChip }
            dayChip
        }
        .padding(.horizontal, 12)
        .guideTarget("schools.filters")
    }

    /// «BORRAR FILTROS»: aparece solo cuando hay algún filtro movido y lo deja todo como de base, sin desplegar nada.
    @ViewBuilder private var clearButton: some View {
        if activeCount > 0 {
            Button { withAnimation(.easeInOut(duration: 0.2)) { resetFilters() } } label: {
                HStack(spacing: 4) {
                    Image(systemName: "xmark").font(.system(size: 9, weight: .bold))
                    Text(L("BORRAR")).font(Cumbre.mono(10.5, .bold)).tracking(0.6)
                }
                .foregroundStyle(Cumbre.terra)
                .padding(.horizontal, 10).padding(.vertical, 7)
                .overlay(Capsule().stroke(Cumbre.terra, lineWidth: 1))
            }
            .buttonStyle(.plain)
        }
    }

    /// Todo como de base: 50 km, cualquier estilo y roca, todas las escuelas, sin grado ni orientación, por cercanía.
    private func resetFilters() {
        vm.maxDistanceKm = 50
        vm.style = nil
        vm.rock = nil
        vm.showMode = .all
        explore.clearAll()
    }

    /// «★ FAVS»: enciende/apaga la comparación de tus favoritas. No filtra la lista (para eso, Mostrar → Favoritos).
    private var favoritesChip: some View {
        Button {
            withAnimation(.easeInOut(duration: 0.2)) { favoritesCompare.toggle() }
        } label: {
            HStack(spacing: 4) {
                Image(systemName: favoritesCompare ? "star.fill" : "star").font(.system(size: 10, weight: .bold))
                Text(L("FAVS")).font(Cumbre.mono(10.5, .bold)).tracking(0.6)
            }
            .foregroundStyle(favoritesCompare ? Color.white : Cumbre.ink2)
            .padding(.horizontal, 10).padding(.vertical, 7)
            .background(favoritesCompare ? Cumbre.terraFill : Cumbre.paper, in: Capsule())
            .overlay(Capsule().stroke(favoritesCompare ? Cumbre.terraFill : Cumbre.rule, lineWidth: 1))
            .lineLimit(1).fixedSize()
        }
        .buttonStyle(.plain)
        .accessibilityLabel(L("Comparar tus favoritas"))
        .accessibilityAddTraits(favoritesCompare ? .isSelected : [])
    }

    /// "HOY ⌄" (o el tramo elegido: "VIE–DOM ⌄"): abre los días para comparar un tramo de hasta 5.
    private var dayChip: some View {
        Button {
            withAnimation(.easeInOut(duration: 0.2)) { vm.daysOpen.toggle() }
        } label: {
            HStack(spacing: 4) {
                Text(DaySelectorRow.rangeLabel(vm.selectedDates.sorted())).font(Cumbre.mono(10.5, .bold)).tracking(0.6)
                Image(systemName: vm.daysOpen ? "chevron.up" : "chevron.down").font(.system(size: 9, weight: .bold))
            }
            .foregroundStyle(Color.white)
            .padding(.horizontal, 11).padding(.vertical, 7)
            .background(Cumbre.terraFill, in: Capsule())
        }
        .buttonStyle(.plain)
    }

    // Sin esto, un permiso denegado se quedaba sin forma de arreglarse
    // desde aquí — el mapa de Escuelas no pintaba el punto azul ni
    // aplicaba "cercanía" en silencio, para siempre (Álvaro, 2026-10-01).
    // Por PERMISO, no por "aún sin coordenadas": al abrir la app la
    // ubicación tarda un momento y el aviso salía y desaparecía en cada
    // arranque (Álvaro, 2026-10-02). Siempre a la vista, aunque los filtros estén plegados.
    @ViewBuilder private var locationWarning: some View {
        if vm.needsLocationPermission {
            Button { vm.requestLocation() } label: {
                HStack(spacing: 8) {
                    Image(systemName: "location.slash").foregroundStyle(Cumbre.terra)
                    Text(L("Sin ubicación — toca para activarla"))
                        .font(.system(size: 12.5)).foregroundStyle(Cumbre.ink2)
                    Spacer()
                    Text(L("ACTIVAR")).font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra)
                }
                .padding(10)
                .background(Cumbre.terraBg).overlay(Rectangle().stroke(Cumbre.terra.opacity(0.4), lineWidth: 1))
            }.buttonStyle(.plain)
        }
    }

    private var filterSections: some View {
        VStack(alignment: .leading, spacing: 12) {
            section(L("DISTANCIA")) {
                chipRow(SchoolListViewModel.distanceOptions, id: { $0.map { String(Int($0)) } ?? "all" },
                        isSel: { $0 == vm.maxDistanceKm },
                        label: { $0 == nil ? NSLocalizedString("schools_filter_all", comment: "") : "\(Int($0!)) km" }) { vm.maxDistanceKm = $0 }
            }
            // (el aviso de ubicación vive en locationWarning, siempre visible)
            // desde aquí — el mapa de Escuelas no pintaba el punto azul ni
            // aplicaba "cercanía" en silencio, para siempre (Álvaro, 2026-10-01).
            // Por PERMISO, no por "aún sin coordenadas": al abrir la app la
            // ubicación tarda un momento y el aviso salía y desaparecía en cada
            // arranque (Álvaro, 2026-10-02).
            if explore.tab == .blocks {
                section(L("GRADO")) { gradeRangeSection }
                section(L("ORIENTACIÓN")) { orientationChips }
            }
            // En VÍAS/BLOQUES se ve todo: el filtro Vía/Bloque sobraba (Álvaro, 2026-10-18). Solo en ESCUELAS.
            if explore.tab != .blocks {
                section(L("ESTILO")) {
                    chipRow([String?.none] + vm.styles.map { Optional($0) }, id: { $0 ?? "all" },
                            isSel: { $0 == vm.style },
                            label: { $0.map(styleLabel) ?? NSLocalizedString("schools_filter_all", comment: "") }) { vm.style = $0 }
                }
            } else {
                Color.clear.frame(height: 0).onAppear { if vm.style != nil { vm.style = nil } }
            }
            section(L("TIPO DE ROCA")) {
                chipRow([String?.none] + vm.rocks.map { Optional($0) }, id: { $0 ?? "all" },
                        isSel: { $0 == vm.rock },
                        label: { $0.map(rockLabel) ?? NSLocalizedString("schools_filter_all", comment: "") }) { vm.rock = $0 }
            }
            if explore.tab == .schools {
                section(L("MOSTRAR")) {
                    chipRow(SchoolListViewModel.ShowMode.allCases, id: { $0.rawValue },
                            isSel: { $0 == vm.showMode },
                            label: { $0.label }) { vm.showMode = $0 }
                }
            }
            if explore.tab == .blocks { heightSection }
            section(L("ORDENAR POR")) {
                if explore.tab == .blocks {
                    chipRow(ExploreLinesViewModel.Sort.allCases, id: { $0.rawValue },
                            isSel: { $0 == explore.sortBy },
                            label: { $0.label }) { explore.sortBy = $0 }
                } else {
                    chipRow(SchoolListViewModel.SortMode.allCases, id: { $0.rawValue },
                            isSel: { $0 == vm.sortBy },
                            label: { $0.label }) { vm.sortBy = $0 }
                }
            }
        }
    }

    /// +1,70 / −1,70: con vídeo de beta o hechas por gente de esa altura (la que dijo en su perfil). Aparte del panel
    /// para que Swift no tarde demasiado en comprobar los tipos de todo el bloque de filtros.
    private var heightSection: some View {
        let options: [String?] = [nil, "TALL", "SHORT"]
        return VStack(alignment: .leading, spacing: 12) {
            section(L("ALTURA")) {
                chipRow(options, id: { (h: String?) -> String in h ?? "all" },
                        isSel: { (h: String?) -> Bool in h == explore.height },
                        label: { (h: String?) -> String in Self.heightLabel(h) }) { (h: String?) in explore.height = h }
            }
            if explore.height != nil { sourceSection }
        }
    }

    /// QUÉ BUSCAS (solo con una altura elegida): vídeo o encadenes / solo vídeo / solo encadenes.
    private var sourceSection: some View {
        let sources: [String] = ["", "_VIDEO", "_SENDS"]
        return section(L("QUÉ BUSCAS")) {
            chipRow(sources, id: { (s: String) -> String in s.isEmpty ? "any" : s },
                    isSel: { (s: String) -> Bool in s == explore.heightSource },
                    label: { (s: String) -> String in SchoolBlocksExplorerModel.sourceLabel(s) }) { (s: String) in
                explore.heightSource = s
            }
        }
    }

    private static func heightLabel(_ h: String?) -> String {
        switch h {
        case nil: return NSLocalizedString("schools_filter_all", comment: "")
        case "TALL": return "+1,70"
        default: return "−1,70"
        }
    }

    /// Segmented control Escuelas / Bloques — cambia la MISMA lista de abajo
    /// entre escuelas y vías, sin pantalla nueva.
    private var tabSwitcher: some View {
        HStack(spacing: 2) {
            tabButton(L("Escuelas"), active: explore.tab == .schools) { explore.tab = .schools }
            tabButton(L("Vías/Bloques"), active: explore.tab == .blocks) { explore.tab = .blocks }
        }
        .padding(3)
        .background(Cumbre.rule.opacity(0.18), in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
        .padding(.horizontal, 12)
    }

    /// Abre/cierra el mapa de la lista. En su PROPIA línea y a todo el ancho: dentro del selector pasaba desapercibido
    /// (Álvaro, 2026-10-14: "casi no se ve que existe" y "su propia línea me parecía más intuitivo").
    private var mapButton: some View {
        Button { withAnimation { vm.showMap.toggle() } } label: {
            HStack(spacing: 8) {
                Image(systemName: "map").font(.system(size: 14, weight: .semibold))
                Text(vm.showMap ? L("OCULTAR MAPA") : L("VER MAPA")).font(Cumbre.mono(12, .bold)).tracking(0.8)
                Spacer(minLength: 0)
                Image(systemName: vm.showMap ? "chevron.up" : "chevron.down").font(.system(size: 11, weight: .bold))
            }
            .foregroundStyle(vm.showMap ? Color.white : Cumbre.terra)
            .padding(.horizontal, 14).padding(.vertical, 11)
            .frame(maxWidth: .infinity)
            .background(vm.showMap ? Cumbre.terra : Cumbre.terra.opacity(0.10), in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
            .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius).stroke(Cumbre.terra, lineWidth: 1))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 12)
        .guideTarget("schools.map")
        .accessibilityLabel(L("Mapa"))
    }

    private func tabButton(_ t: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(t).font(Cumbre.mono(12, .bold))
                .foregroundStyle(active ? .white : Cumbre.ink2)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 9)
                .background(active ? Cumbre.terra : Color.clear, in: RoundedRectangle(cornerRadius: Cumbre.pillRadius - 2))
        }
        .buttonStyle(.plain)
    }

    /// Dos campos MÍN/MÁX, cada uno con su desplegable de grados — Álvaro,
    /// 2026-10-01 (2ª vuelta): en vez de dos filas de chips, "uno mínimo...
    /// abres y se te abre un desplegable... y al lado máximo". El último
    /// grado usado se recuerda entre sesiones (UserDefaults, vía el VM).
    private var gradeRangeSection: some View {
        HStack(spacing: 10) {
            gradeDropdown(L("MÍN"), selected: explore.gradeMin) { explore.gradeMin = $0 }
            gradeDropdown(L("MÁX"), selected: explore.gradeMax) { explore.gradeMax = $0 }
        }
        .padding(.horizontal, 12)
    }

    private func gradeDropdown(_ label: String, selected: String?, onPick: @escaping (String?) -> Void) -> some View {
        Menu {
            Button(L("Sin filtro")) { onPick(nil) }
            ForEach(EXPLORE_GRADE_LADDER, id: \.self) { g in
                Button(g) { onPick(g) }
            }
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.ink3)
                HStack {
                    Text(selected ?? L("—")).font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink)
                    Spacer()
                    Image(systemName: "chevron.up.chevron.down").font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                }
            }
            .padding(.horizontal, 10).padding(.vertical, 8)
            .frame(maxWidth: .infinity)
            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
            .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius).stroke(Cumbre.rule, lineWidth: 1))
        }
    }

    private var orientationChips: some View {
        chipRow(["N", "NE", "E", "SE", "S", "SO", "O", "NO"], id: { $0 },
                isSel: { explore.orientations.contains($0) },
                label: { $0 }) { o in
            if explore.orientations.contains(o) { explore.orientations.remove(o) } else { explore.orientations.insert(o) }
        }
    }

    private func section<C: View>(_ title: String, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).eyebrow().padding(.horizontal, 12)
            content()
        }
    }

    private func chipRow<T>(_ items: [T], id: @escaping (T) -> String,
                            isSel: @escaping (T) -> Bool, label: @escaping (T) -> String,
                            onPick: @escaping (T) -> Void) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                    Button { onPick(item) } label: { chip(label(item), active: isSel(item)) }
                        .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12)
        }
    }

    private func chip(_ t: String, active: Bool) -> some View {
        Text(t)
            .font(Cumbre.mono(11, .bold))
            .tracking(0.8)
            .foregroundStyle(active ? .white : Cumbre.ink2)
            .padding(.horizontal, 12).padding(.vertical, 7)
            .background(active ? Cumbre.terra : Cumbre.paper,
                        in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
            .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius).stroke(Cumbre.rule, lineWidth: 1))
    }
}
