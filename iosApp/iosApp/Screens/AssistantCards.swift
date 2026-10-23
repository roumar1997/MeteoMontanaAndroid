import SwiftUI
import Shared

/// Tarjetas de resultados del asistente: escuelas recomendadas y desglose por
/// sectores. Solo pintan datos reales que ya vienen calculados del servidor.

// MARK: - Escuelas recomendadas

struct AssistantRecommendationView: View {
    let recommendation: AssistantRecommendation

    var body: some View {
        VStack(spacing: 8) {
            ForEach(Array(recommendation.schools.enumerated()), id: \.element.schoolId) { index, card in
                AssistantSchoolCardView(rank: index + 1, card: card,
                                        showForecast: recommendation.forecastAvailable,
                                        filtered: recommendation.filtered)
            }
        }
    }
}

struct AssistantSchoolCardView: View {
    @EnvironmentObject private var assistant: AssistantViewModel
    let rank: Int
    let card: AssistantSchoolCard
    let showForecast: Bool
    /// Si el recuento lleva grado/modalidad/roca pedidos; si no, es el total de la escuela y no "de ese grado".
    let filtered: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                // El puesto en un círculo, como la nota en las tarjetas de Escuelas.
                Text("\(rank)")
                    .font(Cumbre.serif(16, .bold)).foregroundStyle(Cumbre.terra)
                    .frame(width: 34, height: 34)
                    .background(Cumbre.terraBg, in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text(card.name)
                        .font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink)
                    Text(subtitle)
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                }
                Spacer(minLength: 6)
                Button { assistant.requestOpen(schoolId: card.schoolId) } label: {
                    Text(L("ABRIR"))
                        .font(Cumbre.mono(10, .bold)).tracking(1.4)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 12).padding(.vertical, 7)
                        .background(Cumbre.terraFill, in: Capsule())
                }
            }

            if showForecast && !card.days.isEmpty {
                HStack(spacing: 6) {
                    ForEach(card.days, id: \.date) { AssistantDayCell(day: $0) }
                }
            }

            Text(filtered ? L("%@ vías o bloques de ese grado", Int(card.lineCount))
                          : L("%@ vías y bloques en total", Int(card.lineCount)))
                .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink)
        }
        .padding(12)
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Cumbre.rule, lineWidth: 1))
    }

    private var subtitle: String {
        var parts: [String] = []
        if let km = card.distanceKm { parts.append(String(format: "%.0f km", km.doubleValue)) }
        if let rock = card.rockType, !rock.isEmpty { parts.append(rockLabel(rock)) }
        return parts.joined(separator: " · ")
    }
}

/// Un día del pronóstico: abreviatura, score con su color y lluvia.
struct AssistantDayCell: View {
    let day: AssistantDay

    var body: some View {
        VStack(spacing: 2) {
            Text(AssistantPresenter.dayLabel(day.date))
                .font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.ink2)
            Text("\(Int(day.score))")
                .font(Cumbre.serif(20, .bold)).foregroundStyle(Cumbre.score(Int(day.score)))
            Text(day.rainy ? L("LLUVIA") : L("SECO"))
                .font(Cumbre.mono(9, .bold))
                .foregroundStyle(day.rainy ? Cumbre.rain : Cumbre.ink3)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 6)
        .background(Cumbre.bg, in: RoundedRectangle(cornerRadius: 8))
    }
}

// MARK: - Desglose por sectores

struct AssistantBreakdownView: View {
    @EnvironmentObject private var assistant: AssistantViewModel
    let breakdown: AssistantBreakdown

    var body: some View {
        VStack(spacing: 8) {
            ForEach(Array(breakdown.sectors.enumerated()), id: \.offset) { _, sector in
                AssistantSectorView(sector: sector, sun: breakdown.sun, schoolId: breakdown.school.id)
            }
            Button { assistant.requestOpen(schoolId: breakdown.school.id) } label: {
                Text(L("ABRIR %@", breakdown.school.name.uppercased()))
                    .font(Cumbre.mono(10, .bold)).tracking(1.4)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 11)
                    .background(Cumbre.terraFill, in: Capsule())
            }
            .padding(.top, 2)
        }
    }
}

/// Un sector como tarjeta, con el mismo lenguaje que «Escuelas y sectores» del diario (Álvaro, 2026-10-23: «que se
/// vea como en el perfil, no tan soso»): foto de una de sus piedras, nombre, barra de cuántas piedras cumplen lo
/// pedido frente a las que podrían cumplir (sin orientación votada), resumen y desplegable con las piedras.
struct AssistantSectorView: View {
    let sector: AssistantSector
    let sun: String?
    let schoolId: String
    @State private var expanded = false

    private var hasStones: Bool { !sector.stones.isEmpty || !sector.unknownStones.isEmpty }
    /// La primera piedra con foto (las que cumplen primero) para la miniatura del sector.
    private var coverStone: AssistantStone? {
        (sector.stones + sector.unknownStones).first { ($0.photoPath ?? "").isEmpty == false }
    }
    /// Parte de la barra: piedras que cumplen / (las que cumplen + las que podrían). Con los recuentos del servidor,
    /// no con las listas (que pueden venir recortadas).
    private var matching: Int { Int(sector.matching) }
    private var unknown: Int { Int(sector.unknownOrientation) }
    private var confirmedShare: CGFloat {
        let total = matching + unknown
        return total == 0 ? 0 : CGFloat(matching) / CGFloat(total)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button {
                if hasStones { withAnimation(.easeInOut(duration: 0.2)) { expanded.toggle() } }
            } label: {
                HStack(spacing: 12) {
                    cover
                    VStack(alignment: .leading, spacing: 6) {
                        Text(sector.name ?? L("Sin sector"))
                            .font(.system(size: 16, weight: .semibold)).foregroundStyle(Cumbre.ink)
                            .lineLimit(1)
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule().fill(Cumbre.rule.opacity(0.5))
                                Capsule().fill(Cumbre.terraFill)
                                    .frame(width: max(confirmedShare > 0 ? 6 : 0, geo.size.width * confirmedShare))
                            }
                        }
                        .frame(height: 5)
                        Text(AssistantPresenter.sectorSummary(sector, sun: sun))
                            .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra)
                            .lineLimit(2)
                    }
                    if hasStones {
                        Image(systemName: expanded ? "chevron.up" : "chevron.down")
                            .font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.ink3)
                    }
                }
                .padding(10)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if expanded {
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(sector.stones, id: \.blockId) { AssistantStoneRow(stone: $0, confirmed: true, schoolId: schoolId) }
                    if !sector.unknownStones.isEmpty {
                        Text(L("SIN ORIENTACIÓN VOTADA (podrían cumplir)"))
                            .font(Cumbre.mono(9, .bold)).tracking(1.4).foregroundStyle(Cumbre.ink3)
                            .padding(.horizontal, 4).padding(.top, 4)
                        ForEach(sector.unknownStones, id: \.blockId) { AssistantStoneRow(stone: $0, confirmed: false, schoolId: schoolId) }
                    }
                }
                .padding(.horizontal, 10).padding(.bottom, 10)
            }
        }
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Cumbre.rule, lineWidth: 1))
    }

    /// Foto de una piedra del sector; si ninguna tiene foto, el número de piedras en grande.
    @ViewBuilder private var cover: some View {
        if let stone = coverStone, let photo = stone.photoPath {
            MiniTopoThumbnail(photoUrl: photo, points: dedupPoints(TopoParse.points(stone.linePath)), grade: stone.grade)
                .frame(width: 58, height: 58)
                .clipShape(RoundedRectangle(cornerRadius: 10))
        } else {
            VStack(spacing: 0) {
                Text("\(matching + unknown)")
                    .font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.terra)
                Text(L("piedras")).font(Cumbre.mono(8)).foregroundStyle(Cumbre.ink3)
            }
            .frame(width: 58, height: 58)
            .background(Cumbre.bg, in: RoundedRectangle(cornerRadius: 10))
        }
    }
}

/// Una piedra del desglose. Si tiene alguna vía, al tocarla se abre la piedra
/// (a través de esa vía, como hace el resto de la app); si no, solo se muestra.
struct AssistantStoneRow: View {
    @EnvironmentObject private var assistant: AssistantViewModel
    /// Grados partidos por altura (se piden juntos para todas las filas).
    @ObservedObject private var gradeSplits = GradeSplitsStore.shared
    let stone: AssistantStone
    let confirmed: Bool
    let schoolId: String

    var body: some View {
        Group {
            if let line = stone.firstLineId {
                Button { assistant.requestOpen(schoolId: schoolId, viaId: line) } label: {
                    content(showsChevron: true)
                }
                .buttonStyle(.plain)
            } else {
                content(showsChevron: false)
            }
        }
        .onAppear { gradeSplits.want(stone.firstLineId) }
    }

    private func content(showsChevron: Bool) -> some View {
        HStack(spacing: 10) {
            // (el .onAppear con los grados partidos está en el cuerpo de la fila)
            // La misma miniatura con el trazo de la vía que en los resultados de búsqueda.
            if let photo = stone.photoPath, !photo.isEmpty {
                MiniTopoThumbnail(photoUrl: photo, points: dedupPoints(TopoParse.points(stone.linePath)),
                                  grade: stone.grade)
                    .frame(width: 44, height: 44)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            }
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(stone.name)
                        .font(.system(size: 13, weight: .medium)).foregroundStyle(Cumbre.ink).lineLimit(1)
                    if let grade = stone.grade, !grade.isEmpty {
                        if let split = stone.firstLineId.flatMap({ gradeSplits.splits[$0] }) {
                            GradePill(grade: grade, small: true, split: split)
                        } else {
                            Text(grade).font(Cumbre.mono(11, .bold)).foregroundStyle(GradeColor.color(grade))
                        }
                    }
                }
                if let line = stone.lineName, !line.isEmpty, line != stone.name {
                    Text(line).font(.system(size: 12)).foregroundStyle(Cumbre.ink3).lineLimit(1)
                }
            }
            Spacer(minLength: 8)
            Text(AssistantPresenter.stoneLabel(stone))
                .font(Cumbre.mono(10, .bold))
                .foregroundStyle(confirmed ? Cumbre.ok : Cumbre.ink3)
            if showsChevron {
                Image(systemName: "chevron.right")
                    .font(.system(size: 10, weight: .semibold)).foregroundStyle(Cumbre.ink3)
            }
        }
        .padding(.horizontal, 10).padding(.vertical, 8)
        // Cada piedra en su fila redondeada dentro de la tarjeta del sector.
        .background(Cumbre.bg, in: RoundedRectangle(cornerRadius: 10))
        .contentShape(Rectangle())
    }
}
