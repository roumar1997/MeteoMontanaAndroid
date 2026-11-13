package com.meteomontana.android.domain.port

import com.meteomontana.android.domain.model.HeightGroup

/**
 * Puerto: mi altura elegida SIN conexión, a la espera de subirla. Guarda solo la última
 * (elegir otra sustituye a la anterior). Implementado sobre la cola offline (outbox).
 */
interface PendingHeightStore {
    suspend fun save(group: HeightGroup)
    suspend fun pending(): HeightGroup?
    suspend fun clear()
}
