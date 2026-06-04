package com.meteomontana.android.data.chat

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Modelo simple de chat 1-a-1 sobre Firestore.
 *
 *   conversations/{convId}        -> { participants[], lastMessage, lastAt, unread_{uid} }
 *   conversations/{convId}/messages/{msgId} -> { fromUid, text, createdAt }
 *
 *   convId = uids ordenados unidos por "_"
 */

data class Conversation(
    val id: String,
    val participants: List<String>,
    val lastMessage: String?,
    val lastFromUid: String?,
    val lastAt: Date?,
    val unreadCount: Long
)

data class ChatMessage(
    val id: String,
    val fromUid: String,
    val text: String,
    val createdAt: Date?
)

@Singleton
class ChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val convsCol = firestore.collection("conversations")

    fun convIdFor(uidA: String, uidB: String): String =
        listOf(uidA, uidB).sorted().joinToString("_")

    /** Stream de conversaciones del usuario actual ordenadas por última actividad. */
    fun observeMyConversations(): Flow<List<Conversation>> = callbackFlow {
        val me = auth.currentUser?.uid ?: run { close(); return@callbackFlow }
        val listener = convsCol
            .whereArrayContains("participants", me)
            .orderBy("lastAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { doc ->
                    val participants = (doc.get("participants") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    if (participants.isEmpty()) return@mapNotNull null
                    Conversation(
                        id = doc.id,
                        participants = participants,
                        lastMessage = doc.getString("lastMessage"),
                        lastFromUid = doc.getString("lastFromUid"),
                        lastAt = doc.getDate("lastAt"),
                        unreadCount = doc.getLong("unread_$me") ?: 0L
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /** Stream de mensajes de una conversación. */
    fun observeMessages(convId: String): Flow<List<ChatMessage>> = callbackFlow {
        val listener = convsCol.document(convId).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(200)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val msgs = snap?.documents?.map { d ->
                    ChatMessage(
                        id = d.id,
                        fromUid = d.getString("fromUid") ?: "",
                        text = d.getString("text") ?: "",
                        createdAt = d.getDate("createdAt")
                    )
                } ?: emptyList()
                trySend(msgs)
            }
        awaitClose { listener.remove() }
    }

    suspend fun sendMessage(otherUid: String, text: String) {
        val me = auth.currentUser?.uid ?: return
        if (text.isBlank() || text.length > 1000) return
        val convId = convIdFor(me, otherUid)
        val ref = convsCol.document(convId)
        val msgRef = ref.collection("messages").document()
        val now = Date()
        val data = mapOf(
            "fromUid" to me,
            "text" to text.trim(),
            "createdAt" to now
        )
        msgRef.set(data).await()
        ref.set(
            mapOf(
                "participants" to listOf(me, otherUid).sorted(),
                "lastMessage" to text.trim(),
                "lastFromUid" to me,
                "lastAt" to now,
                "unread_$otherUid" to com.google.firebase.firestore.FieldValue.increment(1)
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    suspend fun markRead(convId: String) {
        val me = auth.currentUser?.uid ?: return
        convsCol.document(convId).set(
            mapOf("unread_$me" to 0),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }
}
