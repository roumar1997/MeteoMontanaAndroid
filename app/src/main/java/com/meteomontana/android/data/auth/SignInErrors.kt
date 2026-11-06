package com.meteomontana.android.data.auth

import androidx.annotation.StringRes
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.meteomontana.android.R

/**
 * Error de Credential Manager → texto propio y traducido. Nunca se enseña `e.message`: viene del sistema, en inglés
 * («No credentials available») y sin decir qué hacer.
 */
@StringRes
internal fun signInErrorRes(e: Throwable): Int = when (e) {
    is NoCredentialException -> R.string.auth_no_google_account
    is GetCredentialCancellationException -> R.string.auth_signin_cancelled
    else -> R.string.auth_signin_failed
}
