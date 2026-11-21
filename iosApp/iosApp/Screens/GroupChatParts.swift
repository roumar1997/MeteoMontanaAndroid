import SwiftUI
import Shared

// Piezas del chat de quedada (ronda 7): cabecera con el cuadradito de color, píldoras «Cómo llegar · Invitar · N
// personas» y la tarjeta de material. Espejo de GroupChatHeader.kt. Lo que falta o sobra lo calcula shared
// (`MeetupGearStatus.withMochilas` + `lines`).

struct GroupChatHeader: View {
    @ObservedObject var vm: GroupChatVM
    let onBack: () -> Void
    let onOpenMeetup: () -> Void

    var body: some View {
        let gym = vm.meetup?.gymId != nil
        let tint = gym ? [Color(hex: 0x3B6FD6), Color(hex: 0x7A45C4)] : [Color(hex: 0x1FA84E), Color(hex: 0x1F7F8C)]
        let people = vm.members.isEmpty ? vm.memberNames.count + 1 : vm.members.count
        let day = vm.meetup?.days.first.map { detailFormatDayMonth($0) }
        let subtitle = [day, LP("chat_header_people", people)].compactMap { $0 }.joined(separator: " · ")
        ChatTopBar(title: vm.groupName, onBack: onBack, onTitleClick: vm.meetupId != nil ? onOpenMeetup : nil, avatar: {
            Circle().fill(LinearGradient(colors: tint, startPoint: .topLeading, endPoint: .bottomTrailing)).frame(width: 44, height: 44)
                .overlay(Text(String(vm.groupName.prefix(1)).uppercased()).font(Cumbre.serif(18, .bold)).foregroundStyle(.white))
        }, subtitle: { ChatSubtitle(text: subtitle) }, actions: {
            Button { vm.toggleMute() } label: {
                Image(systemName: vm.muted ? "bell.slash" : "bell.badge").font(.system(size: 17))
                    .foregroundStyle(vm.muted ? Cumbre.ink2 : Cumbre.terraFill)
                    .frame(width: 40, height: 40)
                    .background(vm.muted ? Cumbre.bg : Cumbre.terraFill.opacity(0.14), in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(L(vm.muted ? "w_unmute" : "w_mute"))
        })
    }
}

/// Píldoras bajo la cabecera: cómo llegar, invitar y ver quién va (abre la quedada).
struct GroupChatActions: View {
    @ObservedObject var vm: GroupChatVM
    let onOpenMeetup: () -> Void
    var body: some View {
        if vm.meetupId != nil {
            FlowLayoutView(spacing: 8) {
                if let lat = vm.schoolLat, let lon = vm.schoolLon {
                    Button { openDirections(lat: lat, lon: lon) } label: {
                        pill("arrow.triangle.turn.up.right.diamond", L("group_chat_screen_v3_como_llegar"))
                    }.buttonStyle(.plain)
                }
                if let invite = vm.inviteText {
                    ShareLink(item: invite) { pill("person.badge.plus", L("group_chat_screen_v4_invitar_al_grupo")) }.buttonStyle(.plain)
                }
                Button(action: onOpenMeetup) { pill("person.3", LP("chat_header_people", vm.members.count)) }.buttonStyle(.plain)
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func pill(_ symbol: String, _ label: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: symbol).font(.system(size: 14, weight: .semibold))
            Text(label).font(.system(size: 13, weight: .semibold)).lineLimit(1)
        }
        .foregroundStyle(Cumbre.terraFill)
        .padding(.leading, 10).padding(.trailing, 14).padding(.vertical, 8)
        .background(Cumbre.terraFill.opacity(0.12), in: Capsule())
    }
}

/// Material del grupo: chips verdes con lo que hay y rojos con lo que falta, y «Mi material». Tocar abre la hoja.
struct GroupChatGearCard: View {
    @ObservedObject var vm: GroupChatVM
    let onEdit: () -> Void
    var body: some View {
        if vm.meetupId != nil {
            Button(action: onEdit) {
                HStack(spacing: 10) {
                    RoundedRectangle(cornerRadius: 12).fill(Cumbre.terraFill.opacity(0.14)).frame(width: 38, height: 38)
                        .overlay(Image(systemName: "bag").font(.system(size: 17)).foregroundStyle(Cumbre.terraFill))
                    FlowLayoutView(spacing: 6) {
                        if !vm.gearLines.isEmpty {
                            ForEach(vm.gearLines, id: \.item) { l in
                                let label = gearItemLabel(l.item)
                                chip(l.ok ? "✓ \(label)" : L("meetup_list_gear_missing", label.lowercased()), l.ok ? Cumbre.ok : Cumbre.bad)
                            }
                        } else if let summary = vm.gearSummary {
                            chip(summary, Cumbre.terraFill)
                        } else {
                            Text(L("group_chat_screen_v2_sin_material_indicado")).font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    HStack(spacing: 5) {
                        Image(systemName: "pencil").font(.system(size: 12, weight: .bold))
                        Text(L("meetup_detail_my_gear")).font(.system(size: 12, weight: .bold)).lineLimit(1)
                    }
                    .foregroundStyle(.white).padding(.horizontal, 12).padding(.vertical, 8).background(Cumbre.terraFill, in: Capsule())
                }
                .padding(10)
                .background(ChatStyle.card, in: RoundedRectangle(cornerRadius: 18))
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(Cumbre.rule, lineWidth: 1))
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 12).padding(.bottom, 8)
        }
    }

    private func chip(_ text: String, _ color: Color) -> some View {
        Text(text).font(.system(size: 12, weight: .semibold)).foregroundStyle(color)
            .padding(.horizontal, 10).padding(.vertical, 5).background(color.opacity(0.13), in: Capsule())
    }
}
