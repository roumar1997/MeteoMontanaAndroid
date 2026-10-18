import SwiftUI
import Shared

/// Pestaña "SUGERENCIAS" del panel admin: buzón del botón "?" de ayuda
/// (Álvaro, 2026-08-31: "poder responder o verlo más veces para poder
/// consultarlo"). Responder avisa al autor por push y marca atendida sola.
/// Espejo de SugerenciasTab.kt. Rehecha el 2026-10-18 con tarjetas («se ve muy anticuado»).
struct AdminSuggestionsTab: View {
    let rows: [AdminSuggestionRow]?
    let onRespond: (String, Bool?, String?) -> Void
    @State private var showResolved = false

    private var pending: [AdminSuggestionRow] { (rows ?? []).filter { !$0.resolved } }
    private var resolved: [AdminSuggestionRow] { (rows ?? []).filter { $0.resolved } }

    var body: some View {
        if rows == nil {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if (rows ?? []).isEmpty {
            AdminEmptyState(symbol: "text.bubble", text: "Sin sugerencias todavía").frame(maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 10) {
                    AdminSectionLabel(text: "POR ATENDER · \(pending.count)")
                    if pending.isEmpty {
                        Text("Todo atendido.").font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                    }
                    ForEach(pending, id: \.id) { SuggestionCard(row: $0, onRespond: onRespond) }

                    if !resolved.isEmpty {
                        Button { withAnimation { showResolved.toggle() } } label: {
                            HStack {
                                AdminSectionLabel(text: "ATENDIDAS · \(resolved.count)")
                                Image(systemName: showResolved ? "chevron.up" : "chevron.down")
                                    .font(.system(size: 11, weight: .bold)).foregroundStyle(Cumbre.terra)
                            }
                        }.buttonStyle(.plain).padding(.top, 10)
                        if showResolved {
                            ForEach(resolved, id: \.id) { SuggestionCard(row: $0, onRespond: onRespond) }
                        }
                    }
                }
                .padding(.horizontal, 16).padding(.vertical, 12)
            }
        }
    }
}

private struct SuggestionCard: View {
    let row: AdminSuggestionRow
    let onRespond: (String, Bool?, String?) -> Void
    @State private var replyText = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                Text(row.displayName ?? row.email ?? String(row.uid.prefix(10)))
                    .font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink).lineLimit(1)
                Spacer(minLength: 4)
                AdminChip(text: row.platform.uppercased() + (row.appVersion.map { " \($0)" } ?? ""), color: Cumbre.ink2)
                if row.resolved { AdminChip(text: "ATENDIDA", color: Cumbre.ok) }
            }
            Text(row.message).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
            if let created = row.createdAt {
                Text(String(created.prefix(16)).replacingOccurrences(of: "T", with: " · "))
                    .font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
            }
            if let reply = row.adminReply {
                VStack(alignment: .leading, spacing: 3) {
                    Text("TU RESPUESTA").font(Cumbre.mono(9, .bold)).tracking(1).foregroundStyle(Cumbre.ok)
                    Text(reply).font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
                }
                .padding(10).frame(maxWidth: .infinity, alignment: .leading)
                .background(Cumbre.ok.opacity(0.08), in: RoundedRectangle(cornerRadius: 9))
            }
            HStack(spacing: 8) {
                AdminField(placeholder: "Responder…", text: $replyText)
                Button {
                    onRespond(row.id, nil, replyText)
                    replyText = ""
                } label: {
                    Image(systemName: "paperplane.fill").font(.system(size: 15, weight: .semibold)).foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(canSend ? Cumbre.terraFill : Cumbre.ink3.opacity(0.4), in: RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain).disabled(!canSend)
            }
            if !row.resolved {
                AdminButton(title: "MARCAR ATENDIDA", symbol: "checkmark", kind: .outline) { onRespond(row.id, true, nil) }
            }
        }
        .adminCard(tint: row.resolved ? nil : Cumbre.terra)
    }

    private var canSend: Bool { !replyText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
}
