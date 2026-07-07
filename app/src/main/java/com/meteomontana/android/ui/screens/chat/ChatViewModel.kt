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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val otherUid: String,
    /** Mi uid (de Firebase Auth, disponible offline). Para alinear mis burbujas. */
    val myUid: String? = null,
    val otherProfile: PublicProfile? = null,
    val myProfile: PrivateProfile? = null,
    val canWrite: Boolean = false,
    val messages: List<ChatService.ChatMessage> = emptyList(),
    val loading: Boolean = true,
    /** true si la ventana en vivo llegó llena → puede haber mensajes anteriores
     *  que cargar (muestra el botón "Mensajes anteriores"). */
    val canLoadMore: Boolean = false,
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

    private val _state = MutableStateFlow(ChatUiState(otherUid = otherUid, myUid = me.ifEmpty { null }))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    // Eco optimista: mensajes que acabo de enviar, mostrados YA (estilo WhatsApp)
    // sin esperar a que el listener de Firestore los emita (offline ese listener no
    // refresca en vivo el mensaje local). Se reconcilian (quitan) cuando el mensaje
    // real con el mismo contenido llega del servidor.
    private val pending = mutableListOf<ChatService.ChatMessage>()
    private var lastServer: List<ChatService.ChatMessage> = emptyList()

    // Ventana en vivo de mensajes. Empieza en MESSAGE_PAGE (50) y crece +50 al
    // pulsar "Mensajes anteriores": el listener re-suscribe con una ventana mayor.
    private val messageLimit = MutableStateFlow(ChatService.MESSAGE_PAGE)
    /** Nº de mensajes crudos (antes del filtro cleared) de la última emisión;
     *  si iguala al límite pedido, es que hay (posiblemente) más antiguos. */
    private var lastRawCount = 0

    /** Carga otra página de mensajes anteriores (aumenta la ventana en vivo). */
    fun loadOlder() {
        messageLimit.value += ChatService.MESSAGE_PAGE
    }

    /** ¿Existe ya la conversación (está en mi lista de chats)? Decide si saltarse
     *  startConversation. OJO: NO se basa en que haya mensajes cargados — offline
     *  los mensajes pueden no estar cacheados aunque la conversación exista. */
    private var conversationExists = false

    /** Recalcula los mensajes visibles = servidor + pendientes (reconciliados),
     *  ordenados por fecha. Actualiza el estado directamente (no depende de que el
     *  combine reaccione, que offline no siempre ocurre). */
    private fun rebuildMessages() {
        pending.removeAll { p ->
            lastServer.any { it.fromUid == p.fromUid && it.text == p.text && it.replyToId == p.replyToId }
        }
        val merged = (lastServer + pending).sortedBy { it.createdAtMillis ?: Long.MAX_VALUE }
        val canWrite = _state.value.canWrite || lastServer.isNotEmpty() || conversationExists
        val canLoadMore = lastRawCount >= messageLimit.value
        _state.value = _state.value.copy(
            messages = merged, canWrite = canWrite, loading = false, canLoadMore = canLoadMore
        )
    }

    init {
        viewModelScope.launch {
            val other = runCatching { getPublicProfile(otherUid) }.getOrNull()
            val mine = runCatching { getMyProfile() }.getOrNull()
            val follow = runCatching { getFollowStatus(otherUid) }
                .getOrDefault(FollowStatus(0, 0, false, false))
            // Modelo de privacidad: puedo escribir si el receptor es público, o si
            // hay relación de seguimiento aceptada (en cualquier sentido). Si ya
            // existe conversación, lo recalculamos a true.
            val canWrite = other?.isPublic == true || follow.iFollowThem || follow.theyFollowMe
            _state.value = _state.value.copy(otherProfile = other, myProfile = mine, canWrite = canWrite)

            // Mensajes del servidor + mis conversaciones (para el cleared_<me> y para
            // saber si la conversación existe).
            @OptIn(ExperimentalCoroutinesApi::class)
            kotlinx.coroutines.flow.combine(
                messageLimit.flatMapLatest { chatService.observeMessages(convId, it) },
                chatService.observeMyConversations()
            ) { msgs, convs ->
                lastRawCount = msgs.size
                conversationExists = convs.any { it.id == convId }
                val cleared = convs.firstOrNull { it.id == convId }?.clearedAtMillis
                if (cleared == null) msgs
                else msgs.filter { (it.createdAtMillis ?: Long.MAX_VALUE) > cleared }
            }.collect { server ->
                lastServer = server
                rebuildMessages()
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
        // Eco optimista: muestra el mensaje YA (estilo WhatsApp), antes de tocar la
        // red. Se reconcilia cuando llega el mensaje real del servidor.
        val optimistic = ChatService.ChatMessage(
            id = "pending_${System.nanoTime()}",
            fromUid = me,
            text = text,
            createdAtMillis = System.currentTimeMillis(),
            replyToId = reply?.id,
            replyText = reply?.text,
            replyFromUid = reply?.fromUid
        )
        pending.add(optimistic)
        rebuildMessages()
        viewModelScope.launch {
            // El backend crea el documento de conversación (los clientes no pueden,
            // por las reglas de Firestore). Es la puerta de autorización. Solo hace
            // falta la primera vez de la sesión.
            if (!conversationEnsured) {
                if (conversationExists) {
                    // La conversación YA existe (está en mi lista): no hace falta que el
                    // backend la cree. Saltamos startConversation y escribimos directo en
                    // Firestore, que encola el mensaje offline y lo entrega al reconectar.
                    conversationEnsured = true
                } else {
                    // Conversación nueva: el backend debe crearla y autorizar primero
                    // (las reglas de Firestore no dejan crearla al cliente). Offline esto
                    // falla → quitamos el eco y abortamos (no se puede iniciar una nueva
                    // conversación sin red).
                    val started = runCatching { chatPushApi.startConversation(otherUid) }.isSuccess
                    if (!started) {
                        pending.remove(optimistic)
                        rebuildMessages()
                        return@launch
                    }
                    conversationEnsured = true
                }
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
