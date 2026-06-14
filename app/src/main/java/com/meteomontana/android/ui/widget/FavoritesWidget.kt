package com.meteomontana.android.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.meteomontana.android.R
import com.meteomontana.android.domain.port.LocationProvider
import com.meteomontana.android.data.saved.CachedSchoolsRepository
import com.meteomontana.android.domain.usecase.favorites.GetMyFavoritesUseCase
import com.meteomontana.android.domain.usecase.schools.GetTodayScoresUseCase
import com.meteomontana.android.ui.theme.Ink
import com.meteomontana.android.ui.theme.Ink2
import com.meteomontana.android.ui.theme.Ink2Dark
import com.meteomontana.android.ui.theme.Ink3
import com.meteomontana.android.ui.theme.Ink3Dark
import com.meteomontana.android.ui.theme.InkDark
import com.meteomontana.android.ui.theme.Rule
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.TerraDark
import com.meteomontana.android.ui.theme.scoreColor
import com.meteomontana.android.ui.theme.scoreTextColor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.meteomontana.android.domain.util.Geo
import kotlin.math.roundToInt

/**
 * Widget "Favoritas hoy" — diseño de tarjetas.
 *
 * Cada escuela es una tarjeta idéntica: bloque de score coloreado a la
 * izquierda, nombre serif + línea de contexto (distancia · estilo · roca) a
 * la derecha, y un heatmap horario a todo lo ancho debajo. Todas iguales,
 * ordenadas por score descendente.
 *
 * Datos sin red extra: el catálogo cacheado (SQLDelight) da estilo/roca/
 * coords y LocationProvider la última ubicación conocida para el Haversine.
 *
 * Adaptable: SizeMode.Exact recompone al redimensionar; LocalSize decide
 * cuántas tarjetas caben. Si no caben todas, "+N MÁS EN LA APP". Si la red
 * falla se pinta el último estado guardado en SharedPreferences. Tap en una
 * tarjeta → detalle (deep link targetType="school"); tap ↻ → recarga.
 */
class FavoritesWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadWidgetState(context)
        provideContent { WidgetContent(state) }
    }
}

class FavoritesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FavoritesWidget()
}

/** Acción del botón ↻: vuelve a ejecutar provideGlance (red + repintado). */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        FavoritesWidget().updateAll(context)
    }
}

// ─────────────────────────── datos ───────────────────────────

@Serializable
data class WidgetSchool(
    val id: String,
    val name: String,
    val score: Int,
    val dryRock: Boolean,
    // defaults → los estados cacheados con formatos anteriores siguen decodificando
    val hours: List<Int> = emptyList(),
    val style: String? = null,
    val rock: String? = null,
    val distanceKm: Int? = null
)

@Serializable
data class WidgetState(
    val schools: List<WidgetSchool> = emptyList(),
    val updatedAt: Long = 0L,
    val signedOut: Boolean = false
)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun getMyFavorites(): GetMyFavoritesUseCase
    fun getTodayScores(): GetTodayScoresUseCase
    fun cachedSchools(): CachedSchoolsRepository
    fun locationProvider(): LocationProvider
}

private const val PREFS = "favorites_widget"
private const val KEY_STATE = "state"
private const val MAX_SCHOOLS = 8
private const val HEATMAP_CELLS = 12
private val json = Json { ignoreUnknownKeys = true }

private suspend fun loadWidgetState(context: Context): WidgetState {
    val app = context.applicationContext
    val entryPoint = EntryPointAccessors.fromApplication(app, WidgetEntryPoint::class.java)
    val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val fresh = runCatching {
        val favorites = entryPoint.getMyFavorites().invoke().take(MAX_SCHOOLS)
        if (favorites.isEmpty()) return@runCatching WidgetState(updatedAt = System.currentTimeMillis())
        val scores = entryPoint.getTodayScores().invoke(favorites.map { it.id })
            .associateBy { it.id }
        // Contexto local, sin red: catálogo cacheado (estilo/roca/coords) y
        // última ubicación conocida. Si cualquiera falta, esa parte se omite.
        val catalog = runCatching { entryPoint.cachedSchools().load() }
            .getOrNull().orEmpty().associateBy { it.id }
        val loc = runCatching { entryPoint.locationProvider().current() }.getOrNull()

        val schools = favorites.map { fav ->
            val s = scores[fav.id]
            val cat = catalog[fav.id]
            WidgetSchool(
                id = fav.id,
                name = fav.name,
                score = s?.todayScore ?: -1,
                dryRock = s?.dryRock ?: true,
                hours = s?.hourlyScores?.take(HEATMAP_CELLS).orEmpty(),
                style = cat?.style,
                rock = cat?.rockType ?: fav.rockType,
                distanceKm = if (loc != null && cat != null)
                    Geo.haversineKm(loc.lat, loc.lon, cat.lat, cat.lon).roundToInt()
                else null
            )
        }.sortedByDescending { it.score }
        WidgetState(schools = schools, updatedAt = System.currentTimeMillis())
    }.getOrNull()

    if (fresh != null) {
        prefs.edit().putString(KEY_STATE, json.encodeToString(fresh)).apply()
        return fresh
    }
    // Red caída o sesión no iniciada → último estado conocido
    val cached = prefs.getString(KEY_STATE, null)
        ?.let { runCatching { json.decodeFromString<WidgetState>(it) }.getOrNull() }
    return cached ?: WidgetState(signedOut = true)
}

/** "42 KM · BOULDER · CALIZA" — solo con las partes que existan. */
private fun metaLine(s: WidgetSchool): String? =
    listOfNotNull(
        s.distanceKm?.let { "$it KM" },
        s.style?.uppercase(Locale.ROOT),
        s.rock?.uppercase(Locale.ROOT)
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")

// ─────────────────────────── UI ───────────────────────────

private val InkText = ColorProvider(day = Ink, night = InkDark)
private val Ink2Text = ColorProvider(day = Ink2, night = Ink2Dark)
private val Ink3Text = ColorProvider(day = Ink3, night = Ink3Dark)
private val TerraText = ColorProvider(day = Terra, night = TerraDark)

@Composable
private fun WidgetContent(state: WidgetState) {
    // Presupuesto vertical (dp): overhead = cabecera + padding ~44dp; cada
    // tarjeta + su separación ~70dp. LocalSize decide cuántas caben.
    val size = LocalSize.current
    val hasData = !state.signedOut && state.schools.isNotEmpty()
    val cardsFit = ((size.height.value - 44f) / 70f).toInt().coerceAtLeast(1)
    val truncated = hasData && state.schools.size > cardsFit
    val shown = if (truncated) state.schools.take((cardsFit - 1).coerceAtLeast(1)) else state.schools
    val hidden = state.schools.size - shown.size

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg), contentScale = ContentScale.FillBounds)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clickable(actionStartActivity(openAppIntent(null)))
    ) {
        Header(state.updatedAt)
        Spacer(GlanceModifier.height(8.dp))

        if (!hasData) {
            EmptyState(state.signedOut)
        } else {
            shown.forEachIndexed { i, school ->
                if (i > 0) Spacer(GlanceModifier.height(6.dp))
                SchoolCard(school)
            }
            if (truncated && hidden > 0) {
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    "+ $hidden MÁS EN LA APP",
                    style = TextStyle(color = TerraText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun Header(updatedAt: Long) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "FAVORITAS HOY",
            style = TextStyle(color = TerraText, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.defaultWeight()
        )
        if (updatedAt > 0L) {
            Text(formatTime(updatedAt), style = TextStyle(color = Ink3Text, fontSize = 10.sp))
        }
        Text(
            "↻",
            style = TextStyle(color = Ink2Text, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier
                .padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                .clickable(actionRunCallback<RefreshWidgetAction>())
        )
    }
}

/** Tarjeta de escuela: bloque de score + nombre/meta + heatmap a lo ancho. */
@Composable
private fun SchoolCard(school: WidgetSchool) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(R.drawable.widget_card), contentScale = ContentScale.FillBounds)
            .padding(9.dp)
            .clickable(actionStartActivity(openAppIntent(school.id)))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreBlock(school.score)
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        school.name,
                        style = TextStyle(
                            color = InkText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    if (!school.dryRock) {
                        Spacer(GlanceModifier.width(4.dp))
                        Text("💧", style = TextStyle(fontSize = 11.sp))
                    }
                }
                metaLine(school)?.let {
                    Spacer(GlanceModifier.height(1.dp))
                    Text(it, style = TextStyle(color = Ink3Text, fontSize = 9.sp), maxLines = 1)
                }
            }
        }
        if (school.hours.isNotEmpty()) {
            Spacer(GlanceModifier.height(7.dp))
            HeatmapStrip(school.hours)
        }
    }
}

/** Cuadrado coloreado por score con el número centrado. */
@Composable
private fun ScoreBlock(score: Int) {
    val bg = if (score >= 0) scoreColor(score) else Rule
    val fg = if (score >= 0) scoreTextColor(score) else Ink3
    Box(
        modifier = GlanceModifier
            .size(38.dp)
            .background(ColorProvider(day = bg, night = bg))
            .cornerRadius(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (score >= 0) "$score" else "—",
            style = TextStyle(
                color = ColorProvider(day = fg, night = fg),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

/**
 * Tira de celdas coloreadas por score horario, sin separación (estilo PWA).
 * Cada celda reparte el ancho con defaultWeight → encaja en cualquier tamaño.
 */
@Composable
private fun HeatmapStrip(hours: List<Int>) {
    Row(modifier = GlanceModifier.fillMaxWidth().cornerRadius(3.dp)) {
        hours.forEach { score ->
            val c = if (score >= 0) scoreColor(score) else Rule
            Box(
                GlanceModifier
                    .defaultWeight()
                    .height(7.dp)
                    .background(ColorProvider(day = c, night = c))
            ) {}
        }
    }
}

@Composable
private fun EmptyState(signedOut: Boolean) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⛰", style = TextStyle(fontSize = 24.sp))
            Spacer(GlanceModifier.height(4.dp))
            Text(
                if (signedOut) "Inicia sesión para ver tus escuelas"
                else "Marca escuelas favoritas en la app",
                style = TextStyle(color = Ink2Text, fontSize = 12.sp)
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                "TOCA PARA ABRIR",
                style = TextStyle(color = TerraText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

private fun formatTime(ts: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

private fun openAppIntent(schoolId: String?): Intent =
    Intent()
        .setClassName("com.meteomontana.android", "com.meteomontana.android.MainActivity")
        .apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (schoolId != null) {
                putExtra("targetType", "school")
                putExtra("targetId", schoolId)
                // data único: que cada escuela genere un PendingIntent distinto
                data = android.net.Uri.parse("meteomontana://school/$schoolId")
            }
        }
