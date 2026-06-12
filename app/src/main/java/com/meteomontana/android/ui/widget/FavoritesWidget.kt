package com.meteomontana.android.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.meteomontana.android.domain.usecase.favorites.GetMyFavoritesUseCase
import com.meteomontana.android.domain.usecase.schools.GetTodayScoresUseCase
import com.meteomontana.android.ui.theme.Bg
import com.meteomontana.android.ui.theme.BgDark
import com.meteomontana.android.ui.theme.Ink
import com.meteomontana.android.ui.theme.Ink2
import com.meteomontana.android.ui.theme.Ink2Dark
import com.meteomontana.android.ui.theme.InkDark
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

/**
 * Widget "Favoritas hoy": score actual de las escuelas favoritas en la
 * pantalla de inicio. Tap en una fila → detalle de la escuela (reusa el
 * deep link targetType="school" de los pushes). Tap en ↻ → recarga.
 *
 * El sistema lo refresca cada hora (updatePeriodMillis); si la red falla
 * se pinta el último estado guardado en SharedPreferences.
 */
class FavoritesWidget : GlanceAppWidget() {

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
data class WidgetSchool(val id: String, val name: String, val score: Int, val dryRock: Boolean)

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
}

private const val PREFS = "favorites_widget"
private const val KEY_STATE = "state"
private const val MAX_SCHOOLS = 6
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
        WidgetState(
            schools = favorites.map { fav ->
                val s = scores[fav.id]
                WidgetSchool(fav.id, fav.name, s?.todayScore ?: -1, s?.dryRock ?: true)
            },
            updatedAt = System.currentTimeMillis()
        )
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

// ─────────────────────────── UI ───────────────────────────

private val PaperBg = ColorProvider(day = Bg, night = BgDark)
private val InkText = ColorProvider(day = Ink, night = InkDark)
private val Ink2Text = ColorProvider(day = Ink2, night = Ink2Dark)
private val TerraText = ColorProvider(day = Terra, night = TerraDark)

@Composable
private fun WidgetContent(state: WidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(PaperBg)
            .cornerRadius(8.dp)
            .padding(12.dp)
            .clickable(actionStartActivity(openAppIntent(null)))
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "FAVORITAS HOY",
                style = TextStyle(color = TerraText, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                "↻",
                style = TextStyle(color = Ink2Text, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier
                    .padding(horizontal = 4.dp)
                    .clickable(actionRunCallback<RefreshWidgetAction>())
            )
        }
        Spacer(GlanceModifier.height(6.dp))

        when {
            state.signedOut || state.schools.isEmpty() -> {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.signedOut) "Abre la app para iniciar sesión"
                        else "Marca escuelas favoritas en la app",
                        style = TextStyle(color = Ink2Text, fontSize = 12.sp)
                    )
                }
            }
            else -> {
                state.schools.forEach { school -> SchoolRow(school) }
            }
        }
    }
}

@Composable
private fun SchoolRow(school: WidgetSchool) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(actionStartActivity(openAppIntent(school.id))),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            school.name,
            style = TextStyle(color = InkText, fontSize = 13.sp),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight()
        )
        if (!school.dryRock) {
            Text("💧", style = TextStyle(fontSize = 11.sp))
            Spacer(GlanceModifier.width(4.dp))
        }
        ScoreBadge(school.score)
    }
}

@Composable
private fun ScoreBadge(score: Int) {
    val bg = if (score >= 0) scoreColor(score) else Color(0x00000000)
    val fg = if (score >= 0) scoreTextColor(score) else Color.Gray
    Box(
        modifier = GlanceModifier
            .background(ColorProvider(day = bg, night = bg))
            .cornerRadius(2.dp)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (score >= 0) "$score" else "—",
            style = TextStyle(
                color = ColorProvider(day = fg, night = fg),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

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
