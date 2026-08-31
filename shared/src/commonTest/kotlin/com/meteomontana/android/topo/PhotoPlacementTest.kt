package com.meteomontana.android.topo

import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.util.PhotoPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Colocar una foto en su escuela a partir de dónde se hizo.
 *
 * Los números de estos tests son metros reales, no valores redondos elegidos
 * para que salga verde: el GPS de un móvil en un canchal se equivoca entre 10 y
 * 30 metros, y de ahí salen los dos radios (5 km para la escuela, 25 m para
 * avisar de que la piedra quizá ya existe).
 */
class PhotoPlacementTest {

    private fun escuela(id: String, lat: Double, lon: Double) =
        School(id, id, null, null, null, null, lat, lon, null)

    private fun piedra(id: String, lat: Double, lon: Double, tipo: String = "BLOCK") =
        Block(id, "s1", tipo, id, lat, lon, null, null, "uid", "2026-01-01", emptyList())

    // Zarzalejo y La Pedriza, coordenadas reales aproximadas.
    private val zarzalejo = escuela("zarzalejo", 40.5539, -4.1836)
    private val pedriza = escuela("pedriza", 40.7500, -3.8800)

    @Test
    fun laFotoCaeEnLaEscuelaMasCercana() {
        // Un punto a unos 300 m de Zarzalejo.
        val r = PhotoPlacement.schoolFor(40.5565, -4.1836, listOf(pedriza, zarzalejo))
        assertTrue(r is PhotoPlacement.Result.Found, "esperaba encontrar escuela: $r")
        assertEquals("zarzalejo", r.school.id)
        assertTrue(r.distanceKm < 0.5, "distancia rara: ${r.distanceKm}")
    }

    @Test
    fun sinNingunaEscuelaCercaSeDiceCuantoFalta() {
        // Foto en mitad del Atlántico: nada cerca, pero se informa de lo lejos
        // que quedó lo más próximo para que el usuario entienda por qué.
        val r = PhotoPlacement.schoolFor(35.0, -20.0, listOf(pedriza, zarzalejo))
        assertTrue(r is PhotoPlacement.Result.NoSchoolNearby, "$r")
        assertTrue((r.nearestKm ?: 0.0) > 100.0)
    }

    @Test
    fun elCatalogoVacioNoRevienta() {
        val r = PhotoPlacement.schoolFor(40.0, -4.0, emptyList())
        assertTrue(r is PhotoPlacement.Result.NoSchoolNearby)
        assertNull(r.nearestKm)
    }

    @Test
    fun justoEnElBordeDelRadioTodaviaCuenta() {
        // A 5 km exactos hacia el norte (1 grado de latitud ~ 111,32 km).
        val cincoKm = 5.0 / 111.32
        val r = PhotoPlacement.schoolFor(zarzalejo.lat + cincoKm * 0.999, zarzalejo.lon,
                                         listOf(zarzalejo))
        assertTrue(r is PhotoPlacement.Result.Found, "el borde debe entrar: $r")
    }

    @Test
    fun lasPiedrasCercanasSalenDeMasCercaAMasLejos() {
        val base = 40.5539 to -4.1836
        val metro = 1.0 / 111_320.0            // 1 metro en grados de latitud
        val cerca = piedra("cerca", base.first + metro * 5, base.second)
        val media = piedra("media", base.first + metro * 20, base.second)
        val lejos = piedra("lejos", base.first + metro * 80, base.second)

        val candidatas = PhotoPlacement.nearbyBlocks(base.first, base.second,
                                                     listOf(lejos, media, cerca))
        assertEquals(listOf("cerca", "media"), candidatas.map { it.id },
            "la de 80 m está fuera del radio de 25")
    }

    @Test
    fun parkingsYSectoresNoSonCandidatos() {
        // Solo se propone una PIEDRA: un parking o una zona nunca son "la misma".
        val base = 40.5539 to -4.1836
        val metro = 1.0 / 111_320.0
        val candidatas = PhotoPlacement.nearbyBlocks(
            base.first, base.second,
            listOf(piedra("p", base.first + metro * 3, base.second, "PARKING"),
                   piedra("z", base.first + metro * 4, base.second, "ZONE"),
                   piedra("b", base.first + metro * 6, base.second))
        )
        assertEquals(listOf("b"), candidatas.map { it.id })
    }

    @Test
    fun laVersionPlanaDiceLoMismo() {
        // Es la que usa iOS: tiene que coincidir con la sellada, o las dos apps
        // colocarian la foto en escuelas distintas.
        val escuelas = listOf(pedriza, zarzalejo)
        assertEquals("zarzalejo",
            PhotoPlacement.nearestSchoolWithin(40.5565, -4.1836, escuelas)?.id)
        assertNull(PhotoPlacement.nearestSchoolWithin(35.0, -20.0, escuelas))
        assertNull(PhotoPlacement.nearestSchoolWithin(40.0, -4.0, emptyList()))
        assertTrue((PhotoPlacement.nearestSchoolKm(35.0, -20.0, escuelas) ?: 0.0) > 100.0)
        assertNull(PhotoPlacement.nearestSchoolKm(40.0, -4.0, emptyList()))
    }

    @Test
    fun laParedMiraAlReVesQueLaCamara() {
        // Disparas hacia el norte → la pared que sale en la foto mira al sur.
        assertEquals("S", PhotoPlacement.aspectFromCameraDirection(0f))
        assertEquals("N", PhotoPlacement.aspectFromCameraDirection(180f))
        assertEquals("SO", PhotoPlacement.aspectFromCameraDirection(45f))
    }

    @Test
    fun sinDireccionEnLaFotoNoSeSugiereNada() {
        // Lo normal: la mayoría de fotos no traen el rumbo de la cámara.
        assertNull(PhotoPlacement.aspectFromCameraDirection(null))
    }
}
