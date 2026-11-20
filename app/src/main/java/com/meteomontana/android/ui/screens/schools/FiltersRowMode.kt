package com.meteomontana.android.ui.screens.schools

/** Qué enseña la fila «FILTROS (n) · contador · BORRAR · FAVORITOS · día» según lo que cabe. */
internal data class FiltersRowMode(val showCount: Boolean, val clearAsIcon: Boolean, val favsAsIcon: Boolean)

private const val SIDE_PADDING_DP = 24f
private const val GAP_DP = 8f
private const val FILTERS_CHIP_DP = 124f
private const val CHEVRON_DP = 18f
private const val COUNT_MIN_DP = 80f
private const val CLEAR_FULL_DP = 84f
private const val CLEAR_ICON_DP = 36f
private const val FAVS_FULL_DP = 104f
private const val FAVS_ICON_DP = 38f
private const val DAYS_DP = 88f

/**
 * Reparto de la fila de filtros (lógica pura, se prueba sin pantalla). Los textos crecen con [fontScale]; los iconos
 * no. Si no cabe todo, primero se oculta el contador, luego BORRAR pasa a «✕» y por último FAVORITOS a su estrella.
 */
internal fun filtersRowMode(widthDp: Float, fontScale: Float, active: Boolean, hasFavs: Boolean, hasDays: Boolean): FiltersRowMode {
    val fs = fontScale.coerceAtLeast(1f)
    fun need(count: Boolean, clearIcon: Boolean, favsIcon: Boolean): Float {
        var w = SIDE_PADDING_DP + FILTERS_CHIP_DP * fs + GAP_DP + CHEVRON_DP
        if (count) w += COUNT_MIN_DP * fs + GAP_DP
        if (active) w += GAP_DP + if (clearIcon) CLEAR_ICON_DP else CLEAR_FULL_DP * fs
        if (hasFavs) w += GAP_DP + if (favsIcon) FAVS_ICON_DP else FAVS_FULL_DP * fs
        if (hasDays) w += GAP_DP + DAYS_DP * fs
        return w
    }
    if (widthDp >= need(true, false, false)) return FiltersRowMode(showCount = true, clearAsIcon = false, favsAsIcon = false)
    if (widthDp >= need(false, false, false)) return FiltersRowMode(showCount = false, clearAsIcon = false, favsAsIcon = false)
    if (widthDp >= need(false, true, false)) return FiltersRowMode(showCount = false, clearAsIcon = true, favsAsIcon = false)
    return FiltersRowMode(showCount = false, clearAsIcon = true, favsAsIcon = true)
}
