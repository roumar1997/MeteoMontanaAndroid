package com.meteomontana.android.ui.screens.users

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Terra

/** Estado del interruptor del asistente de IA de OTRO usuario, tal como lo ve un admin. */
data class AdminAssistantUi(val enabled: Boolean, val saving: Boolean = false)

/**
 * Bloque "SOLO ADMIN" del perfil de otro usuario: el interruptor que le activa o desactiva el asistente de IA. Solo se
 * pinta si quien mira es admin (el servidor además exige admin para leer y cambiar el interruptor).
 */
@Composable
fun AdminAssistantAccessCard(
    state: AdminAssistantUi,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, Terra.copy(alpha = 0.6f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            stringResource(R.string.admin_assistant_tag),
            style = EyebrowTextStyle,
            color = Terra
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.admin_assistant_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    stringResource(
                        if (state.enabled) R.string.admin_assistant_on else R.string.admin_assistant_off
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.enabled,
                onCheckedChange = onToggle,
                enabled = !state.saving,
                colors = SwitchDefaults.colors(checkedTrackColor = Terra, checkedThumbColor = Color.White)
            )
        }
        Text(
            stringResource(R.string.admin_assistant_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
