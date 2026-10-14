package com.meteomontana.android.ui.guide

import com.meteomontana.android.domain.usecase.help.Guide
import com.meteomontana.android.domain.usecase.help.GuideStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El recorrido, el encadenado de pantalla + pestaña y el parpadeo del "? AYUDA": lógica con reglas, sin pintura. */
class GuideControllerTest {

    private class FakePrefs : GuidePrefs {
        val map = mutableMapOf<String, Any>()
        override fun bool(key: String, default: Boolean) = map[key] as? Boolean ?: default
        override fun setBool(key: String, value: Boolean) { map[key] = value }
        override fun int(key: String, default: Int) = map[key] as? Int ?: default
        override fun setInt(key: String, value: Int) { map[key] = value }
        override fun removeWithPrefix(vararg prefixes: String) {
            map.keys.filter { k -> prefixes.any { k.startsWith(it) } }.forEach { map.remove(it) }
        }
    }

    private fun guide(key: String, steps: Int) =
        Guide(key, (1..steps).map { GuideStep("$key.t$it", "T$it", "B$it") })

    private val catalog = mapOf("detail" to guide("detail", 2), "detail_map" to guide("detail_map", 2), "schools" to guide("schools", 3))

    private fun controller(prefs: GuidePrefs = FakePrefs()) = GuideController(prefs) { catalog[it] }

    @Test fun `sin pantalla en pantalla la guia no arranca`() {
        val c = controller()
        assertFalse(c.start("schools"))
        assertNull(c.activeKey)
    }

    @Test fun `una clave sin guia no arranca aunque la pantalla este`() {
        val c = controller()
        c.registerHost("chat")
        assertFalse(c.start("chat"))
    }

    @Test fun `recorre los pasos y termina`() {
        val c = controller()
        c.registerHost("schools")
        assertTrue(c.start("schools"))
        assertEquals(0, c.index)
        c.next(); assertEquals(1, c.index)
        c.next(); assertEquals(2, c.index)
        c.next(); assertNull(c.activeKey)
    }

    @Test fun `una pantalla atiende su guia y las de sus pestanas`() {
        val c = controller()
        c.registerHost("detail")
        assertTrue(c.isMine("detail", "detail"))
        assertTrue(c.isMine("detail", "detail_map"))
        assertFalse(c.isMine("detail", "details"))
        assertFalse(c.isMine("detail", "schools"))
        assertTrue(c.start("detail_map"))
    }

    @Test fun `la primera vez cuenta la pantalla y luego la pestana, y despues solo la pestana`() {
        val c = controller()
        c.registerHost("detail")
        assertTrue(c.startScreenAndTab("detail", "detail_map"))
        assertEquals("detail", c.activeKey)
        c.next(); c.next()                       // acaba la de la pantalla (2 pasos)
        assertEquals("detail_map", c.activeKey)  // y sigue la de la pestana
        c.next(); c.next()
        assertNull(c.activeKey)

        assertTrue(c.startScreenAndTab("detail", "detail_map"))
        assertEquals("detail_map", c.activeKey)  // ya vista la de la pantalla: solo la de la pestana
    }

    @Test fun `salir a mitad corta tambien el encadenado`() {
        val c = controller()
        c.registerHost("detail")
        c.startScreenAndTab("detail", "detail_map")
        c.stop()
        assertNull(c.activeKey)
        c.next()
        assertNull(c.activeKey)
    }

    @Test fun `abrir una ayuda apaga el parpadeo y sube la version`() {
        val c = controller()
        c.registerHost("schools")
        val v0 = c.usedVersion
        assertTrue(c.registerVisitAndShouldPulse("schools"))
        c.start("schools")
        assertTrue(c.usedVersion > v0)
        assertTrue(c.isUsed("schools"))
        assertFalse(c.registerVisitAndShouldPulse("schools"))
    }

    @Test fun `solo parpadea las primeras visitas`() {
        val c = controller()
        repeat(GuideController.MAX_PULSE_VISITS) { assertTrue(c.registerVisitAndShouldPulse("weather")) }
        assertFalse(c.registerVisitAndShouldPulse("weather"))
    }

    @Test fun `con las guias apagadas no parpadea nada`() {
        val c = controller()
        c.enabled = false
        assertFalse(c.registerVisitAndShouldPulse("weather"))
    }

    @Test fun `volver a ver las guias lo reinicia y las reactiva`() {
        val prefs = FakePrefs()
        val c = controller(prefs)
        c.enabled = false
        c.markUsed("schools")
        prefs.setInt(GuideController.visitsKey("schools"), 9)
        GuideController.resetAll(prefs)
        assertTrue(c.enabled)
        assertFalse(c.isUsed("schools"))
        assertTrue(prefs.bool(GuideController.KEY_PENDING_SCHOOLS, false))
        assertTrue(c.registerVisitAndShouldPulse("schools"))
    }

    @Test fun `los anfitriones se cuentan por referencia`() {
        val c = controller()
        c.registerHost("schools"); c.registerHost("schools")
        c.unregisterHost("schools")
        assertTrue(c.start("schools"))           // aun queda uno
        c.stop()
        c.unregisterHost("schools")
        assertFalse(c.start("schools"))
    }
}
