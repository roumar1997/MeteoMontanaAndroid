package com.meteomontana.android.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import com.meteomontana.android.R
import com.meteomontana.android.data.auth.signInErrorRes
import org.junit.Assert.assertEquals
import org.junit.Test

/** Regresión: el login enseñaba «No credentials available» en inglés, tal cual lo da Credential Manager. */
class SignInErrorsTest {
    @Test
    fun `sin cuenta de Google se explica que hay que anadirla`() =
        assertEquals(R.string.auth_no_google_account, signInErrorRes(NoCredentialException("No credentials available")))

    @Test
    fun `cancelar tiene su texto`() =
        assertEquals(R.string.auth_signin_cancelled, signInErrorRes(GetCredentialCancellationException("x")))

    @Test
    fun `otros errores de Credential Manager caen en el generico`() =
        assertEquals(R.string.auth_signin_failed, signInErrorRes(GetCredentialUnknownException("boom")))

    @Test
    fun `cualquier otra excepcion cae en el generico`() =
        assertEquals(R.string.auth_signin_failed, signInErrorRes(IllegalStateException("raw english")))
}
