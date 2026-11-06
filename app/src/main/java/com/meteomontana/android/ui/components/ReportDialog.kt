package com.meteomontana.android.ui.components


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.KtorModerationApi
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R

/**
 * Moderación desde la UI: denunciar contenido (con snapshot en el backend) y
 * bloquear usuarios. `hiddenIds` oculta AL INSTANTE lo denunciado para quien
 * denuncia (Apple lo exige); el filtrado permanente lo hace el servidor.
 *
 * OJO: `hiddenIds` guarda claves tipadas "TIPO:id" (p.ej. "FEED_POST:51",
 * "NOTE:51"). Antes guardaba solo el id, pero los ids de post del feed son
 * enteros pequeños que CHOCABAN con ids de notas/comentarios → denunciar una
 * nota con id 51 ocultaba tu post 51. Cada filtro compone su clave con su tipo.
 */
@HiltViewModel
class ModerationViewModel @Inject constructor(
    private val api: KtorModerationApi
) : ViewModel() {

    private val _hiddenIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenIds: StateFlow<Set<String>> = _hiddenIds

    private val _blocked = MutableStateFlow<Set<String>>(emptySet())
    val blocked: StateFlow<Set<String>> = _blocked

    fun loadBlocked() {
        viewModelScope.launch {
            runCatching { api.getBlocked() }.onSuccess { _blocked.value = it }
        }
    }

    fun report(targetType: String, targetId: String, reason: String,
               details: String? = null, alsoBlockUid: String? = null) {
        _hiddenIds.value = _hiddenIds.value + "$targetType:$targetId"
        viewModelScope.launch {
            runCatching { api.report(targetType, targetId, reason, details) }
            alsoBlockUid?.let { uid ->
                runCatching { api.blockUser(uid) }
                _blocked.value = _blocked.value + uid
            }
        }
    }

    fun block(uid: String) {
        viewModelScope.launch {
            runCatching { api.blockUser(uid) }.onSuccess { _blocked.value = _blocked.value + uid }
        }
    }

    fun unblock(uid: String) {
        viewModelScope.launch {
            runCatching { api.unblockUser(uid) }.onSuccess { _blocked.value = _blocked.value - uid }
        }
    }
}


/** Motivos de denuncia (el código lo entiende el servidor; los tres nuevos desde 2026-11-06). */
private val REPORT_REASONS = listOf(
    "SPAM" to R.string.report_reason_spam,
    "OFFENSIVE" to R.string.report_reason_offensive,
    "HARASSMENT" to R.string.report_reason_harassment,
    "INAPPROPRIATE_PHOTO" to R.string.report_reason_photo,
    "OFFENSIVE_NAME" to R.string.report_reason_name,
    "FALSE_INFO" to R.string.report_reason_false_info,
    "OTHER" to R.string.report_reason_other
)

/**
 * Hoja de denuncia: eliges el motivo, cuentas qué pasa si quieres, puedes bloquear al autor de paso y ENVIAR. Después
 * se dice qué pasa ahora (lo revisa el equipo y te avisamos). Igual que ReportSheet de iOS.
 */
@Composable
fun ReportDialog(
    title: String,
    /** @nombre del autor (para el botón de bloquear); null = sin esa opción. */
    authorLabel: String?,
    onReport: (reason: String, alsoBlock: Boolean, details: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf("") }
    var alsoBlock by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (sent) {
                Text(stringResource(R.string.report_sent_title), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.report_sent_body), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xs))
                ReportButton(stringResource(R.string.report_ok), filled = true, onClick = onDismiss)
                return@Column
            }
            Text(title, style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.report_why), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
            REPORT_REASONS.forEach { (code, label) ->
                val on = reason == code
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) Terra.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
                        .border(1.dp, if (on) Terra else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .clickable { reason = code }
                        .padding(horizontal = Spacing.md, vertical = 10.dp)
                ) {
                    Text(stringResource(label), style = MaterialTheme.typography.bodyMedium,
                        color = if (on) Terra else MaterialTheme.colorScheme.onSurface)
                }
            }
            if (reason != null) {
                androidx.compose.material3.OutlinedTextField(
                    value = details, onValueChange = { details = it.take(500) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.report_details_hint)) },
                    minLines = 2, maxLines = 4
                )
            }
            if (authorLabel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { alsoBlock = !alsoBlock }
                        .padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(if (alsoBlock) "☑" else "☐",
                        color = if (alsoBlock) Terra else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.report_dialog_also_block, authorLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (alsoBlock) Terra else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.report_dialog_un_admin_lo_revisara),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ReportButton(stringResource(R.string.report_dialog_v2_cancelar), filled = false,
                    modifier = Modifier.weight(1f), onClick = onDismiss)
                ReportButton(stringResource(R.string.report_send), filled = true, enabled = reason != null,
                    modifier = Modifier.weight(1f)) {
                    reason?.let { onReport(it, alsoBlock, details.trim().ifBlank { null }); sent = true }
                }
            }
        }
    }
}

@Composable
private fun ReportButton(text: String, filled: Boolean, modifier: Modifier = Modifier.fillMaxWidth(),
                         enabled: Boolean = true, onClick: () -> Unit) {
    val clear = androidx.compose.ui.graphics.Color.Transparent
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) Terra.copy(alpha = if (enabled) 1f else 0.35f) else clear)
            .border(1.dp, if (filled) clear else MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = EyebrowTextStyle,
            color = if (filled) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
