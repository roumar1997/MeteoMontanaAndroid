import SwiftUI
import Shared
import CoreLocation

// Pestana DENUNCIAS + ficha de moderacion de usuario. Reparto de AdminView.swift.

struct ModTarget: Identifiable { let id = UUID(); let uid: String }
struct MeetupTarget: Identifiable { let id = UUID(); let meetupId: String }
struct FeedPostTarget: Identifiable { let id = UUID(); let postId: String }

struct AdminReportsTab: View {
    @State private var contentReports: [ContentReportDto] = []
    @State private var meetupReports: [MeetupReport] = []
    @State private var modTarget: ModTarget? = nil
    @State private var meetupTarget: MeetupTarget? = nil
    @State private var feedPostTarget: FeedPostTarget? = nil

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                if contentReports.isEmpty && meetupReports.isEmpty {
                    Text("Sin denuncias pendientes")
                        .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                        .frame(maxWidth: .infinity).padding(.top, 40)
                }
                if !contentReports.isEmpty {
                    Text("CONTENIDO (comentarios / notas / usuarios)")
                        .font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink3)
                    ForEach(contentReports, id: \.id) { r in
                        contentCard(r)
                    }
                }
                if !meetupReports.isEmpty {
                    Text("QUEDADAS")
                        .font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink3)
                        .padding(.top, 6)
                    ForEach(meetupReports, id: \.id) { r in
                        meetupCard(r)
                    }
                }
            }
            .padding(16)
        }
        .background(Cumbre.bg)
        .refreshable { await load() }   // tirar para refrescar la cola
        .task { await load() }
        .sheet(item: $modTarget) { t in
            UserModerationSheet(uid: t.uid)
        }
        .fullScreenCover(item: $meetupTarget) { t in
            NavigationStack {
                MeetupDetailView(meetupId: t.meetupId)
                    .toolbar {
                        ToolbarItem(placement: .topBarLeading) {
                            Button("CERRAR") { meetupTarget = nil }
                                .font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.terra)
                        }
                    }
            }
        }
        // Denuncias del feed: VER POST abre el detalle del post (para
        // FEED_COMMENT también; el snapshot ya enseña el texto del comentario).
        .fullScreenCover(item: $feedPostTarget) { t in
            NavigationStack {
                FeedPostDetailView(postIdString: t.postId)
                    .toolbar {
                        ToolbarItem(placement: .topBarLeading) {
                            Button("CERRAR") { feedPostTarget = nil }
                                .font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.terra)
                        }
                    }
            }
        }
    }

    private func load() async {
        let c = AppDependencies.shared.container
        contentReports = (try? await c.moderationApi.getContentReports()) ?? []
        meetupReports = (try? await c.getPendingReports.invoke()) ?? []
    }

    private func resolveContent(_ id: String, _ action: String) {
        Task {
            _ = try? await AppDependencies.shared.container.moderationApi
                .resolveContentReport(id: id, action: action)
            await load()
        }
    }

    private func resolveMeetup(_ id: String, _ action: String) {
        Task {
            _ = try? await AppDependencies.shared.container.resolveReport.invoke(id: id, action: action)
            await load()
        }
    }

    @ViewBuilder private func contentCard(_ r: ContentReportDto) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(kindLabel(r.targetType) + " - " + reasonLabel(r.reason))
                    .font(.system(size: 13, weight: .bold)).foregroundStyle(Cumbre.bad)
                Spacer()
                Text(String((r.createdAt ?? "").prefix(10)))
                    .font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
            }
            Text(r.snapshot ?? "(contenido no disponible)")
                .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
            HStack(spacing: 8) {
                Button { resolveContent(r.id, "REMOVE") } label: {
                    Text(r.targetType == "USER" ? "MARCAR REVISADO" : "RETIRAR CONTENIDO")
                        .font(Cumbre.mono(11, .bold)).tracking(0.5).foregroundStyle(Cumbre.bad)
                        .frame(maxWidth: .infinity).padding(.vertical, 9)
                        .overlay(Rectangle().stroke(Cumbre.bad, lineWidth: 1))
                }.buttonStyle(.plain)
                Button { resolveContent(r.id, "IGNORE") } label: {
                    Text("IGNORAR")
                        .font(Cumbre.mono(11, .bold)).tracking(0.5).foregroundStyle(Cumbre.ink3)
                        .frame(maxWidth: .infinity).padding(.vertical, 9)
                        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                }.buttonStyle(.plain)
            }
            // VER POST: denuncias del feed (post o comentario) — patrón VER QUEDADA.
            if r.targetType == "FEED_POST" || r.targetType == "FEED_COMMENT" {
                Button { feedPostTarget = FeedPostTarget(postId: r.targetId) } label: {
                    Text("VER POST ▸").font(Cumbre.mono(11, .bold)).tracking(0.5)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 9)
                        .background(Cumbre.terra)
                }.buttonStyle(.plain)
            }
            if let author = r.authorUid {
                Button { modTarget = ModTarget(uid: author) } label: {
                    Text("VER AUTOR ▸").font(Cumbre.mono(10, .bold)).tracking(0.8)
                        .foregroundStyle(Cumbre.terra)
                        .frame(maxWidth: .infinity).padding(.vertical, 6)
                }.buttonStyle(.plain)
            }
        }
        .padding(12)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }

    @ViewBuilder private func meetupCard(_ r: MeetupReport) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(reasonLabel(String(describing: r.reason)) + " - quedada " + String(r.meetupId.prefix(8)))
                .font(.system(size: 13, weight: .bold)).foregroundStyle(Cumbre.bad)
            if let ctx = r.context, !ctx.isEmpty {
                Text(ctx).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
            }
            // Ver la quedada entera para juzgar por qué la denunciaron.
            Button { meetupTarget = MeetupTarget(meetupId: r.meetupId) } label: {
                Text("VER QUEDADA ▸").font(Cumbre.mono(11, .bold)).tracking(0.5)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 9)
                    .background(Cumbre.terra)
            }.buttonStyle(.plain)
            // Acción principal: ELIMINAR la quedada denunciada.
            Button { resolveMeetup(r.id, "delete") } label: {
                Text("ELIMINAR QUEDADA").font(Cumbre.mono(11, .bold)).tracking(0.5)
                    .foregroundStyle(Cumbre.bad)
                    .frame(maxWidth: .infinity).padding(.vertical, 9)
                    .overlay(Rectangle().stroke(Cumbre.bad, lineWidth: 1))
            }.buttonStyle(.plain)
            if let author = r.reportedUid {
                Button { modTarget = ModTarget(uid: author) } label: {
                    Text("VER AUTOR ▸").font(Cumbre.mono(10, .bold)).tracking(0.8)
                        .foregroundStyle(Cumbre.terra)
                        .frame(maxWidth: .infinity).padding(.vertical, 6)
                }.buttonStyle(.plain)
            }
            HStack(spacing: 8) {
                Button { resolveMeetup(r.id, "resolve") } label: {
                    Text("OK, REVISADA").font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink3)
                        .frame(maxWidth: .infinity).padding(.vertical, 9)
                        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                }.buttonStyle(.plain)
                Button { resolveMeetup(r.id, "dismiss") } label: {
                    Text("DESESTIMAR").font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink3)
                        .frame(maxWidth: .infinity).padding(.vertical, 9)
                        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                }.buttonStyle(.plain)
            }
        }
        .padding(12)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }

    private func kindLabel(_ t: String) -> String {
        switch t {
        case "COMMENT": return "COMENTARIO"
        case "NOTE": return "NOTA"
        case "FEED_POST": return "POST DEL FEED"
        case "FEED_COMMENT": return "COMENTARIO DEL FEED"
        default: return "USUARIO"
        }
    }
    private func reasonLabel(_ r: String) -> String {
        switch r.uppercased() {
        case "SPAM": return "Spam"; case "OFFENSIVE": return "Ofensivo"
        case "FALSE_INFO": return "Info falsa"; case "HARASSMENT": return "Acoso"
        case "INAPPROPRIATE": return "Inapropiado"; default: return "Otro" }
    }
}

/// Ficha de moderación de un usuario (VER AUTOR): historial de denuncias +
/// consecuencias (aviso, suspensión temporal, baneo de login reversible).
struct UserModerationSheet: View {
    let uid: String
    @Environment(\.dismiss) private var dismiss
    @State private var mod: UserModerationDto? = nil
    @State private var loading = true
    @State private var reason = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                if loading || mod == nil {
                    ProgressView().padding(.top, 40)
                } else if let m = mod {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(m.username.map { "@" + $0 } ?? (m.displayName ?? String(m.uid.prefix(10))))
                            .font(Cumbre.serif(20, .semibold)).foregroundStyle(Cumbre.ink)
                        Text(statusLine(m))
                            .font(.system(size: 12))
                            .foregroundStyle(m.banned ? Cumbre.bad : Cumbre.ink3)

                        Divider().overlay(Cumbre.rule)
                        Text("HISTORIAL DE DENUNCIAS")
                            .font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink3)
                        if m.reports.isEmpty {
                            Text("Sin denuncias de contenido registradas.")
                                .font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                        } else {
                            ForEach(Array(m.reports.prefix(8).enumerated()), id: \.offset) { _, rep in
                                VStack(alignment: .leading, spacing: 1) {
                                    Text("\(rep.type) · \(rep.reason)")
                                        .font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.bad)
                                    if let s = rep.snapshot {
                                        Text(s).font(.system(size: 13)).foregroundStyle(Cumbre.ink).lineLimit(2)
                                    }
                                }
                            }
                        }

                        // Acciones ya aplicadas (auditoría con motivo).
                        if !m.actions.isEmpty {
                            Divider().overlay(Cumbre.rule)
                            Text("ACCIONES APLICADAS")
                                .font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink3)
                            ForEach(Array(m.actions.prefix(8).enumerated()), id: \.offset) { _, act in
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(actionLabel(act.action) + (act.createdAt.map { " · " + String($0.prefix(10)) } ?? ""))
                                        .font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.ink)
                                    if let rs = act.reason, !rs.isEmpty {
                                        Text("Motivo: \(rs)").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                                    }
                                    if let s = act.snapshot, !s.isEmpty {
                                        Text(s).font(.system(size: 12)).foregroundStyle(Cumbre.ink3).lineLimit(2)
                                    }
                                }
                            }
                        }

                        Divider().overlay(Cumbre.rule)
                        Text("CONSECUENCIAS")
                            .font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink3)
                        TextField("Motivo (se guarda para pruebas)", text: $reason, axis: .vertical)
                            .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                            .padding(10).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                        actionButton("ENVIAR AVISO", Cumbre.terra) {
                            apply { (try? await api().warnUser(uid: uid, reason: reasonOrNil())) ?? nil }
                        }
                        HStack(spacing: 8) {
                            actionButton("SUSPENDER 7 D", Cumbre.terra) {
                                apply { (try? await api().suspendUser(uid: uid, days: 7, reason: reasonOrNil())) ?? nil }
                            }
                            actionButton("SUSPENDER 30 D", Cumbre.terra) {
                                apply { (try? await api().suspendUser(uid: uid, days: 30, reason: reasonOrNil())) ?? nil }
                            }
                        }
                        if m.banned {
                            actionButton("DESBANEAR", Cumbre.terra) {
                                apply { (try? await api().unbanUser2(uid: uid, reason: reasonOrNil())) ?? nil }
                            }
                        } else {
                            actionButton("BANEAR CUENTA", Cumbre.bad) {
                                apply { (try? await api().banUser2(uid: uid, reason: reasonOrNil())) ?? nil }
                            }
                        }
                    }
                    .padding(16)
                }
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Moderación")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cerrar") { dismiss() }.foregroundStyle(Cumbre.terra)
                }
            }
        }
        .task { await reload() }
    }

    private func statusLine(_ m: UserModerationDto) -> String {
        var s = "\(m.reportCount) denuncia(s) · \(m.warnings) aviso(s)"
        if m.banned { s += " · BANEADO" }
        if let su = m.suspendedUntil { s += " · suspendido hasta \(String(su.prefix(10)))" }
        return s
    }

    private func api() -> KtorModerationApi { AppDependencies.shared.container.moderationApi }

    private func reasonOrNil() -> String? {
        let t = reason.trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
    private func actionLabel(_ a: String) -> String {
        switch a {
        case "WARN": return "Aviso"; case "SUSPEND": return "Suspensión"
        case "BAN": return "Baneo"; case "UNBAN": return "Desbaneo"
        case "DELETE_NOTE": return "Nota borrada"; case "DELETE_COMMENT": return "Comentario borrado"
        case "DELETE_MEETUP": return "Quedada borrada"; default: return a }
    }

    private func reload() async {
        loading = true
        mod = (try? await api().getUserModeration(uid: uid)) ?? nil
        loading = false
    }

    private func apply(_ op: @escaping () async -> UserModerationDto?) {
        Task {
            let updated = await op()
            if let updated { mod = updated } else { await reload() }
        }
    }

    @ViewBuilder private func actionButton(_ label: String, _ color: Color,
                                           _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(Cumbre.mono(11, .bold)).tracking(0.5).foregroundStyle(color)
                .frame(maxWidth: .infinity).padding(.vertical, 10)
                .overlay(Rectangle().stroke(color, lineWidth: 1))
        }.buttonStyle(.plain)
    }
}
