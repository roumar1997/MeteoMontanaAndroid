package com.meteomontana.android.domain.usecase.admin

import com.meteomontana.android.domain.model.ContentChangeItem
import com.meteomontana.android.domain.model.ContributorItem
import com.meteomontana.android.domain.repository.AdminHistoryRepository

/** Historial de cambios del catálogo (filtros opcionales; [source] "TRUSTED" = publicado sin revisión). */
class GetContentHistoryUseCase(private val repository: AdminHistoryRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(schoolId: String?, blockId: String?, actorUid: String?, source: String?,
                                before: String?): List<ContentChangeItem> =
        repository.history(schoolId, blockId, actorUid, source, before)
}

/** Vuelve a poner la piedra o vía como estaba ANTES de ese cambio. */
class RestoreContentChangeUseCase(private val repository: AdminHistoryRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(changeId: String) = repository.restore(changeId)
}

/** Aportaciones por persona (todas, o solo [uid]). */
class GetContributorsUseCase(private val repository: AdminHistoryRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(uid: String?): List<ContributorItem> = repository.contributors(uid)
}

class SetUserTrustUseCase(private val repository: AdminHistoryRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(uid: String, trusted: Boolean) = repository.setTrust(uid, trusted)
}
