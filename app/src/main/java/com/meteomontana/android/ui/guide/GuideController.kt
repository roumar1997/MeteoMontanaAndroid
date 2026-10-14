package com.meteomontana.android.ui.guide

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.meteomontana.android.domain.usecase.help.Guide

/**
 * Lo que la guía recuerda entre sesiones. Interfaz (no `SharedPreferences` a pelo) para poder probar el controlador
 * sin Android: el recorrido, el encadenado y el parpadeo son lógica con reglas, no pintura.
 */
interface GuidePrefs {
    fun bool(key: String, default: Boolean): Boolean
    fun setBool(key: String, value: Boolean)
    fun int(key: String, default: Int): Int
    fun setInt(key: String, value: Int)
    /** Borra todo lo guardado cuya clave empiece por alguno de [prefixes]. */
    fun removeWithPrefix(vararg prefixes: String)
}

class SharedGuidePrefs(context: Context) : GuidePrefs {
    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    override fun bool(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun setBool(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun int(key: String, default: Int) = prefs.getInt(key, default)
    override fun setInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    override fun removeWithPrefix(vararg prefixes: String) {
        val e = prefs.edit()
        prefs.all.keys.filter { k -> prefixes.any { k.startsWith(it) } }.forEach { e.remove(it) }
        e.apply()
    }

    companion object { const val NAME = "guides" }
}

/**
 * Guías por pantalla ("? AYUDA"): qué guía está abierta y en qué punto va. Espejo del `GuideStore` de iOS; el CONTENIDO
 * (qué se señala y qué se cuenta, ES/EN) viene de `GuideCatalog` en shared, igual que allí.
 *
 * - Una sola guía a la vez (la de la pantalla visible).
 * - Una pantalla atiende su guía (`detail`) y las de sus pestañas (`detail_map`, `detail_blocks`…).
 * - Una guía solo arranca si su pantalla está en pantalla ([registerHost]); si no, el llamante cae a la ayuda de texto.
 * - El "?" parpadea las primeras visitas y se apaga en cuanto se abre cualquier ayuda de esa pantalla.
 *
 * [guides] resuelve una clave a su guía en el idioma ACTUAL de la app (se llama en cada paso, así que cambiar el idioma
 * con una guía abierta la repinta en el idioma nuevo).
 */
class GuideController(
    private val prefs: GuidePrefs,
    private val guides: (String) -> Guide?
) {
    var activeKey by mutableStateOf<String?>(null)
        private set
    var index by mutableIntStateOf(0)
        private set

    /** Sube cada vez que se abre una ayuda: los "?" que parpadean lo observan y se apagan al momento. */
    var usedVersion by mutableIntStateOf(0)
        private set

    /** Rectángulo en pantalla (coordenadas de la raíz) de cada elemento señalable, por su `target`. */
    val targets = mutableStateMapOf<String, Rect>()

    private val hosts = mutableStateMapOf<String, Int>()
    private var chained: String? = null

    val guide: Guide? get() = activeKey?.let(guides)

    /** Interruptor general: apagado, ningún "?" parpadea y la guía de Escuelas no sale sola. Los "?" siguen abriendo. */
    var enabled: Boolean
        get() = prefs.bool(KEY_ENABLED, true)
        set(value) = prefs.setBool(KEY_ENABLED, value)

    fun registerHost(key: String) { hosts[key] = (hosts[key] ?: 0) + 1 }
    fun unregisterHost(key: String) {
        val n = (hosts[key] ?: 0) - 1
        if (n <= 0) hosts.remove(key) else hosts[key] = n
    }

    /** ¿La pantalla `host` es la dueña de la guía `active`? (`detail` atiende `detail` y `detail_*`). */
    fun isMine(host: String, active: String?): Boolean =
        active != null && (active == host || active.startsWith("${host}_"))

    private fun hasHost(key: String) = hosts.keys.any { isMine(it, key) }

    fun setTarget(id: String, bounds: Rect) { if (targets[id] != bounds) targets[id] = bounds }
    fun clearTarget(id: String) { targets.remove(id) }

    /** Abre la guía de esa pantalla. `false` si no existe o su pantalla no está en pantalla (cae a la ayuda de texto). */
    fun start(key: String, then: String? = null): Boolean {
        if (guides(key) == null || !hasHost(key)) return false
        activeKey = key
        index = 0
        chained = then
        markUsed(key)
        return true
    }

    /**
     * UN solo "?" por pantalla: la primera vez cuenta la pantalla y, a continuación, la pestaña que se está viendo;
     * las siguientes, solo la de la pestaña.
     */
    fun startScreenAndTab(screen: String, tab: String): Boolean =
        if (isUsed(screen)) start(tab) else start(screen, then = tab)

    fun next() {
        val g = guide ?: return stop()
        if (index + 1 < g.steps.size) { index += 1; return }
        val follow = chained
        if (follow != null && start(follow)) return   // terminó la de la pantalla: sigue la de la pestaña
        stop()
    }

    fun stop() { activeKey = null; index = 0; chained = null }

    /** Se abrió alguna ayuda (guía o de texto) de esta pantalla: su "?" deja de parpadear. */
    fun markUsed(key: String) {
        prefs.setBool(usedKey(key), true)
        usedVersion += 1
    }

    fun isUsed(key: String) = prefs.bool(usedKey(key), false)

    /**
     * Se llama UNA vez cada vez que aparece el "?" de una pantalla: cuenta la visita y dice si toca parpadear
     * (solo las primeras [MAX_PULSE_VISITS] visitas y mientras nadie haya abierto la ayuda).
     */
    fun registerVisitAndShouldPulse(key: String): Boolean {
        val visits = prefs.int(visitsKey(key), 0) + 1
        prefs.setInt(visitsKey(key), visits)
        return enabled && !isUsed(key) && visits <= MAX_PULSE_VISITS
    }

    companion object {
        const val KEY_ENABLED = "guides_enabled"
        const val KEY_PENDING_SCHOOLS = "guide_pending_schools"
        const val MAX_PULSE_VISITS = 3

        fun usedKey(key: String) = "guide_used_$key"
        fun visitsKey(key: String) = "guide_visits_$key"

        /** "Volver a ver las pistas": olvida visitas y guías abiertas, deja la de Escuelas pendiente y reactiva las guías. */
        fun resetAll(prefs: GuidePrefs) {
            prefs.removeWithPrefix("guide_visits_", "guide_used_")
            prefs.setBool(KEY_PENDING_SCHOOLS, true)
            prefs.setBool(KEY_ENABLED, true)
        }
    }
}
