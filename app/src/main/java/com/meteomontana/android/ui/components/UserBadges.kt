package com.meteomontana.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.UserBadge
import com.meteomontana.android.domain.usecase.profile.GetUserBadgesUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verificado (admin) y aureola del título alrededor de la foto para CUALQUIER persona en cualquier sitio (perfil, feed,
 * comentarios, chats). Álvaro, 2026-10-19. Las vistas dicen «voy a enseñar a esta persona» y la tienda los pide juntos.
 * Espejo de UserBadgeViews.swift.
 */
@Singleton
class UserBadgeStore @Inject constructor(private val getUserBadges: GetUserBadgesUseCase) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _badges = MutableStateFlow<Map<String, UserBadge>>(emptyMap())
    val badges: StateFlow<Map<String, UserBadge>> = _badges.asStateFlow()
    private val asked = mutableSetOf<String>()
    private val pending = mutableSetOf<String>()
    private var flushing = false

    fun want(uid: String?) {
        if (uid.isNullOrBlank() || uid in asked) return
        pending += uid
        if (flushing) return
        flushing = true
        scope.launch {
            delay(200)
            val ids = pending.toList()
            pending.clear()
            flushing = false
            load(ids)
        }
    }

    /** Tras cambiar un título: se vuelve a pedir. */
    fun refresh(uid: String?) {
        if (uid.isNullOrBlank()) return
        asked -= uid
        want(uid)
    }

    private suspend fun load(uids: List<String>) {
        uids.filter { it !in asked }.chunked(150).forEach { chunk ->
            asked += chunk
            runCatching { getUserBadges(chunk) }
                .onSuccess { list -> _badges.value = _badges.value + list.associateBy { it.uid } }
                .onFailure { asked -= chunk.toSet() }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UserBadgeStoreEntryPoint {
    fun userBadgeStore(): UserBadgeStore
}

@Composable
fun rememberUserBadgeStore(): UserBadgeStore {
    val context = LocalContext.current.applicationContext
    return remember { EntryPointAccessors.fromApplication(context, UserBadgeStoreEntryPoint::class.java).userBadgeStore() }
}

/** El verificado y el título de una persona (null mientras llega o si no hay red). */
@Composable
fun rememberUserBadge(uid: String?): UserBadge? {
    val store = rememberUserBadgeStore()
    LaunchedEffect(uid) { store.want(uid) }
    val all by store.badges.collectAsState()
    return uid?.let { all[it] }
}

/** Color de la aureola: el del título especial, oro para el nivel 50, el de su título; nada si lo oculta o es Aspirante. */
private fun ringStyle(b: UserBadge): Pair<Color, Boolean>? {
    if (b.titleHidden) return null
    b.specialTitle?.takeIf { it.isNotBlank() }?.let {
        return (colorFromHexOrNull(b.specialColor) ?: Color(0xFFB8860B)) to true
    }
    if (b.titleLevel >= 50) return Color(0xFFD9A621) to true
    if (b.titleLevel <= 0) return null
    return titleColor(b.titleLevel) to false
}

/**
 * La aureola alrededor de la foto: se pone ANTES del `.size()/.clip()` de la imagen, así se dibuja por fuera del
 * círculo. Brillante y girando para «Señor de la Cumbre» y los títulos especiales.
 */
@Composable
fun Modifier.userRing(uid: String?, size: Dp): Modifier {
    val badge = rememberUserBadge(uid)
    val style = badge?.let { ringStyle(it) } ?: return this
    val (color, shiny) = style
    val angle = if (shiny) {
        val t by rememberInfiniteTransition(label = "aureola").animateFloat(
            0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "giro")
        t
    } else 0f
    val width = (size * 0.07f).coerceAtLeast(2.dp)
    return this.drawWithContent {
        drawContent()
        val stroke = width.toPx()
        val radius = this.size.minDimension / 2f + stroke / 2f
        if (shiny) {
            rotate(angle) {
                drawCircle(
                    brush = Brush.sweepGradient(listOf(color, Color.White.copy(alpha = 0.95f), color, color.copy(alpha = 0.7f), color)),
                    radius = radius, style = Stroke(stroke)
                )
            }
        } else {
            drawCircle(color = color, radius = radius, style = Stroke(stroke))
        }
    }
}

/** El check azul de verificado junto al nombre, solo si esa persona es admin. */
@Composable
fun VerifiedCheck(uid: String?, size: Dp = 14.dp) {
    val badge = rememberUserBadge(uid)
    if (badge?.verified == true) {
        Icon(Icons.Filled.Verified, contentDescription = stringResource(R.string.verified),
            tint = Color(0xFF1C9CF0), modifier = Modifier.size(size))
    }
}
