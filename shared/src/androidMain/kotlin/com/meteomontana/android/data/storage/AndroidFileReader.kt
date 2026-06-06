package com.meteomontana.android.data.storage

import android.content.Context
import android.net.Uri
import com.meteomontana.android.domain.model.FileRef
import com.meteomontana.android.domain.port.FileReader

class AndroidFileReader(
    private val context: Context
) : FileReader {
    override suspend fun readBytes(ref: FileRef): ByteArray =
        context.contentResolver.openInputStream(Uri.parse(ref.path))?.use { it.readBytes() }
            ?: error("No se pudo leer el fichero: ${ref.path}")
}
