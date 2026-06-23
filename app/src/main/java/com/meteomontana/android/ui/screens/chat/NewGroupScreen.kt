package com.meteomontana.android.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.meteomontana.android.data.api.KtorChatPushApi
import com.meteomontana.android.domain.model.PublicProfile
import com.meteomontana.android.domain.port.AuthService
import com.meteomontana.android.domain.usecase.social.GetFollowersUseCase
import com.meteomontana.android.domain.usecase.social.GetFollowingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewGroupViewModel @Inject constructor(
    private val authService: AuthService,
    private val getFollowers: GetFollowersUseCase,
    private val getFollowing: GetFollowingUseCase,
    private val chatPushApi: KtorChatPushApi
) : ViewModel() {
    private val _contacts = MutableStateFlow<List<PublicProfile>>(emptyList())
    val contacts: StateFlow<List<PublicProfile>> = _contacts.asStateFlow()

    private val _creating = MutableStateFlow(false)
    val creating: StateFlow<Boolean> = _creating.asStateFlow()

    init {
        viewModelScope.launch {
            val me = authService.currentUid() ?: return@launch
            val followers = runCatching { getFollowers(me) }.getOrDefault(emptyList())
            val following = runCatching { getFollowing(me) }.getOrDefault(emptyList())
            _contacts.value = (followers + following)
                .distinctBy { it.uid }
                .sortedBy { (it.displayName ?: it.username ?: "").lowercase() }
        }
    }

    /** Crea el grupo en el backend y devuelve su convId (o null si falla). */
    fun create(name: String, memberUids: List<String>, onCreated: (String) -> Unit) {
        if (name.isBlank() || memberUids.isEmpty() || _creating.value) return
        _creating.value = true
        viewModelScope.launch {
            val convId = runCatching { chatPushApi.createGroup(name.trim(), memberUids) }.getOrNull()
            _creating.value = false
            if (convId != null) onCreated(convId)
        }
    }
}

@Composable
fun NewGroupScreen(
    onBack: () -> Unit,
    onCreated: (convId: String) -> Unit,
    viewModel: NewGroupViewModel = hiltViewModel()
) {
    val contacts by viewModel.contacts.collectAsState()
    val creating by viewModel.creating.collectAsState()
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var query by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Nuevo grupo", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        OutlinedTextField(
            value = name, onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Nombre del grupo") },
            singleLine = true
        )

        Text("ELIGE MIEMBROS (${selected.size})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

        // Buscador: filtra tus contactos (seguidores + seguidos) por nombre.
        if (contacts.isNotEmpty()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Buscar contacto") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true
            )
        }

        // Contactos visibles según la búsqueda (case-insensitive sobre @usuario y nombre).
        val shown = remember(contacts, query) {
            val q = query.trim().lowercase()
            if (q.isBlank()) contacts
            else contacts.filter {
                (it.username ?: "").lowercase().contains(q) ||
                    (it.displayName ?: "").lowercase().contains(q)
            }
        }

        if (contacts.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Text("Sigue a alguien (o que te sigan) para añadir miembros.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp))
            }
        } else if (shown.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Text("Ningún contacto coincide con «${query.trim()}».",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp))
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.uid }) { p ->
                    val isSel = p.uid in selected
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                selected = if (isSel) selected - p.uid else selected + p.uid
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (p.photoUrl != null) {
                            AsyncImage(model = p.photoUrl, contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                        } else {
                            Box(Modifier.size(40.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant))
                        }
                        Text("@" + (p.username ?: p.displayName ?: p.uid.take(6)),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f))
                        if (isSel) {
                            Box(Modifier.size(24.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Check, contentDescription = "Seleccionado",
                                    tint = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        // Botón crear.
        val canCreate = name.isNotBlank() && selected.isNotEmpty() && !creating
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp)
                .clip(MaterialTheme.shapes.small)
                .background(
                    if (canCreate) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable(enabled = canCreate) {
                    viewModel.create(name, selected.toList(), onCreated)
                }
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            if (creating) {
                CircularProgressIndicator(
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(20.dp))
            } else {
                Text("CREAR GRUPO",
                    color = if (canCreate) androidx.compose.ui.graphics.Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
