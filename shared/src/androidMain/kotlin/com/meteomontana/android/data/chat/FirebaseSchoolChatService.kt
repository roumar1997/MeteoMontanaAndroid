package com.meteomontana.android.data.chat

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.meteomontana.android.domain.port.SchoolChatService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Implementación Android del chat ABIERTO de una escuela: colección
 * `school_chats/{schoolId}/messages` (mismas reglas de Firestore que usa
 * iOS — ver `firestore.rules` del repo de la web). Espejo de
 * [IosSchoolChatBridge] pero sin necesitar puente: Android habla con
 * Firestore directamente, igual que [FirebaseChatService].
 */
class FirebaseSchoolChatService(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : SchoolChatService {

    private fun messagesCol(schoolId: String) =
        firestore.collection("school_chats").document(schoolId).collection("messages")

    override fun observeMessages(schoolId: String, limit: Int): Flow<List<SchoolChatService.Message>> = callbackFlow {
        val listener = messagesCol(schoolId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(limit.toLong())
            .addSnapshotListener { snap, err ->
                if (err != null) { close(); return@addSnapshotListener }
                val msgs = snap?.documents?.map { d ->
                    SchoolChatService.Message(
                        id = d.id,
                        fromUid = d.getString("fromUid") ?: "",
                        text = d.getString("text") ?: "",
                        createdAtMillis = d.getDate("createdAt")?.time
                    )
                } ?: emptyList()
                trySend(msgs)
            }
        awaitClose { listener.remove() }
    }

    @Throws(Exception::class)
    override suspend fun sendMessage(schoolId: String, text: String) {
        val me = auth.currentUser?.uid ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 1000) return
        messagesCol(schoolId).document().set(
            mapOf(
                "fromUid" to me,
                "text" to trimmed,
                "createdAt" to Date()
            )
        ).await()
    }
}
