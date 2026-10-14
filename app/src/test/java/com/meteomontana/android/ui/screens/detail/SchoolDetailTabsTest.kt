package com.meteomontana.android.ui.screens.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class SchoolDetailTabsTest {
    @Test fun `solo bloques`() = assertEquals(BlocksTabKind.BOULDERS, blocksTabKind("Bloque"))
    @Test fun `solo vias`() = assertEquals(BlocksTabKind.ROUTES, blocksTabKind("Vía"))
    @Test fun `solo vias sin tilde`() = assertEquals(BlocksTabKind.ROUTES, blocksTabKind("Via"))
    @Test fun `de las dos`() = assertEquals(BlocksTabKind.BOTH, blocksTabKind("Vía,Bloque"))
    @Test fun `de las dos con espacios y minusculas`() = assertEquals(BlocksTabKind.BOTH, blocksTabKind("bloque , vía"))
    @Test fun `sin estilo es bloques`() {
        assertEquals(BlocksTabKind.BOULDERS, blocksTabKind(null))
        assertEquals(BlocksTabKind.BOULDERS, blocksTabKind(""))
    }
}
