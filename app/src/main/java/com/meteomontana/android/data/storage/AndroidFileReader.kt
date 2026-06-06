package com.meteomontana.android.data.storage

import android.content.Context
import android.net.Uri
import com.meteomontana.android.domain.model.FileRef
import com.meteomontana.android.domain.port.FileReader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidFileReader @Inject constructor(
    @ApplicationContext private val context: Context
) : FileReader {
    override suspend fun readBytes(ref: FileRef): ByteArray =
        context.contentResolver.openInputStream(Uri.parse(ref.path))?.use { it.readBytes() }
            ?: error("No se pudo leer el fichero: ${ref.path}")
}
