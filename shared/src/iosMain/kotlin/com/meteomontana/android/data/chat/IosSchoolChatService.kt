package com.meteomontana.android.data.chat

import com.meteomontana.android.domain.port.SchoolChatService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * DTO plano que Swift construye fácilmente (mismo motivo que [IosMsgDto]:
 * evitar clases anidadas de un `interface` en el cruce SKIE). `createdAtMillis
 * = -1` representa null.
 */
data class IosSchoolMsgDto(
    val id: String,
    val fromUid: String,
    val text: String,
    val createdAtMillis: Long,
)

/**
 * Bridge que IMPLEMENTA Swift con FirebaseFirestore, mismo patrón que
 * [IosChatBridge] pero para el chat ABIERTO de una escuela: colección
 * `school_chats/{schoolId}/messages` (ver `firestore.rules`), sin
 * comprobación de participantes.
 */
interface IosSchoolChatBridge {
    fun observeMessages(schoolId: String, limit: Int, onChange: (List<IosSchoolMsgDto>) -> Unit): IosChatListener
    fun sendMessage(schoolId: String, text: String, completion: (String?) -> Unit)
}

class IosSchoolChatService(
    private val bridge: IosSchoolChatBridge,
) : SchoolChatService {

    override fun observeMessages(schoolId: String, limit: Int): Flow<List<SchoolChatService.Message>> = callbackFlow {
        val listener = bridge.observeMessages(schoolId, limit) { dtos -> trySend(dtos.map { it.toModel() }) }
        awaitClose { listener.remove() }
    }

    @Throws(Exception::class)
    override suspend fun sendMessage(schoolId: String, text: String): Unit =
        suspendCancellableCoroutine { cont ->
            bridge.sendMessage(schoolId, text) { err ->
                if (err == null) cont.resume(Unit) else cont.resumeWithException(RuntimeException(err))
            }
        }

    private fun IosSchoolMsgDto.toModel() = SchoolChatService.Message(
        id = id,
        fromUid = fromUid,
        text = text,
        createdAtMillis = createdAtMillis.takeIf { it >= 0 },
    )
}
