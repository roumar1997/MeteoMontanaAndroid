package com.meteomontana.android.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Registro LOCAL de las vías marcadas como hechas (clave "escuelaId|nombreVía").
 *
 * Existe para que el ✓ y, sobre todo, la prevención de duplicados funcionen
 * **sin conexión**: el diario vive en el servidor y offline no se puede
 * consultar, así que sin este registro la app no sabría qué ya marcaste y
 * dejaría volver a sumarlo. Persiste en SharedPreferences.
 */
class JournalDoneStore(context: Context, prefsName: String = "journal_done") {

    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val _keys = MutableStateFlow(load())
    val keys: StateFlow<Set<String>> = _keys

    private fun load(): Set<String> = prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    private fun persist(set: Set<String>) {
        prefs.edit().putStringSet(KEY, set).apply()
        _keys.value = set
    }

    fun add(key: String) { if (key !in _keys.value) persist(_keys.value + key) }
    fun remove(key: String) { if (key in _keys.value) persist(_keys.value - key) }

    /** Sincroniza con la verdad del servidor (al cargar el diario online),
     *  conservando además las marcadas offline que aún no se han subido. */
    fun sync(serverKeys: Set<String>, pendingKeys: Set<String>) {
        persist(serverKeys + pendingKeys)
    }

    private companion object { const val KEY = "done_via_keys" }
}

/**
 * Registro LOCAL de las vías marcadas como PROYECTO (las estás probando, aún
 * no te han salido). Mismo mecanismo que [JournalDoneStore] (necesario para
 * que funcione sin conexión), en un fichero de preferencias aparte para no
 * mezclar las claves con las de "hecha".
 */
class JournalProjectStore(context: Context) {
    private val delegate = JournalDoneStore(context, "journal_project")
    val keys: StateFlow<Set<String>> = delegate.keys
    fun add(key: String) = delegate.add(key)
    fun remove(key: String) = delegate.remove(key)
    fun sync(serverKeys: Set<String>, pendingKeys: Set<String>) = delegate.sync(serverKeys, pendingKeys)
}
