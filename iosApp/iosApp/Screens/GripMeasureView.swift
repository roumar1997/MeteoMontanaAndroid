import SwiftUI
import Shared

enum MeasurePhase: Equatable {
    case idle
    case measuring
    case done(peakKg: Double, avgKg: Double, durationS: Int)
}

@MainActor
final class GripMeasureViewModel: ObservableObject {
    @Published var gripTypes: [GripType] = []
    @Published var selectedGripType: GripType?
    @Published var hand = "LEFT"
    @Published var points: [GripChartPoint] = []
    @Published var phase: MeasurePhase = .idle
    @Published var saved = false
    @Published var sessionPeak: Double = 0

    private let getGripTypes = AppDependencies.shared.container.getGripTypes
    private let createGripMeasureSession = AppDependencies.shared.container.createGripMeasureSession
    private let provider = AppDependencies.shared.container.gripScaleProvider
    private var measureTask: Task<Void, Never>?

    // Estadísticas de TODA la sesión (la lista de puntos se acota para el
    // render; sin esto la gráfica se aplastaba con el tiempo).
    private var sessionSum: Double = 0
    private var sessionCount = 0

    var connectedDeviceId: String? { GripScaleSession.shared.connectedDeviceId }

    func load() async {
        gripTypes = (try? await getGripTypes.invoke()) ?? []
        if selectedGripType == nil { selectedGripType = gripTypes.first }
    }

    func startMeasuring() {
        guard let deviceId = connectedDeviceId, let provider else { return }
        points = []
        phase = .measuring
        saved = false
        sessionPeak = 0; sessionSum = 0; sessionCount = 0
        measureTask = Task {
            for await reading in provider.observeWeight(deviceId: deviceId) {
                sessionCount += 1
                sessionSum += reading.kg
                if reading.kg > sessionPeak { sessionPeak = reading.kg }
                points.append(GripChartPoint(kg: reading.kg, hand: nil))
                if points.count > 300 { points.removeFirst(points.count - 300) }
            }
        }
    }

    func stopMeasuring() {
        measureTask?.cancel()
        measureTask = nil
        provider?.disconnect()
        guard sessionCount > 0 else { phase = .idle; return }
        let avg = sessionSum / Double(sessionCount)
        let durationS = max(sessionCount / 8, 1) // ~8Hz de muestreo (protocolo WH-C06)
        phase = .done(peakKg: sessionPeak, avgKg: avg, durationS: durationS)
    }

    func save() {
        guard let gripType = selectedGripType, case let .done(peak, avg, duration) = phase else { return }
        Task {
            _ = try? await createGripMeasureSession.invoke(req: CreateGripMeasureSessionRequest(
                gripTypeId: gripType.id, hand: hand, peakKg: peak, avgKg: avg,
                durationS: Int32(duration), edgeMm: nil
            ))
            saved = true
        }
    }

    func reset() {
        phase = .idle
        points = []
        saved = false
        sessionPeak = 0; sessionSum = 0; sessionCount = 0
    }
}

struct GripMeasureView: View {
    @StateObject private var vm = GripMeasureViewModel()

    var body: some View {
        Group {
            if vm.connectedDeviceId == nil {
                Text("Conecta primero tu báscula desde la pantalla de Agarres")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                    .multilineTextAlignment(.center).padding(24)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        let editable = vm.phase == .idle

                        GripTypeTwoAxisSelector(
                            gripTypes: vm.gripTypes, selected: vm.selectedGripType,
                            enabled: editable, onSelect: { vm.selectedGripType = $0 }
                        )

                        Text("MANO").eyebrow()
                        HandSelectorView(hand: vm.hand, enabled: editable, onSelect: { vm.hand = $0 })

                        switch vm.phase {
                        case .idle:
                            bigKgDisplay(kg: nil,
                                sublabel: "Elige agarre y mano, cuelga la báscula y dale a EMPEZAR")
                            GripLineChartView(points: vm.points)
                            primaryButton("EMPEZAR A TIRAR", enabled: vm.selectedGripType != nil, bg: Cumbre.terra) {
                                vm.startMeasuring()
                            }
                        case .measuring:
                            let liveKg = vm.points.last?.kg
                            bigKgDisplay(kg: liveKg,
                                sublabel: vm.sessionPeak > 0 ? String(format: "Pico hasta ahora: %.1f kg", vm.sessionPeak) : "Tira ya…")
                            GripLineChartView(points: vm.points, yMaxKg: vm.sessionPeak)
                            primaryButton("PARAR", enabled: true, bg: Cumbre.bad) {
                                vm.stopMeasuring()
                            }
                        case .done(let peak, let avg, let duration):
                            resultCard(peakKg: peak, avgKg: avg, durationS: duration)
                            GripLineChartView(points: vm.points, yMaxKg: peak)
                            if vm.saved {
                                Text("✓ GUARDADO — si supera tu máximo anterior, queda como nuevo récord")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(Cumbre.terra)
                                    .multilineTextAlignment(.center)
                                    .frame(maxWidth: .infinity).padding(12)
                                    .background(Cumbre.terraBg)
                                    .overlay(Rectangle().stroke(Cumbre.terra, lineWidth: 1))
                                secondaryButton("MEDIR OTRA VEZ") { vm.reset() }
                            } else {
                                HStack(spacing: 8) {
                                    secondaryButton("REPETIR") { vm.reset() }
                                    primaryButton("GUARDAR", enabled: true, bg: Cumbre.terra) { vm.save() }
                                }
                            }
                        }
                    }
                    .padding(16)
                }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Medir máximo")
        .navigationBarTitleDisplayMode(.inline)
        .task { await vm.load() }
        .onDisappear { vm.stopMeasuring() }
        // Pantalla siempre encendida mientras se mide (manos ocupadas con la báscula).
        .onAppear { UIApplication.shared.isIdleTimerDisabled = true }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
    }

    /// Número enorme en mono — protagonista absoluto de la pantalla.
    private func bigKgDisplay(kg: Double?, sublabel: String) -> some View {
        VStack(spacing: 2) {
            HStack(alignment: .bottom, spacing: 2) {
                Text(kg.map { String(format: "%.1f", $0) } ?? "—")
                    .font(Cumbre.mono(64, .bold))
                    .foregroundStyle(Cumbre.ink)
                Text("kg").font(Cumbre.mono(20))
                    .foregroundStyle(Cumbre.ink3)
                    .padding(.bottom, 12)
            }
            Text(sublabel).font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }

    private func resultCard(peakKg: Double, avgKg: Double, durationS: Int) -> some View {
        VStack(spacing: 4) {
            Text("RESULTADO").eyebrow()
            HStack(alignment: .bottom, spacing: 2) {
                Text(String(format: "%.1f", peakKg))
                    .font(Cumbre.mono(52, .bold)).foregroundStyle(Cumbre.terra)
                Text("kg").font(Cumbre.mono(18)).foregroundStyle(Cumbre.ink3)
                    .padding(.bottom, 10)
            }
            if let g = vm.selectedGripType {
                Text("\(fingerGroupLabel(g.fingerGroup)) · \(gripStyleLabel(g.style)) · \(vm.hand == "LEFT" ? "Izquierda" : "Derecha")")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
            }
            Text(String(format: "Media %.1f kg · %ds de tirón", avgKg, durationS))
                .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
        }
        .frame(maxWidth: .infinity)
        .padding(16)
        .background(Cumbre.paper)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }

    private func primaryButton(_ label: String, enabled: Bool, bg: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(.system(size: 15, weight: .semibold))
                .frame(maxWidth: .infinity).padding(.vertical, 15)
                .background(enabled ? bg : Cumbre.ink3)
                .foregroundStyle(.white)
        }
        .disabled(!enabled)
    }

    private func secondaryButton(_ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(.system(size: 15, weight: .semibold))
                .frame(maxWidth: .infinity).padding(.vertical, 15)
                .background(Cumbre.paper).foregroundStyle(Cumbre.ink)
                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }
    }
}
