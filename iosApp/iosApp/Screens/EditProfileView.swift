import SwiftUI
import Shared

@MainActor
final class EditProfileViewModel: ObservableObject {
    @Published var username = ""
    @Published var displayName = ""
    @Published var bio = ""
    @Published var topGrade = ""
    @Published var loading = true
    @Published var saving = false

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
            username = p.username ?? ""
            displayName = p.displayName ?? ""
            bio = p.bio ?? ""
            topGrade = p.topGrade ?? ""
        }
        loading = false
    }

    /// Guarda y devuelve true si fue bien.
    func save() async -> Bool {
        saving = true
        defer { saving = false }
        let req = UpdateProfileRequest(
            username: username.trimmingCharacters(in: .whitespaces).nilIfEmpty,
            displayName: displayName.trimmingCharacters(in: .whitespaces).nilIfEmpty,
            bio: bio.nilIfEmpty,
            topGrade: topGrade.trimmingCharacters(in: .whitespaces).nilIfEmpty,
            isPublic: nil,
            photoUrl: nil
        )
        return (try? await updateMyProfile.invoke(req: req)) != nil
    }
}

/// Editar perfil — espejo de EditProfileScreen.kt (campos de texto). Cambiar la
/// foto necesita el bridge de Firebase Storage (pendiente).
struct EditProfileView: View {
    @StateObject private var vm = EditProfileViewModel()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            if vm.loading {
                ProgressView().padding(.top, 60)
            } else {
                VStack(alignment: .leading, spacing: 16) {
                    field("NOMBRE", text: $vm.displayName, placeholder: "Tu nombre")
                    field("USUARIO", text: $vm.username, placeholder: "usuario", lower: true)
                    field("GRADO TOPE", text: $vm.topGrade, placeholder: "p. ej. 7b+")
                    VStack(alignment: .leading, spacing: 6) {
                        Text("BIO").eyebrow()
                        TextField("Sobre ti…", text: $vm.bio, axis: .vertical)
                            .lineLimit(2...5)
                            .font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                            .padding(10).background(Cumbre.paper)
                            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                    }
                }
                .padding(16)
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Editar perfil")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    Task { if await vm.save() { dismiss() } }
                } label: {
                    if vm.saving { ProgressView() } else { Text("Guardar").foregroundStyle(Cumbre.terra) }
                }
                .disabled(vm.saving)
            }
        }
        .task { await vm.load() }
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
