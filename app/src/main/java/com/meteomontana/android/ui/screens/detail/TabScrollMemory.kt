package com.meteomontana.android.ui.screens.detail

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Cada pestaña de la ficha recuerda su propio scroll (Álvaro, 2026-11-13): una pestaña que no se había
 * visitado empieza arriba, al volver a una visitada sigue donde estaba, y tocar la pestaña ya activa sube arriba
 * del todo (animado). Comparten UN solo [ScrollState]; aquí se guarda y se repone la posición de cada una.
 * Mismo comportamiento que `SchoolTabScroll` en iOS.
 */
@Composable
fun rememberTabScrollMemory(tab: SchoolTab, scroll: ScrollState, onSelect: (SchoolTab) -> Unit): (SchoolTab) -> Unit {
    val positions = rememberSaveable { mutableStateOf(IntArray(SchoolTab.entries.size)) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(tab) {
        val target = positions.value[tab.ordinal]
        if (target > 0) {
            // El contenido de la pestaña tarda en medirse: se espera a que haya recorrido suficiente.
            withTimeoutOrNull(1500) { snapshotFlow { scroll.maxValue }.first { it >= target } }
        }
        scroll.scrollTo(target)
        snapshotFlow { scroll.value }.collect { positions.value[tab.ordinal] = it }
    }
    return remember(tab, scroll) {
        { picked: SchoolTab ->
            if (picked == tab) scope.launch { scroll.animateScrollTo(0) } else onSelect(picked)
        }
    }
}
