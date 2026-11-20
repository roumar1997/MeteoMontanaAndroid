package com.meteomontana.android.domain.model.gym

/** Chips de "¿Que no es correcto?". [code] es lo que viaja al servidor. */
enum class GymReportIssue(val code: String) {
    IS_FREE("IS_FREE"),
    IS_PAID("IS_PAID"),
    IS_BOULDER("IS_BOULDER"),
    IS_ROPE("IS_ROPE"),
    SCALE("SCALE"),
    CLOSED("CLOSED"),
    WRONG_LOCATION("WRONG_LOCATION"),
    OTHER("OTHER");

    companion object {
        fun fromCode(raw: String?): GymReportIssue? = entries.firstOrNull { it.code == raw }
    }
}

enum class GymReportKind { CORRECTION, NEW_GYM;
    companion object { fun parse(raw: String?) = entries.firstOrNull { it.name == raw } ?: CORRECTION }
}

enum class GymReportStatus { PENDING, APPLIED, REJECTED;
    companion object { fun parse(raw: String?) = entries.firstOrNull { it.name == raw } ?: PENDING }
}

/** Datos propuestos en un alta ("Añadir un muro que falta"). */
data class GymProposal(
    val name: String,
    val lat: Double,
    val lon: Double,
    val kind: GymKind,
    val discipline: GymDiscipline,
    val outdoor: Boolean
)

/** Reporte tal como lo devuelve el servidor al enviarlo (queda pendiente de un admin). */
data class GymReport(
    val id: Long,
    val gymId: Long?,
    val gymName: String?,
    val authorUid: String,
    val kind: GymReportKind,
    /** Codigos sin interpretar: un chip nuevo del servidor no debe perderse. */
    val issues: List<String>,
    val comment: String?,
    val photoUrl: String?,
    val proposed: GymProposal?,
    val status: GymReportStatus,
    val createdAt: String,
    val scale: GymScale? = null
)

/** Correccion de una ficha. */
data class GymCorrection(
    val gymId: Long,
    val issues: List<GymReportIssue>,
    val comment: String?,
    val photoUrl: String?,
    /** Escala propuesta (issue SCALE): van las dos cosas juntas o el servidor contesta 400. */
    val scale: GymScale? = null
)

/** Alta de un muro nuevo: nombre y punto en el mapa; el resto opcional. */
data class NewGymDraft(
    val name: String,
    val lat: Double,
    val lon: Double,
    val kind: GymKind? = null,
    val discipline: GymDiscipline? = null,
    val outdoor: Boolean? = null,
    val comment: String? = null,
    val photoUrl: String? = null
)
