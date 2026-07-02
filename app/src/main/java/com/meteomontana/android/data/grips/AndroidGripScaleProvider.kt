package com.meteomontana.android.data.grips

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.meteomontana.android.domain.model.GripReading
import com.meteomontana.android.domain.model.GripScaleDevice
import com.meteomontana.android.domain.port.GripScaleProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/**
 * Implementación Android de [GripScaleProvider] para la báscula WH-C06.
 * NO usa GATT/emparejamiento: solo escaneo de anuncios BLE. Protocolo
 * verificado leyendo el código de TheLastKiwi/Dyna (ver GRIPS_DESIGN.md
 * sección 1):
 *
 *   peso_kg = (unsigned(byte[10])*256 + unsigned(byte[11])) / 100.0
 *
 * Identificamos el dispositivo por el **manufacturer ID 256** en vez de
 * filtrar por nombre exacto ("IF_B7") en el `ScanFilter` nativo — es más
 * robusto (algunas unidades/firmwares pueden anunciar el nombre distinto,
 * pero el fabricante siempre manda ese ID) y evita escaneos "vacíos" si el
 * filtro por nombre no casa exacto.
 *
 * ⚠️ No probado contra hardware real hasta esta sesión — si la báscula
 * sigue sin aparecer, comprobar en logcat qué nombre/manufacturer real
 * anuncia (puede que el ID o el offset de bytes necesite un ajuste fino).
 */
class AndroidGripScaleProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : GripScaleProvider {

    private companion object {
        const val MANUFACTURER_ID = 256
        const val PREFS_NAME = "grip_scale_aliases"
    }

    private val bluetoothManager by lazy {
        runCatching { context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }.getOrNull()
    }
    // adapter.isEnabled y bluetoothLeScanner pueden lanzar SecurityException
    // en según qué versión/fabricante si falta algún permiso de Bluetooth —
    // TODO lo que toca esta propiedad va envuelto en runCatching.
    private val scanner: BluetoothLeScanner?
        get() = runCatching { bluetoothManager?.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner }.getOrNull()

    private var activeCallback: ScanCallback? = null

    override fun hasPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun isBluetoothEnabled(): Boolean =
        runCatching { bluetoothManager?.adapter?.isEnabled == true }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    override fun scanDevices(): Flow<List<GripScaleDevice>> = callbackFlow {
        if (!hasPermission() || !isBluetoothEnabled()) { close(); return@callbackFlow }
        val found = LinkedHashMap<String, Int>() // deviceId (MAC) -> rssi
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // Filtro por manufacturer ID en vez de nombre exacto: más robusto.
                if (result.scanRecord?.getManufacturerSpecificData(MANUFACTURER_ID) == null) return
                found[result.device.address] = result.rssi
                trySend(found.map { (id, rssi) -> GripScaleDevice(id, rssi, getAlias(id)) })
            }
            override fun onScanFailed(errorCode: Int) { close(IllegalStateException("BLE scan failed: $errorCode")) }
        }
        activeCallback = callback
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching { scanner?.startScan(null, settings, callback) }
            .onFailure { close(it) }
        awaitClose { stopScan() }
    }

    @SuppressLint("MissingPermission")
    override fun stopScan() {
        runCatching { activeCallback?.let { scanner?.stopScan(it) } }
        activeCallback = null
    }

    @SuppressLint("MissingPermission")
    override fun observeWeight(deviceId: String): Flow<GripReading> = callbackFlow {
        if (!hasPermission() || !isBluetoothEnabled()) { close(); return@callbackFlow }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (result.device.address != deviceId) return
                val data = result.scanRecord?.getManufacturerSpecificData(MANUFACTURER_ID) ?: return
                if (data.size < 12) return
                val kg = (unsignedByte(data[10]) * 256 + unsignedByte(data[11])) / 100.0
                trySend(GripReading(kg, System.currentTimeMillis()))
            }
            override fun onScanFailed(errorCode: Int) { close(IllegalStateException("BLE scan failed: $errorCode")) }
        }
        activeCallback = callback
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching { scanner?.startScan(null, settings, callback) }
            .onFailure { close(it) }
        awaitClose { disconnect() }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        runCatching { activeCallback?.let { scanner?.stopScan(it) } }
        activeCallback = null
    }

    override fun getAlias(deviceId: String): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(deviceId, null)

    override fun setAlias(deviceId: String, alias: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(deviceId, alias).apply()
    }

    private fun unsignedByte(b: Byte): Int = b.toInt() and 0xFF
}
