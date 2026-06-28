package com.meteomontana.android.ui.screens.meetups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.meteomontana.android.domain.model.Meetup
import com.meteomontana.android.domain.util.Geo
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeetupsScreen(
    onMeetupClick: (String) -> Unit = {},
    onOpenChat: (String) -> Unit = {},
    onCreateMeetup: () -> Unit = {},
    onOpenAlert: () -> Unit = {},
    viewModel: MeetupsViewModel = hiltViewModel()
) {
    val state by viewModel.listState.collectAsState()
    val alertState by viewModel.alertState.collectAsState()
    val schoolResults by viewModel.schoolResults.collectAsState()
    val myGender by viewModel.myGender.collectAsState()
    val dayScores by viewModel.dayScores.collectAsState()
    val uLat by viewModel.userLat.collectAsState()
    val uLon by viewModel.userLon.collectAsState()
    var showSchoolFilter by remember { mutableStateOf(false) }
    var showWomenGateDialog by remember { mutableStateOf(false) }
    var mapExpanded by remember { mutableStateOf(false) }
    var filtersExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadAlertState() }

    // Filtros aplicados localmente (recomputa cuando llega la ubicación)
    val displayedMeetups = remember(state.meetups, state.filterPrivacy, state.maxDistanceKm, state.filterDays, state.filterDiscipline, uLat, uLon) {
        var list = state.meetups
        if (state.filterPrivacy != null) list = list.filter { it.privacy == state.filterPrivacy }
        val maxKm = state.maxDistanceKm
        val myLat = uLat
        val myLon = uLon
        if (maxKm != null && myLat != null && myLon != null) {
            list = list.filter { m ->
                val sLat = m.schoolLat ?: return@filter true
                val sLon = m.schoolLon ?: return@filter true
                Geo.haversineKm(myLat, myLon, sLat, sLon) <= maxKm
            }
        }
        if (state.filterDays.isNotEmpty()) list = list.filter { meetup ->
            meetup.days.any { it in state.filterDays }
        }
        val disc = state.filterDiscipline
        if (disc != null) list = list.filter {
            it.discipline == disc || it.discipline == "BOTH" || it.discipline == null
        }
        list
    }

    // Distancia para mostrar en cada card (calculada con la ubicación observada)
    fun distanceFor(meetup: Meetup): Double? {
        val lat = uLat ?: return null
        val lon = uLon ?: return null
        val sLat = meetup.schoolLat ?: return null
        val sLon = meetup.schoolLon ?: return null
        return Geo.haversineKm(lat, lon, sLat, sLon)
    }

    // Cargar scores de todas las escuelas visibles para los días que aparecen
    LaunchedEffect(displayedMeetups) {
        val schoolIds = displayedMeetups.map { it.schoolId }.distinct()
        val allDays = displayedMeetups.flatMap { it.days }.distinct()
        if (schoolIds.isNotEmpty() && allDays.isNotEmpty()) {
            viewModel.loadAllDayScores(schoolIds, allDays)
        }
    }

    // Contar filtros activos
    val activeFilterCount = listOfNotNull(
        state.filterPrivacy, state.filterRelation, state.filterSchoolName,
        state.maxDistanceKm?.toString(), state.filterDiscipline
    ).size + state.filterDays.size

    Column(Modifier.fillMaxSize()) {
        // ── Header (fijo) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(0.dp))
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("QUEDADAS", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = if (displayedMeetups.isEmpty()) "Quedar a escalar"
                           else "${displayedMeetups.size} activas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.meteomontana.android.ui.components.HelpButton(topicKey = "meetups")
                IconButton(onClick = { viewModel.loadMeetups() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Recargar")
                }
                val alertEnabled = alertState?.enabled == true
                IconButton(onClick = onOpenAlert) {
                    Icon(
                        if (alertEnabled) Icons.Outlined.NotificationsActive
                        else Icons.Outlined.NotificationsOff,
                        contentDescription = "Configurar alertas",
                        tint = if (alertEnabled) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onCreateMeetup) {
                    Icon(Icons.Outlined.Add, contentDescription = "Crear quedada",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // ── Coach mark ──
        com.meteomontana.android.ui.components.FirstTimeHint(
            hintKey = "meetups_intro",
            text = "Crea quedadas, filtra por día o distancia, y toca una quedada para ver su detalle o entrar al chat si ya estás unido."
        )

        // ── Mapa (fuera del LazyColumn para que el MapView sea estable) ──
        MeetupsMapPanel(
            meetups = displayedMeetups,
            expanded = mapExpanded,
            onToggle = { mapExpanded = !mapExpanded },
            onSchoolSelected = { schoolId ->
                val name = displayedMeetups.firstOrNull { it.schoolId == schoolId }?.schoolName
                    ?: state.meetups.firstOrNull { it.schoolId == schoolId }?.schoolName
                viewModel.setFilterSchool(schoolId, name)
                mapExpanded = false
            },
            userLat = uLat,
            userLon = uLon,
            maxDistanceKm = state.maxDistanceKm
        )

        // ── Filtros + lista en LazyColumn scrollable ──
        when {
            state.isLoading && displayedMeetups.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && displayedMeetups.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No se pudo cargar", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Spacing.sm))
                        TextButton(onClick = { viewModel.loadMeetups() }) { Text("REINTENTAR") }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Spacing.xxl)
                ) {
                    // Toggle filtros
                    item(key = "filter_toggle") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(0.dp))
                                .clickable { filtersExpanded = !filtersExpanded }
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("FILTROS", style = EyebrowTextStyle,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f))
                            if (activeFilterCount > 0) {
                                Box(Modifier.clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 6.dp, vertical = 1.dp)) {
                                    Text("$activeFilterCount", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.size(6.dp))
                            }
                            Icon(
                                if (filtersExpanded) Icons.Outlined.KeyboardArrowUp
                                else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null, modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (filtersExpanded) {
                        item(key = "filters") {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(0.dp))
                                    .padding(Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                FilterGroupLabel("TIPO DE GRUPO")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip("Todas", state.filterRelation == null && state.filterPrivacy == null) {
                                        viewModel.setFilterRelation(null); viewModel.setFilterPrivacy(null)
                                    }
                                    FilterChip("Siguiendo", state.filterRelation == "following") {
                                        viewModel.setFilterRelation(if (state.filterRelation == "following") null else "following")
                                    }
                                    FilterChip("Seguidos/Seguidores", state.filterPrivacy == "FOLLOWERS") {
                                        viewModel.setFilterPrivacy(if (state.filterPrivacy == "FOLLOWERS") null else "FOLLOWERS")
                                    }
                                    FilterChip("No mixto", state.filterPrivacy == "WOMEN") {
                                        if (myGender == "WOMAN") viewModel.setFilterPrivacy(
                                            if (state.filterPrivacy == "WOMEN") null else "WOMEN")
                                        else showWomenGateDialog = true
                                    }
                                }
                                FilterGroupLabel("DISTANCIA")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val distances = listOf(null to "Cualquiera", 25 to "< 25 km", 50 to "< 50 km",
                                        100 to "< 100 km", 200 to "< 200 km", 500 to "< 500 km")
                                    distances.forEach { (km, label) ->
                                        FilterChip(label, state.maxDistanceKm == km) { viewModel.setMaxDistance(km) }
                                    }
                                }
                                FilterGroupLabel("DISCIPLINA")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val disciplines = listOf(null to "Cualquiera", "BOULDER" to "Bloque",
                                        "ROUTE" to "Vía", "BOTH" to "Ambas")
                                    disciplines.forEach { (key, label) ->
                                        FilterChip(label, state.filterDiscipline == key) {
                                            viewModel.setFilterDiscipline(key)
                                        }
                                    }
                                }
                                FilterGroupLabel("DÍAS")
                                val next10 = remember { nextNDaysFilter(10) }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip("Cualquier día", state.filterDays.isEmpty()) { viewModel.clearFilterDays() }
                                    next10.forEach { (iso, label) ->
                                        FilterChip(label, state.filterDays.contains(iso)) { viewModel.toggleFilterDay(iso) }
                                    }
                                }
                                FilterGroupLabel("ESCUELA")
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(2.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                                        .clickable { showSchoolFilter = true }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Outlined.Search, null, Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = state.filterSchoolName ?: "Buscar escuela…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (state.filterSchoolName != null) MaterialTheme.colorScheme.onSurface
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (state.filterSchoolName != null) {
                                        IconButton(onClick = { viewModel.setFilterSchool(null, null) },
                                            modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Outlined.Close, null, Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "divider") { HorizontalDivider() }

                    if (displayedMeetups.isEmpty()) {
                        item(key = "empty") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(Spacing.xxl),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Outlined.Groups, null, Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(Spacing.md))
                                Text("Sin quedadas activas", style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(Spacing.sm))
                                Text("Crea una para quedar a escalar", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(displayedMeetups, key = { it.id }) { meetup ->
                            MeetupListItem(
                                meetup = meetup,
                                dayScoresMap = dayScores,
                                distanceKm = distanceFor(meetup),
                                onClick = {
                                    if (meetup.joined) onOpenChat(meetup.conversationId)
                                    else onMeetupClick(meetup.id)
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    // Diálogos
    if (showWomenGateDialog) {
        AlertDialog(
            onDismissRequest = { showWomenGateDialog = false },
            title = { Text("Quedadas No Mixto") },
            text = {
                Text("Para ver y participar en quedadas No Mixto necesitas indicar " +
                     "tu género como Mujer en tu perfil.\n\nVe a Perfil → Editar perfil → Género.")
            },
            confirmButton = {
                TextButton(onClick = { showWomenGateDialog = false }) { Text("ENTENDIDO") }
            }
        )
    }
    if (showSchoolFilter) {
        MeetupSchoolFilterDialog(
            results = schoolResults,
            onQueryChange = { viewModel.searchSchools(it) },
            onSelect = { school ->
                viewModel.setFilterSchool(school.id, school.name)
                showSchoolFilter = false
                viewModel.clearSchoolSearch()
            },
            onDismiss = { showSchoolFilter = false; viewModel.clearSchoolSearch() }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeetupListItem(meetup: Meetup, dayScoresMap: Map<String, Int> = emptyMap(),
                   distanceKm: Double? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Foto
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val photo = meetup.photoUrl ?: meetup.creatorPhotoUrl
            if (photo != null) {
                AsyncImage(model = photo, contentDescription = meetup.name,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Outlined.Groups, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Info
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // Escuela + distancia como eyebrow
            val eyebrow = buildString {
                meetup.schoolName?.let { append(it.uppercase()) }
                if (distanceKm != null) append(" · ${distanceKm.toInt()} KM")
            }
            if (eyebrow.isNotBlank()) {
                Text(eyebrow, style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Nombre en serif
            Text(meetup.name, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                fontFamily = com.meteomontana.android.ui.theme.SourceSerif4Family)
            // Días con score individual
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                meetup.days.forEach { day ->
                    val score = dayScoresMap["${meetup.schoolId}_$day"]
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(formatDayMonth(day), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (score != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(scoreColor(score))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text("$score", style = MaterialTheme.typography.labelSmall,
                                    color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            // Meta: disciplina + privacidad
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                meetup.discipline?.let {
                    Text(disciplineLabel(it), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (meetup.privacy != "OPEN") {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(Icons.Outlined.Lock, null, Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(privacyLabel(meetup.privacy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Right: badge + members
        Column(horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                meetup.joined -> {
                    Box(Modifier.clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = Spacing.sm, vertical = 2.dp)) {
                        Text("UNIDO", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                meetup.isFull -> {
                    Box(Modifier.clip(RoundedCornerShape(2.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        .padding(horizontal = Spacing.sm, vertical = 2.dp)) {
                        Text("LLENO", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(Icons.Outlined.Person, null, Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                val limitText = meetup.memberLimit?.let { "${meetup.memberCount}/$it" }
                    ?: "${meetup.memberCount}"
                Text(limitText, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FilterGroupLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
             else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MeetupSchoolFilterDialog(
    results: List<com.meteomontana.android.domain.model.School>,
    onQueryChange: (String) -> Unit,
    onSelect: (com.meteomontana.android.domain.model.School) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buscar escuela", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; onQueryChange(it) },
                    placeholder = { Text("Ej. Zarzalejo, Pedriza…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp)
                )
                if (results.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(results) { school ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(school) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                Text(school.name, style = MaterialTheme.typography.bodyMedium)
                                school.location?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                } else if (query.length >= 2) {
                    Text("Sin resultados", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ── Helpers ──

private val MONTH_NAMES = listOf("ene","feb","mar","abr","may","jun","jul","ago","sep","oct","nov","dic")

private fun nextNDaysFilter(n: Int): List<Pair<String, String>> {
    val dayNames = listOf("dom","lun","mar","mié","jue","vie","sáb")
    val result = mutableListOf<Pair<String, String>>()
    val now = System.currentTimeMillis()
    val dayMs = 86_400_000L
    for (i in 0 until n) {
        val ts = now + i * dayMs
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
        val y = cal.get(java.util.Calendar.YEAR)
        val mo = cal.get(java.util.Calendar.MONTH) + 1
        val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1
        val iso = "%04d-%02d-%02d".format(y, mo, d)
        val label = "${dayNames[dow]} $d ${MONTH_NAMES[mo - 1]}"
        result.add(iso to label)
    }
    return result
}

internal fun formatDayMonth(iso: String): String {
    val parts = iso.split("-")
    if (parts.size != 3) return iso
    val month = parts[1].toIntOrNull() ?: return iso
    val day = parts[2].toIntOrNull() ?: return iso
    return "$day ${MONTH_NAMES.getOrElse(month - 1) { "?" }}"
}

private fun scoreColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF22C55E)
    score >= 60 -> Color(0xFFF59E0B)
    score >= 40 -> Color(0xFFEF4444)
    else        -> Color(0xFF6B7280)
}

internal fun privacyLabel(privacy: String) = when (privacy) {
    "FOLLOWERS" -> "Seguidos"
    "WOMEN"     -> "No mixto"
    else        -> "Abierta"
}

internal fun disciplineLabel(discipline: String) = when (discipline) {
    "BOULDER" -> "Bloque"
    "ROUTE"   -> "Vía"
    "BOTH"    -> "Bloque + Vía"
    else      -> discipline
}
