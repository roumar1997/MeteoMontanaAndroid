package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.ContentChangeItem
import com.meteomontana.android.domain.model.ContributorItem

/** Admin: historial de cambios del catálogo y aportaciones por persona. */
interface AdminHistoryRepository {
    /** Filtros opcionales (null = todos). [before] = fecha del último de la página anterior. */
    suspend fun history(schoolId: String?, blockId: String?, actorUid: String?, source: String?, before: String?): List<ContentChangeItem>
    suspend fun restore(changeId: String)
    suspend fun contributors(uid: String?): List<ContributorItem>
    suspend fun setTrust(uid: String, trusted: Boolean)
}
