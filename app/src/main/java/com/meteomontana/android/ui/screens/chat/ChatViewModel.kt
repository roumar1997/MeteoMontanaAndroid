package com.meteomontana.android.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.ProfileApi
import com.meteomontana.android.data.api.SocialApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.FollowStatus
import com.meteomontana.android.domain.model.PrivateProfile
import com.meteomontana.android.domain.model.PublicProfile
import com.meteomontana.android.domain.port.AuthService
import com.meteomontana.android.domain.port.ChatService
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
    val messages: List<ChatService.ChatMessage> = emptyList(),
    val loading: Boolean = true
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatService: ChatService,
    private val authService: AuthService,
    private val socialApi: SocialApi,
    private val profileApi: ProfileApi
) : ViewModel() {
    private val otherUid: String = checkNotNull(savedStateHandle["uid"])
    private val me: String = authService.currentUid() ?: ""
    private val convId: String = chatService.convIdFor(me, otherUid)

    private val _state = MutableStateFlow(ChatUiState(otherUid = otherUid))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val other = runCatching { socialApi.getUserProfile(otherUid).toDomain() }.getOrNull()
            val mine = runCatching { profileApi.getMyProfile().toDomain() }.getOrNull()
            val follow = runCatching { socialApi.getFollowStatus(otherUid).toDomain() }.getOrDefault(
                FollowStatus(0, 0, false, false)
            )
            val otherIsPublic = other != null
            val canWrite = otherIsPublic || follow.iFollowThem || follow.theyFollowMe
            _state.value = _state.value.copy(
                otherProfile = other, myProfile = mine, canWrite = canWrite
            )

            chatService.observeMessages(convId).collect { msgs ->
                _state.value = _state.value.copy(messages = msgs, loading = false)
                runCatching { chatService.markRead(convId) }
            }
        }
    }

    fun send(text: String) {
        if (!_state.value.canWrite) return
        viewModelScope.launch {
            runCatching { chatService.sendMessage(otherUid, text) }
        }
    }
}
