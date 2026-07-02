package com.meteomontana.android.domain.grips

import com.meteomontana.android.domain.model.GripWorkout
import com.meteomontana.android.domain.model.GripWorkoutSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Motor de un entreno de agarres en ejecución. Implementa la máquina de
 * estados de alternancia de manos de GRIPS_DESIGN.md sección 4 — puro
 * Kotlin (sin dependencias de plataforma) para poder reutilizarlo en
 * Android e iOS. El consumidor llama a [tick] periódicamente (~100ms) con
 * el tiempo transcurrido; el motor no gestiona su propio reloj/coroutines
 * para poder testearse de forma determinista.
 */
class GripWorkoutEngine(private val workout: GripWorkout) {

    /** REST_JUST_ENDED_WORK: marca de un solo tick — "esta mano acaba de
     *  terminar de tirar"; el llamador la convierte a REST inmediatamente y
     *  libera el turno. Existe para distinguir "recién empieza a descansar"
     *  de "ya llevaba un rato descansando" dentro del mismo tick. */
    enum class Phase { WORK, REST_JUST_ENDED_WORK, REST, WAITING, DONE }
    enum class Hand { LEFT, RIGHT, NONE }

    data class HandState(
        val hand: Hand,
        val phase: Phase = Phase.DONE,
        val remainingMs: Long = 0,
        val repIndex: Int = 0
    )

    data class EngineState(
        val setIndex: Int = 0,
        val left: HandState = HandState(Hand.LEFT),
        val right: HandState = HandState(Hand.RIGHT),
        val activeHand: Hand = Hand.NONE,
        val finished: Boolean = false
    )

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    // Solo relevante en POR_SERIE: qué mano está haciendo su ronda ahora mismo.
    private var currentSerieHand = Hand.LEFT
    // Solo relevante en POR_REP: a quién le toca el próximo turno de trabajo.
    // La izquierda ya tiene el primer turno (initialState), así que el
    // SIGUIENTE turno es de la derecha.
    private var nextTurnHand = Hand.RIGHT

    private fun initialState(): EngineState {
        if (workout.sets.isEmpty()) return EngineState(finished = true)
        return when (workout.handMode) {
            "UNA" -> EngineState(
                left = HandState(Hand.LEFT, Phase.WORK, workMs(0), 0),
                right = HandState(Hand.RIGHT, Phase.DONE),
                activeHand = Hand.LEFT
            )
            "POR_SERIE" -> EngineState(
                left = HandState(Hand.LEFT, Phase.WORK, workMs(0), 0),
                right = HandState(Hand.RIGHT, Phase.WAITING),
                activeHand = Hand.LEFT
            )
            else -> EngineState( // POR_REP
                left = HandState(Hand.LEFT, Phase.WORK, workMs(0), 0),
                right = HandState(Hand.RIGHT, Phase.WAITING),
                activeHand = Hand.LEFT
            )
        }
    }

    private fun workMs(setIndex: Int): Long = workout.sets[setIndex].workS * 1000L
    private fun restMs(setIndex: Int): Long = workout.sets[setIndex].restS * 1000L

    /** Avanza el motor [deltaMs] milisegundos. Idempotente/determinista. */
    fun tick(deltaMs: Long) {
        val s = _state.value
        if (s.finished || workout.sets.isEmpty()) return
        when (workout.handMode) {
            "UNA" -> tickSingleHand(deltaMs)
            "POR_SERIE" -> tickPorSerie(deltaMs)
            else -> tickPorRep(deltaMs)
        }
    }

    // ---- UNA: ciclo simple work→rest→siguiente rep→siguiente set, una sola mano ----
    private fun tickSingleHand(deltaMs: Long) {
        val s = _state.value
        val (newLeft, newSetIndex, finished) = advanceSoloCycle(s.left, deltaMs, s.setIndex)
        _state.value = s.copy(
            setIndex = newSetIndex.coerceAtMost((workout.sets.size - 1).coerceAtLeast(0)),
            left = newLeft,
            activeHand = if (newLeft.phase == Phase.WORK) Hand.LEFT else Hand.NONE,
            finished = finished
        )
    }

    /** Avanza el ciclo de una mano que trabaja SOLA en todos sus sets (usado
     *  por UNA y, por ronda, por POR_SERIE). Devuelve (nuevoEstado, nuevoSetIndex, terminado). */
    private fun advanceSoloCycle(hs: HandState, deltaMs: Long, setIndexIn: Int): Triple<HandState, Int, Boolean> {
        var setIndex = setIndexIn
        if (setIndex >= workout.sets.size) return Triple(hs.copy(phase = Phase.DONE), setIndex, true)
        var phase = hs.phase
        var remaining = hs.remainingMs - deltaMs
        var rep = hs.repIndex
        while (remaining <= 0) {
            when (phase) {
                Phase.WORK -> { phase = Phase.REST; remaining += restMs(setIndex) }
                Phase.REST -> {
                    rep++
                    val reps = workout.sets[setIndex].reps
                    if (rep >= reps) {
                        setIndex++
                        rep = 0
                        if (setIndex >= workout.sets.size) return Triple(hs.copy(phase = Phase.DONE, remainingMs = 0), setIndex, true)
                    }
                    phase = Phase.WORK; remaining += workMs(setIndex)
                }
                else -> return Triple(hs.copy(phase = Phase.DONE, remainingMs = 0), setIndex, true)
            }
        }
        return Triple(hs.copy(phase = phase, remainingMs = remaining, repIndex = rep), setIndex, false)
    }

    // ---- POR_SERIE: ronda izq completa → ronda der completa, por set ----
    private fun tickPorSerie(deltaMs: Long) {
        val s = _state.value
        val activeIsLeft = currentSerieHand == Hand.LEFT
        val activeState = if (activeIsLeft) s.left else s.right
        val (advanced, _, roundDone) = advanceSoloCycle(activeState, deltaMs, s.setIndex)

        if (!roundDone) {
            _state.value = if (activeIsLeft) s.copy(left = advanced, activeHand = if (advanced.phase == Phase.WORK) Hand.LEFT else Hand.NONE)
            else s.copy(right = advanced, activeHand = if (advanced.phase == Phase.WORK) Hand.RIGHT else Hand.NONE)
            return
        }

        if (currentSerieHand == Hand.LEFT) {
            // Ronda izquierda del set terminada → empieza la ronda derecha del MISMO set.
            currentSerieHand = Hand.RIGHT
            val right = HandState(Hand.RIGHT, Phase.WORK, workMs(s.setIndex), 0)
            _state.value = s.copy(left = advanced.copy(phase = Phase.DONE), right = right, activeHand = Hand.RIGHT)
        } else {
            // Las dos rondas del set completas → siguiente set, otra vez izquierda primero.
            currentSerieHand = Hand.LEFT
            val nextSet = s.setIndex + 1
            if (nextSet >= workout.sets.size) {
                _state.value = s.copy(finished = true, activeHand = Hand.NONE)
            } else {
                val left = HandState(Hand.LEFT, Phase.WORK, workMs(nextSet), 0)
                _state.value = s.copy(setIndex = nextSet, left = left, right = HandState(Hand.RIGHT, Phase.WAITING), activeHand = Hand.LEFT)
            }
        }
    }

    // ---- POR_REP: máquina de estados de exclusión mutua (sección 4.3) ----
    private fun tickPorRep(deltaMs: Long) {
        val s = _state.value
        val set = workout.sets.getOrNull(s.setIndex) ?: run { _state.value = s.copy(finished = true); return }
        var left = s.left
        var right = s.right
        var active = s.activeHand
        // Mano que ACABA de terminar de tirar en este mismo tick: su descanso
        // arranca fresco (ya "gastó" el delta de este tick en terminar de
        // trabajar) — no hay que restarle el delta otra vez como descanso.
        var justEndedWork = Hand.NONE

        // 1. Avanza el contador de trabajo de la mano activa (si hay una tirando).
        if (active == Hand.LEFT) left = tickWorkingHand(left, deltaMs, s.setIndex)
        else if (active == Hand.RIGHT) right = tickWorkingHand(right, deltaMs, s.setIndex)

        // Si la mano activa acaba de terminar su tramo de trabajo: libera el
        // turno y su descanso arranca (puede correr en paralelo al trabajo
        // de la otra mano, que ya se decide en el paso 3).
        if (left.phase == Phase.REST_JUST_ENDED_WORK) { left = left.copy(phase = Phase.REST); active = Hand.NONE; justEndedWork = Hand.LEFT }
        if (right.phase == Phase.REST_JUST_ENDED_WORK) { right = right.copy(phase = Phase.REST); active = Hand.NONE; justEndedWork = Hand.RIGHT }

        // 2. El descanso de una mano avanza SIEMPRE, tire o no la otra mano en
        //    paralelo (es justo lo que permite alternar sin esperas tontas) —
        //    salvo la mano que acaba de empezar a descansar en este mismo tick.
        if (left.phase == Phase.REST && justEndedWork != Hand.LEFT) left = tickRestingHand(left, deltaMs)
        if (right.phase == Phase.REST && justEndedWork != Hand.RIGHT) right = tickRestingHand(right, deltaMs)

        // 3. ¿Puede empezar a tirar la mano a la que le toca turno? Necesita
        //    (a) que le toque el turno fijo Y (b) que su propio descanso haya
        //    terminado (WAITING = descanso a 0, o REST con remaining<=0).
        if (active == Hand.NONE) {
            val candidate = nextTurnHand
            val candidateState = if (candidate == Hand.LEFT) left else right
            val ownRestDone = candidateState.phase == Phase.WAITING ||
                (candidateState.phase == Phase.REST && candidateState.remainingMs <= 0)
            // "reps" del set es el TOTAL de repeticiones alternadas entre las
            // dos manos (rep 1 izq, rep 2 der, rep 3 izq...), no por mano.
            val repsLeftInSet = set.reps - (left.repIndex + right.repIndex)
            if (ownRestDone && repsLeftInSet > 0) {
                val started = HandState(candidate, Phase.WORK, workMs(s.setIndex), candidateState.repIndex)
                if (candidate == Hand.LEFT) left = started else right = started
                active = candidate
                nextTurnHand = other(candidate)
            } else if (ownRestDone) {
                // El set ya completó todas sus reps; queda a la espera del cambio de set.
                if (candidate == Hand.LEFT) left = left.copy(phase = Phase.WAITING)
                else right = right.copy(phase = Phase.WAITING)
            }
        }

        // 4. ¿Set completo? (entre las dos manos han hecho todas las reps del set,
        //    repartidas por turnos, y ninguna está tirando ahora mismo).
        val totalDone = left.repIndex + right.repIndex
        if (totalDone >= set.reps && active == Hand.NONE) {
            val nextSet = s.setIndex + 1
            if (nextSet >= workout.sets.size) {
                _state.value = s.copy(finished = true, activeHand = Hand.NONE)
                return
            }
            nextTurnHand = Hand.LEFT
            left = HandState(Hand.LEFT, Phase.WORK, workMs(nextSet), 0)
            right = HandState(Hand.RIGHT, Phase.WAITING, 0, 0)
            _state.value = s.copy(setIndex = nextSet, left = left, right = right, activeHand = Hand.LEFT)
            return
        }

        _state.value = s.copy(left = left, right = right, activeHand = active)
    }

    /** Cuenta atrás del tramo de trabajo de la mano activa. Al llegar a 0,
     *  pasa a [Phase.REST_JUST_ENDED_WORK] como señal de "termina este
     *  tick" para que el llamador libere el turno. */
    private fun tickWorkingHand(hs: HandState, deltaMs: Long, setIndex: Int): HandState {
        val remaining = hs.remainingMs - deltaMs
        if (remaining > 0) return hs.copy(remainingMs = remaining)
        return hs.copy(phase = Phase.REST_JUST_ENDED_WORK, remainingMs = restMs(setIndex), repIndex = hs.repIndex + 1)
    }

    private fun tickRestingHand(hs: HandState, deltaMs: Long): HandState {
        val remaining = (hs.remainingMs - deltaMs).coerceAtLeast(0)
        return hs.copy(remainingMs = remaining, phase = if (remaining <= 0) Phase.WAITING else Phase.REST)
    }

    private fun other(h: Hand): Hand = if (h == Hand.LEFT) Hand.RIGHT else Hand.LEFT
}
