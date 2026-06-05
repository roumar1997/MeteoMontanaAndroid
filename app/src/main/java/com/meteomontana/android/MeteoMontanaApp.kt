package com.meteomontana.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre

/**
 * Entry point de la aplicación.
 * @HiltAndroidApp inicializa el contenedor de inyección de dependencias
 * de Hilt para toda la app.
 */
@HiltAndroidApp
class MeteoMontanaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicializa MapLibre antes de que cualquier MapView pueda inflarse.
        // Sin API key porque usamos tiles raster propios (Esri / OpenTopoMap).
        MapLibre.getInstance(this)
    }
}
