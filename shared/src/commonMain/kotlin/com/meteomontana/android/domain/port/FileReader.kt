package com.meteomontana.android.domain.port

import com.meteomontana.android.domain.model.FileRef

/**
 * Puerto para leer bytes de un fichero local.
 * La implementación Android usa ContentResolver. En iOS usará NSData.
 * Puro Kotlin — sin imports Android. Listo para commonMain en Fase 2.
 */
interface FileReader {
    suspend fun readBytes(ref: FileRef): ByteArray
}
