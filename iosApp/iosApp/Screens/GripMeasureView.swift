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

    private let getGripTypes = AppDependencies.shared.container.getGripTypes
    private let createGripMeasureSession = AppDependencies.shared.container.createGripMeasureSession
    private let provider = AppDependencies.shared.container.gripScaleProvider
    private var measureTask: Task<Void, Never>?

    var connectedDeviceId: String? { GripScaleSession.shared.connectedDeviceId }

    func load() async {
        gripTypes = (try? await getGripTypes.invoke()) ?? []
        selectedGripType = gripTypes.first
    }

    func startMeasuring() {
        guard let deviceId = connectedDeviceId, let provider else { return }
        points = []
        phase = .measuring
        saved = false
        measureTask = Task {
            for await reading in provider.observeWeight(deviceId: deviceId) {
                points.append(GripChartPoint(kg: reading.kg, hand: nil))
            }
        }
    }

    func stopMeasuring() {
        measureTask?.cancel()
        measureTask = nil
        provider?.disconnect()
        let kgs = points.map { $0.kg }
        guard !kgs.isEmpty else { phase = .idle; return }
        let peak = kgs.max() ?? 0
        let avg = kgs.reduce(0, +) / Double(kgs.count)
        let durationS = max(kgs.count / 8, 1) // ~8Hz de muestreo (protocolo WH-C06)
        phase = .done(peakKg: peak, avgKg: avg, durationS: durationS)
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
    }
}

struct GripMeasureView: View {
    @StateObject private var vm = GripMeasureViewModel()

    var body: some View {
        Group {
            if vm.connectedDeviceId == nil {
                Text("Conecta primero tu báscula desde la pantalla anterior")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                    .multilineTextAlignment(.center).padding(24)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    VStack(spacing: 16) {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(vm.gripTypes, id: \.id) { g in
                                    let isSel = g.id == vm.selectedGripType?.id
                                    Text(gripTypeLabel(g))
                                        .font(.system(size: 13, weight: .semibold))
                                        .padding(.horizontal, 14).padding(.vertical, 8)
                                        .background(isSel ? Cumbre.terra : Cumbre.paper)
                                        .foregroundStyle(isSel ? .white : Cumbre.ink)
                                        .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
                                        .clipShape(Capsule())
                                        .onTapGesture {
                                            if vm.phase == .idle { vm.selectedGripType = g }
                                        }
                                }
                            }
                        }

                        HStack(spacing: 8) {
                            handButton("IZQUIERDA", value: "LEFT")
                            handButton("DERECHA", value: "RIGHT")
                        }

                        GripLineChartView(points: vm.points)

                        switch vm.phase {
                        case .idle:
                            Text("Pico: — kg").font(Cumbre.serif(28, .bold)).foregroundStyle(Cumbre.ink)
                            Button {
                                vm.startMeasuring()
                            } label: {
                                Text("EMPEZAR A TIRAR").frame(maxWidth: .infinity).padding(.vertical, 14)
                                    .background(Cumbre.terra).foregroundStyle(.white)
                            }
                            .disabled(vm.selectedGripType == nil)
                        case .measuring:
                            Text("Pico: \(String(format: "%.1f", vm.points.map { $0.kg }.max() ?? 0)) kg")
                                .font(Cumbre.serif(28, .bold)).foregroundStyle(Cumbre.ink)
                            Button {
                                vm.stopMeasuring()
                            } label: {
                                Text("PARAR").frame(maxWidth: .infinity).padding(.vertical, 14)
                                    .background(Cumbre.bad).foregroundStyle(.white)
                            }
                        case .done(let peak, let avg, let duration):
                            VStack(spacing: 4) {
                                Text("Pico: \(String(format: "%.1f", peak)) kg")
                                    .font(Cumbre.serif(28, .bold)).foregroundStyle(Cumbre.ink)
                                Text("Media: \(String(format: "%.1f", avg)) kg · \(duration)s")
                                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                            }
                            if vm.saved {
                                Text("GUARDADO").eyebrow(Cumbre.terra).frame(maxWidth: .infinity).padding(.vertical, 8)
                            } else {
                                HStack(spacing: 8) {
                                    Button { vm.reset() } label: {
                                        Text("REPETIR").frame(maxWidth: .infinity).padding(.vertical, 14)
                                            .background(Cumbre.paper).foregroundStyle(Cumbre.ink)
                                            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                                    }
                                    Button { vm.save() } label: {
                                        Text("GUARDAR").frame(maxWidth: .infinity).padding(.vertical, 14)
                                            .background(Cumbre.terra).foregroundStyle(.white)
                                    }
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

    private func handButton(_ label: String, value: String) -> some View {
        let isSel = vm.hand == value
        return Text(label)
            .font(.system(size: 14, weight: .semibold))
            .frame(maxWidth: .infinity).padding(.vertical, 12)
            .background(isSel ? Cumbre.terra : Cumbre.paper)
            .foregroundStyle(isSel ? .white : Cumbre.ink)
            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
            .onTapGesture {
                if vm.phase == .idle { vm.hand = value }
            }
    }
}
