package com.meteomontana.android.topo

import com.meteomontana.android.domain.util.startBadgeLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StartBadgeLabelTest {
    @Test fun spanishLabels() {
        assertEquals("PIE", startBadgeLabel("STAND"))
        assertEquals("SIT", startBadgeLabel("sit"))
        assertEquals("SEM", startBadgeLabel("SEMI"))
        assertEquals("LAN", startBadgeLabel("JUMP"))
        assertEquals("TRV", startBadgeLabel("TRAV"))
        assertNull(startBadgeLabel("x"))
    }

    @Test fun englishLabels() {
        assertEquals("STAND", startBadgeLabel("PIE", english = true))
        assertEquals("SIT", startBadgeLabel("SIT", english = true))
        assertEquals("DYNO", startBadgeLabel("LANCE", english = true))
        assertEquals("TRAV", startBadgeLabel("TRAV", english = true))
        assertNull(startBadgeLabel(null, english = true))
    }
}
