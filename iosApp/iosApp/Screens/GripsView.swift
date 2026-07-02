import SwiftUI
import Shared

/// Sesión de báscula conectada — equivalente a GripScaleSession.kt
/// (Android). Usa @Published para que cualquier vista se entere al
/// instante (igual que el StateFlow en Android).
@MainActor
final class GripScaleSession: ObservableObject {
    static let shared = GripScaleSession()
    @Published var connectedDeviceId: String?
    @Published var connectedAlias: String?

    func set(deviceId: String, alias: String?) {
        connectedDeviceId = deviceId
        connectedAlias = alias
    }
    func updateAlias(deviceId: String, alias: String) {
        if connectedDeviceId == deviceId { connectedAlias = alias }
    }
    func clear() {
        connectedDeviceId = nil
        connectedAlias = nil
    }
}

@MainActor
final class GripsViewModel: ObservableObject {
    @Published var gripTypes: [GripType] = []
    @Published var maxes: [GripMaxRecord] = []
    @Published var workouts: [GripWorkout] = []
    @Published var loading = true
    @Published var error: String?

    private let getGripTypes = AppDependencies.shared.container.getGripTypes
    private let getMyGripMaxes = AppDependencies.shared.container.getMyGripMaxes
    private let getMyGripWorkouts = AppDependencies.shared.container.getMyGripWorkouts
    private let deleteGripWorkout = AppDependencies.shared.container.deleteGripWorkout

    func load() async {
        loading = true
        do {
            gripTypes = try await getGripTypes.invoke()
            maxes = try await getMyGripMaxes.invoke()
            workouts = try await getMyGripWorkouts.invoke()
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }

    func delete(workoutId: String) async {
        _ = try? await deleteGripWorkout.invoke(id: workoutId)
        await load()
    }
}

struct GripsView: View {
    @StateObject private var vm = GripsViewModel()
    @ObservedObject private var session = GripScaleSession.shared

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Estado de conexión, siempre visible.
                    HStack(spacing: 8) {
                        Circle()
                            .fill(session.connectedDeviceId != nil ? Cumbre.ok : Cumbre.ink3)
                            .frame(width: 8, height: 8)
                        Text(session.connectedDeviceId != nil
                             ? "Báscula conectada · \(session.connectedAlias ?? session.connectedDeviceId ?? "")"
                             : "Ninguna báscula conectada")
                            .font(.system(size: 13))
                            .foregroundStyle(Cumbre.ink2)
                        Spacer()
                        if session.connectedDeviceId != nil {
                            Button("DESCONECTAR") { session.clear() }
                                .font(Cumbre.mono(11, .bold))
                                .foregroundStyle(Cumbre.terra)
                        }
                    }

                    HStack(spacing: 8) {
                        NavigationLink(destination: GripConnectView()) {
                            actionCard(
                                icon: session.connectedDeviceId != nil ? "wave.3.right.circle.fill" : "wave.3.right",
                                label: session.connectedDeviceId != nil ? "BÁSCULA CONECTADA" : "CONECTAR BÁSCULA"
                            )
                        }
                        NavigationLink(destination: GripMeasureView()) {
                            actionCard(icon: "chart.line.uptrend.xyaxis", label: "MEDIR MÁXIMO")
                        }
                    }

                    if !vm.maxes.isEmpty {
                        Text("TUS MÁXIMOS").eyebrow()
                        VStack(alignment: .leading, spacing: 4) {
                            ForEach(groupedMaxes(), id: \.gripTypeId) { group in
                                if let gripType = vm.gripTypes.first(where: { $0.id == group.gripTypeId }) {
                                    HStack {
                                        Text(gripTypeLabel(gripType)).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                                        Spacer()
                                        Text("IZQ \(fmt(group.left)) · DER \(fmt(group.right))")
                                            .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                                    }
                                }
                            }
                        }
                        NavigationLink(destination: GripProgressView()) {
                            HStack { Spacer(); Text("VER PROGRESO").eyebrow(Cumbre.terra); Spacer() }
                                .padding(.vertical, 4)
                        }
                    }

                    Divider().overlay(Cumbre.rule)

                    HStack {
                        Text("TUS ENTRENOS").eyebrow()
                        Spacer()
                        NavigationLink(destination: GripWorkoutEditorView(workoutId: nil)) {
                            Image(systemName: "plus").foregroundStyle(Cumbre.terra)
                        }
                    }

                    if vm.workouts.isEmpty {
                        Text("Aún no has creado ningún entreno")
                            .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                            .frame(maxWidth: .infinity).padding(.vertical, 24)
                    } else {
                        VStack(spacing: 0) {
                            ForEach(vm.workouts, id: \.id) { workout in
                                NavigationLink(destination: GripWorkoutEditorView(workoutId: workout.id)) {
                                    HStack {
                                        VStack(alignment: .leading) {
                                            Text(workout.name).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                                            Text("\(workout.sets.count) sets · \(handModeLabel(workout.handMode))")
                                                .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                                        }
                                        Spacer()
                                        Image(systemName: "chevron.right").foregroundStyle(Cumbre.ink3)
                                    }
                                    .padding(.vertical, 12)
                                }
                                Divider().overlay(Cumbre.rule)
                            }
                        }
                    }
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Agarres")
            .task { await vm.load() }
        }
    }

    private struct MaxGroup { let gripTypeId: Int32; let left: Double?; let right: Double? }
    private func groupedMaxes() -> [MaxGroup] {
        let ids = Set(vm.maxes.map { $0.gripTypeId })
        return ids.map { id in
            MaxGroup(
                gripTypeId: id,
                left: vm.maxes.first { $0.gripTypeId == id && $0.hand == "LEFT" }?.maxKg,
                right: vm.maxes.first { $0.gripTypeId == id && $0.hand == "RIGHT" }?.maxKg
            )
        }
    }
    private func fmt(_ v: Double?) -> String {
        guard let v else { return "—kg" }
        return String(format: "%.1fkg", v)
    }

    private func actionCard(icon: String, label: String) -> some View {
        VStack(spacing: 8) {
            Image(systemName: icon).font(.system(size: 24)).foregroundStyle(Cumbre.terra)
            Text(label).eyebrow()
        }
        .frame(maxWidth: .infinity)
        .padding(16)
        .background(Cumbre.paper)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }
}
