import SwiftUI
import Shared

enum ClimbGamePhase: Equatable {
    case setup
    case noMaxRecorded
    case playing
}

@MainActor
final class GripClimbGameViewModel: ObservableObject {
    @Published var gripTypes: [GripType] = []
    @Published var selectedGripType: GripType?
    @Published var hand = "LEFT"
    @Published var difficulty = "MEDIO" // FACIL | MEDIO | DIFICIL
    @Published var uiPhase: ClimbGamePhase = .setup
    @Published var engineState: GripClimbGameEngine.GameState?
    @Published var currentPct: Double = 0

    private let getGripTypes = AppDependencies.shared.container.getGripTypes
    private let getMyGripMaxes = AppDependencies.shared.container.getMyGripMaxes
    private let provider = AppDependencies.shared.container.gripScaleProvider

    private var maxesByGripHand: [String: Double] = [:]
    private var engine: GripClimbGameEngine?
    private var tickTask: Task<Void, Never>?
    private var weightTask: Task<Void, Never>?
    private var currentKg: Double = 0

    var connectedDeviceId: String? { GripScaleSession.shared.connectedDeviceId }

    /// Crecimiento del desplome por metro según dificultad — DUPLICADO de
    /// GripClimbGameEngine (shared) SOLO para dibujar el perfil de la pared
    /// sin cruzar el bridge Kotlin ~100 veces por frame. La física real la
    /// lleva siempre el motor compartido.
    var overhangGrowthPerM: Double {
        switch difficulty {
        case "FACIL": return 1.1
        case "DIFICIL": return 2.6
        default: return 1.8
        }
    }
    func overhangDegAt(_ heightM: Double) -> Double {
        min(heightM * overhangGrowthPerM, 75.0)
    }

    func load() async {
        gripTypes = (try? await getGripTypes.invoke()) ?? []
        if selectedGripType == nil { selectedGripType = gripTypes.first }
        let maxes = (try? await getMyGripMaxes.invoke()) ?? []
        maxesByGripHand = Dictionary(uniqueKeysWithValues: maxes.map { ("\($0.gripTypeId)_\($0.hand)", $0.maxKg) })
    }

    func start() {
        guard let gripType = selectedGripType, let deviceId = connectedDeviceId else { return }
        guard let maxKg = maxesByGripHand["\(gripType.id)_\(hand)"], maxKg > 0 else {
            uiPhase = .noMaxRecorded
            return
        }
        let engineDifficulty: GripClimbGameEngine.Difficulty
        switch difficulty {
        case "FACIL": engineDifficulty = .facil
        case "DIFICIL": engineDifficulty = .dificil
        default: engineDifficulty = .medio
        }
        let e = GripClimbGameEngine(difficulty: engineDifficulty)
        engine = e
        engineState = e.state.value
        uiPhase = .playing
        startWeightListener(deviceId: deviceId)
        startTickLoop(maxKg: maxKg)
    }

    private func startWeightListener(deviceId: String) {
        guard let provider else { return }
        weightTask = Task {
            for await reading in provider.observeWeight(deviceId: deviceId) {
                currentKg = reading.kg
            }
        }
    }

    private func startTickLoop(maxKg: Double) {
        tickTask = Task {
            var lastMs = Date().timeIntervalSince1970 * 1000
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 33_000_000) // ~30fps
                guard let e = engine else { break }
                let now = Date().timeIntervalSince1970 * 1000
                let delta = Int64(now - lastMs)
                lastMs = now
                let pct = min(max(currentKg / maxKg * 100.0, 0), 200)
                currentPct = pct
                let s = e.tick(pctOfMax: pct, deltaMs: delta)
                engineState = s
                if s.phase.name == "GAME_OVER" { break }
            }
        }
    }

    /// Reinicia la partida con los mismos ajustes (agarre/mano/dificultad).
    func retry() {
        stopLoops()
        start()
    }

    func backToSetup() {
        stopLoops()
        uiPhase = .setup
    }

    func stopLoops() {
        tickTask?.cancel(); tickTask = nil
        weightTask?.cancel(); weightTask = nil
        provider?.disconnect()
    }
}

struct GripClimbGameView: View {
    @StateObject private var vm = GripClimbGameViewModel()

    var body: some View {
        Group {
            switch vm.uiPhase {
            case .setup: setupBody
            case .noMaxRecorded: noMaxBody
            case .playing: PlayingCanvas(vm: vm)
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Sube la pared")
        .navigationBarTitleDisplayMode(.inline)
        // En el juego el lienzo manda: ocultamos la barra de navegación.
        .toolbar(vm.uiPhase == .playing ? .hidden : .visible, for: .navigationBar)
        .task { await vm.load() }
        .onDisappear { vm.stopLoops() }
        // Pantalla siempre encendida durante el juego (manos ocupadas).
        .onAppear { UIApplication.shared.isIdleTimerDisabled = true }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
    }

    // =========================================================================
    // Setup
    // =========================================================================

    private var setupBody: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Tira de la báscula para escalar. Suelta y rapelas hacia atrás en péndulo. Cuanto más alto, más se tumba la pared — y más fuerza te pide.")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)

                if vm.connectedDeviceId == nil {
                    Text("Conecta tu báscula primero desde la pantalla de Agarres.")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Cumbre.bad)
                        .frame(maxWidth: .infinity, alignment: .leading).padding(12)
                        .background(Cumbre.bad.opacity(0.10))
                        .overlay(Rectangle().stroke(Cumbre.bad, lineWidth: 1))
                }

                GripTypeTwoAxisSelector(
                    gripTypes: vm.gripTypes, selected: vm.selectedGripType,
                    onSelect: { vm.selectedGripType = $0 }
                )

                Text("MANO").eyebrow()
                HandSelectorView(hand: vm.hand, onSelect: { vm.hand = $0 })

                Text("DIFICULTAD").eyebrow()
                VStack(spacing: 6) {
                    difficultyRow("FACIL", "FÁCIL", "Se tumba despacio · te pedirá hasta el 55% de tu máximo")
                    difficultyRow("MEDIO", "MEDIO", "Ritmo de calentamiento serio · hasta el 75%")
                    difficultyRow("DIFICIL", "DIFÍCIL", "Desploma rápido · hasta el 92% — esto ya no es calentar")
                }

                Button {
                    vm.start()
                } label: {
                    Text("EMPEZAR").font(.system(size: 15, weight: .semibold))
                        .frame(maxWidth: .infinity).padding(.vertical, 15)
                        .background(vm.connectedDeviceId != nil && vm.selectedGripType != nil ? Cumbre.terra : Cumbre.ink3)
                        .foregroundStyle(.white)
                }
                .disabled(vm.connectedDeviceId == nil || vm.selectedGripType == nil)
            }
            .padding(16)
        }
    }

    private func difficultyRow(_ value: String, _ label: String, _ desc: String) -> some View {
        let isSel = vm.difficulty == value
        return HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(label).eyebrow(isSel ? Cumbre.terra : Cumbre.ink)
                Text(desc).font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
            }
            Spacer()
            if isSel { Text("●").foregroundStyle(Cumbre.terra) }
        }
        .padding(12)
        .background(isSel ? Cumbre.terra.opacity(0.10) : Cumbre.paper)
        .overlay(Rectangle().stroke(isSel ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
        .onTapGesture { vm.difficulty = value }
    }

    private var noMaxBody: some View {
        VStack(spacing: 8) {
            Text("Falta tu máximo con esta mano y agarre")
                .font(.system(size: 16, weight: .semibold)).foregroundStyle(Cumbre.ink)
                .multilineTextAlignment(.center)
            Text("El juego mide el esfuerzo como % de TU máximo, así las dos manos exigen lo mismo. Mídelo una vez y listo.")
                .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                .multilineTextAlignment(.center)
            NavigationLink(destination: GripMeasureView()) {
                Text("IR A MEDIR MÁXIMO").font(.system(size: 15, weight: .semibold))
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(Cumbre.terra).foregroundStyle(.white)
            }
            .padding(.top, 8)
            Button { vm.backToSetup() } label: {
                Text("VOLVER").font(.system(size: 15, weight: .semibold))
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(Cumbre.paper).foregroundStyle(Cumbre.ink)
                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
            }
        }
        .padding(20)
        .background(Cumbre.paper)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        .padding(24)
    }
}

// =============================================================================
// Juego — lienzo a pantalla completa
// =============================================================================

private struct PlayingCanvas: View {
    @ObservedObject var vm: GripClimbGameViewModel
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let isDark = colorScheme == .dark
        let skyTop = isDark ? Color(hex: 0x1B2733) : Color(hex: 0xC9DCE8)
        let skyBottom = isDark ? Color(hex: 0x15140F) : Color(hex: 0xF0EAD8)
        let rockColor = isDark ? Color(hex: 0x4A4238) : Color(hex: 0x8A7A68)
        let rockEdge = isDark ? Color(hex: 0x5C5346) : Color(hex: 0x6E6152)
        let ropeColor = isDark ? Color(hex: 0xD8D2C2) : Color(hex: 0x3A3A38)
        let mountainFar = isDark ? Color(hex: 0x2A3540) : Color(hex: 0xA9BCC7)
        let mountainNear = isDark ? Color(hex: 0x33402E) : Color(hex: 0xB7C4B0)
        // Capturado AQUÍ (contexto MainActor): el closure del Canvas es
        // nonisolated y no puede tocar el VM directamente.
        let overhangGrowth = vm.overhangGrowthPerM
        let engineState = vm.engineState

        ZStack {
            Canvas { context, size in
                guard let state = engineState else { return }
                let w = size.width
                let h = size.height
                let pxPerMeterY = 70.0
                let climberScreenY = h * 0.46
                let wallBaseX = w * 0.38
                let maxLeanPx = w * 0.34

                // Cielo con gradiente.
                context.fill(Path(CGRect(origin: .zero, size: size)),
                             with: .linearGradient(
                                Gradient(colors: [skyTop, skyBottom]),
                                startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))

                // Dos capas de montañas con parallax distinto (profundidad).
                func mountains(offsetFactor: Double, baseY: Double, peakY: Double, color: Color) {
                    let parallax = (state.heightM * 60.0 * offsetFactor)
                        .truncatingRemainder(dividingBy: w * 2.0)
                    var path = Path()
                    path.move(to: CGPoint(x: -parallax, y: baseY))
                    path.addLine(to: CGPoint(x: w * 0.15 - parallax, y: peakY))
                    path.addLine(to: CGPoint(x: w * 0.35 - parallax, y: baseY * 0.94))
                    path.addLine(to: CGPoint(x: w * 0.6 - parallax, y: peakY * 0.85))
                    path.addLine(to: CGPoint(x: w * 0.85 - parallax, y: baseY * 0.96))
                    path.addLine(to: CGPoint(x: w * 1.15 - parallax, y: peakY))
                    path.addLine(to: CGPoint(x: w * 1.4 - parallax, y: baseY * 0.92))
                    path.addLine(to: CGPoint(x: w * 1.7 - parallax, y: peakY * 0.9))
                    path.addLine(to: CGPoint(x: w * 2 - parallax, y: baseY))
                    path.addLine(to: CGPoint(x: w * 2 - parallax, y: h))
                    path.addLine(to: CGPoint(x: -parallax, y: h))
                    path.closeSubpath()
                    context.fill(path, with: .color(color))
                }
                mountains(offsetFactor: 0.4, baseY: h * 0.6, peakY: h * 0.34, color: mountainFar.opacity(0.55))
                mountains(offsetFactor: 1.0, baseY: h * 0.72, peakY: h * 0.46, color: mountainNear.opacity(0.55))

                // ---- La pared. Perfil punto a punto: se tumba de verdad. ----
                func worldHeightAt(_ y: Double) -> Double {
                    state.heightM + (climberScreenY - y) / pxPerMeterY
                }
                func leanFraction(_ heightM: Double) -> Double {
                    let deg = min(heightM * overhangGrowth, 75.0)
                    return min(max(deg / 75.0, 0), 1)
                }
                func wallXAt(_ y: Double) -> Double {
                    wallBaseX + leanFraction(worldHeightAt(y)) * maxLeanPx
                }

                var rockPath = Path()
                rockPath.move(to: CGPoint(x: 0, y: h))
                rockPath.addLine(to: CGPoint(x: 0, y: 0))
                var y = 0.0
                while y <= h { rockPath.addLine(to: CGPoint(x: wallXAt(y), y: y)); y += 8 }
                rockPath.addLine(to: CGPoint(x: wallXAt(h), y: h))
                rockPath.closeSubpath()
                context.fill(rockPath, with: .color(rockColor))

                // Borde de la pared, marcado.
                var edgePath = Path()
                edgePath.move(to: CGPoint(x: wallXAt(0), y: 0))
                var ey = 8.0
                while ey <= h { edgePath.addLine(to: CGPoint(x: wallXAt(ey), y: ey)); ey += 8 }
                context.stroke(edgePath, with: .color(rockEdge), lineWidth: 4)

                // Vetas de la roca.
                var ty = 20.0
                while ty <= h {
                    let x = wallXAt(ty)
                    var vein = Path()
                    vein.move(to: CGPoint(x: x * 0.15, y: ty))
                    vein.addLine(to: CGPoint(x: x - 8, y: ty))
                    context.stroke(vein, with: .color(rockEdge.opacity(0.30)), lineWidth: 1.5)
                    ty += 40
                }

                // Marcas de altura cada 5m sobre la pared.
                var mark = max((Int(worldHeightAt(h)) / 5) * 5, 0)
                while true {
                    let markY = climberScreenY - (Double(mark) - state.heightM) * pxPerMeterY
                    if markY < -20 { break }
                    if markY >= 0 && markY <= h && mark > 0 {
                        let x = wallXAt(markY)
                        var line = Path()
                        line.move(to: CGPoint(x: x - 26, y: markY))
                        line.addLine(to: CGPoint(x: x - 6, y: markY))
                        context.stroke(line, with: .color(rockEdge.opacity(0.8)), lineWidth: 3)
                    }
                    mark += 5
                }

                // ---- Muñeco + cuerda (péndulo real al rapelar). ----
                let wallXNow = wallBaseX + leanFraction(state.heightM) * maxLeanPx
                let swinging = state.phase.name == "RAPPEL"
                let ropeLenPx = (state.ropePaidOutM + 1.2) * pxPerMeterY
                let anchorX = wallXNow
                let anchorY = climberScreenY - ropeLenPx * 0.9
                let climberX = swinging ? anchorX + sin(state.swingAngleRad) * ropeLenPx : wallXNow + 6
                let climberY = swinging ? anchorY + cos(state.swingAngleRad) * ropeLenPx : climberScreenY

                // Anclaje (chapa) + cuerda.
                context.fill(Path(ellipseIn: CGRect(x: anchorX - 7, y: anchorY - 7, width: 14, height: 14)),
                             with: .color(rockEdge))
                context.fill(Path(ellipseIn: CGRect(x: anchorX - 3, y: anchorY - 3, width: 6, height: 6)),
                             with: .color(skyTop))
                var rope = Path()
                rope.move(to: CGPoint(x: anchorX, y: anchorY))
                rope.addLine(to: CGPoint(x: climberX, y: climberY - 14))
                context.stroke(rope, with: .color(ropeColor), style: StrokeStyle(lineWidth: 3.5, lineCap: .round))

                // Muñeco: cabeza, tronco, brazos y piernas según fase.
                let bodyC = Cumbre.terra
                func limb(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, width: Double) {
                    var p = Path()
                    p.move(to: CGPoint(x: x1, y: y1))
                    p.addLine(to: CGPoint(x: x2, y: y2))
                    context.stroke(p, with: .color(bodyC), style: StrokeStyle(lineWidth: width, lineCap: .round))
                }
                context.fill(Path(ellipseIn: CGRect(x: climberX - 13, y: climberY - 35, width: 26, height: 26)),
                             with: .color(bodyC))
                limb(climberX, climberY - 9, climberX, climberY + 20, width: 7)
                if swinging {
                    limb(climberX, climberY - 5, climberX - 6, climberY - 20, width: 5)
                    limb(climberX, climberY - 5, climberX + 6, climberY - 20, width: 5)
                    limb(climberX, climberY + 20, climberX - 16, climberY + 26, width: 5)
                    limb(climberX, climberY + 20, climberX - 12, climberY + 34, width: 5)
                } else {
                    limb(climberX, climberY - 4, climberX - 17, climberY - 16, width: 5)
                    limb(climberX, climberY + 2, climberX - 15, climberY + 12, width: 5)
                    limb(climberX, climberY + 20, climberX - 14, climberY + 30, width: 5)
                    limb(climberX, climberY + 20, climberX + 8, climberY + 34, width: 5)
                }
            }
            .ignoresSafeArea()

            // ---- HUD compacto arriba ----
            VStack {
                HStack(spacing: 6) {
                    hudChip("ALTURA", String(format: "%.1f m", vm.engineState?.heightM ?? 0))
                    hudChip("MEJOR", String(format: "%.1f m", vm.engineState?.bestHeightM ?? 0))
                    hudChip("DESPLOME", "\(Int(vm.engineState?.wallOverhangDeg ?? 0))°")
                    Spacer()
                    Button {
                        vm.backToSetup()
                    } label: {
                        Image(systemName: "xmark")
                            .foregroundStyle(Cumbre.ink)
                            .padding(10)
                            .background(Cumbre.bg.opacity(0.85))
                            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                    }
                }
                .padding(.horizontal, 12).padding(.top, 4)
                Spacer()
                // ---- Barra de esfuerzo abajo, grande y con etiquetas ----
                let pct = vm.currentPct
                let required = vm.engineState?.requiredPct ?? 0
                VStack(spacing: 4) {
                    HStack {
                        Text("TU FUERZA: \(Int(pct))%")
                            .eyebrow(pct >= required ? Cumbre.terra : Cumbre.bad)
                        Spacer()
                        Text("NECESITAS: \(Int(required))%").eyebrow(Cumbre.ink)
                    }
                    effortBar(pct: pct, requiredPct: required)
                }
                .padding(16)
            }

            // ---- Game over ----
            if vm.engineState?.phase.name == "GAME_OVER" {
                Color.black.opacity(0.55).ignoresSafeArea()
                VStack(spacing: 8) {
                    Text("¡Rapelado!").font(Cumbre.serif(24, .bold)).foregroundStyle(Cumbre.ink)
                    HStack(alignment: .bottom, spacing: 2) {
                        Text(String(format: "%.1f", vm.engineState?.bestHeightM ?? 0))
                            .font(Cumbre.mono(52, .bold)).foregroundStyle(Cumbre.terra)
                        Text("m").font(Cumbre.mono(18)).foregroundStyle(Cumbre.ink3)
                            .padding(.bottom, 10)
                    }
                    Text("Altura máxima · \(difficultyLabel(vm.difficulty))")
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    Button { vm.retry() } label: {
                        Text("VOLVER A INTENTAR").font(.system(size: 15, weight: .semibold))
                            .frame(maxWidth: .infinity).padding(.vertical, 14)
                            .background(Cumbre.terra).foregroundStyle(.white)
                    }
                    .padding(.top, 8)
                    Button { vm.backToSetup() } label: {
                        Text("CAMBIAR AJUSTES").font(.system(size: 15, weight: .semibold))
                            .frame(maxWidth: .infinity).padding(.vertical, 14)
                            .background(Cumbre.paper).foregroundStyle(Cumbre.ink)
                            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                    }
                }
                .padding(20)
                .background(Cumbre.bg)
                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                .padding(24)
            }
        }
    }

    private func hudChip(_ label: String, _ value: String) -> some View {
        VStack(spacing: 0) {
            Text(label).eyebrow()
            Text(value).font(Cumbre.mono(14, .bold)).foregroundStyle(Cumbre.ink)
        }
        .padding(.horizontal, 12).padding(.vertical, 4)
        .background(Cumbre.bg.opacity(0.85))
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }

    private func effortBar(pct: Double, requiredPct: Double) -> some View {
        Canvas { context, size in
            let fillFrac = min(max(pct / 100.0, 0), 1)
            let fillColor = pct >= requiredPct ? Cumbre.terra : Cumbre.bad
            context.fill(Path(CGRect(x: 0, y: 0, width: size.width * fillFrac, height: size.height)),
                         with: .color(fillColor))
            // Marcador del % mínimo exigido ahora mismo por la pared.
            let reqFrac = min(max(requiredPct / 100.0, 0), 1)
            let markerX = size.width * reqFrac
            var marker = Path()
            marker.move(to: CGPoint(x: markerX, y: 0))
            marker.addLine(to: CGPoint(x: markerX, y: size.height))
            context.stroke(marker, with: .color(Cumbre.ink), lineWidth: 4)
        }
        .frame(height: 22)
        .background(Cumbre.bg.opacity(0.85))
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }

    private func difficultyLabel(_ d: String) -> String {
        switch d {
        case "FACIL": return "Fácil"
        case "DIFICIL": return "Difícil"
        default: return "Medio"
        }
    }
}
