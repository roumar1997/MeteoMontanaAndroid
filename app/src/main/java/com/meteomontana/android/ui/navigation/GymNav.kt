package com.meteomontana.android.ui.navigation

/** Rutas de rocódromos dentro del overlay: la ficha y «crear quedada aquí» (la quedada propone «Quedada en <rocódromo>»). */
object GymNav {
    const val DETAIL = "gyms/{gymId}"
    fun detail(id: Long) = "gyms/$id"
    const val TRAINING = "gym-training"
    /** Ascensiones (por rocódromo, grado y mes) de una persona; sin uid, las mías. */
    const val ASCENTS = "gym-ascents?uid={uid}"
    fun ascents(uid: String? = null) = if (uid.isNullOrBlank()) "gym-ascents" else "gym-ascents?uid=$uid"
    /** Todos los boards de una persona (de todos los rocódromos juntos); sin uid, los míos. */
    const val BOARDS = "gym-boards?uid={uid}"
    fun boards(uid: String? = null) = if (uid.isNullOrBlank()) "gym-boards" else "gym-boards?uid=$uid"
    /** Lo que una persona hizo EN un rocódromo (sesiones y ascensiones); sin uid, lo mío. */
    const val VISIT = "gym-visit/{gymId}?uid={uid}"
    fun visit(gymId: Long, uid: String? = null) = "gym-visit/$gymId?uid=" + (uid?.let { android.net.Uri.encode(it) } ?: "")
    const val CHAT = "gyms/{gymId}/chat?name={name}"
    fun chat(id: Long, name: String) = "gyms/$id/chat?name=${android.net.Uri.encode(name)}"
    const val MEETUP = "gyms/{gymId}/meetup?name={name}"
    fun meetup(id: Long, name: String) = "gyms/$id/meetup?name=${android.net.Uri.encode(name)}"
}
