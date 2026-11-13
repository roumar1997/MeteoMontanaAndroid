package com.meteomontana.android.data.outbox

import com.meteomontana.android.domain.model.HeightGroup
import com.meteomontana.android.domain.port.PendingHeightStore

/** Altura pendiente guardada en la tabla Outbox (tipo HEIGHT, payload = valor de la API). */
class OutboxPendingHeightStore(private val outbox: OutboxRepository?) : PendingHeightStore {

    override suspend fun save(group: HeightGroup) {
        val repo = outbox ?: return
        clear()
        repo.enqueue(OutboxType.HEIGHT, "", group.apiValue)
    }

    override suspend fun pending(): HeightGroup? =
        outbox?.all()?.lastOrNull { it.type == OutboxType.HEIGHT }?.let { HeightGroup.parse(it.payloadJson) }

    override suspend fun clear() {
        val repo = outbox ?: return
        repo.all().filter { it.type == OutboxType.HEIGHT }.forEach { repo.delete(it.id) }
    }
}
