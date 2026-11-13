package com.meteomontana.android.ui.screens.users

import com.meteomontana.android.ui.components.dismissKeyboardOnScroll
import com.meteomontana.android.ui.components.userRing
import com.meteomontana.android.ui.components.VerifiedCheck
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.meteomontana.android.domain.model.PublicProfile
import com.meteomontana.android.domain.usecase.social.SearchUsersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchUsersViewModel @Inject constructor(
    private val searchUsers: SearchUsersUseCase,
    private val getFollowers: com.meteomontana.android.domain.usecase.social.GetFollowersUseCase,
    private val getFollowing: com.meteomontana.android.domain.usecase.social.GetFollowingUseCase,
    private val followUser: com.meteomontana.android.domain.usecase.social.FollowUserUseCase,
    private val unfollowUser: com.meteomontana.android.domain.usecase.social.UnfollowUserUseCase,
    private val getFollowStatus: com.meteomontana.android.domain.usecase.social.GetFollowStatusUseCase,
    private val recentStore: RecentUsersStore
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<PublicProfile>>(emptyList())
    val results: StateFlow<List<PublicProfile>> = _results.asStateFlow()

    /** A quién sigo yo y a quién he pedido seguir (perfil privado), para el botón de cada tarjeta. */
    private val _following = MutableStateFlow<Set<String>>(emptySet())
    val following: StateFlow<Set<String>> = _following.asStateFlow()
    private val _requested = MutableStateFlow<Set<String>>(emptySet())
    val requested: StateFlow<Set<String>> = _requested.asStateFlow()

    /** Antes de escribir: quienes te siguen y tú no (máx. 5) y tus últimas personas abiertas. Espejo de iOS. */
    private val _suggested = MutableStateFlow<List<PublicProfile>>(emptyList())
    val suggested: StateFlow<List<PublicProfile>> = _suggested.asStateFlow()
    private val _recents = MutableStateFlow(recentStore.load())
    val recents: StateFlow<List<RecentUser>> = _recents.asStateFlow()

    val myUid: String? get() = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

    init {
        viewModelScope.launch {
            val me = myUid ?: return@launch
            val following = runCatching { getFollowing(me) }.getOrDefault(emptyList()).map { it.uid }.toSet()
            _following.value = following
            val followers = runCatching { getFollowers(me) }.getOrDefault(emptyList())
            _suggested.value = followers.filter { it.uid !in following && it.uid != me }.take(5)
        }
    }

    fun setQuery(q: String) {
        _query.value = q
        viewModelScope.launch {
            delay(250)
            if (_query.value != q) return@launch
            _results.value = runCatching {
                if (q.isBlank()) emptyList() else searchUsers(q)
            }.getOrDefault(emptyList())
        }
    }

    fun remember(p: PublicProfile) {
        _recents.value = recentStore.remember(
            RecentUser(p.uid, p.displayName ?: p.username ?: "", p.username, p.photoUrl))
    }

    /** Sigue / deja de seguir desde la tarjeta (optimista; si el perfil es privado queda «Solicitado»). */
    fun toggleFollow(uid: String) {
        when {
            uid in _following.value -> {
                _following.value = _following.value - uid
                viewModelScope.launch { runCatching { unfollowUser(uid) }.onFailure { _following.value = _following.value + uid } }
            }
            uid in _requested.value -> Unit
            else -> {
                _following.value = _following.value + uid
                viewModelScope.launch {
                    runCatching {
                        followUser(uid)
                        val st = getFollowStatus(uid)
                        when {
                            st.iFollowThem -> { _requested.value = _requested.value - uid }
                            st.requestPending -> { _following.value = _following.value - uid; _requested.value = _requested.value + uid }
                            else -> _following.value = _following.value - uid
                        }
                    }.onFailure { _following.value = _following.value - uid }
                }
            }
        }
    }
}

@Composable
fun SearchUsersScreen(
    onBack: () -> Unit,
    onUserClick: (String) -> Unit,
    viewModel: SearchUsersViewModel = hiltViewModel()
) {
    val q by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val following by viewModel.following.collectAsStateWithLifecycle()
    val requested by viewModel.requested.collectAsStateWithLifecycle()
    val suggested by viewModel.suggested.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val myUid = viewModel.myUid

    Column(modifier = Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
        com.meteomontana.android.ui.components.SheetHeader(stringResource(R.string.search_users_title), onClose = onBack)
        val closeKeyboard = com.meteomontana.android.ui.components.rememberKeyboardDismisser()
        // Buscador en píldora, como el de Escuelas y el de los chats.
        androidx.compose.material3.TextField(
            value = q, onValueChange = viewModel::setQuery,
            placeholder = { Text(stringResource(R.string.search_users_hint)) },
            singleLine = true,
            leadingIcon = { Icon(androidx.compose.material.icons.Icons.Outlined.Search, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            shape = CircleShape,
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )

        // Cada persona en una tarjeta con su botón de seguir; al tocarla se apunta en «Recientes».
        val card: @Composable (PublicProfile) -> Unit = { user ->
            UserCard(
                user = user,
                followLabel = when {
                    user.uid == myUid -> null
                    user.uid in following -> FollowLabel.Following
                    user.uid in requested -> FollowLabel.Requested
                    else -> FollowLabel.Follow
                },
                onFollow = { viewModel.toggleFollow(user.uid) },
                onClick = { closeKeyboard(); viewModel.remember(user); onUserClick(user.uid) }
            )
        }

        when {
            q.trim().length < 2 && suggested.isEmpty() && recents.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                // Con menos de 2 letras la pista dice ESO, no "sin resultados" —
                // que sugiere que ya se buscó (Álvaro, 2026-08-24, paridad con iOS).
                Text(stringResource(R.string.search_users_screen_v3_escribe_al_menos_2_letras),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            q.trim().length < 2 -> LazyColumn(Modifier.dismissKeyboardOnScroll(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (suggested.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.search_users_suggested)) }
                    items(suggested, key = { "s_" + it.uid }) { card(it) }
                }
                if (recents.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.search_users_recent)) }
                    items(recents, key = { "r_" + it.uid }) { r ->
                        RecentCard(r) { closeKeyboard(); onUserClick(r.uid) }
                    }
                }
                item { Spacer(Modifier.padding(bottom = 16.dp)) }
            }
            results.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(stringResource(R.string.search_users_screen_v3_sin_resultados), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(Modifier.dismissKeyboardOnScroll(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { SectionLabel(stringResource(R.string.search_users_count, results.size)) }
                items(results, key = { it.uid }) { card(it) }
                item { Spacer(Modifier.padding(bottom = 16.dp)) }
            }
        }
    }
}

private enum class FollowLabel { Follow, Following, Requested }

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
}

private val UserCardShape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)

@Composable
private fun UserCard(user: PublicProfile, followLabel: FollowLabel?, onFollow: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(UserCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, UserCardShape)
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) { UserRow(user, onClick) }
        if (followLabel != null) {
            val filled = followLabel == FollowLabel.Follow
            Text(
                stringResource(when (followLabel) {
                    FollowLabel.Follow -> R.string.search_users_follow
                    FollowLabel.Following -> R.string.search_users_following
                    FollowLabel.Requested -> R.string.search_users_requested
                }),
                color = if (filled) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(CircleShape)
                    .background(if (filled) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                    .border(if (filled) 0.dp else 1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable(onClick = onFollow)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun RecentCard(r: RecentUser, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(UserCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, UserCardShape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (r.photoUrl != null) {
            AsyncImage(model = r.photoUrl, contentDescription = null,
                modifier = Modifier.userRing(r.uid, 40.dp).size(40.dp).clip(CircleShape))
        } else {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Column(Modifier.weight(1f)) {
            Text(r.name.ifBlank { stringResource(R.string.w_user) }, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            r.username?.takeIf { it.isNotBlank() }?.let {
                Text("@$it", style = com.meteomontana.android.ui.theme.EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun UserRow(user: PublicProfile, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (user.photoUrl != null) {
            AsyncImage(model = user.photoUrl, contentDescription = null,
                modifier = Modifier.userRing(user.uid, 40.dp).size(40.dp).clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
        } else {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Column(modifier = Modifier.weight(1f)) {
            // Nombre real primero, @usuario debajo — como UserRow de
            // UsersView.swift. Antes era al revés (@usuario + bio), y el
            // nombre real solo salía si no había username (Álvaro,
            // 2026-08-24, paridad con iOS).
            Text(
                user.displayName?.takeIf { it.isNotBlank() }
                    ?: user.username?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.w_user),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            val userName = user.username
            if (!userName.isNullOrBlank()) {
                Text("@$userName",
                    style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val userTopGrade = user.topGrade
        if (!userTopGrade.isNullOrBlank()) {
            // El grado en una pastilla suave.
            Text(userTopGrade,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
}
