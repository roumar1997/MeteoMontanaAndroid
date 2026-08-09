package com.meteomontana.android.domain.util

import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.School

/**
 * A qué escuela pertenece una foto, según dónde se hizo.
 *
 * La cámara guarda en la foto las coordenadas del sitio (EXIF). Con eso se
 * puede proponer una piedra sin tener que buscar la escuela a mano en la lista.
 *
 * **Los límites están elegidos con la precisión real del GPS en la cabeza**, no
 * al azar: en un canchal, entre árboles y bloques, el error típico va de 10 a
 * 30 metros. Por eso:
 * - la foto sirve para acertar la ESCUELA (kilómetros), no la piedra (metros);
 * - las piedras a menos de [RADIO_PIEDRA_M] se enseñan como "¿es una de estas?",
 *   porque a esa distancia el GPS no distingue.
 */
object PhotoPlacement {

    /** Más lejos que esto, no se da por buena ninguna escuela. */
    const val RADIO_ESCUELA_KM = 5.0

    /** Piedras a menos de esto: candidatas a ser la misma. */
    const val RADIO_PIEDRA_M = 25.0

    /** Qué se ha podido deducir de las coordenadas de la foto. */
    sealed class Result {
        /** La foto cae dentro del radio de una escuela. */
        data class Found(val school: School, val distanceKm: Double) : Result()

        /** Hay coordenadas, pero no hay ninguna escuela cerca. */
        data class NoSchoolNearby(val nearestKm: Double?) : Result()
    }

    /**
     * Escuela más cercana a un punto, si está a menos de [RADIO_ESCUELA_KM].
     *
     * Devuelve también la distancia porque la pantalla la enseña: "La Pedriza,
     * a 1,2 km" ayuda a ver de un vistazo si la foto se ubicó donde tocaba.
     */
    fun schoolFor(
        lat: Double,
        lon: Double,
        schools: List<School>,
        radiusKm: Double = RADIO_ESCUELA_KM
    ): Result {
        val cercana = schools.minByOrNull { Geo.haversineKm(lat, lon, it.lat, it.lon) }
            ?: return Result.NoSchoolNearby(null)
        val d = Geo.haversineKm(lat, lon, cercana.lat, cercana.lon)
        return if (d <= radiusKm) Result.Found(cercana, d) else Result.NoSchoolNearby(d)
    }

    /**
     * Versión plana de [schoolFor] para Swift: la escuela, o null.
     *
     * Existe porque una clase sellada cruza mal la frontera con Swift y aquí no
     * aporta nada: la pantalla de iOS solo necesita saber si hay escuela y, si
     * no la hay, a qué distancia quedó la más próxima ([nearestSchoolKm]).
     */
    fun nearestSchoolWithin(
        lat: Double,
        lon: Double,
        schools: List<School>,
        radiusKm: Double = RADIO_ESCUELA_KM
    ): School? = (schoolFor(lat, lon, schools, radiusKm) as? Result.Found)?.school

    /** Distancia a la escuela más cercana, haya entrado en el radio o no. */
    fun nearestSchoolKm(lat: Double, lon: Double, schools: List<School>): Double? =
        schools.minOfOrNull { Geo.haversineKm(lat, lon, it.lat, it.lon) }

    /**
     * Piedras que podrían ser la de la foto, de más cerca a más lejos.
     *
     * Sirve para avisar antes de crear una piedra repetida: si la foto cae
     * encima de una que ya existe, es más probable que sea una cara nueva de
     * esa que una piedra distinta.
     *
     * Solo se miran las piedras: un parking o un sector no son candidatos.
     */
    fun nearbyBlocks(
        lat: Double,
        lon: Double,
        blocks: List<Block>,
        radiusMeters: Double = RADIO_PIEDRA_M
    ): List<Block> = blocks
        .filter { it.type.equals("BLOCK", ignoreCase = true) }
        .map { it to Geo.haversineKm(lat, lon, it.lat, it.lon) * 1000.0 }
        .filter { (_, metros) -> metros <= radiusMeters }
        .sortedBy { (_, metros) -> metros }
        .map { (bloque, _) -> bloque }

    /**
     * Orientación que sugiere la dirección en la que apuntaba la cámara.
     *
     * Muchas cámaras guardan hacia dónde mirabas al disparar. Como la foto
     * apunta a la pared, **la pared mira justo al revés**: si disparaste hacia
     * el norte, la pared que sale en la foto es de orientación sur.
     *
     * Es una sugerencia, nunca un dato: el usuario la confirma. Devuelve null
     * si la foto no trae esa información, que es lo más común.
     */
    fun aspectFromCameraDirection(cameraDegrees: Float?): String? =
        cameraDegrees?.let { Aspect.fromDegrees(it + 180f) }
}
