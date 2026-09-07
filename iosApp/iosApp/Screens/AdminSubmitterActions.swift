import SwiftUI
import Shared

/// Fila de acciones sobre quién mandó una propuesta: mensaje directo y
/// su historial de propuestas anteriores — antes al admin le llegaba una
/// propuesta y no sabía quién la mandó, ni podía preguntarle nada (Álvaro,
/// 2026-09-06). SOLO iOS por ahora (ver nota en CLAUDE.md): Álvaro usa el
/// panel de admin únicamente aquí.
struct AdminSubmitterActions: View {
    let uid: String?
    let name: String?
    /// Foto de perfil del proponente, para reconocer de un vistazo quién
    /// mandó la propuesta (Álvaro, 2026-09-07: "que salga, para facilitar
    /// que se vea quien lo hizo").
    var photoUrl: String? = nil
    /// Presente == el admin está esperando respuesta suya antes de revisar.
    /// nil (sin botón) cuando la propuesta ya está resuelta — no tiene
    /// sentido "esperar respuesta" de algo ya aprobado o rechazado.
    var awaitingReply: Bool? = nil
    var onToggleAwaitingReply: (() -> Void)? = nil
    var busy: Bool = false
    @State private var showChat = false
    @State private var showHistory = false

    var body: some View {
        if let uid, !uid.isEmpty {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 10) {
                    AvatarCircle(url: photoUrl, size: 28)
                    if let name, !name.isEmpty {
                        Text(name).font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink2)
                    }
                    Spacer()
                    Button { showChat = true } label: {
                        Label("Mensaje", systemImage: "bubble.left")
                            .font(Cumbre.mono(11, .bold))
                    }
                    Button { showHistory = true } label: {
                        Label("Historial", systemImage: "clock.arrow.circlepath")
                            .font(Cumbre.mono(11, .bold))
                    }
                }
                .foregroundStyle(Cumbre.terra)
                if let awaitingReply, let onToggleAwaitingReply {
                    Button(action: onToggleAwaitingReply) {
                        Label(awaitingReply ? "DEJAR DE ESPERAR RESPUESTA" : "ESPERAR RESPUESTA SUYA",
                              systemImage: awaitingReply ? "hourglass.circle.fill" : "hourglass.circle")
                            .font(Cumbre.mono(10, .bold)).tracking(0.4)
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(awaitingReply ? .white : Cumbre.ink2)
                    .padding(.horizontal, 8).padding(.vertical, 4)
                    .background(awaitingReply ? Cumbre.ink3 : Color.clear)
                    .overlay(awaitingReply ? nil : RoundedRectangle(cornerRadius: 4).stroke(Cumbre.rule, lineWidth: 1))
                    .clipShape(RoundedRectangle(cornerRadius: 4))
                    .disabled(busy)
                }
            }
            .sheet(isPresented: $showChat) {
                NavigationStack { ChatView(otherUid: uid, otherName: name ?? "") }
            }
            .sheet(isPresented: $showHistory) {
                NavigationStack { UserActivityHistoryView(uid: uid, name: name) }
            }
        }
    }
}

/// Historial de un usuario: escuelas y mejoras que ha propuesto (parkings,
/// piedras, correcciones... todo), para que el admin vea de un vistazo si es
/// alguien fiable (Álvaro, 2026-09-06: "que se viera todo bien").
struct UserActivityHistoryView: View {
    let uid: String
    let name: String?
    @State private var items: [UserActivityItem] = []
    @State private var loading = true
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Group {
            if loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if items.isEmpty {
                Text("Sin propuestas anteriores.")
                    .foregroundStyle(Cumbre.ink3)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List(items, id: \.id) { item in
                    VStack(alignment: .leading, spacing: 3) {
                        HStack {
                            Text(kindLabel(item.kind)).font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra)
                            Spacer()
                            Text(statusLabel(item.status)).font(Cumbre.mono(10, .bold))
                                .foregroundStyle(statusColor(item.status))
                        }
                        Text(item.label ?? "—").font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                        Text(item.createdAt).font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                    }
                    .padding(.vertical, 2)
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle(name ?? "Historial")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("Cerrar") { dismiss() }
            }
        }
        .task {
            items = (try? await AppDependencies.shared.container.getUserActivity.invoke(uid: uid)) ?? []
            loading = false
        }
    }

    private func kindLabel(_ k: String) -> String {
        switch k {
        case "SCHOOL": return "ESCUELA"
        case "PARKING": return "PARKING"
        case "BOULDER": return "PIEDRA"
        case "SECTOR": return "SECTOR"
        case "POSITION_CORRECTION": return "MOVER"
        case "ASSIGN_SECTOR": return "SECTOR→PIEDRA"
        case "SCHOOL_NAME_CORRECTION": return "NOMBRE"
        case "SCHOOL_STYLE_CORRECTION": return "ESTILO"
        default: return k
        }
    }

    private func statusLabel(_ s: String) -> String {
        switch s {
        case "PENDING": return "PENDIENTE"
        case "APPROVED": return "APROBADA"
        case "REJECTED": return "RECHAZADA"
        default: return s
        }
    }

    private func statusColor(_ s: String) -> Color {
        switch s {
        case "APPROVED": return Cumbre.terra
        case "REJECTED": return Cumbre.bad
        default: return Cumbre.ink3
        }
    }
}
