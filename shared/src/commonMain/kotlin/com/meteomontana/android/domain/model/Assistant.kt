package com.meteomontana.android.domain.model

/**
 * Asistente de búsqueda con IA (POST /api/assistant/ask). El servidor NO manda
 * frases: manda un estado y datos reales, y cada app escribe el texto en el
 * idioma del usuario. Espejo del contrato `AskAssistantUseCase.Answer` del
 * backend; cualquier campo nuevo allí debe ser ADITIVO y nullable.
 */
enum class AssistantStatus {
    OK,
    OUT_OF_SCOPE,
    /** Falta o no se encontró una escuela/sector: ver [AssistantClarification]. */
    NEEDS_INPUT,
    /** La IA no respondió: la app sigue con los filtros manuales. */
    UNAVAILABLE,
    USER_LIMIT,
    GLOBAL_LIMIT,
    /** Muchas preguntas a la vez: "prueba en unos segundos". */
    BUSY
}

/** Una escuela o un sector que se puede ofrecer al usuario como opción. */
data class AssistantOption(val id: String, val name: String)

/**
 * Lo que el servidor entendió de la frase. Se devuelve tal cual en el mensaje
 * siguiente para poder refinar ("ahora solo 7a") sin repetirlo todo.
 */
data class AssistantUnderstood(
    val intent: String,
    val dateFrom: String? = null,
    val dateTo: String? = null,
    val gradeMin: String? = null,
    val gradeMax: String? = null,
    val discipline: String? = null,
    val rockTypes: List<String> = emptyList(),
    val orientations: List<String> = emptyList(),
    val maxDistanceKm: Double? = null,
    val q: String? = null,
    val schoolMention: String? = null,
    val sectorMention: String? = null,
    /** "SHADE" | "SUN" | null. */
    val sun: String? = null,
    /** "MORNING" | "AFTERNOON" | "ALL_DAY" | null. */
    val dayPart: String? = null,
    /** Escuelas que se comparan (intención COMPARE). */
    val schoolMentions: List<String> = emptyList(),
    /** Pidió lo mejor valorado ("los mejores", "recomendados", "con estrellas"...). */
    val topRated: Boolean = false,
    /** Mínimo de estrellas (1 a 5) si dijo cuántas. */
    val minStars: Int? = null,
    /** Horas que mira hacia delante una pregunta del tiempo. */
    val hoursAhead: Int? = null,
    /** RAIN | WIND | HUMIDITY | TEMPERATURE | GENERAL (solo en preguntas del tiempo). */
    val weatherTopic: String? = null,
    /** Habló de DONDE ESTÁ: se usó su ubicación. */
    val useMyLocation: Boolean = false,
    /** Solo con vídeo de beta: "ANY" | "TALL" (+1,70 m) | "SHORT" (-1,70 m) | null = no lo pidió. */
    val beta: String? = null,
    /** Salida de la vía: "SIT" | "SEMI" | "STAND" | "JUMP" | "TRAV" | null. */
    val startType: String? = null,
    /** Solo vías con el trazo dibujado. */
    val withTopo: Boolean = false,
    /** Pidió un sitio donde NO llueva. */
    val noRain: Boolean = false,
    /** Solo en MINE (lo suyo): STATS | FAVORITES | LAST_VISIT | MEETUPS. */
    val mineTopic: String? = null,
    /** Año por el que pregunta de su diario ("este año"), o null. */
    val year: Int? = null,
    /** Pidió vías o bloques que NO haya hecho: se cruza en el móvil con su diario. */
    val notDone: Boolean = false,
    /** Solo en ACTION: ADD_FAVORITE | REMOVE_FAVORITE | OPEN_SCHOOL. La app siempre pide confirmación. */
    val action: String? = null,
    /** Pidió las escuelas con MÁS vías/bloques de un grado: se ordenan por cantidad, no por el tiempo. */
    val mostLines: Boolean = false
)

/** El tiempo de ahora en un sitio. */
data class AssistantNow(
    val temperature: Double,
    val humidity: Double,
    val windKmh: Double,
    val precipitationMm: Double,
    val rainProbability: Int,
    val cloudCover: Int,
    val dewPoint: Double?
)

data class AssistantHourPoint(
    val time: String,
    val temperature: Double,
    val precipitationMm: Double,
    val rainProbability: Int,
    val windKmh: Double
)

/** ¿Va a llover en las horas que preguntó? startsInHours: 0 = ahora mismo; null = no se espera. */
data class AssistantRain(
    val expected: Boolean,
    val startsInHours: Int?,
    val totalMm: Double,
    val maxProbability: Int,
    val hoursChecked: Int,
    /** Hora (ISO, "2026-10-08T17:00") en que empieza la primera lluvia; null si no se espera. */
    val startsAt: String? = null,
    /** Última hora (ISO) que se ha mirado: "no llueve HASTA las 23h". */
    val until: String? = null
)

/** Cómo está para escalar (mismo índice que la pantalla del tiempo). */
data class AssistantClimbing(
    val score: Int,
    val label: String,
    val rockWet: Boolean,
    val dryingMessage: String?,
    val bestWindowStart: String?,
    val bestWindowEnd: String?
)

data class AssistantWeather(
    /** null = la ubicación del usuario. */
    val placeName: String?,
    val myLocation: Boolean,
    val topic: String,
    val now: AssistantNow,
    val hours: List<AssistantHourPoint>,
    val rain: AssistantRain,
    val climbing: AssistantClimbing
)

data class AssistantDay(
    val date: String,
    val score: Int,
    val rainMm: Double,
    val rainProb: Int,
    val rainy: Boolean
)

/** Una escuela recomendada: tarjeta con pronóstico por día y nº de vías del grado pedido. */
data class AssistantSchoolCard(
    val schoolId: String,
    val name: String,
    val rockType: String?,
    val distanceKm: Double?,
    val lineCount: Int,
    /** Media de los días; null si no hay pronóstico. */
    val combinedScore: Int?,
    val days: List<AssistantDay>,
    val rainDays: Int
)

data class AssistantRecommendation(
    /** false si las fechas pedidas caen fuera del pronóstico (7 días). */
    val forecastAvailable: Boolean,
    val dates: List<String>,
    val schools: List<AssistantSchoolCard>,
    /** true si se ordenó por número de vías ("las escuelas con más 7a"), sin mirar el tiempo. */
    val byCount: Boolean = false
)

data class AssistantStone(
    val blockId: String,
    val name: String,
    /** Orientación votada (N..NO) o null si nadie la ha votado. */
    val aspect: String?,
    /** "SHADE" | "SUN" | "MIXED" o null si no se sabe. */
    val sun: String?,
    val lineCount: Int,
    /** Una vía de la piedra: las apps abren la piedra a través de ella. null si no tiene vías. */
    val firstLineId: String? = null
)

data class AssistantSector(
    /** null = piedras sin sector asignado. */
    val sectorId: String?,
    val name: String?,
    val total: Int,
    /** Piedras que SÍ cumplen (orientación conocida). */
    val matching: Int,
    val unknownOrientation: Int,
    val stones: List<AssistantStone>,
    /** Piedras sin orientación votada: se enseñan igualmente, podrían cumplir. */
    val unknownStones: List<AssistantStone>
)

data class AssistantBreakdown(
    val school: AssistantOption,
    /** Nombre del sector si la pregunta se limitó a uno. */
    val sectorFilter: String?,
    val sun: String?,
    val dayPart: String?,
    val day: String,
    val sectors: List<AssistantSector>,
    val totalMatching: Int,
    val totalStones: Int,
    val stonesWithOrientation: Int
)

/** Qué hay que preguntar al usuario. kind: NEEDS_SCHOOL | SCHOOL_NOT_FOUND | SCHOOL_AMBIGUOUS | SECTOR_NOT_FOUND. */
data class AssistantClarification(val kind: String, val options: List<AssistantOption>)

data class AssistantAnswer(
    val status: AssistantStatus,
    val understood: AssistantUnderstood?,
    val resolvedSchool: AssistantOption?,
    val resolvedSector: AssistantOption?,
    val recommendation: AssistantRecommendation?,
    val breakdown: AssistantBreakdown?,
    val clarification: AssistantClarification?,
    val weather: AssistantWeather?,
    val summary: AssistantSummary? = null,
    /** Las escuelas de una búsqueda en VARIAS ("los 7a de Albarracín y Zarzalejo"); vacío si fue una sola. */
    val resolvedSchools: List<AssistantOption> = emptyList()
)

/** Cuántas líneas hay de un grupo de grado ("≤5", "6", "7", "8+"). */
data class AssistantGradeBand(val band: String, val count: Int)

/** La ficha de una escuela en cifras ("cuéntame Albarracín"). El texto lo escribe cada app. */
data class AssistantSummary(
    val school: AssistantOption,
    val rockType: String?,
    val region: String?,
    val stones: Int,
    val sectors: Int,
    val lines: Int,
    val boulderLines: Int,
    val routeLines: Int,
    /** Grado de la línea más dura, o null si ninguna tiene grado. */
    val hardest: String?,
    val grades: List<AssistantGradeBand>,
    /** Meses (1-12) en los que mejor se escala, de enero a diciembre; vacío si no hay histórico. */
    val bestMonths: List<Int>
)

/**
 * Las tarjetas de UN mensaje del chat (escuelas recomendadas, desglose por sectores o
 * vías/bloques encontrados). Es lo que se guarda unas horas en el móvil para poder
 * volver a enseñarlas al reabrir la app.
 */
data class AssistantMessagePayload(
    val recommendation: AssistantRecommendation?,
    val breakdown: AssistantBreakdown?,
    val hits: List<LineSearchHit>,
    /** Cuántos resultados hubo en total (en `hits` solo vienen los que se muestran). */
    val hitsTotal: Int,
    val weather: AssistantWeather? = null
)
