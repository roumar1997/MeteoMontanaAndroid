package com.meteomontana.android.ui.screens.profile

import android.content.Context
import com.meteomontana.android.data.local.SharedPrefsKeyValueStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Un orden que elige la persona (categorias de Ascensiones, rocodromos): cache local y marca de «pendiente de subir» por si
 * se cambio sin red. La verdad vive en el servidor.
 */
open class OrderPreference(context: Context, private val key: String) {
    private val store = SharedPrefsKeyValueStore(context, "cumbre_profile_prefs")

    fun get(): String? = store.getString(key)?.takeIf { it.isNotBlank() }

    fun set(order: String, pending: Boolean) {
        store.putString(key, order)
        store.putString(key + "_pending", if (pending) "1" else "")
    }

    fun isPending(): Boolean = store.getString(key + "_pending") == "1"

    /**
     * Propio: el servidor manda (se guarda en el movil); si no tiene nada, o hay un cambio sin subir, sube el del movil
     * con [upload]. Devuelve el valor que queda vigente.
     */
    suspend fun sync(server: String?, upload: suspend (String) -> Unit): String? {
        val local = get()
        if (local != null && (server == null || isPending())) {
            if (runCatching { upload(local) }.isSuccess) set(local, pending = false)
            return local
        }
        if (server != null) set(server, pending = false)
        return server ?: local
    }

    /** Guarda [order] ya y lo sube con [upload]; si falla (sin red) queda «pendiente» para subirlo al abrir el perfil. */
    suspend fun save(order: String, upload: suspend (String) -> Unit) {
        set(order, pending = true)
        if (runCatching { upload(order) }.isSuccess) set(order, pending = false)
    }
}

/** Orden de las categorias de Ascensiones (clave de siempre: asi no se pierde lo ya elegido); servidor `me/ascent-order`. */
class AscentOrderPreference @Inject constructor(@ApplicationContext context: Context) : OrderPreference(context, "ascent_tab_order")

/** Orden de los rocodromos (ids «5,3,9»); servidor `me/gym-order`. */
class GymOrderPreference @Inject constructor(@ApplicationContext context: Context) : OrderPreference(context, "gym_order")

/** Tableros (Kilter, Moon...) de la pestaña Boards que la persona dejo plegados; solo en el movil. */
class BoardsCollapsePreference @Inject constructor(@ApplicationContext context: Context) {
    private val store = SharedPrefsKeyValueStore(context, "cumbre_profile_prefs")

    fun get(): Set<String> = store.getString(KEY).orEmpty().split(",").filter { it.isNotBlank() }.toSet()

    fun set(collapsed: Set<String>) = store.putString(KEY, collapsed.joinToString(","))

    private companion object { const val KEY = "boards_collapsed" }
}
