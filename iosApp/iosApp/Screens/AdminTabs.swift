import SwiftUI
import Shared
import CoreLocation

// Pestanas STATS / LOGS / PUSH del panel admin. Reparto de AdminView.swift.

struct AdminStatsTab: View {
    let stats: AdminStats?
    /// Cambia de pestaña (ESCUELAS → gestionar, PENDIENTES → propuestas).
    var onGoToTab: (AdminTab) -> Void = { _ in }
    @State private var openList: String? = nil
    @State private var users: [AdminUserRow]? = nil
    @State private var notes: [AdminNoteRow]? = nil
    /// "Quién entró" — al pulsar cualquiera de las 3 tarjetas de actividad, o
    /// la franja de aperturas totales (Álvaro, 2026-09-30).
    @State private var showDailyActivity = false
    /// "ver propuestas pasadas" — al pulsar APROBADAS/RECHAZADAS (Álvaro, 2026-09-30).
    @State private var historyStatus: String? = nil

    var body: some View {
        ScrollView {
            if let s = stats {
                Text("Toca una tarjeta para ver su lista")
                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16).padding(.top, 10)

                Text("ACTIVIDAD").font(Cumbre.mono(10, .bold)).tracking(0.8)
                    .foregroundStyle(Cumbre.terra)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16).padding(.top, 12)
                HStack(spacing: 8) {
                    activityCard("USUARIOS HOY", s.dailyActiveUsers)
                    activityCard("7 DÍAS", s.weeklyActiveUsers)
                    activityCard("30 DÍAS", s.monthlyActiveUsers)
                }
                .padding(.horizontal, 16).padding(.top, 6)
                .onTapGesture { showDailyActivity = true }

                Button { showDailyActivity = true } label: {
                    HStack(alignment: .lastTextBaseline, spacing: 6) {
                        Text("\(s.dailyOpensTotal)").font(Cumbre.serif(16, .bold)).foregroundStyle(Cumbre.ink)
                        Text("aperturas de la app HOY").font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                        Spacer()
                        Text("VER QUIÉN ▸").font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra)
                    }
                    .padding(10)
                    .background(Cumbre.paper).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 4)

                Text("CATÁLOGO").font(Cumbre.mono(10, .bold)).tracking(0.8)
                    .foregroundStyle(Cumbre.ink3)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16).padding(.top, 12)
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                    card("USUARIOS", s.totalUsers) { openList = "users"; loadUsers() }
                    card("ADMINS", s.totalAdmins) { openList = "admins"; loadUsers() }
                    card("ESCUELAS", s.totalSchools) { onGoToTab(.gestionar) }
                    card("NOTAS", s.totalNotes) { openList = "notes"; loadNotes() }
                    // Iluminada en terracota si hay pendientes — mismo criterio
                    // que la cabecera de PROPUESTAS (Álvaro, 2026-09-30).
                    card("PENDIENTES", s.submissionsPending, highlighted: s.submissionsPending > 0) { onGoToTab(.propuestas) }
                    card("APROBADAS", s.submissionsApproved) { historyStatus = "APPROVED" }
                    card("RECHAZADAS", s.submissionsRejected) { historyStatus = "REJECTED" }
                }.padding(16)
            } else {
                ProgressView().padding(.top, 40)
            }
        }
        .sheet(isPresented: Binding(get: { openList != nil }, set: { if !$0 { openList = nil } })) {
            listSheet
        }
        .sheet(isPresented: $showDailyActivity) { DailyActivityView() }
        .sheet(isPresented: Binding(get: { historyStatus != nil }, set: { if !$0 { historyStatus = nil } })) {
            if let status = historyStatus { SubmissionsHistoryView(status: status) }
        }
    }

    private func activityCard(_ label: String, _ value: Int64) -> some View {
        VStack(spacing: 4) {
            Text("\(value)").font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.terra)
            Text(label).font(Cumbre.mono(8.5, .bold)).tracking(0.4).foregroundStyle(Cumbre.terra.opacity(0.85))
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 12)
        .background(Cumbre.terraBg).overlay(Rectangle().stroke(Cumbre.terra, lineWidth: 1))
    }

    private func loadUsers() {
        guard users == nil else { return }
        Task { users = (try? await AppDependencies.shared.container.getAdminUsers.invoke()) ?? [] }
    }
    private func loadNotes() {
        guard notes == nil else { return }
        Task { notes = (try? await AppDependencies.shared.container.getAdminNotes.invoke()) ?? [] }
    }

    @ViewBuilder private var listSheet: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    if openList == "notes" {
                        if let list = notes {
                            ForEach(list, id: \.id) { n in
                                // P6: la nota abre su ESCUELA.
                                Group {
                                    // R12: la nota abre su DETALLE (texto
                                    // entero + VER ESCUELA), no la escuela a
                                    // secas donde no se veía la nota.
                                    NavigationLink(destination: AdminNoteDetailView(note: n)) {
                                        adminNoteRow(n)
                                    }.buttonStyle(.plain)
                                }
                                Divider().overlay(Cumbre.rule)
                            }
                        } else { ProgressView().padding(.top, 30) }
                    } else {
                        if let list = users {
                            let shown = openList == "admins" ? list.filter { $0.isAdmin } : list
                            ForEach(shown, id: \.uid) { u in
                                // P6: la fila abre el PERFIL del usuario.
                                NavigationLink(destination: PublicProfileView(uid: u.uid)) {
                                HStack {
                                    AvatarCircle(url: u.photoPath, size: 32)
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(u.username.map { "@" + $0 } ?? (u.displayName ?? String(u.uid.prefix(10))))
                                            .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                                        if u.isAdmin {
                                            Text("ADMIN").font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.terra)
                                        }
                                    }
                                    Spacer()
                                    Text(relativeLastSeen(u.lastSeen))
                                        .font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                                }
                                }.buttonStyle(.plain)
                                .padding(.vertical, 8)
                                Divider().overlay(Cumbre.rule)
                            }
                        } else { ProgressView().padding(.top, 30) }
                    }
                }
                .padding(.horizontal, 16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle(openList == "notes" ? "Notas" : (openList == "admins" ? "Admins" : "Usuarios"))
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func card(_ label: String, _ value: Int64, highlighted: Bool = false, action: @escaping () -> Void = {}) -> some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Text("\(value)").font(Cumbre.serif(28, .bold)).foregroundStyle(highlighted ? Cumbre.terra : Cumbre.ink)
                Text(label).font(Cumbre.mono(10, .bold)).tracking(0.8).foregroundStyle(highlighted ? Cumbre.terra.opacity(0.85) : Cumbre.ink3)
            }
            .frame(maxWidth: .infinity).padding(.vertical, 16)
            .background(highlighted ? Cumbre.terraBg : Color.clear)
            .overlay(Rectangle().stroke(highlighted ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// "hace 12 min" / "ayer, 21:04" / "hace 6 días" / "sin registrar" — última
/// conexión de un usuario en el panel de admin (Álvaro, 2026-09-30).
func relativeLastSeen(_ iso: String?) -> String {
    guard let iso, let date = parseIsoLocalDateTime(iso) else { return "sin registrar" }
    let seconds = Date().timeIntervalSince(date)
    if seconds < 60 { return "ahora mismo" }
    if seconds < 3600 { return "hace \(Int(seconds / 60)) min" }
    if Calendar.current.isDateInToday(date) {
        let f = DateFormatter(); f.dateFormat = "HH:mm"
        return "hoy, \(f.string(from: date))"
    }
    if Calendar.current.isDateInYesterday(date) {
        let f = DateFormatter(); f.dateFormat = "HH:mm"
        return "ayer, \(f.string(from: date))"
    }
    let days = Int(seconds / 86400)
    if days < 30 { return "hace \(days) día\(days == 1 ? "" : "s")" }
    let months = days / 30
    if months < 12 { return "hace \(months) mes\(months == 1 ? "" : "es")" }
    return "hace más de un año"
}

/// El backend manda LocalDateTime sin zona ("2026-09-30T12:34:56"), en UTC
/// (hora del servidor) — se interpreta como tal, igual que el resto de la app.
private func parseIsoLocalDateTime(_ s: String) -> Date? {
    let f = ISO8601DateFormatter()
    f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    if let d = f.date(from: s + "Z") { return d }
    f.formatOptions = [.withInternetDateTime]
    if let d = f.date(from: s + "Z") { return d }
    let df = DateFormatter()
    df.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
    df.timeZone = TimeZone(identifier: "UTC")
    return df.date(from: s)
}

/// "Quién entró" un día concreto — usuarios + cuántas veces cada uno, y el
/// total de aperturas de ese día (Álvaro, 2026-09-30).
struct DailyActivityView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var day = Date()
    @State private var activity: DailyActivity?
    @State private var loading = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                HStack {
                    Button { changeDay(by: -1) } label: {
                        Image(systemName: "chevron.left").foregroundStyle(Cumbre.ink3)
                    }
                    Spacer()
                    Text(dayLabel).font(Cumbre.serif(16, .bold)).foregroundStyle(Cumbre.ink)
                    Spacer()
                    Button { changeDay(by: 1) } label: {
                        Image(systemName: "chevron.right")
                            .foregroundStyle(isToday ? Cumbre.ink3.opacity(0.3) : Cumbre.ink3)
                    }.disabled(isToday)
                }
                .padding(.horizontal, 16).padding(.vertical, 12)
                Divider().overlay(Cumbre.rule)

                if let a = activity {
                    HStack(alignment: .lastTextBaseline, spacing: 14) {
                        HStack(alignment: .lastTextBaseline, spacing: 4) {
                            Text("\(a.users.count)").font(Cumbre.serif(24, .bold)).foregroundStyle(Cumbre.terra)
                            Text("usuarios").font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                        }
                        HStack(alignment: .lastTextBaseline, spacing: 4) {
                            Text("\(a.totalOpens)").font(Cumbre.serif(18, .bold)).foregroundStyle(Cumbre.ink)
                            Text("aperturas totales").font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                        }
                        Spacer()
                    }
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    Divider().overlay(Cumbre.rule)

                    if a.users.isEmpty {
                        Text("Nadie abrió la app ese día.")
                            .font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                            .padding(.top, 30)
                        Spacer()
                    } else {
                        ScrollView {
                            LazyVStack(spacing: 0) {
                                ForEach(a.users, id: \.uid) { u in
                                    NavigationLink(destination: PublicProfileView(uid: u.uid)) {
                                        HStack(spacing: 10) {
                                            Text(u.username.map { "@" + $0 } ?? (u.displayName ?? String(u.uid.prefix(10))))
                                                .font(.system(size: 13)).foregroundStyle(Cumbre.ink)
                                            if u.openCount > 1 {
                                                Text("×\(u.openCount)")
                                                    .font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.terra)
                                                    .padding(.horizontal, 7).padding(.vertical, 2)
                                                    .background(Cumbre.terraBg)
                                                    .clipShape(Capsule())
                                            }
                                            Spacer()
                                            Text(relativeLastSeen(u.lastSeen))
                                                .font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                                        }
                                        .padding(.horizontal, 16).padding(.vertical, 9)
                                    }
                                    .buttonStyle(.plain)
                                    Divider().overlay(Cumbre.rule)
                                }
                            }
                        }
                    }
                } else if loading {
                    ProgressView().padding(.top, 40)
                    Spacer()
                }
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Quién entró")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundColor(Cumbre.terra)
                }
            }
            .task(id: day) { await load() }
        }
    }

    private var isToday: Bool { Calendar.current.isDateInToday(day) }
    private var dayLabel: String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "es_ES")
        f.dateFormat = isToday ? "'Hoy'" : "EEEE, d MMM"
        return f.string(from: day).capitalized
    }
    private func changeDay(by deltaDays: Int) {
        guard let newDay = Calendar.current.date(byAdding: .day, value: deltaDays, to: day) else { return }
        day = min(newDay, Date())
    }
    private func load() async {
        loading = true
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"
        let iso = f.string(from: day)
        activity = try? await AppDependencies.shared.container.getDailyActivity.invoke(date: iso)
        loading = false
    }
}

/// Tab ACTIVIDAD — registro de acciones admin (espejo de ActivityTab).
struct AdminLogsTab: View {
    let logs: [AdminLog]
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                if logs.isEmpty {
                    Text("Sin actividad reciente.").font(.system(size: 14))
                        .foregroundStyle(Cumbre.ink3).padding(24)
                }
                ForEach(logs, id: \.id) { l in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(l.action).font(Cumbre.mono(13, .bold)).foregroundStyle(Cumbre.ink)
                        Text("\(l.targetType)/\(l.targetId)").font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                        if let d = l.details, !d.isEmpty {
                            Text(d).font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
                        }
                        Text(String(l.createdAt.prefix(16))).font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    Divider().overlay(Cumbre.rule)
                }
            }
        }
    }
}

/// Tab PUSH — envío manual de notificación (espejo de PushTab).
struct AdminPushTab: View {
    @ObservedObject var vm: AdminViewModel
    @State private var targetUid: String? = nil
    @State private var targetLabel: String? = nil
    @State private var query = ""
    @State private var results: [PublicProfile] = []
    @State private var searchTask: Task<Void, Never>? = nil
    @State private var title = ""
    @State private var body_ = ""
    @State private var confirmAll = false
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let uid = targetUid {
                    HStack(spacing: 8) {
                        Text("PARA: " + (targetLabel ?? uid))
                            .font(Cumbre.mono(11, .bold)).tracking(0.6)
                            .foregroundStyle(Cumbre.terra)
                        Button("✕ QUITAR") { targetUid = nil; targetLabel = nil }
                            .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink3)
                            .buttonStyle(.plain)
                    }
                } else {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("DESTINATARIO").eyebrow()
                        TextField("Buscar por @usuario o nombre…", text: $query)
                            .font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                            .padding(10).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                            .onChange(of: query) { _, q in
                                searchTask?.cancel()
                                let t = q.trimmingCharacters(in: .whitespaces)
                                guard t.count >= 2 else { results = []; return }
                                searchTask = Task {
                                    try? await Task.sleep(nanoseconds: 250_000_000)
                                    guard !Task.isCancelled else { return }
                                    results = (try? await AppDependencies.shared.container.searchUsers.invoke(query: t, limit: 10)) ?? []
                                }
                            }
                        ForEach(results.prefix(6), id: \.uid) { u in
                            Button {
                                targetUid = u.uid
                                targetLabel = u.username.map { "@" + $0 } ?? u.displayName
                                query = ""; results = []
                            } label: {
                                Text(u.username.map { "@" + $0 } ?? (u.displayName ?? u.uid))
                                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.horizontal, 10).padding(.vertical, 8)
                                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                            }.buttonStyle(.plain)
                        }
                        Text("Sin destinatario → se enviará a TODOS los usuarios.")
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    }
                }
                field("TÍTULO", $title, "Título")
                VStack(alignment: .leading, spacing: 6) {
                    Text("MENSAJE").eyebrow()
                    TextField("Mensaje", text: $body_, axis: .vertical).lineLimit(3...6)
                        .font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                        .padding(10).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                }
                Button {
                    if targetUid == nil { confirmAll = true }
                    else { vm.sendPush(targetUid: targetUid, title: title, body: body_) }
                } label: {
                    HStack { if vm.pushBusy { ProgressView().tint(.white) }
                        Text(targetUid == nil ? "ENVIAR A TODOS LOS USUARIOS" : "ENVIAR PUSH")
                            .font(Cumbre.mono(13, .bold)).tracking(0.8) }
                    .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity).background(Cumbre.terraFill)
                }.buttonStyle(.plain)
                .disabled(vm.pushBusy || title.isEmpty || body_.isEmpty)
                .alert("¿Enviar a TODOS?", isPresented: $confirmAll) {
                    Button("Cancelar", role: .cancel) {}
                    Button("Sí, a todos", role: .destructive) {
                        vm.sendPush(targetUid: nil, title: title, body: body_)
                    }
                } message: {
                    Text("El push llegará a todos los usuarios de Cumbre.")
                }
                if let r = vm.pushResult {
                    Text(r).font(Cumbre.mono(12)).foregroundStyle(Cumbre.ink2)
                }
            }.padding(16)
        }
    }
    private func field(_ label: String, _ text: Binding<String>, _ ph: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).eyebrow()
            TextField(ph, text: text).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                .padding(10).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }
    }
}


/// Fila de nota del panel admin (P6: pulsable → su escuela).
@ViewBuilder
fileprivate func adminNoteRow(_ n: AdminNoteRow) -> some View {
    VStack(alignment: .leading, spacing: 2) {
        Text(n.text).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
        Text([n.author, n.schoolId, (n.createdAt ?? "").isEmpty ? nil : String((n.createdAt ?? "").prefix(10))]
                .compactMap { $0 }.joined(separator: " - "))
            .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
        Text("Ver escuela ›").font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.terra)
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .contentShape(Rectangle())
    .padding(.vertical, 8)
}


/// R12: detalle de una nota del panel admin — se LEE entera y desde aquí
/// se salta a su escuela si hace falta.
struct AdminNoteDetailView: View {
    let note: AdminNoteRow
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("NOTA").font(Cumbre.mono(10, .bold)).tracking(1.5)
                    .foregroundStyle(Cumbre.terra)
                Text(note.text).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                Text([note.author, note.schoolId,
                      (note.createdAt ?? "").isEmpty ? nil : String((note.createdAt ?? "").prefix(10))]
                        .compactMap { $0 }.joined(separator: " · "))
                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                if let sid = note.schoolId, !sid.isEmpty {
                    NavigationLink(destination: SchoolLoaderView(schoolId: sid)) {
                        Text("VER ESCUELA ▸").font(Cumbre.mono(10, .bold)).tracking(1)
                            .foregroundStyle(Cumbre.terra)
                            .padding(.vertical, 8)
                    }
                    .buttonStyle(.plain)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Nota")
        .navigationBarTitleDisplayMode(.inline)
    }
}
