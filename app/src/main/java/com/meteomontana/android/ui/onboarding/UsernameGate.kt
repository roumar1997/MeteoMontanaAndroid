package com.meteomontana.android.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.R
import com.meteomontana.android.data.api.dto.UpdateProfileRequest
import com.meteomontana.android.domain.usecase.profile.GetMyProfileUseCase
import com.meteomontana.android.domain.usecase.profile.UpdateMyProfileUseCase
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Mismas reglas que el backend (UpdateMyProfileUseCase): 3-20, [a-z0-9_]. */
private val USERNAME_REGEX = Regex("^[a-z0-9_]{3,20}$")

data class UsernameGateState(
    /** null = aún no se sabe (cargando perfil); true = falta username. */
    val needsUsername: Boolean? = null,
    val saving: Boolean = false,
    val error: String? = null
)

/**
 * Gate de username obligatorio: tras el tutorial de primer arranque, si el
 * perfil no tiene username (condición del SERVIDOR, no de la instalación:
 * reinstalar no lo re-muestra) se pide elegir uno antes de usar la app.
 * Sin username no hay mención al responder comentarios ni /s/u/{username}.
 */
@HiltViewModel
class UsernameGateViewModel @Inject constructor(
    private val getMyProfile: GetMyProfileUseCase,
    private val updateMyProfile: UpdateMyProfileUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(UsernameGateState())
    val state: StateFlow<UsernameGateState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Si el perfil no carga (offline), no bloqueamos la app: el gate
            // volverá a intentarlo en el siguiente arranque.
            runCatching { getMyProfile() }
                .onSuccess { _state.value = UsernameGateState(needsUsername = it.username == null) }
                .onFailure { _state.value = UsernameGateState(needsUsername = false) }
        }
    }

    fun save(username: String, taken: String, invalid: String, generic: String) {
        _state.value = _state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            runCatching { updateMyProfile(UpdateProfileRequest(username = username)) }
                .onSuccess { _state.value = UsernameGateState(needsUsername = false) }
                .onFailure { t ->
                    val msg = when ((t as? ClientRequestException)?.response?.status?.value) {
                        409 -> taken
                        400 -> invalid
                        else -> generic
                    }
                    _state.value = _state.value.copy(saving = false, error = msg)
                }
        }
    }
}

/**
 * Diálogo a pantalla completa, NO descartable (sin ✕ ni back): elegir username
 * es el último paso del primer arranque. Solo se pinta si [UsernameGateState.needsUsername].
 */
@Composable
fun UsernameGate(viewModel: UsernameGateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    if (state.needsUsername != true) return

    var username by remember { mutableStateOf("") }
    val valid = USERNAME_REGEX.matches(username)
    val taken = stringResource(R.string.username_gate_taken)
    val invalid = stringResource(R.string.username_gate_invalid)
    val generic = stringResource(R.string.username_gate_error)

    Dialog(
        onDismissRequest = { /* obligatorio: no se puede cerrar sin elegir */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .imePadding()
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                stringResource(R.string.username_gate_title).uppercase(),
                style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(R.string.username_gate_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.lg)
            )
            OutlinedTextField(
                value = username,
                onValueChange = { raw ->
                    username = raw.lowercase().replace(" ", "_").take(20)
                },
                prefix = { Text("@", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                placeholder = { Text(stringResource(R.string.username_gate_hint)) },
                singleLine = true,
                isError = state.error != null,
                supportingText = {
                    Text(
                        state.error ?: stringResource(R.string.username_gate_rules),
                        color = if (state.error != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(2.dp)
            )
            Button(
                onClick = { viewModel.save(username, taken, invalid, generic) },
                enabled = valid && !state.saving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg)
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(2.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.common_save).uppercase(), style = EyebrowTextStyle)
                }
            }
        }
    }
}
