import SwiftUI
import Shared

@MainActor
final class GripConnectViewModel: ObservableObject {
    @Published var devices: [GripScaleDevice] = []
    @Published var scanning = false
    @Published var bluetoothOn = false

    private var scanTask: Task<Void, Never>?
    private let provider = AppDependencies.shared.container.gripScaleProvider

    var hasPermission: Bool { provider?.hasPermission() ?? false }

    func refreshBluetoothState() {
        bluetoothOn = provider?.isBluetoothEnabled() ?? false
    }

    func startScan() {
        guard let provider, bluetoothOn, !scanning else { return }
        scanning = true
        scanTask = Task {
            for await list in provider.scanDevices() {
                devices = list
            }
            scanning = false
        }
    }

    func stopScan() {
        scanTask?.cancel()
        scanTask = nil
        provider?.stopScan()
        scanning = false
    }

    func connect(_ deviceId: String) {
        let alias = provider?.getAlias(deviceId: deviceId)
        GripScaleSession.shared.set(deviceId: deviceId, alias: alias)
        stopScan()
    }

    func rename(_ deviceId: String, alias: String) {
        provider?.setAlias(deviceId: deviceId, alias: alias)
        GripScaleSession.shared.updateAlias(deviceId: deviceId, alias: alias)
        if let idx = devices.firstIndex(where: { $0.id == deviceId }) {
            devices[idx] = GripScaleDevice(id: deviceId, rssi: devices[idx].rssi, alias: alias)
        }
    }
}

struct GripConnectView: View {
    @StateObject private var vm = GripConnectViewModel()
    @State private var renameTarget: GripScaleDevice?
    @State private var renameText = ""
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        Group {
            if !vm.bluetoothOn {
                VStack(spacing: 12) {
                    Text("El Bluetooth está apagado")
                        .font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                    Text("Actívalo en Ajustes y vuelve aquí.")
                        .font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if vm.devices.isEmpty {
                VStack(spacing: 12) {
                    if vm.scanning { ProgressView() }
                    Text(vm.scanning ? "Buscando básculas WH-C06 cercanas…" : "Enciende la báscula y espera unos segundos")
                        .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(24)
            } else {
                List(vm.devices, id: \.id) { device in
                    Button {
                        vm.connect(device.id)
                    } label: {
                        HStack {
                            Image(systemName: "wave.3.right").foregroundStyle(Cumbre.terra)
                            VStack(alignment: .leading) {
                                Text(device.alias ?? "Báscula WH-C06").foregroundStyle(Cumbre.ink)
                                Text("\(device.id) · señal \(device.rssi) dBm")
                                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                            }
                            Spacer()
                            Button {
                                renameText = device.alias ?? ""
                                renameTarget = device
                            } label: {
                                Image(systemName: "pencil").foregroundStyle(Cumbre.ink3)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .buttonStyle(.plain)
                }
                .listStyle(.plain)
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Conectar báscula")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            vm.refreshBluetoothState()
            if vm.bluetoothOn { vm.startScan() }
        }
        .onDisappear { vm.stopScan() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                vm.refreshBluetoothState()
                if vm.bluetoothOn { vm.startScan() }
            }
        }
        .alert("Nombre de la báscula", isPresented: Binding(get: { renameTarget != nil }, set: { if !$0 { renameTarget = nil } })) {
            TextField("p.ej. Mi báscula azul", text: $renameText)
            Button("GUARDAR") {
                if let target = renameTarget { vm.rename(target.id, alias: renameText) }
                renameTarget = nil
            }
            Button(NSLocalizedString("common_cancel", comment: ""), role: .cancel) { renameTarget = nil }
        }
    }
}
