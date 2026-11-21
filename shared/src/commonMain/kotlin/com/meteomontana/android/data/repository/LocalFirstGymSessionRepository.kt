package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorGymSessionApi
import com.meteomontana.android.data.api.dto.GymAscentStatsDto
import com.meteomontana.android.data.api.dto.GymAscentsRequestDto
import com.meteomontana.android.data.api.dto.GymSessionDto
import com.meteomontana.android.data.api.dto.toDomainOrNull
import com.meteomontana.android.data.api.dto.toDomainOrNull
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.data.api.dto.toDto
import com.meteomontana.android.domain.model.gym.BoardProblem
import com.meteomontana.android.domain.model.gym.BoardProblems
import com.meteomontana.android.domain.model.gym.GymAscent
import com.meteomontana.android.domain.model.gym.GymAscentStats
import com.meteomontana.android.domain.model.gym.GymSession
import com.meteomontana.android.domain.model.gym.GymTraining
import com.meteomontana.android.domain.model.gym.GymTrainingPeriod
import com.meteomontana.android.domain.model.gym.GymSessionSource
import com.meteomontana.android.domain.port.KeyValueStore
import com.meteomontana.android.domain.repository.GymSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Entrenos primero en el movil: apuntar o quitar funciona siempre (sin red y mientras el servidor no tenga las
 * rutas) y el servidor se avisa en segundo plano. [refresh] une lo del servidor con lo local sin duplicar.
 * Nunca lanza por culpa de la red: la tarjeta del perfil tiene que verse aunque no haya servidor.
 */
class LocalFirstGymSessionRepository(
    private val api: KtorGymSessionApi,
    private val store: KeyValueStore,
    private val clock: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
) : GymSessionRepository {

    override fun today(): LocalDate = clock()

    override fun sessions(): List<GymSession> = load()

    override suspend fun checkInToday(gymId: Long, source: GymSessionSource): GymSession {
        val session = GymSession(gymId, today(), source)
        save(load().filterNot { it.gymId == gymId && it.date == session.date } + session)
        mark(gymId, session.date, UPSERT)
        try {
            api.checkInToday(gymId)
            clearMark(gymId, session.date)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { /* sin ruta o sin red: queda la copia local, pendiente de subir */ }
        return session
    }

    override suspend fun undoToday(gymId: Long) {
        val day = today()
        val id = load().firstOrNull { it.gymId == gymId && it.date == day }?.id ?: 0L
        save(load().filterNot { it.gymId == gymId && it.date == day })
        mark(gymId, day, DELETE + id)
        try {
            api.undoToday(gymId)
            clearMark(gymId, day)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { /* idem */ }
    }

    override suspend fun refresh(): List<GymSession> {
        // Todo el historial (el perfil enseña totales de siempre), no solo el año en curso.
        val from = LocalDate(2020, 1, 1).toString()
        syncPending()
        val remote = try {
            api.mine(from, today().toString()).mapNotNull { it.toDomainOrNull() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return load()
        }
        val local = load()
        val pending = pending()
        // El servidor manda (tiene el id); lo apuntado en el movil que aun no ha subido (pendiente) manda sobre el servidor.
        val merged = remote.filterNot { pending[keyOf(it.gymId, it.date)]?.startsWith(DELETE) == true }.map { r ->
            val l = local.firstOrNull { it.gymId == r.gymId && it.date == r.date }
            val localWins = l != null && (pending[keyOf(r.gymId, r.date)] == UPSERT || (r.ascents.isEmpty() && l.ascents.isNotEmpty()))
            if (l != null && localWins) r.copy(ascents = l.ascents, boardProblems = l.boardProblems) else r
        } + local.filter { l -> remote.none { it.gymId == l.gymId && it.date == l.date } }
        save(merged)
        return merged
    }

    override suspend fun saveAscents(gymId: Long, date: LocalDate, ascents: List<GymAscent>, boardProblems: List<BoardProblem>?): GymSession {
        val clean = ascents.filter { it.count > 0 }
        val existing = load().firstOrNull { it.gymId == gymId && it.date == date } ?: GymSession(gymId, date)
        // Editar un entreno hasta dejarlo a cero BORRA la sesion (en el movil y en el servidor).
        if (clean.isEmpty() && existing.ascents.isNotEmpty()) {
            save(load().filterNot { it.gymId == gymId && it.date == date })
            mark(gymId, date, DELETE + existing.id)
            try {
                deleteRemote(gymId, date, existing.id)
                clearMark(gymId, date)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { /* sin red: el borrado queda pendiente y se sube al volver la conexion */ }
            return GymSession(gymId, date)
        }
        val named = BoardProblems.prune(boardProblems ?: existing.boardProblems, clean)
        var updated = existing.copy(ascents = clean, boardProblems = named)
        save(load().filterNot { it.gymId == gymId && it.date == date } + updated)
        // Pendiente de subir hasta que el servidor lo confirme: sin red se reintenta al abrir la app o al volver la conexion.
        mark(gymId, date, UPSERT)
        try {
            push(updated, sendNames = boardProblems != null)?.let { updated = it }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { /* sin red o sin ruta: queda en el movil y se sube despues */ }
        return updated
    }

    /** Sube lo que se apunto sin red (altas, cambios y borrados). Lo que falla sigue pendiente para la proxima vez. */
    override suspend fun syncPending() {
        for ((key, op) in pending()) {
            val parsed = parseKey(key)
            if (parsed == null) { clearMarkKey(key); continue }
            val (gymId, date) = parsed
            try {
                if (op.startsWith(DELETE)) {
                    deleteRemote(gymId, date, op.removePrefix(DELETE).toLongOrNull() ?: 0L)
                    clearMark(gymId, date)
                } else {
                    val s = load().firstOrNull { it.gymId == gymId && it.date == date }
                    if (s == null) clearMark(gymId, date) else push(s, sendNames = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { /* sigue pendiente */ }
        }
    }

    /** Da de alta la sesion en el servidor si hace falta (solo hoy tiene ruta) y sube lo hecho; null si aun no se puede. */
    private suspend fun push(s: GymSession, sendNames: Boolean): GymSession? {
        val id = s.id.takeIf { it != 0L } ?: if (s.date == today()) api.checkInToday(s.gymId).id else 0L
        if (id == 0L) return null
        val body = GymAscentsRequestDto(s.ascents.map { it.toDto() }, if (sendNames) s.boardProblems.map { it.toDto() } else null)
        val remote = api.putAscents(id, body).toDomainOrNull()
        val updated = s.copy(id = id, ascents = remote?.ascents ?: s.ascents, boardProblems = remote?.boardProblems ?: s.boardProblems)
        save(load().filterNot { it.gymId == s.gymId && it.date == s.date } + updated)
        clearMark(s.gymId, s.date)
        return updated
    }

    private suspend fun deleteRemote(gymId: Long, date: LocalDate, id: Long) {
        if (id != 0L) api.deleteSession(id) else if (date == today()) api.undoToday(gymId)
    }

    private fun keyOf(gymId: Long, date: LocalDate) = "$gymId|$date"

    private fun parseKey(key: String): Pair<Long, LocalDate>? = runCatching {
        key.substringBefore('|').toLong() to LocalDate.parse(key.substringAfter('|'))
    }.getOrNull()

    private fun pending(): Map<String, String> {
        val raw = runCatching { store.getString(PENDING_KEY) }.getOrNull() ?: return emptyMap()
        return runCatching { JSON.decodeFromString(PENDING, raw) }.getOrNull().orEmpty()
    }

    private fun mark(gymId: Long, date: LocalDate, op: String) = savePending(pending() + (keyOf(gymId, date) to op))

    private fun clearMark(gymId: Long, date: LocalDate) = clearMarkKey(keyOf(gymId, date))

    private fun clearMarkKey(key: String) = savePending(pending() - key)

    private fun savePending(m: Map<String, String>) {
        runCatching { store.putString(PENDING_KEY, JSON.encodeToString(PENDING, m)) }
    }

    override suspend fun summary(uidOrUsername: String): com.meteomontana.android.domain.model.gym.GymSummary? =
        try {
            api.summary(uidOrUsername).toDomain()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    override suspend fun myHomeGym(): com.meteomontana.android.domain.model.gym.HomeGym? =
        api.homeGym().homeGym?.toDomain()

    override suspend fun setHomeGym(gymId: Long?): com.meteomontana.android.domain.model.gym.HomeGym? =
        api.putHomeGym(gymId).homeGym?.toDomain()

    override suspend fun myAscentOrder(): String? = api.ascentOrder().order?.takeIf { it.isNotBlank() }

    override suspend fun setAscentOrder(order: String): String? = api.putAscentOrder(order).order?.takeIf { it.isNotBlank() }

    override suspend fun myGymOrder(): String? = api.gymOrder().order?.takeIf { it.isNotBlank() }

    override suspend fun setGymOrder(order: String): String? = api.putGymOrder(order).order?.takeIf { it.isNotBlank() }

    override suspend fun ascentStats(uid: String, period: GymTrainingPeriod, date: LocalDate, gymId: Long?): GymAscentStats =
        try {
            api.ascentStats(uid, period.name, date.toString(), gymId).toDomainOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: GymTraining.stats(load(), period, date, gymId)

    private fun load(): List<GymSession> {
        val raw = runCatching { store.getString(KEY) }.getOrNull() ?: return emptyList()
        return runCatching { JSON.decodeFromString(LIST, raw) }.getOrNull().orEmpty().mapNotNull { it.toDomainOrNull() }
    }

    private fun save(list: List<GymSession>) {
        runCatching { store.putString(KEY, JSON.encodeToString(LIST, list.map { it.toDto() })) }
    }

    private companion object {
        const val KEY = "gym_sessions_v1"
        /** Cambios hechos sin red que el servidor aun no ha confirmado: gymId|fecha -> U (alta o cambio) o D<id> (borrado). */
        const val PENDING_KEY = "gym_sessions_pending_v1"
        const val UPSERT = "U"
        const val DELETE = "D"
        val PENDING = MapSerializer(String.serializer(), String.serializer())
        val LIST = ListSerializer(GymSessionDto.serializer())
        val JSON = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    }
}
