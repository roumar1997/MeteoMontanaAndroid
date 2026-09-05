package com.meteomontana.android.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SchoolDto(
    @SerialName("id")       val id: String,
    @SerialName("name")     val name: String,
    @SerialName("location") val location: String? = null,
    @SerialName("region")   val region: String? = null,
    @SerialName("style")    val style: String? = null,
    @SerialName("rockType") val rockType: String? = null,
    @SerialName("lat")      val lat: Double,
    @SerialName("lon")      val lon: Double,
    @SerialName("source")   val source: String? = null,
    @SerialName("country")  val country: String? = null,
    /** ¿Alguna vez confirmó un usuario procesionaria aquí? Para siempre. */
    @SerialName("hasKnownProcessionary") val hasKnownProcessionary: Boolean = false,
    /** ¿Toca avisar ahora? (zona conocida + temporada dic-may). */
    @SerialName("processionaryAlertActive") val processionaryAlertActive: Boolean = false
)
