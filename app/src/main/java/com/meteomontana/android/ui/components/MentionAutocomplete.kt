package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.meteomontana.android.domain.model.PublicProfile
import com.meteomontana.android.domain.usecase.social.SearchUsersUseCase
import com.meteomontana.android.ui.theme.Terra
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Username en curso al FINAL del texto (lo que va tras el último `@` sin
 * espacios). null si no se está escribiendo una mención ahí. Simplificación:
 * solo autocompleta la mención del final (el caso normal al escribir).
 */
fun activeMentionQuery(text: String): String? {
    val at = text.lastIndexOf('@')
    if (at < 0) return null
    if (at > 0 && !text[at - 1].isWhitespace()) return null   // @ pegado a una palabra → no es mención
    val after = text.substring(at + 1)
    if (after.length > 20 || after.any { it.isWhitespace() }) return null
    return after
}

/** Reemplaza la mención en curso (del último `@`) por `@username ` completo. */
fun applyMention(text: String, username: String): String {
    val at = text.lastIndexOf('@')
    val base = if (at < 0) text else text.substring(0, at)
    return base + "@" + username + " "
}

@HiltViewModel
class MentionSearchViewModel @Inject constructor(
    private val searchUsers: SearchUsersUseCase
) : ViewModel() {
    private val _results = MutableStateFlow<List<PublicProfile>>(emptyList())
    val results: StateFlow<List<PublicProfile>> = _results
    fun search(q: String) {
        viewModelScope.launch {
            _results.value = runCatching { searchUsers(q, 6) }.getOrDefault(emptyList())
        }
    }
    fun clear() { _results.value = emptyList() }
}

/**
 * Lista de sugerencias de usuarios mientras escribes `@`. Se coloca encima del
 * campo de texto; al tocar un usuario llama [onReplace] con el texto ya con la
 * mención insertada. No muestra nada si no hay mención en curso.
 */
@Composable
fun MentionSuggestions(
    text: String,
    onReplace: (String) -> Unit,
    viewModel: MentionSearchViewModel = hiltViewModel()
) {
    val query = activeMentionQuery(text)
    val results by viewModel.results.collectAsState()

    LaunchedEffect(query) {
        val q = query
        if (q == null || q.length < 1) { viewModel.clear(); return@LaunchedEffect }
        delay(220)   // debounce
        viewModel.search(q)
    }

    if (query == null || results.isEmpty()) return
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        results.forEach { p ->
            val uname = p.username ?: return@forEach
            Row(
                Modifier.fillMaxWidth()
                    .clickable { onReplace(applyMention(text, uname)) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (p.photoUrl != null) {
                    AsyncImage(model = p.photoUrl, contentDescription = null,
                        modifier = Modifier.size(26.dp).clip(CircleShape))
                } else {
                    Box(Modifier.size(26.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant))
                }
                Text("@$uname", style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold, color = Terra)
                p.displayName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
