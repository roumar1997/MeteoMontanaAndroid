package com.meteomontana.android.data.map

import android.content.Context
import org.maplibre.android.MapLibre

/**
 * Arranque perezoso de MapLibre: ya no se hace en `Application.onCreate` (frenaba el arranque en frío).
 * Se llama en un hueco libre del hilo principal tras arrancar y, por si acaso, justo antes de crear cada MapView.
 */
object MapLibreInit {
    @Volatile private var done = false

    fun ensure(context: Context) {
        if (done) return
        synchronized(this) {
            if (done) return
            MapLibre.getInstance(context.applicationContext)
            done = true
        }
    }
}
