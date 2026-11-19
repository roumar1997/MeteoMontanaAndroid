import SwiftUI
import Shared

/// Título de sección en mono mayúsculas, con icono opcional. Espejo de `SectionLabel` de Android.
private struct GymSectionLabel: View {
    let text: String
    var icon: String?

    var body: some View {
        HStack(spacing: 6) {
            if let icon {
                Image(icon).renderingMode(.template).resizable().scaledToFit().frame(width: 14, height: 14)
            }
            Text(text.uppercased()).eyebrow(Cumbre.ink2)
        }
        .foregroundStyle(Cumbre.ink2)
        .padding(.top, 16).padding(.bottom, 2)
    }
}

/// Modalidad, horario, precio orientativo (con su fecha), servicios y fiabilidad de la ficha.
struct GymInfoSections: View {
    let gym: Gym

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            GymSectionLabel(text: L("gym_section_discipline"))
            let disciplines = GymPresentation.disciplineChips(gym)
            if disciplines.isEmpty {
                Text(L("gym_unknown")).font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
            } else {
                FlowLayoutView(spacing: 6) {
                    ForEach(disciplines, id: \.self) { GymTag(text: $0, color: GymTone.discipline) }
                }
            }
            hours
            price
            GymSectionLabel(text: L("gym_section_services"))
            VStack(spacing: 8) {
                serviceRow("gym_ic_autobelay", "gym_service_autobelay", gym.hasAutobelay)
                serviceRow("gym_ic_gear_rental", "gym_service_gear", gym.hasGearRental)
                serviceRow("gym_ic_kids", "gym_service_kids", gym.kidsFriendly)
                serviceRow("gym_ic_beginner", "gym_service_beginner", gym.beginnerFriendly)
            }
            Text(GymPresentation.verificationText(gym.verification))
                .font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.ink2).padding(.top, 16)
            if gym.needsOsmAttribution {
                Text(L("gym_osm")).font(.system(size: 11)).foregroundStyle(Cumbre.ink2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder private var hours: some View {
        GymSectionLabel(text: L("gym_section_hours"), icon: "gym_ic_clock")
        let lines = GymPresentation.hoursLines(gym.openingHours)
        if lines.isEmpty {
            Text(L("gym_hours_unknown")).font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
        } else {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(lines, id: \.self) { Text($0).font(.system(size: 14)).foregroundStyle(Cumbre.ink).lineSpacing(4) }
            }
        }
    }

    @ViewBuilder private var price: some View {
        GymSectionLabel(text: L("gym_section_price"), icon: "gym_ic_price")
        let info = GymPresentation.priceInfo(gym)
        if info.lines.isEmpty {
            Text(L("gym_price_unknown")).font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
        } else {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(info.lines, id: \.self) { Text($0).font(.system(size: 14)).foregroundStyle(Cumbre.ink).lineSpacing(4) }
                if let note = info.note {
                    Text(note).font(.system(size: 12)).foregroundStyle(Cumbre.ink2).padding(.top, 2)
                }
            }
        }
    }

    private func serviceRow(_ icon: String, _ labelKey: String, _ value: KotlinBoolean?) -> some View {
        HStack(spacing: 10) {
            Image(icon).renderingMode(.template).resizable().scaledToFit().frame(width: 20, height: 20)
                .foregroundStyle(Cumbre.ink2)
            Text(L(labelKey)).font(.system(size: 14)).foregroundStyle(Cumbre.ink).frame(maxWidth: .infinity, alignment: .leading)
            Text(GymPresentation.serviceValue(value)).font(.system(size: 13, weight: .semibold))
                .foregroundStyle(value == nil ? Cumbre.ink2 : Cumbre.ink)
        }
    }
}

/// «Quién va hoy»: quién ha marcado «Aquí» en este rocódromo y el botón para unirte (con el mismo aviso de
/// privacidad que en una escuela la primera vez que te marcas).
struct GymPresenceSection: View {
    @ObservedObject var vm: GymDetailViewModel
    @State private var privacyOpen = false
    private let myUid = AppDependencies.shared.authBridge.currentUid()

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            GymSectionLabel(text: L("gym_section_who"))
            HStack(spacing: 10) {
                HStack(spacing: -12) {
                    ForEach(Array(vm.people.prefix(4)), id: \.uid) { person in
                        AvatarCircle(url: person.photoUrl, size: 30, uid: person.uid)
                            .overlay(Circle().stroke(Cumbre.paper, lineWidth: 2))
                    }
                }
                Text(vm.people.isEmpty ? L("gym_here_none") : L("gym_here_now", vm.people.count))
                    .font(Cumbre.serif(14, .semibold)).foregroundStyle(Cumbre.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button { if vm.iAmHere { toggle() } else { privacyOpen = true } } label: {
                    Text(L(vm.iAmHere ? "gym_here_leave" : "gym_here_join"))
                        .font(.system(size: 13, weight: .bold)).lineLimit(2)
                        .foregroundStyle(vm.iAmHere ? Cumbre.ink2 : Color.white)
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        .background(vm.iAmHere ? Cumbre.bg : Cumbre.terraFill, in: Capsule())
                }
                .buttonStyle(.plain)
                .disabled(vm.presenceBusy)
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: Cumbre.statCardRadius))
            if vm.presenceError {
                Text(L("gym_here_error")).font(.system(size: 12)).foregroundStyle(Cumbre.bad).padding(.top, 4)
            }
        }
        .task { await vm.loadPresence(myUid: myUid) }
        .sheet(isPresented: $privacyOpen) {
            PresencePrivacySheet { privacyOpen = false; toggle() }
        }
    }

    private func toggle() {
        Task { await vm.togglePresence(myUid: myUid) }
    }
}
