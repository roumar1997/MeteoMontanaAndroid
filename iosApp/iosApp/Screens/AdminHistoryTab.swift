import SwiftUI
import Shared

/// HISTORIAL (admin): cada cambio de piedras, muros, sectores, parkings y vías — quién, cuándo, por qué camino y qué
/// cambió — con RESTAURAR la versión anterior. «SIN REVISIÓN» = lo que publicaron usuarios de confianza.
struct AdminHistoryTab: View {
    /// Si se da, solo los cambios de esa persona (desde APORTACIONES).
    var actorUid: String? = nil

    private enum Filter: String, CaseIterable {
        case todo = "TODO", sinRevision = "SIN REVISIÓN", propuestas = "PROPUESTAS", admin = "ADMIN"
        case restauraciones = "RESTAURADO"
        var source: String? {
            switch self {
            case .todo: return nil
            case .sinRevision: return "TRUSTED"
            case .propuestas: return "CONTRIBUTION"
            case .admin: return "ADMIN"
            case .restauraciones: return "RESTORE"
            }
        }
    }

    @State private var filter: Filter = .todo
    @State private var items: [ContentChangeItem] = []
    @State private var loading = false
    @State private var canLoadMore = true
    @State private var selected: ContentChangeItem?

    var body: some View {
        VStack(spacing: 0) {
            if actorUid == nil {
                VStack(alignment: .leading, spacing: 6) {
                    Text("QUÉ ES ESTO").font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.terra)
                    Text("Cada vez que alguien cambia una piedra, una vía o un muro, queda aquí apuntado: quién, cuándo y qué cambió (lo de antes en rojo, lo nuevo en verde). Toca un cambio para verlo y, si hace falta, RESTAURAR la versión anterior.")
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                    Text("ADMIN = lo hizo un administrador · PROPUESTA = se aprobó una propuesta · SIN REVISIÓN = lo publicó alguien de confianza sin pasar por ti · RESTAURADO = se devolvió a una versión anterior.")
                        .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                }
                .adminCard().padding(.horizontal, 16).padding(.top, 8)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(Filter.allCases, id: \.self) { f in
                        Button { filter = f } label: {
                            Text(f.rawValue).font(Cumbre.mono(10, .bold)).tracking(0.6)
                                .foregroundStyle(filter == f ? .white : Cumbre.ink2)
                                .padding(.horizontal, 10).padding(.vertical, 6)
                                .background(filter == f ? Cumbre.terra : .clear, in: Capsule())
                                .overlay(Capsule().stroke(filter == f ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                        }.buttonStyle(.plain)
                    }
                }.padding(.horizontal, 16).padding(.vertical, 8)
            }
            ScrollView {
                LazyVStack(spacing: 8) {
                    if items.isEmpty && !loading {
                        AdminEmptyState(symbol: "clock.arrow.circlepath", text: L("Sin cambios con este filtro."))
                    }
                    ForEach(items, id: \.id) { c in
                        Button { selected = c } label: { row(c) }.buttonStyle(.plain)
                    }
                    if loading { ProgressView().padding() }
                    else if canLoadMore && !items.isEmpty {
                        Button { Task { await load(more: true) } } label: {
                            Text(L("CARGAR MÁS")).font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra).padding()
                        }.buttonStyle(.plain)
                    }
                }
            }
        }
        .task(id: filter) { await load(more: false) }
        .sheet(item: Binding(get: { selected.map { ChangeSel(change: $0) } }, set: { selected = $0?.change })) { sel in
            ChangeDetailSheet(change: sel.change) { Task { await load(more: false) } }
        }
    }

    private struct ChangeSel: Identifiable { let change: ContentChangeItem; var id: String { change.id } }

    private func row(_ c: ContentChangeItem) -> some View {
        HStack(alignment: .top, spacing: 10) {
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(c.entityType == "LINE" ? L("VÍA") : L("PIEDRA")).font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.ink3)
                    Text(c.title).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink).lineLimit(1)
                }
                Text(summary(c)).font(.system(size: 12)).foregroundStyle(Cumbre.ink2).lineLimit(2)
                Text("\(c.actorName ?? L("Sistema")) · \(c.createdAt.replacingOccurrences(of: "T", with: " ").prefix(16))")
                    .font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
            }
            Spacer()
            SourceBadge(source: c.source)
        }
        .adminCard().padding(.horizontal, 16).contentShape(Rectangle())
    }

    private func summary(_ c: ContentChangeItem) -> String {
        if c.isCreation { return L("Creada") }
        if c.isDeletion { return L("Borrada") }
        return L("Cambió: %@", c.changedFields.map(fieldLabel).joined(separator: ", "))
    }

    private func load(more: Bool) async {
        loading = true
        defer { loading = false }
        let before = more ? items.last?.createdAt : nil
        let page = (try? await AppDependencies.shared.container.getContentHistory.invoke(
            schoolId: nil, blockId: nil, actorUid: actorUid, source: filter.source, before: before)) ?? []
        items = more ? items + page : page
        canLoadMore = page.count >= 50
    }
}

/// De dónde vino el cambio, en una etiqueta de color.
struct SourceBadge: View {
    let source: String
    var body: some View {
        let (label, color): (String, Color) = {
            switch source {
            case "TRUSTED": return (L("SIN REVISIÓN"), Cumbre.warn)
            case "CONTRIBUTION": return (L("PROPUESTA"), Cumbre.ok)
            case "ADMIN": return (L("ADMIN"), Cumbre.terra)
            case "RESTORE": return (L("RESTAURADO"), Cumbre.ink2)
            case "USER": return (L("USUARIO"), Cumbre.ink2)
            default: return (L("SISTEMA"), Cumbre.ink3)
            }
        }()
        Text(label).font(Cumbre.mono(9, .bold)).foregroundStyle(color)
            .padding(.horizontal, 6).padding(.vertical, 3)
            .overlay(Capsule().stroke(color, lineWidth: 1))
    }
}

/// Nombre legible de cada campo del estado guardado.
func fieldLabel(_ key: String) -> String {
    switch key {
    case "name": return L("nombre")
    case "grade": return L("grado")
    case "setterGrade": return L("grado del equipador")
    case "lat", "lon": return L("posición")
    case "photoPath": return L("foto")
    case "linePath": return L("trazo")
    case "description": return L("descripción")
    case "sectorBlockId": return L("sector")
    case "startType": return L("salida")
    case "sortOrder": return L("orden")
    case "faceOrder": return L("cara")
    case "variant": return L("variante")
    case "discipline": return L("modalidad")
    case "path", "geometry", "direction": return L("forma del muro")
    default: return key
    }
}

/// Detalle de un cambio: antes → después campo a campo, y RESTAURAR la versión anterior.
struct ChangeDetailSheet: View {
    let change: ContentChangeItem
    let onRestored: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var confirm = false
    @State private var error: String?
    @State private var working = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text(change.title).font(Cumbre.serif(20, .bold)).foregroundStyle(Cumbre.ink)
                        Spacer()
                        SourceBadge(source: change.source)
                    }
                    Text("\(change.actorName ?? L("Sistema")) · \(change.createdAt.replacingOccurrences(of: "T", with: " ").prefix(16))")
                        .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                    let fields = change.isCreation || change.isDeletion
                        ? Array((change.isCreation ? change.after : change.before).keys).sorted()
                        : change.changedFields
                    ForEach(fields, id: \.self) { k in
                        VStack(alignment: .leading, spacing: 3) {
                            Text(fieldLabel(k).uppercased()).font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.ink3)
                            if let b = change.before[k] {
                                Text(b).font(.system(size: 13)).foregroundStyle(Cumbre.bad).strikethrough(!change.isDeletion)
                                    .lineLimit(4)
                            }
                            if let a = change.after[k] {
                                Text(a).font(.system(size: 13)).foregroundStyle(Cumbre.ok).lineLimit(4)
                            }
                        }
                        Divider().overlay(Cumbre.rule)
                    }
                    if let error { Text(error).font(.system(size: 13)).foregroundStyle(Cumbre.bad) }
                    if !change.isCreation {
                        Text("Restaurar deja esta piedra o vía exactamente como estaba ANTES de este cambio. Queda apuntado en el historial como «restaurado», así que también se puede deshacer.")
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                        AdminButton(title: L("RESTAURAR LA VERSIÓN ANTERIOR"), symbol: "arrow.uturn.backward", busy: working) { confirm = true }
                    }
                }
                .padding(20)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.terra) } }
            .confirmationDialog(L("¿Dejarla como estaba antes de este cambio?"), isPresented: $confirm, titleVisibility: .visible) {
                Button(L("Restaurar")) { Task { await restore() } }
            }
        }
    }

    private func restore() async {
        working = true
        defer { working = false }
        do {
            _ = try await AppDependencies.shared.container.restoreContentChange.invoke(changeId: change.id)
            onRestored()
            dismiss()
        } catch {
            self.error = L("No se pudo restaurar (puede que la piedra ya no exista).")
        }
    }
}

/// APORTACIONES (admin): cuánto ha aportado cada persona y su confianza. Tocar una persona muestra sus cambios.
struct AdminContributorsTab: View {
    @State private var items: [ContributorItem] = []
    @State private var loading = true
    @State private var historyOf: ContributorItem?

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("QUÉ ES ESTO").font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.terra)
                    Text(L("Con 20 aportaciones aprobadas una persona pasa a ser de confianza y lo que propone se publica sin revisión. Puedes quitarla o darla a mano."))
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                    Text("El interruptor de la derecha es «de confianza». Toca el nombre para ver todo lo que ha cambiado esa persona.")
                        .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                }
                .adminCard().padding(16)
                if loading { ProgressView().padding(.top, 30) }
                ForEach(items, id: \.uid) { c in
                    HStack(spacing: 10) {
                        Button { historyOf = c } label: {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(c.name ?? c.uid).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink)
                                    .lineLimit(1)
                                Text(L("%@ aprobadas · %@ rechazadas · %@ pendientes", c.approved, c.rejected, c.pending))
                                    .font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink2)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
                        }.buttonStyle(.plain)
                        Toggle("", isOn: Binding(get: { c.trusted }, set: { v in Task { await setTrust(c, v) } }))
                            .labelsHidden().tint(Cumbre.terra)
                    }
                    .adminCard().padding(.horizontal, 16).padding(.bottom, 6)
                }
            }
        }
        .task { await load() }
        .sheet(item: Binding(get: { historyOf.map { Who(c: $0) } }, set: { historyOf = $0?.c })) { w in
            NavigationStack {
                AdminHistoryTab(actorUid: w.c.uid)
                    .navigationTitle(w.c.name ?? w.c.uid).navigationBarTitleDisplayMode(.inline)
            }
        }
    }

    private struct Who: Identifiable { let c: ContributorItem; var id: String { c.uid } }

    private func load() async {
        items = (try? await AppDependencies.shared.container.getContributors.invoke(uid: nil)) ?? []
        loading = false
    }

    private func setTrust(_ c: ContributorItem, _ trusted: Bool) async {
        if (try? await AppDependencies.shared.container.setUserTrust.invoke(uid: c.uid, trusted: trusted)) != nil {
            await load()
        }
    }
}
