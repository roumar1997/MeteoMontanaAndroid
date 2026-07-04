import SwiftUI
import Shared

// Moderación UGC (requisito App Store): denunciar comentarios/notas/usuarios
// y bloquear usuarios. Espejo de ReportDialog.kt / ModerationViewModel de
// Android. `hiddenIds` oculta AL INSTANTE lo denunciado para quien denuncia.

@MainActor
final class ModerationStore: ObservableObject {
    static let shared = ModerationStore()
    @Published var hiddenIds: Set<String> = []
    @Published var blocked: Set<String> = []

    private var api: KtorModerationApi { AppDependencies.shared.container.moderationApi }

    func loadBlocked() async {
        if let set = try? await api.getBlocked() {
            blocked = Set(set.compactMap { $0 as? String })
        }
    }

    func report(targetType: String, targetId: String, reason: String,
                alsoBlockUid: String? = nil) {
        hiddenIds.insert(targetId)
        Task {
            try? await api.report(targetType: targetType, targetId: targetId, reason: reason)
            if let uid = alsoBlockUid {
                try? await api.blockUser(uid: uid)
                blocked.insert(uid)
            }
        }
    }

    func block(_ uid: String) {
        Task {
            try? await api.blockUser(uid: uid)
            blocked.insert(uid)
        }
    }

    func unblock(_ uid: String) {
        Task {
            try? await api.unblockUser(uid: uid)
            blocked.remove(uid)
        }
    }
}

/// Hoja de denuncia (maqueta A): motivos de un toque + bloquear de paso.
struct ReportSheet: View {
    let title: String
    /// @nombre del autor (para la opción de bloquear); nil = sin esa opción.
    var authorLabel: String? = nil
    let onReport: (_ reason: String, _ alsoBlock: Bool) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var alsoBlock = false

    private let reasons: [(String, String)] = [
        ("SPAM", "Spam o publicidad"),
        ("OFFENSIVE", "Ofensivo o acoso"),
        ("FALSE_INFO", "Información falsa o peligrosa"),
        ("OTHER", "Otro motivo")
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(Cumbre.mono(11, .bold)).tracking(1.4)
                .foregroundStyle(Cumbre.ink3)
                .padding(.top, 18)
            ForEach(reasons, id: \.0) { code, label in
                Button {
                    onReport(code, alsoBlock)
                    dismiss()
                } label: {
                    Text(label).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 12).padding(.vertical, 10)
                        .overlay(RoundedRectangle(cornerRadius: 4).stroke(Cumbre.rule, lineWidth: 1))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            if let author = authorLabel {
                Button { alsoBlock.toggle() } label: {
                    HStack(spacing: 8) {
                        Image(systemName: alsoBlock ? "checkmark.square.fill" : "square")
                            .foregroundStyle(alsoBlock ? Cumbre.terra : Cumbre.ink3)
                        Text("TAMBIÉN BLOQUEAR A \(author.uppercased())")
                            .font(Cumbre.mono(11, .bold)).tracking(0.6)
                            .foregroundStyle(alsoBlock ? Cumbre.terra : Cumbre.ink3)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12).padding(.vertical, 10)
                    .overlay(RoundedRectangle(cornerRadius: 4)
                        .stroke(alsoBlock ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            Text("Un admin lo revisará. El contenido denunciado deja de mostrarse para ti al instante.")
                .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
            Button("CANCELAR") { dismiss() }
                .font(Cumbre.mono(12, .bold))
                .foregroundStyle(Cumbre.ink3)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .buttonStyle(.plain)
            Spacer()
        }
        .padding(.horizontal, 16)
        .presentationDetents([.medium])
        .background(Cumbre.bg)
    }
}
