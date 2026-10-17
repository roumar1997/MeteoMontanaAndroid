import SwiftUI
import Shared

/// Admin: restricciones de UNA escuela (cierres por nidificación, sectores regulados, avisos). Lista + alta/edición.
/// Regla de Álvaro (2026-10-17): solo se cargan con FECHAS CONCRETAS o como cierre PERMANENTE confirmado, y solo en
/// sectores que existan en Cumbre. El servidor lo vuelve a comprobar.
struct AdminRestrictionsSheet: View {
    let school: School
    @Environment(\.dismiss) private var dismiss
    @State private var items: [Restriction] = []
    @State private var blocks: [Block] = []
    @State private var loading = true
    @State private var editing: RestrictionEditTarget?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text(L("Solo con fechas concretas (o cierre permanente confirmado) y en sectores que existan en Cumbre."))
                        .font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
                        .padding(16)
                    Button { editing = RestrictionEditTarget(restriction: nil) } label: {
                        Text(L("+ NUEVA RESTRICCIÓN")).font(Cumbre.mono(12, .bold)).tracking(0.8)
                            .foregroundStyle(.white).frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(Cumbre.terraFill)
                    }
                    .buttonStyle(.plain).padding(.horizontal, 16).padding(.bottom, 12)
                    if loading {
                        ProgressView().frame(maxWidth: .infinity).padding(.top, 30)
                    } else if items.isEmpty {
                        Text(L("Esta escuela no tiene restricciones.")).font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                            .frame(maxWidth: .infinity).padding(.top, 30)
                    }
                    ForEach(items, id: \.id) { r in
                        Divider().overlay(Cumbre.rule)
                        Button { editing = RestrictionEditTarget(restriction: r) } label: {
                            HStack(alignment: .top, spacing: 10) {
                                RestrictionMark(kind: r.kind, size: 20)
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(restrictionWhenText(r)).font(.system(size: 15, weight: .bold))
                                        .foregroundStyle(restrictionColor(r.kind))
                                    if let p = restrictionPlace(r, blocks: blocks) {
                                        Text(p).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink)
                                    }
                                    Text(r.reason).font(.system(size: 13)).foregroundStyle(Cumbre.ink).lineLimit(2)
                                    Text(activeLabel(r)).font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink3)
                                }
                                Spacer()
                                Text("▸").foregroundStyle(Cumbre.terra)
                            }
                            .padding(.horizontal, 16).padding(.vertical, 12).contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle(L("Restricciones · %@", school.name))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.terra) } }
        }
        .task { await reload() }
        .sheet(item: $editing) { t in
            RestrictionEditSheet(school: school, blocks: blocks, original: t.restriction) {
                editing = nil
                Task { await reload() }
            }
        }
    }

    /// Si está en vigor HOY o no (la lista incluye las que aún no han empezado o ya acabaron este año).
    private func activeLabel(_ r: Restriction) -> String {
        guard let active = activeIds else { return "" }
        return active.contains(r.id) ? L("EN VIGOR HOY") : L("FUERA DE FECHAS HOY")
    }

    @State private var activeIds: Set<String>?

    private func reload() async {
        loading = true
        defer { loading = false }
        let c = AppDependencies.shared.container
        blocks = (try? await c.getBlocks.invoke(schoolId: school.id)) ?? []
        items = (try? await c.getSchoolRestrictions.invoke(schoolId: school.id)) ?? []
        if let active = try? await c.getActiveRestrictions.invoke(forceRefresh: true) {
            activeIds = Set(active.map(\.id))
        }
    }
}

struct RestrictionEditTarget: Identifiable {
    let restriction: Restriction?
    var id: String { restriction?.id ?? "new" }
}

/// Alta o edición de una restricción.
struct RestrictionEditSheet: View {
    let school: School
    let blocks: [Block]
    let original: Restriction?
    let onDone: () -> Void

    @State private var kind = "CLOSED"
    @State private var sectorId: String?
    @State private var wholeSector = true
    @State private var blockIds: Set<String> = []
    @State private var reason = ""
    @State private var permanent = false
    @State private var yearly = true
    @State private var startsOn = Date()
    @State private var endsOn = Date()
    @State private var sourceName = ""
    @State private var sourceUrl = ""
    @State private var verifiedOn = Date()
    @State private var saving = false
    @State private var error: String?
    @State private var confirmDelete = false

    private var sectors: [Block] { blocks.filter { $0.type.uppercased() == "ZONE" }.sorted { $0.name < $1.name } }
    private var sectorBlocks: [Block] {
        blocks.filter { $0.type.uppercased() == "BLOCK" && $0.sectorBlockId == sectorId }.sorted { $0.name < $1.name }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section(L("Tipo")) {
                    Picker(L("Tipo"), selection: $kind) {
                        Text(L("Cerrado")).tag("CLOSED")
                        Text(L("Regulado")).tag("REGULATED")
                        Text(L("Aviso")).tag("NOTICE")
                    }.pickerStyle(.segmented)
                }
                Section(L("Dónde")) {
                    Picker(L("Sector"), selection: $sectorId) {
                        Text(L("Elige un sector")).tag(String?.none)
                        ForEach(sectors, id: \.id) { s in Text(s.name).tag(Optional(s.id)) }
                    }
                    if sectorId != nil {
                        Toggle(L("Todo el sector"), isOn: $wholeSector)
                        if !wholeSector {
                            ForEach(sectorBlocks, id: \.id) { b in
                                Button {
                                    if blockIds.contains(b.id) { blockIds.remove(b.id) } else { blockIds.insert(b.id) }
                                } label: {
                                    HStack {
                                        Text(b.name).foregroundStyle(Cumbre.ink)
                                        Spacer()
                                        if blockIds.contains(b.id) { Image(systemName: "checkmark").foregroundStyle(Cumbre.terra) }
                                    }
                                }
                            }
                        }
                    }
                }
                Section(L("Cuándo")) {
                    if kind == "CLOSED" { Toggle(L("Cierre permanente"), isOn: $permanent) }
                    if !permanent || kind != "CLOSED" {
                        Toggle(L("Se repite cada año"), isOn: $yearly)
                        DatePicker(L("Desde"), selection: $startsOn, displayedComponents: .date)
                        DatePicker(L("Hasta"), selection: $endsOn, displayedComponents: .date)
                    }
                }
                Section(L("Motivo")) {
                    TextField(L("Ej.: nidificación de rapaces"), text: $reason, axis: .vertical)
                }
                Section(L("Fuente oficial")) {
                    TextField(L("Ej.: BOCM nº 70, 10 mar 2026"), text: $sourceName)
                    TextField(L("Enlace (opcional)"), text: $sourceUrl)
                        .keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                    DatePicker(L("Comprobado el"), selection: $verifiedOn, displayedComponents: .date)
                }
                if let error { Section { Text(error).foregroundStyle(Cumbre.bad) } }
                if original != nil {
                    Section {
                        Button(L("Eliminar restricción"), role: .destructive) { confirmDelete = true }
                    }
                }
            }
            .navigationTitle(original == nil ? L("Nueva restricción") : L("Editar restricción"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(NSLocalizedString("common_cancel", comment: "")) { onDone() }.foregroundStyle(Cumbre.terra)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(L("GUARDAR")) { Task { await save() } }
                        .font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.terra).disabled(saving || !isValid)
                }
            }
            .confirmationDialog(L("¿Eliminar esta restricción?"), isPresented: $confirmDelete, titleVisibility: .visible) {
                Button(L("Eliminar"), role: .destructive) { Task { await delete() } }
            }
        }
        .onAppear(perform: fill)
    }

    private var isPermanent: Bool { permanent && kind == "CLOSED" }

    private var isValid: Bool {
        sectorId != nil && (wholeSector || !blockIds.isEmpty)
            && !reason.trimmingCharacters(in: .whitespaces).isEmpty
            && !sourceName.trimmingCharacters(in: .whitespaces).isEmpty
            && (isPermanent || yearly || endsOn >= startsOn)
    }

    private static let day: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    private func fill() {
        guard let r = original else { return }
        kind = restrictionKindCode(r.kind)
        sectorId = r.sectorBlockId ?? blocks.first { r.blockIds.contains($0.id) }?.sectorBlockId
        wholeSector = r.blockIds.isEmpty
        blockIds = Set(r.blockIds)
        reason = r.reason
        permanent = r.permanent
        yearly = r.yearly
        if let s = r.startsOn.flatMap(Self.day.date(from:)) { startsOn = s }
        if let e = r.endsOn.flatMap(Self.day.date(from:)) { endsOn = e }
        sourceName = r.sourceName
        sourceUrl = r.sourceUrl ?? ""
        if let v = r.verifiedOn.flatMap(Self.day.date(from:)) { verifiedOn = v }
    }

    private func save() async {
        saving = true
        defer { saving = false }
        let url = sourceUrl.trimmingCharacters(in: .whitespaces)
        let draft = RestrictionDraft(
            schoolId: school.id,
            sectorBlockId: sectorId,
            blockIds: wholeSector ? [] : Array(blockIds),
            kind: kind,
            reason: reason.trimmingCharacters(in: .whitespacesAndNewlines),
            startsOn: isPermanent ? nil : Self.day.string(from: startsOn),
            endsOn: isPermanent ? nil : Self.day.string(from: endsOn),
            yearly: !isPermanent && yearly,
            permanent: isPermanent,
            sourceName: sourceName.trimmingCharacters(in: .whitespaces),
            sourceUrl: url.isEmpty ? nil : url,
            verifiedOn: Self.day.string(from: verifiedOn))
        do {
            _ = try await AppDependencies.shared.container.saveRestriction.invoke(id: original?.id, draft: draft)
            onDone()
        } catch {
            self.error = L("No se pudo guardar. Revisa los datos y la conexión.")
        }
    }

    private func delete() async {
        guard let id = original?.id else { return }
        do {
            _ = try await AppDependencies.shared.container.deleteRestriction.invoke(id: id)
            onDone()
        } catch {
            self.error = L("No se pudo eliminar.")
        }
    }
}
