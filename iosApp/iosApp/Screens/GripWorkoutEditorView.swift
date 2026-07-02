import SwiftUI
import Shared

struct EditableGripSet: Identifiable {
    let id = UUID()
    var reps: Int32 = 6
    var workS: Int32 = 10
    var restS: Int32 = 20
    var gripTypeId: Int32?
    var targetMinPct: Double = 10
    var targetMaxPct: Double = 30
}

@MainActor
final class GripWorkoutEditorViewModel: ObservableObject {
    @Published var name = ""
    @Published var handMode = "POR_REP"
    @Published var countMode = "TIEMPO"
    @Published var restBetweenSetsS: Int32 = 30
    @Published var sets: [EditableGripSet] = [EditableGripSet()]
    @Published var gripTypes: [GripType] = []
    @Published var loading = true

    let workoutId: String?

    private let getGripTypes = AppDependencies.shared.container.getGripTypes
    private let getGripWorkout = AppDependencies.shared.container.getGripWorkout
    private let createGripWorkout = AppDependencies.shared.container.createGripWorkout
    private let updateGripWorkout = AppDependencies.shared.container.updateGripWorkout

    init(workoutId: String?) { self.workoutId = workoutId }

    func load() async {
        let types = (try? await getGripTypes.invoke()) ?? []
        gripTypes = types
        if let workoutId, let w = try? await getGripWorkout.invoke(id: workoutId) {
            name = w.name
            handMode = w.handMode
            countMode = w.countMode
            restBetweenSetsS = w.restBetweenSetsS
            sets = w.sets.map {
                EditableGripSet(reps: $0.reps, workS: $0.workS, restS: $0.restS,
                                 gripTypeId: $0.gripTypeId, targetMinPct: $0.targetMinPct, targetMaxPct: $0.targetMaxPct)
            }
        } else {
            sets = [EditableGripSet(gripTypeId: types.first?.id)]
        }
        loading = false
    }

    func addSet() {
        let last = sets.last ?? EditableGripSet(gripTypeId: gripTypes.first?.id)
        sets.append(last)
    }

    func removeSet(_ id: UUID) {
        guard sets.count > 1 else { return }
        sets.removeAll { $0.id == id }
    }

    func applyToAll(reps: Int32, workS: Int32, restS: Int32) {
        for i in sets.indices { sets[i].reps = reps; sets[i].workS = workS; sets[i].restS = restS }
    }

    var estimatedDurationSeconds: Int32 {
        let perSet = sets.reduce(Int32(0)) { $0 + $1.reps * ($1.workS + $1.restS) }
        let betweenSets = Int32(max(sets.count - 1, 0)) * restBetweenSetsS
        return perSet + betweenSets
    }

    var canSave: Bool { !name.trimmingCharacters(in: .whitespaces).isEmpty && sets.allSatisfy { $0.gripTypeId != nil } }

    func save() async -> String? {
        guard canSave else { return nil }
        let req = CreateGripWorkoutRequest(
            name: name.trimmingCharacters(in: .whitespaces), handMode: handMode, countMode: countMode,
            restBetweenSetsS: restBetweenSetsS,
            sets: sets.enumerated().map { idx, s in
                GripWorkoutSetRequest(sortOrder: Int32(idx), reps: s.reps, workS: s.workS, restS: s.restS,
                                       gripTypeId: s.gripTypeId!, targetMinPct: s.targetMinPct, targetMaxPct: s.targetMaxPct)
            }
        )
        if let workoutId {
            let updated = try? await updateGripWorkout.invoke(id: workoutId, req: req)
            return updated?.id
        } else {
            let created = try? await createGripWorkout.invoke(req: req)
            return created?.id
        }
    }
}

struct GripWorkoutEditorView: View {
    @StateObject private var vm: GripWorkoutEditorViewModel
    @State private var showMassEdit = false
    @State private var navigateToRun: String?

    init(workoutId: String?) {
        _vm = StateObject(wrappedValue: GripWorkoutEditorViewModel(workoutId: workoutId))
    }

    var body: some View {
        Group {
            if vm.loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        TextField("Nombre del entreno", text: $vm.name)
                            .textFieldStyle(.roundedBorder)

                        Text("CAMBIO DE MANO").eyebrow()
                        segmented(["UNA": "Una/Ambas", "POR_SERIE": "Por serie", "POR_REP": "Por rep"], selection: $vm.handMode)

                        Text("CONTAR POR").eyebrow()
                        segmented(["TIEMPO": "Tiempo", "PESO": "Peso"], selection: $vm.countMode)

                        HStack {
                            Text("SETS").eyebrow()
                            Spacer()
                            Button("EDICIÓN MASIVA") { showMassEdit = true }
                                .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                        }

                        ForEach(Array(vm.sets.enumerated()), id: \.element.id) { idx, _ in
                            setCard(index: idx)
                        }

                        Button {
                            vm.addSet()
                        } label: {
                            Text("+ AGREGAR SET").frame(maxWidth: .infinity).padding(.vertical, 14)
                                .foregroundStyle(Cumbre.terra)
                                .overlay(Rectangle().stroke(Cumbre.terra, lineWidth: 1))
                        }

                        HStack {
                            Text("Descanso entre sets").font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                            Spacer()
                            Stepper("\(vm.restBetweenSetsS)s", value: Binding(
                                get: { Int(vm.restBetweenSetsS) },
                                set: { vm.restBetweenSetsS = Int32($0) }
                            ), in: 0...600, step: 5)
                            .fixedSize()
                        }

                        Text("Duración estimada: \(vm.estimatedDurationSeconds / 60)m \(vm.estimatedDurationSeconds % 60)s")
                            .font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.terra)
                            .frame(maxWidth: .infinity).multilineTextAlignment(.center)
                    }
                    .padding(16)
                }
                VStack {
                    Divider().overlay(Cumbre.rule)
                    Button {
                        Task {
                            if let id = await vm.save() { navigateToRun = id }
                        }
                    } label: {
                        Text("GUARDAR").frame(maxWidth: .infinity).padding(.vertical, 14)
                            .background(vm.canSave ? Cumbre.terra : Cumbre.ink3)
                            .foregroundStyle(.white)
                    }
                    .disabled(!vm.canSave)
                    .padding(16)
                }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Editar entreno")
        .navigationBarTitleDisplayMode(.inline)
        .task { await vm.load() }
        .navigationDestination(item: $navigateToRun) { workoutId in
            GripWorkoutRunView(workoutId: workoutId)
        }
        .sheet(isPresented: $showMassEdit) {
            MassEditSheet(vm: vm)
        }
    }

    private func segmented(_ options: [String: String], selection: Binding<String>) -> some View {
        HStack(spacing: 4) {
            ForEach(["UNA", "POR_SERIE", "POR_REP", "TIEMPO", "PESO"].filter { options[$0] != nil }, id: \.self) { key in
                let isSel = selection.wrappedValue == key
                Text(options[key] ?? key)
                    .font(.system(size: 13, weight: .semibold))
                    .frame(maxWidth: .infinity).padding(.vertical, 10)
                    .background(isSel ? Cumbre.terra : Cumbre.paper)
                    .foregroundStyle(isSel ? .white : Cumbre.ink)
                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                    .onTapGesture { selection.wrappedValue = key }
            }
        }
    }

    private func setCard(index: Int) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("SET #\(index + 1)").eyebrow(Cumbre.terra)
                Spacer()
                if vm.sets.count > 1 {
                    Button {
                        vm.removeSet(vm.sets[index].id)
                    } label: { Image(systemName: "xmark").foregroundStyle(Cumbre.ink3) }
                }
            }
            HStack(spacing: 16) {
                miniStepper("Reps", value: Binding(
                    get: { Int(vm.sets[index].reps) }, set: { vm.sets[index].reps = Int32($0) }
                ), range: 1...50)
                if vm.countMode == "TIEMPO" {
                    miniStepper("Trabajo (s)", value: Binding(
                        get: { Int(vm.sets[index].workS) }, set: { vm.sets[index].workS = Int32($0) }
                    ), range: 1...600, step: 5)
                }
                miniStepper("Descanso (s)", value: Binding(
                    get: { Int(vm.sets[index].restS) }, set: { vm.sets[index].restS = Int32($0) }
                ), range: 0...600, step: 5)
            }
            GripTypeTwoAxisSelector(
                gripTypes: vm.gripTypes,
                selected: vm.gripTypes.first { $0.id == vm.sets[index].gripTypeId },
                onSelect: { g in vm.sets[index].gripTypeId = g.id }
            )
            Text("RANGO OBJETIVO: \(Int(vm.sets[index].targetMinPct))% – \(Int(vm.sets[index].targetMaxPct))% de tu máximo")
                .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
            RangeSliderView(
                minValue: Binding(get: { vm.sets[index].targetMinPct }, set: { vm.sets[index].targetMinPct = $0 }),
                maxValue: Binding(get: { vm.sets[index].targetMaxPct }, set: { vm.sets[index].targetMaxPct = $0 }),
                bounds: 0...100
            )
        }
        .padding(12)
        .background(Cumbre.paper)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }

    private func miniStepper(_ label: String, value: Binding<Int>, range: ClosedRange<Int>, step: Int = 1) -> some View {
        VStack(spacing: 2) {
            Text(label).font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
            Stepper("\(value.wrappedValue)", value: value, in: range, step: step)
                .fixedSize()
        }
    }
}

/// Slider de rango sencillo (dos sliders solapados) — SwiftUI no trae uno
/// nativo con dos manejadores hasta iOS 17+.
struct RangeSliderView: View {
    @Binding var minValue: Double
    @Binding var maxValue: Double
    let bounds: ClosedRange<Double>

    var body: some View {
        VStack(spacing: 4) {
            HStack {
                Text("Mín").font(.system(size: 10)).foregroundStyle(Cumbre.ink3)
                Slider(value: Binding(
                    get: { minValue },
                    set: { minValue = min($0, maxValue) }
                ), in: bounds)
                Text("Máx").font(.system(size: 10)).foregroundStyle(Cumbre.ink3)
                Slider(value: Binding(
                    get: { maxValue },
                    set: { maxValue = max($0, minValue) }
                ), in: bounds)
            }
        }
    }
}

private struct MassEditSheet: View {
    @ObservedObject var vm: GripWorkoutEditorViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var reps: Int
    @State private var workS: Int
    @State private var restS: Int

    init(vm: GripWorkoutEditorViewModel) {
        self.vm = vm
        _reps = State(initialValue: Int(vm.sets.first?.reps ?? 6))
        _workS = State(initialValue: Int(vm.sets.first?.workS ?? 10))
        _restS = State(initialValue: Int(vm.sets.first?.restS ?? 20))
    }

    var body: some View {
        NavigationStack {
            Form {
                Stepper("Reps: \(reps)", value: $reps, in: 1...50)
                Stepper("Trabajo: \(workS)s", value: $workS, in: 1...600, step: 5)
                Stepper("Descanso: \(restS)s", value: $restS, in: 0...600, step: 5)
            }
            .navigationTitle("Edición masiva")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("APLICAR") {
                        vm.applyToAll(reps: Int32(reps), workS: Int32(workS), restS: Int32(restS))
                        dismiss()
                    }
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button(NSLocalizedString("common_cancel", comment: "")) { dismiss() }
                }
            }
        }
    }
}
