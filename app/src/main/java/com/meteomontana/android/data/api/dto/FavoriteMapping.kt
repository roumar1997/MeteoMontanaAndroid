package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.DayCell
import com.meteomontana.android.domain.model.FavoriteRow
import com.meteomontana.android.domain.model.FavoriteSchool
import com.meteomontana.android.domain.model.FavoritesGrid

fun FavoriteSchoolDto.toDomain() = FavoriteSchool(id, name, region, rockType, isFavorite)

fun FavoritesGridDto.toDomain() = FavoritesGrid(rows.map { it.toDomain() }, days)

fun FavoriteRowDto.toDomain() = FavoriteRow(schoolId, schoolName, cells.map { it.toDomain() })

fun DayCellDto.toDomain() = DayCell(date, avgScore, label)
