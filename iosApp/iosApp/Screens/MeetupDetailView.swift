import SwiftUI
import Shared

// ── ViewModel ──────────────────────────────────────────────────────────────

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
                Text(NSLocalizedString("meetup_detail_title", comment: "")).font(.headline).lineLimit(1)
                Spacer()
                HelpButton(topicKey: "meetup_detail")
                // Compartir/invitar: ShareLink nativo (el UIActivityViewController
                // anterior no se presentaba desde dentro de un sheet).
                if let m = vm.meetup {
                    ShareLink(item: meetupShareText(m, inviteLink: vm.inviteLink)) {
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
                    VStack(spacing: 0) {
                        SkeletonList(count: 1, dividers: false) { SkeletonCard() }
                        SkeletonList(count: 5) { SkeletonPersonRow(avatar: 36) }
                    }.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                } else if vm.meetup == nil {
                    VStack(spacing: 12) {
                        Text("No se pudo cargar").foregroundColor(Cumbre.ink.opacity(0.6))
                        Button(NSLocalizedString("common_retry", comment: "")) { Task { await vm.load(id: meetupId) } }
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
                                    ?? meetup.creatorUsername ?? L("Organizador")
                                NavigationLink(destination: PublicProfileView(uid: meetup.creatorUid)) {
                                    HStack(spacing: 8) {
                                        MeetupAvatarCircle(url: meetup.creatorPhotoUrl, size: 28)
                                        VStack(alignment: .leading, spacing: 1) {
                                            Text(NSLocalizedString("meetup_detail_organized_by", comment: ""))
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
                                            .background(Cumbre.terraFill)
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
                                    Text(NSLocalizedString("meetup_detail_details", comment: ""))
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
                                         ? L("Anade detalles (material, nivel, punto de encuentro...)")
                                         : L("Sin detalles"))
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

                            // ── Organizador / salir (UNIRME va FIJO abajo, sin scroll) ──
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
                                            else { Text(NSLocalizedString("meetup_detail_leave", comment: "")) }
                                        }
                                        .frame(maxWidth: .infinity)
                                    }
                                    .buttonStyle(.bordered)
                                    .disabled(vm.leaving)
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
                                Text(L("%@ PARTICIPANTES", limitText))
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
                    // ── UNIRME fijo (siempre visible al llegar por invitación) ──
                    .safeAreaInset(edge: .bottom) {
                        if !isCreator && !meetup.joined {
                            VStack(spacing: 0) {
                                Divider().overlay(Cumbre.rule)
                                Group {
                                    if meetup.isFull {
                                        Text("AFORO COMPLETO")
                                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                                            .tracking(1.8)
                                            .frame(maxWidth: .infinity)
                                            .padding(.vertical, 10)
                                            .background(Cumbre.ink.opacity(0.06))
                                            .foregroundColor(Cumbre.ink.opacity(0.5))
                                            .clipShape(RoundedRectangle(cornerRadius: 2))
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
                                    } else {
                                        Button {
                                            Task { await vm.join(id: meetupId) }
                                        } label: {
                                            HStack {
                                                if vm.joining { ProgressView().scaleEffect(0.7).tint(.white) }
                                                else {
                                                    Text(NSLocalizedString("meetup_detail_join", comment: ""))
                                                        .font(.system(size: 13, weight: .bold))
                                                }
                                            }
                                            .foregroundColor(.white)
                                            .frame(maxWidth: .infinity)
                                            .padding(.vertical, 12)
                                            .background(Cumbre.terraFill)
                                            .clipShape(RoundedRectangle(cornerRadius: 2))
                                        }
                                        .disabled(vm.joining)
                                    }
                                }
                                .padding(.horizontal, 16).padding(.vertical, 10)
                            }
                            .background(Cumbre.bg)
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
        .alert(NSLocalizedString("meetup_detail_delete", comment: ""), isPresented: $showDeleteConfirm) {
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
            L("Expulsar a %@?", kickTarget?.displayName ?? kickTarget?.username ?? L("este participante")),
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

