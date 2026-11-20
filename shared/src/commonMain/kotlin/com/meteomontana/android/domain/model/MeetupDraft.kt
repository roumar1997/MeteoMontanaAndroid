package com.meteomontana.android.domain.model

import com.meteomontana.android.domain.util.GradeRange
import com.meteomontana.android.domain.util.gradeScore
import kotlinx.serialization.Serializable

/**
 * Borrador de «Nueva quedada» en 3 pasos: 1 dónde y cuándo, 2 qué y con quién, 3 privacidad y foto. Lógica pura
 * (igual en iOS y Android): qué pasos están completos, cómo se toca el grado y el material, y la petición final.
 * Serializable para que sobreviva a que el sistema mate el proceso. Atrás entre pasos no pierde nada.
 */
@Serializable
data class MeetupDraft(
    val placeIsGym: Boolean = false,
    val schoolId: String = "",
    val gymId: Long? = null,
    val placeName: String = "",
    val days: Set<String> = emptySet(),
    /** BOULDER | ROUTE | BOTH (las dos casillas) | null (sin elegir = cualquiera). */
    val discipline: String? = null,
    val partners: Int = DEFAULT_PARTNERS,
    val gradeMin: String? = null,
    val gradeMax: String? = null,
    /** Material mínimo: clave ([MeetupGearItem]) a cantidad. Ausente = no se pide. */
    val gear: Map<String, Int> = emptyMap(),
    val privacy: String = "OPEN",
    /** null = sin límite. */
    val memberLimit: Int? = null,
    val photoUrl: String? = null,
    val name: String = "",
    val description: String? = null
) {
    val hasBoulder: Boolean get() = discipline == "BOULDER" || discipline == "BOTH"
    val hasRoute: Boolean get() = discipline == "ROUTE" || discipline == "BOTH"

    val hasPlace: Boolean get() = if (placeIsGym) gymId != null else schoolId.isNotBlank()

    /** «Cordada»: una vía en la que se busca a una sola persona. */
    val isRopeTeam: Boolean get() = discipline == "ROUTE" && partners == 1

    /** ¿Se puede pasar del [step] (1 a 3) al siguiente? El paso 2 es opcional entero; el 3 es el último. */
    fun canAdvance(step: Int): Boolean = when (step) {
        1 -> hasPlace && days.isNotEmpty()
        else -> true
    }

    fun withPlaceKind(gym: Boolean): MeetupDraft =
        if (gym == placeIsGym) this else copy(placeIsGym = gym, schoolId = "", gymId = null, placeName = "", gear = emptyMap())

    fun withSchool(id: String, name: String) = copy(placeIsGym = false, schoolId = id, gymId = null, placeName = name)

    fun withGym(id: Long, name: String) = copy(placeIsGym = true, schoolId = "", gymId = id, placeName = name)

    fun toggleDay(iso: String) = copy(days = if (iso in days) days - iso else days + iso)

    /** Al cambiar de modalidad se suelta el material que ya no tiene sentido (cuerda en un bloque, etc.). */
    fun withDiscipline(value: String?): MeetupDraft {
        val d = value.takeIf { it == "BOULDER" || it == "ROUTE" || it == "BOTH" }
        val allowed = MeetupGearItem.forPlace(d, placeIsGym).toSet()
        return copy(discipline = d, gear = gear.filterKeys { it in allowed })
    }

    /** Casillas Bloque y Vía (se pueden marcar las dos): cada toque enciende o apaga la suya. */
    fun toggleBoulder(): MeetupDraft = withDiscipline(disciplineOf(boulder = !hasBoulder, route = hasRoute))

    fun toggleRoute(): MeetupDraft = withDiscipline(disciplineOf(boulder = hasBoulder, route = !hasRoute))

    private fun disciplineOf(boulder: Boolean, route: Boolean): String? = when {
        boulder && route -> "BOTH"
        boulder -> "BOULDER"
        route -> "ROUTE"
        else -> null
    }

    fun withPartners(n: Int) = copy(partners = n.coerceIn(MIN_PARTNERS, MAX_PARTNERS))

    /** «Cordada (1 persona)»: la enciende (1 persona, modalidad vía) o la apaga (vuelve a [DEFAULT_PARTNERS]). */
    fun toggleRopeTeam(): MeetupDraft =
        if (isRopeTeam) withPartners(DEFAULT_PARTNERS) else withDiscipline("ROUTE").withPartners(1)

    /**
     * Toque en la escalera de grados: nada, ese grado suelto; otro, rango ordenado de fácil a difícil; el ya elegido
     * lo quita; con un rango completo, empieza de nuevo (misma regla que [GradeRange.toggle]).
     */
    fun toggleGrade(tapped: String): MeetupDraft {
        val current = when {
            gradeMin == null -> null
            gradeMax == null || gradeMax.equals(gradeMin, ignoreCase = true) -> gradeMin
            else -> "$gradeMin${GradeRange.SEP}$gradeMax"
        }
        val parts = GradeRange.toggle(current, tapped)?.let { GradeRange.parts(it) }.orEmpty()
        return copy(gradeMin = parts.firstOrNull()?.lowercase(), gradeMax = parts.lastOrNull()?.lowercase())
    }

    /** ¿Cae [grade] dentro del rango elegido (o es el grado suelto)? */
    fun isGradeSelected(grade: String): Boolean {
        val min = gradeMin ?: return false
        val s = gradeScore(grade) ?: return false
        val lo = gradeScore(min) ?: return false
        val hi = gradeScore(gradeMax ?: min) ?: return false
        return s in lo..hi
    }

    /** Pide o quita un material; con cantidad empieza en la de por defecto. */
    fun toggleGear(item: String): MeetupDraft =
        if (item in gear) copy(gear = gear - item) else copy(gear = gear + (item to MeetupGearItem.defaultQty(item)))

    fun withGearQty(item: String, qty: Int): MeetupDraft =
        if (item !in gear) this else copy(gear = gear + (item to qty.coerceIn(1, MeetupGearItem.MAX_QTY)))

    /** Límite de participantes: bajar del mínimo lo quita («sin límite»); subir desde «sin límite» empieza en 2. */
    fun stepLimit(delta: Int): MeetupDraft {
        val current = memberLimit
        val next = when {
            current == null -> if (delta > 0) MIN_LIMIT else null
            current + delta < MIN_LIMIT -> null
            else -> (current + delta).coerceAtMost(MAX_LIMIT)
        }
        return copy(memberLimit = next)
    }

    /** Petición al servidor; [defaultName] se usa si el usuario no puso nombre. */
    fun toRequest(defaultName: String): CreateMeetupRequest = CreateMeetupRequest(
        schoolId = if (placeIsGym) "" else schoolId.trim(),
        name = name.trim().ifEmpty { defaultName },
        description = description,
        discipline = discipline,
        privacy = privacy,
        memberLimit = memberLimit,
        photoUrl = photoUrl,
        days = days.sorted(),
        gymId = if (placeIsGym) gymId else null,
        gradeMin = gradeMin,
        gradeMax = gradeMax ?: gradeMin,
        partnersWanted = partners,
        requiredGear = MeetupGearItem.forPlace(discipline, placeIsGym).mapNotNull { item -> gear[item]?.let { MeetupGear(item, it) } }
    )

    companion object {
        const val DEFAULT_PARTNERS = 2
        const val MIN_PARTNERS = 1
        const val MAX_PARTNERS = 20
        const val MIN_LIMIT = 2
        const val MAX_LIMIT = 99

        /** Escalera de grados del paso 2 (cubre de 4 a 8a+; el servidor admite de 3 a 9c+). */
        val GRADE_LADDER: List<String> = listOf(
            "4", "5", "5+", "6a", "6a+", "6b", "6b+", "6c", "6c+", "7a", "7a+", "7b", "7b+", "7c", "7c+", "8a", "8a+"
        )
    }
}
