import SwiftUI
import FirebaseAuth
import Shared

// Enlaces de la comunidad a vídeos de beta (Instagram/YouTube) en piedras/
// muros y vías, con categoría opcional de altura — Álvaro, 2026-09-15: "que
// se pueda poner igual que se vota, viendo la piedra, sin tener que
// editarlo... que se pueda tener varios diferentes, beta personas +1.70 y
// personas -1.70". Directo, sin revisión de admin — mismo patrón que
// LineCommentsView.swift: un fetch por piedra, cada hilo filtra los suyos.

@MainActor
final class BetaLinksStore: ObservableObject {
    @Published var links: [BetaLink] = []
    private var loadedBlockId: String?

    // Regla DI (ARCHITECTURE §2): por los use cases del container, nunca la API.
    private var container: IosDependencyContainer { AppDependencies.shared.container }

    func load(blockId: String) async {
        guard loadedBlockId != blockId else { return }
        loadedBlockId = blockId
        // Carga pasiva: si falla, el hilo sale vacío y se reintenta al reabrir.
        if let list = try? await container.getBetaLinks.invoke(blockId: blockId, lineId: nil) {
            links = list
        } else {
            loadedBlockId = nil
        }
    }

    func add(blockId: String, lineId: String?, url: String, heightCategory: String?) async {
        if let created = await reporting("No se pudo añadir el enlace", {
            try await container.addBetaLink.invoke(blockId: blockId, lineId: lineId, url: url, heightCategory: heightCategory)
        }) {
            links.append(created)
        }
    }

    func delete(linkId: String) async {
        guard await reporting("No se pudo borrar el enlace", {
            try await container.deleteBetaLink.invoke(linkId: linkId)
        }) != nil else { return }
        links.removeAll { $0.id == linkId }
    }
}

/// Categoría de altura para etiquetar la beta — Álvaro, 2026-09-15.
private enum HeightFilter: String, CaseIterable, Identifiable {
    case any = ""
    case tall = "TALL"
    case short = "SHORT"
    var id: String { rawValue }
    var label: String {
        switch self {
        case .any: return "Cualquier altura"
        case .tall: return "Personas +1,70"
        case .short: return "Personas -1,70"
        }
    }
}

/// Hilo desplegable de enlaces de beta: la CABECERA ENTERA es pulsable.
/// lineId = nil → enlaces de la piedra entera.
struct BetaLinksThreadView: View {
    @ObservedObject var store: BetaLinksStore
    let blockId: String
    let lineId: String?
    @State private var expanded = false
    @State private var addingUrl = ""
    @State private var addingCategory: HeightFilter = .any

    private var mine: [BetaLink] {
        store.links
            .filter { $0.blockId == blockId && $0.lineId == lineId }
            .sorted { ($0.createdAt ?? "") > ($1.createdAt ?? "") }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Button { withAnimation { expanded.toggle() } } label: {
                HStack(spacing: 6) {
                    Image(systemName: "play.rectangle")
                        .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                    Text("BETA" + (mine.isEmpty ? "" : " · \(mine.count)"))
                        .font(Cumbre.mono(10, .bold)).tracking(1.0)
                        .foregroundStyle(Cumbre.ink3)
                    Spacer()
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 10)).foregroundStyle(Cumbre.ink3)
                }
                .padding(.vertical, 6)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if expanded {
                if mine.isEmpty {
                    Text("Sé el primero en dejar un enlace de beta.")
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                }
                ForEach(Array(mine.enumerated()), id: \.element.id) { idx, l in
                    if idx > 0 { Divider().overlay(Cumbre.rule) }
                    BetaLinkRow(link: l) {
                        Task { await store.delete(linkId: l.id) }
                    }
                    .padding(.vertical, 4)
                }
                VStack(alignment: .leading, spacing: 6) {
                    Picker("Altura", selection: $addingCategory) {
                        ForEach(HeightFilter.allCases) { c in Text(c.label).tag(c) }
                    }
                    .pickerStyle(.segmented)
                    HStack(spacing: 8) {
                        TextField("Enlace de Instagram/YouTube", text: $addingUrl)
                            .keyboardType(.URL)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .font(.system(size: 14))
                            .padding(.horizontal, 10).padding(.vertical, 8)
                            .background(Cumbre.paper)
                            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                        Button {
                            let u = addingUrl.trimmingCharacters(in: .whitespaces)
                            guard !u.isEmpty else { return }
                            addingUrl = ""
                            let category: String? = addingCategory == .any ? nil : addingCategory.rawValue
                            Task { await store.add(blockId: blockId, lineId: lineId, url: u, heightCategory: category) }
                        } label: {
                            Image(systemName: "plus.circle.fill")
                                .font(.system(size: 20))
                                .foregroundStyle(addingUrl.trimmingCharacters(in: .whitespaces).isEmpty
                                                 ? Cumbre.ink3 : Cumbre.terra)
                        }
                        .buttonStyle(.plain)
                        .disabled(addingUrl.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
                .padding(.bottom, 4)
            }
        }
    }
}

/// Fila de un enlace: chip pulsable (abre fuera de la app) + su categoría de
/// altura si tiene, + borrar si es mío.
private struct BetaLinkRow: View {
    let link: BetaLink
    let onDelete: () -> Void

    private var platform: (icon: String, label: String) {
        let host = URL(string: link.url)?.host?.lowercased() ?? ""
        if host.contains("instagram.com") { return ("camera.fill", "Ver en Instagram") }
        if host.contains("youtube.com") || host.contains("youtu.be") { return ("play.rectangle.fill", "Ver en YouTube") }
        return ("link", "Ver enlace")
    }

    private var categoryLabel: String? {
        switch link.heightCategory {
        case "TALL": return "+1,70"
        case "SHORT": return "-1,70"
        default: return nil
        }
    }

    var body: some View {
        HStack(spacing: 8) {
            if let url = URL(string: link.url) {
                Link(destination: url) {
                    HStack(spacing: 6) {
                        Image(systemName: platform.icon)
                            .font(.system(size: 13))
                            .foregroundStyle(Cumbre.terra)
                        Text(platform.label)
                            .font(.system(size: 13))
                            .foregroundStyle(Cumbre.ink)
                        if let categoryLabel {
                            Text(categoryLabel)
                                .font(Cumbre.mono(10, .bold))
                                .foregroundStyle(Cumbre.terra)
                                .padding(.horizontal, 6).padding(.vertical, 2)
                                .overlay(RoundedRectangle(cornerRadius: 3).stroke(Cumbre.terra, lineWidth: 1))
                        }
                        Image(systemName: "arrow.up.right.square")
                            .font(.system(size: 11))
                            .foregroundStyle(Cumbre.ink3)
                    }
                }
                .buttonStyle(.plain)
            }
            Spacer()
            if Auth.auth().currentUser?.uid == link.uid {
                Button(action: onDelete) {
                    Image(systemName: "trash")
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                        .padding(6).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
    }
}

// Para .sheet(item:)/ForEach — el id ya existe en el DTO de Kotlin.
extension BetaLink: Identifiable {}
