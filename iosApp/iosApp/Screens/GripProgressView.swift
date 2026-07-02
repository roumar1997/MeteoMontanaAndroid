import SwiftUI
import Shared

@MainActor
final class GripProgressViewModel: ObservableObject {
    @Published var gripTypes: [GripType] = []
    @Published var selectedGripType: GripType?
    @Published var hand = "LEFT"
    @Published var sessions: [GripMeasureSession] = []
    @Published var loading = true

    private let getGripTypes = AppDependencies.shared.container.getGripTypes
    private let getMyGripMeasureSessions = AppDependencies.shared.container.getMyGripMeasureSessions

    func load() async {
        gripTypes = (try? await getGripTypes.invoke()) ?? []
        selectedGripType = gripTypes.first
        await loadSessions()
    }

    func selectGripType(_ t: GripType) {
        selectedGripType = t
        Task { await loadSessions() }
    }

    func selectHand(_ h: String) {
        hand = h
        Task { await loadSessions() }
    }

    private func loadSessions() async {
        guard let gripType = selectedGripType else { return }
        loading = true
        let list = (try? await getMyGripMeasureSessions.invoke(gripTypeId: KotlinInt(int: gripType.id), hand: hand)) ?? []
        sessions = list.reversed() // más antiguo primero, para la gráfica
        loading = false
    }
}

struct GripProgressView: View {
    @StateObject private var vm = GripProgressViewModel()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
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
                                .onTapGesture { vm.selectGripType(g) }
                        }
                    }
                }

                HStack(spacing: 8) {
                    handChip("IZQUIERDA", "LEFT")
                    handChip("DERECHA", "RIGHT")
                }

                if vm.loading {
                    ProgressView().frame(maxWidth: .infinity).padding(.vertical, 40)
                } else if vm.sessions.isEmpty {
                    Text("Aún no has medido este agarre con esta mano")
                        .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                        .frame(maxWidth: .infinity).multilineTextAlignment(.center).padding(.vertical, 40)
                } else {
                    GripLineChartView(points: vm.sessions.map { GripChartPoint(kg: $0.peakKg, hand: nil) })
                    Text(String(format: "Máximo histórico: %.1f kg", vm.sessions.map { $0.peakKg }.max() ?? 0))
                        .font(.system(size: 16, weight: .semibold)).foregroundStyle(Cumbre.ink)
                        .frame(maxWidth: .infinity).multilineTextAlignment(.center)
                }
            }
            .padding(16)
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Progreso")
        .navigationBarTitleDisplayMode(.inline)
        .task { await vm.load() }
    }

    private func handChip(_ label: String, _ value: String) -> some View {
        let isSel = vm.hand == value
        return Text(label)
            .font(.system(size: 14, weight: .semibold))
            .frame(maxWidth: .infinity).padding(.vertical, 12)
            .background(isSel ? Cumbre.terra : Cumbre.paper)
            .foregroundStyle(isSel ? .white : Cumbre.ink)
            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
            .onTapGesture { vm.selectHand(value) }
    }
}
