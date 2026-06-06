package com.meteomontana.android.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mapa interactivo a pantalla completa.
 *
 * Markers:
 *  - `existingBlocks`: bloques ya materializados de la escuela. PARKING/ZONE/BLOCK
 *    se dibujan con su forma y color propios.
 *  - `proposalAsBlock`: si se pasa, se dibuja como marker destacado amarillo ★
 *    en sus coords, y al pulsarlo se abre el mismo BlockDetailDialog (útil para
 *    que el admin vea la foto y líneas de la propuesta).
 *  - Si no hay `proposalAsBlock`, se usa el viejo marker (lat/lon/markerTitle).
 *
 * Al tocar cualquier marker → se abre `BlockDetailDialog` con foto/líneas/Cómo llegar.
 */
@Composable
fun FullScreenMapDialog(
    lat: Double,
    lon: Double,
    markerTitle: String = "",
    existingBlocks: List<Block> = emptyList(),
    proposalAsBlock: Block? = null,
    onDeleteBlock: ((String) -> Unit)? = null,
    onUpdateBlock: ((Block, com.meteomontana.android.data.api.dto.CreateBlockRequest) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    var selectedBlock by remember { mutableStateOf<Block?>(null) }
    var editingBlock by remember { mutableStateOf<Block?>(null) }
    var movingBlock by remember { mutableStateOf<Block?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mv = mapViewRef.value ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START   -> mv.onStart()
                Lifecycle.Event.ON_RESUME  -> mv.onResume()
                Lifecycle.Event.ON_PAUSE   -> mv.onPause()
                Lifecycle.Event.ON_STOP    -> mv.onStop()
                Lifecycle.Event.ON_DESTROY -> mv.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef.value?.apply { onPause(); onStop(); onDestroy() }
            mapViewRef.value = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    MapView(context).apply {
                        onCreate(null)
                        mapViewRef.value = this
                        setOnTouchListener { v, event ->
                            when (event.action) {
                                android.view.MotionEvent.ACTION_DOWN ->
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                android.view.MotionEvent.ACTION_UP,
                                android.view.MotionEvent.ACTION_CANCEL ->
                                    v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            false
                        }
                        getMapAsync { map ->
                            map.setStyle(Style.Builder().fromJson(OSM_TOPO_STYLE)) {
                                map.cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(lat, lon))
                                    .zoom(15.0)
                                    .build()

                                val iconFactory = IconFactory.getInstance(context)
                                val markerToBlock = mutableMapOf<Marker, Block>()

                                // Bloques existentes
                                existingBlocks.forEach { block ->
                                    val bmp = bitmapForBlock(block, isProposal = false)
                                    val marker = map.addMarker(
                                        MarkerOptions()
                                            .position(LatLng(block.lat, block.lon))
                                            .title(block.name)
                                            .icon(iconFactory.fromBitmap(bmp))
                                    )
                                    markerToBlock[marker] = block
                                }

                                // Marker principal: o bien la propuesta-as-block, o el simple
                                if (proposalAsBlock != null) {
                                    val bmp = bitmapForBlock(proposalAsBlock, isProposal = true)
                                    val marker = map.addMarker(
                                        MarkerOptions()
                                            .position(LatLng(lat, lon))
                                            .title("PROPUESTA · ${proposalAsBlock.name}")
                                            .icon(iconFactory.fromBitmap(bmp))
                                    )
                                    markerToBlock[marker] = proposalAsBlock
                                } else {
                                    val bmp = pinBitmap(
                                        android.graphics.Color.parseColor("#F59E0B"),
                                        "★", sizeDp = 52
                                    )
                                    map.addMarker(
                                        MarkerOptions()
                                            .position(LatLng(lat, lon))
                                            .title(markerTitle.ifBlank { "%.5f, %.5f".format(lat, lon) })
                                            .icon(iconFactory.fromBitmap(bmp))
                                    )
                                }

                                // Click handler: abre el dialog con detalles del block
                                map.setOnMarkerClickListener { marker ->
                                    val b = markerToBlock[marker]
                                    if (b != null && movingBlock == null) {
                                        selectedBlock = b
                                        true
                                    } else {
                                        false
                                    }
                                }

                                // Tap en cualquier punto del mapa cuando estamos
                                // en "modo mover": actualiza el block con las nuevas coords.
                                map.addOnMapClickListener { point ->
                                    val mb = movingBlock
                                    if (mb != null && onUpdateBlock != null) {
                                        val req = com.meteomontana.android.data.api.dto.CreateBlockRequest(
                                            type = mb.type,
                                            name = mb.name,
                                            lat = point.latitude,
                                            lon = point.longitude,
                                            photoPath = mb.photoPath,
                                            description = mb.description,
                                            lines = mb.lines.map { l ->
                                                com.meteomontana.android.data.api.dto.CreateBlockLineRequest(
                                                    name = l.name, grade = l.grade,
                                                    startType = l.startType,
                                                    linePath = l.linePath
                                                )
                                            }
                                        )
                                        onUpdateBlock(mb, req)
                                        movingBlock = null
                                        true
                                    } else false
                                }
                            }
                            map.uiSettings.isRotateGesturesEnabled = false
                            map.uiSettings.isTiltGesturesEnabled   = false
                        }
                        onStart(); onResume()
                    }
                }
            )

            // Banner "PULSA EN EL MAPA" cuando estamos moviendo
            movingBlock?.let { mb ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(Spacing.md)
                        .clip(MaterialTheme.shapes.small)
                        .background(Terra)
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                ) {
                    Text(
                        "📍 PULSA EN EL MAPA LA NUEVA POSICIÓN DE ${mb.name.uppercase()}",
                        style = EyebrowTextStyle, color = Color.White
                    )
                }
            }

            // Botón "✕ CERRAR"
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.md)
                    .clip(MaterialTheme.shapes.small)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                Text("✕ CERRAR", style = EyebrowTextStyle, color = Color.White)
            }

            // Coordenadas
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(Spacing.md)
                    .clip(MaterialTheme.shapes.small)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            ) {
                Text(
                    "%.6f, %.6f".format(lat, lon),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }
        }
    }

    // Dialog de detalles del block seleccionado
    selectedBlock?.let { block ->
        val isProposalBlock = block === proposalAsBlock
        BlockDetailDialog(
            block = block,
            isProposal = isProposalBlock,
            onEdit = if (onUpdateBlock != null && !isProposalBlock) ({
                editingBlock = block
                selectedBlock = null
            }) else null,
            onDelete = if (onDeleteBlock != null && !isProposalBlock) ({
                onDeleteBlock(block.id)
                selectedBlock = null
            }) else null,
            onDismiss = { selectedBlock = null }
        )
    }

    // Dialog de edición
    editingBlock?.let { block ->
        EditBlockDialog(
            block = block,
            onSave = { req ->
                onUpdateBlock?.invoke(block, req)
                editingBlock = null
            },
            onMoveByMap = {
                movingBlock = block
                editingBlock = null
            },
            onDismiss = { editingBlock = null }
        )
    }
}

// ─── Helpers de iconos ──────────────────────────────────────────────────────

/** Selecciona el bitmap correcto para un block según su tipo / si es propuesta. */
internal fun bitmapForBlock(block: Block, isProposal: Boolean): Bitmap {
    if (isProposal) {
        // Forma de piedra amarilla destacada con ★
        return pinBitmapBoulder(
            label = block.name.takeIf { it.isNotBlank() } ?: "★",
            fillColor = android.graphics.Color.parseColor("#F59E0B"),
            sizeDp = 52
        )
    }
    return when (block.type) {
        "PARKING" -> pinBitmap(android.graphics.Color.parseColor("#1D6DD6"), "P", sizeDp = 36)
        "ZONE"    -> pinBitmap(android.graphics.Color.parseColor("#1FA84E"), "Z", sizeDp = 36)
        else      -> pinBitmapBoulder(
            label = block.name.takeIf { it.isNotBlank() } ?: "?",
            fillColor = android.graphics.Color.parseColor("#C2410C"),
            sizeDp = 44
        )
    }
}

/**
 * Bitmap circular con relleno [colorInt], borde blanco, letra centrada.
 * Para PARKING/ZONE.
 */
internal fun pinBitmap(colorInt: Int, letter: String, sizeDp: Int = 40): Bitmap {
    val size = (sizeDp * 3).coerceAtLeast(64)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val cx = size / 2f
    val cy = size / 2f
    val r = size / 2f - 4f

    canvas.drawCircle(cx, cy, r, android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE; isAntiAlias = true
    })
    canvas.drawCircle(cx, cy, r - 4f, android.graphics.Paint().apply {
        color = colorInt; isAntiAlias = true
    })
    canvas.drawText(letter, cx, cy + size * 0.13f, android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = size * 0.5f; isFakeBoldText = true; isAntiAlias = true
    })
    return bmp
}

/**
 * Bitmap con forma de piedra: polígono irregular de 7 vértices con jitter
 * en los radios (parece roca natural). Relleno [fillColor], borde blanco grueso,
 * sombra inferior sutil y el [label] centrado en blanco.
 */
internal fun pinBitmapBoulder(label: String, fillColor: Int, sizeDp: Int = 44): Bitmap {
    val size = (sizeDp * 3).coerceAtLeast(80)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val cx = size / 2f
    val cy = size / 2f
    val baseR = size / 2f - 6f

    // Jitter pseudo-aleatorio determinista (basado en hash del label para que la
    // misma piedra siempre tenga la misma forma)
    val seed = label.hashCode()
    fun jitter(i: Int): Float {
        val v = (seed xor (i * 2654435761.toInt())) and 0xFFFF
        // 0.78..1.0
        return 0.78f + (v % 1000) / 1000f * 0.22f
    }

    // Construir polígono de 7 vértices con radio aleatorio
    val vertexCount = 7
    val path = android.graphics.Path()
    for (i in 0 until vertexCount) {
        val angle = (Math.PI * 2 * i / vertexCount).toFloat() - (Math.PI / 2).toFloat()
        val r = baseR * jitter(i)
        val x = cx + r * cos(angle)
        val y = cy + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()

    // Sombra inferior sutil
    canvas.drawOval(
        cx - baseR * 0.7f, cy + baseR * 0.7f,
        cx + baseR * 0.7f, cy + baseR * 0.95f,
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb(60, 0, 0, 0); isAntiAlias = true
        }
    )

    // Borde blanco (path expandido — dibujamos blanco grueso bajo el relleno)
    canvas.drawPath(path, android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 12f; strokeJoin = android.graphics.Paint.Join.ROUND
        isAntiAlias = true
    })

    // Relleno
    canvas.drawPath(path, android.graphics.Paint().apply {
        color = fillColor; isAntiAlias = true
        style = android.graphics.Paint.Style.FILL
    })

    // Sombreado interno arriba-izquierda (un poco más claro)
    canvas.drawPath(path, android.graphics.Paint().apply {
        color = android.graphics.Color.argb(40, 255, 255, 255); isAntiAlias = true
        style = android.graphics.Paint.Style.FILL
    }.also {
        // No clip - just slight overlay; left as-is for simplicity
    })

    // Texto
    val textSize = if (label.length <= 2) size * 0.42f else size * 0.32f
    canvas.drawText(label, cx, cy + textSize * 0.35f, android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textAlign = android.graphics.Paint.Align.CENTER
        this.textSize = textSize; isFakeBoldText = true; isAntiAlias = true
        setShadowLayer(4f, 0f, 1f, android.graphics.Color.argb(180, 0, 0, 0))
    })

    return bmp
}

// Estilo topográfico (igual que el selector "Topográfico" en SchoolMap)
private val OSM_TOPO_STYLE = """
{"version":8,"sources":{"topo":{"type":"raster","tiles":[
  "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
  "https://b.tile.opentopomap.org/{z}/{x}/{y}.png",
  "https://c.tile.opentopomap.org/{z}/{x}/{y}.png"
],"tileSize":256,"attribution":"© OpenTopoMap (CC-BY-SA)"}},"layers":[{"id":"topo","type":"raster","source":"topo"}]}
""".trimIndent()
