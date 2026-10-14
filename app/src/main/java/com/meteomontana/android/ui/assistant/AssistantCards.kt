package com.meteomontana.android.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.domain.model.AssistantBreakdown
import com.meteomontana.android.domain.model.AssistantDay
import com.meteomontana.android.domain.model.AssistantRecommendation
import com.meteomontana.android.domain.model.AssistantSchoolCard
import com.meteomontana.android.domain.model.AssistantSector
import com.meteomontana.android.domain.model.AssistantStone
import com.meteomontana.android.domain.model.AssistantWeather
import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.ui.screens.detail.GradePill
import com.meteomontana.android.ui.screens.schools.MiniTopoThumbnail
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Ok
import com.meteomontana.android.ui.theme.Rain
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.Warn
import com.meteomontana.android.ui.theme.scoreColor

// Tarjetas de resultados del asistente (escuelas recomendadas, desglose por sectores, el tiempo, vías encontradas y lo
// suyo del diario). Solo pintan datos reales que ya vienen calculados. Espejo de AssistantCards/Hits/Weather/Journal
// (iOS). Los textos salen de [AssistantPresenter]; las palabras sueltas, de [AssistantWords].

private val CardShape = RoundedCornerShape(2.dp)

@Composable
private fun cardModifier(modifier: Modifier = Modifier) = modifier
    .fillMaxWidth()
    .clip(CardShape)
    .background(MaterialTheme.colorScheme.surface)
    .border(1.dp, MaterialTheme.colorScheme.outline, CardShape)

@Composable
private fun MonoText(text: String, size: Int, color: Color, modifier: Modifier = Modifier, spacing: Float = 0f) =
    Text(text, color = color, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = size.sp,
        letterSpacing = spacing.sp, modifier = modifier)

/** Las pocas palabras sueltas de las tarjetas, en el idioma de la app. */
class AssistantWords(lang: String) {
    private val en = lang.lowercase().startsWith("en")
    private fun t(es: String, english: String) = if (en) english else es
    val open = t("ABRIR", "OPEN")
    fun openSchool(name: String) = t("ABRIR ${name.uppercase()}", "OPEN ${name.uppercase()}")
    val rain = t("LLUVIA", "RAIN")
    val dry = t("SECO", "DRY")
    val noSector = t("Sin sector", "No sector")
    val unknownOrientationHeader = t("SIN ORIENTACIÓN VOTADA (podrían cumplir)", "NO VOTED ORIENTATION (they might match)")
    val noOrientation = t("SIN ORIENTACIÓN ASIGNADA", "NO ORIENTATION ASSIGNED")
    val now = t("AHORA", "NOW")
    val byDay = t("POR DÍAS", "BY DAY")
    val nextHours = t("PRÓXIMAS HORAS", "NEXT HOURS")
    fun forClimbing(label: String) = t("Para escalar: $label", "For climbing: $label")
    val rockWet = t("La roca está húmeda.", "The rock is damp.")
    val rockDry = t("La roca está seca.", "The rock is dry.")
    fun bestWindow(a: String, b: String) = t("Mejor ventana: $a–$b", "Best window: $a–$b")
    fun linesOfGrade(n: Int) = t("$n vías o bloques de ese grado", "$n routes or boulders of that grade")
    fun linesTotal(n: Int) = t("$n vías y bloques en total", "$n routes and boulders in total")
    val openSchoolCd = t("Abrir escuela", "Open school")
}

// ── Escuelas recomendadas ─────────────────────────────────────────────────────────────────────────────────────────

@Composable
fun AssistantRecommendationView(
    recommendation: AssistantRecommendation,
    presenter: AssistantPresenter,
    words: AssistantWords,
    onOpenSchool: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        recommendation.schools.forEachIndexed { index, card ->
            AssistantSchoolCardView(index + 1, card, recommendation.forecastAvailable, recommendation.filtered, presenter, words, onOpenSchool)
        }
    }
}

@Composable
private fun AssistantSchoolCardView(
    rank: Int, card: AssistantSchoolCard, showForecast: Boolean, filtered: Boolean,
    presenter: AssistantPresenter, words: AssistantWords, onOpenSchool: (String) -> Unit
) {
    Column(cardModifier().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("$rank", color = Terra, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text(card.name, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                val subtitle = listOfNotNull(
                    card.distanceKm?.let { "%.0f km".format(it) },
                    card.rockType?.takeIf { it.isNotEmpty() }?.let { CatalogLabelsAdapter.rock(it) }
                ).joinToString(" · ")
                if (subtitle.isNotEmpty()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            MonoText(words.open, 10, Terra, spacing = 1.8f,
                modifier = Modifier.border(1.dp, Terra, CardShape).clickable { onOpenSchool(card.schoolId) }.padding(horizontal = 10.dp, vertical = 7.dp))
        }
        if (showForecast && card.days.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                card.days.forEach { d -> AssistantDayCell(d, presenter, words, Modifier.weight(1f)) }
            }
        }
        MonoText(if (filtered) words.linesOfGrade(card.lineCount) else words.linesTotal(card.lineCount), 11, MaterialTheme.colorScheme.onBackground)
    }
}

/** Un día del pronóstico: abreviatura, score con su color y lluvia. */
@Composable
private fun AssistantDayCell(day: AssistantDay, presenter: AssistantPresenter, words: AssistantWords, modifier: Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.background).border(1.dp, MaterialTheme.colorScheme.outline).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        MonoText(presenter.dayLabel(day.date), 9, MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${day.score}", color = scoreColor(day.score), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        MonoText(if (day.rainy) words.rain else words.dry, 9, if (day.rainy) Rain else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Desglose por sectores ─────────────────────────────────────────────────────────────────────────────────────────

@Composable
fun AssistantBreakdownView(
    breakdown: AssistantBreakdown,
    presenter: AssistantPresenter,
    words: AssistantWords,
    onOpen: (schoolId: String, viaId: String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        breakdown.sectors.forEach { sector -> AssistantSectorView(sector, breakdown.sun, breakdown.school.id, presenter, words, onOpen) }
        MonoText(words.openSchool(breakdown.school.name), 10, Color.White, spacing = 1.8f,
            modifier = Modifier.fillMaxWidth().clip(CardShape).background(Terra)
                .clickable { onOpen(breakdown.school.id, null) }.padding(vertical = 10.dp))
    }
}

@Composable
private fun AssistantSectorView(
    sector: AssistantSector, sun: String?, schoolId: String,
    presenter: AssistantPresenter, words: AssistantWords, onOpen: (String, String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val hasStones = sector.stones.isNotEmpty() || sector.unknownStones.isNotEmpty()
    Column(cardModifier()) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = hasStones) { expanded = !expanded }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(sector.name ?: words.noSector, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(presenter.sectorSummary(sector, sun), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            if (hasStones) Icon(if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        if (expanded) {
            Column(Modifier.padding(bottom = 6.dp)) {
                sector.stones.forEach { AssistantStoneRow(it, true, schoolId, presenter, onOpen) }
                if (sector.unknownStones.isNotEmpty()) {
                    MonoText(words.unknownOrientationHeader, 9, MaterialTheme.colorScheme.onSurfaceVariant, spacing = 1.4f,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp))
                    sector.unknownStones.forEach { AssistantStoneRow(it, false, schoolId, presenter, onOpen) }
                }
            }
        }
    }
}

/** Una piedra del desglose. Si tiene alguna vía, al tocarla se abre la piedra (a través de esa vía); si no, solo se muestra. */
@Composable
private fun AssistantStoneRow(stone: AssistantStone, confirmed: Boolean, schoolId: String, presenter: AssistantPresenter, onOpen: (String, String?) -> Unit) {
    val line = stone.firstLineId
    Row(
        Modifier.fillMaxWidth().then(if (line != null) Modifier.clickable { onOpen(schoolId, line) } else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        stone.photoPath?.takeIf { it.isNotEmpty() }?.let { MiniTopoThumbnail(it, stone.linePath, stone.grade, Modifier.size(44.dp)) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stone.name, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                GradePill(stone.grade, small = true)
            }
            stone.lineName?.takeIf { it.isNotEmpty() && it != stone.name }?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        MonoText(presenter.stoneLabel(stone), 10, if (confirmed) Ok else MaterialTheme.colorScheme.onSurfaceVariant)
        if (line != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
    }
}

// ── El tiempo ─────────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Tarjeta del tiempo: responde a "¿va a llover donde estoy?", "¿qué tal el viento en Zarzalejo?"... Enseña siempre lo
 * esencial (temperatura, viento, humedad, lluvia próxima y cómo está para escalar) y destaca lo que se preguntó. Son los
 * mismos datos y el mismo índice que la pantalla del tiempo: no pueden contradecirse.
 */
@Composable
fun AssistantWeatherCard(weather: AssistantWeather, presenter: AssistantPresenter, words: AssistantWords) {
    Column(cardModifier().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WeatherHeader(weather, presenter, words)
        RainBanner(weather, presenter)
        if (weather.days.isNotEmpty()) DayRows(weather, presenter, words)
        // Con varios días pedidos, las horas de hoy no son lo que se preguntó: se dejan para una sola fecha.
        if (weather.hours.isNotEmpty() && weather.days.size <= 1) HourStrip(weather, presenter, words)
        ClimbingRow(weather, presenter, words)
    }
}

@Composable
private fun WeatherHeader(w: AssistantWeather, p: AssistantPresenter, words: AssistantWords) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (w.myLocation) Icon(Icons.Outlined.LocationOn, null, tint = Terra, modifier = Modifier.size(12.dp))
                    Text(p.weatherTitle(w), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                MonoText(words.now, 9, MaterialTheme.colorScheme.onSurfaceVariant, spacing = 1.6f)
            }
            Text("${Math.round(w.now.temperature)}°", color = MaterialTheme.colorScheme.onBackground, fontFamily = Serif,
                fontWeight = FontWeight.Bold, fontSize = 34.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("${Math.round(w.now.windKmh)} km/h", w.topic == "WIND")
            Metric("${Math.round(w.now.humidity)} %", w.topic == "HUMIDITY")
            Metric("${w.now.cloudCover} %", false)
        }
    }
}

@Composable
private fun Metric(value: String, highlight: Boolean) {
    MonoText(value, 11, if (highlight) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(CardShape).background(if (highlight) Terra else MaterialTheme.colorScheme.background)
            .border(1.dp, if (highlight) Terra else MaterialTheme.colorScheme.outline, CardShape).padding(horizontal = 8.dp, vertical = 4.dp))
}

@Composable
private fun RainBanner(w: AssistantWeather, p: AssistantPresenter) {
    val wet = w.rain.expected
    val tint = if (wet) Rain else Ok
    Row(
        Modifier.fillMaxWidth().clip(CardShape).background(tint.copy(alpha = 0.10f)).border(1.dp, tint.copy(alpha = 0.35f), CardShape).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Outlined.WaterDrop, null, tint = tint, modifier = Modifier.size(16.dp))
        Text(p.rainHeadline(w), color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp,
            fontWeight = if (w.topic == "RAIN") FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun DayRows(w: AssistantWeather, p: AssistantPresenter, words: AssistantWords) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MonoText(words.byDay, 9, MaterialTheme.colorScheme.onSurfaceVariant, spacing = 1.4f)
        w.days.forEachIndexed { index, day ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MonoText(p.dayLabel(day.date), 11, MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
                Text("${Math.round(day.tempMin)}° – ${Math.round(day.tempMax)}°", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp,
                    fontWeight = if (w.topic == "TEMPERATURE") FontWeight.SemiBold else FontWeight.Normal)
                Box(Modifier.weight(1f))
                if (day.precipitationMm >= 0.2) MonoText("${p.oneDecimal(day.precipitationMm)} mm", 11, MaterialTheme.colorScheme.onSurfaceVariant)
                MonoText("${day.score}", 11, MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
            }
            if (index < w.days.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** Tira de horas (barras = lluvia prevista). */
@Composable
private fun HourStrip(w: AssistantWeather, p: AssistantPresenter, words: AssistantWords) {
    val full = 34f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MonoText(words.nextHours, 9, MaterialTheme.colorScheme.onSurfaceVariant, spacing = 1.4f)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            w.hours.forEach { h ->
                Column(Modifier.width(26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    MonoText("${h.rainProbability}%", 8, if (h.rainProbability >= 60) Rain else MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.width(14.dp).height(full.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.width(14.dp).height(p.rainBarHeight(h.precipitationMm, full).dp).background(Rain))
                    }
                    MonoText("${Math.round(h.temperature)}°", 10, MaterialTheme.colorScheme.onBackground)
                    MonoText(p.hourLabel(h.time), 8, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ClimbingRow(w: AssistantWeather, p: AssistantPresenter, words: AssistantWords) {
    val c = w.climbing
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(width = 40.dp, height = 34.dp).border(1.dp, scoreColor(c.score)), contentAlignment = Alignment.Center) {
            Text("${c.score}", color = scoreColor(c.score), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(words.forClimbing(c.label), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(c.dryingMessage?.takeIf { it.isNotEmpty() } ?: if (c.rockWet) words.rockWet else words.rockDry,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            val a = c.bestWindowStart
            val b = c.bestWindowEnd
            if (a != null && b != null) MonoText(words.bestWindow(p.hourLabel(a), p.hourLabel(b)), 10, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Vías y bloques encontrados ────────────────────────────────────────────────────────────────────────────────────

/**
 * Resultados de una búsqueda simple en el chat: TODAS las vías o bloques que encontró el buscador, agrupados por escuela
 * y, dentro, por sector. Escuelas y sectores se pliegan y se despliegan con un toque en su cabecera; cada fila abre esa
 * vía en su piedra (igual que desde Escuelas → Vías/Bloques).
 */
@Composable
fun AssistantHitsView(
    hits: List<LineSearchHit>,
    presenter: AssistantPresenter,
    words: AssistantWords,
    onOpen: (schoolId: String, viaId: String?) -> Unit
) {
    val groups = remember(hits) { presenter.groupAllHits(hits) }
    // Escuelas desplegadas y sectores plegados ("schoolId|sector"). Con muchos resultados solo empieza desplegada la
    // primera escuela: no se pinta (ni se cargan las miniaturas de) lo que no se ve.
    var expanded by remember(hits) { mutableStateOf(presenter.initiallyExpanded(groups)) }
    var collapsedSectors by remember(hits) { mutableStateOf(setOf<String>()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.forEach { group ->
            val open = group.id in expanded
            Column(cardModifier()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).clickable { expanded = if (open) expanded - group.id else expanded + group.id }
                            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(if (open) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text(group.schoolName, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                            modifier = Modifier.weight(1f))
                        MonoText("${group.total}", 11, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, words.openSchoolCd, tint = Terra,
                        modifier = Modifier.clickable { onOpen(group.id, null) }.padding(horizontal = 12.dp, vertical = 10.dp).size(18.dp))
                }
                if (open) group.sectors.forEach { sector ->
                    // Cabecera de sector: solo si hay más de uno o tiene nombre (un único "sin sector" no la necesita).
                    val showsHeader = group.sectors.size > 1 || sector.name != null
                    val key = presenter.sectorKey(group.id, sector.id)
                    val sectorOpen = !showsHeader || key !in collapsedSectors
                    if (showsHeader) Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                            .clickable { collapsedSectors = if (key in collapsedSectors) collapsedSectors - key else collapsedSectors + key }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(if (sectorOpen) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                        MonoText((sector.name ?: words.noSector).uppercase(), 10, MaterialTheme.colorScheme.onSurfaceVariant, spacing = 1.4f, modifier = Modifier.weight(1f))
                        MonoText("${sector.hits.size}", 10, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (sectorOpen) sector.hits.forEach { AssistantHitRow(it, presenter, words, onOpen) }
                }
            }
        }
    }
}

/** Una vía o bloque encontrado: miniatura del topo, nombre, grado coloreado, piedra y orientación votada (o aviso). */
@Composable
private fun AssistantHitRow(hit: LineSearchHit, presenter: AssistantPresenter, words: AssistantWords, onOpen: (String, String?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(hit.schoolId, hit.lineId) }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        hit.photoPath?.takeIf { it.isNotEmpty() }?.let { MiniTopoThumbnail(it, hit.linePath, hit.grade, Modifier.size(44.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(hit.lineName ?: hit.blockName, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                GradePill(hit.grade, small = true)
                val kind = com.meteomontana.android.domain.util.SchoolKind.from(hit.discipline)
                if (kind.isSingle) com.meteomontana.android.ui.components.SchoolKindIcons(kind, height = 13.dp)
            }
            if (hit.lineName != null && hit.blockName.isNotEmpty()) {
                Text(hit.blockName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val avg = hit.rating
            val votes = hit.ratingCount ?: 0
            if (avg != null && votes > 0) MonoText(presenter.starsLabel(avg, votes), 10, Warn)
            val o = hit.orientation
            if (!o.isNullOrEmpty()) MonoText(CatalogLabelsAdapter.aspect(o), 10, MaterialTheme.colorScheme.onSurfaceVariant)
            else MonoText(words.noOrientation, 9, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
    }
}

// ── Lo suyo del diario ────────────────────────────────────────────────────────────────────────────────────────────

/** Las entradas de SU diario que contesta el asistente ("lo que he hecho la última semana"): lista tocable, la más reciente primero. */
@Composable
fun AssistantJournalListView(
    entries: List<JournalSession>,
    total: Int,
    presenter: AssistantPresenter,
    onOpen: (schoolId: String, viaId: String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(cardModifier()) {
            entries.forEach { e ->
                val school = e.schoolId
                Row(
                    Modifier.fillMaxWidth().then(if (school != null) Modifier.clickable { onOpen(school, e.lineId) } else Modifier)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(e.blockName, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            GradePill(e.grade, small = true)
                        }
                        // "Albarracín · 8 oct": escuela y día en que se apuntó.
                        Text(listOfNotNull(e.schoolName, presenter.dateRange(e.date, null)).joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (school != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                }
            }
        }
        presenter.moreNote(total, entries.size)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
    }
}
