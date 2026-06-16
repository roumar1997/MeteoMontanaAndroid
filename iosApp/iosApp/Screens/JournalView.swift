import SwiftUI
import Shared

// Diario de escalada — espejo de JournalEntriesScreen.kt + stats del perfil.

@MainActor
final class JournalViewModel: ObservableObject {
    @Published var entries: [JournalSession] = []
    @Published var stats: JournalStats?
    @Published var loading = true

    private let getMyJournal: GetMyJournalUseCase
    private let getMyStats: GetMyJournalStatsUseCase
    private let createEntry: CreateJournalEntryUseCase
    private let deleteEntry: DeleteJournalEntryUseCase

    init(
        getMyJournal: GetMyJournalUseCase = AppDependencies.shared.container.getMyJournal,
        getMyStats: GetMyJournalStatsUseCase = AppDependencies.shared.container.getMyJournalStats,
        createEntry: CreateJournalEntryUseCase = AppDependencies.shared.container.createJournalEntry,
        deleteEntry: DeleteJournalEntryUseCase = AppDependencies.shared.container.deleteJournalEntry
    ) {
        self.getMyJournal = getMyJournal
        self.getMyStats = getMyStats
        self.createEntry = createEntry
        self.deleteEntry = deleteEntry
    }

    func load() async {
        loading = true
        entries = (try? await getMyJournal.invoke()) ?? []
        stats = try? await getMyStats.invoke()
        loading = false
    }

    func add(blockName: String, grade: String, schoolName: String, sector: String, notes: String) async {
        let df = DateFormatter(); df.dateFormat = "yyyy-MM-dd"
        let req = CreateJournalRequest(
            schoolId: nil,
            schoolName: schoolName.nilIfBlank,
            sector: sector.nilIfBlank,
            blockName: blockName.trimmingCharacters(in: .whitespaces),
            grade: grade.nilIfBlank,
            notes: notes.nilIfBlank,
            date: df.string(from: Date())
        )
        _ = try? await createEntry.invoke(req: req)
        await load()
    }

    func delete(_ id: String) {
        entries.removeAll { $0.id == id }
        Task { try? await deleteEntry.invoke(id: id); await load() }
    }
}

struct JournalView: View {
    @StateObject private var vm = JournalViewModel()
    @State private var showAdd = false

    var body: some View {
        Group {
            if vm.loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        if let s = vm.stats { JournalStatsRow(stats: s) }
                        Button { showAdd = true } label: {
                            Text("+ AÑADIR BLOQUE").font(Cumbre.mono(12, .bold)).tracking(0.8)
                                .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity)
                                .background(Cumbre.ink)
                        }
                        .buttonStyle(.plain).padding(16)
                        Divider().overlay(Cumbre.rule)
                        if vm.entries.isEmpty {
                            Text("Aún no has registrado bloques.")
                                .font(.system(size: 14)).foregroundStyle(Cumbre.ink2).padding(32)
                        } else {
                            ForEach(vm.entries, id: \.id) { e in
                                JournalRow(entry: e) { vm.delete(e.id) }
                                Divider().overlay(Cumbre.rule)
                            }
                        }
                    }
                }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Mi diario")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showAdd) {
            AddBlockSheet { block, grade, school, sector, notes in
                Task { await vm.add(blockName: block, grade: grade, schoolName: school, sector: sector, notes: notes) }
            }
        }
        .task { await vm.load() }
    }
}

struct JournalStatsRow: View {
    let stats: JournalStats
    var body: some View {
        HStack(spacing: 0) {
            cell("\(stats.blockCount)", "BLOQUES")
            cell("\(stats.schoolCount)", "ESCUELAS")
            cell(stats.maxGrade ?? "—", "GRADO MÁX")
        }
        .padding(.vertical, 16)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        .padding(16)
    }
    private func cell(_ v: String, _ l: String) -> some View {
        VStack(spacing: 3) {
            Text(v).font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink)
            Text(l).font(Cumbre.mono(9, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink3)
        }.frame(maxWidth: .infinity)
    }
}

private struct JournalRow: View {
    let entry: JournalSession
    let onDelete: () -> Void
    var body: some View {
        HStack(spacing: 12) {
            if let g = entry.grade, !g.isEmpty {
                Text(g).font(Cumbre.mono(12, .bold)).foregroundStyle(.white)
                    .frame(width: 44, height: 32).background(GradeColor.color(g))
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(entry.blockName).font(Cumbre.serif(16, .semibold)).foregroundStyle(Cumbre.ink)
                let sub = [entry.schoolName, entry.sector].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
                if !sub.isEmpty { Text(sub).font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3) }
                if let n = entry.notes, !n.isEmpty { Text(n).font(.system(size: 13)).foregroundStyle(Cumbre.ink2) }
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 4) {
                Text(String(entry.date.prefix(10))).font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                Button(action: onDelete) {
                    Image(systemName: "trash").font(.system(size: 14)).foregroundStyle(Cumbre.bad)
                }.buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
    }
}

/// Formulario para registrar un bloque escalado.
private struct AddBlockSheet: View {
    let onSave: (String, String, String, String, String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var block = ""
    @State private var grade = ""
    @State private var school = ""
    @State private var sector = ""
    @State private var notes = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    field("BLOQUE / VÍA", $block, "Nombre del bloque")
                    field("GRADO", $grade, "p. ej. 6c+")
                    field("ESCUELA", $school, "Nombre de la escuela")
                    field("SECTOR", $sector, "Sector (opcional)")
                    field("NOTAS", $notes, "Comentarios")
                }.padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Añadir bloque")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("Cancelar") { dismiss() }.foregroundStyle(Cumbre.ink3) }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Guardar") {
                        onSave(block, grade, school, sector, notes); dismiss()
                    }.foregroundStyle(Cumbre.terra)
                        .disabled(block.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
    private func field(_ label: String, _ text: Binding<String>, _ ph: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).eyebrow()
            TextField(ph, text: text).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                .padding(10).background(Cumbre.paper).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }
    }
}

private extension String { var nilIfBlank: String? { trimmingCharacters(in: .whitespaces).isEmpty ? nil : self } }
