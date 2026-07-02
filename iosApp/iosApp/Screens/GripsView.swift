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
                    // Cabecera: título + pill de estado de báscula.
                    HStack {
                        Text("Agarres").font(Cumbre.serif(28, .bold)).foregroundStyle(Cumbre.ink)
                        Spacer()
                        scaleStatusPill
                    }

                    HStack(spacing: 8) {
                        NavigationLink(destination: GripConnectView()) {
                            actionCard(
                                icon: session.connectedDeviceId != nil ? "wave.3.right.circle.fill" : "wave.3.right",
                                label: session.connectedDeviceId != nil ? "BÁSCULA CONECTADA" : "CONECTAR BÁSCULA",
                                caption: session.connectedDeviceId != nil ? "Toca para cambiar" : "Busca tu WH-C06",
                                highlighted: session.connectedDeviceId == nil
                            )
                        }
                        NavigationLink(destination: GripMeasureView()) {
                            actionCard(icon: "chart.line.uptrend.xyaxis", label: "MEDIR MÁXIMO",
                                       caption: "Por agarre y mano")
                        }
                    }
                    NavigationLink(destination: GripClimbGameView()) {
                        gameCard
                    }

                    HStack {
                        Text("TUS MÁXIMOS").eyebrow()
                        Spacer()
                        if !vm.maxes.isEmpty {
                            NavigationLink(destination: GripProgressView()) {
                                Text("VER PROGRESO").eyebrow(Cumbre.terra)
                            }
                        }
                    }

                    if vm.maxes.isEmpty {
                        emptyCard(
                            title: "Aún no has medido ningún máximo",
                            body: "Conecta la báscula y haz tu primera prueba: elige agarre y mano, tira fuerte unos segundos y guarda.",
                            ctaLabel: "EMPEZAR A MEDIR",
                            ctaDestination: AnyView(GripMeasureView())
                        )
                    } else {
                        GripMaxesTableView(gripTypes: vm.gripTypes, maxes: vm.maxes)
                    }

                    HStack {
                        Text("TUS ENTRENOS").eyebrow()
                        Spacer()
                        NavigationLink(destination: GripWorkoutEditorView(workoutId: nil)) {
                            Image(systemName: "plus").foregroundStyle(Cumbre.terra)
                        }
                    }

                    if vm.workouts.isEmpty {
                        emptyCard(
                            title: "Aún no has creado ningún entreno",
                            body: "Define sets, reps, tiempos y el rango de fuerza objetivo (% de tu máximo).",
                            ctaLabel: "CREAR ENTRENO",
                            ctaDestination: AnyView(GripWorkoutEditorView(workoutId: nil))
                        )
                    } else {
                        VStack(spacing: 6) {
                            ForEach(vm.workouts, id: \.id) { workout in
                                workoutCard(workout)
                            }
                        }
                    }
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
            .onAppear { Task { await vm.load() } }
        }
    }

    private var scaleStatusPill: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(session.connectedDeviceId != nil ? Cumbre.ok : Cumbre.ink3)
                .frame(width: 8, height: 8)
            Text(session.connectedDeviceId != nil
                 ? (session.connectedAlias ?? "Conectada")
                 : "Sin báscula")
                .font(.system(size: 12))
                .foregroundStyle(Cumbre.ink)
                .lineLimit(1)
            if session.connectedDeviceId != nil {
                Text("✕")
                    .font(.system(size: 12))
                    .foregroundStyle(Cumbre.ink3)
                    .onTapGesture { session.clear() }
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 5)
        .background(Cumbre.paper)
        .clipShape(Capsule())
        .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
    }

    private var gameCard: some View {
        HStack(spacing: 12) {
            Image(systemName: "mountain.2").font(.system(size: 26)).foregroundStyle(Cumbre.terra)
            VStack(alignment: .leading, spacing: 2) {
                Text("JUEGO: SUBE LA PARED").eyebrow(Cumbre.ink)
                Text("Tira para escalar; suelta y rapelas")
                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
            }
            Spacer()
            Image(systemName: "chevron.right").foregroundStyle(Cumbre.ink3)
        }
        .padding(16)
        .background(Cumbre.paper)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }

    private func actionCard(icon: String, label: String, caption: String, highlighted: Bool = false) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon).font(.system(size: 24)).foregroundStyle(Cumbre.terra)
            Text(label).eyebrow(Cumbre.ink).multilineTextAlignment(.center)
            Text(caption).font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(16)
        .background(Cumbre.paper)
        .overlay(Rectangle().stroke(highlighted ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
    }

    private func emptyCard(title: String, body bodyText: String, ctaLabel: String, ctaDestination: AnyView) -> some View {
        VStack(spacing: 6) {
            Text(title).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                .multilineTextAlignment(.center)
            Text(bodyText).font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                .multilineTextAlignment(.center)
            NavigationLink(destination: ctaDestination) {
                Text(ctaLabel).eyebrow(Cumbre.terra).padding(.top, 8)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(16)
        .background(Cumbre.paper)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }

    private func workoutCard(_ workout: GripWorkout) -> some View {
        HStack {
            NavigationLink(destination: GripWorkoutEditorView(workoutId: workout.id)) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(workout.name).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                    Text("\(workout.sets.count) sets · \(handModeLabel(workout.handMode))")
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                }
                Spacer()
            }
            Button {
                Task { await vm.delete(workoutId: workout.id) }
            } label: {
                Image(systemName: "trash").foregroundStyle(Cumbre.ink3)
            }
            .buttonStyle(.plain)
            Image(systemName: "chevron.right").foregroundStyle(Cumbre.ink3)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .background(Cumbre.paper)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }
}
