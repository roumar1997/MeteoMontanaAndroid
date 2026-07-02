package com.meteomontana.android.domain.port

import com.meteomontana.android.domain.model.GripReading
import com.meteomontana.android.domain.model.GripScaleDevice
import kotlinx.coroutines.flow.Flow

/**
 * Báscula-dinamómetro BLE WH-C06 (ver GRIPS_DESIGN.md sección 1). No usa
 * GATT/emparejamiento: solo escaneo de anuncios BLE filtrando por
 * manufacturer data. Android: BluetoothLeScanner. iOS: CBCentralManager
 * (patrón bridge, como LocationProvider/AuthService).
 */
interface GripScaleProvider {

    /** ¿Tenemos permisos de Bluetooth concedidos? */
    fun hasPermission(): Boolean

    /** ¿Está el Bluetooth del móvil encendido? (sin esto el escaneo no
     *  encuentra nada, aunque los permisos estén concedidos). */
    fun isBluetoothEnabled(): Boolean

    /**
     * Escanea básculas WH-C06 cercanas. Emite la lista acumulada (puede
     * haber más de una — gimnasio con varias unidades) cada vez que aparece
     * una nueva o cambia su señal. Para el escaneo con [stopScan] o al
     * cancelar la coroutine que colecta el Flow.
     */
    fun scanDevices(): Flow<List<GripScaleDevice>>

    fun stopScan()

    /**
     * "Bloquea" una báscula concreta (por [deviceId]) y emite sus lecturas
     * de peso en vivo (~8 Hz) mientras esté al alcance. Dejar de colectar
     * el Flow (o llamar a [disconnect]) para dejar de escuchar.
     */
    fun observeWeight(deviceId: String): Flow<GripReading>

    fun disconnect()

    /** Alias local que el usuario le puso a esta báscula (para reconocerla
     *  si hay varias), o null si nunca se ha renombrado. Guardado solo en
     *  este dispositivo (no sincroniza con el backend). */
    fun getAlias(deviceId: String): String?

    fun setAlias(deviceId: String, alias: String)
}
