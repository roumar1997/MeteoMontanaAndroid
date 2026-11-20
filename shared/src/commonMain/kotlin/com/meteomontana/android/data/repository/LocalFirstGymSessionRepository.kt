package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorGymSessionApi
import com.meteomontana.android.data.api.dto.GymAscentStatsDto
import com.meteomontana.android.data.api.dto.GymAscentsRequestDto
import com.meteomontana.android.data.api.dto.GymSessionDto
import com.meteomontana.android.data.api.dto.toDomainOrNull
import com.meteomontana.android.data.api.dto.toDomainOrNull
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.data.api.dto.toDto
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
        try {
            api.checkInToday(gymId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { /* sin ruta o sin red: queda la copia local */ }
        return session
    }

    override suspend fun undoToday(gymId: Long) {
        val day = today()
        save(load().filterNot { it.gymId == gymId && it.date == day })
        try {
            api.undoToday(gymId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { /* idem */ }
    }

    override suspend fun refresh(): List<GymSession> {
        // Todo el historial (el perfil enseña totales de siempre), no solo el año en curso.
        val from = LocalDate(2020, 1, 1).toString()
        val remote = try {
            api.mine(from, today().toString()).mapNotNull { it.toDomainOrNull() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return load()
        }
        val local = load()
        // El servidor manda (tiene el id); lo apuntado en el movil que aun no ha subido no se pierde.
        val merged = remote.map { r ->
            val l = local.firstOrNull { it.gymId == r.gymId && it.date == r.date }
            if (l != null && r.ascents.isEmpty() && l.ascents.isNotEmpty()) r.copy(ascents = l.ascents) else r
        } + local.filter { l -> remote.none { it.gymId == l.gymId && it.date == l.date } }
        save(merged)
        return merged
    }

    override suspend fun saveAscents(gymId: Long, date: LocalDate, ascents: List<GymAscent>): GymSession {
        val clean = ascents.filter { it.count > 0 }
        val existing = load().firstOrNull { it.gymId == gymId && it.date == date } ?: GymSession(gymId, date)
        // Editar un entreno hasta dejarlo a cero BORRA la sesion (en el movil y en el servidor).
        if (clean.isEmpty() && existing.ascents.isNotEmpty()) {
            save(load().filterNot { it.gymId == gymId && it.date == date })
            try {
                if (existing.id != 0L) api.deleteSession(existing.id) else if (date == today()) api.undoToday(gymId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { /* sin red: al refrescar el servidor la devolvera vacia, pero ya no cuenta como entreno con lo hecho */ }
            return GymSession(gymId, date)
        }
        var updated = existing.copy(ascents = clean)
        save(load().filterNot { it.gymId == gymId && it.date == date } + updated)
        try {
            // Una sesion de hoy que solo existe en el movil se da de alta primero (idempotente) para tener su id.
            val id = existing.id.takeIf { it != 0L } ?: if (date == today()) api.checkInToday(gymId).id else 0L
            if (id != 0L) {
                val remote = api.putAscents(id, GymAscentsRequestDto(clean.map { it.toDto() })).toDomainOrNull()
                updated = updated.copy(id = id, ascents = remote?.ascents ?: clean)
                save(load().filterNot { it.gymId == gymId && it.date == date } + updated)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { /* sin red o sin ruta: queda en el movil y se sube al refrescar */ }
        return updated
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
        val LIST = ListSerializer(GymSessionDto.serializer())
        val JSON = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    }
}
