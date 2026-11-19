package com.meteomontana.android.gyms

import com.meteomontana.android.domain.model.gym.GymLabelPlanner
import com.meteomontana.android.domain.model.gym.LabelCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GymLabelPlannerTest {

    @Test fun `dos marcadores lejos se rotulan los dos`() {
        val r = GymLabelPlanner.plan(listOf(LabelCandidate("a", 100f, 200f, 10), LabelCandidate("b", 600f, 200f, 10)), 1f)
        assertEquals(setOf("a", "b"), r)
    }

    @Test fun `dos marcadores pegados no se pisan, solo uno lleva nombre`() {
        val r = GymLabelPlanner.plan(listOf(LabelCandidate("a", 100f, 200f, 12), LabelCandidate("b", 120f, 200f, 12)), 1f)
        assertEquals(1, r.size)
    }

    @Test fun `el seleccionado gana a los demas`() {
        val r = GymLabelPlanner.plan(
            listOf(LabelCandidate("a", 100f, 200f, 12), LabelCandidate("b", 120f, 200f, 12, priority = true)), 1f
        )
        assertEquals(setOf("b"), r)
    }

    @Test fun `un nombre no se pinta encima del circulo de otro marcador`() {
        // b esta justo debajo de a: el nombre de a caeria sobre el circulo de b.
        val r = GymLabelPlanner.plan(listOf(LabelCandidate("a", 100f, 200f, 8), LabelCandidate("b", 100f, 215f, 0)), 1f)
        assertTrue("a" !in r)
    }

    @Test fun `los grupos (sin nombre) no llevan`() {
        assertEquals(emptySet<String>(), GymLabelPlanner.plan(listOf(LabelCandidate("c", 100f, 200f, 0)), 1f))
    }
}
