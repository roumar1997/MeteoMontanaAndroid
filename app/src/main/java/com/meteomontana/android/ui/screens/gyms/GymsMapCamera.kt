package com.meteomontana.android.ui.screens.gyms

import android.content.Context
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymClusterer
import com.meteomontana.android.domain.model.gym.GymMapItem
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap

internal const val FIRST_ZOOM_WITH_LOCATION = 9.5
internal const val SPAIN_ZOOM = 5.0
private const val SINGLE_GYM_ZOOM = 13.5

/** Cámara inicial: la que había, o centrada en el usuario, o España. `true` = ya está colocada (no encuadrar). */
internal fun placeInitialCamera(map: MapLibreMap, saved: CameraPosition?, userLat: Double?, userLon: Double?): Boolean {
    when {
        saved != null -> map.cameraPosition = saved
        userLat != null && userLon != null ->
            map.cameraPosition = CameraPosition.Builder().target(LatLng(userLat, userLon)).zoom(FIRST_ZOOM_WITH_LOCATION).build()
        else -> {
            map.cameraPosition = CameraPosition.Builder().target(LatLng(40.4, -3.7)).zoom(SPAIN_ZOOM).build()
            return false
        }
    }
    return true
}

/** Encuadra las fichas visibles (una sola: zoom directo, los bounds de un punto llevan al mundo entero). */
internal fun fitTo(ctx: Context, map: MapLibreMap, gyms: List<Gym>) {
    if (gyms.isEmpty()) return
    runCatching {
        if (gyms.size == 1) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(gyms[0].lat, gyms[0].lon), SINGLE_GYM_ZOOM), 400)
        } else {
            val b = LatLngBounds.Builder()
            gyms.forEach { b.include(LatLng(it.lat, it.lon)) }
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), px(ctx, 48)), 400)
        }
    }
}

/** Tocar un grupo acerca la cámara a sus miembros; si están todos en el mismo punto, zoom directo por encima del grupo. */
internal fun zoomIntoCluster(ctx: Context, map: MapLibreMap, item: GymMapItem) {
    val lats = item.gyms.map { it.lat }
    val lons = item.gyms.map { it.lon }
    val tiny = (lats.max() - lats.min()) < 1e-4 && (lons.max() - lons.min()) < 1e-4
    runCatching {
        if (tiny) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(item.lat, item.lon), GymClusterer.MAX_CLUSTER_ZOOM + 1), 350)
        } else {
            val b = LatLngBounds.Builder()
            item.gyms.forEach { b.include(LatLng(it.lat, it.lon)) }
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), px(ctx, 72)), 350)
        }
    }
}

private fun px(ctx: Context, dp: Int) = (dp * ctx.resources.displayMetrics.density).toInt()
