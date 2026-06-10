package com.meteomontana.android

import android.app.Application
import com.meteomontana.android.data.outbox.OutboxFlusher
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre
import javax.inject.Inject

@HiltAndroidApp
class MeteoMontanaApp : Application() {

    @Inject lateinit var outboxFlusher: OutboxFlusher

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        outboxFlusher.start()
    }
}
