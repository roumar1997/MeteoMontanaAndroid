package com.meteomontana.android.grips

import com.meteomontana.android.domain.grips.GripWorkoutEngine
import com.meteomontana.android.domain.model.GripWorkout
import com.meteomontana.android.domain.model.GripWorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GripWorkoutEngineTest {

    private fun workout(handMode: String, reps: Int, workS: Int, restS: Int, sets: Int = 1) = GripWorkout(
        id = "w1", name = "Test", handMode = handMode, countMode = "TIEMPO",
        restBetweenSetsS = 0, createdAt = "", updatedAt = "",
        sets = (0 until sets).map {
            GripWorkoutSet("s$it", it, reps, workS, restS, gripTypeId = 1, targetMinPct = 10.0, targetMaxPct = 30.0)
        }
    )

    @Test fun `UNA - una sola mano hace todas las reps y luego termina`() {
        val engine = GripWorkoutEngine(workout("UNA", reps = 2, workS = 10, restS = 5))
        // rep1 work 10s
        engine.tick(10_000)
        assertEquals(GripWorkoutEngine.Phase.REST, engine.state.value.left.phase)
        // rest 5s -> empieza rep2
        engine.tick(5_000)
        assertEquals(GripWorkoutEngine.Phase.WORK, engine.state.value.left.phase)
        assertEquals(1, engine.state.value.left.repIndex)
        // rep2 work 10s -> rest 5s -> terminado (solo 2 reps)
        engine.tick(10_000)
        engine.tick(5_000)
        assertTrue(engine.state.value.finished)
    }

    @Test fun `POR_SERIE - ronda izquierda completa antes de empezar la derecha`() {
        val engine = GripWorkoutEngine(workout("POR_SERIE", reps = 1, workS = 10, restS = 5))
        assertEquals(GripWorkoutEngine.Hand.LEFT, engine.state.value.activeHand)
        engine.tick(10_000) // left work done -> rest
        engine.tick(5_000)  // left rest done -> ronda izq completa (1 rep) -> empieza derecha
        assertEquals(GripWorkoutEngine.Hand.RIGHT, engine.state.value.activeHand)
        assertEquals(GripWorkoutEngine.Phase.WORK, engine.state.value.right.phase)
    }

    @Test fun `POR_REP - ejemplo exacto de Rodrigo, work10 rest20`() {
        // work=10s, rest=20s: la izquierda tira, la derecha empieza en cuanto
        // la izquierda termina (y la izquierda arranca su descanso de 20s en
        // paralelo). Como el descanso (20s) es más largo que 2x el trabajo de
        // la otra mano, a los 20s la derecha ya ha terminado su turno pero la
        // izquierda TODAVÍA no ha completado sus 20s de descanso (solo lleva
        // 10s) -> nadie tira hasta el segundo 30, cuando el descanso de la
        // izquierda por fin termina.
        val engine = GripWorkoutEngine(workout("POR_REP", reps = 4, workS = 10, restS = 20))
        assertEquals(GripWorkoutEngine.Hand.LEFT, engine.state.value.activeHand)

        engine.tick(10_000) // t=10: izq termina de tirar -> empieza a descansar Y la derecha tira YA
        var s = engine.state.value
        assertEquals(GripWorkoutEngine.Hand.RIGHT, s.activeHand)
        assertEquals(GripWorkoutEngine.Phase.REST, s.left.phase)
        assertEquals(20_000L, s.left.remainingMs)

        engine.tick(10_000) // t=20: derecha termina -> pero la izq solo lleva 10s de sus 20s de descanso
        s = engine.state.value
        assertEquals(GripWorkoutEngine.Hand.NONE, s.activeHand)
        assertEquals(10_000L, s.left.remainingMs)

        engine.tick(10_000) // t=30: el descanso de la izquierda por fin termina -> le toca
        s = engine.state.value
        assertEquals(GripWorkoutEngine.Hand.LEFT, s.activeHand)
        assertEquals(1, s.left.repIndex) // acaba de terminar su 1ª rep, ahora arranca la 2ª
    }

    @Test fun `POR_REP - nunca hay dos manos tirando a la vez, incluso con descanso 0`() {
        val engine = GripWorkoutEngine(workout("POR_REP", reps = 4, workS = 5, restS = 0))
        repeat(20) {
            engine.tick(500)
            val s = engine.state.value
            val bothWorking = s.left.phase == GripWorkoutEngine.Phase.WORK && s.right.phase == GripWorkoutEngine.Phase.WORK
            assertTrue("nunca deben tirar las dos manos a la vez", !bothWorking)
        }
    }

    @Test fun `POR_REP - alterna en orden fijo izquierda, derecha, izquierda, derecha`() {
        // work=1s, rest=200ms (rest corto: no genera esperas extra, alterna
        // sin más demora que el propio trabajo de la otra mano).
        val engine = GripWorkoutEngine(workout("POR_REP", reps = 4, workS = 1, restS = 0))
        val order = mutableListOf<GripWorkoutEngine.Hand>()
        var lastActive = GripWorkoutEngine.Hand.NONE
        repeat(60) { // 60 * 200ms = 12s, de sobra para 4 reps de 1s cada una
            engine.tick(200)
            val active = engine.state.value.activeHand
            if (active != GripWorkoutEngine.Hand.NONE && active != lastActive) order.add(active)
            lastActive = active
        }
        assertEquals(listOf(
            GripWorkoutEngine.Hand.LEFT, GripWorkoutEngine.Hand.RIGHT,
            GripWorkoutEngine.Hand.LEFT, GripWorkoutEngine.Hand.RIGHT
        ), order)
    }
}
