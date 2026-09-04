package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.meteomontana.android.domain.model.SchoolPresence
import com.meteomontana.android.domain.usecase.presence.ClearSchoolPresenceUseCase
import com.meteomontana.android.domain.usecase.presence.GetSchoolPresenceUseCase
import com.meteomontana.android.domain.usecase.presence.MarkSchoolPresenceUseCase
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SchoolPresenceViewModel @Inject constructor(
    private val getPresence: GetSchoolPresenceUseCase,
    private val markPresence: MarkSchoolPresenceUseCase,
    private val clearPresence: ClearSchoolPresenceUseCase
) : ViewModel() {
    var people by mutableStateOf<List<SchoolPresence>>(emptyList())
        private set
    var iAmHere by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    // Antes se tragaba cualquier fallo en silencio — "Ya no estoy" parecía no
    // hacer nada y no había forma de saber por qué. Si algo falla, se ve.
    var errorText by mutableStateOf<String?>(null)
        private set

    fun load(schoolId: String, myUid: String?) {
        viewModelScope.launch {
            try {
                val list = getPresence.execute(schoolId)
                people = list
                iAmHere = myUid != null && list.any { it.uid == myUid }
            } catch (e: Exception) {
                errorText = "No se pudo cargar quién hay aquí: ${e.message}"
            }
        }
    }

    fun toggle(schoolId: String, myUid: String?) {
        if (loading) return
        loading = true
        errorText = null
        viewModelScope.launch {
            try {
                if (iAmHere) clearPresence.execute(schoolId) else markPresence.execute(schoolId)
            } catch (e: Exception) {
                errorText = "${if (iAmHere) "No se pudo quitar" else "No se pudo marcar"} la presencia: ${e.message}"
            }
            loading = false
            load(schoolId, myUid)
        }
    }
}

/**
 * "Estoy aquí": quién está presente en esta escuela ahora mismo, con un botón
 * para marcarte tú también. Fija bajo la cabecera, fuera del scroll — espejo
 * exacto de `SchoolPresenceRow.swift`.
 */
@Composable
fun SchoolPresenceRow(
    schoolId: String,
    schoolName: String,
    myUid: String?,
    onOpenChat: (uid: String, name: String) -> Unit,
    onOpenSchoolChat: () -> Unit,
    // Sube al deslizar hacia abajo en la ficha (pull-to-refresh) para pedir
    // otra vez quién hay — sin caché, es información en vivo (Álvaro,
    // 2026-09-04: mejor a demanda que sondear sola cada X segundos).
    refreshKey: Int = 0,
    viewModel: SchoolPresenceViewModel = hiltViewModel()
) {
    var showPrivacyNote by remember { mutableStateOf(false) }
    var showAllPresent by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(schoolId, myUid, refreshKey) {
        viewModel.load(schoolId, myUid)
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                .height(34.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (viewModel.people.isNotEmpty()) {
                Row(
                    modifier = Modifier.clickable { showAllPresent = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        viewModel.people.take(4).forEachIndexed { idx, person ->
                            Box(
                                modifier = Modifier
                                    .padding(start = (idx * 12).dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                            ) {
                                PresenceAvatar(url = person.photoUrl, size = 18.dp)
                            }
                        }
                    }
                    Spacer(Modifier.padding(start = (viewModel.people.take(4).size * 12 + 6).dp))
                    Text(
                        "${viewModel.people.size} aquí ahora",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            PresenceChatButton(onClick = onOpenSchoolChat)
            Spacer(Modifier.padding(start = Spacing.xs))
            PresenceMarkButton(
                iAmHere = viewModel.iAmHere,
                loading = viewModel.loading,
                onClick = {
                    if (viewModel.iAmHere) viewModel.toggle(schoolId, myUid)
                    else showPrivacyNote = true
                }
            )
        }
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
        viewModel.errorText?.let { err ->
            Text(
                err,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            )
        }
    }

    if (showPrivacyNote) {
        PresencePrivacySheet(
            onConfirm = {
                showPrivacyNote = false
                viewModel.toggle(schoolId, myUid)
            },
            onDismiss = { showPrivacyNote = false }
        )
    }

    if (showAllPresent) {
        PresenceAllSheet(
            people = viewModel.people,
            myUid = myUid,
            onDismiss = { showAllPresent = false },
            onOpenChat = { uid, name ->
                showAllPresent = false
                onOpenChat(uid, name)
            }
        )
    }
}

@Composable
private fun PresenceAvatar(url: String?, size: androidx.compose.ui.unit.Dp) {
    if (url != null) {
        AsyncImage(model = url, contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape))
    } else {
        Box(
            modifier = Modifier.size(size).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

@Composable
private fun PresenceChatButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Chat, contentDescription = "Chat de la escuela",
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun PresenceMarkButton(iAmHere: Boolean, loading: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (iAmHere) MaterialTheme.colorScheme.onSurfaceVariant else Terra)
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Place, contentDescription = null,
            tint = Color.White, modifier = Modifier.size(11.dp))
        Spacer(Modifier.padding(start = 4.dp))
        Text(
            if (iAmHere) "Ya no estoy" else "Estoy aquí",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = Serif, fontWeight = FontWeight.Bold),
            color = Color.White
        )
    }
}

@Composable
private fun PresencePrivacySheet(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(Spacing.lg)
        ) {
            Column {
                Text(
                    "Al marcar \"Estoy aquí\"",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = Serif, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.padding(top = Spacing.sm))
                Text(
                    "Cualquiera que abra esta escuela verá que estás aquí y podrá escribirte por chat — aunque tu perfil sea privado. Nadie podrá ver tu perfil completo si no te sigue. Se desactiva sola pasadas 10 horas, o puedes quitarla tú antes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.padding(top = Spacing.md))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Terra)
                        .clickable(onClick = onConfirm)
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Entendido, estoy aquí",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Serif, fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun PresenceAllSheet(
    people: List<SchoolPresence>,
    myUid: String?,
    onDismiss: () -> Unit,
    onOpenChat: (uid: String, name: String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${people.size} aquí ahora",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = Serif, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Cerrar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Terra,
                        modifier = Modifier.clickable(onClick = onDismiss)
                    )
                }
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                LazyColumn(modifier = Modifier.height(400.dp)) {
                    items(people) { person ->
                        val isMe = person.uid == myUid
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isMe) {
                                    onOpenChat(person.uid, person.displayName ?: person.username ?: "Usuario")
                                }
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PresenceAvatar(url = person.photoUrl, size = 36.dp)
                            Spacer(Modifier.padding(start = Spacing.sm))
                            Text(
                                if (isMe) "Tú" else (person.displayName ?: person.username ?: "Usuario"),
                                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            if (!isMe) {
                                Icon(Icons.Outlined.Chat, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
