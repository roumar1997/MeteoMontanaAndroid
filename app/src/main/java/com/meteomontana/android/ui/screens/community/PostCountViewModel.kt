package com.meteomontana.android.ui.screens.community

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.usecase.feed.GetFeedPostCountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Cuantas publicaciones enseña un perfil (la cuenta la hace el servidor). null mientras no se sabe o si falla. */
@HiltViewModel
class PostCountViewModel @Inject constructor(private val getCount: GetFeedPostCountUseCase) : ViewModel() {
    private val _count = MutableStateFlow<Int?>(null)
    val count: StateFlow<Int?> = _count

    /** [uid] null = el perfil propio. */
    fun load(uid: String?) {
        viewModelScope.launch { runCatching { getCount(uid) }.getOrNull()?.let { _count.value = it } }
    }
}

/** Numero de publicaciones del perfil [uid] (null = propio) para la ficha «Publicaciones»; se refresca al volver a verla. */
@Composable
fun rememberPostCount(uid: String?, refreshKey: Any? = null): Int? {
    val vm: PostCountViewModel = hiltViewModel(key = "post_count_${uid ?: "me"}")
    LaunchedEffect(uid, refreshKey) { vm.load(uid) }
    val count by vm.count.collectAsStateWithLifecycle()
    return count
}
