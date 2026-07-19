import SwiftUI
import Shared
import CoreLocation

// Pestanas STATS / LOGS / PUSH del panel admin. Reparto de AdminView.swift.

struct AdminStatsTab: View {
    let stats: AdminStats?
    /// Cambia de pestaña (ESCUELAS → gestionar, PENDIENTES → propuestas).
    var onGoToTab: (AdminTab) -> Void = { _ in }
    @State private var openList: String? = nil
    @State private var users: [AdminUserRowDto]? = nil
    @State private var notes: [AdminNoteRowDto]? = nil

    var body: some View {
        ScrollView {
            if let s = stats {
                Text("Toca una tarjeta para ver su lista")
                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16).padding(.top, 10)
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                    card("USUARIOS", s.totalUsers) { openList = "users"; loadUsers() }
                    card("ADMINS", s.totalAdmins) { openList = "admins"; loadUsers() }
                    card("ESCUELAS", s.totalSchools) { onGoToTab(.gestionar) }
                    card("NOTAS", s.totalNotes) { openList = "notes"; loadNotes() }
                    card("PENDIENTES", s.submissionsPending) { onGoToTab(.propuestas) }
                    card("APROBADAS", s.submissionsApproved) { onGoToTab(.actividad) }
                    card("RECHAZADAS", s.submissionsRejected) { onGoToTab(.actividad) }
                }.padding(16)
            } else {
                ProgressView().padding(.top, 40)
            }
        }
        .sheet(isPresented: Binding(get: { openList != nil }, set: { if !$0 { openList = nil } })) {
            listSheet
        }
    }

    private func loadUsers() {
        guard users == nil else { return }
        Task { users = (try? await AppDependencies.shared.container.moderationApi.getAdminUsers()) ?? [] }
    }
    private func loadNotes() {
        guard notes == nil else { return }
        Task { notes = (try? await AppDependencies.shared.container.moderationApi.getAdminNotes()) ?? [] }
    }

    @ViewBuilder private var listSheet: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    if openList == "notes" {
                        if let list = notes {
                            ForEach(list, id: \.id) { n in
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(n.text).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                                    Text([n.author, n.schoolId, (n.createdAt ?? "").isEmpty ? nil : String((n.createdAt ?? "").prefix(10))]
                                            .compactMap { $0 }.joined(separator: " - "))
                                        .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                                }
                                .padding(.vertical, 8)
                                Divider().overlay(Cumbre.rule)
                            }
                        } else { ProgressView().padding(.top, 30) }
                    } else {
                        if let list = users {
                            let shown = openList == "admins" ? list.filter { $0.isAdmin } : list
                            ForEach(shown, id: \.uid) { u in
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

    private func card(_ label: String, _ value: Int64, action: @escaping () -> Void = {}) -> some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Text("\(value)").font(Cumbre.serif(28, .bold)).foregroundStyle(Cumbre.ink)
                Text(label).font(Cumbre.mono(10, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink3)
            }
            .frame(maxWidth: .infinity).padding(.vertical, 16)
            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
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
                    .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity).background(Cumbre.terra)
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
