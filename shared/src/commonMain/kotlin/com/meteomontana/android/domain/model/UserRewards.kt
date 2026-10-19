package com.meteomontana.android.domain.model

/**
 * Recompensas de una persona: nivel por aportaciones aprobadas (0, 1, 5 … 50), siguiente nivel, el título que enseña
 * (el de un nivel desbloqueado), si escala sobre todo vías o bloque e insignias ("COLLABORATOR", "TRUSTED").
 */
data class UserRewards(
    val uid: String,
    val approved: Int,
    val level: Int,
    val nextLevel: Int?,
    val titleLevel: Int,
    val climberType: String?,
    val badges: List<String>,
    /** Id del logro que enseña bajo su nombre (uno de los desbloqueados), o null. */
    val featuredAchievement: String? = null,
    /** La persona no quiere enseñar ningún título. */
    val titleHidden: Boolean = false,
    /** Título especial que le ha puesto un admin (se enseña en vez del de su nivel) y su color «#RRGGBB». */
    val specialTitle: String? = null,
    val specialColor: String? = null,
) {
    /** Niveles cuyo título ya puede elegir (de menor a mayor). */
    val unlockedLevels: List<Int> get() = LEVELS.filter { it <= level }

    companion object {
        val LEVELS = listOf(0, 1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50)
        val CLIMBER_TYPES = listOf("ROUTES", "BOULDER", "BOTH")
    }
}
