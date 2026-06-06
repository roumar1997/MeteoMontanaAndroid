package com.meteomontana.android.data.chat

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.meteomontana.android.domain.port.ChatService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

private fun Date?.toMillis(): Long? = this?.time

@Singleton
class FirebaseChatService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ChatService {

    private val convsCol = firestore.collection("conversations")

    override fun convIdFor(uidA: String, uidB: String): String =
        listOf(uidA, uidB).sorted().joinToString("_")

    override fun observeMyConversations(): Flow<List<ChatService.Conversation>> = callbackFlow {
        val me = auth.currentUser?.uid ?: run { close(); return@callbackFlow }
        val listener = convsCol
            .whereArrayContains("participants", me)
            .orderBy("lastAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { doc ->
                    val participants = (doc.get("participants") as? List<*>)
                        ?.filterIsInstance<String>() ?: emptyList()
                    if (participants.isEmpty()) return@mapNotNull null
                    ChatService.Conversation(
                        id = doc.id,
                        participants = participants,
                        lastMessage = doc.getString("lastMessage"),
                        lastFromUid = doc.getString("lastFromUid"),
                        lastAtMillis = doc.getDate("lastAt").toMillis(),
                        unreadCount = doc.getLong("unread_$me") ?: 0L
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override fun observeMessages(convId: String): Flow<List<ChatService.ChatMessage>> = callbackFlow {
        val listener = convsCol.document(convId).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(200)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val msgs = snap?.documents?.map { d ->
                    ChatService.ChatMessage(
                        id = d.id,
                        fromUid = d.getString("fromUid") ?: "",
                        text = d.getString("text") ?: "",
                        createdAtMillis = d.getDate("createdAt").toMillis()
                    )
                } ?: emptyList()
                trySend(msgs)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun sendMessage(otherUid: String, text: String) {
        val me = auth.currentUser?.uid ?: return
        if (text.isBlank() || text.length > 1000) return
        val convId = convIdFor(me, otherUid)
        val ref = convsCol.document(convId)
        val msgRef = ref.collection("messages").document()
        val now = Date()
        msgRef.set(mapOf(
            "fromUid" to me,
            "text" to text.trim(),
            "createdAt" to now
        )).await()
        ref.set(
            mapOf(
                "participants" to listOf(me, otherUid).sorted(),
                "lastMessage" to text.trim(),
                "lastFromUid" to me,
                "lastAt" to now,
                "unread_$otherUid" to FieldValue.increment(1)
            ),
            SetOptions.merge()
        ).await()
    }

    override suspend fun markRead(convId: String) {
        val me = auth.currentUser?.uid ?: return
        convsCol.document(convId).set(
            mapOf("unread_$me" to 0),
            SetOptions.merge()
        ).await()
    }
}
