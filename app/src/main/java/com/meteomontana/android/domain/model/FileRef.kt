package com.meteomontana.android.domain.model

/**
 * Referencia a un fichero local. Sustituye android.net.Uri en el dominio.
 * En Android se construye con FileRef(uri.toString()); la capa de infraestructura
 * lo convierte de vuelta a Uri para leer bytes con ContentResolver.
 * Puro Kotlin — sin imports Android. Listo para commonMain en Fase 2.
 */
@JvmInline
value class FileRef(val path: String)
