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
    @State private var showChat = false
    @State private var showHistory = false

    var body: some View {
        if let uid, !uid.isEmpty {
            HStack(spacing: 12) {
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
