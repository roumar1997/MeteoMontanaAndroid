package com.meteomontana.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Cubre el parseo de enlaces compartidos `/s/...` (antes sin ningún test).
 * El caso `/s/q` con token es el más caliente: ese token decide el bypass de
 * FOLLOWERS al unirse a una quedada.
 */
class DeepLinkParserTest {

    private fun segs(path: String) = path.trim('/').split("/")

    @Test fun `quedada con token de invitacion`() {
        val r = DeepLinkParser.parse(segs("s/q/m123")) { if (it == "i") "tok-abc" else null }
        assertEquals(DeepLinkTarget("meetup", "m123"), r?.target)
        assertEquals("m123", r?.meetupInviteId)
        assertEquals("tok-abc", r?.meetupInviteToken)
    }

    @Test fun `quedada sin token`() {
        val r = DeepLinkParser.parse(segs("s/q/m123"))
        assertEquals(DeepLinkTarget("meetup", "m123"), r?.target)
        assertEquals("m123", r?.meetupInviteId)
        assertNull(r?.meetupInviteToken)
    }

    @Test fun `escuela`() {
        assertEquals(DeepLinkTarget("school", "albarracin"), DeepLinkParser.parse(segs("s/e/albarracin"))?.target)
    }

    @Test fun `via con escuela y linea`() {
        assertEquals(DeepLinkTarget("via", "esc|via7"), DeepLinkParser.parse(segs("s/v/esc/via7"))?.target)
    }

    @Test fun `via sin la linea no navega`() {
        assertNull(DeepLinkParser.parse(segs("s/v/esc")))
    }

    @Test fun `perfil de usuario`() {
        assertEquals(DeepLinkTarget("user", "jara"), DeepLinkParser.parse(segs("s/u/jara"))?.target)
    }

    @Test fun `publicacion del feed`() {
        assertEquals(DeepLinkTarget("feed_post", "42"), DeepLinkParser.parse(segs("s/p/42"))?.target)
    }

    @Test fun `prefijo distinto de s no es deep-link`() {
        assertNull(DeepLinkParser.parse(segs("x/e/algo")))
    }

    @Test fun `s sin segundo segmento`() {
        assertNull(DeepLinkParser.parse(listOf("s")))
    }

    @Test fun `subtipo desconocido`() {
        assertNull(DeepLinkParser.parse(segs("s/z/algo")))
    }
}
