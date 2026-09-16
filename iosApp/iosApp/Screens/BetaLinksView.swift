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

    func add(blockId: String, lineId: String?, url: String, heightCategory: String?, authorName: String?) async {
        if let created = await reporting("No se pudo añadir el enlace", {
            try await container.addBetaLink.invoke(blockId: blockId, lineId: lineId, url: url, heightCategory: heightCategory, authorName: authorName)
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
    @State private var justAdded = false
    // Denuncia (requisito App Store para UGC) — mismo patrón que comentarios:
    // se oculta al instante para quien denuncia; si denuncia un admin se
    // borra ya en el servidor (Álvaro, 2026-09-16: "la gente puede subir lo
    // que quiera, debe poder denunciar... y yo como admin eliminar cualquiera").
    @ObservedObject private var moderation = ModerationStore.shared
    @State private var reportTarget: BetaLink? = nil

    private var all: [BetaLink] {
        store.links
            .filter { $0.blockId == blockId && $0.lineId == lineId
                      && !moderation.hiddenIds.contains("BETA_LINK:\($0.id)") }
            .sorted { ($0.createdAt ?? "") > ($1.createdAt ?? "") }
    }

    private var shown: [BetaLink] {
        viewFilter == .any ? all : all.filter { $0.heightCategory == viewFilter.rawValue }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Button { withAnimation { expanded.toggle() } } label: {
                HStack(spacing: 6) {
                    // Círculo relleno en terracota cuando HAY vídeos, contorno
                    // gris cuando no — para que se note de un vistazo si esta
                    // vía tiene beta en vídeo (Álvaro, 2026-09-16).
                    ZStack {
                        Circle()
                            .fill(all.isEmpty ? Color.clear : Cumbre.terra)
                            .overlay(Circle().stroke(all.isEmpty ? Cumbre.ink3 : Color.clear, lineWidth: 1))
                        Image(systemName: "play.fill")
                            .font(.system(size: 7))
                            .foregroundStyle(all.isEmpty ? Cumbre.ink3 : Cumbre.bg)
                            .offset(x: 0.5)
                    }
                    .frame(width: 15, height: 15)
                    Text("BETA" + (all.isEmpty ? "" : " · \(all.count)"))
                        .font(Cumbre.mono(10, .bold)).tracking(1.0)
                        .foregroundStyle(all.isEmpty ? Cumbre.ink3 : Cumbre.terra)
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
                    BetaLinkRow(link: l,
                                onDelete: { Task { await store.delete(linkId: l.id) } },
                                onReport: { reportTarget = l })
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
                Button(f.label) { pastingUrlFor = f }
            }
            Button("Cancelar", role: .cancel) {}
        }
        // Paso 2: pegar el enlace (+ de quién es, opcional) — hoja propia, no
        // alert nativo, para poder bloquear "Guardar" sin enlace y avisar en
        // el sitio (Álvaro, 2026-09-16: "no debería dejarte darle a guardar
        // si no tiene el enlace, y que salga ahí el error").
        .sheet(item: $pastingUrlFor) { category in
            BetaLinkUrlSheet(category: category, onCancel: { pastingUrlFor = nil }) { url, authorName in
                pastingUrlFor = nil
                Task {
                    await store.add(blockId: blockId, lineId: lineId, url: url,
                                     heightCategory: category == .any ? nil : category.rawValue,
                                     authorName: authorName)
                    justAdded = true
                    try? await Task.sleep(nanoseconds: 2_000_000_000)
                    justAdded = false
                }
            }
        }
        .sheet(item: $reportTarget) { l in
            ReportSheet(title: "DENUNCIAR ENLACE DE BETA") { reason, _ in
                moderation.report(targetType: "BETA_LINK", targetId: l.id, reason: reason)
            }
        }
    }
}

/// Paso 2 del alta: pegar el enlace + nombre opcional. Hoja propia (no alert
/// nativo) porque el sistema no permite deshabilitar botones ni mostrar
/// errores dentro de un `.alert` — aquí "Guardar" queda bloqueado sin enlace
/// y el aviso sale junto al campo (Álvaro, 2026-09-16).
private struct BetaLinkUrlSheet: View {
    let category: HeightFilter
    let onCancel: () -> Void
    let onSave: (_ url: String, _ authorName: String?) -> Void
    @State private var urlDraft = ""
    @State private var authorNameDraft = ""
    @State private var touched = false

    private var trimmedUrl: String { urlDraft.trimmingCharacters(in: .whitespaces) }
    private var showError: Bool { touched && trimmedUrl.isEmpty }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("ENLACE DE BETA").font(Cumbre.mono(11, .bold)).tracking(1.4)
                .foregroundStyle(Cumbre.ink3)
                .padding(.top, 18)
            Text(category == .any ? "Se abrirá fuera de la app." : "Para \(category.label.lowercased()). Se abrirá fuera de la app.")
                .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
            TextField("Enlace de Instagram/YouTube", text: $urlDraft)
                .keyboardType(.URL)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .padding(10)
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(showError ? Color.red : Cumbre.rule, lineWidth: 1))
                .onChange(of: urlDraft) { _ in touched = true }
            if showError {
                Text("Falta el enlace de Instagram/YouTube.")
                    .font(.system(size: 12)).foregroundStyle(.red)
            }
            TextField("¿De quién es la beta? (opcional)", text: $authorNameDraft)
                .padding(10)
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(Cumbre.rule, lineWidth: 1))
            HStack {
                Button("CANCELAR") { onCancel() }
                    .font(Cumbre.mono(12, .bold))
                    .foregroundStyle(Cumbre.ink3)
                Spacer()
                Button("GUARDAR") {
                    touched = true
                    guard !trimmedUrl.isEmpty else { return }
                    let name = authorNameDraft.trimmingCharacters(in: .whitespaces)
                    onSave(trimmedUrl, name.isEmpty ? nil : name)
                }
                .font(Cumbre.mono(12, .bold))
                .foregroundStyle(trimmedUrl.isEmpty ? Cumbre.ink3 : Cumbre.terra)
                .disabled(trimmedUrl.isEmpty)
            }
            .padding(.top, 4)
            Spacer()
        }
        .padding(.horizontal, 16)
        .presentationDetents([.medium])
        .background(Cumbre.bg)
    }
}

/// Fila de un enlace: chip pulsable (abre fuera de la app) + su categoría de
/// altura si tiene, + borrar si es mío.
private struct BetaLinkRow: View {
    let link: BetaLink
    let onDelete: () -> Void
    let onReport: () -> Void

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
        VStack(alignment: .leading, spacing: 2) {
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
                // Propio → borrar directo. Ajeno → denunciar (si eres admin,
                // denunciar lo borra ya en el servidor — mismo patrón que
                // comentarios, no hace falta un botón de borrar aparte).
                if Auth.auth().currentUser?.uid == link.uid {
                    Button(action: onDelete) {
                        Image(systemName: "trash")
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                            .padding(6).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                } else {
                    Button(action: onReport) {
                        Image(systemName: "flag")
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3.opacity(0.7))
                            .padding(6).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            if let author = link.authorName, !author.isEmpty {
                Text("de \(author)")
                    .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
            }
        }
    }
}

// Para .sheet(item:)/ForEach — el id ya existe en el DTO de Kotlin.
extension BetaLink: Identifiable {}
