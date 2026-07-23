package com.meteomontana.android.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import kotlin.math.cos
import kotlin.math.sin

/**
 * FÁBRICA DE MARCADORES del mapa — todos los bitmaps de pines de Cumbre en un
 * solo sitio (espejo de `MarkerRenderer.swift` en iOS; si cambias un pin aquí,
 * revisa allí).
 *
 * Única responsabilidad: ASPECTO VISUAL de los marcadores. Aquí no hay lógica
 * de mapa, ni de datos, ni de flujo — solo Canvas → Bitmap y la caché de
 * iconos de MapLibre. Los usan `SchoolMap.kt` (placeMarkers) y
 * `FullScreenMapDialog.kt` (mapa del admin).
 */

/** Color terra de las piedras: el muro usa el MISMO color (solo que como línea). */
internal const val PIEDRA_COLOR = "#C2410C"

/**
 * Caché de iconos de marcador. Sin ella, cada re-sincronización registra
 * sprites NUEVOS en el atlas de texturas de MapLibre (fromBitmap = id único);
 * tras muchas re-sync el atlas se corrompe y los markers se pintan como
 * bandas rayadas gigantes por el mapa (visto al hacer zoom en La Pedriza).
 * Reutilizar el mismo Icon por clave lo evita de raíz.
 */
private val iconCache = HashMap<String, Icon>()
internal fun cachedIcon(
    factory: IconFactory,
    key: String,
    make: () -> Bitmap
): Icon = iconCache.getOrPut(key) { factory.fromBitmap(make()) }

/** Versión atenuada (alpha ~35%) del bitmap si [faded]; tal cual si no. */
internal fun Bitmap.fadedIf(faded: Boolean): Bitmap {
    if (!faded) return this
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 90 }
    c.drawBitmap(this, 0f, 0f, paint)
    return out
}

/** Marker fantasma terra con ★ para la posición candidata. */
internal fun ghostBitmap(): Bitmap {
    val size = 64
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#C2410C")
    }
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; color = android.graphics.Color.WHITE
    }
    c.drawCircle(size / 2f, size / 2f, 24f, fill)
    c.drawCircle(size / 2f, size / 2f, 24f, border)
    val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE; textSize = 28f
        textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    c.drawText("★", size / 2f, size / 2f + 10f, txt)
    return bmp
}

/** Punto numerado de la polilínea del muro en construcción (preview del trazado). */
internal fun wallPointBitmap(number: Int): Bitmap {
    val size = 56
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor(PIEDRA_COLOR)
    }
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; color = android.graphics.Color.WHITE
    }
    c.drawCircle(size / 2f, size / 2f, 20f, fill)
    c.drawCircle(size / 2f, size / 2f, 20f, border)
    val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE; textSize = 24f
        textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    c.drawText(number.toString(), size / 2f, size / 2f + 9f, txt)
    return bmp
}

/** Símbolo de escuela: triángulo terra estilo "montaña" para marcar el centro. */
internal fun schoolBitmap(label: String): Bitmap {
    val size = 72
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1C1C1A")
    }
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = android.graphics.Color.WHITE
    }
    val path = android.graphics.Path().apply {
        // Triángulo apuntando arriba (montaña)
        moveTo(size / 2f, 8f)
        lineTo(size - 8f, size - 10f)
        lineTo(8f, size - 10f)
        close()
    }
    c.drawPath(path, fill)
    c.drawPath(path, border)
    // Punto blanco dentro para que destaque
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    c.drawCircle(size / 2f, size / 2f + 4f, 5f, dot)
    return bmp
}

/** Cuadrado azul con "P" blanca — símbolo internacional de parking. */
internal fun parkingBitmap(): Bitmap {
    val size = 64
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)

    // Fondo azul redondeado
    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#1a56db") }
    c.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 10f, 10f, bg)

    // Borde blanco
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = android.graphics.Color.WHITE
        strokeWidth = 3f
    }
    c.drawRoundRect(RectF(1.5f, 1.5f, size - 1.5f, size - 1.5f), 9f, 9f, stroke)

    // "P" blanca
    val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 34f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    c.drawText("P", size / 2f, size / 2f + 12f, txt)
    return bmp
}

/**
 * Pin de piedra (BLOCK). Polígono irregular terra con el nombre dentro.
 * Usa el mismo helper compartido que el mapa del admin para coherencia visual.
 */
internal fun blockBitmap(label: String): Bitmap = pinBitmapBoulder(
    label = label.takeIf { it.isNotBlank() } ?: "?",
    fillColor = android.graphics.Color.parseColor("#C2410C"),
    sizeDp = 22   // ↓ Más pequeñas: en zonas con muchas piedras juntas, no se solapan tanto.
)

/** Pin verde para zonas (tipo ZONE). */
internal fun zoneBitmap(): Bitmap {
    val size = 68
    // Altura doble: el dibujo va en la mitad SUPERIOR y la inferior queda
    // transparente → como el ancla del Marker es el centro del bitmap, el
    // globo se ve DESPLAZADO hacia arriba (pin real), sin tapar la piedra.
    val bmp = Bitmap.createBitmap(size, size * 2, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#3F6B4A") }
    val cx = size / 2f; val cy = size / 2f - 5f; val r = 27f
    c.drawCircle(cx, cy, r, fill)
    val path = android.graphics.Path().apply {
        moveTo(cx - 10f, cy + r - 5f)
        lineTo(cx, cy + r + 13f)
        lineTo(cx + 10f, cy + r - 5f)
        close()
    }
    c.drawPath(path, fill)
    val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 26f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    c.drawText("Z", cx, cy + 9f, txt)
    return bmp
}

/**
 * Punto azul "mi ubicación": disco azul con borde blanco y halo suave. Con
 * [headingDeg] (brújula) añade un cono de dirección hacia donde apunta el
 * móvil — para orientarse buscando la piedra.
 */
internal fun userDotBitmap(headingDeg: Float? = null): Bitmap {
    val sizePx = 72
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = sizePx / 2f
    if (headingDeg != null) {
        // Cono translúcido apuntando al rumbo (norte = arriba).
        canvas.save()
        canvas.rotate(headingDeg, cx, cx)
        val cone = android.graphics.Path().apply {
            moveTo(cx, cx)
            lineTo(cx - 13f, cx - 32f)
            lineTo(cx + 13f, cx - 32f)
            close()
        }
        canvas.drawPath(cone, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(120, 30, 100, 220) })
        canvas.restore()
    }
    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(50, 30, 100, 220) }
    canvas.drawCircle(cx, cx, 22f, halo)
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    canvas.drawCircle(cx, cx, 12f, border)
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(30, 100, 220) }
    canvas.drawCircle(cx, cx, 9f, dot)
    return bmp
}

/** Bitmap circular con relleno [colorInt], borde blanco, letra centrada. Para PARKING/ZONE. */
internal fun pinBitmap(colorInt: Int, letter: String, sizeDp: Int = 40): Bitmap {
    val size = (sizeDp * 3).coerceAtLeast(64)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = size / 2f
    val cy = size / 2f
    val r = size / 2f - 4f

    canvas.drawCircle(cx, cy, r, Paint().apply {
        color = android.graphics.Color.WHITE; isAntiAlias = true
    })
    canvas.drawCircle(cx, cy, r - 4f, Paint().apply {
        color = colorInt; isAntiAlias = true
    })
    canvas.drawText(letter, cx, cy + size * 0.13f, Paint().apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
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
    val canvas = Canvas(bmp)
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
        Paint().apply {
            color = android.graphics.Color.argb(60, 0, 0, 0); isAntiAlias = true
        }
    )

    // Borde blanco (path expandido — dibujamos blanco grueso bajo el relleno)
    canvas.drawPath(path, Paint().apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 12f; strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    })

    // Relleno
    canvas.drawPath(path, Paint().apply {
        color = fillColor; isAntiAlias = true
        style = Paint.Style.FILL
    })

    // Sombreado interno arriba-izquierda (un poco más claro)
    canvas.drawPath(path, Paint().apply {
        color = android.graphics.Color.argb(40, 255, 255, 255); isAntiAlias = true
        style = Paint.Style.FILL
    })

    // Texto
    val textSize = if (label.length <= 2) size * 0.42f else size * 0.32f
    canvas.drawText(label, cx, cy + textSize * 0.35f, Paint().apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        this.textSize = textSize; isFakeBoldText = true; isAntiAlias = true
        setShadowLayer(4f, 0f, 1f, android.graphics.Color.argb(180, 0, 0, 0))
    })

    return bmp
}
