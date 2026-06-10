package com.meteomontana.android.data.stats

import kotlin.math.max
import kotlin.math.min

/**
 * Port simplificado del climbScore() de la PWA. Devuelve 0-100.
 * Fórmula: combinación de temperatura óptima, humedad/dewpoint, viento, lluvia.
 * Para histórico mensual usamos valores diarios (max/mean) — bastante para tendencia.
 */
fun climbScoreDaily(
    tempMax: Double,
    humidity: Double,
    windKmh: Double,
    precipMm: Double,
    precipProb: Int,
    rockType: String?
): Int {
    // Temperatura ideal 10-22°C
    val tempScore = when {
        tempMax in 12.0..22.0 -> 100.0
        tempMax in 5.0..28.0  -> 70.0
        tempMax in 0.0..32.0  -> 40.0
        else                  -> 10.0
    }

    // Humedad: bajo es mejor
    val humScore = when {
        humidity < 50  -> 100.0
        humidity < 65  -> 80.0
        humidity < 80  -> 50.0
        else           -> 20.0
    }

    // Viento: 5-20 km/h ideal
    val windScore = when {
        windKmh in 4.0..22.0  -> 100.0
        windKmh in 1.0..30.0  -> 70.0
        windKmh < 1.0          -> 50.0
        else                   -> 30.0
    }

    // Lluvia: 0mm = 100; cap depende de tipo de roca
    val rainCap = when (rockType?.uppercase()) {
        "CALIZA"   -> 5.0
        "GRANITO"  -> 8.0
        "ARENISCA" -> 3.0
        else       -> 6.0
    }
    val rainScore = if (precipMm <= 0.1 && precipProb < 20) 100.0
                    else (100.0 * (1.0 - (precipMm / rainCap).coerceIn(0.0, 1.0)))
                        .let { max(0.0, it - precipProb * 0.3) }

    // Peso: temp 30%, lluvia 30%, hum 20%, viento 20%
    val score = tempScore * 0.30 + rainScore * 0.30 + humScore * 0.20 + windScore * 0.20
    return min(100.0, max(0.0, score)).toInt()
}

fun scoreLabel(score: Int): String = when {
    score >= 80 -> "Excelente"
    score >= 65 -> "Bueno"
    score >= 50 -> "Regular"
    score >= 30 -> "Malo"
    else        -> "Muy malo"
}
