package com.meteomontana.android.domain.model

/**
 * Action text of an inbox row when the server sends [Notification.postPhrase]
 * (already in the recipient's language: «tu bloque «X» (6a) en Albarracín»).
 * Returns null when there is no phrase or the type doesn't use it, so the apps
 * fall back to their previous text (older servers).
 */
object NotificationPhrases {
    fun action(type: String, many: Boolean, postPhrase: String?, lang: String): String? {
        val p = postPhrase?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val en = lang.lowercase().startsWith("en")
        return when (type) {
            "FEED_LIKE" -> if (en) "liked $p" else if (many) "dieron me gusta a $p" else "dio me gusta a $p"
            "FEED_COMMENT" -> if (en) "commented on $p" else "comentó en $p"
            "FEED_MENTION" -> if (en) "mentioned you in $p" else "te mencionó en $p"
            else -> null
        }
    }
}
