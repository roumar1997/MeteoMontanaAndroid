package com.meteomontana.android.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.PublicProfile
import com.meteomontana.android.domain.port.AuthService
import com.meteomontana.android.domain.port.ChatService
import com.meteomontana.android.domain.usecase.social.GetPublicProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatListItem(
    val conversation: ChatService.Conversation,
    val otherProfile: PublicProfile?
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatService: ChatService,
    private val authService: AuthService,
    private val getPublicProfile: GetPublicProfileUseCase
) : ViewModel() {
    private val _items = MutableStateFlow<List<ChatListItem>>(emptyList())
    val items: StateFlow<List<ChatListItem>> = _items.asStateFlow()

    init {
        viewModelScope.launch {
            chatService.observeMyConversations().collect { convs ->
                val me = authService.currentUid()
                val list = convs.map { conv ->
                    val otherUid = conv.participants.firstOrNull { it != me }
                    val profile = otherUid?.let {
                        runCatching { getPublicProfile(it) }.getOrNull()
                    }
                    ChatListItem(conv, profile)
                }
                _items.value = list
            }
        }
    }

    /** Swipe → borrar conversación. Optimista: la quitamos ya de la lista. */
    fun deleteConversation(convId: String) {
        _items.value = _items.value.filterNot { it.conversation.id == convId }
        viewModelScope.launch { runCatching { chatService.deleteConversation(convId) } }
    }

    /** Swipe → marcar como no leída (vuelve a salir el badge). */
    fun markUnread(convId: String) {
        viewModelScope.launch { runCatching { chatService.markUnread(convId) } }
    }
}
