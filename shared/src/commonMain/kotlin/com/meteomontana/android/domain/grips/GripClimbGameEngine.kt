package com.meteomontana.android.domain.grips

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Motor del minijuego arcade "Flappy pared": el escalador cuelga de una
 * cuerda fija arriba (como un top-rope). Mientras tiras fuerte, sube pegado
 * a la pared; al soltar, la cuerda se destensa y cae en péndulo hacia atrás
 * y hacia abajo (rapel), no en caída recta. La pared se va tumbando
 * (desplomando) cuanto más alto llegas, así que hace falta más % de tu
 * máximo para seguir subiendo — hasta que deja de ser calentamiento.
 *
 * Puro Kotlin (sin dependencias de plataforma) para reutilizarse en Android
 * e iOS, mismo patrón que [GripWorkoutEngine].
 */
class GripClimbGameEngine(private val difficulty: Difficulty) {

    enum class Difficulty { FACIL, MEDIO, DIFICIL }

    enum class Phase { CLIMBING, RAPPEL, GAME_OVER }

    /**
     * [wallOverhangDeg] es cuánto se ha tumbado la pared en el punto actual
     * (0 = pared vertical, positivo = desplome hacia el escalador — techo).
     * [requiredPct] es el % de tu máximo que hace falta tirar AHORA MISMO
     * para seguir subiendo, dado el desplome actual.
     */
    data class GameState(
        val heightM: Double = 0.0,
        val bestHeightM: Double = 0.0,
        val phase: Phase = Phase.CLIMBING,
        val wallOverhangDeg: Double = 0.0,
        val requiredPct: Double = 0.0,
        // Ángulo del péndulo durante el rapel (0 = colgado pegado a la pared,
        // positivo = balanceándose hacia atrás/afuera).
        val swingAngleRad: Double = 0.0,
        val ropePaidOutM: Double = 0.0
    )

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    // --- Parámetros por dificultad: qué tan rápido se empina la pared y el
    // tope de exigencia (nunca se pide más del 95% de tu máximo). ---
    private val overhangGrowthPerM: Double = when (difficulty) {
        Difficulty.FACIL -> 1.1
        Difficulty.MEDIO -> 1.8
        Difficulty.DIFICIL -> 2.6
    }
    private val maxRequiredPct: Double = when (difficulty) {
        Difficulty.FACIL -> 55.0
        Difficulty.MEDIO -> 75.0
        Difficulty.DIFICIL -> 92.0
    }
    private val climbSpeedMPerSec: Double = 0.9

    private var swingAngularVel = 0.0
    private val gravity = 9.8

    /**
     * Un tick del motor. [pctOfMax] = fuerza actual como % de tu máximo en
     * esa mano (0-100+, ya normalizado por el llamador). [deltaMs] tiempo
     * transcurrido desde el tick anterior.
     */
    fun tick(pctOfMax: Double, deltaMs: Long): GameState {
        val dt = (deltaMs / 1000.0).coerceIn(0.0, 0.2)
        val s = _state.value
        if (s.phase == Phase.GAME_OVER) return s

        val overhang = overhangAt(s.heightM)
        val required = requiredPctAt(s.heightM)
        val grabbing = pctOfMax >= required

        val next = if (grabbing) {
            // Pegado a la pared, subiendo. Cuanto más te sobra de fuerza
            // respecto al mínimo exigido, un poco más rápido subes.
            val margin = ((pctOfMax - required) / 100.0).coerceIn(0.0, 1.0)
            val speed = climbSpeedMPerSec * (0.6 + 0.4 * margin)
            val newHeight = s.heightM + speed * dt
            swingAngularVel = 0.0
            s.copy(
                heightM = newHeight,
                bestHeightM = max(s.bestHeightM, newHeight),
                phase = Phase.CLIMBING,
                wallOverhangDeg = overhangAt(newHeight),
                requiredPct = requiredPctAt(newHeight),
                swingAngleRad = 0.0,
                ropePaidOutM = 0.0
            )
        } else {
            // Soltaste: la cuerda se destensa y el muñeco pendula hacia
            // atrás/abajo. Simulamos un péndulo simple con la cuerda pagando
            // longitud (te vas alejando de la pared mientras bajas).
            val ropeLen = (s.ropePaidOutM + 1.2).coerceAtLeast(1.2)
            val angularAccel = -(gravity / ropeLen) * sin(s.swingAngleRad)
            swingAngularVel += angularAccel * dt
            swingAngularVel *= 0.995 // fricción del aire/roce con la cuerda
            val newAngle = (s.swingAngleRad + swingAngularVel * dt).coerceIn(0.0, PI / 2.1)
            val descent = (1.0 - cos(newAngle)) * ropeLen * 0.5 + 0.35 * dt
            val newHeight = (s.heightM - descent * dt * 3.0).coerceAtLeast(0.0)
            val newPaidOut = (s.ropePaidOutM + 0.4 * dt).coerceIn(0.0, 3.0)

            if (newHeight <= 0.0 && s.heightM > 0.0) {
                s.copy(heightM = 0.0, phase = Phase.GAME_OVER)
            } else {
                s.copy(
                    heightM = newHeight,
                    phase = Phase.RAPPEL,
                    wallOverhangDeg = overhangAt(newHeight),
                    requiredPct = requiredPctAt(newHeight),
                    swingAngleRad = newAngle,
                    ropePaidOutM = newPaidOut
                )
            }
        }
        _state.value = next
        return next
    }

    /** Ángulo de desplome de la pared a una altura dada — expuesto para que la
     *  UI pueda dibujar el perfil de la pared por delante/detrás del punto
     *  actual (no solo el ángulo de "ahora mismo"). */
    fun overhangAt(heightM: Double): Double =
        min(heightM * overhangGrowthPerM, 75.0) // grados; tope ~75° para que siga siendo jugable

    private fun requiredPctAt(heightM: Double): Double {
        val overhang = overhangAt(heightM)
        // 0° pared vertical => casi sin esfuerzo; a tope de desplome exige maxRequiredPct.
        val frac = (overhang / 75.0).coerceIn(0.0, 1.0)
        return 8.0 + frac * (maxRequiredPct - 8.0)
    }

    fun reset() {
        swingAngularVel = 0.0
        _state.value = GameState()
    }
}
