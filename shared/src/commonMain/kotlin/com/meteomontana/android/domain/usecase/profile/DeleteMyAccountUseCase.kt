package com.meteomontana.android.domain.usecase.profile

import com.meteomontana.android.domain.repository.ProfileRepository

/** Borra la cuenta del usuario (perfil + datos personales en el backend). */
class DeleteMyAccountUseCase(private val repository: ProfileRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke() = repository.deleteMyAccount()
}
