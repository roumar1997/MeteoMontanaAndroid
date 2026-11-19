package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorGymSessionApi
import com.meteomontana.android.data.api.dto.GymSessionDto
import com.meteomontana.android.data.api.dto.toDomainOrNull
import com.meteomontana.android.data.api.dto.toDto
import com.meteomontana.android.domain.model.gym.GymSession
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
            api.checkInToday(gymId, source.name)
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
        val since = LocalDate(today().year, 1, 1).toString()
        val remote = try {
            api.mine(since).mapNotNull { it.toDomainOrNull() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return load()
        }
        val merged = (load() + remote).distinctBy { it.gymId to it.date }
        save(merged)
        return merged
    }

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
