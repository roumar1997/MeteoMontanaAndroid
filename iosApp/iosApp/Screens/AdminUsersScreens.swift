import SwiftUI
import Shared

// Pantallas propias de los cuadraditos de la portada del admin (Álvaro, 2026-10-18: «cada cuadradito tiene que abrir
// lo suyo, no la pestaña antigua»): usuarios activos, todos los usuarios, notas y mantenimiento.

/// Qué lista de usuarios enseña `AdminUsersScreen`.
enum AdminUsersMode: Hashable {
    /// Quienes abrieron la app en los últimos N días (1 = hoy).
    case active(days: Int)
    /// Todos los registrados, el más nuevo primero.
    case all

    var title: String {
        switch self {
        case .active(let d): return d == 1 ? "Activos hoy" : "Activos \(d) días"
        case .all: return "Todos los usuarios"
        }
    }
}

/// Si cada persona tiene el asistente de IA activado a mano (se pide al aparecer su fila y se recuerda).
@MainActor
final class AssistantFlagStore: ObservableObject {
    @Published private(set) var flags: [String: Bool] = [:]
    private var asked: Set<String> = []

    func want(_ uid: String) {
        guard !asked.contains(uid) else { return }
        asked.insert(uid)
        Task {
            if let on = try? await AppDependencies.shared.container.getUserAssistantAccess.invoke(uid: uid) {
                flags[uid] = on.boolValue
            } else { asked.remove(uid) }
        }
    }
}

struct AdminUsersScreen: View {
    let mode: AdminUsersMode
    @State private var users: [AdminUserRow]?
    @State private var query = ""
    @State private var onlyNew = false
    /// Aportaciones aprobadas por persona y si tiene la IA activada a mano.
    @State private var approved: [String: Int] = [:]
    @StateObject private var ai = AssistantFlagStore()

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 8) {
                AdminField(placeholder: "Buscar por @usuario o nombre…", text: $query)
                    .padding(.top, 8)
                if mode == .all {
                    HStack {
                        Button { withAnimation { onlyNew.toggle() } } label: {
                            HStack(spacing: 6) {
                                Image(systemName: onlyNew ? "checkmark.circle.fill" : "line.3.horizontal.decrease.circle")
                                    .font(.system(size: 14, weight: .semibold))
                                Text("SOLO NUEVOS · 7 DÍAS").font(Cumbre.mono(11, .bold)).tracking(0.5)
                            }
                            .foregroundStyle(onlyNew ? Color.white : Cumbre.terra)
                            .padding(.horizontal, 14).padding(.vertical, 9)
                            .background(onlyNew ? Cumbre.terraFill : Cumbre.terra.opacity(0.08), in: Capsule())
                            .overlay(Capsule().stroke(Cumbre.terra, lineWidth: 1.5))
                        }.buttonStyle(.plain)
                        Spacer()
                    }
                }
                if let list = shown {
                    Text("\(list.count) USUARIOS").font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink3)
                        .padding(.top, 4)
                    if list.isEmpty { AdminEmptyState(symbol: "person.slash", text: "Nadie por aquí.") }
                    ForEach(list, id: \.uid) { u in
                        NavigationLink(destination: PublicProfileView(uid: u.uid)) { row(u) }.buttonStyle(.plain)
                    }
                } else {
                    ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
                }
            }
            .padding(.horizontal, 16).padding(.bottom, 30)
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(mode.title)
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if users == nil { users = (try? await AppDependencies.shared.container.getAdminUsers.invoke()) ?? [] }
            let contributors = (try? await AppDependencies.shared.container.getContributors.invoke(uid: nil)) ?? []
            approved = Dictionary(contributors.map { ($0.uid, Int($0.approved)) }, uniquingKeysWith: { a, _ in a })
        }
        .refreshable { users = (try? await AppDependencies.shared.container.getAdminUsers.invoke()) ?? users }
    }

    private func date(_ iso: String?) -> Date? { iso.flatMap(parseIsoLocalDateTime) }

    private var shown: [AdminUserRow]? {
        guard let users else { return nil }
        var list = users
        switch mode {
        case .active(let days):
            let cutoff = days == 1 ? Calendar.current.startOfDay(for: Date()) : Date().addingTimeInterval(-Double(days) * 86400)
            list = list.filter { (date($0.lastSeen) ?? .distantPast) >= cutoff }
            list.sort { (date($0.lastSeen) ?? .distantPast) > (date($1.lastSeen) ?? .distantPast) }
        case .all:
            if onlyNew {
                let cutoff = Date().addingTimeInterval(-7 * 86400)
                list = list.filter { (date($0.createdAt) ?? .distantPast) >= cutoff }
            }
            list.sort { (date($0.createdAt) ?? .distantPast) > (date($1.createdAt) ?? .distantPast) }
        }
        let q = query.trimmingCharacters(in: .whitespaces).lowercased().replacingOccurrences(of: "@", with: "")
        if !q.isEmpty {
            list = list.filter { ($0.username ?? "").lowercased().contains(q) || ($0.displayName ?? "").lowercased().contains(q) }
        }
        return list
    }

    private func row(_ u: AdminUserRow) -> some View {
        HStack(spacing: 12) {
            AvatarCircle(url: u.photoPath, size: 40, uid: u.uid)
                .onAppear { ai.want(u.uid) }
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(u.username.map { "@" + $0 } ?? (u.displayName ?? String(u.uid.prefix(10))))
                        .font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink).lineLimit(1)
                    if u.isAdmin { AdminChip(text: "ADMIN", color: Cumbre.terra) }
                }
                Text("Alta: \(dayLabel(u.createdAt))").font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                HStack(spacing: 5) {
                    let n = approved[u.uid] ?? 0
                    AdminChip(text: n == 1 ? "1 APORTACIÓN" : "\(n) APORTACIONES", color: n > 0 ? Cumbre.ok : Cumbre.ink3)
                    if u.isAdmin { AdminChip(text: "IA · ADMIN", color: Cumbre.terra) }
                    else if ai.flags[u.uid] == true { AdminChip(text: "IA ACTIVADA", color: Cumbre.terra, filled: true) }
                    else if ai.flags[u.uid] == false { AdminChip(text: "SIN IA", color: Cumbre.ink3) }
                }
            }
            Spacer(minLength: 6)
            VStack(alignment: .trailing, spacing: 2) {
                Text("ÚLTIMA VEZ").font(Cumbre.mono(8.5, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink3)
                Text(relativeLastSeen(u.lastSeen)).font(Cumbre.mono(10.5, .bold)).foregroundStyle(Cumbre.ink2)
            }
        }
        .adminCard()
    }

    private func dayLabel(_ iso: String?) -> String {
        guard let d = date(iso) else { return "sin registrar" }
        let f = DateFormatter(); f.locale = Locale(identifier: "es_ES"); f.dateFormat = "d MMM yyyy"
        return f.string(from: d)
    }
}

/// Notas de los usuarios en las escuelas, más recientes primero, con buscador.
struct AdminNotesScreen: View {
    @State private var notes: [AdminNoteRow]?
    @State private var query = ""

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 8) {
                AdminField(placeholder: "Buscar en las notas…", text: $query).padding(.top, 8)
                if let list = shown {
                    Text("\(list.count) NOTAS").font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink3).padding(.top, 4)
                    if list.isEmpty { AdminEmptyState(symbol: "note.text", text: "No hay notas.") }
                    ForEach(list, id: \.id) { n in
                        NavigationLink(destination: AdminNoteDetailView(note: n)) { card(n) }.buttonStyle(.plain)
                    }
                } else {
                    ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
                }
            }
            .padding(.horizontal, 16).padding(.bottom, 30)
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Notas")
        .navigationBarTitleDisplayMode(.inline)
        .task { if notes == nil { notes = (try? await AppDependencies.shared.container.getAdminNotes.invoke()) ?? [] } }
    }

    private var shown: [AdminNoteRow]? {
        guard let notes else { return nil }
        let sorted = notes.sorted { ($0.createdAt ?? "") > ($1.createdAt ?? "") }
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        return q.isEmpty ? sorted : sorted.filter {
            $0.text.lowercased().contains(q) || ($0.author ?? "").lowercased().contains(q) || ($0.schoolId ?? "").lowercased().contains(q)
        }
    }

    private func card(_ n: AdminNoteRow) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(n.text).font(.system(size: 14)).foregroundStyle(Cumbre.ink).lineLimit(4).multilineTextAlignment(.leading)
            HStack(spacing: 6) {
                if let a = n.author, !a.isEmpty { AdminChip(text: a.uppercased(), color: Cumbre.ink2) }
                if let s = n.schoolId, !s.isEmpty { AdminChip(text: s.replacingOccurrences(of: "-", with: " ").uppercased(), color: Cumbre.terra) }
                Spacer()
                Text(String((n.createdAt ?? "").prefix(10))).font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
            }
        }
        .adminCard()
    }
}

/// Tareas de mantenimiento puntuales (antes escondidas en las estadísticas antiguas).
struct AdminMaintenanceScreen: View {
    @State private var running = false
    @State private var result: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                AdminSectionLabel(text: "BUSCADOR POR GRADO").padding(.top, 12)
                VStack(alignment: .leading, spacing: 10) {
                    Text("Rellena el grado numérico de las vías antiguas para que el buscador las encuentre. Se puede repetir sin romper nada; las vías nuevas ya se rellenan solas.")
                        .font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
                    AdminButton(title: running ? "RELLENANDO…" : "RELLENAR GRADO DE VÍAS ANTIGUAS", symbol: "wrench.and.screwdriver",
                                busy: running) { run() }
                        .disabled(running)
                    if let result { Text(result).font(.system(size: 12)).foregroundStyle(Cumbre.ink2) }
                }
                .adminCard()
            }
            .padding(.horizontal, 16)
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Mantenimiento")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func run() {
        running = true
        Task {
            let r = try? await AppDependencies.shared.container.backfillGradeScore.invoke()
            running = false
            result = r.map { "Pendientes: \($0.total) · actualizadas: \($0.updated) · sin grado reconocible: \($0.unrecognized)" }
                ?? "Fallo al rellenar grade_score (revisa conexión)."
        }
    }
}
