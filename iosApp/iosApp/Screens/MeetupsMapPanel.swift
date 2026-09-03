import SwiftUI
import Shared
import CoreLocation
import MapLibre

// ── ViewModel ──────────────────────────────────────────────────────────────

// Mapa de quedadas + alerta configurable + filtro por escuela.
// Reparto de MeetupsView.swift.

struct MeetupsMapPanel: View {
    let meetups: [Meetup]
    let userLat: Double?
    let userLon: Double?
    let maxDistanceKm: Double?
    let onSchoolSelected: (String) -> Void

    @State private var show = false
    // Satélite por defecto, paridad con el mapa de Escuelas (Álvaro,
    // 2026-09-03: "debería funcionar igual que el de escuelas").
    @State private var mapStyle: MapStyleKind = .satellite
    @State private var popup: SchoolMeetupGroup?
    @State private var fullscreenMap = false
    // Filtro SOLO del mapa (no toca la lista de quedadas de fuera) — mismo
    // criterio que el mapa de Escuelas, cuyos filtros de pantalla completa
    // tampoco afectan a nada fuera del propio mapa salvo el estilo compartido.
    @State private var disciplineFilter: String?

    private var filteredMeetups: [Meetup] {
        guard let d = disciplineFilter else { return meetups }
        return meetups.filter { $0.discipline == d }
    }

    private var groups: [SchoolMeetupGroup] {
        let grouped = Dictionary(grouping: filteredMeetups.filter {
            $0.schoolLat?.doubleValue != nil && $0.schoolLon?.doubleValue != nil
        }, by: { $0.schoolId })
        return grouped.compactMap { (id, list) -> SchoolMeetupGroup? in
            guard let first = list.first,
                  let lat = first.schoolLat?.doubleValue,
                  let lon = first.schoolLon?.doubleValue else { return nil }
            return SchoolMeetupGroup(schoolId: id, schoolName: first.schoolName ?? id, lat: lat, lon: lon, count: list.count)
        }
    }

    private var markers: [CumbreMarker] {
        var ms: [CumbreMarker] = []
        if let lat = userLat, let lon = userLon {
            ms.append(CumbreMarker(
                id: "__USER__", coordinate: .init(latitude: lat, longitude: lon),
                title: "Tu ubicacion", kind: .user
            ))
        }
        for g in groups {
            // Diamante terracota con el nº de quedadas dentro + nombre de escuela debajo.
            ms.append(CumbreMarker(
                id: g.schoolId, coordinate: .init(latitude: g.lat, longitude: g.lon),
                title: g.schoolName,
                kind: .score, color: UIColor(red: 0.76, green: 0.33, blue: 0.18, alpha: 1),
                score: g.count, name: g.schoolName, showName: true
            ))
        }
        return ms
    }

    private var center: CLLocationCoordinate2D {
        if let lat = userLat, let lon = userLon {
            return .init(latitude: lat, longitude: lon)
        }
        if let first = groups.first {
            return .init(latitude: first.lat, longitude: first.lon)
        }
        return .init(latitude: 40.4, longitude: -3.7)
    }

    var body: some View {
        VStack(spacing: 0) {
            Button { withAnimation { show.toggle() } } label: {
                HStack(spacing: 6) {
                    Image(systemName: "map").font(.system(size: 13)).foregroundColor(Cumbre.terra)
                    Text(show ? "OCULTAR MAPA" : "VER MAPA DE QUEDADAS")
                        .font(Cumbre.mono(11, .bold)).tracking(0.8)
                        .foregroundColor(Cumbre.terra)
                    Spacer()
                    if !groups.isEmpty {
                        Text("\(groups.count) escuela\(groups.count == 1 ? "" : "s")")
                            .font(.caption).foregroundColor(Cumbre.ink.opacity(0.5))
                    }
                    Image(systemName: show ? "chevron.up" : "chevron.down")
                        .font(.system(size: 11)).foregroundColor(Cumbre.terra)
                }
                .padding(.horizontal, 14).padding(.vertical, 10)
                .background(Cumbre.terra.opacity(0.08), in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
                .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius).stroke(Cumbre.terra, lineWidth: 1))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 16).padding(.vertical, 4)

            if show {
                mapBox(height: 240)
                if groups.isEmpty {
                    Text("No hay quedadas con ubicacion para mostrar en el mapa")
                        .font(.caption).foregroundColor(Cumbre.ink.opacity(0.6))
                        .padding(.horizontal, 16).padding(.vertical, 8)
                }
                if let g = popup { popupCard(g) }
            }
        }
        .fullScreenCover(isPresented: $fullscreenMap) {
            ZStack(alignment: .bottom) {
                mapBox(height: 240, isFullscreen: true)
                    .ignoresSafeArea()
                if let g = popup { popupCard(g).padding(.bottom, 8) }
            }
        }
    }

    private func mapBox(height: CGFloat, isFullscreen: Bool = false) -> some View {
        ZStack(alignment: .topTrailing) {
            MapLibreView(
                center: center,
                zoom: groups.isEmpty && maxDistanceKm != nil ? zoomForKm(maxDistanceKm!) : (userLat != nil ? 8 : 6),
                markers: markers, style: mapStyle,
                autoFitToMarkers: !groups.isEmpty,
                onTapMarker: { id in
                    popup = groups.first(where: { $0.schoolId == id })
                },
                // A pantalla completa la brújula baja bajo la isla/notch,
                // igual que en el mapa de Escuelas (Álvaro, 2026-09-01).
                compassTopMargin: isFullscreen ? 106 : 56
            )
            .frame(maxWidth: .infinity, maxHeight: isFullscreen ? .infinity : height)
            .id("\(maxDistanceKm ?? -1)|\(disciplineFilter ?? "-")|\(groups.map { $0.schoolId }.sorted().joined())")
            MapStyleChips(selection: $mapStyle)
                .padding(.top, isFullscreen ? 50 : 0)
            VStack {
                HStack {
                    Button { fullscreenMap.toggle() } label: {
                        Image(systemName: fullscreenMap
                              ? "arrow.down.right.and.arrow.up.left"
                              : "arrow.up.left.and.arrow.down.right")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Cumbre.ink)
                            .frame(width: 34, height: 34)
                            .background(Cumbre.bg)
                            .clipShape(Circle())
                            .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    Spacer()
                }
                .padding(.top, isFullscreen ? 50 : 0)
                Spacer()
            }
            .padding(10)
            .frame(maxWidth: .infinity, maxHeight: isFullscreen ? .infinity : height)
        }
        .frame(height: isFullscreen ? nil : height)
        .overlay(isFullscreen ? nil : Divider(), alignment: .bottom)
        .safeAreaInset(edge: .bottom) {
            if isFullscreen { fullscreenFilters }
        }
    }

    private var fullscreenFilters: some View {
        HStack(spacing: 6) {
            Text("MODALIDAD").font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.ink3)
            filterPill(label: "Ambas", selected: disciplineFilter == nil) { disciplineFilter = nil }
            filterPill(label: "Bloque", selected: disciplineFilter == "BOULDER") { disciplineFilter = "BOULDER" }
            filterPill(label: "Via", selected: disciplineFilter == "ROUTE") { disciplineFilter = "ROUTE" }
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .frame(maxWidth: .infinity)
        // Material de cristal nativo (blur real), paridad con el mapa de Escuelas.
        .background(.ultraThinMaterial)
    }

    private func filterPill(label: String, selected: Bool, onTap: @escaping () -> Void) -> some View {
        Button(action: onTap) {
            Text(label).font(Cumbre.mono(11, .bold))
                .foregroundStyle(selected ? .white : Cumbre.ink2)
                .padding(.horizontal, 10).padding(.vertical, 8)
                .background(selected ? Cumbre.terraFill : Color.clear,
                            in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
                .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius)
                    .stroke(selected ? Color.clear : Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private func popupCard(_ g: SchoolMeetupGroup) -> some View {
        // Mini-ficha (mismo estilo que parkings/escuelas).
        HStack(spacing: 10) {
            Text("\(g.count)")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 34, height: 34)
                .background(Cumbre.terraFill)
                .clipShape(RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading, spacing: 2) {
                Text(g.schoolName).font(.body).fontWeight(.medium)
                    .foregroundColor(Cumbre.ink)
                Text("\(g.count) quedada\(g.count == 1 ? "" : "s") activa\(g.count == 1 ? "" : "s")")
                    .font(.caption).foregroundColor(Cumbre.ink2)
            }
            Spacer()
            Button {
                onSchoolSelected(g.schoolId)
                popup = nil
                fullscreenMap = false
                withAnimation { show = false }
            } label: {
                Text("VER").font(Cumbre.mono(10, .bold)).tracking(1.2)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 10).padding(.vertical, 6)
                    .background(Cumbre.terraFill)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            }
            .buttonStyle(.plain)
            Button { popup = nil } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Cumbre.ink2)
                    .padding(4)
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(Cumbre.bg)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.rule, lineWidth: 1))
        .padding(.horizontal, 8).padding(.vertical, 4)
    }

    private func zoomForKm(_ km: Double) -> Double {
        if km <= 25 { return 10 }
        if km <= 50 { return 9 }
        if km <= 100 { return 8 }
        if km <= 200 { return 7 }
        if km <= 500 { return 6 }
        return 5
    }
}

// ── Configuración de alertas (espejo de MeetupAlertScreen.kt) ───────────────

struct MeetupAlertView: View {
    @ObservedObject var vm: MeetupsViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var enabled = false
    @State private var selectedDays: Set<String> = []
    @State private var discipline: String?
    @State private var privacy: String?
    @State private var schoolId: String?
    @State private var schoolName: String?
    @State private var maxDistanceKm: Int32?
    @State private var showSchoolPicker = false
    private let next14 = generateNextDays(14)
    private var isWoman: Bool { vm.myGender == "WOMAN" }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    // Activar/desactivar
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Activar alertas").font(.body).fontWeight(.medium)
                            Text("Recibe una notificacion cuando se cree una quedada que te interese")
                                .font(.caption).foregroundColor(Cumbre.ink.opacity(0.6))
                        }
                        Spacer()
                        Toggle("", isOn: $enabled).labelsHidden().tint(Cumbre.terra)
                    }

                    Group {
                        Divider()
                        // Escuela
                        VStack(alignment: .leading, spacing: 6) {
                            FilterGroupLabel(text: "ESCUELA")
                            Text("Avisame solo de una escuela en concreto, o de cualquiera")
                                .font(.caption).foregroundColor(Cumbre.ink.opacity(0.6))
                            HStack {
                                Button {
                                    showSchoolPicker = true
                                } label: {
                                    HStack {
                                        Image(systemName: "magnifyingglass").font(.system(size: 13))
                                        Text(schoolName ?? "Cualquier escuela").lineLimit(1)
                                        Spacer()
                                    }
                                    .padding(.vertical, 10).padding(.horizontal, 12)
                                    .background(RoundedRectangle(cornerRadius: 4).stroke(Cumbre.rule))
                                }
                                .buttonStyle(.plain)
                                .foregroundColor(Cumbre.ink)
                                if schoolId != nil {
                                    Button {
                                        schoolId = nil; schoolName = nil
                                    } label: {
                                        Image(systemName: "xmark.circle.fill").foregroundColor(Cumbre.ink.opacity(0.5))
                                    }
                                }
                            }
                        }

                        Divider()
                        // Modalidad
                        VStack(alignment: .leading, spacing: 6) {
                            FilterGroupLabel(text: "MODALIDAD")
                            HStack(spacing: 6) {
                                FilterPill(label: "Ambas", selected: discipline == nil) { discipline = nil }
                                FilterPill(label: "Bloque", selected: discipline == "BOULDER") { discipline = "BOULDER" }
                                FilterPill(label: "Via", selected: discipline == "ROUTE") { discipline = "ROUTE" }
                            }
                        }

                        Divider()
                        // Privacidad
                        VStack(alignment: .leading, spacing: 6) {
                            FilterGroupLabel(text: "TIPO DE QUEDADA")
                            FlowLayoutView(spacing: 6) {
                                FilterPill(label: "Todas", selected: privacy == nil) { privacy = nil }
                                FilterPill(label: "Abiertas", selected: privacy == "OPEN") { privacy = "OPEN" }
                                FilterPill(label: "Seguidos/Seguidores", selected: privacy == "FOLLOWERS") { privacy = "FOLLOWERS" }
                                FilterPill(label: "No mixto", selected: privacy == "WOMEN") {
                                    if isWoman { privacy = "WOMEN" }
                                }
                                .opacity(isWoman ? 1 : 0.4)
                            }
                            if privacy == "WOMEN" && !isWoman {
                                Text("Necesitas indicar tu genero como Mujer en tu perfil para usar este filtro.")
                                    .font(.caption).foregroundColor(Cumbre.bad)
                            }
                        }

                        Divider()
                        // Distancia
                        VStack(alignment: .leading, spacing: 6) {
                            FilterGroupLabel(text: "DISTANCIA")
                            Text("Avisame solo de quedadas a menos de X km de mi ubicacion")
                                .font(.caption).foregroundColor(Cumbre.ink.opacity(0.6))
                            HStack(spacing: 6) {
                                FilterPill(label: "Sin limite", selected: maxDistanceKm == nil) { maxDistanceKm = nil }
                                FilterPill(label: "50 km", selected: maxDistanceKm == 50) { maxDistanceKm = 50 }
                                FilterPill(label: "100 km", selected: maxDistanceKm == 100) { maxDistanceKm = 100 }
                                FilterPill(label: "200 km", selected: maxDistanceKm == 200) { maxDistanceKm = 200 }
                            }
                        }

                        Divider()
                        // Días
                        VStack(alignment: .leading, spacing: 6) {
                            FilterGroupLabel(text: "DIAS")
                            Text("Avisame si la quedada incluye alguno de estos dias (vacio = cualquier dia)")
                                .font(.caption).foregroundColor(Cumbre.ink.opacity(0.6))
                            FlowLayoutView(spacing: 6) {
                                ForEach(next14) { d in
                                    FilterPill(label: d.label, selected: selectedDays.contains(d.iso)) {
                                        if selectedDays.contains(d.iso) { selectedDays.remove(d.iso) }
                                        else { selectedDays.insert(d.iso) }
                                    }
                                }
                            }
                        }

                        if let err = vm.alertError {
                            Text(err).font(.caption).foregroundColor(Cumbre.bad)
                        }
                    }
                    .opacity(enabled ? 1 : 0.45)
                    .disabled(!enabled)
                }
                .padding(16)
            }

            Divider()
            Button {
                let daysCsv = selectedDays.isEmpty ? nil : selectedDays.sorted().joined(separator: ",")
                Task {
                    await vm.saveAlert(enabled: enabled, daysCsv: daysCsv, schoolId: schoolId,
                                       discipline: discipline, privacy: privacy, maxDistanceKm: maxDistanceKm)
                    dismiss()
                }
            } label: {
                Text(enabled ? "GUARDAR ALERTA" : "DESACTIVAR ALERTA")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Cumbre.terraFill)
                    .cornerRadius(6)
            }
            .padding(16)
        }
        .navigationTitle("Alertas de quedadas")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundColor(Cumbre.terra)
            }
        }
        .onAppear {
            enabled = vm.alertEnabled
            selectedDays = Set(vm.alertDaysCsv?
                .split(separator: ",").map { String($0).trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty } ?? [])
            discipline = vm.alertDiscipline
            privacy = vm.alertPrivacy
            schoolId = vm.alertSchoolId
            schoolName = vm.alertSchoolName
            maxDistanceKm = vm.alertMaxDistanceKm
        }
        .sheet(isPresented: $showSchoolPicker) {
            MeetupSchoolFilterSheet { school in
                schoolId = school.id
                schoolName = school.name
                showSchoolPicker = false
            }
        }
    }
}

struct MeetupSchoolFilterSheet: View {
    let onSelect: (School) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var results: [School] = []
    private let searchSchools = AppDependencies.shared.container.searchSchools

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Buscador con icono + fondo claro (mas visible)
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(Cumbre.ink.opacity(0.5))
                    TextField("Ej. Zarzalejo, Pedriza…", text: $query)
                        .autocorrectionDisabled()
                    if !query.isEmpty {
                        Button { query = ""; results = [] } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(Cumbre.ink.opacity(0.3))
                        }
                    }
                }
                .padding(.horizontal, 12).padding(.vertical, 11)
                .background(Cumbre.ink.opacity(0.06))
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Cumbre.ink.opacity(0.15), lineWidth: 1))
                .padding(16)
                .onChange(of: query) { q in
                    let trimmed = q.trimmingCharacters(in: .whitespaces)
                    guard trimmed.count >= 2 else { results = []; return }
                    Task {
                        if let res = try? await searchSchools.invoke(query: trimmed, limit: 8) {
                            results = res
                        }
                    }
                }

                if results.isEmpty && query.count >= 2 {
                    Text("Sin resultados")
                        .font(.subheadline).foregroundColor(Cumbre.ink.opacity(0.5))
                        .padding(.top, 20)
                    Spacer()
                } else {
                    List(results, id: \.id) { school in
                        Button { onSelect(school) } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(school.name).foregroundColor(Cumbre.ink)
                                if let loc = school.location {
                                    Text(loc).font(.caption).foregroundColor(Cumbre.ink.opacity(0.6))
                                }
                            }
                        }
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("Buscar escuela")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundColor(Cumbre.terra)
                }
            }
        }
    }
}
