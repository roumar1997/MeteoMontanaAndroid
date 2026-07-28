import SwiftUI
import Shared

/// Votación comunitaria (C2/C5) — espejo de CommunityVoteUi.kt de Android.
/// Regla de diseño (DESIGN.md): todo lo VOTABLE lleva el mismo lenguaje —
/// chip con borde DISCONTINUO terra + ▾. Se aprende una vez.

let ASPECTS = ["N", "NE", "E", "SE", "S", "SO", "O", "NO"]

/// Chip pulsable con borde discontinuo terra + ▾.
struct VotableChip: View {
    let text: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Text(text).font(Cumbre.mono(11, .bold)).tracking(0.8)
                Text("▾").font(.system(size: 10))
            }
            .foregroundStyle(Cumbre.terra)
            .padding(.horizontal, 10).padding(.vertical, 4)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(Cumbre.terra, style: StrokeStyle(lineWidth: 1.5, dash: [5, 3.5]))
            )
        }
        .buttonStyle(.plain)
    }
}

/// Estado de la votación del bloque abierto. Regla DI: use cases del container.
@MainActor
final class CommunityVoteStore: ObservableObject {
    @Published var orientation: [OrientationSummary] = []
    @Published var sunByPhoto: [Int: SunHours] = [:]   // -1 = bloque entero
    @Published var grade: GradeSummary? = nil

    private var container: IosDependencyContainer { AppDependencies.shared.container }

    private func key(_ photoIndex: Int?) -> Int { photoIndex.map { Int(truncating: $0 as NSNumber) } ?? -1 }

    func summaryFor(_ photoIndex: Int?) -> OrientationSummary? {
        orientation.first { s in
            if let pi = photoIndex { return s.photoIndex?.intValue == pi }
            return s.photoIndex == nil
        }
    }

    func loadOrientation(blockId: String) async {
        if let list = try? await container.getOrientation.invoke(blockId: blockId) {
            orientation = list
        }
    }

    func loadSun(blockId: String, photoIndex: Int?) async {
        let kotlinIdx: KotlinInt? = photoIndex.map { KotlinInt(int: Int32($0)) }
        if let sun = try? await container.getSunHours.invoke(blockId: blockId, photoIndex: kotlinIdx) {
            sunByPhoto[key(photoIndex)] = sun
        }
    }

    func voteOrientation(blockId: String, photoIndex: Int?, aspect: String) async {
        let kotlinIdx: KotlinInt? = photoIndex.map { KotlinInt(int: Int32($0)) }
        if let list = await reporting("No se pudo registrar el voto", {
            try await self.container.voteOrientation.invoke(
                blockId: blockId, photoIndex: kotlinIdx, aspect: aspect)
        }) {
            orientation = list
            await loadSun(blockId: blockId, photoIndex: photoIndex)
        }
    }

    func loadGrade(lineId: String) async {
        grade = nil
        if let g = try? await container.getGradeVotes.invoke(lineId: lineId) {
            grade = g
        }
    }

    func voteGrade(lineId: String, grade newGrade: String) async {
        do {
            grade = try await container.voteGrade.invoke(lineId: lineId, grade: newGrade)
        } catch {
            let text = String(describing: error)
            ErrorPresenter.shared.show(
                text.contains("403") || text.contains("GRADE_VOTE")
                    ? "Solo puede votar el grado quien la tiene en su diario"
                    : ErrorPresenter.friendly(error, fallback: "No se pudo registrar el voto"))
        }
    }
}

/// Barras de votos (compartidas por orientación y grado).
struct VoteBars: View {
    let votes: [String: Int]
    let highlight: String?
    let myVote: String?

    var body: some View {
        let maxCount = max(votes.values.max() ?? 1, 1)
        VStack(spacing: 6) {
            ForEach(votes.sorted { $0.value > $1.value }, id: \.key) { option, count in
                HStack(spacing: 8) {
                    Text(option).font(Cumbre.mono(12, .bold))
                        .foregroundStyle(option == highlight ? Cumbre.terra : Cumbre.ink)
                        .frame(width: 38, alignment: .leading)
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(Cumbre.paper2)
                            Capsule()
                                .fill(option == highlight ? Cumbre.terra : Cumbre.terra.opacity(0.45))
                                .frame(width: geo.size.width * CGFloat(count) / CGFloat(maxCount))
                        }
                    }
                    .frame(height: 14)
                    Text("\(count)" + (option == myVote ? " · tú ✓" : ""))
                        .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                }
            }
        }
    }
}

/// Hoja de votar ORIENTACIÓN (C2).
struct OrientationVoteSheet: View {
    @ObservedObject var store: CommunityVoteStore
    let blockId: String
    let photoIndex: Int?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let summary = store.summaryFor(photoIndex)
        VStack(alignment: .leading, spacing: 10) {
            Text("¿HACIA DÓNDE MIRA ESTA PARED?")
                .font(Cumbre.mono(11, .bold)).tracking(1).foregroundStyle(Cumbre.terra)
            Text("Vota la comunidad; se muestra la más votada. Un voto por persona — puedes cambiarlo.")
                .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
            if let s = summary, !s.votes.isEmpty {
                VoteBars(
                    votes: s.votes.mapValues { $0.intValue },
                    highlight: s.consensus, myVote: s.myVote)
            } else {
                Text("Sin votos todavía. ¡Sé el primero!")
                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
            }
            let cols = [GridItem(.adaptive(minimum: 56), spacing: 6)]
            LazyVGrid(columns: cols, spacing: 6) {
                ForEach(ASPECTS, id: \.self) { a in
                    let selected = summary?.myVote == a
                    Button {
                        Task { await store.voteOrientation(blockId: blockId, photoIndex: photoIndex, aspect: a) }
                    } label: {
                        Text(a + (selected ? " ✓" : ""))
                            .font(Cumbre.mono(11, .bold))
                            .foregroundStyle(selected ? .white : Cumbre.ink)
                            .frame(maxWidth: .infinity).padding(.vertical, 8)
                            .background(RoundedRectangle(cornerRadius: 6)
                                .fill(selected ? Cumbre.terra : Cumbre.paper))
                            .overlay(RoundedRectangle(cornerRadius: 6)
                                .stroke(selected ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
            }
            Button("CERRAR") { dismiss() }
                .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                .frame(maxWidth: .infinity).padding(.top, 6)
        }
        .padding(16)
        .presentationDetents([.medium])
    }
}

/// Hoja de votar GRADO (C5).
struct GradeVoteSheet: View {
    @ObservedObject var store: CommunityVoteStore
    let lineId: String
    let canVote: Bool
    @Environment(\.dismiss) private var dismiss

    private let suffixes = ["a", "a+", "b", "b+", "c", "c+"]

    var body: some View {
        ScrollView {
        VStack(alignment: .leading, spacing: 10) {
            Text("¿QUÉ GRADO LE DAS?")
                .font(Cumbre.mono(11, .bold)).tracking(1).foregroundStyle(Cumbre.terra)
            Text(canVote
                 ? "El grado que se muestra es el consenso (con 3+ votos). El del equipador queda como referencia."
                 : "Solo puede votar quien la tiene en su diario (pruébala o encadénala primero).")
                .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
            if let s = store.grade {
                if !s.votes.isEmpty {
                    VoteBars(votes: s.votes.mapValues { $0.intValue },
                             highlight: s.displayedGrade, myVote: s.myVote)
                }
                HStack {
                    Text("Mostrado").font(.system(size: 12)).foregroundStyle(Cumbre.ink)
                    Spacer()
                    Text((s.displayedGrade ?? "—") +
                         ((s.setterGrade != nil && s.setterGrade != s.displayedGrade)
                          ? "  ·  equipador: \(s.setterGrade!)" : ""))
                        .font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.terra)
                }
                .padding(10)
                .background(Cumbre.terraBg)
                .clipShape(RoundedRectangle(cornerRadius: 6))
                if canVote {
                    Text("TU VOTO").font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink3)
                    ForEach(["4", "5", "6", "7", "8"], id: \.self) { n in
                        HStack(spacing: 6) {
                            ForEach(suffixes, id: \.self) { suf in
                                let g = n + suf
                                let selected = s.myVote == g
                                Button {
                                    Task { await store.voteGrade(lineId: lineId, grade: g) }
                                } label: {
                                    Text(g).font(Cumbre.mono(11, .bold))
                                        .foregroundStyle(selected ? .white : Cumbre.ink)
                                        .frame(maxWidth: .infinity).padding(.vertical, 7)
                                        .background(RoundedRectangle(cornerRadius: 6)
                                            .fill(selected ? Cumbre.terra : Cumbre.paper))
                                        .overlay(RoundedRectangle(cornerRadius: 6)
                                            .stroke(selected ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
            } else {
                ProgressView().frame(maxWidth: .infinity)
            }
            Button("CERRAR") { dismiss() }
                .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                .frame(maxWidth: .infinity).padding(.top, 6)
        }
        .padding(16)
        }
        .presentationDetents([.medium, .large])
    }
}

/// Tira horaria de sol (amarillo = al sol, azul tinta = sombra).
struct SunStripView: View {
    let sun: SunHours

    private let sunColor = Color(red: 0.91, green: 0.72, blue: 0.29)
    private let shadeColor = Color(red: 0.24, green: 0.29, blue: 0.36)

    var body: some View {
        if !sun.hours.isEmpty {
            VStack(alignment: .leading, spacing: 4) {
                Text("SOL EN ESTA PARED · HOY")
                    .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink3)
                HStack(spacing: 2) {
                    ForEach(Array(sun.hours.enumerated()), id: \.offset) { _, h in
                        VStack(spacing: 2) {
                            RoundedRectangle(cornerRadius: 4)
                                .fill(h.inSun ? sunColor : shadeColor)
                                .frame(height: 18)
                            Text(hourLabel(h.time)).font(.system(size: 8))
                                .foregroundStyle(Cumbre.ink3)
                        }
                    }
                }
                HStack(spacing: 12) {
                    legend(sunColor, "Al sol"); legend(shadeColor, "Sombra")
                }
            }
        }
    }

    private func hourLabel(_ iso: String) -> String {
        let h = iso.split(separator: "T").last.map { String($0.prefix(2)) } ?? ""
        return h.hasPrefix("0") ? String(h.dropFirst()) : h
    }

    private func legend(_ color: Color, _ label: String) -> some View {
        HStack(spacing: 4) {
            RoundedRectangle(cornerRadius: 2).fill(color).frame(width: 9, height: 9)
            Text(label).font(.system(size: 10)).foregroundStyle(Cumbre.ink3)
        }
    }
}
