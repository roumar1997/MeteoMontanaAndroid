package com.meteomontana.android.domain.model

/** Un vídeo de beta: su enlace (Instagram, YouTube...) y la altura de quien lo subió ("TALL" / "SHORT" / null). */
data class BetaVideo(val url: String, val height: String?)

/**
 * Una pastilla de "lo que sabe la comunidad" de una vía. La app pone el texto en su idioma:
 * - [Video]: hay vídeo de beta de gente de esa altura (null = sin altura). Tocarla abre el vídeo, o la lista si hay varios.
 * - [Done]: cuántas personas la han hecho en total.
 * - [DoneByHeight]: de ellas, cuántas miden más / menos de 1,70.
 */
sealed class CommunityBadge {
    data class Video(val height: String?, val videos: List<BetaVideo>) : CommunityBadge()
    data class Done(val people: Int) : CommunityBadge()
    data class DoneByHeight(val height: String, val people: Int) : CommunityBadge()

    companion object {
        /** "+1,70" / "−1,70" / null: así se escribe la altura en las pastillas (igual en ES y EN). */
        fun heightText(height: String?): String? = when (height) {
            "TALL" -> "+1,70"
            "SHORT" -> "−1,70"
            else -> null
        }
    }
}
