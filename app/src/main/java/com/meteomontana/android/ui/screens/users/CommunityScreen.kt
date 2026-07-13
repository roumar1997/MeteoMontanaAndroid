package com.meteomontana.android.ui.screens.users

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.meteomontana.android.domain.model.TopContributor
import com.meteomontana.android.domain.usecase.social.GetTopContributorsUseCase
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CommunityUiState {
    data object Loading : CommunityUiState
    data class Success(val contributors: List<TopContributor>) : CommunityUiState
    data class Error(val message: String) : CommunityUiState
}

@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val getTopContributors: GetTopContributorsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<CommunityUiState>(CommunityUiState.Loading)
    val state: StateFlow<CommunityUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = CommunityUiState.Loading
        viewModelScope.launch {
            _state.value = runCatching { getTopContributors(20) }
                .fold(
                    onSuccess = { CommunityUiState.Success(it) },
                    onFailure = { CommunityUiState.Error(it.toUserMessage()) }
                )
        }
    }
}

/** Pantalla Comunidad: ranking de mayores contribuidores a la guía. */
@Composable
fun CommunityScreen(
    onBack: () -> Unit,
    onUserClick: (String) -> Unit,
    /** false cuando se embebe (chip RANKING de la pestaña Comunidad): sin cabecera. */
    showHeader: Boolean = true,
    viewModel: CommunityViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        if (showHeader) {
            com.meteomontana.android.ui.components.SheetHeader("Comunidad", onClose = onBack)
        }
        Text(
            "MAYORES CONTRIBUIDORES",
            style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when (val s = state) {
            is CommunityUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is CommunityUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "REINTENTAR", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp)
                            .clickable { viewModel.load() }.padding(8.dp)
                    )
                }
            }
            is CommunityUiState.Success -> {
                if (s.contributors.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(
                            "Aún no hay contribuciones aprobadas.\n¡Sé el primero en proponer algo!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn {
                        itemsIndexed(s.contributors) { index, c ->
                            ContributorRow(rank = index + 1, c = c) { onUserClick(c.uid) }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContributorRow(rank: Int, c: TopContributor, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Puesto: medalla para el podio, número para el resto.
        Text(
            when (rank) {
                1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"
                else -> "$rank"
            },
            fontSize = if (rank <= 3) 22.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
        if (c.photoUrl != null) {
            AsyncImage(
                model = c.photoUrl, contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        } else {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                c.displayName ?: c.username?.let { "@$it" } ?: c.uid.take(6),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            val username = c.username
            if (!username.isNullOrBlank()) {
                Text(
                    "@$username",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${c.approvedCount}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "APORTES", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
