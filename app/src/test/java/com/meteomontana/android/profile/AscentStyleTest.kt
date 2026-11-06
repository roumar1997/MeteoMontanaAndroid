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

    @Test fun `ida y vuelta de cada opcion`() {
        AscentStyle.entries.forEach { assertEquals(it, AscentStyle.fromFlags(it.aVista, it.alFlash)) }
    }
}
