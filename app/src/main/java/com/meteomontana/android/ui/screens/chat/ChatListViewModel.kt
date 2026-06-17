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

/** "Borrada para mí" y sin mensajes posteriores → no se muestra. Si llega un
 *  mensaje después de cleared, vuelve a aparecer. Compartido entre la lista y
 *  el badge de no-leídos. */
fun isHiddenForMe(c: ChatService.Conversation): Boolean {
    val cleared = c.clearedAtMillis ?: return false
    val last = c.lastAtMillis ?: return true
    return last <= cleared
}

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatService: ChatService,
    private val authService: AuthService,
    private val getPublicProfile: GetPublicProfileUseCase,
    private val getFollowers: com.meteomontana.android.domain.usecase.social.GetFollowersUseCase,
    private val getFollowing: com.meteomontana.android.domain.usecase.social.GetFollowingUseCase
) : ViewModel() {
    private val _items = MutableStateFlow<List<ChatListItem>>(emptyList())
    val items: StateFlow<List<ChatListItem>> = _items.asStateFlow()

    // Contactos a quienes puedo escribir: seguidores ∪ seguidos (sin repetir).
    private val _contacts = MutableStateFlow<List<PublicProfile>>(emptyList())
    val contacts: StateFlow<List<PublicProfile>> = _contacts.asStateFlow()

    fun loadContacts() {
        if (_contacts.value.isNotEmpty()) return
        viewModelScope.launch {
            val me = authService.currentUid() ?: return@launch
            val followers = runCatching { getFollowers(me) }.getOrDefault(emptyList())
            val following = runCatching { getFollowing(me) }.getOrDefault(emptyList())
            _contacts.value = (followers + following)
                .distinctBy { it.uid }
                .sortedBy { (it.displayName ?: it.username ?: "").lowercase() }
        }
    }

    init {
        viewModelScope.launch {
            chatService.observeMyConversations().collect { convs ->
                val me = authService.currentUid()
                // Oculta las que "borré para mí" (cleared) sin mensajes posteriores.
                val list = convs.filterNot { isHiddenForMe(it) }.map { conv ->
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
