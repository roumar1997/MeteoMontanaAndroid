package com.meteomontana.android.domain.usecase.favorites

import com.meteomontana.android.domain.model.FavoritesGrid
import com.meteomontana.android.domain.repository.FavoritesRepository

/**
 * Rejilla de favoritas: filas por escuela, columnas por día, con score medio y
 * etiqueta por celda. Alimenta el grid del tab Tiempo (espejo de Android).
 */
class GetFavoritesGridUseCase(private val repository: FavoritesRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): FavoritesGrid = repository.getFavoritesGrid()
}
