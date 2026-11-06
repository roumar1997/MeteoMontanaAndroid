import SwiftUI
import Shared

// Moderación UGC (requisito App Store): denunciar comentarios/notas/usuarios
// y bloquear usuarios. Espejo de ReportDialog.kt / ModerationViewModel de
// Android. `hiddenIds` oculta AL INSTANTE lo denunciado para quien denuncia.
// Guarda claves tipadas "TIPO:id" (p.ej. "FEED_POST:51", "NOTE:51"): los ids de
// post del feed son enteros pequeños que chocaban con ids de notas/comentarios
// → denunciar una nota id 51 ocultaba el post 51. Cada filtro compone su clave.

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

    func report(targetType: String, targetId: String, reason: String, details: String? = nil,
                alsoBlockUid: String? = nil) {
        hiddenIds.insert("\(targetType):\(targetId)")
        Task {
            try? await api.report(targetType: targetType, targetId: targetId, reason: reason, details: details)
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


/// Hoja de denuncia: eliges el motivo, cuentas qué pasa si quieres, puedes bloquear al autor de paso y ENVIAR. Después
/// se dice qué pasa ahora (lo revisa el equipo y te avisamos). Igual que ReportDialog de Android.
struct ReportSheet: View {
    let title: String
    /// @nombre del autor (para la opción de bloquear); nil = sin esa opción.
    var authorLabel: String? = nil
    let onReport: (_ reason: String, _ alsoBlock: Bool, _ details: String?) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var reason: String?
    @State private var details = ""
    @State private var alsoBlock = false
    @State private var sent = false

    /// El código lo entiende el servidor; los tres nuevos desde 2026-11-06.
    private let reasons: [(String, String)] = [
        ("SPAM", L("Spam o publicidad")),
        ("OFFENSIVE", L("Ofensivo o insultante")),
        ("HARASSMENT", L("Acoso")),
        ("INAPPROPRIATE_PHOTO", L("Foto inapropiada")),
        ("OFFENSIVE_NAME", L("Nombre o usuario ofensivo")),
        ("FALSE_INFO", L("Información falsa o peligrosa")),
        ("OTHER", L("Otro motivo"))
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                if sent { sentView } else { form }
            }
            .padding(.horizontal, 16).padding(.top, 18).padding(.bottom, 20)
        }
        .presentationDetents([.large])
        .background(Cumbre.bg)
    }

    private var sentView: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(L("Gracias por avisar")).font(.system(size: 18, weight: .semibold)).foregroundStyle(Cumbre.ink)
            Text(L("Lo revisará el equipo de Cumbre y te avisaremos cuando esté revisado. Mientras tanto, ya no lo verás."))
                .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
            pill(L("ENTENDIDO"), filled: true) { dismiss() }
        }
    }

    @ViewBuilder private var form: some View {
        Text(title).font(Cumbre.mono(11, .bold)).tracking(1.4).foregroundStyle(Cumbre.ink3)
        Text(L("¿Qué pasa?")).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
        ForEach(reasons, id: \.0) { code, label in
            let on = reason == code
            Button { reason = code } label: {
                Text(label).font(.system(size: 15)).foregroundStyle(on ? Cumbre.terra : Cumbre.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12).padding(.vertical, 10)
                    .background(on ? Cumbre.terra.opacity(0.12) : Color.clear, in: RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(on ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        if reason != nil {
            TextField(L("Cuéntanos qué pasa (opcional)"), text: $details, axis: .vertical)
                .lineLimit(2...4)
                .padding(10)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Cumbre.rule, lineWidth: 1))
                .onChange(of: details) { _, v in if v.count > 500 { details = String(v.prefix(500)) } }
        }
        if let author = authorLabel {
            Button { alsoBlock.toggle() } label: {
                HStack(spacing: 8) {
                    Image(systemName: alsoBlock ? "checkmark.square.fill" : "square")
                        .foregroundStyle(alsoBlock ? Cumbre.terra : Cumbre.ink3)
                    Text(L("TAMBIÉN BLOQUEAR A %@", author.uppercased()))
                        .font(Cumbre.mono(11, .bold)).tracking(0.6)
                        .foregroundStyle(alsoBlock ? Cumbre.terra : Cumbre.ink3)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 6)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        Text("Un admin lo revisará. El contenido denunciado deja de mostrarse para ti al instante.")
            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
        HStack(spacing: 10) {
            pill(L("CANCELAR"), filled: false) { dismiss() }
            pill(L("ENVIAR"), filled: true, enabled: reason != nil) {
                guard let r = reason else { return }
                let text = details.trimmingCharacters(in: .whitespacesAndNewlines)
                onReport(r, alsoBlock, text.isEmpty ? nil : text)
                withAnimation { sent = true }
            }
        }
    }

    private func pill(_ text: String, filled: Bool, enabled: Bool = true, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text).font(Cumbre.mono(12, .bold))
                .foregroundStyle(filled ? .white : Cumbre.ink3)
                .frame(maxWidth: .infinity).padding(.vertical, 11)
                .background(filled ? Cumbre.terraFill.opacity(enabled ? 1 : 0.35) : Color.clear, in: Capsule())
                .overlay(Capsule().stroke(filled ? Color.clear : Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}
