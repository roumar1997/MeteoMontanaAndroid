import SwiftUI
import Shared

/// Resultados de una búsqueda simple en el chat: TODAS las vías o bloques que encontró el buscador,
/// agrupados por escuela y, dentro, por sector. Escuelas y sectores se pliegan y se despliegan con un toque
/// en su cabecera; cada fila abre esa vía en su piedra (igual que desde Escuelas → Vías/Bloques).
struct AssistantHitsView: View {
    let hits: [LineSearchHit]
    let total: Int
    /// Escuelas desplegadas y sectores plegados ("schoolId|sector"). Con muchos resultados solo empieza
    /// desplegada la primera escuela: no se pinta (ni se cargan las miniaturas de) lo que no se ve.
    @State private var expanded: Set<String>
    @State private var collapsedSectors: Set<String> = []

    init(hits: [LineSearchHit], total: Int) {
        self.hits = hits
        self.total = total
        _expanded = State(initialValue: AssistantPresenter.initiallyExpanded(AssistantPresenter.groupAllHits(hits)))
    }

    var body: some View {
        let groups = AssistantPresenter.groupAllHits(hits)
        VStack(alignment: .leading, spacing: 8) {
            ForEach(groups) { group in
                AssistantHitGroupView(
                    group: group,
                    isExpanded: expanded.contains(group.id),
                    collapsedSectors: collapsedSectors,
                    onToggle: { toggle(group.id) },
                    onToggleSector: { toggleSector(group.id, $0) })
            }
        }
    }

    private func toggle(_ id: String) {
        withAnimation(.easeInOut(duration: 0.2)) {
            if expanded.contains(id) { expanded.remove(id) } else { expanded.insert(id) }
        }
    }

    private func toggleSector(_ schoolId: String, _ sector: String) {
        let key = AssistantPresenter.sectorKey(schoolId, sector)
        withAnimation(.easeInOut(duration: 0.2)) {
            if collapsedSectors.contains(key) { collapsedSectors.remove(key) } else { collapsedSectors.insert(key) }
        }
    }
}

struct AssistantHitGroupView: View {
    @EnvironmentObject private var assistant: AssistantViewModel
    let group: AssistantPresenter.HitGroup
    let isExpanded: Bool
    let collapsedSectors: Set<String>
    let onToggle: () -> Void
    let onToggleSector: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
            if isExpanded {
                ForEach(group.sectors) { sector in
                    // Cabecera de sector: solo si hay más de uno o tiene nombre (un único "sin sector" no la necesita).
                    let showsHeader = group.sectors.count > 1 || sector.name != nil
                    let sectorKey = AssistantPresenter.sectorKey(group.id, sector.id)
                    let sectorOpen = !showsHeader || !collapsedSectors.contains(sectorKey)
                    if showsHeader { sectorHeader(sector, open: sectorOpen) }
                    if sectorOpen {
                        ForEach(sector.hits, id: \.stableId) { AssistantHitRow(hit: $0) }
                    }
                }
            }
        }
        .background(Cumbre.paper)
        .clipShape(RoundedRectangle(cornerRadius: 2))
        .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
    }

    /// Escuela: tocar la cabecera pliega o despliega; el botón de la derecha abre la escuela.
    private var header: some View {
        HStack(spacing: 0) {
            Button(action: onToggle) {
                HStack(spacing: 8) {
                    Image(systemName: isExpanded ? "chevron.down" : "chevron.right")
                        .font(.system(size: 11, weight: .bold)).foregroundStyle(Cumbre.ink3)
                        .frame(width: 14)
                    Text(group.schoolName)
                        .font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink)
                        .multilineTextAlignment(.leading)
                    Spacer(minLength: 6)
                    Text("\(group.total)")
                        .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink2)
                }
                .padding(.leading, 12).padding(.vertical, 10)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(group.schoolName), \(group.total)")
            .accessibilityHint(isExpanded ? L("Plegar") : L("Desplegar"))

            Button { assistant.requestOpen(schoolId: group.id) } label: {
                Image(systemName: "arrow.up.right.square")
                    .font(.system(size: 15)).foregroundStyle(Cumbre.terra)
                    .padding(.horizontal, 12).padding(.vertical, 10)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(L("Abrir escuela"))
        }
    }

    private func sectorHeader(_ sector: AssistantPresenter.HitSector, open: Bool) -> some View {
        Button { onToggleSector(sector.id) } label: {
            HStack(spacing: 6) {
                Image(systemName: open ? "chevron.down" : "chevron.right")
                    .font(.system(size: 9, weight: .bold)).foregroundStyle(Cumbre.ink3)
                    .frame(width: 12)
                Text((sector.name ?? L("Sin sector")).uppercased())
                    .font(Cumbre.mono(10, .bold)).tracking(1.4).foregroundStyle(Cumbre.ink2)
                Spacer()
                Text("\(sector.hits.count)")
                    .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink3)
            }
            .padding(.horizontal, 12).padding(.vertical, 6)
            .background(Cumbre.bg.opacity(0.6))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Una vía o bloque encontrado: miniatura del topo, nombre, grado coloreado,
/// piedra y orientación votada (o aviso de que no la tiene).
struct AssistantHitRow: View {
    @EnvironmentObject private var assistant: AssistantViewModel
    let hit: LineSearchHit

    var body: some View {
        Button { assistant.requestOpen(schoolId: hit.schoolId, viaId: hit.lineId) } label: {
            HStack(spacing: 10) {
                if let photo = hit.photoPath, !photo.isEmpty {
                    MiniTopoThumbnail(photoUrl: photo, points: dedupPoints(TopoParse.points(hit.linePath)),
                                      grade: hit.grade)
                        .frame(width: 44, height: 44)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                        .overlay(RoundedRectangle(cornerRadius: 4).stroke(Cumbre.rule, lineWidth: 1))
                }
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(hit.lineName ?? hit.blockName)
                            .font(.system(size: 14, weight: .medium)).foregroundStyle(Cumbre.ink).lineLimit(1)
                        if let g = hit.grade {
                            Text(g).font(Cumbre.mono(11, .bold)).foregroundStyle(GradeColor.color(g))
                        }
                        let kind = SchoolKind(style: hit.discipline)
                        if kind.isSingle { SchoolKindIcons(kind: kind, height: 13) }
                    }
                    if hit.lineName != nil, !hit.blockName.isEmpty {
                        Text(hit.blockName)
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3).lineLimit(1)
                    }
                    if let avg = hit.rating?.doubleValue, let votes = hit.ratingCount?.intValue, votes > 0 {
                        Text(AssistantPresenter.starsLabel(rating: avg, count: Int(votes)))
                            .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.warn)
                    }
                    if let o = hit.orientation, !o.isEmpty {
                        Text(aspectLabel(o)).font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink3)
                    } else {
                        Text(L("SIN ORIENTACIÓN ASIGNADA"))
                            .font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.ink3.opacity(0.7))
                    }
                }
                Spacer(minLength: 6)
                Image(systemName: "chevron.right")
                    .font(.system(size: 10, weight: .semibold)).foregroundStyle(Cumbre.ink3)
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
