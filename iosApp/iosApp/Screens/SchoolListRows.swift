import SwiftUI
import Shared
import CoreLocation

// Lista de escuelas — réplica fiel de SchoolListScreen.kt de Android:
// fila de iconos, header "Escuelas" + count + "+ Enviar escuela", banner ☕,

// Filas de la lista de escuelas: selector de dias, item, badges, estados.
// Reparto de SchoolListView.swift.

struct DaySelectorRow: View {
    @ObservedObject var vm: SchoolListViewModel

    fileprivate static let isoFmt: DateFormatter = {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.locale = Locale(identifier: "en_US_POSIX"); return f
    }()
    private let dayLetters = CalendarLabels.weekdaysShortSunFirst().map { $0.uppercased() }  // weekday 1=domingo

    /// "HOY" sin días; "VIE" con uno; "VIE–DOM" si son seguidos; "3 DÍAS" si no.
    static func rangeLabel(_ isos: [String]) -> String {
        guard !isos.isEmpty else { return L("HOY") }
        let cal = Calendar(identifier: .gregorian)
        let dates = isos.compactMap { isoFmt.date(from: $0) }.sorted()
        let letters = CalendarLabels.weekdaysShortSunFirst().map { $0.uppercased() }
        func name(_ d: Date) -> String { letters[cal.component(.weekday, from: d) - 1] }
        guard let first = dates.first, let last = dates.last else { return L("%@ DÍAS", isos.count) }
        if dates.count == 1 { return name(first) }
        let span = cal.dateComponents([.day], from: first, to: last).day ?? 0
        return span == dates.count - 1 ? "\(name(first))–\(name(last))" : L("%@ DÍAS", dates.count)
    }

    private var next7: [Date] {
        let cal = Calendar(identifier: .gregorian)
        let today = cal.startOfDay(for: Date())
        return (0..<7).compactMap { cal.date(byAdding: .day, value: $0, to: today) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(vm.selectedDates.isEmpty ? L("Elige hasta 5 días para comparar el tramo")
                     : L("%@ elegido%@ · nota = media de los días", vm.selectedDates.count, vm.selectedDates.count > 1 ? "s" : ""))
                    .font(.system(size: 11.5)).foregroundStyle(Cumbre.ink3)
                Spacer()
                if !vm.selectedDates.isEmpty {
                    Button { vm.clearDates() } label: {
                        Text(L("VOLVER A HOY")).font(Cumbre.mono(9.5, .bold)).tracking(0.8).foregroundStyle(Cumbre.terra)
                    }.buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12)
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
                            .background(selected ? Cumbre.terra : Cumbre.paper,
                                        in: RoundedRectangle(cornerRadius: Cumbre.statCardRadius))
                            .overlay(RoundedRectangle(cornerRadius: Cumbre.statCardRadius)
                                .stroke(Cumbre.rule, lineWidth: 1))
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
                Text(L("LLUEVE %@", rainy)).font(.system(size: 11, weight: .semibold)).tracking(0.8)
                    .foregroundStyle(Cumbre.bad)
                if range.maxRainMm > 0 {
                    Text(String(format: L("máx %.1f mm"), range.maxRainMm))
                        .font(.system(size: 10)).foregroundStyle(Cumbre.ink3)
                }
            }
        }
    }
}

/// "2026-06-17" → "L"/"M"/"X"/"J"/"V"/"S"/"D".
private let weekdayFormatter: DateFormatter = {
    let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.locale = Locale(identifier: "en_US_POSIX"); return f
}()

func weekdayLetter(_ iso: String) -> String {
    // Formatter ESTATICO: crear uno por celda (~ms) × cientos de celdas
    // atascaba el hilo principal (hallazgo P4 del barrido anti-watchdog).
    guard let d = weekdayFormatter.date(from: iso) else { return String(iso.suffix(2)) }
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
            .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius).stroke(Cumbre.ink, lineWidth: 1))
    }
}

// MARK: - Fila rica (réplica de SchoolListItem.kt)

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
            Text(L("Error: %@", message)).font(.system(size: 15)).foregroundStyle(Cumbre.bad)
            Button(action: onRetry) { OutlinedCumbreButton(text: NSLocalizedString("common_retry", comment: "")) }
        }
        .frame(maxWidth: .infinity).padding(40)
    }
}

#Preview { SchoolListView() }
