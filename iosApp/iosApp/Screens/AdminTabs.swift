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

    var body: some View {
        ScrollView {
            if let s = stats {
                VStack(alignment: .leading, spacing: 0) {
                    if s.submissionsPending > 0 {
                        Button { onGoToTab(.propuestas) } label: {
                            HStack(spacing: 12) {
                                Text("⏳").font(.system(size: 26))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("PENDIENTE DE REVISAR").font(Cumbre.mono(10, .bold)).tracking(0.8).opacity(0.85)
                                    Text("\(s.submissionsPending) propuesta\(s.submissionsPending == 1 ? "" : "s")")
                                        .font(Cumbre.serif(22, .bold))
                                    Text("Toca para ir directo a revisarlas →").font(.system(size: 11.5)).opacity(0.9)
                                }
                                Spacer()
                            }
                            .foregroundStyle(.white)
                            .padding(16)
                            .background(Cumbre.terraFill)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                        }.buttonStyle(.plain)
                        .padding(.horizontal, 16).padding(.top, 12)
                    }

                    sectionLabel("Comunidad")
                    grid {
                        card("USUARIOS", s.totalUsers, "👤", Cumbre.rain) { openList = "users"; loadUsers() }
                        card("ADMINS", s.totalAdmins, "🛡️", Cumbre.terra) { openList = "admins"; loadUsers() }
                    }
                    sectionLabel("Contenido")
                    grid {
                        card("ESCUELAS", s.totalSchools, "🧗", Cumbre.rain) { onGoToTab(.gestionar) }
                        card("NOTAS", s.totalNotes, "📓", Cumbre.rain) { openList = "notes"; loadNotes() }
                    }
                    sectionLabel("Moderación")
                    grid {
                        card("APROBADAS", s.submissionsApproved, "✔️", Cumbre.ok) { onGoToTab(.actividad) }
                        card("RECHAZADAS", s.submissionsRejected, "✕", Cumbre.bad) { onGoToTab(.actividad) }
                    }
                }
                .padding(.bottom, 16)
            } else {
                ProgressView().padding(.top, 40)
            }
        }
        .sheet(isPresented: Binding(get: { openList != nil }, set: { if !$0 { openList = nil } })) {
            listSheet
        }
    }

    private func sectionLabel(_ t: String) -> some View {
        Text(t.uppercased()).font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink3)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16).padding(.top, 16).padding(.bottom, 6)
    }

    private func grid<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) { content() }
            .padding(.horizontal, 16)
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
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(u.username.map { "@" + $0 } ?? (u.displayName ?? String(u.uid.prefix(10))))
                                            .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                                        if u.isAdmin {
                                            Text("ADMIN").font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.terra)
                                        }
                                    }
                                    Spacer()
                                    Text(String((u.createdAt ?? "").prefix(10)))
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

    /// Tarjeta con icono + color por tema (Comunidad/Contenido/Moderación),
    /// para que de un vistazo se sepa a qué grupo pertenece cada cifra —
    /// antes era una rejilla neutra, todo del mismo color (Álvaro, 2026-09-06).
    private func card(_ label: String, _ value: Int64, _ icon: String, _ tint: Color,
                       action: @escaping () -> Void = {}) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 6) {
                Text(icon).font(.system(size: 18))
                Text("\(value)").font(Cumbre.serif(22, .bold)).foregroundStyle(tint)
                Text(label).font(Cumbre.mono(9.5, .bold)).tracking(0.5).foregroundStyle(tint)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(tint.opacity(0.12))
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Tab ACTIVIDAD — historial de propuestas ya resueltas (aprobadas/rechazadas),
/// con quién las mandó — antes "aprobadas"/"rechazadas" eran solo un número en
/// STATS, sin poder ver el detalle (Álvaro, 2026-09-06). Reutiliza las mismas
/// tarjetas que PROPUESTAS, así que ya traen nombre/mensaje/historial del
/// autor de serie.
struct AdminActivityTab: View {
    @ObservedObject var vm: AdminViewModel

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 6) {
                statusChip("APROBADAS", "APPROVED")
                statusChip("RECHAZADAS", "REJECTED")
            }.padding(12)
            Divider().overlay(Cumbre.rule)
            if vm.activityLoading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if vm.activitySubmissions.isEmpty && vm.activityContributions.isEmpty {
                Text("Nada por aquí todavía.")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(vm.activitySubmissions, id: \.id) { s in
                            SubmissionAdminCard(submission: s, busy: false, onApprove: {}, onReject: {})
                            Divider().overlay(Cumbre.rule)
                        }
                        ForEach(vm.activityContributions, id: \.id) { c in
                            ContributionAdminCard(contribution: c, busy: false, onApprove: {}, onReject: {})
                            Divider().overlay(Cumbre.rule)
                        }
                    }
                }
            }
        }
    }

    private func statusChip(_ label: String, _ value: String) -> some View {
        let on = vm.activityStatus == value
        return Button {
            Task { await vm.loadActivity(status: value) }
        } label: {
            Text(label).font(Cumbre.mono(11, .bold)).tracking(0.6)
                .foregroundStyle(on ? .white : Cumbre.ink2)
                .padding(.horizontal, 12).padding(.vertical, 7)
                .background(on ? (value == "APPROVED" ? Cumbre.ok : Cumbre.bad) : Color.clear)
                .overlay(Rectangle().stroke(on ? (value == "APPROVED" ? Cumbre.ok : Cumbre.bad) : Cumbre.rule, lineWidth: 1))
        }.buttonStyle(.plain)
    }
}

/// Registro de acciones admin (espejo de ActivityTab de Android) — ya no está
/// enganchado a ninguna pestaña (sustituido por AdminActivityTab), se deja
/// por si hace falta retomarlo.
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
