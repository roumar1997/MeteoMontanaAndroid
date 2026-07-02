import CoreBluetooth
import Foundation
import Shared

/// Handle para dejar de escuchar (equivalente a `ChatListenerHandle`). El
/// lado Kotlin lo llama en `awaitClose` cuando el Flow se cancela.
final class GripScanHandle: NSObject, IosGripListener {
    private let onRemove: () -> Void
    init(_ onRemove: @escaping () -> Void) { self.onRemove = onRemove }
    func remove() { onRemove() }
}

/// Implementación Swift del bridge `IosGripScaleBridge` (Kotlin iosMain) con
/// CBCentralManager. MISMO protocolo que `AndroidGripScaleProvider`: escaneo
/// BLE de anuncios (sin GATT/emparejar), identificando la báscula WH-C06 por
/// el manufacturer ID 256 y calculando el peso como
/// `(byte[10]*256 + byte[11]) / 100.0` — ver GRIPS_DESIGN.md sección 1.
///
/// ⚠️ No probado contra hardware real. `CBPeripheral.identifier` es un UUID
/// estable por dispositivo iOS (no la MAC — Apple no la expone), válido
/// igualmente como id local para "bloquear" una báscula concreta.
final class GripScaleBridge: NSObject, IosGripScaleBridge, CBCentralManagerDelegate {

    private static let manufacturerId: UInt16 = 256

    private lazy var central = CBCentralManager(delegate: self, queue: nil)
    private var state: CBManagerState = .unknown

    // deviceId (peripheral.identifier.uuidString) -> (rssi, peripheral)
    private var found: [String: Int] = [:]
    private var onDevicesChange: (([IosGripDeviceDto]) -> Void)?
    private var onWeightReading: ((Double, Double) -> Void)?
    private var observingDeviceId: String?

    func hasPermission() -> Bool {
        if #available(iOS 13.1, *) {
            switch CBManager.authorization {
            case .allowedAlways: return true
            default: return false
            }
        }
        return true
    }

    func isBluetoothEnabled() -> Bool { state == .poweredOn }

    // MARK: - Escanear básculas cercanas

    func scanDevices(onChange: @escaping ([IosGripDeviceDto]) -> Void) -> IosGripListener {
        found.removeAll()
        onDevicesChange = onChange
        startScanIfReady()
        return GripScanHandle { [weak self] in
            self?.onDevicesChange = nil
            self?.stopScanIfIdle()
        }
    }

    func stopScan() {
        onDevicesChange = nil
        stopScanIfIdle()
    }

    // MARK: - "Bloquear" una báscula y escuchar su peso

    func observeWeight(deviceId: String, onReading: @escaping (Double, Double) -> Void) -> IosGripListener {
        observingDeviceId = deviceId
        onWeightReading = onReading
        startScanIfReady()
        return GripScanHandle { [weak self] in
            self?.onWeightReading = nil
            self?.observingDeviceId = nil
            self?.stopScanIfIdle()
        }
    }

    func disconnect() {
        onWeightReading = nil
        observingDeviceId = nil
        stopScanIfIdle()
    }

    // MARK: - Alias local (UserDefaults, no sincroniza con el backend)

    private let aliasPrefix = "grip_scale_alias_"

    func getAlias(deviceId: String) -> String? {
        UserDefaults.standard.string(forKey: aliasPrefix + deviceId)
    }

    func setAlias(deviceId: String, alias: String) {
        UserDefaults.standard.set(alias, forKey: aliasPrefix + deviceId)
    }

    // MARK: - Internals

    private func startScanIfReady() {
        guard state == .poweredOn else { return }
        guard !central.isScanning else { return }
        central.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: true])
    }

    private func stopScanIfIdle() {
        if onDevicesChange == nil && onWeightReading == nil {
            central.stopScan()
        }
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        state = central.state
        if state == .poweredOn { startScanIfReady() }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral,
                         advertisementData: [String: Any], rssi RSSI: NSNumber) {
        guard let mfgData = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data,
              mfgData.count >= 2 else { return }
        // Los 2 primeros bytes son el manufacturer ID en little-endian (igual
        // que Android separa el ID del payload en getManufacturerSpecificData).
        let id = UInt16(mfgData[0]) | (UInt16(mfgData[1]) << 8)
        guard id == Self.manufacturerId else { return }
        let payload = mfgData.suffix(from: 2) // resto de bytes, igual que Android

        let deviceId = peripheral.identifier.uuidString

        if onDevicesChange != nil {
            found[deviceId] = RSSI.intValue
            let list = found.map { (id, rssi) in
                IosGripDeviceDto(id: id, rssi: Int32(rssi), alias: getAlias(deviceId: id))
            }
            onDevicesChange?(list)
        }

        if observingDeviceId == deviceId, onWeightReading != nil, payload.count >= 12 {
            let bytes = [UInt8](payload)
            let kg = Double(Int(bytes[10]) * 256 + Int(bytes[11])) / 100.0
            let timestampMs = Date().timeIntervalSince1970 * 1000
            onWeightReading?(kg, timestampMs)
        }
    }
}
