package com.meteomontana.android.topo

import com.meteomontana.android.domain.util.SpainOnlyFeatures
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Radar y boletín de montaña son de AEMET, la agencia estatal española.
 *
 * Al abrir Francia y Portugal hay que esconderlos allí, y el caso peligroso es
 * el contrario: que se escondan en España por no venir el país. Una app nueva
 * hablando con un servidor viejo recibe escuelas SIN país, y entonces el
 * usuario español se quedaría sin radar y sin boletín sin motivo.
 */
class SpainOnlyFeaturesTest {

    @Test
    fun enEspanaSiSeEnsenan() {
        assertTrue(SpainOnlyFeatures.showsMountainBulletin("ES"))
        assertTrue(SpainOnlyFeatures.showsRadar("ES"))
    }

    @Test
    fun fueraDeEspanaNo() {
        assertFalse(SpainOnlyFeatures.showsMountainBulletin("FR"))
        assertFalse(SpainOnlyFeatures.showsMountainBulletin("PT"))
        assertFalse(SpainOnlyFeatures.showsRadar("FR"))
    }

    @Test
    fun sinPaisSeAsumeEspana() {
        // Servidor anterior al catálogo: todo lo que había era español. Si aquí
        // se respondiera que no, se le quitarían radar y boletín a todo el mundo
        // en cuanto la app se adelante al backend.
        assertTrue(SpainOnlyFeatures.showsMountainBulletin(null))
        assertTrue(SpainOnlyFeatures.showsMountainBulletin(""))
        assertTrue(SpainOnlyFeatures.showsMountainBulletin("   "))
    }

    @Test
    fun elCodigoSeAceptaComoVenga() {
        assertTrue(SpainOnlyFeatures.showsMountainBulletin(" es "))
        assertFalse(SpainOnlyFeatures.showsMountainBulletin(" fr "))
    }
}
