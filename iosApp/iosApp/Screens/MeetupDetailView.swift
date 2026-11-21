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
            // Sin quedada cargada, solo volver; con ella, la cabecera de color (MeetupHero) lleva todas las acciones.
            if vm.meetup == nil {
                HStack {
                    Button { dismiss() } label: { Image(systemName: "chevron.left").foregroundColor(Cumbre.ink) }
                    Spacer()
                }
                .padding(.horizontal, 16).padding(.vertical, 10)
            }

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
                            MeetupHero(meetup: meetup, canReport: !isCreator, onBack: { dismiss() },
                                       onReport: { showReportSheet = true }, chat: {
                                if meetup.joined || isCreator {
                                    NavigationLink(destination: GroupChatView(convId: meetup.conversationId, groupName: meetup.name)) {
                                        MeetupHeroIcon(symbol: "bubble.left.and.bubble.right")
                                    }
                                    .buttonStyle(.plain)
                                }
                            }, share: {
                                ShareLink(item: meetupShareText(meetup, inviteLink: vm.inviteLink)) {
                                    MeetupHeroIcon(symbol: "square.and.arrow.up")
                                }
                                .buttonStyle(.plain)
                            })
                            VStack(spacing: 12) {
                                MeetupChips(meetup: meetup)
                                MeetupDaysRow(days: meetup.days, scores: vm.dayScores)
                                MeetupGearSemaphore(meetup: meetup)
                            }
                            .padding(.vertical, 14)

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
                    currentGearJson: m.members.first(where: { $0.uid == myUid })?.gearJson,
                    gym: m.isGymMeetup, placeName: m.placeName ?? m.schoolName, members: Array(m.members), myUid: myUid
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

