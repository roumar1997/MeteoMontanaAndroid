package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.UserBadge
import com.meteomontana.android.domain.model.UserRewards

/** Recompensas: las de cualquiera (perfil) y las mías (elegir título y viero/bloquero). */
interface RewardsRepository {
    suspend fun of(uid: String): UserRewards
    suspend fun mine(): UserRewards
    /** climberType "" = quitarlo, null = no tocarlo; titleLevel -1 = quitar, null = no tocarlo. */
    /** featuredAchievement "" = quitarlo, null = no tocarlo. titleHidden true/false = ocultar/enseñar el título, null = no tocarlo. */
    suspend fun update(climberType: String?, titleLevel: Int?, featuredAchievement: String? = null,
                       titleHidden: Boolean? = null): UserRewards
    /** Verificado y título de varias personas a la vez (para la aureola y el check en cualquier lista). */
    suspend fun badges(uids: List<String>): List<UserBadge>
    /** Solo admin: pone (o quita, con título vacío) el título especial de alguien. */
    suspend fun setSpecialTitle(uid: String, title: String?, color: String?): UserRewards
}
