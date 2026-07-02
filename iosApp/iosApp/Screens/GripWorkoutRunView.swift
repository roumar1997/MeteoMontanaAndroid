import SwiftUI
import AudioToolbox
import Shared

@MainActor
final class GripWorkoutRunViewModel: ObservableObject {
    @Published var workout: GripWorkout?
    @Published var engineState: GripWorkoutEngine.EngineState?
    @Published var points: [GripChartPoint] = []
    @Published var currentKg: Double = 0
    // Máximo visto en la sesión — fija la escala Y de la gráfica.
    @Published var sessionMaxKg: Double = 0

    private let getGripWorkout = AppDependencies.shared.container.getGripWorkout
    private let getGripTypes = AppDependencies.shared.container.getGripTypes
    private let getMyGripMaxes = AppDependencies.shared.container.getMyGripMaxes
    private let provider = AppDependencies.shared.container.gripScaleProvider

    private var gripTypes: [GripType] = []
    private var maxesByGripHand: [String: Double] = [:]
    private var engine: GripWorkoutEngine?
    private var tickTask: Task<Void, Never>?
    private var weightTask: Task<Void, Never>?
    private var lastBeepMs: Int64 = 0

    var connectedDeviceId: String? { GripScaleSession.shared.connectedDeviceId }

    func load(workoutId: String) async {
        guard let w = try? await getGripWorkout.invoke(id: workoutId) else { return }
        workout = w
        gripTypes = (try? await getGripTypes.invoke()) ?? []
        let maxes = (try? await getMyGripMaxes.invoke()) ?? []
        maxesByGripHand = Dictionary(uniqueKeysWithValues: maxes.map { ("\($0.gripTypeId)_\($0.hand)", $0.maxKg) })
        let e = GripWorkoutEngine(workout: w)
        engine = e
        engineState = e.state.value
        startEngineLoop()
        startWeightListener()
    }

    private func startEngineLoop() {
        tickTask = Task {
            var lastMs = Date().timeIntervalSince1970 * 1000
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 100_000_000)
                guard let e = engine else { break }
                let now = Date().timeIntervalSince1970 * 1000
                let delta = Int64(now - lastMs)
                lastMs = now
                e.tick(deltaMs: delta)
                let s = e.state.value
                engineState = s
                if s.finished { break }
            }
        }
    }

    private func startWeightListener() {
        guard let deviceId = connectedDeviceId, let provider else { return }
        weightTask = Task {
            for await reading in provider.observeWeight(deviceId: deviceId) {
                currentKg = reading.kg
                if reading.kg > sessionMaxKg { sessionMaxKg = reading.kg }
                guard let hand = engineState?.activeHand, hand.name != "NONE" else { continue }
                points.append(GripChartPoint(kg: reading.kg, hand: hand.name))
                if points.count > 200 { points.removeFirst(points.count - 200) }
                checkTargetBeep(reading.kg)
            }
        }
    }

    /// Rango objetivo en kg del set/mano actuales, o nil si no hay máximo guardado.
    func currentTargetRangeKg() -> (Double, Double)? {
        guard let w = workout, let es = engineState else { return nil }
        guard es.setIndex >= 0, Int(es.setIndex) < w.sets.count else { return nil }
        let set = w.sets[Int(es.setIndex)]
        guard es.activeHand.name != "NONE" else { return nil }
        guard let max = maxesByGripHand["\(set.gripTypeId)_\(es.activeHand.name)"] else { return nil }
        return (max * set.targetMinPct / 100.0, max * set.targetMaxPct / 100.0)
    }

    func currentGripLabel() -> String? {
        guard let w = workout, let es = engineState else { return nil }
        guard es.setIndex >= 0, Int(es.setIndex) < w.sets.count else { return nil }
        let set = w.sets[Int(es.setIndex)]
        guard let gt = gripTypes.first(where: { $0.id == set.gripTypeId }) else { return nil }
        return gripTypeLabel(gt)
    }

    /// Pita en bucle mientras la fuerza está fuera del rango objetivo — usa el canal
    /// normal del sistema (respeta silencio/volumen, no salta como una alarma).
    private func checkTargetBeep(_ kg: Double) {
        guard let range = currentTargetRangeKg() else { return }
        let outOfRange = kg < range.0 || kg > range.1
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        if outOfRange && now - lastBeepMs > 500 {
            lastBeepMs = now
            AudioServicesPlaySystemSoundWithCompletion(1057, nil)
        }
    }

    func stop() {
        tickTask?.cancel(); tickTask = nil
        weightTask?.cancel(); weightTask = nil
        provider?.disconnect()
    }
}

struct GripWorkoutRunView: View {
    @StateObject private var vm = GripWorkoutRunViewModel()
    @Environment(\.dismiss) private var dismiss
    let workoutId: String

    var body: some View {
        Group {
            if vm.connectedDeviceId == nil {
                Text("Conecta tu báscula antes de entrenar")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if vm.workout == nil || vm.engineState == nil {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if vm.engineState!.finished {
                VStack(spacing: 8) {
                    Text("¡Entreno completado!")
                        .font(Cumbre.serif(24, .bold)).foregroundStyle(Cumbre.terra)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                runningBody
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(vm.workout?.name ?? "Entreno")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { vm.stop(); dismiss() } label: { Image(systemName: "xmark") }
            }
        }
        .task { await vm.load(workoutId: workoutId) }
        .onDisappear { vm.stop() }
        // Pantalla siempre encendida durante el entreno (manos ocupadas).
        .onAppear { UIApplication.shared.isIdleTimerDisabled = true }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
    }

    @ViewBuilder
    private var runningBody: some View {
        let es = vm.engineState!
        let w = vm.workout!
        let currentSet = es.setIndex >= 0 && Int(es.setIndex) < w.sets.count ? w.sets[Int(es.setIndex)] : nil
        let range = vm.currentTargetRangeKg()
        ScrollView {
            VStack(spacing: 10) {
                Text("SET \(es.setIndex + 1) DE \(w.sets.count)" + (currentSet.map { " · \($0.reps) REPS" } ?? ""))
                    .eyebrow()
                if let label = vm.currentGripLabel() {
                    Text(label).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                }

                handIndicator(es)

                Text(String(format: "%.1f kg", vm.currentKg))
                    .font(Cumbre.mono(40, .bold))
                    .foregroundStyle(kgColor(es: es, range: range))
                if let range {
                    Text(String(format: "OBJETIVO: %.1f – %.1f kg", range.0, range.1)).eyebrow()
                } else if es.activeHand.name != "NONE" {
                    Text("Sin máximo guardado para este agarre y mano — mide tu máximo para tener objetivo")
                        .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                        .multilineTextAlignment(.center).padding(.horizontal, 24)
                }

                GripLineChartView(points: vm.points, targetMin: range?.0, targetMax: range?.1,
                                  yMaxKg: vm.sessionMaxKg)
                    .padding(.horizontal, 8)

                HStack(spacing: 8) {
                    handPanel("IZQUIERDA", es.left, active: es.activeHand.name == "LEFT",
                              totalReps: Int(currentSet?.reps ?? 0), handMode: w.handMode)
                    handPanel("DERECHA", es.right, active: es.activeHand.name == "RIGHT",
                              totalReps: Int(currentSet?.reps ?? 0), handMode: w.handMode)
                }
                .padding(.horizontal, 16)
            }
            .padding(.vertical, 16)
        }
    }

    private func kgColor(es: GripWorkoutEngine.EngineState, range: (Double, Double)?) -> Color {
        guard let range else { return Cumbre.ink }
        if vm.currentKg >= range.0 && vm.currentKg <= range.1 { return Cumbre.terra }
        if es.activeHand.name == "NONE" { return Cumbre.ink }
        return Cumbre.bad
    }

    private func handIndicator(_ es: GripWorkoutEngine.EngineState) -> some View {
        let label: String
        switch es.activeHand.name {
        case "LEFT": label = "TIRA CON LA IZQUIERDA"
        case "RIGHT": label = "TIRA CON LA DERECHA"
        default: label = "DESCANSO"
        }
        let bg: Color = es.activeHand.name == "NONE" ? Cumbre.ink3 : Cumbre.terra
        return Text(label)
            .font(.system(size: 18, weight: .bold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity).padding(.vertical, 16)
            .background(bg)
            .padding(.horizontal, 16)
    }

    private func handPanel(_ label: String, _ hs: GripWorkoutEngine.HandState,
                           active: Bool, totalReps: Int, handMode: String) -> some View {
        let seconds = max(hs.remainingMs / 1000, 0)
        let phaseLabel: String
        switch hs.phase.name {
        case "WORK": phaseLabel = "TRABAJO"
        case "REST", "REST_JUST_ENDED_WORK": phaseLabel = "DESCANSO"
        case "WAITING": phaseLabel = "ESPERANDO"
        default: phaseLabel = "HECHO"
        }
        // Progreso de reps de ESTA mano en el set. En POR_REP las reps se
        // reparten entre las dos manos (contador total conjunto).
        let repsThisHand = handMode == "UNA" ? totalReps : (totalReps + 1) / 2
        return VStack(spacing: 2) {
            Text(label).eyebrow(active ? Cumbre.terra : Cumbre.ink3)
            Text("\(seconds)s").font(Cumbre.mono(20, .bold)).foregroundStyle(Cumbre.ink)
            Text(phaseLabel).font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
            if totalReps > 0 {
                Text("REP \(min(Int(hs.repIndex), repsThisHand))/\(repsThisHand)")
                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(12)
        .background(active ? Cumbre.terra.opacity(0.10) : Cumbre.paper)
        .overlay(Rectangle().stroke(active ? Cumbre.terra : Cumbre.rule, lineWidth: active ? 2 : 1))
    }
}
