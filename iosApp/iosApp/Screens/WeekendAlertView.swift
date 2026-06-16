import SwiftUI
import Shared

// Alerta de tiempo — réplica de WeekendAlertScreen.kt. Configura una alerta que
// compara hasta 3 escuelas (o las mejores en un radio) para los días elegidos.
// Persiste en /api/me/weekend-alert. (El push lo manda el backend a tus tokens
// FCM; en iOS el envío llegará cuando exista el bridge de FCM.)

private let DAY_LABELS = ["L", "M", "X", "J", "V", "S", "D"]   // ISO 1..7
private let HOUR_OPTIONS = [7, 8, 9, 10, 20, 21]
private let RADIUS_OPTIONS = [25, 50, 100, 150, 200]

@MainActor
final class WeekendAlertViewModel: ObservableObject {
    @Published var loading = true
    @Published var enabled = false
    @Published var alertDays: Set<Int> = [5, 6, 7]
    @Published var notifyDay = 4
    @Published var notifyHour = 20
    @Published var nearby = false
    @Published var radiusKm = 50
    @Published var selected: [School] = []
    @Published var query = ""
    @Published var suggestions: [School] = []
    @Published var optimalEnabled = false
    @Published var optimalThreshold = 70
    @Published var saving = false
    @Published var savedOk = false
    @Published var error: String?

    private let container = AppDependencies.shared.container
    private let bridge = AppDependencies.shared.locationBridge
    private var catalog: [School] = []

    func load() async {
        loading = true
        catalog = (try? await container.getSchools.invoke(
            region: nil, style: nil, rockType: nil, lat: nil, lon: nil, radioKm: nil)) ?? []
        if let dto = try? await container.getWeekendAlert.invoke() {
            enabled = dto.enabled
            notifyDay = Int(dto.notifyDay)
            notifyHour = Int(dto.notifyHour)
            let days = dto.alertDays.map { Int($0.intValue) }
            alertDays = Set(days.isEmpty ? [5, 6, 7] : days)
            nearby = dto.mode.caseInsensitiveCompare("NEARBY") == .orderedSame
            radiusKm = dto.radiusKm.map { Int($0.intValue) } ?? 50
            let byId = Dictionary(catalog.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
            selected = dto.schoolIds.compactMap { byId[$0] }
            optimalEnabled = dto.optimalEnabled?.boolValue ?? false
            optimalThreshold = dto.optimalThreshold.map { Int($0.intValue) } ?? 70
        }
        loading = false
    }

    func search(_ q: String) {
        query = q
        let needle = q.trimmingCharacters(in: .whitespaces).lowercased()
        guard needle.count >= 2 else { suggestions = []; return }
        suggestions = catalog
            .filter { $0.name.lowercased().contains(needle) }
            .filter { c in !selected.contains { $0.id == c.id } }
            .prefix(5).map { $0 }
    }

    func add(_ s: School) {
        guard selected.count < 3 else { return }
        selected.append(s); query = ""; suggestions = []; savedOk = false
    }
    func remove(_ id: String) { selected.removeAll { $0.id == id }; savedOk = false }
    func toggleDay(_ d: Int) {
        if alertDays.contains(d) { alertDays.remove(d) } else { alertDays.insert(d) }
        savedOk = false; error = nil
    }

    func save() async {
        if enabled && !nearby && selected.isEmpty { error = "Elige al menos una escuela"; return }
        if enabled && alertDays.isEmpty { error = "Elige al menos un día a comparar"; return }
        saving = true; error = nil
        defer { saving = false }

        var lat: Double?; var lon: Double?
        if nearby {
            if let loc = try? await container.locationProvider?.current() { lat = loc.lat; lon = loc.lon }
            if lat == nil {
                error = "No pudimos obtener tu ubicación — concede el permiso e inténtalo de nuevo"
                return
            }
        }
        let dto = WeekendAlertDto(
            enabled: enabled,
            notifyDay: Int32(notifyDay),
            notifyHour: Int32(notifyHour),
            schoolIds: nearby ? [] : selected.map { $0.id },
            mode: nearby ? "NEARBY" : "SCHOOLS",
            radiusKm: nearby ? KotlinInt(int: Int32(radiusKm)) : nil,
            lat: lat.map { KotlinDouble(double: $0) },
            lon: lon.map { KotlinDouble(double: $0) },
            alertDays: alertDays.sorted().map { KotlinInt(int: Int32($0)) },
            optimalEnabled: KotlinBoolean(bool: optimalEnabled),
            optimalThreshold: KotlinInt(int: Int32(optimalThreshold))
        )
        if (try? await container.updateWeekendAlert.invoke(req: dto)) != nil {
            savedOk = true
        } else {
            error = "No se pudo guardar. Revisa la conexión."
        }
    }

    func requestLocation() { bridge.requestPermission() }
}

struct WeekendAlertView: View {
    @StateObject private var vm = WeekendAlertViewModel()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Group {
            if vm.loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                form
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Alerta de tiempo")
        .navigationBarTitleDisplayMode(.inline)
        .task { await vm.load() }
        .onChange(of: vm.savedOk) { _, ok in
            if ok { Task { try? await Task.sleep(nanoseconds: 600_000_000); dismiss() } }
        }
    }

    private var form: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text("Te enviamos una notificación comparando hasta 3 escuelas para los días que elijas de la próxima semana: nota global, desglose por día y aviso de lluvia.")
                    .font(.system(size: 13)).foregroundStyle(Cumbre.ink2)

                toggleRow("ACTIVADA", isOn: $vm.enabled)

                section("DÍAS A COMPARAR") {
                    HStack(spacing: 6) {
                        ForEach(1...7, id: \.self) { iso in
                            chip(DAY_LABELS[iso - 1], selected: vm.alertDays.contains(iso)) { vm.toggleDay(iso) }
                        }
                    }
                }

                section("MODO") {
                    HStack(spacing: 8) {
                        chip("MIS ESCUELAS", selected: !vm.nearby) { vm.nearby = false; vm.savedOk = false }
                        chip("POR CERCANÍA", selected: vm.nearby) { vm.nearby = true; vm.savedOk = false }
                    }
                }

                if vm.nearby {
                    section("RADIO") {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 6) {
                                ForEach(RADIUS_OPTIONS, id: \.self) { km in
                                    chip("\(km) km", selected: vm.radiusKm == km) { vm.radiusKm = km; vm.savedOk = false }
                                }
                            }
                        }
                        Button("Activar ubicación") { vm.requestLocation() }
                            .font(Cumbre.mono(11)).foregroundStyle(Cumbre.terra)
                    }
                } else {
                    schoolPicker
                }

                section("DÍA DE AVISO") {
                    HStack(spacing: 6) {
                        ForEach(1...7, id: \.self) { iso in
                            chip(DAY_LABELS[iso - 1], selected: vm.notifyDay == iso) { vm.notifyDay = iso; vm.savedOk = false }
                        }
                    }
                }
                section("HORA DE AVISO") {
                    HStack(spacing: 6) {
                        ForEach(HOUR_OPTIONS, id: \.self) { h in
                            chip("\(h):00", selected: vm.notifyHour == h) { vm.notifyHour = h; vm.savedOk = false }
                        }
                    }
                }

                Divider().overlay(Cumbre.rule)
                toggleRow("VENTANA ÓPTIMA HOY", isOn: $vm.optimalEnabled)
                Text("Aviso si hoy hay una buena franja en tus favoritas.")
                    .font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                if vm.optimalEnabled {
                    HStack(spacing: 6) {
                        ForEach([60, 70, 80], id: \.self) { t in
                            chip("≥ \(t)", selected: vm.optimalThreshold == t) { vm.optimalThreshold = t; vm.savedOk = false }
                        }
                    }
                }

                if let e = vm.error { Text(e).font(.system(size: 13)).foregroundStyle(Cumbre.bad) }

                Button { Task { await vm.save() } } label: {
                    HStack {
                        if vm.saving { ProgressView().tint(.white) }
                        Text(vm.savedOk ? "GUARDADO ✓" : "GUARDAR").font(Cumbre.mono(13, .bold)).tracking(0.8)
                    }
                    .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity)
                    .background(vm.savedOk ? Cumbre.ok : Cumbre.terra)
                }
                .buttonStyle(.plain).disabled(vm.saving)
            }
            .padding(16)
        }
    }

    private var schoolPicker: some View {
        section("ESCUELAS (máx 3)") {
            ForEach(vm.selected, id: \.id) { s in
                HStack {
                    Text(s.name).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                    Spacer()
                    Button { vm.remove(s.id) } label: {
                        Image(systemName: "xmark.circle.fill").foregroundStyle(Cumbre.ink3)
                    }.buttonStyle(.plain)
                }
                .padding(.vertical, 4)
            }
            if vm.selected.count < 3 {
                TextField("Buscar escuela…", text: Binding(get: { vm.query }, set: { vm.search($0) }))
                    .font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                    .autocorrectionDisabled()
                    .padding(10).background(Cumbre.paper).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                ForEach(vm.suggestions, id: \.id) { s in
                    Button { vm.add(s) } label: {
                        Text(s.name).font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.vertical, 6)
                    }.buttonStyle(.plain)
                }
            }
        }
    }

    private func section<C: View>(_ title: String, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).eyebrow()
            content()
        }
    }

    private func toggleRow(_ title: String, isOn: Binding<Bool>) -> some View {
        HStack {
            Text(title).eyebrow()
            Spacer()
            Toggle("", isOn: isOn).labelsHidden().tint(Cumbre.terra)
        }
    }

    private func chip(_ t: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(t).font(Cumbre.mono(11, .bold)).tracking(0.4)
                .foregroundStyle(selected ? .white : Cumbre.ink2)
                .padding(.horizontal, 11).padding(.vertical, 7)
                .background(selected ? Cumbre.terra : Cumbre.paper)
                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }.buttonStyle(.plain)
    }
}
