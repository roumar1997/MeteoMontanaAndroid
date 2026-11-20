package com.meteomontana.android.gym

import com.meteomontana.android.domain.model.gym.GymClusterer
import com.meteomontana.android.domain.model.gym.GymKind
import com.meteomontana.android.domain.model.gym.GymOpenStatus
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GymStreetAndSinglesTest {

    @Test
    fun `un muro de calle esta siempre abierto`() {
        val street = gym(kind = GymKind.FREE)
        val night = Instant.parse("2026-11-16T02:00:00Z")
        assertEquals(GymOpenStatus.AlwaysOpen, street.openStatusAt(night))
        assertEquals(true, street.isOpenAt(night))
    }

    @Test
    fun `sin agrupar cada rocodromo es su marcador`() {
        val gyms = (1L..5L).map { gym(id = it, lat = 40.4, lon = -3.7) }
        val items = GymClusterer.singles(gyms)
        assertEquals(5, items.size)
        assertTrue(items.none { it.isCluster })
    }
}
