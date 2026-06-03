package com.meteomontana.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Entry point de la aplicación.
 * @HiltAndroidApp inicializa el contenedor de inyección de dependencias
 * de Hilt para toda la app.
 */
@HiltAndroidApp
class MeteoMontanaApp : Application()
