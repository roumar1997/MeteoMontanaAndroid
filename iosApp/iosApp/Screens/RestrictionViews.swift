import SwiftUI
import Shared

/// Restricciones en vigor de la escuela abierta + sus piedras y sectores (para saber de qué sector es cada piedra).
/// Espejo de `SchoolRestrictionsUi` (Android).
struct SchoolRestrictionsUI {
    let restrictions: SchoolRestrictions
    let blocks: [Block]

    func forBlockId(_ blockId: String) -> [Restriction] {
        restrictions.forBlock(blockId: blockId, sectorOfBlock: blocks.first { $0.id == blockId }?.sectorBlockId)
    }

    /// Todas las de ese sector (el entero y sus piedras sueltas).
    func allInSector(_ sectorId: String) -> [Restriction] {
        let inSector = Set(blocks.filter { $0.sectorBlockId == sectorId }.map(\.id))
        return restrictions.active.filter { r in
            r.sectorBlockId == sectorId || r.blockIds.contains { inSector.contains($0) }
        }
    }

    func allInSectorNamed(_ name: String?) -> [Restriction] {
        guard let name, let s = blocks.first(where: { $0.type.uppercased() == "ZONE" && $0.name == name }) else { return [] }
        return allInSector(s.id)
    }
}

private struct SchoolRestrictionsKey: EnvironmentKey {
    static let defaultValue: SchoolRestrictionsUI? = nil
}

extension EnvironmentValues {
    /// Se da UNA vez en la ficha y la leen el chip del sector, la pestaña BLOQUES y la ficha de la piedra.
    var schoolRestrictions: SchoolRestrictionsUI? {
        get { self[SchoolRestrictionsKey.self] }
        set { self[SchoolRestrictionsKey.self] = newValue }
    }
}

/// Carga las restricciones en vigor de una escuela. Sin red no se pinta nada (mejor sin "!" que con un error).
/// Las piedras solo se piden si HAY restricciones (lo normal es que no haya ninguna y no cuesta nada).
@MainActor
final class SchoolRestrictionsModel: ObservableObject {
    @Published private(set) var ui: SchoolRestrictionsUI?
    private var loadedFor: String?
    private let container = AppDependencies.shared.container

    func load(schoolId: String) async {
        guard loadedFor != schoolId else { return }
        loadedFor = schoolId
        guard let sr = try? await container.getSchoolActiveRestrictions.invoke(schoolId: schoolId, forceRefresh: false) else {
            loadedFor = nil
            return
        }
        guard !sr.isEmpty else { ui = nil; return }
        let blocks = (try? await container.getBlocks.invoke(schoolId: schoolId)) ?? []
        ui = SchoolRestrictionsUI(restrictions: sr, blocks: blocks)
    }
}

// MARK: - Piezas

/// Cerrado = rojo, regulado = ámbar, aviso = gris del texto secundario.
func restrictionColor(_ kind: RestrictionKind) -> Color {
    switch kind {
    case .closed: return Cumbre.bad
    case .regulated: return Cumbre.warn
    default: return Cumbre.ink2
    }
}

/// Fuerza: cerrado (0) > regulado (1) > aviso (2). El enum de Kotlin llega a Swift sin `ordinal`.
func restrictionRank(_ kind: RestrictionKind) -> Int {
    switch kind {
    case .closed: return 0
    case .regulated: return 1
    default: return 2
    }
}

/// Código que entiende el servidor ("CLOSED"…).
func restrictionKindCode(_ kind: RestrictionKind) -> String {
    switch kind {
    case .closed: return "CLOSED"
    case .regulated: return "REGULATED"
    default: return "NOTICE"
    }
}

private func kindLabel(_ kind: RestrictionKind) -> String {
    switch kind {
    case .closed: return L("Cerrado")
    case .regulated: return L("Regulado")
    default: return L("Aviso")
    }
}

private func strongest(_ items: [Restriction]) -> Restriction? {
    items.min { restrictionRank($0.kind) < restrictionRank($1.kind) }
}

/// "Cerrado hasta el 31 jul" / "Cerrado siempre".
func restrictionWhenText(_ r: Restriction) -> String {
    let kind = kindLabel(r.kind)
    if r.permanent { return L("%@ siempre", kind) }
    guard let d = r.endDay?.intValue, let m = r.endMonth?.intValue else { return kind }
    let f = DateFormatter()
    f.locale = LanguageManager.shared.locale
    let month = f.shortMonthSymbols[m - 1].replacingOccurrences(of: ".", with: "")
    return L("%@ hasta el %@", kind, "\(d) \(month)")
}

/// Dónde se aplica: "Sector Alcores" o "3 piedras · Alcores".
func restrictionPlace(_ r: Restriction, blocks: [Block]) -> String? {
    let sector = blocks.first { $0.id == r.sectorBlockId }?.name
    if r.blockIds.isEmpty { return sector.map { L("Sector %@", $0) } }
    if r.blockIds.count == 1 { return blocks.first { $0.id == r.blockIds.first }?.name }
    return L("%@ piedras", r.blockIds.count) + (sector.map { " · \($0)" } ?? "")
}

/// El "!" redondo junto al sector o la piedra.
struct RestrictionMark: View {
    let kind: RestrictionKind
    var size: CGFloat = 16
    var body: some View {
        Text("!").font(.system(size: size * 0.7, weight: .bold)).foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(restrictionColor(kind), in: Circle())
    }
}

/// El "!" tocable: abre la hoja con el detalle. Nada si no hay restricción.
struct RestrictionMarkButton: View {
    let items: [Restriction]
    var size: CGFloat = 16
    @Environment(\.schoolRestrictions) private var ui
    @State private var open = false
    var body: some View {
        if let s = strongest(items) {
            Button { open = true } label: { RestrictionMark(kind: s.kind, size: size) }
                .buttonStyle(.plain)
                .sheet(isPresented: $open) { RestrictionsSheet(items: items, blocks: ui?.blocks ?? []) }
        }
    }
}

/// Pastilla junto al nombre de la escuela: "! 2 CERRADOS". Solo dentro de la escuela, nunca en la lista.
struct RestrictionsPill: View {
    let ui: SchoolRestrictionsUI
    let action: () -> Void
    var body: some View {
        if let s = strongest(ui.restrictions.active) {
            let closed = Int(ui.restrictions.closedCount)
            let label = closed == 1 ? L("1 cerrado")
                : closed > 1 ? L("%@ cerrados", closed)
                : kindLabel(s.kind)
            Button(action: action) {
                HStack(spacing: 6) {
                    RestrictionMark(kind: s.kind)
                    Text(label.uppercased()).font(Cumbre.mono(10, .bold)).tracking(1).foregroundStyle(restrictionColor(s.kind))
                        .lineLimit(1)
                }
                .padding(.leading, 4).padding(.trailing, 10).padding(.vertical, 4)
                .overlay(Capsule().stroke(restrictionColor(s.kind), lineWidth: 1))
            }
            .buttonStyle(.plain)
        }
    }
}

/// Aviso fijo arriba de la ficha de una piedra o sector.
struct RestrictionBanner: View {
    let items: [Restriction]
    @Environment(\.schoolRestrictions) private var ui
    @State private var open = false
    var body: some View {
        if let r = strongest(items) {
            Button { open = true } label: {
                HStack(spacing: 10) {
                    RestrictionMark(kind: r.kind, size: 20)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(restrictionWhenText(r)).font(.system(size: 14, weight: .bold)).foregroundStyle(restrictionColor(r.kind))
                        Text(r.reason).font(.system(size: 13)).foregroundStyle(Cumbre.ink).lineLimit(2)
                    }
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 16).padding(.vertical, 10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(restrictionColor(r.kind).opacity(0.12))
            }
            .buttonStyle(.plain)
            .sheet(isPresented: $open) { RestrictionsSheet(items: items, blocks: ui?.blocks ?? []) }
        }
    }
}

/// Hoja con las restricciones: qué y hasta cuándo, dónde, motivo y fuente (tocable si tiene enlace).
struct RestrictionsSheet: View {
    let items: [Restriction]
    let blocks: [Block]
    @Environment(\.openURL) private var openURL

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text(L("Restricciones en vigor").uppercased()).eyebrow().padding(.bottom, 6)
                ForEach(Array(items.sorted { restrictionRank($0.kind) < restrictionRank($1.kind) }.enumerated()), id: \.element.id) { i, r in
                    if i > 0 { Divider().overlay(Cumbre.rule) }
                    row(r)
                }
                Text(L("Datos de la normativa oficial. Ante la duda, manda la señalización en el lugar."))
                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink2).padding(.top, 8)
            }
            .padding(20)
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .presentationDetents([.medium, .large])
    }

    private func row(_ r: Restriction) -> some View {
        HStack(alignment: .top, spacing: 10) {
            RestrictionMark(kind: r.kind, size: 20)
            VStack(alignment: .leading, spacing: 3) {
                Text(restrictionWhenText(r)).font(.system(size: 15, weight: .bold)).foregroundStyle(restrictionColor(r.kind))
                if let place = restrictionPlace(r, blocks: blocks) {
                    Text(place).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink)
                }
                Text(r.reason).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                if r.yearly {
                    Text(L("Se repite cada año en las mismas fechas")).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                }
                if let url = r.sourceUrl.flatMap(URL.init(string:)) {
                    Button { openURL(url) } label: {
                        Text(L("Fuente: %@", r.sourceName)).font(Cumbre.mono(11)).foregroundStyle(Cumbre.terra)
                    }.buttonStyle(.plain)
                } else {
                    Text(L("Fuente: %@", r.sourceName)).font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink2)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 10)
    }
}
