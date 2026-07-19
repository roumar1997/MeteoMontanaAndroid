import SwiftUI
import Shared
import CoreLocation

// Lista de escuelas — réplica fiel de SchoolListScreen.kt de Android:
// fila de iconos, header "Escuelas" + count + "+ Enviar escuela", banner ☕,

// Filas de la lista de escuelas: selector de dias, item, badges, estados.
// Reparto de SchoolListView.swift.

struct DaySelectorRow: View {
    @ObservedObject var vm: SchoolListViewModel

    private static let isoFmt: DateFormatter = {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.locale = Locale(identifier: "en_US_POSIX"); return f
    }()
    private let dayLetters = ["DOM", "LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB"]  // weekday 1=domingo

    private var next7: [Date] {
        let cal = Calendar(identifier: .gregorian)
        let today = cal.startOfDay(for: Date())
        return (0..<7).compactMap { cal.date(byAdding: .day, value: $0, to: today) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(vm.selectedDates.isEmpty ? "DÍAS · elige hasta 5 para comparar el tramo"
                 : "DÍAS · \(vm.selectedDates.count) elegido\(vm.selectedDates.count > 1 ? "s" : "")")
                .eyebrow().padding(.horizontal, 12)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(next7, id: \.self) { d in
                        let iso = Self.isoFmt.string(from: d)
                        let cal = Calendar(identifier: .gregorian)
                        let weekday = cal.component(.weekday, from: d)
                        let dayNum = cal.component(.day, from: d)
                        let selected = vm.selectedDates.contains(iso)
                        Button { vm.toggleDate(iso) } label: {
                            VStack(spacing: 1) {
                                Text(dayLetters[weekday - 1]).font(Cumbre.mono(11, .bold)).tracking(0.6)
                                    .foregroundStyle(selected ? .white : Cumbre.ink2)
                                Text("\(dayNum)").font(.system(size: 10))
                                    .foregroundStyle(selected ? .white.opacity(0.85) : Cumbre.ink3)
                            }
                            .padding(.horizontal, 12).padding(.vertical, 7)
                            .background(selected ? Cumbre.terra : Cumbre.paper)
                            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 12)
            }
        }
        .padding(.vertical, 8)
    }
}

/// Fila por día del tramo: una celda por día con su score (color) y la inicial
/// del día debajo. Los días con lluvia llevan la inicial y el borde en rojo.
struct DayRangeRow: View {
    let range: RangeScore
    var body: some View {
        HStack(spacing: 3) {
            ForEach(Array(range.days.enumerated()), id: \.offset) { _, d in
                let score = Int(d.score)
                VStack(spacing: 2) {
                    Text("\(score)")
                        .font(Cumbre.mono(11, .bold))
                        .foregroundStyle(Cumbre.score(score))
                        .frame(width: 26, height: 22)
                        .background(Cumbre.score(score).opacity(0.18))
                        .overlay(Rectangle().stroke(d.rainy ? Cumbre.bad : Cumbre.score(score), lineWidth: 1))
                    Text(weekdayLetter(d.date))
                        .font(.system(size: 9))
                        .foregroundStyle(d.rainy ? Cumbre.bad : Cumbre.ink3)
                }
            }
        }
    }
}

/// Resumen de lluvia del tramo: qué días llueve (iniciales) + máximo de mm.
struct RainSummaryTag: View {
    let range: RangeScore
    var body: some View {
        VStack(alignment: .trailing, spacing: 2) {
            if range.rainDays == 0 {
                Text("● SIN LLUVIA").font(.system(size: 11, weight: .semibold)).tracking(0.8)
                    .foregroundStyle(Cumbre.ok)
            } else {
                let rainy = range.days.filter { $0.rainy }.map { weekdayLetter($0.date) }.joined(separator: " ")
                Text("LLUEVE \(rainy)").font(.system(size: 11, weight: .semibold)).tracking(0.8)
                    .foregroundStyle(Cumbre.bad)
                if range.maxRainMm > 0 {
                    Text(String(format: "máx %.1f mm", range.maxRainMm))
                        .font(.system(size: 10)).foregroundStyle(Cumbre.ink3)
                }
            }
        }
    }
}

/// "2026-06-17" → "L"/"M"/"X"/"J"/"V"/"S"/"D".
func weekdayLetter(_ iso: String) -> String {
    let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.locale = Locale(identifier: "en_US_POSIX")
    guard let d = f.date(from: iso) else { return String(iso.suffix(2)) }
    let weekday = Calendar(identifier: .gregorian).component(.weekday, from: d) // 1=domingo
    let labels = ["D", "L", "M", "X", "J", "V", "S"]
    return labels[weekday - 1]
}

struct OutlinedCumbreButton: View {
    let text: String
    var tint: Color = Cumbre.ink
    var body: some View {
        Text(text)
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(tint)
            .padding(.horizontal, 12).padding(.vertical, 8)
            .overlay(Rectangle().stroke(Cumbre.ink, lineWidth: 1))
    }
}

// MARK: - Fila rica (réplica de SchoolListItem.kt)

struct SchoolListItemView: View {
    let rank: Int
    let school: School
    let score: SchoolScore?
    var range: RangeScore? = nil
    var distanceKm: Int? = nil
    var isFavorite: Bool = false
    var isSelected: Bool = false
    var onToggleFavorite: () -> Void = {}

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            // Modo tramo → el badge muestra el score combinado de los días.
            ScoreBadge(score: range.map { Int($0.combinedScore) } ?? score.map { Int($0.todayScore) })
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .center, spacing: 0) {
                    Text(String(format: "%02d", rank))
                        .font(Cumbre.mono(11, .semibold))
                        .tracking(1.4)
                        .foregroundStyle(Cumbre.ink3)
                        .frame(width: 24, alignment: .leading)
                    Text(school.name)
                        .font(Cumbre.serif(19, .bold))
                        .foregroundStyle(Cumbre.ink)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    // Estrella tocable con update optimista. BorderlessButtonStyle
                    // para que reciba el tap sin disparar la navegación de la fila.
                    Button(action: onToggleFavorite) {
                        Image(systemName: isFavorite ? "star.fill" : "star")
                            .font(.system(size: 18))
                            .foregroundStyle(isFavorite ? Cumbre.terra : Cumbre.ink3)
                            .frame(width: 36, height: 36)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.borderless)
                }
                Text(subtitle)
                    .font(Cumbre.mono(12))
                    .foregroundStyle(Cumbre.ink3)
                    .padding(.top, 4)
                HStack(alignment: .center, spacing: 8) {
                    if let range {
                        DayRangeRow(range: range)
                        Spacer(minLength: 4)
                        RainSummaryTag(range: range)
                    } else {
                        HeatmapBar(scores: score?.hourlyScores.map { $0.intValue })
                        DryWetTag(dry: score?.dryRock, rainProb: score.map { Int($0.rainProb) }, rainMm: score?.rainMm)
                    }
                }
                .padding(.top, 8)
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 12)
        .overlay(isSelected ? Rectangle().stroke(Cumbre.terra, lineWidth: 2) : nil)
        .contentShape(Rectangle())
    }

    private var subtitle: String {
        var parts: [String] = []
        if let r = school.rockType, !r.isEmpty { parts.append(r.uppercased()) }
        if let reg = school.region, !reg.isEmpty { parts.append(reg) }
        if let km = distanceKm { parts.append("\(km) KM") }
        return parts.joined(separator: "  ·  ")
    }
}

struct ScoreBadge: View {
    let score: Int?
    var body: some View {
        let color = score.map { Cumbre.score($0) } ?? Cumbre.rule
        VStack(spacing: 1) {
            Text(score.map(String.init) ?? "—")
                .font(Cumbre.serif(28, .bold))
                .foregroundStyle(score != nil ? color : Cumbre.ink2)
            Text(Cumbre.scoreLabel(score))
                .font(.system(size: 8, weight: .bold))
                .tracking(0.6)
                .foregroundStyle(score != nil ? color : Cumbre.ink3)
        }
        .frame(width: 64, height: 72)
        .background((score != nil ? color : Cumbre.paper).opacity(score != nil ? 0.12 : 1))
        .overlay(RoundedRectangle(cornerRadius: 2).stroke(color, lineWidth: 1.5))
        .clipShape(RoundedRectangle(cornerRadius: 2))
    }
}

struct HeatmapBar: View {
    let scores: [Int]?
    var body: some View {
        let cells: [Int?] = {
            if let s = scores, !s.isEmpty { return Array(s.prefix(10)).map { Optional($0) } }
            return Array(repeating: nil, count: 10)
        }()
        HStack(spacing: 0) {
            ForEach(Array(cells.enumerated()), id: \.offset) { _, s in
                Rectangle().fill(s.map { Cumbre.score($0) } ?? Cumbre.rule.opacity(0.35))
            }
        }
        .frame(height: 16)
        .frame(maxWidth: .infinity)
    }
}

struct DryWetTag: View {
    let dry: Bool?
    let rainProb: Int?
    let rainMm: Double?
    var body: some View {
        VStack(alignment: .trailing, spacing: 2) {
            if let dry {
                Text(dry ? "● SECA" : "● MOJADA")
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(0.8)
                    .foregroundStyle(dry ? Cumbre.ok : Cumbre.bad)
            }
            if dry == false, let p = rainProb, p > 0 {
                Text("\(p)%").font(.system(size: 10)).foregroundStyle(Cumbre.bad)
            }
        }
    }
}

// MARK: - Estados

struct SkeletonRow: View {
    var body: some View {
        let tone = Cumbre.ink3.opacity(0.12)
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 2).fill(tone).frame(width: 64, height: 72)
            VStack(alignment: .leading, spacing: 6) {
                RoundedRectangle(cornerRadius: 2).fill(tone).frame(width: 160, height: 16)
                RoundedRectangle(cornerRadius: 2).fill(tone).frame(width: 110, height: 12)
                RoundedRectangle(cornerRadius: 2).fill(tone).frame(height: 14)
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 12)
    }
}

struct EmptyRow: View {
    let canClear: Bool
    let onClear: () -> Void
    var body: some View {
        VStack(spacing: 12) {
            Text("No hay escuelas con esos filtros")
                .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
            if canClear {
                Button(action: onClear) { OutlinedCumbreButton(text: NSLocalizedString("schools_clear_filters", comment: "")) }
            }
        }
        .frame(maxWidth: .infinity).padding(32)
    }
}

struct ErrorRow: View {
    let message: String
    let onRetry: () -> Void
    var body: some View {
        VStack(spacing: 12) {
            Text("Error: \(message)").font(.system(size: 15)).foregroundStyle(Cumbre.bad)
            Button(action: onRetry) { OutlinedCumbreButton(text: NSLocalizedString("common_retry", comment: "")) }
        }
        .frame(maxWidth: .infinity).padding(40)
    }
}

#Preview { SchoolListView() }
