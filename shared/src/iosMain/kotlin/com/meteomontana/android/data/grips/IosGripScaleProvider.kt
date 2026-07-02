package com.meteomontana.android.data.grips

import com.meteomontana.android.domain.model.GripReading
import com.meteomontana.android.domain.model.GripScaleDevice
import com.meteomontana.android.domain.port.GripScaleProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Handle para dejar de escuchar (equivalente a `IosChatListener`). Lo
 *  implementa Swift (guarda internamente qué detener) y Kotlin lo llama en
 *  `awaitClose` cuando el Flow se cancela. */
interface IosGripListener {
    fun remove()
}

/** DTO de nivel superior que Swift construye fácilmente. */
data class IosGripDeviceDto(val id: String, val rssi: Int, val alias: String?)

/**
 * Bridge de la báscula WH-C06 que IMPLEMENTA Swift con CBCentralManager. Solo
 * callbacks (sin `suspend`/`Flow`, que no se implementan bien desde Swift);
 * el lado Kotlin ([IosGripScaleProvider]) los envuelve en `Flow` y los mapea
 * al port [GripScaleProvider].
 *
 * DEBE seguir el MISMO protocolo que `AndroidGripScaleProvider`: escaneo BLE
 * (sin GATT/emparejar), identificando la báscula por el manufacturer ID 256
 * y calculando el peso como `(byte[10]*256 + byte[11]) / 100.0` — ver
 * GRIPS_DESIGN.md sección 1.
 */
interface IosGripScaleBridge {
    fun hasPermission(): Boolean
    fun isBluetoothEnabled(): Boolean
    fun scanDevices(onChange: (List<IosGripDeviceDto>) -> Unit): IosGripListener
    fun stopScan()
    // timestampMs como Double (no Long): SKIE deja los tipos función con Long
    // "empaquetado" (KotlinLong) en vez de desempaquetarlo a Int64 como hace
    // con un valor de retorno normal, y eso rompe la conformidad del
    // protocolo en Swift.
    fun observeWeight(deviceId: String, onReading: (Double, Double) -> Unit): IosGripListener
    fun disconnect()
    fun getAlias(deviceId: String): String?
    fun setAlias(deviceId: String, alias: String)
}

/** Implementación iOS de [GripScaleProvider]. Equivalente del
 *  `AndroidGripScaleProvider`, pero la parte nativa (CoreBluetooth) vive en
 *  Swift vía [IosGripScaleBridge]. */
class IosGripScaleProvider(
    private val bridge: IosGripScaleBridge,
) : GripScaleProvider {

    override fun hasPermission(): Boolean = bridge.hasPermission()

    override fun isBluetoothEnabled(): Boolean = bridge.isBluetoothEnabled()

    override fun scanDevices(): Flow<List<GripScaleDevice>> = callbackFlow {
        val listener = bridge.scanDevices { dtos -> trySend(dtos.map { it.toModel() }) }
        awaitClose { listener.remove() }
    }

    override fun stopScan() = bridge.stopScan()

    override fun observeWeight(deviceId: String): Flow<GripReading> = callbackFlow {
        val listener = bridge.observeWeight(deviceId) { kg, timestampMs -> trySend(GripReading(kg, timestampMs.toLong())) }
        awaitClose { listener.remove() }
    }

    override fun disconnect() = bridge.disconnect()

    override fun getAlias(deviceId: String): String? = bridge.getAlias(deviceId)

    override fun setAlias(deviceId: String, alias: String) = bridge.setAlias(deviceId, alias)

    private fun IosGripDeviceDto.toModel() = GripScaleDevice(id, rssi, alias)
}
