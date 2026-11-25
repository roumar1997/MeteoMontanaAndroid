package com.meteomontana.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.maps.MapView

/**
 * Conecta un MapView de MapLibre al lifecycle de Compose. El MapView nativo
 * exige onStart/onResume/onPause/onStop/onDestroy explícitos o filtra
 * memoria/contexto. Único punto de verdad para todos los mapas de la app
 * (lista, detalle, admin, dialog fullscreen).
 *
 * @param mapViewRef referencia al MapView creado en el factory del AndroidView
 *   (MutableState porque el factory corre después de la primera composición).
 * @param onDisposed limpieza extra al salir de composición (p. ej. anular la
 *   referencia al MapLibreMap).
 */
@Composable
fun MapViewLifecycleEffect(
    mapViewRef: MutableState<MapView?>,
    onDisposed: () -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mv = mapViewRef.value ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START   -> mv.onStart()
                Lifecycle.Event.ON_RESUME  -> mv.onResume()
                Lifecycle.Event.ON_PAUSE   -> mv.onPause()
                Lifecycle.Event.ON_STOP    -> mv.onStop()
                Lifecycle.Event.ON_DESTROY -> mv.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef.value?.apply { onPause(); onStop(); onDestroy() }
            mapViewRef.value = null
            onDisposed()
        }
    }
}
