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
    val dayPart: String? = null
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
    val schools: List<AssistantSchoolCard>
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
    val clarification: AssistantClarification?
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
    val hitsTotal: Int
)
