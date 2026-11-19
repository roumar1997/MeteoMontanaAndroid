package com.meteomontana.android.ui.screens.gyms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymMapItem
import com.meteomontana.android.ui.components.CumbreChip
import com.meteomontana.android.ui.components.CumbreSearchField
import com.meteomontana.android.ui.components.LocalTabBarInset
import com.meteomontana.android.ui.components.dismissKeyboardOnScroll
import com.meteomontana.android.ui.components.rememberKeyboardDismisser
import com.meteomontana.android.ui.screens.chat.openDirections
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing

private const val DEFAULT_LAT = 40.4168
private const val DEFAULT_LON = -3.7038

/** Las acciones de [GymsViewModel] que usa la pantalla, sueltas: las subpantallas reciben funciones, no el ViewModel. */
private class GymsActions(vm: GymsViewModel) {
    val setQuery: (String) -> Unit = vm::setQuery
    val toggleFree: () -> Unit = vm::toggleFree
    val toggleBoulder: () -> Unit = vm::toggleBoulder
    val toggleRope: () -> Unit = vm::toggleRope
    val toggleOpenNow: () -> Unit = vm::toggleOpenNow
    val toggleFavorites: () -> Unit = vm::toggleFavorites
    val setChain: (String?) -> Unit = vm::setChain
    val setMaxDistance: (Double?) -> Unit = vm::setMaxDistance
    val setView: (GymsView) -> Unit = vm::setView
    val refresh: () -> Unit = vm::refresh
    val clearFilters: () -> Unit = vm::clearFilters
    val cluster: (List<Gym>, Double) -> List<GymMapItem> = vm::cluster
}

/**
 * Cuerpo de Escuelas cuando el selector está en «Rocódromos» o «Muros gratis» (sustituye a la lista de escuelas y
 * a su nota del tiempo): buscador, filtros en cápsulas, contador, alternar Mapa/Lista y el contenido.
 */
@Composable
fun GymsLayerScreen(
    userLat: Double?,
    userLon: Double?,
    locationPermission: Boolean,
    onRequestLocation: () -> Unit,
    onOpenGym: (Long) -> Unit,
    modifier: Modifier = Modifier,
    // Mismo dueño que el del selector de Escuelas: es el MISMO ViewModel (la capa elegida se comparte).
    viewModel: GymsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val layer by viewModel.layer.collectAsStateWithLifecycle()
    var mapCenter by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var addWallOpen by remember { mutableStateOf(false) }

    LaunchedEffect(userLat, userLon) { viewModel.setOrigin(userLat, userLon) }
    // Volver de una ficha: el favorito puede haber cambiado allí.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) viewModel.refreshFavorites() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Column(modifier.fillMaxSize()) {
        GymsLayerHeader(remember(viewModel) { GymsActions(viewModel) }, state, isFreeLayer = layer == SchoolsLayer.FREE_WALLS)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            GymsLayerContent(
                remember(viewModel) { GymsActions(viewModel) }, state, userLat, userLon, locationPermission, onRequestLocation, onOpenGym,
                onCenter = { la, lo -> mapCenter = la to lo }, onAddWall = { addWallOpen = true }
            )
        }
    }
    if (addWallOpen) {
        NewGymSheet(
            lat = mapCenter?.first ?: userLat ?: DEFAULT_LAT,
            lon = mapCenter?.second ?: userLon ?: DEFAULT_LON,
            onDismiss = { addWallOpen = false }
        )
    }
}

/** Buscador, cápsulas de filtro y la fila «contador · Mapa | Lista». */
@Composable
private fun GymsLayerHeader(actions: GymsActions, state: GymsUiState, isFreeLayer: Boolean) {
    val closeKeyboard = rememberKeyboardDismisser()
    val filters = state.filters
    Column {
    Box(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)) {
        CumbreSearchField(
            value = filters.query, onValueChange = actions.setQuery,
            placeholder = stringResource(R.string.gym_search_hint), onSearch = { closeKeyboard() }
        )
    }
    GymFilterBar(
        filters = filters, chains = state.chains, showFreeFilter = !isFreeLayer,
        onFree = actions.toggleFree, onBoulder = actions.toggleBoulder, onRope = actions.toggleRope,
        onOpenNow = actions.toggleOpenNow, onFavorites = actions.toggleFavorites,
        onChain = actions.setChain, onDistance = actions.setMaxDistance
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            stringResource(if (isFreeLayer) R.string.gym_count_free else R.string.gym_count_gyms, state.items.size).uppercase(),
            style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)
        )
        CumbreChip(stringResource(R.string.gym_view_map), state.view == GymsView.MAP, { actions.setView(GymsView.MAP) })
        CumbreChip(stringResource(R.string.gym_view_list), state.view == GymsView.LIST, { actions.setView(GymsView.LIST) })
    }
    }
}

/** Carga, error sin copia, lista vacía, mapa con su tarjeta o la lista. */
@Composable
private fun BoxScope.GymsLayerContent(
    actions: GymsActions,
    state: GymsUiState,
    userLat: Double?,
    userLon: Double?,
    locationPermission: Boolean,
    onRequestLocation: () -> Unit,
    onOpenGym: (Long) -> Unit,
    onCenter: (Double, Double) -> Unit,
    onAddWall: () -> Unit
) {
    val mapState = rememberGymsMapState()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    when {
        state.loading && state.all.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
        state.loadFailed && state.all.isEmpty() ->
            Message(stringResource(R.string.gym_load_error), stringResource(R.string.gym_retry), actions.refresh)
        state.view == GymsView.MAP -> {
            GymsMap(
                gyms = state.items.map { it.gym }, cluster = actions.cluster, userLat = userLat, userLon = userLon,
                selectedId = selectedId, onSelect = { selectedId = it }, mapState = mapState, onCenterChange = onCenter,
                onRequestLocation = onRequestLocation, locationPermission = locationPermission, modifier = Modifier.fillMaxSize()
            )
            val selected = state.items.firstOrNull { it.gym.id == selectedId }
            Column(
                Modifier.align(Alignment.BottomCenter).padding(horizontal = Spacing.md)
                    .padding(bottom = LocalTabBarInset.current + Spacing.sm)
            ) {
                if (selected == null) AddWallPill(onAddWall)
                else SelectedGymCard(selected, { selectedId = null }, onOpenGym)
            }
        }
        state.items.isEmpty() ->
            Message(stringResource(R.string.gym_empty), stringResource(R.string.gym_clear_filters), actions.clearFilters)
        else -> LazyColumn(
            Modifier.fillMaxSize().dismissKeyboardOnScroll(),
            contentPadding = PaddingValues(bottom = LocalTabBarInset.current + Spacing.lg)
        ) {
            items(state.items, key = { it.gym.id }) { item ->
                GymListRow(item, item.gym.openStatusNow(), item.gym.id in state.favorites, { onOpenGym(item.gym.id) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
            }
            item { Box(Modifier.padding(Spacing.lg)) { AddWallPill(onAddWall) } }
        }
    }
}

@Composable
private fun SelectedGymCard(item: com.meteomontana.android.domain.model.gym.GymListItem, onClose: () -> Unit, onOpenGym: (Long) -> Unit) {
    val ctx = LocalContext.current
    val gym = item.gym
    GymMapCard(
        gym = gym, status = gym.openStatusNow(), distanceText = GymPresentation.distanceText(item.distanceKm),
        onClose = onClose, onDirections = { openDirections(ctx, gym.lat, gym.lon, gym.name) }, onOpen = { onOpenGym(gym.id) }
    )
}

/** «Añadir un muro que falta»: píldora discontinua, la acción secundaria de la capa. */
@Composable
private fun AddWallPill(onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(CircleShape)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant), CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.gym_add_missing), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Message(text: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Box(Modifier.padding(top = Spacing.md)) { CumbreChip(action, true, onAction) }
    }
}
