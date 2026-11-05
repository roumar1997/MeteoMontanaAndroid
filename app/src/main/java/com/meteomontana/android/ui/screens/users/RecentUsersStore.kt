package com.meteomontana.android.ui.screens.users

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Persona abierta desde el buscador: lo justo para enseñarla en «Recientes» sin pedir nada al servidor. */
data class RecentUser(val uid: String, val name: String, val username: String?, val photoUrl: String?)

/**
 * Últimas personas abiertas desde el buscador de usuarios (máx. 6), guardadas en el móvil. Espejo de RecentUser
 * (UsersView.swift). Solo vive aquí lo que el usuario ya vio; no es un dato del servidor.
 */
@Singleton
class RecentUsersStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("recent_user_searches", Context.MODE_PRIVATE)

    fun load(): List<RecentUser> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            // isNull + getString, NO optString: con un null del JSON optString devuelve la CADENA «null».
            RecentUser(
                o.getString("uid"), o.getString("name"),
                if (o.isNull("username")) null else o.getString("username"),
                if (o.isNull("photoUrl")) null else o.getString("photoUrl")
            )
        }
    }.getOrDefault(emptyList())

    /** Pone a [user] el primero de la lista (sin repetirlo) y devuelve la lista nueva. */
    fun remember(user: RecentUser): List<RecentUser> {
        val list = (listOf(user) + load().filter { it.uid != user.uid }).take(LIMIT)
        val arr = JSONArray()
        list.forEach { u ->
            arr.put(JSONObject().put("uid", u.uid).put("name", u.name)
                .put("username", u.username ?: JSONObject.NULL)
                .put("photoUrl", u.photoUrl ?: JSONObject.NULL))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
        return list
    }

    private companion object {
        const val KEY = "recents_v1"
        const val LIMIT = 6
    }
}
