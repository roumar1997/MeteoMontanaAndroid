package com.meteomontana.android.gym

import com.meteomontana.android.domain.model.gym.GymClusterer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GymClustererTest {

    private val near = listOf(
        gym(1, lat = 40.4168, lon = -3.7038),
        gym(2, lat = 40.4200, lon = -3.7000),
        gym(3, lat = 40.4100, lon = -3.7100)
    )
    private val far = gym(4, lat = 41.3851, lon = 2.1734)

    @Test
    fun `con zoom lejano los cercanos se juntan y el lejano queda suelto`() {
        val items = GymClusterer.cluster(near + far, zoom = 6.0)
        assertEquals(2, items.size)
        val cluster = items.single { it.isCluster }
        assertEquals(3, cluster.count)
        assertEquals(listOf(1L, 2, 3), cluster.gyms.map { it.id })
        assertEquals(listOf(4L), items.single { !it.isCluster }.gyms.map { it.id })
    }

    @Test
    fun `el centro del grupo es la media`() {
        val c = GymClusterer.cluster(near, zoom = 6.0).single()
        assertEquals(near.map { it.lat }.average(), c.lat, 1e-9)
        assertEquals(near.map { it.lon }.average(), c.lon, 1e-9)
    }

    @Test
    fun `con zoom cercano no se agrupa nada`() {
        val items = GymClusterer.cluster(near + far, zoom = GymClusterer.MAX_CLUSTER_ZOOM)
        assertEquals(4, items.size)
        assertTrue(items.none { it.isCluster })
    }

    @Test
    fun `al acercar se separan poco a poco`() {
        val lejos = GymClusterer.cluster(near, zoom = 4.0).size
        val medio = GymClusterer.cluster(near, zoom = 10.0).size
        assertTrue(lejos <= medio)
        assertTrue(medio <= GymClusterer.cluster(near, zoom = 13.0).size)
    }

    @Test
    fun `es determinista y no depende del orden de entrada`() {
        val a = GymClusterer.cluster(near + far, zoom = 7.0)
        val b = GymClusterer.cluster((near + far).reversed(), zoom = 7.0)
        assertEquals(a, b)
        assertEquals(a.map { it.id }, a.map { it.id }.sorted())
    }

    @Test
    fun `lista vacia`() {
        assertTrue(GymClusterer.cluster(emptyList(), 5.0).isEmpty())
    }

    @Test
    fun `el tamaño de celda se divide a la mitad con cada nivel de zoom`() {
        assertEquals(GymClusterer.cellSizeDegrees(5.0) / 2, GymClusterer.cellSizeDegrees(6.0), 1e-12)
    }
}
