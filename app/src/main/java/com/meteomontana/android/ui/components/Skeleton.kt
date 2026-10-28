package com.meteomontana.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.meteomontana.android.R
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Esqueletos de carga (octubre de 2026). Regla de la app:
//  - CONTENIDO que carga (listas, feed, diario, fichas) → esqueleto con brillo con la forma de lo que viene.
//  - ACCIONES (enviar, guardar, unirse…) → spinner pequeño dentro del botón, como siempre.
// El esqueleto no sale si los datos llegan en menos de 0,2 s ([SkeletonDelay]). Espejo de Theme/Skeleton.swift (iOS).

/** Color de los "huesos" del esqueleto. */
@Composable
private fun boneTone(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)

/**
 * Brillo que cruza el grupo de izquierda a derecha. Se aplica al GRUPO entero (no a cada hueso) y solo se pinta
 * encima de lo ya dibujado (SrcAtop), así la franja de luz recorre la pantalla de una vez.
 */
fun Modifier.shimmer(): Modifier = composed {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val light = Color.White.copy(alpha = if (dark) 0.07f else 0.55f)
    val progress by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-progress",
    )
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val band = maxOf(size.width * 0.45f, 120.dp.toPx())
            val x = -band + progress * (size.width + band)
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, light, Color.Transparent), startX = x, endX = x + band),
                blendMode = BlendMode.SrcAtop,
            )
        }
}

/** Un "hueso": rectángulo (ancho fijo o todo el ancho), pastilla o círculo. */
@Composable
fun Bone(width: Dp? = null, height: Dp, shape: Shape = RoundedCornerShape(3.dp)) {
    val base = if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()
    Box(base.height(height).clip(shape).background(boneTone()))
}

@Composable
fun BoneCircle(size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(boneTone()))
}

@Composable
fun BonePill(width: Dp, height: Dp) = Bone(width, height, RoundedCornerShape(50))

/** No enseña nada durante 0,2 s: si los datos llegan antes, no hay parpadeo de esqueleto. */
@Composable
fun SkeletonDelay(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(200); visible = true }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(150), label = "skeleton-delay")
    val label = stringResource(R.string.common_loading)
    Box(modifier.alpha(alpha).semantics { contentDescription = label }) { content() }
}

/** Lista de esqueletos con brillo y su retardo: [count] filas de la forma [row]. */
@Composable
fun SkeletonList(count: Int = 6, dividers: Boolean = true, modifier: Modifier = Modifier, row: @Composable () -> Unit) {
    SkeletonDelay(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().shimmer()) {
            repeat(count) {
                row()
                if (dividers) HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
            }
        }
    }
}

// ─── Formas de cada pantalla ──────────────────────────────────────────────────

/** Fila de persona/aviso/chat/comentario: avatar redondo + dos líneas. */
@Composable
fun SkeletonPersonRow(avatar: Dp = 44.dp, trailing: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        BoneCircle(avatar)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Bone(150.dp, 14.dp)
            Bone(220.dp, 11.dp)
        }
        if (trailing) BonePill(64.dp, 28.dp)
    }
}

/** Fila de vía/bloque del diario, proyectos o BLOQUES: pastilla del grado + nombre + chips + lugar. */
@Composable
fun SkeletonGradeRow() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Bone(52.dp, 38.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Bone(160.dp, 17.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { BonePill(62.dp, 18.dp); BonePill(70.dp, 18.dp) }
            Bone(200.dp, 11.dp)
        }
        Bone(70.dp, 11.dp)
    }
}

/** Fila de escuela (lista de Escuelas, guardadas, aviso de fin de semana). */
@Composable
fun SkeletonSchoolRow() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Bone(64.dp, 72.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Bone(160.dp, 16.dp)
            Bone(110.dp, 12.dp)
            Bone(null, 14.dp)
        }
    }
}

/** Tarjeta del feed: cabecera (avatar, nombre, etiqueta, hora) + hueco de la foto. */
@Composable
fun SkeletonFeedCard() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BoneCircle(40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { Bone(110.dp, 14.dp); Bone(80.dp, 10.dp) }
            Bone(72.dp, 10.dp)
        }
        Bone(null, 300.dp, RoundedCornerShape(0.dp))
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            repeat(3) { Bone(22.dp, 22.dp) }
        }
    }
}

/** Tarjeta de quedada: título, dos líneas y fila de avatares. */
@Composable
fun SkeletonCard() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Bone(180.dp, 17.dp)
        Bone(240.dp, 11.dp)
        Bone(140.dp, 11.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(3) { BoneCircle(26.dp) } }
    }
}

/** Contenedor de pantalla completa con brillo y retardo, alineado arriba. */
@Composable
private fun SkeletonScreen(content: @Composable () -> Unit) {
    SkeletonDelay(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().shimmer().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            content()
        }
    }
}

/** Cabecera de perfil: avatar grande, nombre, usuario, contadores y unas filas. */
@Composable
fun SkeletonProfile() {
    SkeletonDelay(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().shimmer().padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BoneCircle(84.dp)
            Bone(150.dp, 20.dp)
            Bone(100.dp, 12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                repeat(3) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Bone(34.dp, 18.dp); Bone(56.dp, 10.dp)
                    }
                }
            }
            Column { repeat(3) { SkeletonGradeRow() } }
        }
    }
}

/** Tiempo: tarjeta del día de hoy + filas de días. */
@Composable
fun SkeletonWeather() = SkeletonScreen {
    Bone(120.dp, 11.dp)
    Bone(null, 150.dp)
    repeat(7) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Bone(44.dp, 14.dp); BoneCircle(28.dp)
            Box(Modifier.weight(1f)) { Bone(null, 10.dp) }
            Bone(50.dp, 14.dp)
        }
    }
}

/** Ficha de escuela al abrir: puntuación, 16 h y pestañas. */
@Composable
fun SkeletonSchoolDetail() = SkeletonScreen {
    Bone(220.dp, 26.dp)
    Bone(160.dp, 12.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Bone(84.dp, 84.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Bone(null, 14.dp); Bone(160.dp, 11.dp); Bone(120.dp, 11.dp)
        }
    }
    Bone(null, 64.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(4) { Box(Modifier.weight(1f)) { Bone(null, 30.dp, RoundedCornerShape(50)) } }
    }
    Bone(null, 220.dp)
}

/** Formulario (editar perfil, enviar escuela…): etiqueta + campo, varias veces. */
@Composable
fun SkeletonForm(fields: Int = 5) = SkeletonScreen {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BoneCircle(84.dp) }
    repeat(fields) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Bone(90.dp, 10.dp); Bone(null, 40.dp) }
    }
}

/** Hueco de una foto mientras llega: tono cálido de piedra con brillo (luego, el color medio de cada foto). */
@Composable
fun PhotoPlaceholder(modifier: Modifier = Modifier) {
    // shimmer() va POR FUERA del fondo: su brillo solo se pinta sobre lo que hay dibujado debajo.
    Box(modifier.shimmer().background(PhotoPlaceholderColor))
}

val PhotoPlaceholderColor = Color(0xFF998776)
