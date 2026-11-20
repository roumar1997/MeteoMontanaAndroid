package com.meteomontana.android.domain.model

data class Meetup(
    val id: String,
    /** Vacio en una quedada de rocodromo (mira [gymId]). */
    val schoolId: String,
    val schoolName: String?,
    val schoolLat: Double?,
    val schoolLon: Double?,
    val name: String,
    val description: String?,   // detalles del organizador
    val discipline: String?,    // BOULDER | ROUTE | BOTH | null
    val privacy: String,        // OPEN | FOLLOWERS | WOMEN
    val memberLimit: Int?,
    val memberCount: Int,
    val photoUrl: String?,
    val creatorUid: String,
    val creatorUsername: String?,
    val creatorPhotoUrl: String?,
    val conversationId: String,
    val days: List<String>,     // ISO dates "2026-07-05"
    val lastDay: String,
    val expiresAt: Long,        // epoch millis
    val createdAt: Long,
    val members: List<MeetupMember>,
    val joined: Boolean,
    val gymId: Long? = null,    // rocodromo; null = escuela
    val placeName: String? = null,
    /** Grado orientativo (rango, minusculas); null si no se indico. */
    val gradeMin: String? = null,
    val gradeMax: String? = null,
    /** Cuantos compañeros se buscan (1 = cordada); null si no se indico. */
    val partnersWanted: Int? = null,
    /** Material minimo que pide la quedada y cuanto cubren ya los apuntados. */
    val requiredGear: List<MeetupGear> = emptyList(),
    val gearCoverage: List<MeetupGearCoverage> = emptyList()
) {
    val isGymMeetup: Boolean get() = gymId != null

    val isFull: Boolean get() = memberLimit != null && memberCount >= memberLimit

    /** Hay material pedido que aun no cubre nadie del todo. */
    val gearMissing: Boolean get() = gearCoverage.any { !it.ok }

    val isRopeTeam: Boolean get() = discipline == "ROUTE" && partnersWanted == 1

    // Alias para Swift: `description` choca con NSObject.description vía SKIE.
    val descriptionText: String? get() = description
}

data class MeetupMember(
    val uid: String,
    val username: String?,
    val displayName: String?,
    val photoUrl: String?,
    val gearJson: String? = null
)

data class CreateMeetupRequest(
    val schoolId: String,
    val name: String,
    val description: String? = null,
    val discipline: String?,    // BOULDER | ROUTE | BOTH | null
    val privacy: String,
    val memberLimit: Int?,
    val photoUrl: String?,
    val days: List<String>,     // ISO dates
    val gymId: Long? = null,    // rocodromo (entonces schoolId va en blanco)
    /** Grado orientativo (rango frances, opcional). */
    val gradeMin: String? = null,
    val gradeMax: String? = null,
    /** Cuantos se buscan (1 a 20; 1 = cordada). No sustituye a [memberLimit]. */
    val partnersWanted: Int? = null,
    /** Material minimo (max. 4): cuerda, gri-gri, cintas con cantidad, crashpads. */
    val requiredGear: List<MeetupGear> = emptyList()
)
