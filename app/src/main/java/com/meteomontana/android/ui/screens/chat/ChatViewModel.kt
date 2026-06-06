package com.meteomontana.android.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.data.chat.ChatMessage
import com.meteomontana.android.domain.model.FollowStatus
import com.meteomontana.android.domain.model.PrivateProfile
import com.meteomontana.android.domain.model.PublicProfile
import com.meteomontana.android.data.chat.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val otherUid: String,
    val otherProfile: PublicProfile? = null,
    val myProfile: PrivateProfile? = null,
    val canWrite: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val loading: Boolean = true
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepo: ChatRepository,
    private val api: SchoolApi
) : ViewModel() {
    private val otherUid: String = checkNotNull(savedStateHandle["uid"])
    private val me: String = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val convId: String = chatRepo.convIdFor(me, otherUid)

    private val _state = MutableStateFlow(ChatUiState(otherUid = otherUid))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Cargar perfiles y permiso
            val other = runCatching { api.getUserProfile(otherUid).toDomain() }.getOrNull()
            val mine = runCatching { api.getMyProfile().toDomain() }.getOrNull()
            val follow = runCatching { api.getFollowStatus(otherUid).toDomain() }.getOrDefault(
                FollowStatus(0, 0, false, false)
            )
            // Regla: si "other" es público -> puedes escribir
            //        si "other" es privado y NO te sigue -> NO puedes (a menos que tú seas público y
            //        ya hayas escrito antes — vamos a simplificar: tienes que seguirle o que te siga).
            val otherIsPublic = other != null   // PublicProfileDto solo se devuelve para perfiles públicos
            val canWrite = otherIsPublic || follow.iFollowThem || follow.theyFollowMe
            _state.value = _state.value.copy(
                otherProfile = other, myProfile = mine, canWrite = canWrite
            )

            // Escucha mensajes en vivo
            chatRepo.observeMessages(convId).collect { msgs ->
                _state.value = _state.value.copy(messages = msgs, loading = false)
                runCatching { chatRepo.markRead(convId) }
            }
        }
    }

    fun send(text: String) {
        if (!_state.value.canWrite) return
        viewModelScope.launch {
            runCatching { chatRepo.sendMessage(otherUid, text) }
        }
    }
}
