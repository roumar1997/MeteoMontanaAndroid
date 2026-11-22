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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import com.meteomontana.android.ui.components.CumbreChip
import com.meteomontana.android.ui.screens.schools.FiltersToggleRow
import com.meteomontana.android.ui.screens.schools.MapToggleBar
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
    val toggleStreet: () -> Unit = vm::toggleStreet
    val toggleClosed: () -> Unit = vm::toggleClosed
    val toggleFavorite: (Long) -> Unit = vm::toggleFavorite
    val setMaxDistance: (Double?) -> Unit = vm::setMaxDistance
    val refresh: () -> Unit = vm::refresh
    val clearFilters: () -> Unit = vm::clearFilters
}

/**
 * Cuerpo de Escuelas cuando el selector está en «Rocódromos» o «Muros gratis» (sustituye a la lista de escuelas y
 * a su nota del tiempo), con la misma forma: buscador, mapa plegable, filtros plegables y la lista por cercanía.
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

    val actions = remember(viewModel) { GymsActions(viewModel) }
    val closeKeyboard = rememberKeyboardDismisser()
    val mapState = rememberGymsMapState()
    val filters = state.filters
    // Igual que Escuelas: el mapa y los filtros se abren y se cierran; debajo, siempre la lista por cercanía.
    var mapOpen by rememberSaveable { mutableStateOf(false) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    androidx.activity.compose.BackHandler(enabled = filtersOpen) { filtersOpen = false }

    LazyColumn(
        modifier.fillMaxSize().dismissKeyboardOnScroll(),
        contentPadding = PaddingValues(bottom = LocalTabBarInset.current + Spacing.lg)
    ) {
        item {
            Box(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)) {
                CumbreSearchField(
                    value = filters.query, onValueChange = actions.setQuery,
                    placeholder = stringResource(R.string.gym_search_hint), onSearch = { closeKeyboard() }
                )
            }
        }
        if (mapOpen) {
            item {
                GymsMapBox(
                    state, selectedId, { selectedId = it }, mapState, userLat, userLon, locationPermission, onRequestLocation,
                    onOpenGym, actions, onCenter = { la, lo -> mapCenter = la to lo }
                )
            }
        }
        item {
            Spacer(Modifier.height(Spacing.xs))
            MapToggleBar(expanded = mapOpen, onToggle = { mapOpen = !mapOpen })
            Spacer(Modifier.height(Spacing.sm))
            FiltersToggleRow(
                open = filtersOpen, activeCount = gymActiveFilterCount(filters),
                countText = stringResource(R.string.gym_count_gyms, state.items.size),
                dayLabel = null, daysOpen = false,
                onToggleOpen = { filtersOpen = !filtersOpen }, onToggleDays = {},
                onClear = { actions.clearFilters(); filtersOpen = false },
                favsOn = filters.onlyFavorites, onToggleFavs = actions.toggleFavorites,
                favsLabel = stringResource(R.string.gym_favs_chip), favsDescription = stringResource(R.string.gym_favs_chip_desc)
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        if (filtersOpen) {
            item {
                GymFiltersPanel(
                    filters = filters, resultCount = state.items.size,
                    // La distancia es de elección única: al elegirla el panel se pliega.
                    onDistance = { actions.setMaxDistance(it); filtersOpen = false },
                    onBoulder = actions.toggleBoulder, onRope = actions.toggleRope, onFree = actions.toggleFree,
                    onStreet = actions.toggleStreet, onOpenNow = actions.toggleOpenNow, onClosed = actions.toggleClosed,
                    onApply = { filtersOpen = false }
                )
            }
        }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp) }
        when {
            state.loading && state.all.isEmpty() ->
                item { Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            state.loadFailed && state.all.isEmpty() ->
                item { Message(stringResource(R.string.gym_load_error), stringResource(R.string.gym_retry), actions.refresh) }
            state.items.isEmpty() ->
                item { Message(stringResource(R.string.gym_empty), stringResource(R.string.gym_clear_filters), actions.clearFilters) }
            else -> items(state.items, key = { it.gym.id }) { item ->
                GymListRow(
                    item, item.gym.openStatusNow(), item.gym.id in state.favorites, { onOpenGym(item.gym.id) },
                    onToggleFavorite = { actions.toggleFavorite(item.gym.id) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
            }
        }
        item { Box(Modifier.padding(Spacing.lg)) { AddWallPill { addWallOpen = true } } }
    }
    if (addWallOpen) {
        NewGymSheet(
            lat = mapCenter?.first ?: userLat ?: DEFAULT_LAT,
            lon = mapCenter?.second ?: userLon ?: DEFAULT_LON,
            onDismiss = { addWallOpen = false }
        )
    }
}

/** Mapa plegable (320 dp, como el de Escuelas) con la tarjeta de cristal del marcador pulsado. */
@Composable
private fun GymsMapBox(
    state: GymsUiState,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    mapState: GymsMapState,
    userLat: Double?,
    userLon: Double?,
    locationPermission: Boolean,
    onRequestLocation: () -> Unit,
    onOpenGym: (Long) -> Unit,
    actions: GymsActions,
    onCenter: (Double, Double) -> Unit
) {
    Box(Modifier.fillMaxWidth().height(320.dp)) {
        GymsMap(
            gyms = state.items.map { it.gym }, userLat = userLat, userLon = userLon,
            selectedId = selectedId, onSelect = onSelect, mapState = mapState, onCenterChange = onCenter,
            onRequestLocation = onRequestLocation, locationPermission = locationPermission, modifier = Modifier.fillMaxSize()
        )
        // Sin la píldora «Añadir un muro que falta» encima: tapaba marcadores (va al final de la lista).
        val selected = state.items.firstOrNull { it.gym.id == selectedId }
        if (selected != null) Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = Spacing.md).padding(bottom = Spacing.sm)) {
            SelectedGymCard(
                selected, selected.gym.id in state.favorites, { onSelect(null) }, onOpenGym, { actions.toggleFavorite(selected.gym.id) }
            )
        }
    }
}

@Composable
private fun SelectedGymCard(
    item: com.meteomontana.android.domain.model.gym.GymListItem,
    isFavorite: Boolean,
    onClose: () -> Unit,
    onOpenGym: (Long) -> Unit,
    onToggleFavorite: () -> Unit
) {
    val ctx = LocalContext.current
    val gym = item.gym
    GymMapCard(
        gym = gym, status = gym.openStatusNow(), distanceText = GymPresentation.distanceText(item.distanceKm),
        onClose = onClose, onDirections = { openDirections(ctx, gym.lat, gym.lon, gym.name) }, onOpen = { onOpenGym(gym.id) },
        isFavorite = isFavorite, onToggleFavorite = onToggleFavorite
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
        Modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Box(Modifier.padding(top = Spacing.md)) { CumbreChip(action, true, onAction) }
    }
}
