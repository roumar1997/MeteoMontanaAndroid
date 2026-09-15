package com.meteomontana.android.domain.usecase.blocks

import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.repository.BlockRepository

/**
 * Pone/cambia/quita el enlace de beta (Instagram/YouTube) DIRECTO, sin pasar
 * por editar ni por revisión de admin — mismo trato que valorar con estrellas
 * (Álvaro, 2026-09-15: "que se pueda poner igual que se vota, viendo la
 * piedra, sin tener que editarlo"). Habla con el PUERTO, no con la API.
 */
class SetBetaUrlUseCase(private val repo: BlockRepository) {
    @Throws(Exception::class)
    suspend fun forLine(blockId: String, lineId: String, url: String?): Block =
        repo.setLineBetaUrl(blockId, lineId, url)

    @Throws(Exception::class)
    suspend fun forBlock(blockId: String, url: String?): Block =
        repo.setBlockBetaUrl(blockId, url)
}
