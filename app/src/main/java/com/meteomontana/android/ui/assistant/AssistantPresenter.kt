package com.meteomontana.android.ui.assistant

import com.meteomontana.android.domain.model.AssistantBreakdown
import com.meteomontana.android.domain.model.AssistantClarification
import com.meteomontana.android.domain.model.AssistantRecommendation
import com.meteomontana.android.domain.model.AssistantRestriction
import com.meteomontana.android.domain.model.AssistantRestrictions
import com.meteomontana.android.domain.model.GradeSummary
import com.meteomontana.android.domain.usecase.journal.Achievement
import com.meteomontana.android.domain.usecase.journal.GoalProgress
import com.meteomontana.android.domain.model.AssistantSector
import com.meteomontana.android.domain.model.AssistantStatus
import com.meteomontana.android.domain.model.AssistantStone
import com.meteomontana.android.domain.model.AssistantSummary
import com.meteomontana.android.domain.model.AssistantUnderstood
import com.meteomontana.android.domain.model.AssistantWeather
import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.usecase.assistant.MineAnswerer
import java.time.LocalDate

/** Cómo se muestran los valores del catálogo (roca, región, orientación): cambian con el idioma de la app. */
interface AssistantLabels {
    fun rock(raw: String): String
    fun region(raw: String): String
    fun aspect(code: String): String
}

/**
 * Convierte lo que devuelve el asistente en textos y etiquetas para pintar. Espejo de `AssistantPresenter.swift` (iOS).
 *
 * El servidor NO manda frases (solo un estado y datos reales): aquí se escriben los textos en el idioma de la app.
 * Es lógica pura, sin vistas ni Android, para poder probarla en JVM. Cada frase lleva su versión ES y EN en el mismo
 * sitio ([t]) para que no se descuadren.
 */
class AssistantPresenter(lang: String, private val labels: AssistantLabels) {

    private val en = lang.lowercase().startsWith("en")
    private fun t(es: String, english: String) = if (en) english else es

    // ── Estados sin datos ─────────────────────────────────────────────────────────────────────────────────────────

    /** Texto para los estados que no traen resultados; null si hay datos que mostrar. */
    fun message(status: AssistantStatus): String? = when (status) {
        AssistantStatus.OUT_OF_SCOPE -> t(
            "Solo puedo ayudarte con escuelas, bloques, vías y el tiempo para escalar.",
            "I can only help with crags, boulders, routes and climbing weather.")
        AssistantStatus.UNAVAILABLE -> t(
            "Ahora mismo no puedo responder. Puedes usar los filtros de la pestaña Escuelas.",
            "I can't answer right now. You can use the filters in the Crags tab.")
        AssistantStatus.USER_LIMIT -> t(
            "Has llegado al límite de preguntas de hoy. Mañana podrás volver a usarme.",
            "You've reached today's question limit. You can use me again tomorrow.")
        AssistantStatus.GLOBAL_LIMIT -> t(
            "El asistente está descansando por hoy. Vuelve mañana o usa los filtros.",
            "The assistant is resting for today. Come back tomorrow or use the filters.")
        AssistantStatus.BUSY -> t(
            "Hay muchas preguntas ahora mismo. Prueba de nuevo en unos segundos.",
            "Lots of questions right now. Try again in a few seconds.")
        AssistantStatus.NOT_ENABLED -> t(
            "El asistente no está activado para tu cuenta.",
            "The assistant isn't turned on for your account.")
        else -> null
    }

    /** Qué preguntar al usuario cuando falta o no se encontró una escuela/sector. */
    fun clarification(c: AssistantClarification): String = when (c.kind) {
        "NEEDS_SCHOOL" -> t("¿De qué escuela hablas?", "Which crag do you mean?")
        "SCHOOL_NOT_FOUND" -> t("No encuentro esa escuela. ¿Puedes escribir su nombre de otra forma?",
            "I can't find that crag. Could you write its name another way?")
        "SCHOOL_AMBIGUOUS" -> t("¿A cuál de estas te refieres?", "Which of these do you mean?")
        "NEEDS_TWO_SCHOOLS" -> t("¿Con qué otra escuela quieres compararla?", "Which other crag do you want to compare it with?")
        "NEEDS_LOCATION" -> t("Necesito saber dónde estás. Activa la ubicación o dime el nombre del sitio.",
            "I need to know where you are. Turn on location or tell me the place.")
        "PLACE_NOT_FOUND" -> t("No encuentro ese sitio en el mapa. ¿Puedes decirlo de otra forma (pueblo, ciudad o provincia)?",
            "I can't find that place on the map. Could you say it another way (town, city or province)?")
        "NEEDS_HOME" -> t("Aún no sé dónde vives. Cuando estés en casa, dime «guarda aquí como mi casa».",
            "I don't know where you live yet. When you're at home, tell me \"save here as my home\".")
        "SECTOR_NOT_FOUND" ->
            if (c.options.isEmpty()) t("Esa escuela no tiene sectores.", "That crag has no sectors.")
            else t("No encuentro ese sector. Estos son los de la escuela:", "I can't find that sector. These are the crag's sectors:")
        else -> t("Ahora mismo no puedo responder. Puedes usar los filtros de la pestaña Escuelas.",
            "I can't answer right now. You can use the filters in the Crags tab.")
    }

    // ── Chips con lo entendido ────────────────────────────────────────────────────────────────────────────────────

    /** Etiquetas con lo que se ha entendido, para que el usuario vea si acertó. */
    fun chips(u: AssistantUnderstood, school: String?, sector: String?): List<String> {
        val out = mutableListOf<String>()
        if (school != null) out += school
        if (sector != null) out += sector
        if (u.discipline == "BOULDER") out += t("Bloque", "Boulder")
        if (u.discipline == "ROUTE") out += t("Vía", "Route")
        val a = u.gradeMin
        val b = u.gradeMax
        when {
            a != null && b != null -> out += if (a == b) a else "$a – $b"
            a != null -> out += "≥ $a"
            b != null -> out += "≤ $b"
        }
        u.dateFrom?.let { out += dateRange(it, u.dateTo) }
        if (u.sun == "SHADE") out += t("Sombra", "Shade")
        if (u.sun == "SUN") out += t("Sol", "Sun")
        if (u.dayPart == "MORNING") out += t("Mañana", "Morning")
        if (u.dayPart == "AFTERNOON") out += t("Tarde", "Afternoon")
        u.orientations.forEach { out += t("Cara ${labels.aspect(it)}", "${labels.aspect(it)} face") }
        u.rockTypes.forEach { out += labels.rock(it) }
        u.maxDistanceKm?.let { out += "< ${it.toInt()} km" }
        if (u.topRated) out += t("Mejor valoradas", "Top rated")
        u.minStars?.let { out += "★ ≥ $it" }
        u.hoursAhead?.let { out += "$it h" }
        if (u.useMyLocation) out += t("Mi ubicación", "My location")
        if (u.beta == "ANY") out += t("Con beta", "With beta")
        if (u.beta == "TALL") out += t("Beta +1,70", "Beta over 1.70 m")
        if (u.beta == "SHORT") out += t("Beta -1,70", "Beta under 1.70 m")
        u.startType?.let { out += startLabel(it) }
        if (u.withTopo) out += t("Con topo", "With topo")
        if (u.noRain) out += t("Sin lluvia", "No rain")
        if (u.notDone) out += t("Sin hacer", "Not done yet")
        if (u.mostLines) out += t("Con más vías", "Most routes")
        u.year?.let { out += "$it" }
        return out
    }

    // ── Acciones ──────────────────────────────────────────────────────────────────────────────────────────────────

    /** Lo que se le pregunta al usuario antes de hacer algo por él. */
    fun actionPrompt(kind: String, school: String): String = when (kind) {
        "ADD_FAVORITE" -> t("¿Añado $school a tus favoritas?", "Add $school to your favourites?")
        "REMOVE_FAVORITE" -> t("¿Quito $school de tus favoritas?", "Remove $school from your favourites?")
        "ENABLE_WEEKEND_ALERT" -> t("¿Activo el aviso de fin de semana para $school?", "Turn on the weekend alert for $school?")
        "ENABLE_MORNING_ALERT" -> t(
            "¿Activo el aviso de cada mañana? Entre las 7 y las 11 te aviso si alguna de tus favoritas tiene buenas condiciones ese día.",
            "Turn on the morning alert? Between 7 and 11 I'll tell you if any of your favourites has good conditions that day.")
        "SET_HOME" -> t("¿Guardo donde estás ahora como tu casa? Solo se guarda en este móvil.",
            "Save where you are now as your home? It's only stored on this phone.")
        else -> t("¿Abro $school?", "Open $school?")
    }

    fun homeNoLocation() = t("No sé dónde estás: activa la ubicación y vuelve a decírmelo.", "I don't know where you are: turn on location and tell me again.")
    fun homeSaved() = t("Hecho: guardada tu casa. Ya puedes preguntarme «cerca de casa».", "Done: your home is saved. You can now ask me \"near home\".")
    fun morningAlertAlready() = t("El aviso de cada mañana ya estaba activado.", "The morning alert was already on.")
    fun morningAlertDone() = t(
        "Hecho: cada mañana, entre las 7 y las 11, te aviso si alguna favorita tiene buen día. Necesitas tener escuelas en favoritas.",
        "Done: every morning, between 7 and 11, I'll tell you if a favourite has a good day. You need crags in your favourites.")
    fun meetupOpening() = t("Abriendo la quedada para que la revises y la crees.", "Opening the meetup so you can review and create it.")
    fun meetupTime(time: String) = t("Quedamos a las $time.", "Meeting at $time.")

    fun meetupPrompt(school: String, date: String?, time: String?): String {
        val day = date?.let { dayLabel(it) }
        return when {
            day != null && time != null -> t("¿Preparo una quedada en $school el $day a las $time?", "Set up a meetup at $school on $day at $time?")
            day != null -> t("¿Preparo una quedada en $school el $day?", "Set up a meetup at $school on $day?")
            else -> t("¿Preparo una quedada en $school?", "Set up a meetup at $school?")
        }
    }

    /** Nombre propuesto para la quedada: "Albarracín · SÁB 25 · 09:00". */
    fun meetupName(school: String, date: String?, time: String?): String =
        listOfNotNull(school, date?.let { dayLabel(it) }, time).joinToString(" · ")

    fun multiLogPrompt(count: Int, date: String, missing: List<String>): String {
        val head = t("¿Apunto estas $count en tu diario el ${dateRange(date, null)}?", "Log these $count in your diary on ${dateRange(date, null)}?")
        if (missing.isEmpty()) return head
        return head + " " + t("No encuentro: ${missing.joinToString(", ")}.", "I can't find: ${missing.joinToString(", ")}.")
    }
    fun noneOfTheseFound(names: List<String>) = t("No encuentro ninguna de esas vías: ${names.joinToString(", ")}.",
        "I can't find any of those routes: ${names.joinToString(", ")}.")
    fun couldNotLog(name: String) = t("No se pudo apuntar «$name».", "Couldn't log “$name”.")
    fun logAll(count: Int) = t("Apuntar las $count", "Log all $count")
    fun doneWord() = t("hecha", "sent")
    fun projectWord() = t("proyecto", "project")
    fun cancelWord() = t("Cancelar", "Cancel")
    fun confirmWord() = t("Confirmar", "Confirm")

    /** La pregunta de confirmación al apuntar UNA vía en el diario. */
    fun logPrompt(kind: String, hit: LineSearchHit, date: String): String {
        val name = hit.lineName ?: hit.blockName
        val grade = hit.grade?.let { " ($it)" } ?: ""
        val place = "$name$grade · ${hit.schoolName}"
        val day = dateRange(date, null)
        return if (kind == "LOG_PROJECT") t("¿Apunto «$place» como proyecto el $day?", "Log “$place” as a project on $day?")
        else t("¿Apunto «$place» como hecha el $day?", "Log “$place” as sent on $day?")
    }

    /** Lo que se le dice al usuario cuando la acción ya está hecha. */
    fun actionDone(kind: String, school: String): String = when (kind) {
        "ADD_FAVORITE" -> t("Hecho: $school está ahora en tus favoritas.", "Done: $school is now in your favourites.")
        "REMOVE_FAVORITE" -> t("Hecho: $school ya no está en tus favoritas.", "Done: $school is no longer in your favourites.")
        else -> t("Abriendo $school.", "Opening $school.")
    }

    // ── Ficha de una escuela y lo suyo ────────────────────────────────────────────────────────────────────────────

    /** La ficha de una escuela en cifras ("cuéntame Albarracín"): varias líneas cortas. */
    fun summaryText(s: AssistantSummary): String {
        val lines = mutableListOf<String>()
        var head = s.school.name
        s.region?.takeIf { it.isNotEmpty() }?.let { head += " · ${labels.region(it)}" }
        s.rockType?.takeIf { it.isNotEmpty() }?.let { head += " · ${labels.rock(it)}" }
        lines += head
        if (s.lines == 0) {
            lines += t("Todavía no tiene vías ni bloques en Cumbre.", "It has no routes or boulders in Cumbre yet.")
        } else {
            lines += t("${s.stones} piedras en ${s.sectors} sectores, ${s.lines} líneas (${s.boulderLines} bloques, ${s.routeLines} vías).",
                "${s.stones} stones in ${s.sectors} sectors, ${s.lines} lines (${s.boulderLines} boulders, ${s.routeLines} routes).")
            if (s.grades.isNotEmpty()) {
                val bands = s.grades.joinToString(" · ") { "${it.band}: ${it.count}" }
                lines += t("Grados: $bands.", "Grades: $bands.")
            }
            s.hardest?.let { lines += t("La más dura: $it.", "Hardest: $it.") }
        }
        if (s.bestMonths.isNotEmpty()) {
            val names = monthsLong()
            val months = s.bestMonths.mapNotNull { names.getOrNull(it - 1) }
            if (months.isNotEmpty()) {
                val list = months.joinToString(", ")
                lines += t("Mejores meses: $list.", "Best months: $list.")
            }
        }
        return lines.joinToString("\n")
    }

    /** La respuesta a una pregunta sobre lo suyo (diario, favoritas, quedadas), ya calculada en el móvil. */
    fun mineText(r: MineAnswerer.MineAnswer, period: String? = null): String {
        val count = r.count
        return when (r.topic) {
            "FAVORITES" ->
                if (count == 0) t("Aún no tienes escuelas favoritas.", "You have no favourite schools yet.")
                else t("Tus favoritas ($count): ${r.items.joinToString(", ")}.", "Your favourites ($count): ${r.items.joinToString(", ")}.")
            "LAST_VISIT" -> {
                val date = r.lastDate
                val place = r.place
                when {
                    date == null && place != null -> t("No tienes ninguna sesión apuntada en $place.", "You have no sessions logged at $place.")
                    date == null -> t("Aún no has apuntado ninguna sesión en tu diario.", "You haven't logged any session in your diary yet.")
                    place != null -> t("Tu última vez en $place fue el ${dateRange(date, null)}.", "Your last time at $place was on ${dateRange(date, null)}.")
                    else -> t("Tu última sesión fue el ${dateRange(date, null)}.", "Your last session was on ${dateRange(date, null)}.")
                }
            }
            "MEETUPS" ->
                if (count == 0) t("No hay quedadas próximas.", "There are no upcoming meetups.")
                else t("Quedadas próximas ($count): ${r.items.joinToString(" · ")}.", "Upcoming meetups ($count): ${r.items.joinToString(" · ")}.")
            else -> {
                val max = r.maxGrade
                if (period != null) when {
                    count == 0 -> t("$period: no tienes ninguna encadenada en tu diario.", "$period: you have no sends in your diary.")
                    max != null -> t("$period: llevas $count encadenadas. La más dura: $max.", "$period: you have $count sends. Hardest: $max.")
                    else -> t("$period: llevas $count encadenadas.", "$period: you have $count sends.")
                } else when {
                    count == 0 -> t("No tienes ninguna encadenada con esos filtros en tu diario.", "You have no sends in your diary matching those filters.")
                    max != null -> t("Llevas $count encadenadas con esos filtros. La más dura: $max.", "You have $count sends matching those filters. Hardest: $max.")
                    else -> t("Llevas $count encadenadas con esos filtros.", "You have $count sends matching those filters.")
                }
            }
        }
    }

    /** Etiqueta de la salida de una vía. */
    fun startLabel(start: String): String = when (start) {
        "SIT" -> t("Salida sentado", "Sit start")
        "STAND" -> t("Salida de pie", "Standing start")
        "SEMI" -> t("Semisentado", "Semi-sit start")
        "JUMP" -> t("Con lance", "With a jump")
        "TRAV" -> t("Travesía", "Traverse")
        else -> start
    }

    // ── Fechas ────────────────────────────────────────────────────────────────────────────────────────────────────

    private fun weekdaysShortSunFirst() = if (en) listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    else listOf("dom", "lun", "mar", "mié", "jue", "vie", "sáb")

    private fun monthsShort() = if (en) listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    else listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

    private fun monthsLong() = if (en) listOf("January", "February", "March", "April", "May", "June", "July", "August",
        "September", "October", "November", "December")
    else listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

    private fun parse(iso: String): LocalDate? = runCatching { LocalDate.parse(iso.take(10)) }.getOrNull()

    /** "SÁB 6" (día de la semana abreviado + número) en el idioma de la app. */
    fun dayLabel(iso: String): String {
        val date = parse(iso) ?: return iso
        val weekday = date.dayOfWeek.value % 7   // domingo = 0
        return "${weekdaysShortSunFirst()[weekday].uppercase()} ${date.dayOfMonth}"
    }

    /** "6 – 8 dic" o "6 dic" si es un solo día. */
    fun dateRange(from: String, to: String?): String {
        val a = parse(from) ?: return from
        val months = monthsShort()
        val d1 = a.dayOfMonth
        val m1 = months[a.monthValue - 1]
        val b = if (to != null && to != from) parse(to) else null
        if (b == null) return "$d1 $m1"
        val d2 = b.dayOfMonth
        val m2 = months[b.monthValue - 1]
        return if (m1 == m2) "$d1 – $d2 $m2" else "$d1 $m1 – $d2 $m2"
    }

    // ── Restricciones ─────────────────────────────────────────────────────────────────────────────────────────────

    fun restrictionsIntro(r: AssistantRestrictions, school: String?): String {
        val period = if (r.from == r.to) dayLabel(r.from) else dateRange(r.from, r.to)
        val active = r.items.count { it.active }
        if (school != null) {
            if (r.items.isEmpty()) return t("No tengo ninguna restricción apuntada en $school. Aun así, mira los carteles al llegar.",
                "I have no restrictions recorded at $school. Still, check the signs when you arrive.")
            if (active == 0) return t("En $school no hay restricciones en vigor para $period. Estas son las que tiene en otras fechas:",
                "At $school there are no restrictions in force for $period. These are the ones it has on other dates:")
            return if (active == 1) t("En $school hay 1 restricción en vigor para $period:", "At $school there is 1 restriction in force for $period:")
            else t("En $school hay $active restricciones en vigor para $period:", "At $school there are $active restrictions in force for $period:")
        }
        if (active == 0) return t("No tengo restricciones en vigor para $period en las ${r.schoolsChecked} escuelas de la zona. Mira los carteles al llegar.",
            "I have no restrictions in force for $period at the ${r.schoolsChecked} crags in the area. Check the signs when you arrive.")
        return if (active == 1) t("Hay 1 restricción en vigor en la zona para $period:", "There is 1 restriction in force in the area for $period:")
        else t("Hay $active restricciones en vigor en la zona para $period:", "There are $active restrictions in force in the area for $period:")
    }

    fun restrictionKind(kind: String) = when (kind) {
        "CLOSED" -> t("Cerrado", "Closed")
        "REGULATED" -> t("Regulado", "Regulated")
        else -> t("Aviso", "Notice")
    }

    /** Qué afecta: el sector, o cuántas piedras sueltas. */
    fun restrictionPlace(i: AssistantRestriction): String {
        if (!i.sectorName.isNullOrEmpty()) return i.sectorName!!
        return if (i.blockCount == 1) t("1 piedra", "1 boulder") else t("${i.blockCount} piedras", "${i.blockCount} boulders")
    }

    /** Cuándo: "Todo el año", "Cada año, 1 feb – 30 jun" o "1 feb – 30 jun". */
    fun restrictionWhen(i: AssistantRestriction): String {
        if (i.permanent) return t("Todo el año", "All year")
        val from = i.startsOn ?: return ""
        val to = i.endsOn ?: return ""
        return if (i.yearly) t("Cada año, ${dateRange(from, to)}", "Every year, ${dateRange(from, to)}") else dateRange(from, to)
    }

    fun restrictionsSource(name: String) = t("Fuente: $name", "Source: $name")
    fun restrictionsDisclaimer() = t("Datos que mantiene Cumbre a partir de fuentes oficiales. Mira siempre los carteles de la zona.",
        "Data kept by Cumbre from official sources. Always check the signs in the area.")

    // ── Viaje de varios días ──────────────────────────────────────────────────────────────────────────────────────

    /** Un día del plan: la mejor escuela de ese día (sin lluvia si alguna se libra). */
    data class TripDay(val date: String, val schoolId: String, val schoolName: String, val score: Int, val rainy: Boolean)

    fun tripPlan(r: AssistantRecommendation): List<TripDay> {
        if (!r.forecastAvailable) return emptyList()
        return r.dates.mapNotNull { date ->
            val options = r.schools.mapNotNull { s -> s.days.firstOrNull { it.date == date }?.let { s to it } }
            val dry = options.filter { !it.second.rainy }
            val best = dry.ifEmpty { options }.maxByOrNull { it.second.score } ?: return@mapNotNull null
            TripDay(date, best.first.schoolId, best.first.name, best.second.score, best.second.rainy)
        }
    }

    fun tripIntro(plan: List<TripDay>): String {
        val first = plan.firstOrNull() ?: return t("Aún no hay previsión para esos días. Estas son las escuelas que mejor encajan:",
            "There's no forecast for those days yet. These are the crags that fit best:")
        if (plan.map { it.schoolId }.toSet().size == 1) return t("Para esos ${plan.size} días, lo mejor es quedarte en ${first.schoolName}:",
            "For those ${plan.size} days, your best bet is to stay at ${first.schoolName}:")
        return t("Tu plan para ${plan.size} días, con el mejor sitio de cada día:", "Your plan for ${plan.size} days, with the best place each day:")
    }

    // ── Grado según la altura ─────────────────────────────────────────────────────────────────────────────────────

    /** "Zarzamora: los de más de 1,70 la votan 7a y los de menos 7a+." a partir de los votos separados por altura. */
    fun heightGradeLine(name: String, summary: GradeSummary): String {
        summary.heightSplit?.let { return t("$name: los de más de 1,70 la votan ${it.tallGrade} y los de menos ${it.shortGrade}.",
            "$name: climbers over 1.70 m vote it ${it.tallGrade} and shorter ones ${it.shortGrade}.") }
        fun top(votes: Map<String, Int>?) = votes?.maxByOrNull { it.value }?.takeIf { it.value > 0 }
        val tall = top(summary.votesByHeight["TALL"])
        val short = top(summary.votesByHeight["SHORT"])
        return when {
            tall == null && short == null -> t("$name: aún no hay votos de grado por altura.", "$name: no grade votes by height yet.")
            tall != null && short != null && tall.key == short.key ->
                t("$name: altos y bajos coinciden en ${tall.key}.", "$name: tall and short climbers agree on ${tall.key}.")
            else -> "$name: " + listOfNotNull(
                tall?.let { t("+1,70: ${it.key} (${it.value} votos)", "+1.70: ${it.key} (${it.value} votes)") },
                short?.let { t("−1,70: ${it.key} (${it.value} votos)", "−1.70: ${it.key} (${it.value} votes)") }
            ).joinToString(" · ")
        }
    }
    fun noHeightVotes() = t("No he encontrado votos de grado para esas vías.", "I haven't found grade votes for those routes.")

    // ── Objetivo y logros ─────────────────────────────────────────────────────────────────────────────────────────

    fun goalText(p: GoalProgress): String {
        val g = p.goal
        val parts = mutableListOf<String>()
        g.grade?.let { grade ->
            parts += if (p.gradeReached) t("el $grade ya lo tienes", "you already have $grade")
            else t("el $grade aún no (tu máximo este año: ${p.maxGradeThisYear ?: "—"})", "$grade not yet (your best this year: ${p.maxGradeThisYear ?: "—"})")
        }
        g.ascents?.let { a ->
            val done = p.ascentsDone
            parts += if (done >= a) t("$a ascensiones: ¡hecho!", "$a ascents: done!")
            else t("$done de $a ascensiones (te faltan ${a - done})", "$done of $a ascents (${a - done} to go)")
        }
        g.days?.let { d ->
            val done = p.daysDone
            parts += if (done >= d) t("$d días de roca: ¡hecho!", "$d days on rock: done!")
            else t("$done de $d días de roca (te faltan ${d - done})", "$done of $d days on rock (${d - done} to go)")
        }
        if (parts.isEmpty()) return t("Tu objetivo de ${g.year} no tiene metas todavía.", "Your ${g.year} goal has no targets yet.")
        return t("Tu objetivo de ${g.year}: ${parts.joinToString("; ")}.", "Your ${g.year} goal: ${parts.joinToString("; ")}.")
    }

    fun noGoalText(year: Int) = t("Aún no tienes objetivo para $year. Puedes ponerlo en tu perfil, en Estadísticas.",
        "You don't have a goal for $year yet. You can set it in your profile, under Statistics.")

    /** Cuántos logros tiene y el siguiente de cada familia (el catálogo va de fácil a difícil dentro de cada una). */
    fun achievementsText(all: List<Achievement>, describe: (Achievement) -> String): String {
        val unlocked = all.count { it.unlocked }
        val next = all.filter { !it.unlocked }.distinctBy { it.group }.take(3).map(describe)
        if (next.isEmpty()) return t("Tienes los ${all.size} logros. ¡Todos!", "You have all ${all.size} achievements!")
        return t("Tienes $unlocked de ${all.size} logros. Los siguientes más a mano: ${next.joinToString(" · ")}.",
            "You have $unlocked of ${all.size} achievements. The closest next ones: ${next.joinToString(" · ")}.")
    }

    // ── Resultados de una búsqueda ────────────────────────────────────────────────────────────────────────────────

    /** Resultados de un sector dentro de una escuela (name == null: piedras sin sector asignado). */
    data class HitSector(val name: String?, val hits: List<LineSearchHit>) { val id: String get() = name ?: "" }

    /** Resultados de una escuela, repartidos por sectores. [total] = todos los de esa escuela. */
    data class HitGroup(val id: String, val schoolName: String, val sectors: List<HitSector>, val total: Int)

    /**
     * TODOS los resultados, agrupados por escuela y sector, sin recortar nada: lo que no cabe en pantalla se resuelve
     * plegando y desplegando, no escondiendo filas (Álvaro, 2026-10-08: "me tiene que salir la lista de todas").
     */
    fun groupAllHits(hits: List<LineSearchHit>): List<HitGroup> =
        groupHits(hits, maxSchools = Int.MAX_VALUE, perSchool = Int.MAX_VALUE, perSchoolWhenAlone = Int.MAX_VALUE)

    /**
     * Qué escuelas empiezan desplegadas: con pocos resultados todas; con muchos solo la primera, para que el chat no
     * se haga eterno ni se carguen cientos de miniaturas que nadie está viendo.
     */
    fun initiallyExpanded(groups: List<HitGroup>, limit: Int = 20): Set<String> {
        val total = groups.sumOf { it.total }
        return if (total <= limit) groups.map { it.id }.toSet() else groups.take(1).map { it.id }.toSet()
    }

    /** Clave estable de un sector dentro de una escuela (para saber si está plegado). */
    fun sectorKey(schoolId: String, sectorId: String) = "$schoolId|$sectorId"

    /**
     * Agrupa los resultados por escuela conservando el orden en que llegan (el servidor los manda por cercanía) y,
     * dentro de cada escuela, por sector. Como mucho [maxSchools] escuelas; con varias se enseñan [perSchool] filas de
     * cada una, y si solo hay UNA se enseñan hasta [perSchoolWhenAlone].
     */
    fun groupHits(hits: List<LineSearchHit>, maxSchools: Int = 5, perSchool: Int = 4, perSchoolWhenAlone: Int = 60): List<HitGroup> {
        val order = mutableListOf<String>()
        val bySchool = LinkedHashMap<String, MutableList<LineSearchHit>>()
        for (h in hits) {
            if (h.schoolId !in bySchool) order += h.schoolId
            bySchool.getOrPut(h.schoolId) { mutableListOf() }.add(h)
        }
        val chosen = order.take(maxSchools)
        val limit = if (chosen.size == 1) perSchoolWhenAlone else perSchool
        return chosen.map { id ->
            val all = bySchool[id].orEmpty()
            HitGroup(id, all.firstOrNull()?.schoolName.orEmpty(), bySector(all.take(limit)), all.size)
        }
    }

    /** Reparte por sector: primero los que más resultados tienen; "sin sector" siempre al final. */
    fun bySector(hits: List<LineSearchHit>): List<HitSector> {
        val bySector = LinkedHashMap<String, MutableList<LineSearchHit>>()
        for (h in hits) bySector.getOrPut((h.sectorName ?: "").trim()) { mutableListOf() }.add(h)
        return bySector.map { (name, list) -> HitSector(name.ifEmpty { null }, list) }
            .sortedWith(Comparator { a, b ->
                when {
                    (a.name == null) != (b.name == null) -> if (b.name == null) -1 else 1
                    a.hits.size != b.hits.size -> b.hits.size.compareTo(a.hits.size)
                    else -> (a.name ?: "").compareTo(b.name ?: "")
                }
            })
    }

    fun hitsIntro(total: Int, needsLocationForDistance: Boolean, rated: Boolean = false): String {
        var text = if (rated) {
            if (total == 0) t("Todavía nadie ha valorado vías con esos filtros.", "Nobody has rated routes with those filters yet.")
            else t("Las mejor valoradas ($total):", "Top rated ($total):")
        } else {
            if (total == 0) t("No he encontrado vías o bloques con esos filtros.", "I found no routes or boulders with those filters.")
            else t("He encontrado $total resultados:", "I found $total results:")
        }
        if (needsLocationForDistance) text += " " + t("Para filtrar por distancia activa tu ubicación.", "Turn on your location to filter by distance.")
        return text
    }

    /** Aviso de que hay más resultados de los que caben en el chat; null si se ven todos. */
    fun moreNote(total: Int, shown: Int): String? =
        if (total > shown) t("Y ${total - shown} más. Mira todos en Escuelas → Vías/Bloques.",
            "And ${total - shown} more. See them all in Crags → Routes/Boulders.") else null

    // ── Estrellas ─────────────────────────────────────────────────────────────────────────────────────────────────

    /** "★ 4,5 (8)": media y número de votos, con la coma o el punto del idioma de la app. */
    fun starsLabel(rating: Double, count: Int) = "★ ${oneDecimal(rating)} ($count)"

    /**
     * Deja solo las vías que alguien ha valorado (y, si dijo cuántas estrellas, con esa media o más), de más a menos
     * estrellas; a igualdad, primero las que más gente ha valorado y, si aún empatan, el orden en que llegaron.
     */
    fun rankByRating(hits: List<LineSearchHit>, minStars: Int?): List<LineSearchHit> {
        data class Rated(val hit: LineSearchHit, val avg: Double, val votes: Int, val order: Int)
        val rated = hits.mapIndexedNotNull { index, h ->
            val avg = h.rating ?: return@mapIndexedNotNull null
            val votes = h.ratingCount ?: return@mapIndexedNotNull null
            if (votes <= 0) null else Rated(h, avg, votes, index)
        }
        return rated.filter { minStars == null || it.avg >= minStars }
            .sortedWith(compareByDescending<Rated> { it.avg }.thenByDescending { it.votes }.thenBy { it.order })
            .map { it.hit }
    }

    // ── Comparar ──────────────────────────────────────────────────────────────────────────────────────────────────

    fun compareIntro(r: AssistantRecommendation): String {
        if (r.schools.isEmpty()) return t("No he podido comparar esas escuelas.", "I couldn't compare those crags.")
        val names = r.schools.joinToString(", ") { it.name }
        var text = t("Comparación de $names:", "Comparing $names:")
        if (r.forecastAvailable) {
            // Sin grado pedido ("compárame el tiempo en…") el nº de vías no es "de un grado": no se menciona.
            text += " " + if (r.filtered) t("Ordenadas por el tiempo de esos días y el nº de vías de ese grado.",
                "Ranked by the weather on those days and the number of routes of that grade.")
            else t("Ordenadas por el tiempo de esos días.", "Ranked by the weather on those days.")
        }
        return text
    }

    // ── El tiempo ─────────────────────────────────────────────────────────────────────────────────────────────────

    /** Una décima, con coma en español y punto en inglés. */
    fun oneDecimal(value: Double): String {
        val s = String.format(java.util.Locale.US, "%.1f", value)
        return if (en) s else s.replace('.', ',')
    }

    fun weatherTitle(w: AssistantWeather): String = w.placeName ?: t("Tu ubicación", "Your location")

    /** "15h" a partir de "2026-10-08T15:00". */
    fun hourLabel(iso: String): String {
        val time = iso.substringAfterLast('T', iso)
        return if (time.length >= 2) "${time.take(2)}h" else iso
    }

    /** Altura de la barra de lluvia de una hora: 3 mm o más llenan la barra; un poco de lluvia siempre se ve. */
    fun rainBarHeight(mm: Double, full: Float): Float {
        if (mm <= 0) return 0f
        return maxOf(3f, minOf(full, (mm / 3.0).toFloat() * full))
    }

    fun windLabel(kmh: Double): String = when {
        kmh < 12 -> t("flojo", "light")
        kmh < 25 -> t("moderado", "moderate")
        kmh < 40 -> t("fuerte", "strong")
        else -> t("muy fuerte", "very strong")
    }

    /**
     * Responde a "¿va a llover?" con un SÍ o un NO claro, la hora y la probabilidad: "No, no se espera lluvia hasta las
     * 23h (probabilidad máxima 0 %)." / "Sí, va a llover: empezará sobre las 17h…".
     */
    fun rainHeadline(w: AssistantWeather): String {
        val r = w.rain
        val hours = r.hoursChecked
        val prob = r.maxProbability
        val until = r.until?.let { hourLabel(it) }
        val start = r.startsInHours
        if (!r.expected || start == null) {
            return if (until != null) t("No, no se espera lluvia hasta las $until. La probabilidad máxima es del $prob %.",
                "No, no rain expected until $until. The maximum chance is $prob %.")
            else t("No, no se espera lluvia en las próximas $hours h. La probabilidad máxima es del $prob %.",
                "No, no rain expected in the next $hours h. The maximum chance is $prob %.")
        }
        val mm = oneDecimal(r.totalMm)
        if (start == 0) {
            return if (until != null) t("Sí, está lloviendo ahora o va a empezar ya (≈$mm mm hasta las $until).",
                "Yes, it's raining now or about to (≈$mm mm until $until).")
            else t("Sí, está lloviendo ahora o va a empezar ya (≈$mm mm en las próximas $hours h).",
                "Yes, it's raining now or about to (≈$mm mm in the next $hours h).")
        }
        val at = r.startsAt?.let { hourLabel(it) }
        if (at != null && until != null) {
            return t("Sí, va a llover: empezará sobre las $at (en ~$start h). Caerán ≈$mm mm hasta las $until, con hasta un $prob % de probabilidad.",
                "Yes, it will rain: starting around $at (in ~$start h). About $mm mm until $until, with up to $prob % chance.")
        }
        return t("Sí, va a llover: empezará en ~$start h (≈$mm mm en las próximas $hours h, hasta un $prob % de probabilidad).",
            "Yes, it will rain: starting in ~$start h (≈$mm mm in the next $hours h, up to $prob % chance).")
    }

    private fun round(d: Double): Int = Math.round(d).toInt()

    /** Mínima y máxima de cada día pedido, una línea por día: "VIE 9: 5 – 14 °C". */
    fun temperatureByDay(w: AssistantWeather): String {
        val lines = w.days.map { d -> "${dayLabel(d.date)}: ${round(d.tempMin)} – ${round(d.tempMax)} °C" }
        return (listOf(weatherTitle(w) + ":") + lines).joinToString("\n")
    }

    /** La frase del asistente sobre el tiempo, según lo que se preguntó. */
    fun weatherHeadline(w: AssistantWeather): String {
        // "Temperaturas mínimas y máximas hoy, mañana y pasado": una línea por día, no el "ahora" ni las horas de hoy.
        if (w.topic == "TEMPERATURE" && w.days.isNotEmpty()) return temperatureByDay(w)
        val place = weatherTitle(w)
        val n = w.now
        return when (w.topic) {
            "RAIN" -> "$place: ${rainHeadline(w)}"
            "WIND" -> {
                val peak = maxOf(n.windKmh, w.hours.maxOfOrNull { it.windKmh } ?: n.windKmh)
                t("$place: viento ahora ${round(n.windKmh)} km/h (${windLabel(n.windKmh)}). Máximo en las próximas horas: ${round(peak)} km/h.",
                    "$place: wind now ${round(n.windKmh)} km/h (${windLabel(n.windKmh)}). Peak in the next hours: ${round(peak)} km/h.")
            }
            "HUMIDITY" -> {
                var text = t("$place: humedad ${round(n.humidity)} %.", "$place: humidity ${round(n.humidity)} %.")
                n.dewPoint?.let { text += " " + t("Punto de rocío: ${round(it)} °C.", "Dew point: ${round(it)} °C.") }
                text += " " + if (w.climbing.rockWet) t("La roca está húmeda.", "The rock is damp.") else t("La roca está seca.", "The rock is dry.")
                text
            }
            "ROCK" ->
                if (w.climbing.rockWet) {
                    val drying = w.climbing.dryingMessage.orEmpty()
                    t("$place: la roca está húmeda.", "$place: the rock is damp.") + (if (drying.isEmpty()) "" else " $drying")
                } else t("$place: la roca está seca, se puede escalar.", "$place: the rock is dry, you can climb.")
            "TEMPERATURE" -> {
                val temps = w.hours.map { it.temperature }
                val low = round(temps.minOrNull() ?: n.temperature)
                val high = round(temps.maxOrNull() ?: n.temperature)
                t("$place: ahora hay ${round(n.temperature)} °C. En las próximas horas, entre $low y $high °C.",
                    "$place: it's ${round(n.temperature)} °C now. In the next hours, between $low and $high °C.")
            }
            else -> t("$place: ahora ${round(n.temperature)} °C, viento ${round(n.windKmh)} km/h, humedad ${round(n.humidity)} %. Para escalar: ${w.climbing.label} (${w.climbing.score}/100).",
                "$place: now ${round(n.temperature)} °C, wind ${round(n.windKmh)} km/h, humidity ${round(n.humidity)} %. For climbing: ${w.climbing.label} (${w.climbing.score}/100).")
        }
    }

    // ── Mensajes recuperados ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * Aviso de cuánto hace que se enseñó una tarjeta guardada ("Guardado hace 3 h. El tiempo puede haber cambiado.").
     * null si hace menos de media hora: ahí no hace falta decir nada.
     */
    fun savedNote(createdAtMs: Long, nowMs: Long = System.currentTimeMillis()): String? {
        val seconds = (nowMs - createdAtMs) / 1000.0
        if (seconds < 1800) return null
        val hours = maxOf(1, Math.round(seconds / 3600).toInt())
        return t("Guardado hace $hours h. El tiempo puede haber cambiado.", "Saved $hours h ago. The weather may have changed.")
    }

    // ── Cabeceras de los resultados ───────────────────────────────────────────────────────────────────────────────

    fun recommendationIntro(r: AssistantRecommendation): String {
        if (r.schools.isEmpty()) {
            return if (r.filtered) t("No he encontrado escuelas con vías de ese grado en la zona.", "I found no crags with routes of that grade nearby.")
            else t("No he encontrado escuelas en la zona.", "I couldn't find any schools in the area.")
        }
        // Pedido por cantidad ("las escuelas con más 7a"): no es una falta de previsión.
        if (r.byCount) return t("Las escuelas con más vías o bloques de ese grado:", "The schools with the most routes or boulders of that grade:")
        if (!r.forecastAvailable) {
            return if (r.filtered) t("Aún no hay previsión para esas fechas. Te ordeno las escuelas por número de vías de ese grado.",
                "There's no forecast for those dates yet. I'm ranking crags by number of routes of that grade.")
            else t("Aún no hay previsión para esas fechas. Te ordeno las escuelas por número de vías.",
                "There's no forecast for those dates yet. I'm ranking the schools by number of routes.")
        }
        // Una sola escuela y varios días ("¿qué día voy a Albarracín?"): se dice cuál es el mejor.
        val only = r.schools.singleOrNull()
        if (only != null && r.dates.size >= 2) {
            val dry = only.days.filter { !it.rainy }
            val best = (if (dry.isEmpty()) only.days else dry).maxByOrNull { it.score }
            if (best != null) return t("En ${only.name} el mejor día es ${dayLabel(best.date)} (${best.score}/100).",
                "In ${only.name} the best day is ${dayLabel(best.date)} (${best.score}/100).")
        }
        val first = r.dates.firstOrNull() ?: return t("Esta es la mejor opción:", "This is the best option:")
        val range = dateRange(first, r.dates.lastOrNull())
        return t("Para $range, esta es la mejor opción:", "For $range, this is the best option:")
    }

    fun breakdownIntro(b: AssistantBreakdown): String {
        if (b.totalStones == 0) return t("No he encontrado piedras que cumplan eso en ${b.school.name}.", "I found no boulders matching that at ${b.school.name}.")
        val known = t("De ${b.totalStones} piedras, ${b.stonesWithOrientation} tienen la orientación votada.",
            "Of ${b.totalStones} boulders, ${b.stonesWithOrientation} have a voted orientation.")
        val filtered = b.sun != null || b.sectors.any { it.unknownOrientation > 0 }
        return if (filtered) known else t("Estas son las piedras de ${b.school.name} por sector.", "These are the boulders at ${b.school.name} by sector.")
    }

    /** Resumen corto de un sector: "2 a la sombra · 1 sin orientación". */
    fun sectorSummary(s: AssistantSector, sun: String?): String {
        val parts = mutableListOf<String>()
        val matching = s.matching
        parts += when (sun) {
            "SHADE" -> t("$matching a la sombra", "$matching in the shade")
            "SUN" -> t("$matching al sol", "$matching in the sun")
            else -> t("$matching piedras", "$matching boulders")
        }
        if (s.unknownOrientation > 0) parts += t("${s.unknownOrientation} sin orientación", "${s.unknownOrientation} without orientation")
        return parts.joinToString(" · ")
    }

    /** Etiqueta de una piedra: orientación votada y, si se sabe, sombra o sol. */
    fun stoneLabel(s: AssistantStone): String {
        val aspect = s.aspect ?: return t("sin orientación votada", "no voted orientation")
        val a = labels.aspect(aspect)
        return when (s.sun) {
            "SHADE" -> "$a · ${t("sombra", "shade")}"
            "SUN" -> "$a · ${t("sol", "sun")}"
            "MIXED" -> "$a · ${t("sol y sombra", "sun and shade")}"
            else -> a
        }
    }

    // ── Mensajes del propio asistente (errores, acciones, ayudas) ─────────────────────────────────────────────────

    fun connectError() = t("No se pudo conectar. Inténtalo otra vez.", "Couldn't connect. Try again.")
    fun cantAnswer() = t("Ahora mismo no puedo responder.", "I can't answer right now.")
    fun whichLineToLog() = t("¿Qué vía quieres apuntar? Dime su nombre.", "Which route do you want to log? Tell me its name.")
    fun noLineNamed(name: String) = t("No encuentro ninguna vía llamada «$name».", "I can't find any route called “$name”.")
    fun alreadyLogged(name: String) = t("Ya tienes «$name» apuntada.", "You already have “$name” logged.")
    fun whichOfTheseToLog() = t("¿Cuál de estas quieres apuntar?", "Which of these do you want to log?")
    fun couldNotDo() = t("No se pudo hacer. Inténtalo otra vez.", "That didn't work. Please try again.")
    fun alreadyInWeekendAlert(school: String) = t("$school ya estaba en tu aviso de fin de semana.", "$school was already in your weekend alert.")
    fun weekendAlertFull() = t("Ya tienes 3 escuelas en tu aviso de fin de semana. Quita una en los ajustes de la alerta de tiempo.",
        "You already have 3 schools in your weekend alert. Remove one in the weather alert settings.")
    fun weekendAlertDone(school: String) = t("Hecho: te avisaré del fin de semana en $school.", "Done: I'll alert you about the weekend at $school.")
    fun loggedOffline(line: String) = t("Sin conexión: apuntaré «$line» cuando vuelvas a tener red.", "Offline: I'll log “$line” when you're back online.")
    fun loggedDone(line: String) = t("Hecho: «$line» apuntada como hecha.", "Done: “$line” logged as sent.")
    fun loggedProject(line: String) = t("Hecho: «$line» apuntada como proyecto.", "Done: “$line” logged as a project.")

    // ── Chat ──────────────────────────────────────────────────────────────────────────────────────────────────

    /** Saludo de la pantalla vacía con el nombre de pila ("Hola, Álvaro"); sin nombre, solo "Hola". */
    fun greeting(displayName: String?): String {
        val first = displayName?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() } ?: return t("Hola", "Hello")
        return t("Hola, $first", "Hello, $first")
    }

    /**
     * Icono de una etiqueta de lo entendido (sitio, sol/sombra, cuándo…). Las etiquetas se guardan como texto, así que se
     * deduce al pintar: lo conocido por su texto, los grados y números sin icono y el resto (escuela o sector) con la chincheta.
     */
    fun chipIcon(chip: String): ChipIcon? {
        val known = mapOf(
            t("Sombra", "Shade") to ChipIcon.SHADE, t("Sol", "Sun") to ChipIcon.SUN,
            t("Mañana", "Morning") to ChipIcon.MORNING, t("Tarde", "Afternoon") to ChipIcon.AFTERNOON,
            t("Bloque", "Boulder") to ChipIcon.BOULDER, t("Vía", "Route") to ChipIcon.ROUTE,
            t("Mi ubicación", "My location") to ChipIcon.LOCATION, t("Mejor valoradas", "Top rated") to ChipIcon.STAR,
            t("Sin lluvia", "No rain") to ChipIcon.NO_RAIN, t("Con topo", "With topo") to ChipIcon.TOPO,
            t("Sin hacer", "Not done yet") to ChipIcon.NOT_DONE, t("Con más vías", "Most routes") to ChipIcon.MOST_LINES,
            t("Con beta", "With beta") to ChipIcon.BETA, t("Beta +1,70", "Beta over 1.70 m") to ChipIcon.BETA,
            t("Beta -1,70", "Beta under 1.70 m") to ChipIcon.BETA
        )
        known[chip]?.let { return it }
        if (chip.startsWith("Cara ") || chip.endsWith(" face")) return ChipIcon.FACE
        if (chip.first().isDigit() && (chip.endsWith(" h") || monthsShort().any { it.isNotEmpty() && chip.endsWith(it) })) return ChipIcon.CALENDAR
        if (chip.any { it.isDigit() } || chip.startsWith("★") || chip.startsWith("≥") || chip.startsWith("≤") || chip.startsWith("<")) return null
        return ChipIcon.PIN
    }

    enum class ChipIcon { SHADE, SUN, MORNING, AFTERNOON, BOULDER, ROUTE, LOCATION, STAR, NO_RAIN, TOPO, NOT_DONE, MOST_LINES, BETA, FACE, CALENDAR, PIN }

    /** Una sugerencia para seguir la conversación: una pregunta que se envía tal cual, o abrir una escuela ([openSchoolId]). */
    data class FollowUp(val title: String, val openSchoolId: String? = null)

    /**
     * Qué ofrecer debajo de la última respuesta, según lo que enseñó. Vacío si no hay nada natural que proponer o si la
     * respuesta espera algo del usuario (confirmar, elegir, reintentar).
     */
    fun followUps(m: AssistantMessage): List<FollowUp> {
        if (m.isUser || m.retryText != null || m.options.isNotEmpty() || m.pendingActions.any { it.state == PendingAction.State.WAITING }) return emptyList()
        fun ask(es: String, english: String) = FollowUp(t(es, english))
        m.breakdown?.let { b ->
            return listOf(
                if (b.sun == "SUN") ask("¿Y a la sombra?", "And in the shade?") else ask("¿Y al sol?", "And in the sun?"),
                ask("¿Y mañana a las 17?", "And tomorrow at 5 pm?"),
                FollowUp(t("Abrir ${b.school.name}", "Open ${b.school.name}"), b.school.id)
            )
        }
        if (m.recommendation != null) return listOf(ask("¿Y el finde?", "And the weekend?"), ask("Solo bloques", "Boulders only"), ask("Más cerca de mí", "Closer to me"))
        if (m.weather != null) return listOf(ask("¿Y mañana?", "And tomorrow?"), ask("¿Mejor hora para ir?", "Best time to go?"), ask("¿Está la roca seca?", "Is the rock dry?"))
        if (m.hits.isNotEmpty()) return listOf(ask("Que no haya hecho", "Not done yet"), ask("Solo con topo", "Only with topo"), ask("Un grado más", "One grade harder"))
        if (m.journalEntries.isNotEmpty()) return listOf(ask("¿Y el mes pasado?", "And last month?"), ask("Solo bloques", "Boulders only"), ask("Mis proyectos", "My projects"))
        return emptyList()
    }

    /** Frases de ejemplo para el chat vacío. */
    fun suggestions() = listOf(
        t("Bloques de 6a a 7a cerca de mí", "6a to 7a boulders near me"),
        t("¿Dónde voy este sábado?", "Where should I go this Saturday?"),
        t("Sector con más piedras a la sombra en Albarracín", "Sector with the most shaded boulders in Albarracín")
    )
}
