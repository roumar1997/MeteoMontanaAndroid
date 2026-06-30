package com.meteomontana.android.ui.components

import android.content.Context
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

/** Guarda si el usuario ya eligió idioma (para no preguntar mas de una vez). */
object LanguagePrefs {
    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasChosenLanguage(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LANGUAGE_SET, false)

    fun markChosen(context: Context) {
        prefs(context).edit().putBoolean(KEY_LANGUAGE_SET, true).apply()
    }
}

/** Aplica el idioma elegido a toda la app (persiste automáticamente vía AppCompatDelegate). */
fun applyAppLanguage(context: Context, languageCode: String) {
    LanguagePrefs.markChosen(context)
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageCode))
    // En algunos OEM (MIUI, etc.) setApplicationLocales no dispara recreación automáticamente.
    // La forzamos explícitamente para que todas las cadenas se refresquen.
    (context as? android.app.Activity)?.recreate()
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
