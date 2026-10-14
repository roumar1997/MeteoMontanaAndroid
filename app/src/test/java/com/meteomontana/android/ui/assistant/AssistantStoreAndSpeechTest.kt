package com.meteomontana.android.ui.assistant

import com.meteomontana.android.domain.model.AssistantUnderstood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantStoreAndSpeechTest {

    private class MemoryStorage : TranscriptStorage {
        var text: String? = null
        override fun read() = text
        override fun write(text: String) { this.text = text }
        override fun clear() { text = null }
    }

    private fun conversation(uid: String = "u1", savedAt: Long = 1_000, count: Int = 2) = StoredAssistantConversation(
        uid = uid, savedAt = savedAt,
        messages = (1..count).map { StoredAssistantConversation.Message(it % 2 == 1, "m$it", listOf("c"), null, 5) },
        previous = StoredAssistantConversation.Understood.from(AssistantUnderstood(intent = "SEARCH", gradeMin = "7A", topRated = true, minStars = 4))
    )

    // ── Conversación guardada ─────────────────────────────────────────────────────────────────────────────────────

    @Test fun `se guarda y se recupera tal cual`() {
        val store = AssistantTranscriptStore(MemoryStorage())
        store.save(conversation())
        val back = store.load("u1", nowMs = 2_000)!!
        assertEquals(listOf("m1", "m2"), back.messages.map { it.text })
        val u = back.previous!!.toDomain()
        assertEquals("7A", u.gradeMin)
        assertEquals(true, u.topRated)
        assertEquals(4, u.minStars)
    }

    @Test fun `caduca pasadas las horas y se borra`() {
        val storage = MemoryStorage()
        val store = AssistantTranscriptStore(storage)
        store.save(conversation(savedAt = 0))
        assertNull(store.load("u1", nowMs = AssistantTranscriptStore.TTL_MS))
        assertNull(storage.text)
    }

    @Test fun `solo se recupera de la misma cuenta`() {
        val store = AssistantTranscriptStore(MemoryStorage())
        store.save(conversation(uid = "u1"))
        assertNull(store.load("otra", nowMs = 2_000))
        assertNotNull(store.load("u1", nowMs = 2_000))
    }

    @Test fun `guarda como mucho los ultimos mensajes`() {
        val store = AssistantTranscriptStore(MemoryStorage())
        store.save(conversation(count = 50))
        val back = store.load("u1", nowMs = 2_000)!!
        assertEquals(AssistantTranscriptStore.MAX_MESSAGES, back.messages.size)
        assertEquals("m50", back.messages.last().text)
    }

    @Test fun `una conversacion guardada con una version anterior sigue leyendose`() {
        val storage = MemoryStorage()
        storage.text = """{"uid":"u1","savedAt":1000,"messages":[{"isUser":true,"text":"hola","createdAt":5}],""" +
            """"previous":{"intent":"SEARCH","gradeMin":"6A"}}"""
        val back = AssistantTranscriptStore(storage).load("u1", nowMs = 2_000)!!
        assertEquals("6A", back.previous!!.gradeMin)
        assertEquals(false, back.previous!!.topRated)
    }

    @Test fun `un texto roto no rompe nada`() {
        val storage = MemoryStorage().also { it.text = "{no es json" }
        assertNull(AssistantTranscriptStore(storage).load("u1"))
    }

    // ── Cuándo dejar de escuchar ──────────────────────────────────────────────────────────────────────────────────

    private val policy = AssistantSpeechPolicy()

    @Test fun `con texto y silencio de 2,5 s se envia`() {
        assertEquals(AssistantSpeechPolicy.Decision.SEND, policy.decide(nowMs = 6_000, startedAtMs = 0, lastTextAtMs = 3_500, hasText = true))
    }

    @Test fun `con texto reciente sigue escuchando`() {
        assertEquals(AssistantSpeechPolicy.Decision.KEEP_LISTENING, policy.decide(nowMs = 5_000, startedAtMs = 0, lastTextAtMs = 3_500, hasText = true))
    }

    @Test fun `sin oir nada se abandona a los 8 s`() {
        assertEquals(AssistantSpeechPolicy.Decision.KEEP_LISTENING, policy.decide(7_000, 0, null, false))
        assertEquals(AssistantSpeechPolicy.Decision.GIVE_UP, policy.decide(8_000, 0, null, false))
    }

    @Test fun `el tope de duracion envia si hay texto y abandona si no`() {
        assertEquals(AssistantSpeechPolicy.Decision.SEND, policy.decide(25_000, 0, 24_900, true))
        assertEquals(AssistantSpeechPolicy.Decision.GIVE_UP, policy.decide(25_000, 0, null, false))
    }
}
