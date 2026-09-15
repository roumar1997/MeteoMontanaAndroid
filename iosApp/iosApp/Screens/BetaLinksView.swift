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
    /// Etiqueta corta para los chips de VER (filtrar la lista).
    var shortLabel: String {
        switch self {
        case .any: return "Todas"
        case .tall: return "+1,70"
        case .short: return "-1,70"
        }
    }
}

/// Hilo desplegable de enlaces de beta: la CABECERA ENTERA es pulsable.
/// lineId = nil → enlaces de la piedra entera. Añadir es un flujo de DOS
/// PASOS (Álvaro, 2026-09-15: "que salga como para que tú pongas el enlace
/// y digas si es para más o menos de 1,70... no así como si fuera un
/// comentario"): 1) elegir para quién, 2) pegar el enlace.
struct BetaLinksThreadView: View {
    @ObservedObject var store: BetaLinksStore
    let blockId: String
    let lineId: String?
    @State private var expanded = false
    @State private var viewFilter: HeightFilter = .any
    @State private var choosingCategory = false
    @State private var pastingUrlFor: HeightFilter? = nil
    @State private var urlDraft = ""
    @State private var justAdded = false

    private var all: [BetaLink] {
        store.links
            .filter { $0.blockId == blockId && $0.lineId == lineId }
            .sorted { ($0.createdAt ?? "") > ($1.createdAt ?? "") }
    }

    private var shown: [BetaLink] {
        viewFilter == .any ? all : all.filter { $0.heightCategory == viewFilter.rawValue }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Button { withAnimation { expanded.toggle() } } label: {
                HStack(spacing: 6) {
                    Image(systemName: "play.rectangle")
                        .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                    Text("BETA" + (all.isEmpty ? "" : " · \(all.count)"))
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
                // VER: qué enlaces se muestran — todos, o solo los de una altura.
                if all.count > 1 {
                    HStack(spacing: 6) {
                        ForEach(HeightFilter.allCases) { f in
                            Button { viewFilter = f } label: {
                                Text(f.shortLabel)
                                    .font(Cumbre.mono(10, viewFilter == f ? .bold : .regular))
                                    .foregroundStyle(viewFilter == f ? Cumbre.bg : Cumbre.ink3)
                                    .padding(.horizontal, 9).padding(.vertical, 4)
                                    .background(viewFilter == f ? Cumbre.ink : Color.clear)
                                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Cumbre.rule, lineWidth: 1))
                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.bottom, 4)
                }
                if shown.isEmpty {
                    Text(all.isEmpty ? "Sé el primero en dejar un enlace de beta."
                                      : "No hay enlaces de beta para \(viewFilter.label.lowercased()).")
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                }
                ForEach(Array(shown.enumerated()), id: \.element.id) { idx, l in
                    if idx > 0 { Divider().overlay(Cumbre.rule) }
                    BetaLinkRow(link: l) {
                        Task { await store.delete(linkId: l.id) }
                    }
                    .padding(.vertical, 4)
                }
                if justAdded {
                    Label("Enlace añadido", systemImage: "checkmark.circle.fill")
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ok)
                        .padding(.top, 2)
                }
                Button { choosingCategory = true } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "plus.circle")
                        Text("Añadir enlace de beta")
                    }
                    .font(.system(size: 12))
                    .foregroundStyle(Cumbre.terra)
                }
                .buttonStyle(.plain)
                .padding(.top, 4).padding(.bottom, 4)
            }
        }
        // Paso 1: ¿para quién es esta beta?
        .confirmationDialog("¿Para quién es esta beta?", isPresented: $choosingCategory, titleVisibility: .visible) {
            ForEach(HeightFilter.allCases) { f in
                Button(f.label) {
                    urlDraft = ""
                    pastingUrlFor = f
                }
            }
            Button("Cancelar", role: .cancel) {}
        }
        // Paso 2: pegar el enlace.
        .alert("Enlace de beta", isPresented: Binding(
            get: { pastingUrlFor != nil },
            set: { if !$0 { pastingUrlFor = nil } }
        )) {
            TextField("Enlace de Instagram/YouTube", text: $urlDraft)
                .keyboardType(.URL)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            Button("Cancelar", role: .cancel) { pastingUrlFor = nil }
            Button("Guardar") {
                let u = urlDraft.trimmingCharacters(in: .whitespaces)
                guard !u.isEmpty, let category = pastingUrlFor else { pastingUrlFor = nil; return }
                pastingUrlFor = nil
                Task {
                    await store.add(blockId: blockId, lineId: lineId, url: u,
                                     heightCategory: category == .any ? nil : category.rawValue)
                    justAdded = true
                    try? await Task.sleep(nanoseconds: 2_000_000_000)
                    justAdded = false
                }
            }
        } message: {
            if let category = pastingUrlFor {
                Text(category == .any ? "Se abrirá fuera de la app." : "Para \(category.label.lowercased()). Se abrirá fuera de la app.")
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
