package com.meteomontana.android.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.TitleEffectCatalog
import com.meteomontana.android.domain.model.TitleEffectSpec
import com.meteomontana.android.domain.usecase.titleeffects.ObserveTitleEffectsUseCase
import com.meteomontana.android.domain.usecase.titleeffects.RefreshTitleEffectsUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Sprites empaquetados en la app (respaldo sin red; los efectos integrados solo tienen estos). */
private val BUILT_IN_SPRITES = mapOf(
    "effect_fairy1" to R.drawable.effect_fairy1, "effect_fairy2" to R.drawable.effect_fairy2,
    "effect_fairy3" to R.drawable.effect_fairy3, "effect_fairy4" to R.drawable.effect_fairy4,
    "effect_dragon1" to R.drawable.effect_dragon1, "effect_dragon2" to R.drawable.effect_dragon2,
    "effect_van1" to R.drawable.effect_van1, "effect_van2" to R.drawable.effect_van2,
)

/** 30 fps como mucho: es un adorno, no merece el doble de batería. */
private const val FRAME_MS = 33L

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TitleEffectsEntryPoint {
    fun observeTitleEffects(): ObserveTitleEffectsUseCase
    fun refreshTitleEffects(): RefreshTitleEffectsUseCase
}

/** Catálogo del servidor (lo guardado en disco al instante; se renueva detrás, con límite de frecuencia). */
@Composable
fun rememberTitleEffectCatalog(): TitleEffectCatalog {
    val context = LocalContext.current.applicationContext
    val entry = remember { EntryPointAccessors.fromApplication(context, TitleEffectsEntryPoint::class.java) }
    LaunchedEffect(Unit) { entry.refreshTitleEffects()() }
    val catalog by entry.observeTitleEffects()().collectAsState()
    return catalog
}

/**
 * Una imagen por sprite: la empaquetada al momento (si la hay) y, si tiene URL, la del CDN en cuanto llega (Coil la
 * guarda en disco). null = aún no hay nada que pintar para esa figura.
 */
@Composable
internal fun rememberEffectSprites(spec: TitleEffectSpec): State<List<ImageBitmap?>> {
    val context = LocalContext.current
    val state = remember(spec) {
        mutableStateOf(spec.sprites.map { s ->
            s.asset?.let { BUILT_IN_SPRITES[it] }?.let { ImageBitmap.imageResource(context.resources, it) }
        })
    }
    LaunchedEffect(spec) {
        spec.sprites.forEachIndexed { i, s ->
            val url = s.url ?: return@forEachIndexed
            val bmp = loadRemote(context, url) ?: return@forEachIndexed
            state.value = state.value.toMutableList().also { it[i] = bmp }
        }
    }
    return state
}

private suspend fun loadRemote(context: Context, url: String): ImageBitmap? = runCatching {
    val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
    (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()?.asImageBitmap()
}.getOrNull()

/**
 * Segundos desde que empezó la animación. Solo corre si [animated], la app está en primer plano y el sistema no tiene
 * las animaciones quitadas; se para sola al salir de pantalla (deja la composición). Se lee en la fase de DIBUJO, así
 * que no recompone nada.
 */
@Composable
internal fun rememberEffectClock(animated: Boolean): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    if (animated && !reduceMotion) {
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val offset = time.floatValue
                val start = withFrameNanos { it }
                while (isActive) {
                    withFrameNanos { time.floatValue = offset + (it - start) / 1_000_000_000f }
                    delay(FRAME_MS)
                }
            }
        }
    }
    return time
}
