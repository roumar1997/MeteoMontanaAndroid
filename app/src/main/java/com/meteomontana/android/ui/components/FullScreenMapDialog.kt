package com.meteomontana.android.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
    /** Si !=null → contribución de POSITION_CORRECTION: dibuja marker GRIS en (lat,lon),
     *  marker ESTRELLA en (proposedLat, proposedLon) y línea terra entre ambos. */
    positionCorrectionNew: Pair<Double, Double>? = null,
    positionCorrectionTargetName: String? = null,
    onDeleteBlock: ((String) -> Unit)? = null,
    onUpdateBlock: ((Block, com.meteomontana.android.data.api.dto.CreateBlockRequest) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    // Última ubicación conocida del usuario → punto azul.
    val userLoc = rememberUserLocation()
    var selectedBlock by remember { mutableStateOf<Block?>(null) }
    var editingBlock by remember { mutableStateOf<Block?>(null) }
    var movingBlock by remember { mutableStateOf<Block?>(null) }
    var ghostPosition by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var mapStyle by remember { mutableStateOf("topo") }

    MapViewLifecycleEffect(mapViewRef)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // key fuerza re-crear el MapView cuando:
            //  - Llegan los blocks de la escuela (carga async tras pulsar en GESTIONAR).
            //  - El admin cambia el estilo de mapa (Topo/Satélite).
            //  - Cambia la posición candidata (ghost) para repintar el marker fantasma.
            androidx.compose.runtime.key(existingBlocks.size, mapStyle, ghostPosition, movingBlock?.id) {
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
                            map.setStyle(Style.Builder().fromJson(fullStyleJson(mapStyle))) {
                                map.cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(lat, lon))
                                    .zoom(15.0)
                                    .build()

                                val iconFactory = IconFactory.getInstance(context)
                                val markerToBlock = mutableMapOf<Marker, Block>()

                                // Punto azul con la posición del usuario.
                                userLoc?.let {
                                    map.addMarker(
                                        MarkerOptions()
                                            .position(LatLng(it.lat, it.lon))
                                            .icon(iconFactory.fromBitmap(userDotBitmap()))
                                    )
                                }

                                // Bloques existentes (el que se está moviendo va semitransparente)
                                existingBlocks.forEach { block ->
                                    val isMovingThis = movingBlock?.id == block.id
                                    val baseBmp = bitmapForBlock(block, isProposal = false)
                                    val bmp = if (isMovingThis) fadeBitmap(baseBmp) else baseBmp
                                    val marker = map.addMarker(
                                        MarkerOptions()
                                            .position(LatLng(block.lat, block.lon))
                                            .title(block.name)
                                            .icon(iconFactory.fromBitmap(bmp))
                                    )
                                    markerToBlock[marker] = block
                                }
                                // Marker fantasma ★ en la posición candidata.
                                ghostPosition?.let { (gLat, gLon) ->
                                    val bmp = pinBitmap(
                                        android.graphics.Color.parseColor("#F59E0B"),
                                        "★", sizeDp = 48
                                    )
                                    map.addMarker(
                                        MarkerOptions()
                                            .position(LatLng(gLat, gLon))
                                            .title("Nueva posición")
                                            .icon(iconFactory.fromBitmap(bmp))
                                    )
                                }

                                // POSITION_CORRECTION: dibuja vieja + nueva + línea, y auto-fit.
                                if (positionCorrectionNew != null) {
                                    val (pLat, pLon) = positionCorrectionNew
                                    val oldIcon = pinBitmap(
                                        android.graphics.Color.parseColor("#8A8478"), "✕", sizeDp = 44
                                    )
                                    map.addMarker(
                                        MarkerOptions()
                                            .position(LatLng(lat, lon))
                                            .title("POSICIÓN ACTUAL${positionCorrectionTargetName?.let { " · $it" } ?: ""}")
                                            .icon(iconFactory.fromBitmap(oldIcon))
                                    )
                                    val newIcon = pinBitmap(
                                        android.graphics.Color.parseColor("#F59E0B"), "★", sizeDp = 52
                                    )
                                    map.addMarker(
                                        MarkerOptions()
                                            .position(LatLng(pLat, pLon))
                                            .title("PROPUESTA · NUEVA POSICIÓN")
                                            .icon(iconFactory.fromBitmap(newIcon))
                                    )
                                    map.addPolyline(
                                        org.maplibre.android.annotations.PolylineOptions()
                                            .add(LatLng(lat, lon))
                                            .add(LatLng(pLat, pLon))
                                            .color(android.graphics.Color.parseColor("#C2410C"))
                                            .width(4f)
                                    )
                                    // Auto-fit a ambos puntos con padding.
                                    val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
                                        .include(LatLng(lat, lon))
                                        .include(LatLng(pLat, pLon))
                                        .build()
                                    map.animateCamera(
                                        org.maplibre.android.camera.CameraUpdateFactory
                                            .newLatLngBounds(bounds, 200)
                                    )
                                } else if (proposalAsBlock != null) {
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

                                // En modo mover: cada tap actualiza la posición candidata (ghost).
                                // No movemos aún — esperamos a que el admin pulse ACEPTAR.
                                map.addOnMapClickListener { point ->
                                    if (movingBlock != null) {
                                        ghostPosition = point.latitude to point.longitude
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
            }

            // Banner contextual: paso 1 "pulsa la nueva posición" / paso 2 con ACEPTAR
            movingBlock?.let { mb ->
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(Spacing.md)
                        .clip(MaterialTheme.shapes.small)
                        .background(Terra)
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                ) {
                    Text(
                        if (ghostPosition == null)
                            "📍 PULSA EN EL MAPA LA NUEVA POSICIÓN DE ${mb.name.uppercase()}"
                        else
                            "✓ POSICIÓN FIJADA PARA ${mb.name.uppercase()} · PULSA OTRA VEZ PARA RECORREGIR",
                        style = EyebrowTextStyle, color = Color.White
                    )
                    if (ghostPosition != null) {
                        androidx.compose.foundation.layout.Spacer(Modifier.height(Spacing.xs))
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Box(modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(Color.White.copy(alpha = 0.18f))
                                .clickable {
                                    ghostPosition = null
                                    movingBlock = null
                                }
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                            ) {
                                Text("CANCELAR", style = EyebrowTextStyle, color = Color.White)
                            }
                            Box(modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(Color.White)
                                .clickable {
                                    val (gLat, gLon) = ghostPosition!!
                                    val req = com.meteomontana.android.data.api.dto.CreateBlockRequest(
                                        type = mb.type, name = mb.name,
                                        lat = gLat, lon = gLon,
                                        photoPath = mb.photoPath, description = mb.description,
                                        lines = mb.lines.map { l ->
                                            com.meteomontana.android.data.api.dto.CreateBlockLineRequest(
                                                name = l.name, grade = l.grade,
                                                startType = l.startType, linePath = l.linePath
                                            )
                                        }
                                    )
                                    onUpdateBlock?.invoke(mb, req)
                                    movingBlock = null
                                    ghostPosition = null
                                }
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                            ) {
                                Text("✓ ACEPTAR Y MOVER", style = EyebrowTextStyle, color = Terra)
                            }
                        }
                    }
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

            // Chips Topo / Satélite arriba a la izquierda
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Spacing.md),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.xs)
            ) {
                listOf("topo" to "Topo", "sat" to "Satélite").forEach { (id, label) ->
                    val selected = mapStyle == id
                    Box(modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (selected) Color.White
                            else Color.Black.copy(alpha = 0.55f)
                        )
                        .clickable {
                            mapStyle = id
                            mapViewRef.value?.getMapAsync { map ->
                                map.setStyle(Style.Builder().fromJson(fullStyleJson(id)))
                            }
                        }
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    ) {
                        Text(
                            label, style = EyebrowTextStyle,
                            color = if (selected) Color.Black else Color.White
                        )
                    }
                }
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
        "PARKING" -> pinBitmap(android.graphics.Color.parseColor("#1D6DD6"), "P", sizeDp = 28)
        "ZONE"    -> pinBitmap(android.graphics.Color.parseColor("#1FA84E"), "Z", sizeDp = 32)
        else      -> pinBitmapBoulder(
            label = block.name.takeIf { it.isNotBlank() } ?: "?",
            fillColor = android.graphics.Color.parseColor("#C2410C"),
            sizeDp = 22
        )
    }
}

/**
 * Bitmap circular con relleno [colorInt], borde blanco, letra centrada.
 * Para PARKING/ZONE.
 */
/** Punto azul "mi ubicación": disco azul con borde blanco y halo suave. */
internal fun userDotBitmap(): Bitmap {
    val sizePx = 48
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = sizePx / 2f
    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(50, 30, 100, 220) }
    canvas.drawCircle(cx, cx, 22f, halo)
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    canvas.drawCircle(cx, cx, 12f, border)
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(30, 100, 220) }
    canvas.drawCircle(cx, cx, 9f, dot)
    return bmp
}

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

private val SAT_STYLE = """
{"version":8,"sources":{"sat":{"type":"raster","tiles":[
  "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
],"tileSize":256,"attribution":"Tiles © Esri"}},"layers":[{"id":"sat","type":"raster","source":"sat"}]}
""".trimIndent()

internal fun fullStyleJson(id: String): String = if (id == "sat") SAT_STYLE else OSM_TOPO_STYLE

/** Devuelve una copia del bitmap con alpha 0.35f. */
private fun fadeBitmap(src: android.graphics.Bitmap): android.graphics.Bitmap {
    val out = android.graphics.Bitmap.createBitmap(src.width, src.height, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(out)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { alpha = 90 }
    c.drawBitmap(src, 0f, 0f, paint)
    return out
}
