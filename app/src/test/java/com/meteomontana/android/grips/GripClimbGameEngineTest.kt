package com.meteomontana.android.grips

import com.meteomontana.android.domain.grips.GripClimbGameEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GripClimbGameEngineTest {

    @Test fun `tirando fuerte por encima del minimo, sube`() {
        val engine = GripClimbGameEngine(GripClimbGameEngine.Difficulty.MEDIO)
        repeat(20) { engine.tick(pctOfMax = 100.0, deltaMs = 100) }
        assertTrue("debe subir al tirar con margen de sobra", engine.state.value.heightM > 0.0)
        assertEquals(GripClimbGameEngine.Phase.CLIMBING, engine.state.value.phase)
    }

    @Test fun `sin tirar (0 pct), nunca sube y no se cae del suelo`() {
        val engine = GripClimbGameEngine(GripClimbGameEngine.Difficulty.MEDIO)
        repeat(30) { engine.tick(pctOfMax = 0.0, deltaMs = 100) }
        assertEquals(0.0, engine.state.value.heightM, 0.001)
    }

    @Test fun `al soltar tras subir, entra en fase RAPPEL y desciende`() {
        val engine = GripClimbGameEngine(GripClimbGameEngine.Difficulty.MEDIO)
        repeat(30) { engine.tick(pctOfMax = 100.0, deltaMs = 100) } // sube
        val heightBeforeRelease = engine.state.value.heightM
        assertTrue(heightBeforeRelease > 0.0)

        engine.tick(pctOfMax = 0.0, deltaMs = 100) // suelta
        assertEquals(GripClimbGameEngine.Phase.RAPPEL, engine.state.value.phase)

        repeat(10) { engine.tick(pctOfMax = 0.0, deltaMs = 100) }
        assertTrue("debe descender respecto a antes de soltar", engine.state.value.heightM < heightBeforeRelease)
    }

    @Test fun `la pared se va desplomando y exige mas pct cuanto mas alto`() {
        val engine = GripClimbGameEngine(GripClimbGameEngine.Difficulty.DIFICIL)
        val requiredAtStart = engine.state.value.requiredPct
        repeat(200) { engine.tick(pctOfMax = 100.0, deltaMs = 100) }
        val requiredHigherUp = engine.state.value.requiredPct
        assertTrue("cuanto más alto, más % de tu máximo hace falta", requiredHigherUp > requiredAtStart)
    }

    @Test fun `si se llega al suelo tras haber subido, termina en GAME_OVER`() {
        val engine = GripClimbGameEngine(GripClimbGameEngine.Difficulty.FACIL)
        repeat(15) { engine.tick(pctOfMax = 100.0, deltaMs = 100) } // sube un poco
        repeat(400) { engine.tick(pctOfMax = 0.0, deltaMs = 100) } // suelta hasta tocar suelo
        assertEquals(GripClimbGameEngine.Phase.GAME_OVER, engine.state.value.phase)
    }

    @Test fun `reset vuelve todo a cero`() {
        val engine = GripClimbGameEngine(GripClimbGameEngine.Difficulty.FACIL)
        repeat(20) { engine.tick(pctOfMax = 100.0, deltaMs = 100) }
        engine.reset()
        assertEquals(0.0, engine.state.value.heightM, 0.001)
        assertEquals(GripClimbGameEngine.Phase.CLIMBING, engine.state.value.phase)
    }
}
