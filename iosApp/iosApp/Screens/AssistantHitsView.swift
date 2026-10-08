import SwiftUI
import Shared

/// Resultados de una búsqueda simple en el chat: las vías o bloques que
/// encontró el buscador, agrupados por escuela. Cada fila se puede tocar y abre
/// esa vía en su piedra (igual que desde la pestaña Escuelas → Vías/Bloques).
struct AssistantHitsView: View {
    let hits: [LineSearchHit]
    let total: Int

    var body: some View {
        let groups = AssistantPresenter.groupHits(hits)
        let shown = groups.reduce(0) { $0 + $1.shown.count }
        VStack(alignment: .leading, spacing: 8) {
            ForEach(groups) { AssistantHitGroupView(group: $0) }
            if let note = AssistantPresenter.moreNote(total: total, shown: shown) {
                Text(note)
                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
            }
        }
    }
}

struct AssistantHitGroupView: View {
    @EnvironmentObject private var assistant: AssistantViewModel
    let group: AssistantPresenter.HitGroup

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button { assistant.requestOpen(schoolId: group.id) } label: {
                HStack {
                    Text(group.schoolName)
                        .font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink)
                    Spacer()
                    Text("\(group.total)")
                        .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink2)
                    Image(systemName: "chevron.right")
                        .font(.system(size: 10, weight: .semibold)).foregroundStyle(Cumbre.ink3)
                }
                .padding(.horizontal, 12).padding(.vertical, 10)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            ForEach(group.sectors) { sector in
                // Cabecera de sector: solo si hay más de uno o tiene nombre (un único "sin sector" no la necesita).
                if group.sectors.count > 1 || sector.name != nil {
                    HStack {
                        Text((sector.name ?? L("Sin sector")).uppercased())
                            .font(Cumbre.mono(10, .bold)).tracking(1.4).foregroundStyle(Cumbre.ink2)
                        Spacer()
                        Text("\(sector.hits.count)")
                            .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink3)
                    }
                    .padding(.horizontal, 12).padding(.vertical, 5)
                    .background(Cumbre.bg.opacity(0.6))
                }
                ForEach(sector.hits, id: \.stableId) { AssistantHitRow(hit: $0) }
            }
        }
        .background(Cumbre.paper)
        .clipShape(RoundedRectangle(cornerRadius: 2))
        .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
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
