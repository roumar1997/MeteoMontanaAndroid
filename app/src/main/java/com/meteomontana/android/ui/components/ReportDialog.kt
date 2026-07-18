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
               alsoBlockUid: String? = null) {
        _hiddenIds.value = _hiddenIds.value + "$targetType:$targetId"
        viewModelScope.launch {
            runCatching { api.report(targetType, targetId, reason) }
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

/**
 * Hoja de denuncia (maqueta A aprobada): motivos de un toque + opción de
 * bloquear al autor de paso.
 */
@Composable
fun ReportDialog(
    title: String,
    /** @nombre del autor (para el botón de bloquear); null = sin esa opción. */
    authorLabel: String?,
    onReport: (reason: String, alsoBlock: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var alsoBlock by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(4.dp))
                .padding(Spacing.md)
        ) {
            Text(title, style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.sm))
            listOf(
                "SPAM" to "Spam o publicidad",
                "OFFENSIVE" to "Ofensivo o acoso",
                "FALSE_INFO" to "Información falsa o peligrosa",
                "OTHER" to "Otro motivo"
            ).forEach { (code, label) ->
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                        .clickable { onReport(code, alsoBlock) }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (authorLabel != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, if (alsoBlock) Terra else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(4.dp))
                        .clickable { alsoBlock = !alsoBlock }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(if (alsoBlock) "☑" else "☐",
                        color = if (alsoBlock) Terra else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("También bloquear a $authorLabel",
                        style = EyebrowTextStyle,
                        color = if (alsoBlock) Terra else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Text("Un admin lo revisará. El contenido denunciado deja de mostrarse para ti al instante.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.sm))
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text("CANCELAR", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
