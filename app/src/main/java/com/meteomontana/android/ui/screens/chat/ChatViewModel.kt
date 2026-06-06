package com.meteomontana.android.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.FollowStatus
import com.meteomontana.android.domain.model.PrivateProfile
import com.meteomontana.android.domain.model.PublicProfile
import com.meteomontana.android.domain.port.AuthService
import com.meteomontana.android.domain.port.ChatService
import com.meteomontana.android.domain.usecase.profile.GetMyProfileUseCase
import com.meteomontana.android.domain.usecase.social.GetFollowStatusUseCase
import com.meteomontana.android.domain.usecase.social.GetPublicProfileUseCase
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
    private val getPublicProfile: GetPublicProfileUseCase,
    private val getFollowStatus: GetFollowStatusUseCase,
    private val getMyProfile: GetMyProfileUseCase
) : ViewModel() {
    private val otherUid: String = checkNotNull(savedStateHandle["uid"])
    private val me: String = authService.currentUid() ?: ""
    private val convId: String = chatService.convIdFor(me, otherUid)

    private val _state = MutableStateFlow(ChatUiState(otherUid = otherUid))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val other = runCatching { getPublicProfile(otherUid) }.getOrNull()
            val mine = runCatching { getMyProfile() }.getOrNull()
            val follow = runCatching { getFollowStatus(otherUid) }
                .getOrDefault(FollowStatus(0, 0, false, false))
            val canWrite = other != null || follow.iFollowThem || follow.theyFollowMe
            _state.value = _state.value.copy(otherProfile = other, myProfile = mine, canWrite = canWrite)

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
