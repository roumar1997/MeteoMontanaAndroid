package com.meteomontana.android.domain.usecase.blocks

import com.meteomontana.android.domain.model.LineComment
import com.meteomontana.android.domain.repository.BlockRepository

/** Comentarios de la comunidad en piedras/vías (con votos de utilidad). */
class GetLineCommentsUseCase(private val repo: BlockRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(blockId: String): List<LineComment> = repo.getComments(blockId)
}

class AddLineCommentUseCase(private val repo: BlockRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(blockId: String, lineId: String?, text: String): LineComment =
        repo.addComment(blockId, lineId, text)
}

class VoteLineCommentUseCase(private val repo: BlockRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(commentId: String, value: Int): Int =
        repo.voteComment(commentId, value)
}

class DeleteLineCommentUseCase(private val repo: BlockRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(commentId: String) = repo.deleteComment(commentId)
}
