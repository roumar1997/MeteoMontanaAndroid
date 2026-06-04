package com.meteomontana.android.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.PublicProfileDto
import com.meteomontana.android.data.chat.ChatRepository
import com.meteomontana.android.data.chat.Conversation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatListItem(
    val conversation: Conversation,
    val otherProfile: PublicProfileDto?
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepo: ChatRepository,
    private val api: SchoolApi
) : ViewModel() {
    private val _items = MutableStateFlow<List<ChatListItem>>(emptyList())
    val items: StateFlow<List<ChatListItem>> = _items.asStateFlow()

    init {
        viewModelScope.launch {
            chatRepo.observeMyConversations().collect { convs ->
                // Para cada conversación cargamos el perfil del otro.
                val list = convs.map { conv ->
                    val me = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    val other = conv.participants.firstOrNull { it != me }
                    val profile = other?.let {
                        runCatching { api.getUserProfile(it) }.getOrNull()
                    }
                    ChatListItem(conv, profile)
                }
                _items.value = list
            }
        }
    }
}
