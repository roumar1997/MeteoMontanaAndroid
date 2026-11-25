package com.meteomontana.android.data.local

import android.content.Context

/**
 * Guarda el ETag del catálogo de escuelas (GET /api/schools). Se manda como
 * If-None-Match en el siguiente refresh: si el backend responde 304, la lista
 * cacheada en SQLDelight sigue vigente y nos ahorramos la descarga.
 */
class CatalogEtagStore(context: Context) {

    private val prefs = context.getSharedPreferences("catalog_meta", Context.MODE_PRIVATE)

    fun get(): String? = prefs.getString(KEY_ETAG, null)

    fun set(etag: String?) {
        prefs.edit().putString(KEY_ETAG, etag).apply()
    }

    private companion object {
        const val KEY_ETAG = "schools_etag"
    }
}
