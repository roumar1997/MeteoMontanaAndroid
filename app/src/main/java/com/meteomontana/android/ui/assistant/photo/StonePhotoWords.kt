package com.meteomontana.android.ui.assistant.photo

import kotlin.math.roundToInt

/**
 * Los textos de «¿Qué piedra es esta?» en el idioma de la app (los mismos que en iOS). Van aquí y no en `strings.xml`
 * porque el asistente entero se redacta así (ver `AssistantWords`): el idioma se pide en cada uso y puede cambiar en marcha.
 */
class StonePhotoWords(lang: String) {
    private val en = lang.lowercase().startsWith("en")
    private fun t(es: String, english: String) = if (en) english else es

    val title = t("¿Qué piedra es esta?", "Which boulder is this?")
    val userMessage = t("¿Qué piedra es esta? (foto)", "Which boulder is this? (photo)")
    val takePhoto = t("Hacer una foto", "Take a photo")
    val pickPhoto = t("Elegir de la galería", "Choose from library")
    val menuCd = t("¿Qué piedra es esta? Hacer o elegir una foto", "Which boulder is this? Take or choose a photo")
    val open = t("ABRIR", "OPEN")
    val stop = t("Parar", "Stop")
    val close = t("Cerrar", "Close")
    val noLines = t("Esta piedra aún no tiene vías para abrir.", "This boulder has no climbs to open yet.")
    val noPhotoToCompare = t("sin foto para comparar", "no photo to compare")
    val byLooksOnly = t(
        "La foto no dice dónde se hizo: la he comparado con las piedras de la escuela, solo por cómo se ven.",
        "The photo doesn't say where it was taken: I compared it with this crag's boulders, by looks only."
    )

    // Pasos de la búsqueda
    val readingWhere = t("Leyendo dónde se hizo la foto…", "Reading where the photo was taken…")
    val checkingWhere = t("Mirando dónde estás…", "Checking where you are…")
    fun stage(s: StoneStage): String = when (s) {
        StoneStage.Schools -> t("Buscando escuelas cerca de la foto…", "Looking for crags near the photo…")
        StoneStage.Stones -> t("Buscando piedras cerca…", "Looking for nearby boulders…")
        is StoneStage.Comparing -> t("Comparando con ${s.count} piedras…", "Comparing with ${s.count} boulders…")
    }

    // Fallos
    val cameraDenied = t(
        "Para hacer la foto necesito permiso de cámara. Puedes dárselo en Ajustes.",
        "To take the photo I need camera access. You can grant it in Settings."
    )
    val cannotOpen = t(
        "No he podido abrir esa foto. Prueba con otra o hazla con la cámara.",
        "I couldn't open that photo. Try another one or take it with the camera."
    )
    fun tookTooLong(stuckAt: String?) = if (stuckAt == null) {
        t("Ha tardado demasiado. Inténtalo otra vez con buena cobertura.", "It took too long. Try again with good coverage.")
    } else {
        t("Ha tardado demasiado (se quedó en: $stuckAt). Inténtalo otra vez con buena cobertura.",
            "It took too long (it got stuck at: $stuckAt). Try again with good coverage.")
    }
    val noLocation = t(
        "La foto no guarda dónde se hizo y no tengo tu ubicación. Activa la ubicación o hazla desde la escuela.",
        "The photo doesn't store where it was taken and I don't have your location. Turn on location or take it at the crag."
    )
    val noData = t(
        "No he podido cargar las escuelas. Comprueba la conexión e inténtalo de nuevo.",
        "I couldn't load the crags. Check your connection and try again."
    )
    val noDataInSchool = t(
        "No he podido cargar las piedras de esta escuela. Comprueba la conexión e inténtalo de nuevo.",
        "I couldn't load this crag's boulders. Check your connection and try again."
    )
    fun noSchoolNear(approximate: Boolean, closestName: String?, closestKm: Double?): String {
        val base = if (approximate) {
            t("Esa foto no guarda dónde se hizo, y donde estás ahora no hay ninguna escuela cerca. Hazla con la cámara desde la escuela, o elige una foto con ubicación.",
                "That photo doesn't store where it was taken, and there's no crag near where you are now. Take it with the camera at the crag, or choose a photo with location.")
        } else {
            t("No hay ninguna piedra de Cumbre cerca de donde se hizo la foto.", "There's no Cumbre boulder near where the photo was taken.")
        }
        if (closestName == null || closestKm == null) return base
        return base + " " + t("La escuela más cercana es $closestName, a ${closestKm.roundToInt()} km.",
            "The closest crag is $closestName, ${closestKm.roundToInt()} km away.")
    }
    fun noStonesNear(school: String) = t("En $school aún no hay piedras colocadas cerca de ese punto.",
        "In $school there are no boulders placed near that spot yet.")
    fun noStonesWithPhoto(school: String) = t("En $school aún no hay piedras con foto para comparar.",
        "In $school there are no boulders with a photo to compare yet.")

    // Resultado
    fun intro(sure: Boolean, approximate: Boolean, school: String, weak: Boolean): String {
        val base = when {
            weak -> t("No se parece mucho a ninguna de las piedras de $school que hay cerca: puede que aún no esté en Cumbre. Las más cercanas:",
                "It doesn't look much like any boulder near $school: it may not be in Cumbre yet. The closest ones:")
            sure -> t("Creo que es esta, en $school:", "I think it's this one, in $school:")
            else -> t("Puede ser una de estas de $school:", "It could be one of these in $school:")
        }
        return if (approximate) base + " " + t("(la foto no guardaba dónde se hizo: uso dónde estás ahora)",
            "(the photo didn't store where it was taken: I'm using where you are now)") else base
    }

    /** «a 12 m · se parece un 74 %» (sin metros cuando no se sabe dónde se hizo la foto). */
    fun detail(c: StoneCandidate): String {
        val similarity = c.similarity?.let { t("se parece un ${(it * 100).roundToInt()} %", "${(it * 100).roundToInt()} % similar") }
            ?: noPhotoToCompare
        if (c.meters < 0) return similarity
        val away = t("a ${c.meters.roundToInt()} m", "${c.meters.roundToInt()} m away")
        return "$away · $similarity"
    }
}
