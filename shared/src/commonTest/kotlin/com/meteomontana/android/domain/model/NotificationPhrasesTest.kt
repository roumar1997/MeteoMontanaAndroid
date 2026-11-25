package com.meteomontana.android.domain.model

import com.meteomontana.android.data.api.dto.NotificationDto
import com.meteomontana.android.data.api.dto.toDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationPhrasesTest {
    private val p = "tu bloque «X» (6a) en Albarracín"

    @Test fun usesPhraseWhenPresent() {
        assertEquals("dio me gusta a $p", NotificationPhrases.action("FEED_LIKE", false, p, "es"))
        assertEquals("dieron me gusta a $p", NotificationPhrases.action("FEED_LIKE", true, p, "es"))
        assertEquals("comentó en $p", NotificationPhrases.action("FEED_COMMENT", false, p, "es"))
        assertEquals("te mencionó en $p", NotificationPhrases.action("FEED_MENTION", false, p, "es"))
        assertEquals("liked your route «Y»", NotificationPhrases.action("FEED_LIKE", true, "your route «Y»", "en"))
    }

    @Test fun fallsBackWithoutPhrase() {
        assertNull(NotificationPhrases.action("FEED_LIKE", false, null, "es"))
        assertNull(NotificationPhrases.action("FEED_COMMENT", false, "  ", "es"))
        assertNull(NotificationPhrases.action("NEW_FOLLOWER", false, p, "es"))
    }

    @Test fun dtoCarriesOptionalPhrase() {
        val base = NotificationDto(id = "1", type = "FEED_LIKE", title = "t", createdAt = "now")
        assertNull(base.toDomain().postPhrase)
        assertEquals(p, base.copy(postPhrase = p).toDomain().postPhrase)
    }
}
