import SwiftUI
import Shared

// ── ViewModel ──────────────────────────────────────────────────────────────

@MainActor
final class MeetupsViewModel: ObservableObject {
    @Published var meetups: [Meetup] = []
    @Published var loading = false
    @Published var error: String?
    @Published var alertEnabled: Bool = false

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

    func loadAlert() async {
        do {
            let state = try await getMeetupAlert.execute()
            alertEnabled = state.enabled
        } catch {}
    }

    func toggleAlert() {
        let newEnabled = !alertEnabled
        alertEnabled = newEnabled
        Task {
            do {
                let state = try await setMeetupAlert.execute(enabled: newEnabled, daysCsv: nil)
                alertEnabled = state.enabled
            } catch {
                alertEnabled = !newEnabled
            }
        }
    }

    func loadLocation() async {
        do {
            let loc = try await AppDependencies.shared.container.locationProvider.current()
            userLat = loc.lat
            userLon = loc.lon
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
        UserDefaults.standard.set(d, forKey: Self.prefsKey)
    }

    private func restoreFilters() {
        guard let d = UserDefaults.standard.dictionary(forKey: Self.prefsKey) else { return }
        filterRelation = d["relation"] as? String
        filterPrivacy = d["privacy"] as? String
        filterMaxDistanceKm = d["maxDistanceKm"] as? Double
        filterDiscipline = d["discipline"] as? String
    }
}

// ── List view ──────────────────────────────────────────────────────────────

struct MeetupsView: View {
    @StateObject private var vm = MeetupsViewModel()
    @State private var showCreate = false
    @State private var filtersExpanded = false
    @State private var showSchoolFilter = false
    @State private var showWomenGateDialog = false

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
                    Button { vm.toggleAlert() } label: {
                        Image(systemName: vm.alertEnabled ? "bell.fill" : "bell.slash")
                            .foregroundColor(vm.alertEnabled ? Cumbre.terra : Cumbre.ink.opacity(0.5))
                    }
                    Button { showCreate = true } label: {
                        Image(systemName: "plus").foregroundColor(Cumbre.terra)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Cumbre.bg)
                .overlay(Divider(), alignment: .bottom)

                // ── Coach mark ──
                FirstTimeHint(
                    hintKey: "meetups_intro",
                    text: "Crea quedadas, filtra por dia o distancia, y toca una quedada para ver su detalle o entrar al chat si ya estas unido."
                )

                // ── Mapa desplegable ──
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
                        Text("FILTROS")
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .tracking(1.5)
                            .foregroundColor(Cumbre.ink.opacity(0.6))
                        if activeFilterCount > 0 {
                            Text("\(activeFilterCount)")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 5).padding(.vertical, 1)
                                .background(Cumbre.terra)
                                .clipShape(Capsule())
                        }
                        Spacer()
                        Image(systemName: filtersExpanded ? "chevron.up" : "chevron.down")
                            .font(.caption).foregroundColor(Cumbre.ink.opacity(0.5))
                    }
                    .padding(.horizontal, 16).padding(.vertical, 8)
                    .contentShape(Rectangle())
                    .onTapGesture { withAnimation(.easeInOut(duration: 0.2)) { filtersExpanded.toggle() } }

                    if filtersExpanded {
                        VStack(alignment: .leading, spacing: 10) {
                            // TIPO DE GRUPO
                            FilterGroupLabel(text: "TIPO DE GRUPO")
                            FlowLayoutView {
                                FilterPill(label: "Todas", selected: vm.filterRelation == nil && vm.filterPrivacy == nil) {
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
                            let nextDays = generateNextDays(10)
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
                                    .onTapGesture { showSchoolFilter = true }
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
                Group {
                    if vm.loading && vm.meetups.isEmpty {
                        ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if let err = vm.error, vm.meetups.isEmpty {
                        VStack(spacing: 12) {
                            Text("No se pudo cargar").foregroundColor(Cumbre.ink.opacity(0.6))
                            Button("REINTENTAR") { Task { await vm.load() } }
                                .foregroundColor(Cumbre.terra)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if displayedMeetups.isEmpty {
                        VStack(spacing: 12) {
                            Image(systemName: "person.3").font(.system(size: 40))
                                .foregroundColor(Cumbre.ink.opacity(0.4))
                            Text("Sin quedadas activas").foregroundColor(Cumbre.ink.opacity(0.6))
                            Text("Crea una para quedar a escalar")
                                .font(.footnote).foregroundColor(Cumbre.ink.opacity(0.5))
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else {
                        List {
                            ForEach(displayedMeetups, id: \.id) { meetup in
                                NavigationLink(destination: Group {
                                    if meetup.joined {
                                        GroupChatView(convId: meetup.conversationId, groupName: meetup.name)
                                    } else {
                                        MeetupDetailView(meetupId: meetup.id)
                                    }
                                }) {
                                    MeetupRowView(meetup: meetup, dayScoresMap: vm.dayScores, distanceKm: distanceFor(meetup))
                                }
                                .listRowInsets(EdgeInsets())
                                .listRowSeparator(.hidden)
                            }
                        }
                        .listStyle(.plain)
                    }
                }
            }
            .navigationBarHidden(true)
            .sheet(isPresented: $showCreate) {
                CreateMeetupView { _ in
                    showCreate = false
                    Task { await vm.load() }
                }
            }
            .alert("Quedadas No Mixto", isPresented: $showWomenGateDialog) {
                Button("ENTENDIDO", role: .cancel) {}
            } message: {
                Text("Para ver y participar en quedadas No Mixto necesitas indicar tu genero como Mujer en tu perfil.\n\nVe a Perfil -> Editar perfil -> Genero.")
            }
            .sheet(isPresented: $showSchoolFilter) {
                MeetupSchoolFilterSheet { school in
                    vm.setFilterSchool(id: school.id, name: school.name)
                    showSchoolFilter = false
                }
            }
            .onChange(of: displayedMeetups.count) { _ in
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

// ── Row ─────────────────────────────────────────────────────────────────────

struct MeetupRowView: View {
    let meetup: Meetup
    var dayScoresMap: [String: Int] = [:]
    var distanceKm: Double?

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            // Photo/avatar
            let photo = meetup.photoUrl ?? meetup.creatorPhotoUrl
            ZStack {
                RoundedRectangle(cornerRadius: 2).fill(Cumbre.ink.opacity(0.06))
                if let url = photo.flatMap({ URL(string: $0) }) {
                    AsyncImage(url: url) { img in img.resizable().scaledToFill() }
                        placeholder: { Color.clear }
                        .clipped()
                } else {
                    Image(systemName: "person.3").foregroundColor(Cumbre.ink.opacity(0.4))
                }
            }
            .frame(width: 50, height: 50)
            .clipShape(RoundedRectangle(cornerRadius: 2))

            VStack(alignment: .leading, spacing: 3) {
                // Eyebrow: school + distance
                let eyebrow: String = {
                    var parts: [String] = []
                    if let s = meetup.schoolName { parts.append(s.uppercased()) }
                    if let km = distanceKm { parts.append("\(Int(km)) KM") }
                    return parts.joined(separator: " \u{00B7} ")
                }()
                if !eyebrow.isEmpty {
                    Text(eyebrow)
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .tracking(1.5)
                        .foregroundColor(Cumbre.ink.opacity(0.5))
                }
                // Name in serif
                Text(meetup.name)
                    .font(Cumbre.serif(16, .medium))

                // Days with individual scores
                FlowLayoutView {
                    ForEach(meetup.days, id: \.self) { day in
                        let score = dayScoresMap["\(meetup.schoolId)_\(day)"]
                        HStack(spacing: 4) {
                            Text(formatDayMonth(day))
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(Cumbre.ink.opacity(0.7))
                            if let s = score {
                                Text("\(s)")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 4).padding(.vertical, 1)
                                    .background(scoreColor(s))
                                    .clipShape(RoundedRectangle(cornerRadius: 2))
                            }
                        }
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(Cumbre.ink.opacity(0.05))
                        .clipShape(RoundedRectangle(cornerRadius: 2))
                    }
                }

                // Meta: discipline + privacy
                HStack(spacing: 4) {
                    if let disc = meetup.discipline {
                        Text(disciplineLabel(disc)).font(.caption).foregroundColor(Cumbre.ink.opacity(0.6))
                    }
                    if meetup.privacy != "OPEN" {
                        HStack(spacing: 2) {
                            Image(systemName: "lock").font(.system(size: 9)).foregroundColor(Cumbre.ink.opacity(0.5))
                            Text(privacyLabel(meetup.privacy)).font(.caption).foregroundColor(Cumbre.ink.opacity(0.6))
                        }
                    }
                }
            }

            Spacer()

            // Right: badge + members
            VStack(alignment: .trailing, spacing: 4) {
                if meetup.joined {
                    Text("UNIDO")
                        .font(.system(size: 10, weight: .bold))
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(Cumbre.terra)
                        .foregroundColor(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 2))
                } else if meetup.isFull {
                    Text("LLENO")
                        .font(.system(size: 10, weight: .bold))
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.ink.opacity(0.2), lineWidth: 1))
                        .foregroundColor(Cumbre.ink.opacity(0.5))
                }
                HStack(spacing: 2) {
                    Image(systemName: "person").font(.system(size: 10)).foregroundColor(Cumbre.ink.opacity(0.5))
                    let limitText: String = {
                        if let lim = meetup.memberLimit { return "\(meetup.memberCount)/\(lim.int32Value)" }
                        return "\(meetup.memberCount)"
                    }()
                    Text(limitText).font(.caption).foregroundColor(Cumbre.ink.opacity(0.6))
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Cumbre.bg)
        .overlay(Divider(), alignment: .bottom)
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

private struct FilterPill: View {
    let label: String; let selected: Bool; let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 11, weight: .bold))
                .padding(.horizontal, 10).padding(.vertical, 4)
                .background(selected ? Cumbre.terra.opacity(0.15) : Cumbre.bg)
                .foregroundColor(selected ? Cumbre.terra : Cumbre.ink.opacity(0.6))
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(selected ? Cumbre.terra : Cumbre.ink.opacity(0.2), lineWidth: 1))
                .cornerRadius(4)
        }
    }
}

private struct FilterGroupLabel: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.system(size: 10, weight: .bold, design: .monospaced))
            .tracking(1.5)
            .foregroundColor(Cumbre.ink.opacity(0.5))
    }
}

/// Wrapping flow layout (like Android FlowRow). Uses SwiftUI Layout protocol (iOS 16+).
struct FlowLayoutView: Layout {
    var spacing: CGFloat = 6

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxW = proposal.width ?? .infinity
        var x: CGFloat = 0; var y: CGFloat = 0; var rowH: CGFloat = 0
        for sv in subviews {
            let s = sv.sizeThatFits(.unspecified)
            if x + s.width > maxW && x > 0 { x = 0; y += rowH + spacing; rowH = 0 }
            x += s.width + spacing; rowH = max(rowH, s.height)
        }
        return CGSize(width: maxW, height: y + rowH)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX; var y = bounds.minY; var rowH: CGFloat = 0
        for sv in subviews {
            let s = sv.sizeThatFits(.unspecified)
            if x + s.width > bounds.maxX && x > bounds.minX { x = bounds.minX; y += rowH + spacing; rowH = 0 }
            sv.place(at: CGPoint(x: x, y: y), proposal: .init(s))
            x += s.width + spacing; rowH = max(rowH, s.height)
        }
    }
}

func privacyLabel(_ privacy: String) -> String {
    switch privacy {
    case "FOLLOWERS": return "Seguidos"
    case "WOMEN":     return "No mixto"
    default:          return "Abierta"
    }
}

func disciplineLabel(_ discipline: String) -> String {
    switch discipline {
    case "BOULDER": return "Bloque"
    case "ROUTE":   return "Via"
    case "BOTH":    return "Bloque + Via"
    default:        return discipline
    }
}

private func formatDayMonth(_ iso: String) -> String {
    let months = ["ene","feb","mar","abr","may","jun","jul","ago","sep","oct","nov","dic"]
    let parts = iso.split(separator: "-")
    guard parts.count == 3, let mo = Int(parts[1]), let d = Int(parts[2]) else { return iso }
    return "\(d) \(months[mo - 1])"
}

private func scoreColor(_ score: Int) -> Color {
    if score >= 80 { return Color(red: 0.13, green: 0.77, blue: 0.37) }
    if score >= 60 { return Color(red: 0.96, green: 0.62, blue: 0.04) }
    if score >= 40 { return Color(red: 0.93, green: 0.27, blue: 0.27) }
    return Color(red: 0.42, green: 0.44, blue: 0.50)
}

private struct DayInfo: Identifiable {
    let iso: String
    let label: String
    var id: String { iso }
}

private func generateNextDays(_ count: Int) -> [DayInfo] {
    let cal = Calendar.current
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "es_ES")
    let isoFormatter = DateFormatter()
    isoFormatter.dateFormat = "yyyy-MM-dd"
    let weekdays = ["dom","lun","mar","mie","jue","vie","sab"]
    let months = ["ene","feb","mar","abr","may","jun","jul","ago","sep","oct","nov","dic"]

    var result: [DayInfo] = []
    let today = Date()
    for i in 0..<count {
        guard let date = cal.date(byAdding: .day, value: i, to: today) else { continue }
        let comps = cal.dateComponents([.weekday, .day, .month], from: date)
        let wd = weekdays[(comps.weekday ?? 1) - 1]
        let d = comps.day ?? 1
        let mo = months[(comps.month ?? 1) - 1]
        let iso = isoFormatter.string(from: date)
        result.append(DayInfo(iso: iso, label: "\(wd) \(d) \(mo)"))
    }
    return result
}

// ── School filter sheet ────────────────────────────────────────────────────

// ── Mapa de quedadas ──────────────────────────────────────────────────────

private struct SchoolMeetupGroup: Identifiable {
    let schoolId: String
    let schoolName: String
    let lat: Double
    let lon: Double
    let count: Int
    var id: String { schoolId }
}

struct MeetupsMapPanel: View {
    let meetups: [Meetup]
    let userLat: Double?
    let userLon: Double?
    let maxDistanceKm: Double?
    let onSchoolSelected: (String) -> Void

    @State private var show = false
    @State private var mapStyle: MapStyleKind = .topo
    @State private var popup: SchoolMeetupGroup?

    private var groups: [SchoolMeetupGroup] {
        let grouped = Dictionary(grouping: meetups.filter {
            $0.schoolLat?.doubleValue != nil && $0.schoolLon?.doubleValue != nil
        }, by: { $0.schoolId })
        return grouped.map { (id, list) in
            SchoolMeetupGroup(
                schoolId: id,
                schoolName: list.first?.schoolName ?? id,
                lat: list.first!.schoolLat!.doubleValue,
                lon: list.first!.schoolLon!.doubleValue,
                count: list.count
            )
        }
    }

    private var markers: [CumbreMarker] {
        var ms: [CumbreMarker] = []
        if let lat = userLat, let lon = userLon {
            ms.append(CumbreMarker(
                id: "user", coordinate: .init(latitude: lat, longitude: lon),
                title: "Tu ubicacion", kind: .user
            ))
        }
        for g in groups {
            ms.append(CumbreMarker(
                id: g.schoolId, coordinate: .init(latitude: g.lat, longitude: g.lon),
                title: "\(g.schoolName) · \(g.count)",
                kind: .score, color: UIColor(red: 0.78, green: 0.40, blue: 0.13, alpha: 1),
                score: nil, name: "\(g.schoolName) · \(g.count)", showName: true
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
                .padding(.horizontal, 16).padding(.vertical, 10)
                .background(Cumbre.ink.opacity(0.04))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .overlay(Divider(), alignment: .bottom)

            if show && !groups.isEmpty {
                ZStack(alignment: .topTrailing) {
                    MapLibreView(
                        center: center,
                        zoom: maxDistanceKm != nil ? zoomForKm(maxDistanceKm!) : (userLat != nil ? 8 : 6),
                        markers: markers, style: mapStyle,
                        autoFitToMarkers: maxDistanceKm == nil,
                        onTapMarker: { id in
                            popup = groups.first(where: { $0.schoolId == id })
                        }
                    )
                    .frame(height: 240)
                    MapStyleChips(selection: $mapStyle)
                }
                .overlay(Divider(), alignment: .bottom)

                if let g = popup {
                    Button {
                        onSchoolSelected(g.schoolId)
                        popup = nil
                        withAnimation { show = false }
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(g.schoolName).font(.body).fontWeight(.medium)
                                    .foregroundColor(Cumbre.ink)
                                Text("\(g.count) quedada\(g.count == 1 ? "" : "s") activa\(g.count == 1 ? "" : "s")")
                                    .font(.caption).foregroundColor(Cumbre.ink.opacity(0.6))
                            }
                            Spacer()
                            Text("VER ▸").font(.system(size: 11, weight: .bold))
                                .foregroundColor(Cumbre.terra)
                        }
                        .padding(12)
                        .background(Cumbre.bg)
                        .overlay(RoundedRectangle(cornerRadius: 4).stroke(Cumbre.ink.opacity(0.2), lineWidth: 1))
                        .padding(.horizontal, 8).padding(.vertical, 4)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
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

private struct MeetupSchoolFilterSheet: View {
    let onSelect: (School) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var results: [School] = []
    private let searchSchools = AppDependencies.shared.container.searchSchools

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                TextField("Buscar escuela...", text: $query)
                    .padding(10)
                    .overlay(Rectangle().stroke(Cumbre.ink.opacity(0.2), lineWidth: 1))
                    .padding(16)
                    .onChange(of: query) { q in
                        let trimmed = q.trimmingCharacters(in: .whitespaces)
                        guard trimmed.count >= 2 else { results = []; return }
                        Task {
                            if let res = try? await searchSchools.invoke(query: trimmed) {
                                results = res
                            }
                        }
                    }
                List(results, id: \.id) { school in
                    Button { onSelect(school) } label: {
                        Text(school.name).foregroundColor(Cumbre.ink)
                    }
                }
                .listStyle(.plain)
            }
            .navigationTitle("Escuela")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cerrar") { dismiss() }
                }
            }
        }
    }
}
