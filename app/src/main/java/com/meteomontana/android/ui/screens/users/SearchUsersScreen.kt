package com.meteomontana.android.ui.screens.users

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
    private val searchUsers: SearchUsersUseCase
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<PublicProfile>>(emptyList())
    val results: StateFlow<List<PublicProfile>> = _results.asStateFlow()

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
}

@Composable
fun SearchUsersScreen(
    onBack: () -> Unit,
    onUserClick: (String) -> Unit,
    viewModel: SearchUsersViewModel = hiltViewModel()
) {
    val q by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
        com.meteomontana.android.ui.components.SheetHeader(stringResource(R.string.search_users_title), onClose = onBack)
        Spacer(Modifier.padding(top = 8.dp))
        val closeKeyboard = com.meteomontana.android.ui.components.rememberKeyboardDismisser()
        OutlinedTextField(
            value = q, onValueChange = viewModel::setQuery,
            placeholder = { Text(stringResource(R.string.search_users_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(Modifier.padding(top = 8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (results.isEmpty()) {
            // Con menos de 2 letras la pista dice ESO, no "sin resultados" —
            // que sugiere que ya se buscó. Faltaba en Android (UsersView.swift
            // sí lo hace) — Álvaro, 2026-08-24, paridad con iOS.
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    if (q.length < 2) "Escribe al menos 2 letras" else "Sin resultados",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn {
                items(results) { user ->
                    UserRow(user) { closeKeyboard(); onUserClick(user.uid) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun UserRow(user: PublicProfile, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (user.photoUrl != null) {
            AsyncImage(model = user.photoUrl, contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape)
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
                    ?: "Usuario",
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
            Text(userTopGrade,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}
