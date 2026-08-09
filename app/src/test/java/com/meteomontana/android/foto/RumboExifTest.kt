package com.meteomontana.android.foto

import com.meteomontana.android.ui.components.fraccionADecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El rumbo de la cámara, tal y como lo escriben las cámaras en el EXIF.
 *
 * De aquí sale la orientación que se sugiere al proponer una piedra, así que un
 * fallo silencioso aquí se traduce en paredes guardadas mirando a donde no es.
 * El formato estándar es una fracción ("4500/100" = 45°), pero no todas las
 * cámaras lo respetan.
 */
class RumboExifTest {

    @Test fun `fraccion normal`() {
        assertEquals(45f, fraccionADecimal("4500/100")!!, 0.001f)
        assertEquals(270f, fraccionADecimal("2700/10")!!, 0.001f)
    }

    @Test fun `numero suelto, como lo escriben algunas camaras`() {
        assertEquals(123.5f, fraccionADecimal("123.5")!!, 0.001f)
    }

    @Test fun `division por cero no revienta`() {
        // Visto en camaras baratas: preferimos no sugerir nada a sugerir basura.
        assertNull(fraccionADecimal("100/0"))
    }

    @Test fun `basura devuelve nulo`() {
        assertNull(fraccionADecimal(""))
        assertNull(fraccionADecimal("norte"))
        assertNull(fraccionADecimal("a/b"))
    }
}
