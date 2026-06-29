package com.meteomontana.android.ui.screens.users
import com.meteomontana.android.util.toUserMessage

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.PublicProfile
import com.meteomontana.android.domain.usecase.social.FollowUserUseCase
import com.meteomontana.android.domain.usecase.social.GetFollowStatusUseCase
import com.meteomontana.android.domain.usecase.social.GetFollowersUseCase
import com.meteomontana.android.domain.usecase.social.GetFollowingUseCase
import com.meteomontana.android.domain.usecase.social.RemoveFollowerUseCase
import com.meteomontana.android.domain.usecase.social.UnfollowUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FollowListUiState {
    data object Loading : FollowListUiState
    data class Success(
        val items: List<PublicProfile>,
        // uids a los que YO sigo (aceptado) y a los que tengo solicitud pendiente,
        // para pintar el botón Seguir/Siguiendo/Solicitado de cada fila.
        val following: Set<String> = emptySet(),
        val requested: Set<String> = emptySet()
    ) : FollowListUiState
    data class Error(val message: String) : FollowListUiState
}

@HiltViewModel
class FollowListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getFollowers: GetFollowersUseCase,
    private val getFollowing: GetFollowingUseCase,
    private val removeFollowerUseCase: RemoveFollowerUseCase,
    private val followUser: FollowUserUseCase,
    private val unfollowUser: UnfollowUserUseCase,
    private val getFollowStatus: GetFollowStatusUseCase
) : ViewModel() {
    private val uid: String = checkNotNull(savedStateHandle["uid"])
    private val mode: String = checkNotNull(savedStateHandle["mode"]) // "followers" | "following"
    val myUid: String? = FirebaseAuth.getInstance().currentUser?.uid

    private val _state = MutableStateFlow<FollowListUiState>(FollowListUiState.Loading)
    val state: StateFlow<FollowListUiState> = _state.asStateFlow()

    val title: String = if (mode == "followers") "Seguidores" else "Siguiendo"

    // Solo puedo eliminar seguidores en MI propia lista de "Seguidores".
    val canRemove: Boolean = mode == "followers" && uid == myUid

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = try {
                val list = if (mode == "followers") getFollowers(uid) else getFollowing(uid)
                // Conjunto de a quién sigo YO, para los botones por fila. Si estoy en
                // MI propia lista de "Siguiendo", esa lista YA es ese conjunto.
                val following = if (uid == myUid && mode == "following") {
                    list.map { it.uid }.toSet()
                } else if (myUid != null) {
                    runCatching { getFollowing(myUid).map { it.uid }.toSet() }.getOrDefault(emptySet())
                } else emptySet()
                // Solicitudes pendientes: a un perfil privado al que ya pedí seguir
                // hay que pintarlo "Solicitado" también en la lista (no solo en su
                // perfil). El backend no devuelve el estado por fila, así que para
                // cada usuario que NO sigo consulto su follow-status en paralelo.
                val requested = if (myUid != null) {
                    coroutineScope {
                        list.filter { it.uid != myUid && it.uid !in following }
                            .map { p ->
                                async {
                                    p.uid to runCatching { getFollowStatus(p.uid).requestPending }
                                        .getOrDefault(false)
                                }
                            }
                            .awaitAll()
                            .filter { it.second }
                            .map { it.first }
                            .toSet()
                    }
                } else emptySet()
                FollowListUiState.Success(list, following, requested)
            } catch (t: Throwable) {
                FollowListUiState.Error(t.toUserMessage())
            }
        }
    }

    private fun updateSuccess(block: (FollowListUiState.Success) -> FollowListUiState.Success) {
        val cur = _state.value as? FollowListUiState.Success ?: return
        _state.value = block(cur)
    }

    /** Sigue / deja de seguir a la persona de la fila (optimista, reconcilia con el backend). */
    fun toggleFollow(targetUid: String) {
        if (targetUid == myUid) return
        val cur = _state.value as? FollowListUiState.Success ?: return
        if (targetUid in cur.following) {
            // Dejar de seguir.
            updateSuccess { it.copy(following = it.following - targetUid) }
            viewModelScope.launch {
                runCatching { unfollowUser(targetUid) }
                    .onFailure { updateSuccess { it.copy(following = it.following + targetUid) } }
            }
        } else if (targetUid in cur.requested) {
            return // ya solicitado: no hago nada (perfil privado pendiente)
        } else {
            // Seguir (optimista). Tras la llamada consulto el estado real: si el
            // perfil es privado quedará en "Solicitado" en vez de "Siguiendo".
            updateSuccess { it.copy(following = it.following + targetUid) }
            viewModelScope.launch {
                runCatching {
                    followUser(targetUid)
                    getFollowStatus(targetUid)
                }.onSuccess { st ->
                    updateSuccess {
                        when {
                            st.iFollowThem -> it.copy(following = it.following + targetUid, requested = it.requested - targetUid)
                            st.requestPending -> it.copy(following = it.following - targetUid, requested = it.requested + targetUid)
                            else -> it.copy(following = it.following - targetUid)
                        }
                    }
                }.onFailure {
                    updateSuccess { it.copy(following = it.following - targetUid) }
                }
            }
        }
    }

    /** Elimina a un seguidor (optimista; si la red falla, recargo la lista). */
    fun removeFollower(followerUid: String) {
        updateSuccess { it.copy(items = it.items.filter { p -> p.uid != followerUid }) }
        viewModelScope.launch {
            runCatching { removeFollowerUseCase(followerUid) }.onFailure { load() }
        }
    }
}

@Composable
fun FollowListScreen(
    onBack: () -> Unit,
    onUserClick: (String) -> Unit,
    viewModel: FollowListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(viewModel.title, style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when (val s = state) {
            FollowListUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is FollowListUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
            is FollowListUiState.Success -> {
                if (s.items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("Vacío", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn {
                        items(s.items, key = { it.uid }) { u ->
                            UserRow(
                                u = u,
                                onClick = { onUserClick(u.uid) },
                                isMe = u.uid == viewModel.myUid,
                                iFollow = u.uid in s.following,
                                requested = u.uid in s.requested,
                                onToggleFollow = { viewModel.toggleFollow(u.uid) },
                                onRemove = if (viewModel.canRemove) {
                                    { viewModel.removeFollower(u.uid) }
                                } else null
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRow(
    u: PublicProfile,
    onClick: () -> Unit,
    isMe: Boolean = false,
    iFollow: Boolean = false,
    requested: Boolean = false,
    onToggleFollow: () -> Unit = {},
    onRemove: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (u.photoUrl != null) {
            AsyncImage(model = u.photoUrl, contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
        } else {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("@${u.username ?: u.displayName ?: u.uid.take(6)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            val uBio = u.bio
            if (!uBio.isNullOrBlank()) {
                Text(uBio, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        // Botón seguir/siguiendo/solicitado — en cualquier lista (también de otros),
        // para poder seguir desde aquí sin entrar al perfil. No para mí mismo.
        if (!isMe) {
            val label = when {
                iFollow   -> stringResource(R.string.profile_unfollow)
                requested -> stringResource(R.string.profile_requested)
                else      -> stringResource(R.string.profile_follow)
            }
            RowActionButton(
                text = label,
                filled = !iFollow && !requested,
                enabled = !requested,
                onClick = onToggleFollow
            )
        }
        // "Eliminar" solo en MI lista de seguidores → fuerza que dejen de seguirme.
        if (onRemove != null) {
            RowActionButton(text = stringResource(R.string.common_delete), filled = false, onClick = onRemove)
        }
    }
}

@Composable
private fun RowActionButton(
    text: String,
    filled: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val bg = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (filled) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onBackground
    val borderColor = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(modifier = Modifier
        .clip(RoundedCornerShape(2.dp))
        .background(bg, RoundedCornerShape(2.dp))
        .border(1.dp, borderColor, RoundedCornerShape(2.dp))
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}
