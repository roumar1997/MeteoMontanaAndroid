package com.meteomontana.android.domain.usecase.contributions

/**
 * Diff PURO de una vía corregida (piedra POINT): qué CAMPOS cambian respecto a
 * la versión existente. Vive en `shared` (no en la UI) para que Android e iOS
 * rendericen exactamente lo mismo y para poder testearlo sin Compose/SwiftUI —
 * fue justo aquí donde se coló el bug de "faltaba la variante". Hexagonal: la
 * lógica de negocio no depende del framework de UI; la UI solo pinta el
 * resultado.
 */

/** Campo comparable de una vía. La UI traduce a etiqueta ("Nombre", "Grado"…). */
enum class LineField { NAME, GRADE, VARIANT, START_TYPE, DESCRIPTION }

/** Un campo que cambia: viejo → nuevo (ya normalizados; null = vacío/ausente). */
data class FieldChange(val field: LineField, val old: String?, val new: String?)

/** Campos de una vía para comparar (sin puntos: el trazado se compara aparte). */
data class LineFields(
    val name: String? = null,
    val grade: String? = null,
    val variant: String? = null,
    val startType: String? = null,
    val description: String? = null
)

/** Resultado del diff de UNA vía propuesta contra su versión existente. */
data class LineDiff(
    val isNew: Boolean,
    val displayName: String?,
    val changes: List<FieldChange>,
    val drawingChanged: Boolean
) {
    /** ¿Cambia algo (nueva, algún campo, o el trazado)? */
    val hasAnyChange: Boolean get() = isNew || changes.isNotEmpty() || drawingChanged
}

private fun norm(s: String?): String? = s?.takeIf { it.isNotBlank() }

/**
 * Compara la vía existente [orig] (null = vía NUEVA) con la propuesta [new] y
 * devuelve SOLO los campos que cambian. [drawingChanged] lo calcula el caller
 * (compara los puntos de la línea, que son propios de cada plataforma).
 */
fun computeLineDiff(orig: LineFields?, new: LineFields, drawingChanged: Boolean): LineDiff {
    if (orig == null) {
        return LineDiff(isNew = true, displayName = norm(new.name), changes = emptyList(),
            drawingChanged = drawingChanged)
    }
    val changes = buildList {
        if (norm(orig.name) != norm(new.name)) add(FieldChange(LineField.NAME, norm(orig.name), norm(new.name)))
        if (norm(orig.grade) != norm(new.grade)) add(FieldChange(LineField.GRADE, norm(orig.grade), norm(new.grade)))
        if (norm(orig.variant) != norm(new.variant)) add(FieldChange(LineField.VARIANT, norm(orig.variant), norm(new.variant)))
        if (norm(orig.startType) != norm(new.startType)) add(FieldChange(LineField.START_TYPE, norm(orig.startType), norm(new.startType)))
        if (norm(orig.description) != norm(new.description)) add(FieldChange(LineField.DESCRIPTION, norm(orig.description), norm(new.description)))
    }
    return LineDiff(isNew = false, displayName = norm(new.name) ?: norm(orig.name),
        changes = changes, drawingChanged = drawingChanged)
}
