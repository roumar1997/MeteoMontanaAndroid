import SwiftUI
import Shared

// ── ViewModel ──────────────────────────────────────────────────────────────

@MainActor
final class MeetupDetailViewModel: ObservableObject {
    @Published var meetup: Meetup?
    @Published var loading = false
    @Published var error: String?
    @Published var joining = false
    @Published var leaving = false

    private let getMeetup    = AppDependencies.shared.container.getMeetup
    private let joinMeetup   = AppDependencies.shared.container.joinMeetup
    private let leaveMeetup  = AppDependencies.shared.container.leaveMeetup
    private let updateMeetup = AppDependencies.shared.container.updateMeetup
    private let kickMember   = AppDependencies.shared.container.kickMeetupMember
    private let reportMeetup = AppDependencies.shared.container.reportMeetup

    @Published var savingDescription = false

    func updateDescription(id: String, text: String?) async {
        savingDescription = true
        do {
            if let uc = updateMeetup { meetup = try await uc.execute(meetupId: id, description: text) }
        } catch { self.error = error.localizedDescription }
        savingDescription = false
    }

    func load(id: String) async {
        loading = true; error = nil
        do {
            if let uc = getMeetup { meetup = try await uc.execute(id: id) }
        } catch { self.error = error.localizedDescription }
        loading = false
    }

    func join(id: String) async {
        joining = true
        do {
            if let uc = joinMeetup { meetup = try await uc.execute(id: id) }
        } catch { self.error = error.localizedDescription }
        joining = false
    }

    func leave(id: String) async {
        leaving = true
        do {
            if let uc = leaveMeetup {
                try await uc.execute(id: id)
                // Recargar para reflejar el nuevo estado (no reconstruimos Meetup manualmente)
                await load(id: id)
            }
        } catch { self.error = error.localizedDescription }
        leaving = false
    }

    func kick(meetupId: String, targetUid: String) async {
        do {
            try await kickMember.execute(meetupId: meetupId, targetUid: targetUid)
            await load(id: meetupId)
        } catch { self.error = error.localizedDescription }
    }

    func report(meetupId: String, reportedUid: String?, reason: String) async {
        do {
            try await reportMeetup.execute(
                meetupId: meetupId,
                reportedUid: reportedUid,
                reason: reason,
                context: nil
            )
            reportDone = true
        } catch { self.error = error.localizedDescription }
    }

    @Published var reportDone = false
}

// ── Detail view ─────────────────────────────────────────────────────────────

struct MeetupDetailView: View {
    let meetupId: String
    @StateObject private var vm = MeetupDetailViewModel()
    @Environment(\.dismiss) private var dismiss
    @State private var kickTarget: MeetupMember?
    @State private var showKickConfirm = false
    @State private var showChat = false
    @State private var showReportSheet = false
    @State private var showEditDescription = false
    @State private var descriptionDraft = ""

    private var myUid: String? {
        AppDependencies.shared.authBridge.currentUid()
    }

    var body: some View {
        VStack(spacing: 0) {
            // Toolbar
            HStack {
                Button { dismiss() } label: {
                    Image(systemName: "chevron.left").foregroundColor(Cumbre.ink)
                }
                Text(vm.meetup?.name ?? "Quedada")
                    .font(.headline).lineLimit(1)
                Spacer()
                if let convId = vm.meetup?.conversationId,
                   let m = vm.meetup,
                   (m.joined || m.creatorUid == myUid) {
                    NavigationLink(destination: GroupChatView(convId: convId, groupName: m.name)) {
                        Image(systemName: "bubble.left.and.bubble.right")
                            .foregroundColor(Cumbre.ink)
                    }
                }
                if let m = vm.meetup, m.creatorUid != myUid {
                    Button { showReportSheet = true } label: {
                        Image(systemName: "flag").foregroundColor(Cumbre.ink.opacity(0.6))
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Cumbre.bg)
            .overlay(Divider(), alignment: .bottom)

            Group {
                if vm.loading && vm.meetup == nil {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if vm.meetup == nil {
                    VStack(spacing: 12) {
                        Text("No se pudo cargar").foregroundColor(Cumbre.ink.opacity(0.6))
                        Button("REINTENTAR") { Task { await vm.load(id: meetupId) } }
                            .foregroundColor(Cumbre.terra)
                    }.frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    let meetup = vm.meetup!
                    let isCreator = meetup.creatorUid == myUid

                    ScrollView {
                        VStack(alignment: .leading, spacing: 0) {
                            // Header photo
                            if let urlStr = meetup.photoUrl, let url = URL(string: urlStr) {
                                AsyncImage(url: url) { img in
                                    img.resizable().scaledToFill()
                                } placeholder: { Color(Cumbre.ink.opacity(0.06)) }
                                .frame(maxWidth: .infinity, maxHeight: 200)
                                .clipped()
                            }

                            VStack(alignment: .leading, spacing: 12) {
                                // School link
                                if let school = meetup.schoolName {
                                    NavigationLink(destination: EmptyView()) {
                                        HStack(spacing: 6) {
                                            Text("VER ESCUELA: \(school)")
                                                .font(.system(size: 11, weight: .bold))
                                                .foregroundColor(Cumbre.terra)
                                        }
                                        .padding(.horizontal, 10).padding(.vertical, 6)
                                        .overlay(RoundedRectangle(cornerRadius: 4).stroke(Cumbre.ink.opacity(0.2), lineWidth: 1))
                                    }
                                }

                                // Cómo llegar (Google Maps)
                                if let lat = meetup.schoolLat?.doubleValue, let lon = meetup.schoolLon?.doubleValue {
                                    Button {
                                        openDirections(lat: lat, lon: lon)
                                    } label: {
                                        HStack(spacing: 6) {
                                            Image(systemName: "location.fill").font(.caption2)
                                            Text("CÓMO LLEGAR").font(.system(size: 11, weight: .bold))
                                        }
                                        .foregroundColor(Cumbre.terra)
                                        .padding(.horizontal, 10).padding(.vertical, 6)
                                        .overlay(RoundedRectangle(cornerRadius: 4).stroke(Cumbre.ink.opacity(0.2), lineWidth: 1))
                                    }
                                }

                                // Descripción (detalles del organizador)
                                VStack(alignment: .leading, spacing: 4) {
                                    HStack(spacing: 6) {
                                        Text("DETALLES")
                                            .font(.system(size: 11, weight: .bold))
                                            .foregroundColor(Cumbre.ink.opacity(0.6))
                                        if isCreator {
                                            Button {
                                                descriptionDraft = meetup.descriptionText ?? ""
                                                showEditDescription = true
                                            } label: {
                                                Image(systemName: "pencil").font(.caption).foregroundColor(Cumbre.terra)
                                            }
                                        }
                                    }
                                    if let desc = meetup.descriptionText, !desc.isEmpty {
                                        Text(desc).font(.subheadline)
                                    } else {
                                        Text(isCreator
                                             ? "Añade detalles (material, nivel, punto de encuentro…)"
                                             : "Sin detalles")
                                            .font(.caption).foregroundColor(Cumbre.ink.opacity(0.5))
                                    }
                                }

                                // Dates
                                let days = meetup.days
                                HStack(spacing: 6) {
                                    Image(systemName: "calendar").font(.footnote).foregroundColor(Cumbre.ink.opacity(0.5))
                                    Text(days.joined(separator: "  ·  "))
                                        .font(.subheadline).foregroundColor(Cumbre.ink.opacity(0.7))
                                }

                                // Privacy + Discipline chips
                                HStack(spacing: 8) {
                                    if meetup.privacy != "OPEN" {
                                        InfoBadge(icon: "lock", label: privacyLabel(meetup.privacy))
                                    }
                                    if let disc = meetup.discipline {
                                        InfoBadge(label: disciplineLabel(disc))
                                    }
                                }

                                // Creator
                                HStack(spacing: 8) {
                                    AvatarCircle(url: meetup.creatorPhotoUrl, size: 24)
                                    Text("Organiza: \(meetup.creatorUsername ?? meetup.creatorUid)")
                                        .font(.footnote).foregroundColor(Cumbre.ink.opacity(0.6))
                                }
                            }
                            .padding(16)

                            Divider()

                            // Join/leave/full
                            Group {
                                if isCreator {
                                    Text("Eres el organizador")
                                        .font(.footnote).foregroundColor(Cumbre.ink.opacity(0.5))
                                        .padding(16)
                                } else if meetup.joined {
                                    Button {
                                        Task { await vm.leave(id: meetupId) }
                                    } label: {
                                        HStack {
                                            if vm.leaving { ProgressView().scaleEffect(0.7) }
                                            else { Text("SALIR DE LA QUEDADA") }
                                        }
                                        .frame(maxWidth: .infinity)
                                    }
                                    .buttonStyle(.bordered)
                                    .disabled(vm.leaving)
                                    .padding(16)
                                } else if meetup.isFull {
                                    Text("AFORO COMPLETO")
                                        .font(.footnote).fontWeight(.bold)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 10)
                                        .background(Cumbre.ink.opacity(0.06))
                                        .foregroundColor(Cumbre.ink.opacity(0.5))
                                        .cornerRadius(4)
                                        .padding(16)
                                } else {
                                    Button {
                                        Task { await vm.join(id: meetupId) }
                                    } label: {
                                        HStack {
                                            if vm.joining { ProgressView().scaleEffect(0.7).tint(.white) }
                                            else { Text("UNIRSE A LA QUEDADA") }
                                        }
                                        .frame(maxWidth: .infinity)
                                    }
                                    .buttonStyle(.borderedProminent)
                                    .tint(Cumbre.terra)
                                    .disabled(vm.joining)
                                    .padding(16)
                                }
                            }

                            Divider()

                            // Members header
                            let members = meetup.members
                            let limitText: String = {
                                if let lim = meetup.memberLimit { return "\(meetup.memberCount)/\(lim.int32Value)" }
                                return "\(meetup.memberCount)"
                            }()
                            HStack(spacing: 6) {
                                Image(systemName: "person").foregroundColor(Cumbre.ink.opacity(0.5))
                                Text("\(limitText) participantes")
                                    .font(.system(size: 11, weight: .bold))
                            }
                            .padding(.horizontal, 16).padding(.vertical, 10)

                            // Member list
                            ForEach(members, id: \.uid) { member in
                                MeetupMemberRow(
                                    member: member,
                                    canKick: isCreator && member.uid != myUid
                                ) {
                                    kickTarget = member
                                    showKickConfirm = true
                                }
                                Divider()
                            }

                            Spacer(minLength: 80)
                        }
                    }
                }
            }

            // Denuncia enviada banner
            if vm.reportDone {
                Text("Denuncia enviada. La revisaremos pronto.")
                    .font(.footnote)
                    .frame(maxWidth: .infinity)
                    .padding(8)
                    .background(Color.green.opacity(0.12))
                    .foregroundColor(.green)
                    .onAppear {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 3) { vm.reportDone = false }
                    }
            }

            // Error banner
            if let err = vm.error {
                Text(err)
                    .font(.footnote)
                    .frame(maxWidth: .infinity)
                    .padding(8)
                    .background(Color.red.opacity(0.12))
                    .foregroundColor(.red)
                    .onAppear {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 3) { vm.error = nil }
                    }
            }
        }
        .navigationBarHidden(true)
        .task { await vm.load(id: meetupId) }
        .sheet(isPresented: $showEditDescription) {
            EditDescriptionSheet(
                text: $descriptionDraft,
                saving: vm.savingDescription
            ) {
                let trimmed = descriptionDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                Task { await vm.updateDescription(id: meetupId, text: trimmed.isEmpty ? nil : trimmed) }
                showEditDescription = false
            } onCancel: {
                showEditDescription = false
            }
        }
        .confirmationDialog(
            "Denunciar quedada",
            isPresented: $showReportSheet,
            titleVisibility: .visible
        ) {
            Button("Spam o publicidad") {
                Task { await vm.report(meetupId: meetupId, reportedUid: vm.meetup?.creatorUid, reason: "SPAM") }
            }
            Button("Contenido inapropiado") {
                Task { await vm.report(meetupId: meetupId, reportedUid: vm.meetup?.creatorUid, reason: "INAPPROPRIATE") }
            }
            Button("Acoso o comportamiento ofensivo") {
                Task { await vm.report(meetupId: meetupId, reportedUid: vm.meetup?.creatorUid, reason: "HARASSMENT") }
            }
            Button("Otro motivo") {
                Task { await vm.report(meetupId: meetupId, reportedUid: vm.meetup?.creatorUid, reason: "OTHER") }
            }
            Button("Cancelar", role: .cancel) {}
        }
        .confirmationDialog(
            "¿Expulsar a \(kickTarget?.displayName ?? kickTarget?.username ?? "este participante")?",
            isPresented: $showKickConfirm,
            titleVisibility: .visible
        ) {
            Button("Expulsar", role: .destructive) {
                if let t = kickTarget {
                    Task { await vm.kick(meetupId: meetupId, targetUid: t.uid) }
                }
            }
            Button("Cancelar", role: .cancel) {}
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

/// Abre Google Maps (o Apple Maps) con indicaciones a las coordenadas de la escuela.
func openDirections(lat: Double, lon: Double) {
    if let url = URL(string: "https://www.google.com/maps/dir/?api=1&destination=\(lat),\(lon)") {
        UIApplication.shared.open(url)
    }
}

// ── Sub-views ────────────────────────────────────────────────────────────────

private struct EditDescriptionSheet: View {
    @Binding var text: String
    let saving: Bool
    let onSave: () -> Void
    let onCancel: () -> Void

    var body: some View {
        NavigationView {
            VStack {
                TextEditor(text: $text)
                    .frame(minHeight: 140)
                    .overlay(RoundedRectangle(cornerRadius: 6).stroke(Cumbre.ink.opacity(0.2), lineWidth: 1))
                    .padding()
                Spacer()
            }
            .navigationTitle("Detalles de la quedada")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { onCancel() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if saving { ProgressView() }
                    else { Button("Guardar") { onSave() } }
                }
            }
        }
    }
}

private struct MeetupMemberRow: View {
    let member: MeetupMember
    let canKick: Bool
    let onKick: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            AvatarCircle(url: member.photoUrl, size: 36)
            VStack(alignment: .leading, spacing: 2) {
                Text(member.displayName ?? member.username ?? member.uid)
                    .font(.subheadline).fontWeight(.medium)
                if let u = member.username {
                    Text("@\(u)").font(.caption).foregroundColor(Cumbre.ink.opacity(0.5))
                }
            }
            Spacer()
            if canKick {
                Button(action: onKick) {
                    Image(systemName: "person.badge.minus").foregroundColor(.red)
                }
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
    }
}

private struct InfoBadge: View {
    var icon: String? = nil
    let label: String
    var body: some View {
        HStack(spacing: 4) {
            if let i = icon { Image(systemName: i).font(.caption2) }
            Text(label).font(.caption)
        }
        .foregroundColor(Cumbre.ink.opacity(0.6))
        .padding(.horizontal, 8).padding(.vertical, 3)
        .overlay(RoundedRectangle(cornerRadius: 4).stroke(Cumbre.ink.opacity(0.2), lineWidth: 1))
    }
}

private struct AvatarCircle: View {
    let url: String?
    let size: CGFloat
    var body: some View {
        ZStack {
            Circle().fill(Cumbre.ink.opacity(0.08))
            if let u = url, let imgUrl = URL(string: u) {
                AsyncImage(url: imgUrl) { img in img.resizable().scaledToFill() }
                    placeholder: { Color.clear }
                    .clipShape(Circle())
            } else {
                Image(systemName: "person.fill").foregroundColor(Cumbre.ink.opacity(0.4))
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }
}
