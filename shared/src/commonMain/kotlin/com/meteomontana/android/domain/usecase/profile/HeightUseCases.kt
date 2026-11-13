package com.meteomontana.android.domain.usecase.profile

import com.meteomontana.android.domain.model.HeightGroup
import com.meteomontana.android.domain.port.PendingHeightStore
import com.meteomontana.android.domain.repository.ProfileRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * Mi altura (más o menos de 1,70 m, o "prefiero no decirlo"); null si todavía no la he elegido.
 * Si hay una elegida sin conexión pendiente de subir, manda esa (es la más reciente).
 */
class GetMyHeightUseCase(
    private val repository: ProfileRepository,
    private val pending: PendingHeightStore? = null,
) {
    @Throws(Exception::class)
    suspend operator fun invoke(): HeightGroup? =
        pending?.pending() ?: repository.getMyHeight()
}

/**
 * Guarda mi altura (en mi mochilita). Devuelve el grupo que quedó guardado.
 * Sin conexión (o si el servidor falla) se guarda en local y se sube sola al volver la red,
 * como el ascenso offline (Álvaro, 2026-11-13: antes decía «sin conexión, inténtalo de nuevo»).
 */
class SetMyHeightUseCase(
    private val repository: ProfileRepository,
    private val pending: PendingHeightStore? = null,
) {
    @Throws(Exception::class)
    suspend operator fun invoke(group: HeightGroup): HeightGroup {
        val store = pending ?: return repository.setMyHeight(group)
        return try {
            repository.setMyHeight(group).also { store.clear() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            store.save(group)
            group
        }
    }
}

/** Sube la altura que quedó pendiente sin conexión (la llama el drenado de la cola offline). */
class FlushPendingHeightUseCase(
    private val repository: ProfileRepository,
    private val pending: PendingHeightStore,
) {
    @Throws(Exception::class)
    suspend operator fun invoke() {
        val group = pending.pending() ?: return
        repository.setMyHeight(group)
        pending.clear()
    }
}
