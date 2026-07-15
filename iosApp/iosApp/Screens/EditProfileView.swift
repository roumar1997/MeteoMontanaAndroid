import SwiftUI
import Shared
import PhotosUI

@MainActor
final class EditProfileViewModel: ObservableObject {
    @Published var username = ""
    @Published var displayName = ""
    @Published var bio = ""
    @Published var topGrade = ""
    @Published var isPublic = true
    @Published var gender = ""   // "" | "WOMAN" | "MAN" | "OTHER"
    // Material propio (perfil): mismo formato/helpers que el gear de quedadas
    // (parseGear/buildGearJson/gearItemsForDiscipline en MeetupDetailView.swift).
    // Se usa para autorrellenar el material al unirte a una quedada.
    @Published var gear: [String: Int] = [:]
    @Published var photoUrl: String?
    @Published var pickedImage: UIImage?      // foto nueva pendiente de subir
    @Published var uploading = false
    @Published var loading = true
    @Published var saving = false
    private var uid: String?

    private let getMyProfile: GetMyProfileUseCase
    private let updateMyProfile: UpdateMyProfileUseCase

    init(
        getMyProfile: GetMyProfileUseCase = AppDependencies.shared.container.getMyProfile,
        updateMyProfile: UpdateMyProfileUseCase = AppDependencies.shared.container.updateMyProfile
    ) {
        self.getMyProfile = getMyProfile
        self.updateMyProfile = updateMyProfile
    }

    func load() async {
        loading = true
        if let p = try? await getMyProfile.invoke() {
            uid = p.uid
            username = p.username ?? ""
            displayName = p.displayName ?? ""
            bio = p.bio ?? ""
            topGrade = p.topGrade ?? ""
            isPublic = p.isPublic
            gender = p.gender ?? ""
            let current = parseGear(p.gearJson)
            var initial: [String: Int] = [:]
            for item in gearItemsForDiscipline(nil) { initial[item.key] = current[item.key] ?? 0 }
            gear = initial
            photoUrl = p.photoUrl
        }
        loading = false
    }

    /// Guarda y devuelve true si fue bien. Si hay foto nueva, la sube primero.
    func save() async -> Bool {
        saving = true
        defer { saving = false }
        // Sube la foto nueva a Storage (si la hay) y usa su URL.
        if let img = pickedImage {
            uploading = true
            photoUrl = (try? await StorageUploader.uploadProfilePhoto(img)) ?? photoUrl
            uploading = false
        }
        let req = UpdateProfileRequest(
            username: username.trimmingCharacters(in: .whitespaces).nilIfEmpty,
            displayName: displayName.trimmingCharacters(in: .whitespaces).nilIfEmpty,
            bio: bio.nilIfEmpty,
            topGrade: topGrade.trimmingCharacters(in: .whitespaces).nilIfEmpty,
            isPublic: KotlinBoolean(bool: isPublic),
            photoUrl: photoUrl,
            gender: gender.nilIfEmpty,
            gearJson: buildGearJson(gear)
        )
        return (try? await updateMyProfile.invoke(req: req)) != nil
    }
}

/// Editar perfil — espejo de EditProfileScreen.kt (campos de texto). Cambiar la
/// foto necesita el bridge de Firebase Storage (pendiente).
private struct CropCandidate: Identifiable { let id = UUID(); let image: UIImage }

struct EditProfileView: View {
    @StateObject private var vm = EditProfileViewModel()
    @Environment(\.dismiss) private var dismiss
    @State private var pickerItem: PhotosPickerItem?
    @State private var cropCandidate: CropCandidate?

    var body: some View {
        ScrollView {
            if vm.loading {
                ProgressView().padding(.top, 60)
            } else {
                VStack(alignment: .leading, spacing: 16) {
                    avatarPicker
                    field("NOMBRE", text: $vm.displayName, placeholder: "Tu nombre")
                    field("USUARIO", text: $vm.username, placeholder: "usuario", lower: true)
                    // El grado tope ya NO es manual: se calcula solo desde el diario
                    // (tope de bloque y de vía por separado). Sin campo editable.
                    VStack(alignment: .leading, spacing: 6) {
                        Text("GRADO MÁXIMO").eyebrow()
                        Text("Se calcula solo desde tu diario")
                            .font(.system(size: 15)).foregroundStyle(Cumbre.terra)
                    }
                    VStack(alignment: .leading, spacing: 6) {
                        Text("BIO").eyebrow()
                        TextField("Sobre ti…", text: $vm.bio, axis: .vertical)
                            .lineLimit(2...5)
                            .font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                            .padding(10).background(Cumbre.paper)
                            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                    }
                    // Público/privado: si es privado, tu perfil sale bloqueado a
                    // quien no te sigue y los follows pasan por solicitud.
                    VStack(alignment: .leading, spacing: 6) {
                        Toggle(isOn: $vm.isPublic) {
                            Text("PERFIL PÚBLICO").eyebrow()
                        }
                        .tint(Cumbre.terra)
                        .padding(10).background(Cumbre.paper)
                        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                        Text(vm.isPublic
                             ? "Cualquiera puede ver tu perfil, diario y estadísticas."
                             : "Tu perfil queda bloqueado; seguirte requiere aprobar una solicitud.")
                            .font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                    }

                    // Género — solo para el gate de quedadas no mixtas
                    VStack(alignment: .leading, spacing: 8) {
                        Text("GÉNERO (privado — solo para quedadas no mixtas)").eyebrow()
                        // Rejilla 2x2 (antes HStack de 3; con "Otro" ya no cabe en una fila
                        // sin recortarse en iPhones pequeños).
                        let genderOptions = [("WOMAN", "Mujer"), ("MAN", "Hombre"), ("OTHER", "Otro"), ("", "No indicar")]
                        VStack(spacing: 8) {
                            ForEach(0..<2, id: \.self) { row in
                                HStack(spacing: 8) {
                                    ForEach(0..<2, id: \.self) { col in
                                        let (val, label) = genderOptions[row * 2 + col]
                                        let sel = vm.gender == val
                                        Button { vm.gender = val } label: {
                                            Text(label)
                                                .font(.system(size: 12, weight: sel ? .bold : .regular))
                                                .frame(maxWidth: .infinity)
                                                .padding(.horizontal, 10).padding(.vertical, 6)
                                                .background(sel ? Cumbre.terra.opacity(0.15) : Cumbre.paper)
                                                .foregroundStyle(sel ? Cumbre.terra : Cumbre.ink3)
                                                .overlay(RoundedRectangle(cornerRadius: 4)
                                                    .stroke(sel ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                                                .cornerRadius(4)
                                        }
                                    }
                                }
                            }
                        }
                        Text("Nunca se muestra a nadie. Solo lo usa el servidor para crear quedadas no mixtas.")
                            .font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                    }

                    // Material propio: se autorrellena al unirte a una quedada
                    // (sigue siendo editable ahí, para esa quedada concreta).
                    VStack(alignment: .leading, spacing: 8) {
                        Text("MI MATERIAL").eyebrow()
                        VStack(spacing: 8) {
                            ForEach(gearItemsForDiscipline(nil), id: \.key) { item in
                                if isBooleanGearKey(item.key) {
                                    HStack {
                                        Text(item.label).font(.subheadline).fontWeight(.medium)
                                        Spacer()
                                        Toggle("", isOn: Binding(
                                            get: { (vm.gear[item.key] ?? 0) > 0 },
                                            set: { vm.gear[item.key] = $0 ? 1 : 0 }
                                        )).labelsHidden().tint(Cumbre.terra)
                                    }
                                } else {
                                    HStack {
                                        Text(item.label).font(.subheadline).fontWeight(.medium)
                                        Spacer()
                                        HStack(spacing: 8) {
                                            Button {
                                                let cur = vm.gear[item.key] ?? 0
                                                if cur > 0 { vm.gear[item.key] = cur - 1 }
                                            } label: {
                                                Image(systemName: "minus.circle")
                                                    .font(.title3)
                                                    .foregroundColor((vm.gear[item.key] ?? 0) > 0 ? Cumbre.ink : Cumbre.ink.opacity(0.2))
                                            }
                                            .disabled((vm.gear[item.key] ?? 0) == 0)
                                            Text("\(vm.gear[item.key] ?? 0)")
                                                .font(.title3).fontWeight(.bold).monospacedDigit()
                                                .frame(minWidth: 28)
                                            Button {
                                                vm.gear[item.key, default: 0] += 1
                                            } label: {
                                                Image(systemName: "plus.circle")
                                                    .font(.title3).foregroundColor(Cumbre.terra)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        .padding(10).background(Cumbre.paper)
                        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                    }
                }
                .padding(16)
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(NSLocalizedString("profile_edit", comment: ""))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    Task { if await vm.save() { dismiss() } }
                } label: {
                    if vm.saving { ProgressView() } else { Text(NSLocalizedString("common_save", comment: "")).foregroundStyle(Cumbre.terra) }
                }
                .disabled(vm.saving)
            }
        }
        .task { await vm.load() }
        // Tras elegir foto, abre el recortador para encuadrarla antes de fijarla.
        .fullScreenCover(item: $cropCandidate) { c in
            ImageCropView(
                image: c.image,
                onCancel: { cropCandidate = nil },
                onDone: { cropped in vm.pickedImage = cropped; cropCandidate = nil }
            )
        }
    }

    private var avatarPicker: some View {
        HStack(spacing: 16) {
            ZStack {
                if let img = vm.pickedImage {
                    Image(uiImage: img).resizable().scaledToFill()
                } else if let u = vm.photoUrl.flatMap({ URL(string: $0) }) {
                    AsyncImage(url: u) { $0.resizable().scaledToFill() } placeholder: {
                        Image(systemName: "person.crop.circle.fill").resizable().foregroundStyle(Cumbre.ink3)
                    }
                } else {
                    Image(systemName: "person.crop.circle.fill").resizable().foregroundStyle(Cumbre.ink3)
                }
            }
            .frame(width: 72, height: 72).clipShape(Circle())
            .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))

            PhotosPicker(selection: $pickerItem, matching: .images) {
                Text(vm.uploading ? "Subiendo…" : "Cambiar foto")
                    .font(Cumbre.mono(12, .bold)).tracking(0.6).foregroundStyle(Cumbre.terra)
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
            }
            Spacer()
        }
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self),
                   let img = UIImage(data: data) {
                    cropCandidate = CropCandidate(image: img)   // → abre el recortador
                }
            }
        }
    }

    private func field(_ label: String, text: Binding<String>, placeholder: String, lower: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).eyebrow()
            TextField(placeholder, text: text)
                .font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                .autocorrectionDisabled()
                .textInputAutocapitalization(lower ? .never : .sentences)
                .padding(10).background(Cumbre.paper)
                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
