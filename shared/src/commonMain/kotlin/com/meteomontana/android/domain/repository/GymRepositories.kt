package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.SchoolPresence
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymAdminEdit
import com.meteomontana.android.domain.model.gym.GymAscent
import com.meteomontana.android.domain.model.gym.GymAscentStats
import com.meteomontana.android.domain.model.gym.GymCorrection
import com.meteomontana.android.domain.model.gym.GymNear
import com.meteomontana.android.domain.model.gym.GymReport
import com.meteomontana.android.domain.model.gym.GymSession
import com.meteomontana.android.domain.model.gym.GymSessionSource
import com.meteomontana.android.domain.model.gym.GymTrainingPeriod
import com.meteomontana.android.domain.model.gym.NewGymDraft

/** Copia local del catalogo de rocodromos (con el ETag con el que se descargo). Funciona sin conexion. */
data class GymCatalogSnapshot(val gyms: List<Gym>, val etag: String?)

interface GymCatalogCache {
    /** null si no hay nada guardado (o esta ilegible). */
    fun load(): GymCatalogSnapshot?
    fun save(snapshot: GymCatalogSnapshot)
}

/** Puerto del catalogo. Los use cases dependen de esto, nunca del `KtorGymApi`. */
interface GymRepository {
    /** Lo que haya en la copia local, al instante (vacio si nunca se ha descargado). */
    fun cachedGyms(): List<Gym>

    /**
     * Catalogo entero. Pregunta al servidor con el ETag; en 304 o sin red usa la copia local.
     * Lanza solo si no hay red Y no hay copia.
     */
    suspend fun getGyms(): List<Gym>

    suspend fun getGym(id: Long): Gym

    suspend fun findNear(lat: Double, lon: Double, radiusKm: Double, limit: Int): List<GymNear>
}

interface GymFavoriteRepository {
    suspend fun getFavoriteIds(): Set<Long>
    suspend fun setFavorite(gymId: Long, favorite: Boolean)
}

interface GymReportRepository {
    suspend fun submitCorrection(correction: GymCorrection): GymReport
    suspend fun submitNewGym(draft: NewGymDraft): GymReport
    suspend fun uploadPhoto(bytes: ByteArray): String
}

/** "Quien va hoy" en un rocodromo; devuelve el mismo modelo que la presencia de escuelas. */
interface GymPresenceRepository {
    suspend fun getActivePresence(gymId: Long): List<SchoolPresence>
    suspend fun markPresence(gymId: Long): SchoolPresence
    suspend fun clearPresence(gymId: Long)
}

/** Entrenos del usuario en rocodromos (v1.1). Primero en el movil; el servidor se entera cuando puede. */
interface GymSessionRepository {
    fun today(): kotlinx.datetime.LocalDate
    fun sessions(): List<GymSession>
    suspend fun checkInToday(gymId: Long, source: GymSessionSource): GymSession
    suspend fun undoToday(gymId: Long)
    suspend fun refresh(): List<GymSession>

    /** Sube lo apuntado sin red (altas, cambios y borrados de entrenos). No lanza por la red; [refresh] ya lo hace antes. */
    suspend fun syncPending()

    /** Apunta lo hecho (por color o grado) en la sesion de ese dia; SUSTITUYE lo anterior. Primero local, luego el servidor. */
    /** [boardProblems] null = no tocar los nombres ya guardados (solo se podan los que ya no caben en los recuentos). */
    suspend fun saveAscents(gymId: Long, date: kotlinx.datetime.LocalDate, ascents: List<GymAscent>, boardProblems: List<com.meteomontana.android.domain.model.gym.BoardProblem>? = null): GymSession

    /** Todo el historial de [uidOrUsername] según el servidor; null si no se puede ver (privado) o no hay red. */
    suspend fun summary(uidOrUsername: String): com.meteomontana.android.domain.model.gym.GymSummary?

    /** Última copia buena del resumen de [uidOrUsername] guardada en el móvil (sin red); null si nunca se guardó. */
    fun cachedSummary(uidOrUsername: String): com.meteomontana.android.domain.model.gym.GymSummary?

    /** Mi rocódromo habitual según el servidor; null si no tiene. Lanza si no hay red. */
    suspend fun myHomeGym(): com.meteomontana.android.domain.model.gym.HomeGym?

    /** Fija (o quita, con null) mi rocódromo habitual en el servidor. Lanza si no hay red: quien llama conserva la caché local. */
    suspend fun setHomeGym(gymId: Long?): com.meteomontana.android.domain.model.gym.HomeGym?

    /** Mi orden de categorias de Ascensiones segun el servidor; null si no lo ha elegido. Lanza si no hay red. */
    suspend fun myAscentOrder(): String?

    /** Guarda mi orden de categorias de Ascensiones en el servidor. Lanza si no hay red: quien llama conserva la cache local. */
    suspend fun setAscentOrder(order: String): String?

    /** Mi orden de rocodromos segun el servidor (ids «5,3,9»); null si no lo ha elegido. Lanza si no hay red. */
    suspend fun myGymOrder(): String?

    /** Guarda mi orden de rocodromos en el servidor. Lanza si no hay red: quien llama conserva la cache local. */
    suspend fun setGymOrder(order: String): String?

    /** Ascensiones de un periodo: las del servidor y, si no hay red o ruta, las calculadas con las sesiones del movil. */
    suspend fun ascentStats(uid: String, period: GymTrainingPeriod, date: kotlinx.datetime.LocalDate, gymId: Long?): GymAscentStats
}

/** Revision de reportes y edicion de fichas por un admin (solo iOS; Android no tiene panel de admin). */
interface GymAdminRepository {
    suspend fun pendingReports(): List<GymReport>
    suspend fun applyReport(id: Long): GymReport
    suspend fun rejectReport(id: Long): GymReport
    suspend fun editGym(id: Long, edit: GymAdminEdit): Gym
}
