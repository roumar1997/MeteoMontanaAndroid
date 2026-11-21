import SwiftUI
import Shared

// Detalle de quedada moderno (maqueta 5): cabecera de color, chips, días y material del grupo como semáforo.
// Espejo de MeetupDetailBody.kt.

private enum MeetupHeroTone {
    static let gymBlue = Color(hex: 0x3B6FD6)
    static let gymPurple = Color(hex: 0x7A45C4)
    static let cragGreen = Color(hex: 0x1FA84E)
    static let cragTeal = Color(hex: 0x1F7F8C)
}

/// Cabecera de color con volver · chat · cómo llegar · compartir (· denunciar), chips de estado, nombre y «día · lugar».
struct MeetupHero<Chat: View, Share: View>: View {
    let meetup: Meetup
    let canReport: Bool
    let onBack: () -> Void
    let onReport: () -> Void
    @ViewBuilder let chat: () -> Chat
    @ViewBuilder let share: () -> Share

    var body: some View {
        let colors = meetup.isGymMeetup ? [MeetupHeroTone.gymBlue, MeetupHeroTone.gymPurple]
            : [MeetupHeroTone.cragGreen, MeetupHeroTone.cragTeal]
        let place = meetup.placeName ?? meetup.schoolName
        let subtitle = [meetup.days.first.map { detailFormatDayMonth($0) }, place].compactMap { $0 }.joined(separator: " · ")
        let spots = meetup.memberLimit.map { max(0, Int(truncating: $0) - Int(meetup.memberCount)) }
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                round("chevron.left", L("common_back"), onBack)
                Spacer()
                chat()
                if let lat = meetup.schoolLat?.doubleValue, let lon = meetup.schoolLon?.doubleValue {
                    round("arrow.triangle.turn.up.right.diamond", L("common_directions")) { openDirections(lat: lat, lon: lon) }
                }
                share()
                if canReport { round("flag", L("common_report"), onReport) }
            }
            FlowLayoutView(spacing: 6) {
                if let d = meetup.discipline { chip(disciplineLabel(d), Color.white.opacity(0.22)) }
                chip(privacyLabel(meetup.privacy), Color.white.opacity(0.22))
                if meetup.isFull {
                    chip(L("meetup_full_chip"), Color(hex: 0xB45309))
                } else if let spots {
                    chip(LP("meetup_spots_left", spots), MeetupHeroTone.cragGreen)
                }
            }
            .padding(.top, 32)
            Text(meetup.name).font(Cumbre.serif(28, .bold)).foregroundStyle(.white).padding(.top, 10)
            if !subtitle.isEmpty {
                Text(subtitle).font(.system(size: 14)).foregroundStyle(Color.white.opacity(0.9)).padding(.top, 4)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background {
            ZStack {
                LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing)
                if let s = meetup.photoUrl, let url = URL(string: s) {
                    CachedImage(url: url) { $0.resizable().scaledToFill() } placeholder: { Color.clear }
                }
                LinearGradient(colors: [.clear, Color.black.opacity(0.55)], startPoint: .top, endPoint: .bottom)
            }
            .clipped()
        }
    }

    private func chip(_ text: String, _ fill: Color) -> some View {
        Text(text.uppercased()).eyebrow(.white).lineLimit(1)
            .padding(.horizontal, 10).padding(.vertical, 5).background(fill, in: Capsule())
    }

    private func round(_ symbol: String, _ a11y: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) { MeetupHeroIcon(symbol: symbol) }.buttonStyle(.plain).accessibilityLabel(a11y)
    }
}

/// Botón redondo blanco de la cabecera (para usar también dentro de un NavigationLink o un ShareLink).
struct MeetupHeroIcon: View {
    let symbol: String
    var body: some View {
        Image(systemName: symbol).font(.system(size: 17, weight: .semibold)).foregroundStyle(Color(hex: 0x2A2924))
            .frame(width: 44, height: 44).background(Color.white.opacity(0.92), in: Circle())
    }
}

/// Nivel (colores de grado), cuántos van y los compañeros buscados.
struct MeetupChips: View {
    let meetup: Meetup
    var body: some View {
        FlowLayoutView(spacing: 8) {
            if let min = meetup.gradeMin {
                let max = meetup.gradeMax ?? min
                let same = min.lowercased() == max.lowercased()
                chip(same ? min.uppercased() : "\(min.uppercased())–\(max.uppercased())", dots: same ? [min] : [min, max])
            }
            if let limit = meetup.memberLimit {
                chip(L("meetup_chip_going_of", meetup.memberCount, limit))
            } else {
                chip(LP("meetup_chip_going", Int(meetup.memberCount)))
            }
            if meetup.isRopeTeam {
                chip(L("meetup_facts_rope_team"))
            } else if let p = meetup.partnersWanted {
                chip(LP("meetup_facts_partners", Int(truncating: p)))
            }
        }
        .padding(.horizontal, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func chip(_ text: String, dots: [String] = []) -> some View {
        HStack(spacing: 6) {
            ForEach(dots, id: \.self) { g in
                Circle().fill(GradeColor.chip(g)).frame(width: 11, height: 11).overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
            }
            Text(text).font(.system(size: 13, weight: .semibold)).foregroundStyle(Cumbre.ink).lineLimit(1)
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
        .background(Cumbre.paper, in: Capsule()).overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
    }
}

/// Días propuestos con la nota de cada uno.
struct MeetupDaysRow: View {
    let days: [String]
    let scores: [String: Int]
    var body: some View {
        if days.count >= 2 || !scores.isEmpty {
            FlowLayoutView(spacing: 8) {
                ForEach(days, id: \.self) { d in
                    HStack(spacing: 6) {
                        Text(detailFormatDayMonth(d)).font(.system(size: 13, weight: .semibold)).foregroundStyle(Cumbre.ink)
                        if let s = scores[d] {
                            Text("\(s)").font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
                                .padding(.horizontal, 6).padding(.vertical, 2).background(detailScoreColor(s), in: Capsule())
                        }
                    }
                    .padding(.horizontal, 12).padding(.vertical, 7)
                    .background(Cumbre.paper, in: Capsule()).overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
                }
            }
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// «Material del grupo»: un recuadro por cosa pedida, verde si está cubierta y terracota si falta.
struct MeetupGearSemaphore: View {
    let meetup: Meetup
    var body: some View {
        let cov = meetup.gearCoverage
        if !cov.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text(L("meetup_gear_group").uppercased()).eyebrow(Cumbre.ink2)
                    Spacer()
                    Text(L("meetup_gear_covered", cov.filter { $0.ok }.count, cov.count)).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                }
                FlowLayoutView(spacing: 8) {
                    ForEach(cov, id: \.item) { c in
                        let fg = c.ok ? Color(hex: 0x16803C) : Cumbre.terraFill
                        VStack(spacing: 1) {
                            Image(systemName: c.ok ? "checkmark" : "exclamationmark").font(.system(size: 18, weight: .bold)).foregroundStyle(fg)
                            Text(gearItemLabel(c.item)).font(.system(size: 12, weight: .semibold)).foregroundStyle(fg).lineLimit(1)
                            Text("\(c.provided)/\(c.required)").font(.system(size: 11)).foregroundStyle(fg)
                        }
                        .padding(.horizontal, 14).padding(.vertical, 10)
                        .background((c.ok ? Color(hex: 0x1FA84E) : Cumbre.terraFill).opacity(0.16), in: RoundedRectangle(cornerRadius: 14))
                    }
                }
            }
            .padding(14).cardChrome(20).padding(.horizontal, 16)
        }
    }
}
