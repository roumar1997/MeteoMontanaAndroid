package com.meteomontana.android.data.chat

import com.meteomontana.android.domain.port.ChatService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Handle para cancelar un snapshot listener de Firestore. Lo implementa Swift
 * (devuelve algo que sabe quitar el listener); el lado Kotlin lo llama en
 * `awaitClose` cuando el Flow se cancela.
 */
interface IosChatListener {
    fun remove()
}

/**
 * DTOs de nivel superior que Swift construye fácilmente (sin tocar las clases
 * anidadas `ChatService.Conversation/ChatMessage`, frágiles con SKIE). El lado
 * Kotlin los mapea a los modelos del port. `*Millis = -1` representa null
 * (evita el boxing de `Long?` al pasarlo desde Swift).
 */
data class IosConvDto(
    val id: String,
    val participants: List<String>,
    val lastMessage: String?,
    val lastFromUid: String?,
    val lastAtMillis: Long,
    val unreadCount: Long,
)

data class IosMsgDto(
    val id: String,
    val fromUid: String,
    val text: String,
    val createdAtMillis: Long,
)

/**
 * Bridge de chat que IMPLEMENTA Swift con FirebaseFirestore. Solo callbacks (sin
 * `suspend` ni `Flow`, que no se implementan bien desde Swift): Swift registra
 * los snapshot listeners y rellena los DTOs; el lado Kotlin ([IosChatService])
 * los envuelve en `Flow`/`suspend` y los mapea al port.
 *
 * DEBE usar la MISMA estructura Firestore que `FirebaseChatService` de Android:
 * colección `conversations` (doc id = uids ordenados unidos por `_`), campos
 * `participants` / `lastMessage` / `lastFromUid` / `lastAt` / `unread_<uid>`, y
 * subcolección `messages` (`fromUid` / `text` / `createdAt`). Así ambas apps leen
 * y escriben el mismo chat.
 */
interface IosChatBridge {
    fun observeConversations(onChange: (List<IosConvDto>) -> Unit): IosChatListener
    fun observeMessages(convId: String, onChange: (List<IosMsgDto>) -> Unit): IosChatListener
    fun sendMessage(otherUid: String, text: String, completion: (String?) -> Unit)
    fun markRead(convId: String, completion: (String?) -> Unit)
}

/**
 * Implementación iOS de [ChatService]. Equivalente del `FirebaseChatService` de
 * Android, pero la parte nativa (Firestore) vive en Swift vía [IosChatBridge].
 */
class IosChatService(
    private val bridge: IosChatBridge,
) : ChatService {

    override fun convIdFor(uidA: String, uidB: String): String =
        listOf(uidA, uidB).sorted().joinToString("_")

    override fun observeMyConversations(): Flow<List<ChatService.Conversation>> = callbackFlow {
        val listener = bridge.observeConversations { dtos -> trySend(dtos.map { it.toModel() }) }
        awaitClose { listener.remove() }
    }

    override fun observeMessages(convId: String): Flow<List<ChatService.ChatMessage>> = callbackFlow {
        val listener = bridge.observeMessages(convId) { dtos -> trySend(dtos.map { it.toModel() }) }
        awaitClose { listener.remove() }
    }

    @Throws(Exception::class)
    override suspend fun sendMessage(otherUid: String, text: String): Unit =
        suspendCancellableCoroutine { cont ->
            bridge.sendMessage(otherUid, text) { err ->
                if (err == null) cont.resume(Unit) else cont.resumeWithException(RuntimeException(err))
            }
        }

    @Throws(Exception::class)
    override suspend fun markRead(convId: String): Unit =
        suspendCancellableCoroutine { cont ->
            bridge.markRead(convId) { err ->
                if (err == null) cont.resume(Unit) else cont.resumeWithException(RuntimeException(err))
            }
        }

    private fun IosConvDto.toModel() = ChatService.Conversation(
        id = id,
        participants = participants,
        lastMessage = lastMessage,
        lastFromUid = lastFromUid,
        lastAtMillis = lastAtMillis.takeIf { it >= 0 },
        unreadCount = unreadCount,
    )

    private fun IosMsgDto.toModel() = ChatService.ChatMessage(
        id = id,
        fromUid = fromUid,
        text = text,
        createdAtMillis = createdAtMillis.takeIf { it >= 0 },
    )
}
