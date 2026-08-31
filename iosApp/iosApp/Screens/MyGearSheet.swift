import SwiftUI
import Shared

/// "Mi material" como hoja propia, colgada del icono de mochila del perfil.
///
/// Antes vivía enterrado en Editar perfil, entre el nombre y el género. Aquí se
/// llega en un toque y solo hay material, que es lo que se cambia a menudo:
/// llevas dos crashpads un finde y tres el siguiente.
///
/// Reutiliza los helpers de las quedadas (`parseGear`, `buildGearJson`,
/// `gearItemsForDiscipline`, `isBooleanGearKey`) — el formato es el mismo, y de
/// ahí sale el reparto de material al unirte a una quedada.
struct MyGearSheet: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var vm = MyGearViewModel()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Lo que sueles llevar. Se usa para repartir el material en las quedadas.")
                        .font(.footnote).foregroundStyle(Cumbre.ink3)

                    VStack(spacing: 8) {
                        ForEach(gearItemsForDiscipline(nil), id: \.key) { item in
                            HStack {
                                Text(item.label).font(.subheadline).fontWeight(.medium)
                                Spacer()
                                if isBooleanGearKey(item.key) {
                                    Toggle("", isOn: Binding(
                                        get: { (vm.gear[item.key] ?? 0) > 0 },
                                        set: { vm.gear[item.key] = $0 ? 1 : 0 }
                                    )).labelsHidden().tint(Cumbre.terra)
                                } else {
                                    HStack(spacing: 12) {
                                        Button {
                                            let cur = vm.gear[item.key] ?? 0
                                            if cur > 0 { vm.gear[item.key] = cur - 1 }
                                        } label: {
                                            Image(systemName: "minus.circle").font(.title3)
                                                .foregroundStyle((vm.gear[item.key] ?? 0) > 0
                                                                 ? Cumbre.ink : Cumbre.ink.opacity(0.2))
                                        }
                                        .buttonStyle(.plain)
                                        .disabled((vm.gear[item.key] ?? 0) == 0)
                                        Text("\(vm.gear[item.key] ?? 0)")
                                            .font(Cumbre.mono(16, .bold)).frame(minWidth: 24)
                                        Button { vm.gear[item.key, default: 0] += 1 } label: {
                                            Image(systemName: "plus.circle").font(.title3)
                                                .foregroundStyle(Cumbre.terra)
                                        }.buttonStyle(.plain)
                                    }
                                }
                            }
                            .padding(.vertical, 4)
                        }
                    }
                    .padding(12)
                    .background(Cumbre.paper)
                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))

                    if let aviso = vm.aviso {
                        Text(aviso).font(.footnote).foregroundStyle(Cumbre.terra)
                    }
                }
                .padding(16)
                // El estado de carga NO puede encoger la vista: si el contenido
                // es solo la ruedita, la pantalla se dimensiona a ese tamaño, el
                // fondo con ella, y asoma la pantalla de detrás por los lados
                // —la "raya" que reportó Rodrigo—. Ocupar el ancho lo evita.
                .frame(maxWidth: .infinity, alignment: .leading)
                .opacity(vm.loading ? 0 : 1)
                .overlay { if vm.loading { ProgressView().padding(.top, 40) } }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Mi material")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(NSLocalizedString("common_close", comment: "")) { dismiss() }
                        .foregroundStyle(Cumbre.terra)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        Task { if await vm.save() { dismiss() } }
                    } label: {
                        if vm.saving { ProgressView() }
                        else { Text(NSLocalizedString("common_save", comment: "")).foregroundStyle(Cumbre.terra) }
                    }.disabled(vm.saving || vm.loading)
                }
            }
            .task { await vm.load() }
        }
    }
}

@MainActor
final class MyGearViewModel: ObservableObject {
    @Published var gear: [String: Int] = [:]
    @Published var loading = false
    @Published var saving = false
    @Published var aviso: String?

    private let getMyProfile: GetMyProfileUseCase
    private let updateMyProfile: UpdateMyProfileUseCase

    init(getMyProfile: GetMyProfileUseCase = AppDependencies.shared.container.getMyProfile,
         updateMyProfile: UpdateMyProfileUseCase = AppDependencies.shared.container.updateMyProfile) {
        self.getMyProfile = getMyProfile
        self.updateMyProfile = updateMyProfile
    }

    func load() async {
        loading = true
        defer { loading = false }
        do {
            let p = try await getMyProfile.invoke()
            // TRAZA TEMPORAL (build 125): el material se guardaba en el servidor
            // pero llegaba vacío a la pantalla, y todo el camino compartido está
            // probado y limpio — el único tramo sin verificar es este cruce de
            // Kotlin a Swift. Esto dice qué llega DE VERDAD. Quitar cuando se
            // identifique la causa.
            let crudo = p.gearJson ?? "<nil>"
            print("CUMBRE-GEAR: gearJson recibido = \(crudo)")
            let current = parseGear(p.gearJson)
            print("CUMBRE-GEAR: interpretado = \(current)")
            var initial: [String: Int] = [:]
            for item in gearItemsForDiscipline(nil) { initial[item.key] = current[item.key] ?? 0 }
            gear = initial
            if p.gearJson == nil { aviso = "El servidor no ha devuelto material." }
        } catch {
            aviso = "No se ha podido cargar tu material."
        }
    }

    func save() async -> Bool {
        saving = true
        defer { saving = false }
        // Solo el material: los demás campos van a null y el servidor conserva
        // los que ya tenía. Así esta hoja no puede pisar el nombre ni la bio.
        let req = UpdateProfileRequest(
            username: nil, displayName: nil, bio: nil, topGrade: nil,
            isPublic: nil, photoUrl: nil, gender: nil,
            gearJson: buildGearJson(gear)
        )
        do {
            let actualizado = try await updateMyProfile.invoke(req: req)
            print("CUMBRE-GEAR: guardado, el servidor devuelve = \(actualizado.gearJson ?? "<nil>")")
            return true
        } catch {
            aviso = "No se ha podido guardar."
            return false
        }
    }
}
