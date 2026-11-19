package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymDiscipline
import com.meteomontana.android.domain.model.gym.GymKind
import com.meteomontana.android.domain.model.gym.GymNear
import com.meteomontana.android.domain.model.gym.GymVerification
import com.meteomontana.android.domain.model.gym.OpeningSlot
import kotlinx.serialization.Serializable

// Los enums viajan como String y se parsean en el mapper: un valor que el servidor invente mas adelante
// NO debe tirar la decodificacion de todo el catalogo (lo desconocido cae en un valor por defecto).

@Serializable
data class OpeningSlotDto(val days: String = "", val open: String = "", val close: String = "")

@Serializable
data class GymDto(
    val id: Long,
    val slug: String = "",
    val name: String,
    val chain: String? = null,
    val kind: String? = null,
    val discipline: String? = null,
    val region: String = "",
    val province: String = "",
    val city: String = "",
    val address: String? = null,
    val lat: Double,
    val lon: Double,
    val website: String? = null,
    val instagram: String? = null,
    val openingHours: List<OpeningSlotDto>? = null,
    val priceSingleEur: Double? = null,
    val pricePassEur: Double? = null,
    val pricePassLabel: String? = null,
    val priceUpdatedAt: String? = null,
    val hasAutobelay: Boolean? = null,
    val hasGearRental: Boolean? = null,
    val kidsFriendly: Boolean? = null,
    val beginnerFriendly: Boolean? = null,
    val outdoor: Boolean = false,
    val verification: String? = null,
    val source: String? = null,
    val hidden: Boolean = false,
    val updatedAt: String? = null,
    val closed: Boolean = false
)

@Serializable
data class GymNearDto(
    val id: Long,
    val slug: String = "",
    val name: String,
    val chain: String? = null,
    val kind: String? = null,
    val discipline: String? = null,
    val city: String = "",
    val province: String = "",
    val lat: Double,
    val lon: Double,
    val outdoor: Boolean = false,
    val verification: String? = null,
    val distanceKm: Double = 0.0
)

fun GymDto.toDomain() = Gym(
    id = id, slug = slug, name = name, chain = chain,
    kind = GymKind.parse(kind), discipline = GymDiscipline.parse(discipline),
    region = region, province = province, city = city, address = address,
    lat = lat, lon = lon, website = website, instagram = instagram,
    openingHours = openingHours?.map { OpeningSlot(it.days, it.open, it.close) },
    priceSingleEur = priceSingleEur, pricePassEur = pricePassEur, pricePassLabel = pricePassLabel,
    priceUpdatedAt = priceUpdatedAt,
    hasAutobelay = hasAutobelay, hasGearRental = hasGearRental,
    kidsFriendly = kidsFriendly, beginnerFriendly = beginnerFriendly,
    outdoor = outdoor, verification = GymVerification.parse(verification),
    source = source, hidden = hidden, updatedAt = updatedAt, closed = closed
)

/** Para guardar la copia local; el reverso exacto de [toDomain]. */
fun Gym.toDto() = GymDto(
    id = id, slug = slug, name = name, chain = chain, kind = kind.name, discipline = discipline.name,
    region = region, province = province, city = city, address = address, lat = lat, lon = lon,
    website = website, instagram = instagram,
    openingHours = openingHours?.map { OpeningSlotDto(it.days, it.open, it.close) },
    priceSingleEur = priceSingleEur, pricePassEur = pricePassEur, pricePassLabel = pricePassLabel,
    priceUpdatedAt = priceUpdatedAt, hasAutobelay = hasAutobelay, hasGearRental = hasGearRental,
    kidsFriendly = kidsFriendly, beginnerFriendly = beginnerFriendly, outdoor = outdoor,
    verification = verification.name, source = source, hidden = hidden, updatedAt = updatedAt, closed = closed
)

fun GymNearDto.toDomain() = GymNear(
    id = id, slug = slug, name = name, chain = chain,
    kind = GymKind.parse(kind), discipline = GymDiscipline.parse(discipline),
    city = city, province = province, lat = lat, lon = lon, outdoor = outdoor,
    verification = GymVerification.parse(verification), distanceKm = distanceKm
)
