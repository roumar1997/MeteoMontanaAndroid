package com.meteomontana.android.data.api

/**
 * Idioma de la app ("es" | "en") que se manda al servidor en la cabecera
 * Accept-Language de cada petición. El servidor lo guarda al recibir /me y
 * redacta las notificaciones del usuario en ese idioma.
 *
 * Lo fijan las apps al arrancar y cada vez que el usuario cambia de idioma.
 */
object ApiLanguage {
    var code: String = "es"
}
