package com.meteomontana.android.data.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * La "huella" de una foto, para decir si dos fotos enseñan lo mismo. Es el equivalente en Android de lo que iOS hace
 * con Vision (`VNGenerateImageFeaturePrint`): una red pequeña (MobileNet V3, 4 MB, dentro del APK) convierte cada
 * foto en un vector y dos fotos se parecen cuanto más apuntan esos vectores en la misma dirección.
 *
 * Todo ocurre en el móvil: ni la foto del usuario ni las de las piedras salen a ningún servidor.
 *
 * El modelo no es seguro entre hilos, así que las pasadas van de una en una ([lock]); cada una tarda decenas de
 * milisegundos, y lo lento de buscar una piedra es descargar las fotos, no esto.
 */
@Singleton
class StoneImageEmbedder @Inject constructor(@ApplicationContext private val context: Context) {

    private val lock = Mutex()
    private var embedder: ImageEmbedder? = null

    /** Huella (normalizada a longitud 1) de la imagen, o null si el modelo no se pudo cargar o la imagen no vale. */
    suspend fun embed(bitmap: Bitmap): FloatArray? = withContext(Dispatchers.Default) {
        lock.withLock {
            runCatching {
                val model = embedder ?: create().also { embedder = it }
                val vector = model.embed(BitmapImageBuilder(bitmap).build())
                    .embeddingResult().embeddings().firstOrNull()?.floatEmbedding()
                vector?.let(::normalized)
            }.getOrNull()
        }
    }

    private fun create(): ImageEmbedder = ImageEmbedder.createFromOptions(
        context,
        ImageEmbedder.ImageEmbedderOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
            .setRunningMode(RunningMode.IMAGE)
            .setL2Normalize(true)
            .build()
    )

    companion object {
        private const val MODEL_ASSET = "stone_embedder.tflite"

        private fun normalized(v: FloatArray): FloatArray {
            val norm = sqrt(v.fold(0.0) { acc, x -> acc + x * x }).toFloat()
            return if (norm > 0f) FloatArray(v.size) { v[it] / norm } else v
        }

        /**
         * Parecido de 0 (nada que ver) a 1 (la misma piedra) entre dos huellas. El coseno de dos fotos cualquiera de
         * roca ya sale en torno a 0,3-0,5; solo por encima de eso empieza a haber parecido real, así que se
         * reescala [COSINE_FLOOR]..1 a 0..1 para que el umbral de «no se parece a ninguna» signifique algo.
         * Los dos números son de partida y se afinan con fotos reales.
         */
        fun similarity(a: FloatArray, b: FloatArray): Double? {
            if (a.size != b.size || a.isEmpty()) return null
            var dot = 0.0
            for (i in a.indices) dot += a[i] * b[i]
            return ((dot - COSINE_FLOOR) / (1.0 - COSINE_FLOOR)).coerceIn(0.0, 1.0)
        }

        const val COSINE_FLOOR = 0.30
    }
}
