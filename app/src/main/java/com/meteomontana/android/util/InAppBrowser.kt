package com.meteomontana.android.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Abre un enlace web DENTRO de la app (Custom Tabs). Si no hay navegador compatible, cae al intent normal y, si
 * tampoco hay nada que lo abra, no hace nada (nunca revienta).
 */
fun openInApp(context: Context, url: String) {
    val uri = Uri.parse(url)
    runCatching { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri) }
        .recoverCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
