import SwiftUI
import Shared
import CoreLocation
import MapLibre

// ── ViewModel ──────────────────────────────────────────────────────────────

// Fila de quedada + pills de filtro + helpers de fecha/dias.
// Reparto del antiguo MeetupsView.swift de 1.182 lineas.

/// Tarjeta de una quedada en la lista (ronda 6): cubierta (foto o avatar del organizador con su aureola), lugar como en
/// la app (anillo de nota en roca, cuadrado en rocódromo), días con su nota, modalidad/cordada/privacidad y el material
/// que hay y el que falta. Espejo de `MeetupListItem.kt`.
struct MeetupRowView: View {
    let meetup: Meetup
    var dayScoresMap: [String: Int] = [:]
    var distanceKm: Double?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                cover
                VStack(alignment: .leading, spacing: 2) {
                    Text(meetup.name).font(Cumbre.serif(16, .bold)).foregroundStyle(Cumbre.ink).lineLimit(2)
                    if let u = meetup.creatorUsername {
                        Text("@\(u)").font(.system(size: 12)).foregroundStyle(Cumbre.ink2).lineLimit(1)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                badges
            }
            placeCard
            FlowLayoutView(spacing: 4) {
                ForEach(meetup.days, id: \.self) { day in
                    HStack(spacing: 4) {
                        Text(formatDayMonth(day)).font(.system(size: 11, weight: .medium)).foregroundStyle(Cumbre.ink2)
                        if let s = dayScoresMap["\(meetup.schoolId)_\(day)"] {
                            Text("\(s)").font(.system(size: 10, weight: .bold)).foregroundStyle(.white)
                                .padding(.horizontal, 4).padding(.vertical, 1).background(scoreColor(s), in: Capsule())
                        }
                    }
                    .padding(.horizontal, 6).padding(.vertical, 2).background(Cumbre.paper2, in: Capsule())
                }
            }
            FlowLayoutView(spacing: 6) {
                if let d = meetup.discipline { miniPill(disciplineLabel(d), Cumbre.ink2) }
                if meetup.isRopeTeam { miniPill(L("meetup_rope_route_label"), Cumbre.terraFill) }
                if meetup.privacy != "OPEN" {
                    HStack(spacing: 2) {
                        Image(systemName: "lock").font(.system(size: 10))
                        Text(privacyLabel(meetup.privacy)).font(.caption)
                    }
                    .foregroundStyle(Cumbre.ink2)
                }
            }
            gearRow
        }
        .padding(12)
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 20))
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(Cumbre.rule, lineWidth: 1))
        .padding(.horizontal, 16).padding(.vertical, 5)
    }

    @ViewBuilder private var cover: some View {
        if let url = meetup.photoUrl.flatMap({ URL(string: $0) }) {
            CachedImage(url: url) { $0.resizable().scaledToFill() } placeholder: { Cumbre.paper2 }
                .frame(width: 54, height: 54).clipShape(RoundedRectangle(cornerRadius: 16))
        } else if meetup.creatorPhotoUrl != nil {
            AvatarCircle(url: meetup.creatorPhotoUrl, size: 54, uid: meetup.creatorUid).padding(3)
        } else {
            Circle().fill(Cumbre.paper2).frame(width: 54, height: 54)
                .overlay(Image(systemName: "person.3").foregroundStyle(Cumbre.ink2))
        }
    }

    private var badges: some View {
        VStack(alignment: .trailing, spacing: 4) {
            if meetup.joined {
                Text(L("meetups_joined").uppercased()).font(.system(size: 10, weight: .bold)).foregroundStyle(.white)
                    .padding(.horizontal, 8).padding(.vertical, 2).background(Cumbre.terraFill, in: Capsule())
            } else if meetup.isFull {
                Text(L("meetups_full").uppercased()).font(.system(size: 10, weight: .bold)).foregroundStyle(Cumbre.ink2)
                    .padding(.horizontal, 8).padding(.vertical, 2).overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
            }
            HStack(spacing: 2) {
                Image(systemName: "person").font(.system(size: 10))
                Text(meetup.memberLimit.map { "\(meetup.memberCount)/\($0.int32Value)" } ?? "\(meetup.memberCount)").font(.caption)
            }
            .foregroundStyle(Cumbre.ink2)
        }
    }

    @ViewBuilder private var placeCard: some View {
        if let name = meetup.placeName ?? meetup.schoolName, !name.isEmpty {
            HStack(spacing: 10) {
                if meetup.isGymMeetup {
                    GymKindBadge(kind: .commercial, size: 40)
                } else {
                    let score = meetup.days.lazy.compactMap { dayScoresMap["\(meetup.schoolId)_\($0)"] }.first
                    ZStack {
                        Circle().stroke(Cumbre.rule, lineWidth: 4)
                        if let score {
                            Circle().trim(from: 0, to: CGFloat(score) / 100).stroke(Cumbre.score(score), style: StrokeStyle(lineWidth: 4, lineCap: .round))
                                .rotationEffect(.degrees(-90))
                        }
                        Text(score.map { "\($0)" } ?? "–").font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.ink)
                    }
                    .frame(width: 40, height: 40)
                }
                VStack(alignment: .leading, spacing: 1) {
                    Text(name).font(Cumbre.serif(15, .bold)).foregroundStyle(Cumbre.ink).lineLimit(2)
                    if let km = distanceKm { Text("\(Int(km)) km").font(.system(size: 12)).foregroundStyle(Cumbre.ink2) }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 10).padding(.vertical, 8)
            .background(Cumbre.bg.opacity(0.6), in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Cumbre.rule.opacity(0.7), lineWidth: 1))
        }
    }

    /// Material del grupo sin abrir la quedada (shared: `MeetupGearStatus.lines`).
    @ViewBuilder private var gearRow: some View {
        let lines = MeetupGearStatus.shared.lines(meetup: meetup)
        if !lines.isEmpty {
            FlowLayoutView(spacing: 6) {
                Text(L("meetup_list_material").uppercased()).eyebrow(Cumbre.ink2)
                ForEach(lines, id: \.item) { l in
                    let label = gearItemLabel(l.item)
                    miniPill(l.ok ? "✓ \(label)" : L("meetup_list_gear_missing", label.lowercased()), l.ok ? Cumbre.ok : Cumbre.bad)
                }
            }
        }
    }

    private func miniPill(_ text: String, _ color: Color) -> some View {
        Text(text).font(.system(size: 12, weight: .semibold)).foregroundStyle(color).lineLimit(1)
            .padding(.horizontal, 9).padding(.vertical, 3).background(color.opacity(0.12), in: Capsule())
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

struct FilterPill: View {
    let label: String; let selected: Bool; let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 12, weight: .bold))
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(selected ? Cumbre.terra : Cumbre.bg)
                .foregroundColor(selected ? .white : Cumbre.ink.opacity(0.7))
                .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius).stroke(selected ? Cumbre.terra : Cumbre.ink.opacity(0.25), lineWidth: 1))
                .cornerRadius(Cumbre.pillRadius)
        }
    }
}

struct FilterGroupLabel: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.system(size: 10, weight: .bold, design: .monospaced))
            .tracking(1.5)
            .foregroundColor(Cumbre.ink.opacity(0.5))
    }
}

/// Wrapping flow layout (like Android FlowRow). Uses SwiftUI Layout protocol (iOS 16+).
struct FlowLayoutView: Layout {
    var spacing: CGFloat = 6

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        // OJO: si el ancho propuesto es nil/infinito (SwiftUI mide la talla
        // intrínseca dentro de un ScrollView/LazyVStack), NO devolver un ancho
        // infinito → SwiftUI crashea. Caemos a una sola fila y devolvemos el
        // ancho REAL del contenido, nunca maxW infinito.
        let maxW: CGFloat = proposal.width.map { $0.isFinite ? $0 : .greatestFiniteMagnitude }
            ?? .greatestFiniteMagnitude
        var x: CGFloat = 0; var y: CGFloat = 0; var rowH: CGFloat = 0
        var widestRow: CGFloat = 0
        for sv in subviews {
            let s = sv.sizeThatFits(.unspecified)
            if x + s.width > maxW && x > 0 {
                widestRow = max(widestRow, x - spacing)
                x = 0; y += rowH + spacing; rowH = 0
            }
            x += s.width + spacing; rowH = max(rowH, s.height)
        }
        widestRow = max(widestRow, x - spacing)
        let finalW = (proposal.width.map { $0.isFinite } ?? false) ? maxW : max(0, widestRow)
        return CGSize(width: finalW, height: y + rowH)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX; var y = bounds.minY; var rowH: CGFloat = 0
        for sv in subviews {
            let s = sv.sizeThatFits(.unspecified)
            if x + s.width > bounds.maxX && x > bounds.minX { x = bounds.minX; y += rowH + spacing; rowH = 0 }
            sv.place(at: CGPoint(x: x, y: y), proposal: .init(s))
            x += s.width + spacing; rowH = max(rowH, s.height)
        }
    }
}

enum MeetupSheet: Identifiable, Equatable {
    case create
    case detail(String)
    case chat(String, String)   // convId, groupName
    case alert
    case schoolFilter
    var id: String {
        switch self {
        case .create: return "create"
        case .detail(let mid): return "detail_\(mid)"
        case .chat(let cid, _): return "chat_\(cid)"
        case .alert: return "alert"
        case .schoolFilter: return "schoolFilter"
        }
    }
}

func privacyLabel(_ privacy: String) -> String {
    switch privacy {
    case "FOLLOWERS": return L("Seguidos")
    case "WOMEN":     return L("No mixto")
    default:          return L("Abierta")
    }
}

func disciplineLabel(_ discipline: String) -> String {
    switch discipline {
    case "BOULDER": return L("Bloque")
    case "ROUTE":   return L("Via")
    case "BOTH":    return L("Bloque + Via")
    default:        return discipline
    }
}

func formatDayMonth(_ iso: String) -> String {
    let months = ["ene","feb","mar","abr","may","jun","jul","ago","sep","oct","nov","dic"]
    let parts = iso.split(separator: "-")
    guard parts.count == 3, let mo = Int(parts[1]), let d = Int(parts[2]) else { return iso }
    return "\(d) \(months[mo - 1])"
}

func scoreColor(_ score: Int) -> Color {
    if score >= 80 { return Color(red: 0.13, green: 0.77, blue: 0.37) }
    if score >= 60 { return Color(red: 0.96, green: 0.62, blue: 0.04) }
    if score >= 40 { return Color(red: 0.93, green: 0.27, blue: 0.27) }
    return Color(red: 0.42, green: 0.44, blue: 0.50)
}

struct MeetupDayInfo: Identifiable {
    let iso: String
    let label: String
    var id: String { iso }
}

func generateNextDays(_ count: Int) -> [MeetupDayInfo] {
    let cal = Calendar.current
    let formatter = DateFormatter()
    formatter.locale = LanguageManager.shared.locale
    let isoFormatter = DateFormatter()
    isoFormatter.dateFormat = "yyyy-MM-dd"
    let weekdays = ["dom","lun","mar","mie","jue","vie","sab"]
    let months = ["ene","feb","mar","abr","may","jun","jul","ago","sep","oct","nov","dic"]

    var result: [MeetupDayInfo] = []
    let today = Date()
    for i in 0..<count {
        guard let date = cal.date(byAdding: .day, value: i, to: today) else { continue }
        let comps = cal.dateComponents([.weekday, .day, .month], from: date)
        let wd = weekdays[(comps.weekday ?? 1) - 1]
        let d = comps.day ?? 1
        let mo = months[(comps.month ?? 1) - 1]
        let iso = isoFormatter.string(from: date)
        result.append(MeetupDayInfo(iso: iso, label: "\(wd) \(d) \(mo)"))
    }
    return result
}

// ── School filter sheet ────────────────────────────────────────────────────

// ── Mapa de quedadas ──────────────────────────────────────────────────────

struct SchoolMeetupGroup: Identifiable {
    let schoolId: String
    let schoolName: String
    let lat: Double
    let lon: Double
    let count: Int
    var id: String { schoolId }
}
