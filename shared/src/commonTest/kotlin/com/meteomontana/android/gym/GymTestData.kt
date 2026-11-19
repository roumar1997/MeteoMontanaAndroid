package com.meteomontana.android.gym

import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymDiscipline
import com.meteomontana.android.domain.model.gym.GymKind
import com.meteomontana.android.domain.model.gym.GymVerification
import com.meteomontana.android.domain.model.gym.OpeningSlot

/** Fabrica de fichas para los tests: solo se rellena lo que cada caso necesita. */
internal fun gym(
    id: Long = 1,
    name: String = "Gym $id",
    kind: GymKind = GymKind.COMMERCIAL,
    discipline: GymDiscipline = GymDiscipline.BOTH,
    chain: String? = null,
    city: String = "Madrid",
    province: String = "Madrid",
    lat: Double = 40.4,
    lon: Double = -3.7,
    hours: List<OpeningSlot>? = null,
    priceSingle: Double? = null,
    hidden: Boolean = false,
    source: String? = null
) = Gym(
    id = id, slug = "gym-$id", name = name, chain = chain, kind = kind, discipline = discipline,
    region = "Madrid", province = province, city = city, address = null, lat = lat, lon = lon,
    website = null, instagram = null, openingHours = hours, priceSingleEur = priceSingle,
    pricePassEur = null, pricePassLabel = null, priceUpdatedAt = null, hasAutobelay = null,
    hasGearRental = null, kidsFriendly = null, beginnerFriendly = null,
    outdoor = kind == GymKind.FREE, verification = GymVerification.UNCONFIRMED, source = source,
    hidden = hidden, updatedAt = null
)
