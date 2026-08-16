package com.meteomontana.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.meteomontana.android.data.outbox.OutboxFlusher
import com.meteomontana.android.data.photos.CacheFotosOffline
import com.meteomontana.android.data.saved.SavedSchoolsSync
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class MeteoMontanaApp : Application(), ImageLoaderFactory {

    @Inject lateinit var outboxFlusher: OutboxFlusher
    @Inject lateinit var savedSchoolsSync: SavedSchoolsSync
    @Inject lateinit var cacheFotos: CacheFotosOffline

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        outboxFlusher.start()
        savedSchoolsSync.start()
    }

    /**
     * Cargador de imágenes con UN interceptor: si la foto que se pide ya está
     * descargada para ver la escuela sin cobertura, se sirve el fichero local.
     *
     * Se hace AQUÍ y no en cada pantalla a propósito: así vale para todas las
     * fotos de la app (piedras, caras, mini-fichas) sin tocar ni un composable,
     * y nadie puede olvidarse de usarlo. Si la foto no está guardada, la
     * petición sigue su curso normal por red.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add { chain ->
                    val url = chain.request.data as? String
                    val local = url?.let { cacheFotos.rutaLocal(it) }
                    if (local == null) {
                        chain.proceed(chain.request)
                    } else {
                        chain.proceed(
                            chain.request.newBuilder().data(File(local)).build()
                        )
                    }
                }
            }
            .build()
}
