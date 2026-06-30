package com.meteomontana.android.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat

private const val PREFS_NAME = "language_prefs"
private const val KEY_LANGUAGE_SET = "language_chosen"
private const val KEY_LANGUAGE_CODE = "language_code"

/** Guarda si el usuario ya eligió idioma y cuál es, para aplicarlo en attachBaseContext. */
object LanguagePrefs {
    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasChosenLanguage(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LANGUAGE_SET, false)

    fun markChosen(context: Context) {
        prefs(context).edit().putBoolean(KEY_LANGUAGE_SET, true).apply()
    }

    fun saveLanguageCode(context: Context, code: String) {
        prefs(context).edit()
            .putBoolean(KEY_LANGUAGE_SET, true)
            .putString(KEY_LANGUAGE_CODE, code)
            .apply()
    }

    fun getSavedLanguageCode(context: Context): String? =
        prefs(context).getString(KEY_LANGUAGE_CODE, null)
}

/** Aplica el idioma elegido a toda la app. */
fun applyAppLanguage(context: Context, languageCode: String) {
    // 1. Persiste el código para que attachBaseContext lo aplique en la recreación.
    LanguagePrefs.saveLanguageCode(context, languageCode)
    // 2. setApplicationLocales para API 33+ (per-app language en ajustes del sistema).
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageCode))
    // 3. Fuerza recreación — findActivity() traversa los ContextWrapper de Compose.
    context.findActivity()?.recreate()
}

/** Traversa la cadena de ContextWrapper hasta encontrar la Activity real. */
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

fun currentAppLanguage(): String {
    val locales = AppCompatDelegate.getApplicationLocales()
    return if (!locales.isEmpty) locales[0]?.language ?: "es" else "es"
}

/** Selector de idioma: dialog reutilizable para primera apertura y para Perfil. */
@Composable
fun LanguagePickerDialog(
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Idioma / Language") },
        text = {
            Column {
                LanguageOption("Español", "es", onSelected)
                LanguageOption("English", "en", onSelected)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResourceCancel()) }
        }
    )
}

@Composable
private fun stringResourceCancel(): String =
    androidx.compose.ui.res.stringResource(com.meteomontana.android.R.string.common_cancel)

@Composable
private fun LanguageOption(label: String, code: String, onSelected: (String) -> Unit) {
    val selected = currentAppLanguage() == code
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .clickable { onSelected(code) }
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        if (selected) {
            Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
