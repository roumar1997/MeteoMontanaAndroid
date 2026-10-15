package com.meteomontana.android.domain.usecase.profile

import com.meteomontana.android.domain.model.HeightGroup
import com.meteomontana.android.domain.repository.ProfileRepository

/** Mi altura (más o menos de 1,70 m, o "prefiero no decirlo"); null si todavía no la he elegido. */
class GetMyHeightUseCase(private val repository: ProfileRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): HeightGroup? = repository.getMyHeight()
}

/** Guarda mi altura (en mi mochilita). Devuelve el grupo que quedó guardado. */
class SetMyHeightUseCase(private val repository: ProfileRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(group: HeightGroup): HeightGroup = repository.setMyHeight(group)
}
