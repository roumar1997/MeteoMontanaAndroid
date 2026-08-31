import SwiftUI
import Shared

/// Pestaña "SUGERENCIAS" del panel admin: buzón del botón "?" de ayuda
/// (Álvaro, 2026-08-31: "poder responder o verlo más veces para poder
/// consultarlo"). Responder avisa al autor por push y marca atendida sola.
/// Espejo de SugerenciasTab.kt.
struct AdminSuggestionsTab: View {
    let rows: [AdminSuggestionRow]?
    let onRespond: (String, Bool?, String?) -> Void

    private var sorted: [AdminSuggestionRow] {
        (rows ?? []).sorted { !$0.resolved && $1.resolved }
    }

    var body: some View {
        if rows == nil {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if sorted.isEmpty {
            Text("Sin sugerencias todavía")
                .foregroundStyle(Cumbre.ink3)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(sorted, id: \.id) { row in
                        SuggestionCard(row: row, onRespond: onRespond)
                        Divider().overlay(Cumbre.rule)
                    }
                }
            }
        }
    }
}

private struct SuggestionCard: View {
    let row: AdminSuggestionRow
    let onRespond: (String, Bool?, String?) -> Void
    @State private var replyText = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("\(row.displayName ?? row.email ?? row.uid) · \(row.platform)\(row.appVersion.map { " \($0)" } ?? "")")
                    .font(Cumbre.mono(10, .bold)).tracking(0.8).foregroundStyle(Cumbre.terra)
                Spacer()
                if row.resolved {
                    Text("ATENDIDA").font(Cumbre.mono(10, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink3)
                }
            }
            Text(row.message).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
            if let created = row.createdAt {
                Text(String(created.prefix(16))).font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
            }
            if let reply = row.adminReply {
                VStack(alignment: .leading, spacing: 2) {
                    Text("TU RESPUESTA").font(Cumbre.mono(10, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink3)
                    Text(reply).font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                }
                .padding(.top, 2)
            }
            HStack(spacing: 8) {
                TextField("Responder…", text: $replyText)
                    .textFieldStyle(.roundedBorder)
                Button("ENVIAR") {
                    onRespond(row.id, nil, replyText)
                    replyText = ""
                }
                .disabled(replyText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            if !row.resolved {
                Button("MARCAR ATENDIDA") { onRespond(row.id, true, nil) }
                    .font(.system(size: 13)).foregroundStyle(Cumbre.terra)
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
    }
}
