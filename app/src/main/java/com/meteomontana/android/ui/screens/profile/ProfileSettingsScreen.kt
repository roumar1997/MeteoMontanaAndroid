package com.meteomontana.android.ui.screens.profile

import com.meteomontana.android.ui.components.SkeletonProfile
import com.meteomontana.android.ui.components.VerifiedCheck
import com.meteomontana.android.ui.components.userRing
import androidx.compose.material.icons.outlined.Language
import com.meteomontana.android.util.AppText
import android.content.Context
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import com.meteomontana.android.ui.guide.guideTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.JournalStats
import com.meteomontana.android.domain.model.PrivateProfile

// Ajustes del perfil: idioma, secciones y filas del menú.

/** Fila "Idioma" de Ajustes: abre un diálogo Automático / Español / English. */
@Composable
internal fun LanguageRow() {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val current = com.meteomontana.android.util.AppLanguage.get(ctx)
    val options = listOf(
        com.meteomontana.android.util.AppLanguage.SYSTEM to stringResource(R.string.language_system),
        com.meteomontana.android.util.AppLanguage.ES to stringResource(R.string.language_es),
        com.meteomontana.android.util.AppLanguage.EN to stringResource(R.string.language_en),
    )
    MenuRow(Icons.Outlined.Language, stringResource(R.string.settings_language)) { open = true }
    if (open) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.language_dialog_title)) },
            text = {
                Column {
                    options.forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                open = false
                                if (value != current) {
                                    com.meteomontana.android.util.AppLanguage.set(ctx, value)
                                    // OJO: ctx.applicationContext es el MISMO objeto desde que arrancó el
                                    // proceso, con la configuración (idioma) que tenía entonces — reenvolverlo
                                    // aquí es lo que de verdad hace que AppText/CatalogLabels lean el idioma
                                    // nuevo sin esperar a que se mate y reabra la app (Álvaro, 2026-09-28:
                                    // tras cambiar de idioma, "Granito" y "Malo" seguían en español).
                                    com.meteomontana.android.util.AppText.init(
                                        com.meteomontana.android.util.AppLanguage.wrap(ctx.applicationContext)
                                    )
                                    com.meteomontana.android.data.api.ApiLanguage.code = com.meteomontana.android.util.AppLanguage.effective(ctx)
                                    (ctx as? android.app.Activity)?.recreate()
                                }
                            }.padding(vertical = 10.dp)
                        ) {
                            androidx.compose.material3.RadioButton(selected = value == current, onClick = null)
                            Spacer(Modifier.padding(start = 12.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { open = false }) { Text(stringResource(R.string.w_close)) }
            }
        )
    }
}

/**
 * Hoja de ajustes (⚙️): agrupa lo que antes ocupaba el perfil — cuenta, mi
 * actividad, preferencias y sesión. Bottom sheet (paridad iOS).
 */
@Composable
internal fun ProfileSettingsScreen(
    onBack: () -> Unit,
    isPrivate: Boolean,
    onEdit: () -> Unit,
    onSavedSchools: () -> Unit,
    onWeekendAlert: () -> Unit,
    onSubmissions: () -> Unit,
    onOpenFollowRequests: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Cabecera: flecha atrás + "Ajustes".
        Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.profile_screen_atras),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(stringResource(R.string.profile_settings),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Center))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
        Column(Modifier.fillMaxWidth()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(bottom = 24.dp)) {
            SettingsSectionLabel(stringResource(R.string.profile_section_account))
            MenuRow(Icons.Outlined.Edit, stringResource(R.string.profile_edit), onEdit)

            SettingsSectionLabel(stringResource(R.string.profile_section_activity))
            MenuRow(Icons.Outlined.Download, stringResource(R.string.profile_saved_schools), onSavedSchools)
            MenuRow(Icons.Outlined.Place, stringResource(R.string.profile_my_contributions), onSubmissions)
            if (isPrivate) {
                MenuRow(Icons.Outlined.PersonAdd, stringResource(R.string.profile_follow_requests), onOpenFollowRequests)
            }

            SettingsSectionLabel(stringResource(R.string.profile_section_prefs))
            MenuRow(Icons.Outlined.Notifications, stringResource(R.string.profile_weather_alert), onWeekendAlert)
            LanguageRow()
            val ctx = LocalContext.current
            MenuRow(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.profile_show_hints)) {
                com.meteomontana.android.ui.components.resetAllHints(ctx)
                android.widget.Toast.makeText(ctx, ctx.getString(R.string.profile_screen_v3_pistas_reactivadas), android.widget.Toast.LENGTH_SHORT).show()
            }
            // Interruptor general de las guías por pantalla ("? AYUDA" parpadea / guía de Escuelas sale sola).
            com.meteomontana.android.ui.guide.GuidesToggleRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // Cierre de la pantalla EXACTAMENTE como iOS (AccountView): botón
            // "Cerrar sesión" en su recuadro, debajo "Eliminar cuenta" como
            // texto discreto centrado, y la versión al final del todo. Aquí
            // estaba en otro orden y "Eliminar cuenta" gritaba en rojo con
            // papelera, cuando es la acción que MENOS hay que invitar a pulsar.
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp))
            var showDelete by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .clickable(onClick = onSignOut)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.profile_logout),
                    style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                stringResource(R.string.profile_delete_account),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .clickable { showDelete = true }
                    .padding(vertical = 12.dp)
            )
            // Que version es esta. Sin esto, "no me ha llegado el arreglo" y
            // "no lo has arreglado" son indistinguibles.
            Text(
                stringResource(R.string.profile_screen_v2_cumbre) + com.meteomontana.android.BuildConfig.VERSION_NAME +
                    " (vc" + com.meteomontana.android.BuildConfig.VERSION_CODE + ") · " +
                    com.meteomontana.android.BuildConfig.BUILD_TIME +
                    if (com.meteomontana.android.BuildConfig.DEBUG) " · staging" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (showDelete) {
                AlertDialog(
                    onDismissRequest = { showDelete = false },
                    title = { Text(stringResource(R.string.profile_screen_eliminar_tu_cuenta)) },
                    text = { Text(stringResource(R.string.profile_screen_se_borraran_tu_perfil)) },
                    confirmButton = {
                        TextButton(onClick = { showDelete = false; onDeleteAccount() }) {
                            Text(stringResource(R.string.profile_screen_v2_eliminar), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.common_cancel)) }
                    }
                )
            }
        }
    }
}

@Composable
internal fun SettingsSectionLabel(text: String) {
    Text(text.uppercase(),
        style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 2.dp))
}

@Composable
internal fun MenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}


