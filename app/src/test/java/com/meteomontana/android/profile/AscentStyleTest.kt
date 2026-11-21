package com.meteomontana.android.profile

import com.meteomontana.android.domain.model.AscentStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AscentStyleTest {
    @Test fun `sin flags es ninguno`() = assertEquals(AscentStyle.NONE, AscentStyle.fromFlags(false, false))
    @Test fun `aVista es a vista`() = assertEquals(AscentStyle.ONSIGHT, AscentStyle.fromFlags(true, false))
    @Test fun `alFlash es al flash`() = assertEquals(AscentStyle.FLASH, AscentStyle.fromFlags(false, true))
    @Test fun `datos incoherentes con los dos a true gana a vista`() =
        assertEquals(AscentStyle.ONSIGHT, AscentStyle.fromFlags(true, true))

    @Test fun `ninguna opcion enciende los dos booleanos a la vez`() {
        AscentStyle.entries.forEach { assertFalse(it.aVista && it.alFlash) }
    }

    @Test fun `flash luego desmarcar luego a vista termina con un solo estilo`() {
        val link = com.meteomontana.android.domain.model.gym.AttemptsStyleLink
        val empty = com.meteomontana.android.domain.model.gym.StyleAttempts(false, null)
        val flash = link.setFlash(empty, true)
        assertEquals(AscentStyle.FLASH, AscentStyle.fromFlags(flash.onsight, flash.flash))
        val onsight = link.setOnsight(empty, true) // desmarcar = hoja nueva; marcar a vista
        assertFalse(onsight.onsight && onsight.flash)
        assertEquals(AscentStyle.ONSIGHT, AscentStyle.fromFlags(onsight.onsight, onsight.flash))
        assertFalse(link.setOnsight(flash, true).flash) // incluso desde flash
    }

    @Test fun `ida y vuelta de cada opcion`() {
        AscentStyle.entries.forEach { assertEquals(it, AscentStyle.fromFlags(it.aVista, it.alFlash)) }
    }
}
