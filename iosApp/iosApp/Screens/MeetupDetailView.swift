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
    @Published var dayScores: [String: Int] = [:]

    private let getMeetup    = AppDependencies.shared.container.getMeetup
    private let joinMeetup   = AppDependencies.shared.container.joinMeetup
    private let leaveMeetup  = AppDependencies.shared.container.leaveMeetup
    private let updateMeetup = AppDependencies.shared.container.updateMeetup
    private let kickMember   = AppDependencies.shared.container.kickMeetupMember
    private let reportMeetup = AppDependencies.shared.container.reportMeetup
    private let getRangeScores = AppDependencies.shared.container.getRangeScores

    @Published var savingDescription = false
    private let updateMyGearUC = AppDependencies.shared.container.updateMyGear

    func updateMyGear(id: String, gearJson: String) async {
        do {
            meetup = try await updateMyGearUC.execute(meetupId: id, gearJson: gearJson)
        } catch { self.error = error.localizedDescription }
    }

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
        // Load day scores
        if let m = meetup, !m.schoolId.isEmpty, !m.days.isEmpty {
            await loadDayScores(schoolId: m.schoolId, days: Array(m.days))
        }
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

    func deleteMeetup(id: String) async -> Bool {
        // Direct HTTP DELETE since DeleteMeetupUseCase isn't exposed in iOS container
        guard let url = URL(string: "\(AppConfig.apiBaseUrl)meetups/\(id)") else { return false }
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        // Get auth token
        if let token = await withCheckedContinuation({ cont in
            AppDependencies.shared.authBridge.currentIdToken(forceRefresh: false) { t in cont.resume(returning: t) }
        }) {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, http.statusCode < 300 {
                return true
            }
            self.error = "Error al eliminar"
            return false
        } catch {
            self.error = error.localizedDescription
            return false
        }
    }

    @Published var reportDone = false

    private func loadDayScores(schoolId: String, days: [String]) async {
        do {
            let results = try await getRangeScores.invoke(ids: [schoolId], dates: days)
            if let school = results.first {
                var map: [String: Int] = [:]
                for day in school.days { map[day.date] = Int(day.score) }
                dayScores = map
            }
        } catch {}
    }
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
    @State private var showDeleteConfirm = false
    @State private var showLeaveConfirm = false
    @State private var showEditGear = false
    @State private var myGender: String?

    private var myUid: String? {
        AppDependencies.shared.authBridge.currentUid()
    }

    var body: some View {
        VStack(spacing: 0) {
            // ── Toolbar ──
            HStack {
                Button { dismiss() } label: {
                    Image(systemName: "chevron.left").foregroundColor(Cumbre.ink)
                }
                Text("Quedada").font(.headline).lineLimit(1)
                Spacer()
                HelpButton(topicKey: "meetup_detail")
                // Share
                if let m = vm.meetup {
                    Button { shareMeetup(m) } label: {
                        Image(systemName: "square.and.arrow.up").foregroundColor(Cumbre.ink.opacity(0.7))
                    }
                }
                if let convId = vm.meetup?.conversationId,
                   let m = vm.meetup,
                   (m.joined || m.creatorUid == myUid) {
                    NavigationLink(destination: GroupChatView(convId: convId, groupName: m.name)) {
                        Image(systemName: "bubble.left.and.bubble.right").foregroundColor(Cumbre.terra)
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
                            // ── Photo ──
                            if let urlStr = meetup.photoUrl, let url = URL(string: urlStr) {
                                AsyncImage(url: url) { img in
                                    img.resizable().scaledToFill()
                                } placeholder: { Color(Cumbre.ink.opacity(0.06)) }
                                .frame(maxWidth: .infinity, maxHeight: 200)
                                .clipped()
                            }

                            // ── Hero section (sand background) ──
                            VStack(alignment: .leading, spacing: 10) {
                                // Name in large serif
                                Text(meetup.name)
                                    .font(Cumbre.serif(22, .semibold))

                                // Organizer with ORGANIZA eyebrow
                                let creatorName = meetup.members.first(where: { $0.uid == meetup.creatorUid })
                                    .flatMap { $0.displayName ?? $0.username }
                                    ?? meetup.creatorUsername ?? "Organizador"
                                NavigationLink(destination: PublicProfileView(uid: meetup.creatorUid)) {
                                    HStack(spacing: 8) {
                                        MeetupAvatarCircle(url: meetup.creatorPhotoUrl, size: 28)
                                        VStack(alignment: .leading, spacing: 1) {
                                            Text("ORGANIZA")
                                                .font(.system(size: 10, weight: .bold, design: .monospaced))
                                                .tracking(1.8)
                                                .foregroundColor(Cumbre.ink.opacity(0.6))
                                            Text(creatorName)
                                                .font(.subheadline).fontWeight(.medium)
                                                .foregroundColor(Cumbre.ink)
                                        }
                                    }
                                }
                                .buttonStyle(.plain)

                                // Action buttons
                                HStack(spacing: 8) {
                                    if let lat = meetup.schoolLat?.doubleValue, let lon = meetup.schoolLon?.doubleValue {
                                        Button {
                                            openDirections(lat: lat, lon: lon)
                                        } label: {
                                            HStack(spacing: 4) {
                                                Image(systemName: "location.fill").font(.caption2)
                                                Text("Como llegar").font(.system(size: 13, weight: .medium))
                                            }
                                            .foregroundColor(.white)
                                            .frame(maxWidth: .infinity)
                                            .padding(.vertical, 8)
                                            .background(Cumbre.terra)
                                            .clipShape(RoundedRectangle(cornerRadius: 2))
                                        }
                                    }
                                    if meetup.schoolName != nil {
                                        NavigationLink(destination: SchoolLoaderView(schoolId: meetup.schoolId)) {
                                            HStack(spacing: 4) {
                                                Image(systemName: "mountain.2").font(.caption2)
                                                Text("Ver escuela").font(.system(size: 13, weight: .medium))
                                            }
                                            .foregroundColor(Cumbre.ink)
                                            .frame(maxWidth: .infinity)
                                            .padding(.vertical, 8)
                                            .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.ink.opacity(0.3), lineWidth: 1))
                                        }
                                    }
                                }
                            }
                            .padding(16)
                            .background(Cumbre.ink.opacity(0.04))

                            // ── Info rows with icons ──
                            VStack(alignment: .leading, spacing: 14) {
                                // School
                                if let school = meetup.schoolName {
                                    DetailInfoRow(icon: "mountain.2") {
                                        Text(school).font(.subheadline).fontWeight(.medium)
                                    }
                                }

                                // Days with scores
                                DetailInfoRow(icon: "calendar") {
                                    FlowLayoutView {
                                        ForEach(meetup.days, id: \.self) { day in
                                            let score = vm.dayScores[day]
                                            HStack(spacing: 4) {
                                                Text(detailFormatDayMonth(day))
                                                    .font(.system(size: 12, weight: .medium))
                                                if let s = score {
                                                    Text("\(s)")
                                                        .font(.system(size: 10, weight: .bold))
                                                        .foregroundColor(.white)
                                                        .padding(.horizontal, 4).padding(.vertical, 1)
                                                        .background(detailScoreColor(s))
                                                        .clipShape(RoundedRectangle(cornerRadius: 2))
                                                }
                                            }
                                            .padding(.horizontal, 6).padding(.vertical, 3)
                                            .background(Cumbre.ink.opacity(0.05))
                                            .clipShape(RoundedRectangle(cornerRadius: 2))
                                        }
                                    }
                                }

                                // Privacy + discipline
                                HStack(spacing: 8) {
                                    if meetup.privacy != "OPEN" {
                                        DetailInfoRow(icon: "lock") {
                                            Text(privacyLabel(meetup.privacy)).font(.caption)
                                                .foregroundColor(Cumbre.ink.opacity(0.6))
                                        }
                                    }
                                    if let disc = meetup.discipline {
                                        Text("\u{00B7} \(disciplineLabel(disc))").font(.caption)
                                            .foregroundColor(Cumbre.ink.opacity(0.6))
                                    }
                                }
                            }
                            .padding(16)

                            Divider()

                            // ── Description ──
                            VStack(alignment: .leading, spacing: 4) {
                                HStack(spacing: 6) {
                                    Text("DETALLES")
                                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                                        .tracking(1.8)
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
                                         ? "Anade detalles (material, nivel, punto de encuentro...)"
                                         : "Sin detalles")
                                        .font(.caption).italic().foregroundColor(Cumbre.ink.opacity(0.5))
                                }
                            }
                            .padding(16)

                            Divider()

                            // ── Material (gear) ──
                            GearSectionView(
                                meetup: meetup,
                                myUid: myUid,
                                isCreator: isCreator,
                                onEditGear: { showEditGear = true }
                            )

                            Divider()

                            // ── Join/leave/delete ──
                            Group {
                                if isCreator {
                                    VStack(spacing: 8) {
                                        Text("Eres el organizador")
                                            .font(.footnote).foregroundColor(Cumbre.ink.opacity(0.5))
                                        Button {
                                            showDeleteConfirm = true
                                        } label: {
                                            Text("ELIMINAR QUEDADA")
                                                .font(.system(size: 13, weight: .bold))
                                                .foregroundColor(.red)
                                                .frame(maxWidth: .infinity)
                                                .padding(.vertical, 10)
                                                .overlay(RoundedRectangle(cornerRadius: 2)
                                                    .stroke(Color.red, lineWidth: 1))
                                        }
                                    }
                                    .padding(16)
                                } else if meetup.joined {
                                    Button {
                                        showLeaveConfirm = true
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
                                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                                        .tracking(1.8)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 10)
                                        .background(Cumbre.ink.opacity(0.06))
                                        .foregroundColor(Cumbre.ink.opacity(0.5))
                                        .clipShape(RoundedRectangle(cornerRadius: 2))
                                        .padding(16)
                                } else if meetup.privacy == "WOMEN" && myGender != "WOMAN" {
                                    VStack(spacing: 6) {
                                        Image(systemName: "lock.fill").foregroundColor(Cumbre.ink.opacity(0.4))
                                        Text("Esta quedada es No Mixto")
                                            .font(.subheadline).fontWeight(.medium)
                                        Text("Para unirte necesitas indicar tu genero como Mujer en tu perfil (Perfil > Editar perfil > Genero).")
                                            .font(.caption).foregroundColor(Cumbre.ink.opacity(0.6))
                                            .multilineTextAlignment(.center)
                                    }
                                    .frame(maxWidth: .infinity)
                                    .padding(16)
                                } else {
                                    Button {
                                        Task { await vm.join(id: meetupId) }
                                    } label: {
                                        HStack {
                                            if vm.joining { ProgressView().scaleEffect(0.7).tint(.white) }
                                            else {
                                                Text("UNIRSE A LA QUEDADA")
                                                    .font(.system(size: 13, weight: .bold))
                                            }
                                        }
                                        .foregroundColor(.white)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 12)
                                        .background(Cumbre.terra)
                                        .clipShape(RoundedRectangle(cornerRadius: 2))
                                    }
                                    .disabled(vm.joining)
                                    .padding(16)
                                }
                            }

                            Divider()

                            // ── Members ──
                            let limitText: String = {
                                if let lim = meetup.memberLimit { return "\(meetup.memberCount)/\(lim.int32Value)" }
                                return "\(meetup.memberCount)"
                            }()
                            HStack(spacing: 6) {
                                Image(systemName: "person").font(.caption).foregroundColor(Cumbre.ink.opacity(0.5))
                                Text("\(limitText) PARTICIPANTES")
                                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                                    .tracking(1.5)
                                    .foregroundColor(Cumbre.ink.opacity(0.6))
                            }
                            .padding(.horizontal, 16).padding(.vertical, 10)

                            ForEach(meetup.members, id: \.uid) { member in
                                NavigationLink(destination: PublicProfileView(uid: member.uid)) {
                                    MeetupMemberRow(
                                        member: member,
                                        canKick: isCreator && member.uid != myUid
                                    ) {
                                        kickTarget = member
                                        showKickConfirm = true
                                    }
                                }
                                .buttonStyle(.plain)
                                Divider()
                            }

                            Spacer(minLength: 80)
                        }
                    }
                }
            }

            // Report done banner
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
        .task {
            await vm.load(id: meetupId)
            if let p = try? await AppDependencies.shared.container.getMyProfile.invoke() {
                myGender = p.gender
            }
        }
        .sheet(isPresented: $showEditGear) {
            if let m = vm.meetup {
                EditGearSheet(
                    discipline: m.discipline,
                    currentGearJson: m.members.first(where: { $0.uid == myUid })?.gearJson
                ) { json in
                    Task { await vm.updateMyGear(id: meetupId, gearJson: json) }
                    showEditGear = false
                } onCancel: {
                    showEditGear = false
                }
            }
        }
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
        .alert("Eliminar quedada", isPresented: $showDeleteConfirm) {
            Button("ELIMINAR", role: .destructive) {
                Task {
                    let ok = await vm.deleteMeetup(id: meetupId)
                    if ok { dismiss() }
                }
            }
            Button("CANCELAR", role: .cancel) {}
        } message: {
            Text("Se eliminara la quedada y su chat de grupo. Esta accion no se puede deshacer.")
        }
        .confirmationDialog(
            "Expulsar a \(kickTarget?.displayName ?? kickTarget?.username ?? "este participante")?",
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
        .alert("Salir de la quedada", isPresented: $showLeaveConfirm) {
            Button("SALIR", role: .destructive) {
                Task { await vm.leave(id: meetupId) }
            }
            Button("CANCELAR", role: .cancel) {}
        } message: {
            Text("Dejaras de participar en esta quedada y no podras ver el chat de grupo.")
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

private func shareMeetup(_ meetup: Meetup) {
    let days = meetup.days.map { detailFormatDayMonth($0) }.joined(separator: ", ")
    let plazas: String = {
        if let lim = meetup.memberLimit {
            return "\(meetup.memberCount)/\(lim.int32Value) plazas"
        }
        return "\(meetup.memberCount) participantes"
    }()
    var text = "Quedada: \(meetup.name)\n"
    if let s = meetup.schoolName { text += "Escuela: \(s)\n" }
    text += "\(days) \u{00B7} \(plazas)\n\nDescarga Cumbre:\nAndroid: https://play.google.com/store/apps/details?id=com.meteomontana.android\niOS: https://apps.apple.com/app/cumbre/id0000000000"
    let av = UIActivityViewController(activityItems: [text], applicationActivities: nil)
    UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow?.rootViewController }.first?.present(av, animated: true)
}

private func detailFormatDayMonth(_ iso: String) -> String {
    let weekdays = ["dom","lun","mar","mie","jue","vie","sab"]
    let months = ["ene","feb","mar","abr","may","jun","jul","ago","sep","oct","nov","dic"]
    let parts = iso.split(separator: "-")
    guard parts.count == 3,
          let y = Int(parts[0]), let mo = Int(parts[1]), let d = Int(parts[2]) else { return iso }
    var comps = DateComponents()
    comps.year = y; comps.month = mo; comps.day = d
    if let date = Calendar.current.date(from: comps) {
        let wd = Calendar.current.component(.weekday, from: date)
        return "\(weekdays[wd - 1]) \(d) \(months[mo - 1])"
    }
    return "\(d) \(months[mo - 1])"
}

private func detailScoreColor(_ score: Int) -> Color {
    if score >= 80 { return Color(red: 0.13, green: 0.77, blue: 0.37) }
    if score >= 60 { return Color(red: 0.96, green: 0.62, blue: 0.04) }
    if score >= 40 { return Color(red: 0.93, green: 0.27, blue: 0.27) }
    return Color(red: 0.42, green: 0.44, blue: 0.50)
}

// ── Sub-views ────────────────────────────────────────────────────────────────

private struct DetailInfoRow<Content: View>: View {
    let icon: String
    @ViewBuilder let content: Content
    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 14))
                .foregroundColor(Cumbre.terra)
                .frame(width: 18)
            content
        }
    }
}

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
            MeetupAvatarCircle(url: member.photoUrl, size: 36)
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

struct MeetupAvatarCircle: View {
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

// MARK: - Gear helpers

func parseGear(_ json: String?) -> [String: Int] {
    guard let json = json?.trimmingCharacters(in: .whitespaces),
          !json.isEmpty, json != "{}" else { return [:] }
    var result: [String: Int] = [:]
    let inner = json.trimmingCharacters(in: CharacterSet(charactersIn: "{}"))
    for pair in inner.split(separator: ",") {
        let parts = pair.split(separator: ":")
        guard parts.count == 2 else { continue }
        let key = parts[0].trimmingCharacters(in: .whitespaces).trimmingCharacters(in: CharacterSet(charactersIn: "\""))
        if let val_ = Int(parts[1].trimmingCharacters(in: .whitespaces)), val_ > 0 {
            result[key] = val_
        }
    }
    return result
}

func buildGearJson(_ gear: [String: Int]) -> String {
    let entries = gear.filter { $0.value > 0 }
    if entries.isEmpty { return "{}" }
    let pairs = entries.map { "\"\($0.key)\":\($0.value)" }
    return "{\(pairs.joined(separator: ","))}"
}

func gearItemsForDiscipline(_ discipline: String?) -> [(key: String, label: String)] {
    switch discipline {
    case "BOULDER": return [("crashpads", "Crashpads")]
    case "ROUTE": return [("cintas", "Cintas"), ("cuerda", "Cuerdas"), ("grigri", "Gri-gri")]
    default: return [("crashpads", "Crashpads"), ("cintas", "Cintas"), ("cuerda", "Cuerdas"), ("grigri", "Gri-gri")]
    }
}

func gearLabel(_ key: String) -> String {
    switch key {
    case "crashpads": return "crashpads"
    case "cintas": return "cintas"
    case "cuerda": return "cuerdas"
    case "grigri": return "gri-gri"
    default: return key
    }
}

func totalGear(_ members: [MeetupMember]) -> [String: Int] {
    var totals: [String: Int] = [:]
    for m in members {
        for (k, v) in parseGear(m.gearJson) {
            totals[k, default: 0] += v
        }
    }
    return totals.filter { $0.value > 0 }
}

func totalGearSummary(_ members: [MeetupMember]) -> String {
    let totals = totalGear(members)
    if totals.isEmpty { return "" }
    return totals.map { "\($0.value) \(gearLabel($0.key))" }.joined(separator: " \u{00B7} ")
}

// MARK: - Gear section view

private struct GearSectionView: View {
    let meetup: Meetup
    let myUid: String?
    let isCreator: Bool
    let onEditGear: () -> Void

    var body: some View {
        let totals = totalGear(Array(meetup.members))

        VStack(alignment: .leading, spacing: 10) {
            // Eyebrow
            HStack(spacing: 6) {
                Image(systemName: "bag").font(.system(size: 13))
                    .foregroundColor(Cumbre.ink.opacity(0.6))
                Text("MATERIAL")
                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                    .tracking(1.8)
                    .foregroundColor(Cumbre.ink.opacity(0.6))
            }

            // Total pills
            if !totals.isEmpty {
                FlowLayoutView {
                    ForEach(Array(totals.keys.sorted()), id: \.self) { key in
                        HStack(spacing: 4) {
                            Text("\(totals[key] ?? 0)")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(Cumbre.terra)
                            Text(gearLabel(key))
                                .font(.system(size: 12))
                                .foregroundColor(Cumbre.ink.opacity(0.6))
                        }
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .background(Cumbre.ink.opacity(0.05))
                        .clipShape(RoundedRectangle(cornerRadius: 2))
                    }
                }
            } else {
                Text("Nadie ha indicado material todavia. Pulsa abajo para añadir el tuyo.")
                    .font(.caption).italic().foregroundColor(Cumbre.ink.opacity(0.5))
            }

            // Per-member breakdown
            ForEach(meetup.members, id: \.uid) { member in
                let gear = parseGear(member.gearJson)
                let summary = gear.isEmpty ? "" : gear.map { "\($0.value) \(gearLabel($0.key))" }.joined(separator: " \u{00B7} ")
                HStack(spacing: 8) {
                    MeetupAvatarCircle(url: member.photoUrl, size: 28)
                    Text(member.displayName ?? member.username ?? String(member.uid.prefix(6)))
                        .font(.system(size: 13, weight: .medium))
                    Spacer()
                    if !summary.isEmpty {
                        Text(summary)
                            .font(.system(size: 12))
                            .foregroundColor(Cumbre.ink.opacity(0.6))
                    } else {
                        Text("sin material")
                            .font(.system(size: 12)).italic()
                            .foregroundColor(Cumbre.ink.opacity(0.35))
                    }
                }
            }

            // Edit button
            if meetup.joined || isCreator {
                Button(action: onEditGear) {
                    HStack(spacing: 6) {
                        Image(systemName: "pencil").font(.caption2)
                        Text("Editar mi material").font(.system(size: 13, weight: .medium))
                    }
                    .foregroundColor(Cumbre.ink)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.ink.opacity(0.3), lineWidth: 1))
                }
            }
        }
        .padding(16)
    }
}

// MARK: - Edit gear sheet

struct EditGearSheet: View {
    let discipline: String?
    let currentGearJson: String?
    let onSave: (String) -> Void
    let onCancel: () -> Void

    @State private var gear: [String: Int] = [:]

    var body: some View {
        let items = gearItemsForDiscipline(discipline)

        NavigationView {
            VStack(spacing: 16) {
                Text("Indica el material que llevas a la quedada")
                    .font(.caption).foregroundColor(Cumbre.ink.opacity(0.6))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal)

                ForEach(items, id: \.key) { item in
                    HStack {
                        Text(item.label).font(.subheadline).fontWeight(.medium)
                        Spacer()
                        HStack(spacing: 8) {
                            Button {
                                let cur = gear[item.key] ?? 0
                                if cur > 0 { gear[item.key] = cur - 1 }
                            } label: {
                                Image(systemName: "minus.circle")
                                    .font(.title3)
                                    .foregroundColor(gear[item.key, default: 0] > 0 ? Cumbre.ink : Cumbre.ink.opacity(0.2))
                            }
                            .disabled(gear[item.key, default: 0] == 0)

                            Text("\(gear[item.key] ?? 0)")
                                .font(.title3).fontWeight(.bold).monospacedDigit()
                                .frame(minWidth: 28)

                            Button {
                                gear[item.key, default: 0] += 1
                            } label: {
                                Image(systemName: "plus.circle")
                                    .font(.title3).foregroundColor(Cumbre.terra)
                            }
                        }
                    }
                    .padding(.horizontal)
                }

                Spacer()
            }
            .padding(.top)
            .navigationTitle("Mi material")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { onCancel() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Guardar") { onSave(buildGearJson(gear)) }
                        .fontWeight(.bold)
                }
            }
            .onAppear {
                let current = parseGear(currentGearJson)
                let items = gearItemsForDiscipline(discipline)
                var initial: [String: Int] = [:]
                for item in items { initial[item.key] = current[item.key] ?? 0 }
                gear = initial
            }
        }
    }
}
