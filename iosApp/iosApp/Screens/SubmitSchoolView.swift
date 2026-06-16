import SwiftUI
import Shared

// Proponer escuela nueva — espejo de SubmitSchoolScreen.kt de Android.
// Campos de texto + pegar coordenadas de Google Maps (parser tolerante).
// El mapa para fijar posición a mano llegará con el bridge de MapLibre.

@MainActor
final class SubmitSchoolViewModel: ObservableObject {
    @Published var name = ""
    @Published var region = ""
    @Published var style = ""
    @Published var rockType = ""
    @Published var lat = ""
    @Published var lon = ""
    @Published var coordsPaste = ""
    @Published var location = ""
    @Published var notes = ""
    @Published var saving = false
    @Published var errorText: String?
    @Published var done = false

    // Valores existentes del catálogo para los desplegables (evita erratas).
    @Published var regionOptions: [String] = []
    @Published var styleOptions: [String] = []
    @Published var rockOptions: [String] = []
    @Published var locationOptions: [String] = []

    private let submitSchool: SubmitSchoolUseCase
    private let getSchools: GetSchoolsUseCase
    init(
        submitSchool: SubmitSchoolUseCase = AppDependencies.shared.container.submitSchool,
        getSchools: GetSchoolsUseCase = AppDependencies.shared.container.getSchools
    ) {
        self.submitSchool = submitSchool
        self.getSchools = getSchools
    }

    /// Carga las opciones de los desplegables desde el catálogo (valores únicos).
    func loadOptions() async {
        guard let schools = try? await getSchools.invoke(
            region: nil, style: nil, rockType: nil, lat: nil, lon: nil, radioKm: nil
        ) else { return }
        regionOptions = unique(schools.map { $0.region })
        styleOptions = unique(schools.map { $0.style })
        rockOptions = unique(schools.map { $0.rockType })
        locationOptions = unique(schools.map { $0.location })
    }

    private func unique(_ raw: [String?]) -> [String] {
        Array(Set(raw.compactMap { $0 }.filter { !$0.isEmpty })).sorted()
    }

    var canSubmit: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
        && parseDouble(lat) != nil && parseDouble(lon) != nil
    }

    /// Pega texto de Google Maps ("40.42, -3.70") y rellena lat/lon.
    func applyPaste() {
        if let (la, lo) = SubmitSchoolViewModel.parseCoords(coordsPaste) {
            lat = String(format: "%.6f", la)
            lon = String(format: "%.6f", lo)
        }
    }

    func submit() async {
        guard let la = parseDouble(lat), let lo = parseDouble(lon) else { return }
        saving = true; errorText = nil
        defer { saving = false }
        let req = SubmitSchoolRequest(
            name: name.trimmingCharacters(in: .whitespaces),
            region: region.nilIfBlank,
            style: style.nilIfBlank,
            rockType: rockType.nilIfBlank,
            lat: la, lon: lo,
            location: location.nilIfBlank,
            source: nil,
            notes: notes.nilIfBlank
        )
        do {
            _ = try await submitSchool.invoke(req: req)
            done = true
        } catch {
            errorText = "No se pudo enviar la propuesta. Revisa la conexión."
        }
    }

    private func parseDouble(_ s: String) -> Double? {
        Double(s.replacingOccurrences(of: ",", with: "."))
    }

    /// Parser tolerante: extrae los 2 primeros números del texto (lat, lon) y
    /// valida rangos. Espejo de parseCoordinates() de Android.
    static func parseCoords(_ raw: String) -> (Double, Double)? {
        let matches = raw.matches(of: /-?\d+[.,]?\d*/).map { String($0.output) }
        guard matches.count >= 2 else { return nil }
        guard let la = Double(matches[0].replacingOccurrences(of: ",", with: ".")),
              let lo = Double(matches[1].replacingOccurrences(of: ",", with: ".")) else { return nil }
        if la < -90 || la > 90 || lo < -180 || lo > 180 { return nil }
        return (la, lo)
    }
}

struct SubmitSchoolView: View {
    @StateObject private var vm = SubmitSchoolViewModel()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                if vm.done {
                    successView
                } else {
                    form
                }
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Proponer escuela")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cerrar") { dismiss() }.foregroundStyle(Cumbre.ink3)
                }
            }
            .task { await vm.loadOptions() }
        }
    }

    private var form: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                field("NOMBRE", $vm.name, "ej: La Pedriza")
                pickerField("REGIÓN", $vm.region, vm.regionOptions)
                pickerField("ESTILO", $vm.style, vm.styleOptions)
                pickerField("TIPO DE ROCA", $vm.rockType, vm.rockOptions)

                // Pegar coordenadas de Google Maps.
                VStack(alignment: .leading, spacing: 6) {
                    Text("PEGAR COORDENADAS (GOOGLE MAPS)").eyebrow()
                    HStack(spacing: 8) {
                        TextField("40.4168, -3.7038", text: $vm.coordsPaste)
                            .font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                            .autocorrectionDisabled()
                            .padding(10).background(Cumbre.paper)
                            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                            .onChange(of: vm.coordsPaste) { _, _ in vm.applyPaste() }
                    }
                }
                HStack(spacing: 10) {
                    field("LATITUD", $vm.lat, "40.42").frame(maxWidth: .infinity)
                    field("LONGITUD", $vm.lon, "-3.70").frame(maxWidth: .infinity)
                }

                pickerField("UBICACIÓN", $vm.location, vm.locationOptions)
                field("NOTAS", $vm.notes, "Cualquier info útil")

                if let e = vm.errorText {
                    Text(e).font(.system(size: 13)).foregroundStyle(Cumbre.bad)
                }

                Button {
                    Task { await vm.submit() }
                } label: {
                    HStack {
                        if vm.saving { ProgressView().tint(.white) }
                        Text("ENVIAR PROPUESTA").font(Cumbre.mono(13, .bold)).tracking(0.8)
                    }
                    .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity)
                    .background(vm.canSubmit ? Cumbre.terra : Cumbre.ink3)
                }
                .buttonStyle(.plain)
                .disabled(!vm.canSubmit || vm.saving)
                .padding(.top, 4)
            }
            .padding(16)
        }
    }

    private var successView: some View {
        VStack(spacing: 16) {
            Image(systemName: "checkmark.seal.fill").font(.system(size: 56)).foregroundStyle(Cumbre.ok)
            Text("¡Propuesta enviada!").font(Cumbre.serif(24, .bold)).foregroundStyle(Cumbre.ink)
            Text("La revisaremos en 24-48 h. Gracias por aportar a la comunidad.")
                .font(.system(size: 15)).foregroundStyle(Cumbre.ink2)
                .multilineTextAlignment(.center).padding(.horizontal, 24)
            Button("CERRAR") { dismiss() }
                .font(Cumbre.mono(12, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink)
                .padding(.vertical, 14).padding(.horizontal, 32)
                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                .padding(.top, 8)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity).padding(32)
    }

    private func field(_ label: String, _ text: Binding<String>, _ ph: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).eyebrow()
            TextField(ph, text: text).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                .padding(10).background(Cumbre.paper)
                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }
    }

    /// Campo desplegable con valores del catálogo (evita erratas). La última
    /// opción "Otro…" muestra un campo de texto para casos no listados.
    @ViewBuilder
    private func pickerField(_ label: String, _ value: Binding<String>, _ options: [String]) -> some View {
        let isOther = !value.wrappedValue.isEmpty && !options.contains(value.wrappedValue)
        VStack(alignment: .leading, spacing: 6) {
            Text(label).eyebrow()
            Menu {
                ForEach(options, id: \.self) { opt in
                    Button(opt) { value.wrappedValue = opt }
                }
                Divider()
                Button("Otro…") { value.wrappedValue = " " }   // espacio → modo "otro"
            } label: {
                HStack {
                    Text(value.wrappedValue.trimmingCharacters(in: .whitespaces).isEmpty ? "Seleccionar…" : value.wrappedValue)
                        .foregroundStyle(value.wrappedValue.trimmingCharacters(in: .whitespaces).isEmpty ? Cumbre.ink3 : Cumbre.ink)
                    Spacer()
                    Image(systemName: "chevron.down").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                }
                .font(.system(size: 15))
                .padding(10).background(Cumbre.paper)
                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
            }
            if isOther {
                TextField("Escribe el valor", text: value)
                    .font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                    .padding(10).background(Cumbre.paper)
                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
            }
        }
    }
}

private extension String {
    var nilIfBlank: String? { trimmingCharacters(in: .whitespaces).isEmpty ? nil : self }
}
