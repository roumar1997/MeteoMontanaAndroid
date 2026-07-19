package com.meteomontana.android.ui.screens.detail

import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.BlockLine
import com.meteomontana.android.domain.model.FileRef
import com.meteomontana.android.domain.port.FileReader
import com.meteomontana.android.domain.usecase.feed.FeedKind
import com.meteomontana.android.domain.usecase.feed.PublishFeedPostUseCase
import com.meteomontana.android.domain.usecase.feed.UploadFeedPhotoUseCase
import javax.inject.Inject

/**
 * Publica el tick de una vía en el feed Comunidad (post + foto de celebración
 * opcional). Extraído de SchoolDetailViewModel: es una responsabilidad aparte
 * (fire-and-forget: si falla no bloquea ni deshace el diario).
 */
class TickFeedPublisher @Inject constructor(
    private val publishFeedPost: PublishFeedPostUseCase,
    private val uploadFeedPhoto: UploadFeedPhotoUseCase,
    private val fileReader: FileReader
) {
    /**
     * kind = PROJECT_DONE si la vía estaba en proyectos y pasa a hecha; TICK en
     * el resto. Los ids del backend son VARCHAR (UUID) → se mandan como String
     * tal cual (convertirlos a Long con toLongOrNull hacía que NUNCA se
     * publicara — bug probado en móvil).
     */
    suspend fun publish(
        block: Block,
        line: BlockLine,
        wasProject: Boolean,
        /** Descripción opcional del autor (hoja de publicar; ALWAYS = sin caption). */
        caption: String? = null,
        /** URI local (content://) de la foto de celebración hecha en la hoja, o null. */
        photoUri: String? = null,
        /** Aviso discreto si la foto no se pudo subir (el post queda sin foto). */
        onPhotoUploadFailed: (() -> Unit)? = null
    ) {
        val kind = if (wasProject) FeedKind.PROJECT_DONE else FeedKind.TICK
        // Modalidad de la piedra (misma distinción Bloque/Vía del detalle).
        val discipline = if (block.discipline.equals("ROUTE", ignoreCase = true)) "ROUTE" else "BOULDER"
        val postId = runCatching {
            publishFeedPost(
                block.id, line.id.takeIf { it.isNotBlank() }, kind, discipline, caption
            )
        }.getOrNull() ?: return
        // Foto de celebración (opcional): comprimir (mismo pipeline que las
        // fotos de perfil/piedras, muy por debajo de los 5MB del backend) y
        // subirla como multipart. Si falla, el post queda sin foto.
        if (photoUri != null) {
            runCatching {
                val bytes = fileReader.readImageCompressed(FileRef(photoUri))
                uploadFeedPhoto(postId, bytes)
            }.onFailure { onPhotoUploadFailed?.invoke() }
        }
    }
}
