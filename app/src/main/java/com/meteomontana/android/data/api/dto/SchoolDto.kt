package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.School
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTO que mapea exactamente lo que devuelve el back.
 * Si el back cambia campos, este es el único sitio que tocamos.
 */
@JsonClass(generateAdapter = true)
data class SchoolDto(
    @Json(name = "id")        val id: String,
    @Json(name = "name")      val name: String,
    @Json(name = "location")  val location: String?,
    @Json(name = "region")    val region: String?,
    @Json(name = "style")     val style: String?,
    @Json(name = "rockType")  val rockType: String?,
    @Json(name = "lat")       val lat: Double,
    @Json(name = "lon")       val lon: Double,
    @Json(name = "source")    val source: String?
) {
    fun toDomain(): School = School(
        id = id, name = name, location = location, region = region,
        style = style, rockType = rockType, lat = lat, lon = lon, source = source
    )
}
