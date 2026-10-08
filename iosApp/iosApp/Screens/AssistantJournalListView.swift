import SwiftUI
import Shared

/// Las entradas de SU diario que contesta el asistente ("lo que he hecho la última semana"): una lista
/// tocable, la más reciente primero. Tocar una abre esa vía en su escuela (igual que los resultados de búsqueda).
struct AssistantJournalListView: View {
    @EnvironmentObject private var assistant: AssistantViewModel
    let entries: [JournalSession]
    let total: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            VStack(spacing: 0) {
                ForEach(entries, id: \.id) { row($0) }
            }
            .background(Cumbre.paper)
            .clipShape(RoundedRectangle(cornerRadius: 2))
            .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
            if let note = AssistantPresenter.moreNote(total: total, shown: entries.count) {
                Text(note).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
            }
        }
    }

    private func row(_ e: JournalSession) -> some View {
        Button {
            if let school = e.schoolId { assistant.requestOpen(schoolId: school, viaId: e.lineId) }
        } label: {
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(e.blockName)
                            .font(.system(size: 14, weight: .medium)).foregroundStyle(Cumbre.ink).lineLimit(1)
                        if let g = e.grade, !g.isEmpty {
                            Text(g).font(Cumbre.mono(11, .bold)).foregroundStyle(GradeColor.color(g))
                        }
                    }
                    Text(subtitle(e)).font(.system(size: 12)).foregroundStyle(Cumbre.ink3).lineLimit(1)
                }
                Spacer(minLength: 6)
                if e.schoolId != nil {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 10, weight: .semibold)).foregroundStyle(Cumbre.ink3)
                }
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(e.schoolId == nil)
    }

    /// "Albarracín · 8 oct": escuela y día en que se apuntó.
    private func subtitle(_ e: JournalSession) -> String {
        [e.schoolName, AssistantPresenter.dateRange(e.date, nil)].compactMap { $0 }.joined(separator: " · ")
    }
}
