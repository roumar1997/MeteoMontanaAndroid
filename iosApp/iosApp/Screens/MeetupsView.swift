import SwiftUI
import Shared
import CoreLocation
import MapLibre

// ── ViewModel ──────────────────────────────────────────────────────────────

@MainActor
final class MeetupsViewModel: ObservableObject {
    @Published var meetups: [Meetup] = []
    @Published var loading = false
    @Published var error: String?
    @Published var alertEnabled: Bool = false
    @Published var alertDaysCsv: String?

    // Filters (persisted in UserDefaults)
    @Published var filterRelation: String?    // nil = todas, "following"
    @Published var filterPrivacy: String?     // nil | "FOLLOWERS" | "WOMEN"
    @Published var filterMaxDistanceKm: Double?
    @Published var filterDiscipline: String?  // nil | "BOULDER" | "ROUTE" | "BOTH"
    @Published var filterDays: Set<String> = []
    @Published var filterSchoolId: String?
    @Published var filterSchoolName: String?

    // User location
    @Published var userLat: Double?
    @Published var userLon: Double?

    // Day scores: key = "schoolId_isoDate" -> score
    @Published var dayScores: [String: Int] = [:]

    // Gender (for WOMEN gate)
    @Published var myGender: String?

    private let getMeetups = AppDependencies.shared.container.getMeetups
    private let getMeetupAlert = AppDependencies.shared.container.getMeetupAlert
    private let setMeetupAlert = AppDependencies.shared.container.setMeetupAlert
    private let getRangeScores = AppDependencies.shared.container.getRangeScores
    private let getProfile = AppDependencies.shared.container.getMyProfile

    private static let prefsKey = "meetups_filters"

    init() {
        restoreFilters()
        Task { await load() }
        Task { await loadAlert() }
        Task { await loadLocation() }
        Task { await loadGender() }
    }

    func load(relation: String? = nil) async {
        loading = true
        error = nil
        let rel = relation ?? filterRelation
        do {
            if let uc = getMeetups {
                let result = try await uc.execute(
                    schoolId: filterSchoolId,
                    date: nil,
                    relation: rel
                )
                meetups = result
            }
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }

    func setFilter(_ relation: String?) {
        filterRelation = relation
        persistFilters()
        Task { await load(relation: relation) }
    }

    func setFilterPrivacy(_ privacy: String?) {
        filterPrivacy = privacy
        persistFilters()
    }

    func setFilterMaxDistance(_ km: Double?) {
        filterMaxDistanceKm = km
        persistFilters()
    }

    func setFilterDiscipline(_ disc: String?) {
        filterDiscipline = disc
        persistFilters()
    }

    func toggleFilterDay(_ day: String) {
        if filterDays.contains(day) { filterDays.remove(day) }
        else { filterDays.insert(day) }
    }

    func clearFilterDays() { filterDays.removeAll() }

    func setFilterSchool(id: String?, name: String?) {
        filterSchoolId = id
        filterSchoolName = name
        persistFilters()
        Task { await load() }
    }

    @Published var alertSchoolId: String?
    @Published var alertSchoolName: String?
    @Published var alertDiscipline: String?
    @Published var alertPrivacy: String?
    @Published var alertMaxDistanceKm: Int32?
    @Published var alertError: String?

    func loadAlert() async {
        do {
            let state = try await getMeetupAlert.execute()
            alertEnabled = state.enabled
            alertDaysCsv = state.daysCsv
            alertSchoolId = state.schoolId
            alertSchoolName = state.schoolName
            alertDiscipline = state.discipline
            alertPrivacy = state.privacy
            alertMaxDistanceKm = state.maxDistanceKm?.int32Value
        } catch {}
    }

    /// Guarda la alerta con todos sus filtros (espejo del backend completo).
    func saveAlert(enabled: Bool, daysCsv: String?, schoolId: String?,
                   discipline: String?, privacy: String?, maxDistanceKm: Int32?) async {
        alertError = nil
        do {
            var lat: Double? = nil
            var lon: Double? = nil
            if maxDistanceKm != nil, let lp = AppDependencies.shared.container.locationProvider {
                let loc = try? await lp.current()
                lat = loc?.lat
                lon = loc?.lon
            }
            let state = try await setMeetupAlert.execute(
                enabled: enabled, daysCsv: daysCsv, schoolId: schoolId,
                discipline: discipline, privacy: privacy,
                maxDistanceKm: maxDistanceKm.map { KotlinInt(int: $0) },
                userLat: lat.map { KotlinDouble(double: $0) },
                userLon: lon.map { KotlinDouble(double: $0) }
            )
            alertEnabled = state.enabled
            alertDaysCsv = state.daysCsv
            alertSchoolId = state.schoolId
            alertSchoolName = state.schoolName
            alertDiscipline = state.discipline
            alertPrivacy = state.privacy
            alertMaxDistanceKm = state.maxDistanceKm?.int32Value
        } catch {
            if error.localizedDescription.contains("GENDER_REQUIRED") {
                alertError = "Para filtrar por No Mixto necesitas indicar tu género como Mujer en tu perfil."
            } else {
                alertError = error.localizedDescription
            }
        }
    }

    func loadLocation() async {
        do {
            guard let lp = AppDependencies.shared.container.locationProvider else { return }
            let loc = try await lp.current()
            userLat = loc?.lat
            userLon = loc?.lon
        } catch {}
    }

    func loadGender() async {
        do {
            let p = try await getProfile.invoke()
            myGender = p.gender
        } catch {}
    }

    func loadAllDayScores(schoolIds: [String], days: [String]) {
        guard !schoolIds.isEmpty, !days.isEmpty else { dayScores = [:]; return }
        Task {
            do {
                let results = try await getRangeScores.invoke(ids: schoolIds, dates: days)
                var map: [String: Int] = [:]
                for school in results {
                    for day in school.days {
                        map["\(school.id)_\(day.date)"] = Int(day.score)
                    }
                }
                dayScores = map
            } catch {}
        }
    }

    // ── Persistence ──

    private func persistFilters() {
        var d: [String: Any] = [:]
        if let r = filterRelation { d["relation"] = r }
        if let p = filterPrivacy { d["privacy"] = p }
        if let km = filterMaxDistanceKm { d["maxDistanceKm"] = km }
        if let disc = filterDiscipline { d["discipline"] = disc }
        if let sid = filterSchoolId { d["schoolId"] = sid }
        if let sn = filterSchoolName { d["schoolName"] = sn }
        UserDefaults.standard.set(d, forKey: Self.prefsKey)
    }

    private func restoreFilters() {
        guard let d = UserDefaults.standard.dictionary(forKey: Self.prefsKey) else { return }
        filterRelation = d["relation"] as? String
        filterPrivacy = d["privacy"] as? String
        filterMaxDistanceKm = d["maxDistanceKm"] as? Double
        filterDiscipline = d["discipline"] as? String
        filterSchoolId = d["schoolId"] as? String
        filterSchoolName = d["schoolName"] as? String
    }
}

// ── List view ──────────────────────────────────────────────────────────────

struct MeetupsView: View {
    @StateObject private var vm = MeetupsViewModel()
    @State private var filtersExpanded = false
    @State private var showWomenGateDialog = false
    @State private var activeSheet: MeetupSheet?

    // Filters applied locally
    private var displayedMeetups: [Meetup] {
        var list = vm.meetups
        if let priv = vm.filterPrivacy { list = list.filter { $0.privacy == priv } }
        if let maxKm = vm.filterMaxDistanceKm, let lat = vm.userLat, let lon = vm.userLon {
            list = list.filter { m in
                guard let sLat = m.schoolLat?.doubleValue, let sLon = m.schoolLon?.doubleValue else { return true }
                return Geo.shared.haversineKm(lat1: lat, lon1: lon, lat2: sLat, lon2: sLon) <= maxKm
            }
        }
        if !vm.filterDays.isEmpty {
            list = list.filter { meetup in meetup.days.contains(where: { vm.filterDays.contains($0) }) }
        }
        if let disc = vm.filterDiscipline {
            list = list.filter { $0.discipline == disc || $0.discipline == "BOTH" || $0.discipline == nil }
        }
        return list
    }

    private func distanceFor(_ meetup: Meetup) -> Double? {
        guard let lat = vm.userLat, let lon = vm.userLon,
              let sLat = meetup.schoolLat?.doubleValue,
              let sLon = meetup.schoolLon?.doubleValue else { return nil }
        return Geo.shared.haversineKm(lat1: lat, lon1: lon, lat2: sLat, lon2: sLon)
    }

    private var activeFilterCount: Int {
        var c = 0
        if vm.filterRelation != nil { c += 1 }
        if vm.filterPrivacy != nil { c += 1 }
        if vm.filterMaxDistanceKm != nil { c += 1 }
        if vm.filterDiscipline != nil { c += 1 }
        if vm.filterSchoolName != nil { c += 1 }
        c += vm.filterDays.count
        return c
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // ── Header ──
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("QUEDADAS")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .tracking(1.8)
                            .foregroundColor(Cumbre.ink.opacity(0.6))
                        Text(displayedMeetups.isEmpty ? "Quedar a escalar" : "\(displayedMeetups.count) activas")
                            .font(.headline)
                    }
                    Spacer()
                    HelpButton(topicKey: "meetups")
                    Button { Task { await vm.load() } } label: {
                        Image(systemName: "arrow.clockwise").foregroundColor(Cumbre.ink)
                    }
                    Button { activeSheet = .alert } label: {
                        Image(systemName: vm.alertEnabled ? "bell.fill" : "bell")
                            .foregroundColor(vm.alertEnabled ? Cumbre.terra : Cumbre.ink.opacity(0.5))
                    }
                    Button { activeSheet = .create } label: {
                        Image(systemName: "plus").foregroundColor(Cumbre.terra)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Cumbre.bg)
                .overlay(Divider(), alignment: .bottom)

                // ── TODO en un ScrollView: coach marks + mapa + filtros + lista
                // (asi el mapa scrollea con el resto, como en la pantalla de escuelas).
                ScrollView {
                    LazyVStack(spacing: 0, pinnedViews: []) {

                // ── Coach marks ──
                FirstTimeHint(
                    hintKey: "meetups_intro",
                    text: "Crea quedadas, filtra por dia o distancia, y toca una quedada para ver su detalle o entrar al chat si ya estas unido."
                )
                FirstTimeHint(
                    hintKey: "meetups_alert_v2",
                    text: "🔔 Toca la campana de arriba para crear ALERTAS: te avisamos cuando alguien cree una quedada en los dias, escuela o distancia que te interesan."
                )

                // ── Mapa desplegable (dentro del scroll) ──
                MeetupsMapPanel(
                    meetups: displayedMeetups,
                    userLat: vm.userLat,
                    userLon: vm.userLon,
                    maxDistanceKm: vm.filterMaxDistanceKm,
                    onSchoolSelected: { schoolId in
                        let name = displayedMeetups.first(where: { $0.schoolId == schoolId })?.schoolName
                        vm.setFilterSchool(id: schoolId, name: name)
                    }
                )

                // ── Filters accordion ──
                VStack(spacing: 0) {
                    // Toggle bar
                    HStack(spacing: 6) {
                        Image(systemName: "line.3.horizontal.decrease")
                            .font(.system(size: 13)).foregroundColor(Cumbre.terra)
                        Text(NSLocalizedString("common_filters", comment: ""))
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .tracking(1.5)
                            .foregroundColor(Cumbre.terra)
                        if activeFilterCount > 0 {
                            Text("\(activeFilterCount)")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 6).padding(.vertical, 1)
                                .background(Cumbre.terra)
                                .clipShape(Capsule())
                        }
                        Spacer()
                        Image(systemName: filtersExpanded ? "chevron.up" : "chevron.down")
                            .font(.system(size: 12, weight: .semibold)).foregroundColor(Cumbre.terra)
                    }
                    .padding(.horizontal, 16).padding(.vertical, 11)
                    .background(Cumbre.ink.opacity(0.05))
                    .overlay(Divider(), alignment: .bottom)
                    .contentShape(Rectangle())
                    .onTapGesture { withAnimation(.easeInOut(duration: 0.2)) { filtersExpanded.toggle() } }

                    if filtersExpanded {
                        VStack(alignment: .leading, spacing: 10) {
                            // TIPO DE GRUPO
                            FilterGroupLabel(text: "TIPO DE GRUPO")
                            FlowLayoutView {
                                FilterPill(label: "Todos", selected: vm.filterRelation == nil && vm.filterPrivacy == nil) {
                                    vm.setFilter(nil); vm.setFilterPrivacy(nil)
                                }
                                FilterPill(label: "Siguiendo", selected: vm.filterRelation == "following") {
                                    vm.setFilter(vm.filterRelation == "following" ? nil : "following")
                                }
                                FilterPill(label: "Seguidos/Seguidores", selected: vm.filterPrivacy == "FOLLOWERS") {
                                    vm.setFilterPrivacy(vm.filterPrivacy == "FOLLOWERS" ? nil : "FOLLOWERS")
                                }
                                FilterPill(label: "No mixto", selected: vm.filterPrivacy == "WOMEN") {
                                    if vm.filterPrivacy == "WOMEN" {
                                        vm.setFilterPrivacy(nil)
                                    } else if vm.myGender == "WOMAN" {
                                        vm.setFilterPrivacy("WOMEN")
                                    } else {
                                        showWomenGateDialog = true
                                    }
                                }
                            }

                            // DISTANCIA
                            FilterGroupLabel(text: "DISTANCIA")
                            FlowLayoutView {
                                FilterPill(label: "Cualquiera", selected: vm.filterMaxDistanceKm == nil) { vm.setFilterMaxDistance(nil) }
                                FilterPill(label: "< 25 km", selected: vm.filterMaxDistanceKm == 25) { vm.setFilterMaxDistance(25) }
                                FilterPill(label: "< 50 km", selected: vm.filterMaxDistanceKm == 50) { vm.setFilterMaxDistance(50) }
                                FilterPill(label: "< 100 km", selected: vm.filterMaxDistanceKm == 100) { vm.setFilterMaxDistance(100) }
                                FilterPill(label: "< 200 km", selected: vm.filterMaxDistanceKm == 200) { vm.setFilterMaxDistance(200) }
                                FilterPill(label: "< 500 km", selected: vm.filterMaxDistanceKm == 500) { vm.setFilterMaxDistance(500) }
                            }

                            // DISCIPLINA
                            FilterGroupLabel(text: "DISCIPLINA")
                            FlowLayoutView {
                                FilterPill(label: "Cualquiera", selected: vm.filterDiscipline == nil) { vm.setFilterDiscipline(nil) }
                                FilterPill(label: "Bloque", selected: vm.filterDiscipline == "BOULDER") { vm.setFilterDiscipline("BOULDER") }
                                FilterPill(label: "Via", selected: vm.filterDiscipline == "ROUTE") { vm.setFilterDiscipline("ROUTE") }
                                FilterPill(label: "Ambas", selected: vm.filterDiscipline == "BOTH") { vm.setFilterDiscipline("BOTH") }
                            }

                            // DIAS
                            FilterGroupLabel(text: "DIAS")
                            let nextDays = generateNextDays(14)
                            FlowLayoutView {
                                FilterPill(label: "Cualquier dia", selected: vm.filterDays.isEmpty) {
                                    vm.clearFilterDays()
                                }
                                ForEach(nextDays, id: \.iso) { d in
                                    FilterPill(label: d.label, selected: vm.filterDays.contains(d.iso)) {
                                        vm.toggleFilterDay(d.iso)
                                    }
                                }
                            }

                            // ESCUELA
                            FilterGroupLabel(text: "ESCUELA")
                            HStack(spacing: 8) {
                                Text(vm.filterSchoolName ?? "Buscar escuela...")
                                    .font(.system(size: 13))
                                    .foregroundColor(vm.filterSchoolName != nil ? Cumbre.ink : Cumbre.ink.opacity(0.5))
                                    .onTapGesture { activeSheet = .schoolFilter }
                                if vm.filterSchoolName != nil {
                                    Button {
                                        vm.setFilterSchool(id: nil, name: nil)
                                    } label: {
                                        Image(systemName: "xmark.circle.fill")
                                            .font(.caption).foregroundColor(Cumbre.ink.opacity(0.4))
                                    }
                                }
                            }
                        }
                        .padding(.horizontal, 16).padding(.bottom, 10)
                    }
                    Divider()
                }

                // ── Content ──
                if vm.loading && vm.meetups.isEmpty {
                    ProgressView().frame(maxWidth: .infinity).padding(.vertical, 60)
                } else if vm.error != nil && vm.meetups.isEmpty {
                    VStack(spacing: 12) {
                        Text("No se pudo cargar").foregroundColor(Cumbre.ink.opacity(0.6))
                        Button(NSLocalizedString("common_retry", comment: "")) { Task { await vm.load() } }
                            .foregroundColor(Cumbre.terra)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 60)
                } else if displayedMeetups.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "person.3").font(.system(size: 40))
                            .foregroundColor(Cumbre.ink.opacity(0.4))
                        Text(NSLocalizedString("meetups_empty", comment: "")).foregroundColor(Cumbre.ink.opacity(0.6))
                        Text("Crea una para quedar a escalar")
                            .font(.footnote).foregroundColor(Cumbre.ink.opacity(0.5))
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 60)
                } else {
                    ForEach(displayedMeetups, id: \.id) { meetup in
                        Button {
                            if meetup.joined {
                                activeSheet = .chat(meetup.conversationId, meetup.name)
                            } else {
                                activeSheet = .detail(meetup.id)
                            }
                        } label: {
                            MeetupRowView(meetup: meetup, dayScoresMap: vm.dayScores, distanceKm: distanceFor(meetup))
                        }
                        .buttonStyle(.plain)
                    }
                }

                    } // LazyVStack
                } // ScrollView
            }
            .navigationBarHidden(true)
            .sheet(item: $activeSheet) { sheet in
                switch sheet {
                case .create:
                    NavigationStack {
                        CreateMeetupView { _ in
                            activeSheet = nil
                            Task { await vm.load() }
                        }
                    }
                case .detail(let id):
                    NavigationStack { MeetupDetailView(meetupId: id) }
                case .chat(let convId, let name):
                    NavigationStack { GroupChatView(convId: convId, groupName: name) }
                case .alert:
                    NavigationStack { MeetupAlertView(vm: vm) }
                case .schoolFilter:
                    MeetupSchoolFilterSheet { school in
                        vm.setFilterSchool(id: school.id, name: school.name)
                        activeSheet = nil
                    }
                }
            }
            .onChange(of: activeSheet) { s in
                if s == nil { Task { await vm.load() } }
            }
            .alert("Quedadas No Mixto", isPresented: $showWomenGateDialog) {
                Button("ENTENDIDO", role: .cancel) {}
            } message: {
                Text("Para ver y participar en quedadas No Mixto necesitas indicar tu genero como Mujer en tu perfil.\n\nVe a Perfil -> Editar perfil -> Genero.")
            }
            .onChange(of: displayedMeetups.map { $0.id }.joined()) { _ in
                let schoolIds = displayedMeetups.map { $0.schoolId }
                let allDays = displayedMeetups.flatMap { $0.days }
                if !schoolIds.isEmpty, !allDays.isEmpty {
                    vm.loadAllDayScores(schoolIds: Array(Set(schoolIds)), days: Array(Set(allDays)))
                }
            }
            .task {
                // Load scores on first display
                let schoolIds = displayedMeetups.map { $0.schoolId }
                let allDays = displayedMeetups.flatMap { $0.days }
                if !schoolIds.isEmpty, !allDays.isEmpty {
                    vm.loadAllDayScores(schoolIds: Array(Set(schoolIds)), days: Array(Set(allDays)))
                }
            }
        }
    }
}
