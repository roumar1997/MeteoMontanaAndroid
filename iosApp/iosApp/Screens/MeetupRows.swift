import SwiftUI
import Shared
import CoreLocation
import MapLibre

// ── ViewModel ──────────────────────────────────────────────────────────────

// Fila de quedada + pills de filtro + helpers de fecha/dias.
// Reparto del antiguo MeetupsView.swift de 1.182 lineas.

struct MeetupRowView: View {
    let meetup: Meetup
    var dayScoresMap: [String: Int] = [:]
    var distanceKm: Double?

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            // Photo/avatar
            let photo = meetup.photoUrl  // solo la foto de la quedada, no la del creador
            ZStack {
                RoundedRectangle(cornerRadius: 2).fill(Cumbre.ink.opacity(0.06))
                if let url = photo.flatMap({ URL(string: $0) }) {
                    AsyncImage(url: url) { img in img.resizable().scaledToFill() }
                        placeholder: { Color.clear }
                        .clipped()
                } else {
                    Image(systemName: "person.3").foregroundColor(Cumbre.ink.opacity(0.4))
                }
            }
            .frame(width: 50, height: 50)
            .clipShape(RoundedRectangle(cornerRadius: 2))

            VStack(alignment: .leading, spacing: 3) {
                // Eyebrow: school + distance
                let eyebrow: String = {
                    var parts: [String] = []
                    if let s = meetup.schoolName { parts.append(s.uppercased()) }
                    if let km = distanceKm { parts.append("\(Int(km)) KM") }
                    return parts.joined(separator: " \u{00B7} ")
                }()
                if !eyebrow.isEmpty {
                    Text(eyebrow)
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .tracking(1.5)
                        .foregroundColor(Cumbre.ink.opacity(0.5))
                }
                // Name in serif
                Text(meetup.name)
                    .font(Cumbre.serif(16, .medium))

                // Days with individual scores
                FlowLayoutView {
                    ForEach(meetup.days, id: \.self) { day in
                        let score = dayScoresMap["\(meetup.schoolId)_\(day)"]
                        HStack(spacing: 4) {
                            Text(formatDayMonth(day))
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(Cumbre.ink.opacity(0.7))
                            if let s = score {
                                Text("\(s)")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 4).padding(.vertical, 1)
                                    .background(scoreColor(s))
                                    .clipShape(RoundedRectangle(cornerRadius: 2))
                            }
                        }
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(Cumbre.ink.opacity(0.05))
                        .clipShape(RoundedRectangle(cornerRadius: 2))
                    }
                }

                // Meta: discipline + privacy
                HStack(spacing: 4) {
                    if let disc = meetup.discipline {
                        Text(disciplineLabel(disc)).font(.caption).foregroundColor(Cumbre.ink.opacity(0.6))
                    }
                    if meetup.privacy != "OPEN" {
                        HStack(spacing: 2) {
                            Image(systemName: "lock").font(.system(size: 9)).foregroundColor(Cumbre.ink.opacity(0.5))
                            Text(privacyLabel(meetup.privacy)).font(.caption).foregroundColor(Cumbre.ink.opacity(0.6))
                        }
                    }
                }
            }

            Spacer()

            // Right: badge + members
            VStack(alignment: .trailing, spacing: 4) {
                if meetup.joined {
                    Text(NSLocalizedString("meetups_joined", comment: ""))
                        .font(.system(size: 10, weight: .bold))
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(Cumbre.terra)
                        .foregroundColor(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 2))
                } else if meetup.isFull {
                    Text(NSLocalizedString("meetups_full", comment: ""))
                        .font(.system(size: 10, weight: .bold))
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.ink.opacity(0.2), lineWidth: 1))
                        .foregroundColor(Cumbre.ink.opacity(0.5))
                }
                HStack(spacing: 2) {
                    Image(systemName: "person").font(.system(size: 10)).foregroundColor(Cumbre.ink.opacity(0.5))
                    let limitText: String = {
                        if let lim = meetup.memberLimit { return "\(meetup.memberCount)/\(lim.int32Value)" }
                        return "\(meetup.memberCount)"
                    }()
                    Text(limitText).font(.caption).foregroundColor(Cumbre.ink.opacity(0.6))
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Cumbre.bg)
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
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(selected ? Cumbre.terra : Cumbre.ink.opacity(0.25), lineWidth: 1))
                .cornerRadius(4)
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
    case "FOLLOWERS": return "Seguidos"
    case "WOMEN":     return "No mixto"
    default:          return "Abierta"
    }
}

func disciplineLabel(_ discipline: String) -> String {
    switch discipline {
    case "BOULDER": return "Bloque"
    case "ROUTE":   return "Via"
    case "BOTH":    return "Bloque + Via"
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
    formatter.locale = Locale(identifier: "es_ES")
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
