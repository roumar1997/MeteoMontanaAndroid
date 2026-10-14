package com.meteomontana.android.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.usecase.help.GuideCatalog
import com.meteomontana.android.domain.usecase.help.GuideStep
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.terraFillColor
import com.meteomontana.android.util.AppLanguage

// Pintura de las guías por pantalla (2026-10-14): el foco sobre un elemento REAL de la pantalla, la tarjeta y el botón
// "? AYUDA". La lógica (qué guía, en qué paso, qué recuerda) vive en [GuideController], con tests; el contenido, en
// `GuideCatalog` (shared), igual que en iOS. Aquí solo se registra cada elemento y se dibuja.

val LocalGuideController = compositionLocalOf<GuideController> {
    error("Falta el GuideController: envolver la app en ProvideGuides")
}

/** Crea el [GuideController] de la app (idioma = el de la app en cada paso) y lo pone a disposición de todas las pantallas. */
@Composable
fun ProvideGuides(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val controller = remember {
        GuideController(SharedGuidePrefs(context)) { key -> GuideCatalog.byKey(key, AppLanguage.effective(context)) }
    }
    CompositionLocalProvider(LocalGuideController provides controller, content = content)
}

/** Marca este elemento como señalable por una guía (`id` = el `target` del `GuideStep`). `enabled = false` lo ignora. */
fun Modifier.guideTarget(id: String, enabled: Boolean = true): Modifier = composed {
    val controller = LocalGuideController.current
    DisposableEffect(id) { onDispose { controller.clearTarget(id) } }
    onGloballyPositioned { coords ->
        if (enabled && coords.isAttached) controller.setTarget(id, coords.boundsInRoot()) else controller.clearTarget(id)
    }
}

/**
 * Pone esta pantalla como "anfitriona" de la guía `key`: pinta el foco y la tarjeta encima de toda ella. Mientras está
 * en pantalla, su guía puede arrancar; al salir, deja de poder (y se cierra si estaba abierta).
 */
@Composable
fun GuideHost(key: String, modifier: Modifier = Modifier.fillMaxSize(), content: @Composable () -> Unit) {
    val controller = LocalGuideController.current
    DisposableEffect(key) {
        controller.registerHost(key)
        onDispose {
            controller.unregisterHost(key)
            if (controller.isMine(key, controller.activeKey)) controller.stop()
        }
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.onGloballyPositioned { origin = it.positionInRoot() }) {
        content()
        val guide = if (controller.isMine(key, controller.activeKey)) controller.guide else null
        if (guide != null && controller.index < guide.steps.size) {
            val step = guide.steps[controller.index]
            GuideOverlay(
                step = step,
                position = controller.index + 1,
                total = guide.steps.size,
                hole = controller.targets[step.target]?.translate(-origin),
                onNext = controller::next,
                onStop = controller::stop
            )
        }
    }
}

@Composable
private fun GuideOverlay(
    step: GuideStep,
    position: Int,
    total: Int,
    /** null = ese elemento no está en pantalla ahora: la tarjeta sale centrada, sin foco. */
    hole: Rect?,
    onNext: () -> Unit,
    onStop: () -> Unit
) {
    BackHandler(onBack = onStop)
    val density = LocalDensity.current
    val gap = with(density) { 18.dp.toPx() }
    val inset = with(density) { 6.dp.toPx() }
    val radius = with(density) { 12.dp.toPx() }
    val stroke = with(density) { 2.dp.toPx() }
    val focus = hole?.inflate(inset)
    val terra = Terra

    Box(
        Modifier
            .fillMaxSize()
            // Tocar fuera de la tarjeta NO cierra ni avanza: solo los botones (así no se salta una guía sin querer).
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                if (focus != null) addRoundRect(androidx.compose.ui.geometry.RoundRect(focus, CornerRadius(radius)))
            }
            drawPath(path, Color.Black.copy(alpha = 0.62f))
            if (focus != null) {
                drawRoundRect(terra, focus.topLeft, Size(focus.width, focus.height), CornerRadius(radius), style = Stroke(stroke))
            }
        }
        // Debajo del elemento si está en la mitad de arriba; encima si está abajo. Sin elemento: centrada.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val heightPx = with(density) { maxHeight.toPx() }
            val card: @Composable (Modifier) -> Unit = { m ->
                GuideCard(step, position, total, onNext, onStop, m)
            }
            when {
                focus == null -> card(Modifier.align(Alignment.Center))
                focus.center.y < heightPx * 0.55f -> card(
                    Modifier.align(Alignment.TopCenter)
                        .offset { IntOffset(0, minOf(focus.bottom + gap, heightPx * 0.62f).toInt()) }
                )
                else -> card(
                    Modifier.align(Alignment.BottomCenter)
                        .offset { IntOffset(0, -maxOf(heightPx - focus.top + gap, 0f).toInt()) }
                )
            }
        }
    }
}

@Composable
private fun GuideCard(
    step: GuideStep,
    position: Int,
    total: Int,
    onNext: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("$position/$total", color = Terra, fontFamily = Mono, fontWeight = FontWeight.Bold,
            fontSize = 10.sp, letterSpacing = 1.6.sp)
        Text(step.title, color = MaterialTheme.colorScheme.onBackground, fontFamily = Serif,
            fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(step.body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.5.sp, lineHeight = 20.sp)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.guide_exit),
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Mono, fontWeight = FontWeight.Bold,
                fontSize = 11.sp, letterSpacing = 0.8.sp,
                modifier = Modifier.defaultMinSize(minHeight = 40.dp).clickable(onClick = onStop).padding(vertical = 10.dp)
            )
            Box(Modifier.weight(1f))
            Text(
                stringResource(if (position < total) R.string.guide_next else R.string.guide_done),
                color = Color.White, fontFamily = Mono, fontWeight = FontWeight.Bold,
                fontSize = 11.sp, letterSpacing = 0.8.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(terraFillColor())
                    .clickable(onClick = onNext)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }
}

/**
 * "? AYUDA": el MISMO botón en todas las pantallas (símbolo "?" + palabra). Con la palabra se entiende qué hace; "GUÍA"
 * se descartó porque en una app de escalada se lee como guía de escalada. Parpadea las primeras visitas de la pantalla
 * y se apaga al pulsarlo.
 */
@Composable
fun GuidePill(topicKey: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val controller = LocalGuideController.current
    var pulsing by remember { mutableStateOf(false) }
    LaunchedEffect(topicKey) { pulsing = controller.registerVisitAndShouldPulse(topicKey) }
    // Pulsado el "?" (o cualquier ayuda de la pantalla), deja de parpadear en el acto.
    val used = controller.usedVersion
    LaunchedEffect(used) { if (controller.isUsed(topicKey)) pulsing = false }

    val help = stringResource(R.string.guide_help)
    Row(
        modifier
            .then(if (pulsing) Modifier.pulseRing() else Modifier)
            .defaultMinSize(minHeight = 40.dp)
            .clip(CircleShape)
            .background(Terra.copy(alpha = 0.10f))
            .border(1.2.dp, Terra, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = Terra, modifier = Modifier.size(15.dp))
        Text(help, color = Terra, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp)
    }
}

/** Anillo que se expande y se desvanece detrás del botón. Solo existe (y anima) mientras toca parpadear. */
private fun Modifier.pulseRing(): Modifier = composed {
    val ring = rememberInfiniteTransition(label = "guide-pulse")
    val t by ring.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "t")
    drawBehind {
        val s = 0.95f + 0.55f * t
        val w = size.width * s
        val h = size.height * s
        drawRoundRect(
            Terra.copy(alpha = 0.9f * (1f - t)),
            Offset((size.width - w) / 2f, (size.height - h) / 2f), Size(w, h),
            CornerRadius(h / 2f), style = Stroke(2.dp.toPx())
        )
    }
}

/** "Explicarme cada pantalla": interruptor general de las guías (bienvenida y Ajustes). */
@Composable
fun GuidesToggleRow(modifier: Modifier = Modifier) {
    val controller = LocalGuideController.current
    var on by remember { mutableStateOf(controller.enabled) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(R.string.guides_toggle_title), color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(stringResource(R.string.guides_toggle_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
        }
        Switch(checked = on, onCheckedChange = { on = it; controller.enabled = it })
    }
}
