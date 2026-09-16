package com.meteomontana.android.domain.usecase.blocks

import com.meteomontana.android.domain.model.BetaLink
import com.meteomontana.android.domain.repository.BlockRepository

/**
 * Enlaces de beta (Instagram/YouTube) en piedras/vías — directo, sin pasar
 * por revisión de admin, mismo trato que las estrellas o los comentarios
 * (Álvaro, 2026-09-15: "que se pueda poner igual que se vota... y que se
 * pueda tener varios diferentes, beta personas +1.70 y personas -1.70").
 */
class GetBetaLinksUseCase(private val repo: BlockRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(blockId: String, lineId: String?): List<BetaLink> =
        repo.getBetaLinks(blockId, lineId)
}

class AddBetaLinkUseCase(private val repo: BlockRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(blockId: String, lineId: String?, url: String, heightCategory: String?, authorName: String? = null): BetaLink =
        repo.addBetaLink(blockId, lineId, url, heightCategory, authorName)
}

class DeleteBetaLinkUseCase(private val repo: BlockRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(linkId: String) = repo.deleteBetaLink(linkId)
}
