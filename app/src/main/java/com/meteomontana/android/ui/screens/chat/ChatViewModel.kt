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
    val loading: Boolean = true,
    /** Mensaje al que estoy respondiendo (cita), o null. */
    val replyingTo: ChatService.ChatMessage? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatService: ChatService,
    private val authService: AuthService,
    private val getPublicProfile: GetPublicProfileUseCase,
    private val getFollowStatus: GetFollowStatusUseCase,
    private val getMyProfile: GetMyProfileUseCase,
    private val chatPushApi: com.meteomontana.android.data.api.KtorChatPushApi
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
            // Modelo de privacidad: puedo escribir si el receptor es público, o si
            // hay relación de seguimiento aceptada (en cualquier sentido). Si ya hay
            // conversación abierta, lo recalculamos a true al cargar los mensajes.
            val canWrite = other?.isPublic == true || follow.iFollowThem || follow.theyFollowMe
            _state.value = _state.value.copy(otherProfile = other, myProfile = mine, canWrite = canWrite)

            // Combinamos mensajes + mis conversaciones para ocultar el historial
            // anterior a un "borrado para mí" (cleared_<me>). Si me escriben de
            // nuevo, los mensajes nuevos (posteriores a cleared) sí se ven.
            kotlinx.coroutines.flow.combine(
                chatService.observeMessages(convId),
                chatService.observeMyConversations()
            ) { msgs, convs ->
                val cleared = convs.firstOrNull { it.id == convId }?.clearedAtMillis
                if (cleared == null) msgs
                else msgs.filter { (it.createdAtMillis ?: Long.MAX_VALUE) > cleared }
            }.collect { visible ->
                // Si ya hay conversación con mensajes, ambos pueden seguir hablando
                // aunque no haya follow ni el receptor sea público (excepción del modelo).
                val canWrite = _state.value.canWrite || visible.isNotEmpty()
                _state.value = _state.value.copy(messages = visible, canWrite = canWrite, loading = false)
                runCatching { chatService.markRead(convId) }
            }
        }
    }

    /** Conversación asegurada en el backend esta sesión (evita pedirlo en cada mensaje). */
    private var conversationEnsured = false

    /** Empieza a responder a [msg] (muestra la cita sobre el campo de texto). */
    fun startReply(msg: ChatService.ChatMessage) {
        _state.value = _state.value.copy(replyingTo = msg)
    }

    /** Cancela la respuesta en curso. */
    fun cancelReply() {
        _state.value = _state.value.copy(replyingTo = null)
    }

    fun send(text: String) {
        if (!_state.value.canWrite) return
        val reply = _state.value.replyingTo
        _state.value = _state.value.copy(replyingTo = null)
        viewModelScope.launch {
            // El backend crea el documento de conversación (los clientes no pueden,
            // por las reglas de Firestore). Es la puerta de autorización: si no está
            // permitido escribir, responde 403 y abortamos. Idempotente, así que solo
            // hace falta la primera vez de la sesión.
            if (!conversationEnsured) {
                val started = runCatching { chatPushApi.startConversation(otherUid) }.isSuccess
                if (!started) return@launch
                conversationEnsured = true
            }
            val ok = runCatching {
                chatService.sendMessage(
                    otherUid, text,
                    replyToId = reply?.id,
                    replyText = reply?.text,
                    replyFromUid = reply?.fromUid
                )
            }.isSuccess
            // Si el mensaje se escribió en Firestore con éxito, pedimos al backend
            // que dispare la push notification al receptor.
            if (ok) runCatching { chatPushApi.notifyMessage(otherUid, text) }
        }
    }
}
