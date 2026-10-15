import SwiftUI
import Shared

/// Votación comunitaria (C2/C5) — espejo de CommunityVoteUi.kt de Android.
/// Regla de diseño (DESIGN.md): todo lo VOTABLE lleva el mismo lenguaje —
/// chip con borde DISCONTINUO terra + ▾. Se aprende una vez.

let ASPECTS = Aspect.shared.ALL

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
    /// Lo justo de cada vía para pintar su pastilla de grado sin abrir la hoja: nº de votos y si la altura parte la opinión.
    @Published var gradeBadges: [String: GradeBadge] = [:]
    /// Votos de grado por vía (lineId → total), para enseñar "N votos" junto al grado sin abrir la hoja.
    var gradeCounts: [String: Int] { gradeBadges.mapValues { $0.count } }
    /// Mi altura (más o menos de 1,70 m), privada; nil = todavía no la he elegido.
    @Published var myHeight: HeightGroup? = nil

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
        if let list = await reporting(L("No se pudo registrar el voto"), {
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
            gradeBadges[lineId] = Self.badge(g)
            if let mine = Self.height(from: g.myHeight) { myHeight = mine }
        }
    }

    private func total(_ g: GradeSummary) -> Int { g.votes.values.reduce(0) { $0 + $1.intValue } }

    /// El grupo de altura que manda el servidor (TALL | SHORT | NONE) como valor Swift.
    private static func height(from raw: String?) -> HeightGroup? {
        switch raw {
        case "TALL": return .tall
        case "SHORT": return .short
        case "NONE": return .unspecified
        default: return nil
        }
    }

    nonisolated static func badge(_ g: GradeSummary) -> GradeBadge {
        GradeBadge(
            count: g.votes.values.reduce(0) { $0 + $1.intValue },
            split: g.heightSplit.map { GradeSplitInfo(tall: $0.tallGrade, short: $0.shortGrade) })
    }

    /// Mi altura guardada (para saber si hay que pedirla al votar).
    func loadMyHeight() async {
        if let h = try? await container.getMyHeight.invoke() { myHeight = h }
    }

    /// La elijo UNA vez (en la hoja de votar o en la mochilita); sus votos ya hechos pasan a contar en su grupo.
    func setMyHeight(_ group: HeightGroup) async {
        if let saved = await reporting(L("No se pudo guardar tu altura"), {
            try await self.container.setMyHeight.invoke(group: group)
        }) {
            myHeight = saved
            if let lineId = grade?.lineId { await loadGrade(lineId: lineId) }
        }
    }

    /// Votos de grado de todas las vías de una piedra, a la vez (una consulta por vía; una piedra tiene pocas).
    func loadGradeCounts(lineIds: [String]) async {
        let container = self.container
        var found: [String: GradeBadge] = [:]
        await withTaskGroup(of: (String, GradeBadge)?.self) { group in
            for id in lineIds {
                group.addTask {
                    guard let g = try? await container.getGradeVotes.invoke(lineId: id) else { return nil }
                    return (id, CommunityVoteStore.badge(g))
                }
            }
            for await r in group { if let r { found[r.0] = r.1 } }
        }
        // Se vuelca FUERA del grupo: así la propiedad (del actor principal) se toca desde el actor principal.
        gradeBadges.merge(found) { _, new in new }
    }

    /// Quita MI voto de grado (y recalcula el resumen).
    func removeGrade(lineId: String) async {
        if let g = await reporting(L("No se pudo quitar el voto"), {
            try await self.container.removeGradeVote.invoke(lineId: lineId)
        }) {
            grade = g
            gradeBadges[lineId] = Self.badge(g)
        }
    }

    /// Guarda el voto. Devuelve true si salió bien (la hoja de votar se cierra solo entonces).
    @discardableResult
    func voteGrade(lineId: String, grade newGrade: String) async -> Bool {
        do {
            let g = try await container.voteGrade.invoke(lineId: lineId, grade: newGrade)
            grade = g
            gradeBadges[lineId] = Self.badge(g)
            return true
        } catch {
            let text = String(describing: error)
            ErrorPresenter.shared.show(
                text.contains("403") || text.contains("GRADE_VOTE")
                    ? L("Solo puede votar el grado quien la tiene en su diario")
                    : ErrorPresenter.friendly(error, fallback: L("No se pudo registrar el voto")))
            return false
        }
    }
}

/// Barras de votos (compartidas por orientación y grado).
struct VoteBars: View {
    let votes: [String: Int]
    let highlight: String?
    let myVote: String?
    /// En los votos de GRADO: cada barra lleva el color de su grado.
    var gradeColored = false

    /// Más votado primero; en empate, por grado o por nombre: lo decide shared (mismo orden en Android).
    private var ordered: [(option: String, count: Int)] {
        VoteOrdering.shared.ordered(votes: votes.mapValues { KotlinInt(int: Int32($0)) })
            .map { (option: $0.option, count: Int($0.count)) }
    }

    var body: some View {
        let maxCount = max(votes.values.max() ?? 1, 1)
        VStack(spacing: 6) {
            ForEach(ordered, id: \.option) { option, count in
                let barColor = gradeColored ? GradeColor.chip(option) : Cumbre.terra
                HStack(spacing: 8) {
                    Text(option).font(Cumbre.mono(12, .bold))
                        .foregroundStyle(option == highlight ? Cumbre.terra : Cumbre.ink)
                        .frame(width: ordered.contains { $0.option.contains("/") } ? 64 : 38, alignment: .leading)
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(Cumbre.paper2)
                            Capsule()
                                .fill(option == highlight ? barColor : barColor.opacity(0.55))
                                .overlay(Capsule().stroke(gradeColored ? Cumbre.rule : Color.clear, lineWidth: 1))
                                .frame(width: max(geo.size.width * CGFloat(count) / CGFloat(maxCount), 14))
                        }
                    }
                    .frame(height: 14)
                    Text("\(count)" + (option == myVote ? L(" · tú ✓") : ""))
                        .font(.system(size: 13, weight: .semibold)).foregroundStyle(Cumbre.ink2)
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
    /// Paso fino (2º): aquí se lee el número de grados, no basta con los 15º
    /// del cono de dirección del mapa.
    @StateObject private var brujula = HeadingProvider(stepDegrees: 2)

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
            // Brújula: INFORMA, no decide. Estando en la roca dice hacia dónde
            // miras; el punto cardinal lo eliges tú tocando su chip, porque la
            // brújula del móvil se descalibra con facilidad.
            if let rumbo = brujula.heading {
                VStack(spacing: 4) {
                    CompassDial(headingDegrees: Double(rumbo))
                        .frame(width: 120, height: 120)
                    Text(L("Estás mirando al %@ · %@", aspectLabel(Aspect.shared.fromDegrees(degrees: Float(rumbo))),
                           Aspect.shared.degreesLabel(degrees: Float(rumbo))))
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                }
                .frame(maxWidth: .infinity)
            }
            let cols = [GridItem(.adaptive(minimum: 56), spacing: 6)]
            LazyVGrid(columns: cols, spacing: 6) {
                ForEach(ASPECTS, id: \.self) { a in
                    let selected = summary?.myVote == a
                    Button {
                        Task { await store.voteOrientation(blockId: blockId, photoIndex: photoIndex, aspect: a) }
                    } label: {
                        Text(aspectLabel(a) + (selected ? " ✓" : ""))
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
        .presentationDetents([.large])
        // La brújula solo mientras la hoja está abierta: el sensor gasta batería.
        .onAppear { brujula.start() }
        .onDisappear { brujula.stop() }
    }
}

/// Hoja de votar GRADO (C5).
struct GradeVoteSheet: View {
    @ObservedObject var store: CommunityVoteStore
    let lineId: String
    let canVote: Bool
    @Environment(\.dismiss) private var dismiss
    /// "Cambiar" mi altura: vuelve a enseñar las tres opciones.
    @State private var changingHeight = false
    /// Lo que has elegido pero AÚN NO has guardado: pulsar grados solo lo cambia, el voto se guarda con GUARDAR.
    @State private var draft: String? = nil
    @State private var edited = false
    @State private var saving = false

    /// El voto que se enseña marcado: tu borrador si has tocado algo, y si no el que ya tienes guardado.
    private func shownVote(_ s: GradeSummary) -> String? { edited ? draft : s.myVote }

    private let suffixes = ["a", "a+", "b", "b+", "c", "c+"]

    /// Todos los grados en orden (4a, 4a+, ... 8c+): sirve para saber cuáles son contiguos.
    private var allGrades: [String] {
        ["4", "5", "6", "7", "8"].flatMap { n in suffixes.map { n + $0 } }
    }

    /// Qué voto queda al pulsar [g]: si ya está elegido, se quita de un voto doble (queda el otro); si es el contiguo
    /// del grado elegido, el voto pasa a ser el PAR (6b/6b+, de menor a mayor); si no, es el nuevo voto.
    private func nextVote(tapping g: String, current: String?) -> String {
        let parts = gradeParts(current)
        guard let gi = allGrades.firstIndex(of: g) else { return g }
        if parts.contains(g) { return parts.count == 2 ? (parts[0] == g ? parts[1] : parts[0]) : g }
        if parts.count == 1, let pi = allGrades.firstIndex(of: parts[0]), abs(pi - gi) == 1 {
            return pi < gi ? parts[0] + "/" + g : g + "/" + parts[0]
        }
        return g
    }

    var body: some View {
        ScrollView {
        VStack(alignment: .leading, spacing: 10) {
            Text("¿QUÉ GRADO LE DAS?")
                .font(Cumbre.mono(11, .bold)).tracking(1).foregroundStyle(Cumbre.terra)
            Text(canVote
                 ? L("El grado que se muestra es el consenso (con 3+ votos). El del equipador queda como referencia.")
                 : L("Solo puede votar quien la tiene en su diario (pruébala o encadénala primero)."))
                .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
            if let s = store.grade {
                if s.heightSplit != nil {
                    Text(L("Opinión repartida según la altura"))
                        .font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.terra)
                        .padding(8).frame(maxWidth: .infinity, alignment: .leading)
                        .background(Cumbre.terraBg).clipShape(RoundedRectangle(cornerRadius: 8))
                }
                if HeightVoteGroups.hasHeightData(s) {
                    HeightVoteGroups(summary: s)
                } else if !s.votes.isEmpty {
                    VoteBars(votes: s.votes.mapValues { $0.intValue },
                             highlight: s.displayedGrade, myVote: s.myVote, gradeColored: true)
                }
                HStack {
                    Text("Mostrado").font(.system(size: 12)).foregroundStyle(Cumbre.ink)
                    Spacer()
                    Text((s.displayedGrade ?? "—") +
                         ((s.setterGrade != nil && s.setterGrade != s.displayedGrade)
                          ? L("  ·  equipador: %@", s.setterGrade!) : ""))
                        .font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.terra)
                }
                .padding(10)
                .background(Cumbre.terraBg)
                .clipShape(RoundedRectangle(cornerRadius: 6))
                if canVote {
                    // La altura se pide UNA vez, aquí mismo: hasta que se elige, los grados están apagados.
                    if store.myHeight == nil || changingHeight {
                        HeightPromptCard(selected: store.myHeight) { group in
                            Task { await store.setMyHeight(group); changingHeight = false }
                        }
                    } else if let mine = store.myHeight {
                        HStack {
                            Text(L("Tu altura: %@", heightLabel(mine))).font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(Cumbre.ink)
                            Spacer()
                            Button(L("Cambiar")) { changingHeight = true }
                                .font(.system(size: 12)).foregroundStyle(Cumbre.terra)
                        }
                        .padding(10).background(Cumbre.paper)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Cumbre.rule, lineWidth: 1))
                    }
                    Text("TU VOTO").font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink3)
                    if store.myHeight == nil {
                        Text(L("Elige tu altura para votar")).font(.system(size: 12)).foregroundStyle(Cumbre.terra)
                    }
                    Text(L("Pulsa un grado. Si dudas entre dos, pulsa también el de al lado."))
                        .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                    ForEach(["4", "5", "6", "7", "8"], id: \.self) { n in
                        HStack(spacing: 6) {
                            ForEach(suffixes, id: \.self) { suf in
                                let g = n + suf
                                let selected = gradeParts(shownVote(s)).contains(g)
                                let style = GradeColor.chipStyle(g)
                                let accent = style.dark ? Cumbre.ink : style.stroke
                                Button {
                                    // Solo se elige: el voto no se guarda hasta pulsar GUARDAR.
                                    draft = nextVote(tapping: g, current: shownVote(s))
                                    edited = true
                                } label: {
                                    // Cada grado con SU color; el elegido (o los DOS, si dudas) va relleno.
                                    Text(g).font(Cumbre.mono(11, .bold))
                                        .foregroundStyle(selected ? (style.dark ? Color.black : Color.white) : accent)
                                        .frame(maxWidth: .infinity).padding(.vertical, 7)
                                        .background(RoundedRectangle(cornerRadius: 6)
                                            .fill(selected ? GradeColor.chip(g) : Cumbre.paper))
                                        .overlay(RoundedRectangle(cornerRadius: 6)
                                            .stroke(selected && !style.dark ? Color.clear : accent, lineWidth: 1))
                                }
                                .buttonStyle(.plain)
                                .disabled(store.myHeight == nil)
                                .opacity(store.myHeight == nil ? 0.38 : 1)
                            }
                        }
                    }
                    // GUARDAR: se activa cuando hay una elección nueva distinta de la ya guardada.
                    let canSave = store.myHeight != nil && edited && draft != nil && draft != s.myVote && !saving
                    Button {
                        guard let vote = draft else { return }
                        saving = true
                        Task {
                            let ok = await store.voteGrade(lineId: lineId, grade: vote)
                            saving = false
                            if ok { dismiss() }
                        }
                    } label: {
                        Text(L("GUARDAR")).font(Cumbre.mono(12, .bold)).foregroundStyle(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(Capsule().fill(Cumbre.terra))
                    }
                    .buttonStyle(.plain)
                    .disabled(!canSave)
                    .opacity(canSave ? 1 : 0.38)
                    if s.myVote != nil, store.myHeight != nil {
                        Button {
                            Task {
                                await store.removeGrade(lineId: lineId)
                                edited = false
                                draft = nil
                            }
                        } label: {
                            Text(L("QUITAR MI VOTO")).font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                                .frame(maxWidth: .infinity).padding(.vertical, 10)
                                .overlay(Capsule().stroke(Cumbre.terra, lineWidth: 1))
                        }
                        .buttonStyle(.plain)
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
        // A pantalla completa: GUARDAR, QUITAR MI VOTO y CERRAR tienen que verse siempre sin arrastrar la hoja.
        .presentationDetents([.large])
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
                // N1: titulo + leyenda en la MISMA fila (aprovechar espacio).
                HStack {
                    Text("SOL EN ESTA PARED · HOY")
                        .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink3)
                    Spacer()
                    legend(sunColor, L("Sol")); legend(shadeColor, L("Sombra"))
                }
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
