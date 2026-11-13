package com.meteomontana.android.ui.screens.schools

import com.meteomontana.android.ui.components.SkeletonList
import com.meteomontana.android.ui.components.SkeletonSchoolRow
import com.meteomontana.android.util.CalendarLabels
import com.meteomontana.android.ui.theme.terraFillColor
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.ui.components.SchoolListItem
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.TerraBg
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R
import com.meteomontana.android.ui.components.CumbreSheetShape
import com.meteomontana.android.ui.components.cumbreSheetSurface
import kotlinx.coroutines.flow.first

// Cabecera de Escuelas (AYUDA, iconos con insignia, tema claro/oscuro y «Aportar»).

/**
 * Cabecera de Escuelas en UNA fila (como iOS): "? AYUDA" a la izquierda, los iconos en cápsula y "+ Aportar" relleno
 * a la derecha. Sin el título "Escuelas": ya lo dice la pestaña de abajo, y así los botones respiran (2026-10-14).
 * Fija, FUERA del PullToRefreshBox: dentro, el gesto de refrescar competía por el toque con APORTAR.
 */
@Composable
internal fun SchoolsHeader(
    unread: Long,
    chatUnread: Long = 0,
    onSearchUsers: () -> Unit,
    onChats: () -> Unit,
    onNotifications: () -> Unit,
    onSubmitSchool: () -> Unit,
    /** Elegir una foto y proponer la piedra en la escuela donde se hizo. */
    onSubmitBlockPhoto: () -> Unit
) {
    var aportando by remember { mutableStateOf(false) }
    if (aportando) {
        AportarSheet(
            onDismiss = { aportando = false },
            onPiedra = { aportando = false; onSubmitBlockPhoto() },
            onEscuela = { aportando = false; onSubmitSchool() }
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.meteomontana.android.ui.components.HelpButton(topicKey = "schools")
        Spacer(Modifier.weight(1f))
        com.meteomontana.android.ui.components.CumbrePillGroup {
            IconButton(onClick = onSearchUsers, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Outlined.PersonSearch, contentDescription = stringResource(R.string.search_users_title),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            IconButton(onClick = onChats, modifier = Modifier.size(38.dp)) {
                BadgedIcon(chatUnread) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = stringResource(R.string.chat_title),
                        tint = MaterialTheme.colorScheme.onBackground)
                }
            }
            // Tema ANTES que la campana: es el orden de iOS (buscar, chats, luna, campana).
            ThemeToggleButton()
            IconButton(onClick = onNotifications, modifier = Modifier.size(38.dp)) {
                BadgedIcon(unread) {
                    Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.notifications_title),
                        tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
        // UN solo botón, con la palabra y relleno: es la acción principal de la cabecera. Las dos formas de aportar
        // viven en la hoja, donde cada una cabe con su explicación.
        Text(
            stringResource(R.string.schools_contribute),
            color = Color.White,
            style = com.meteomontana.android.ui.theme.EyebrowTextStyle.copy(fontSize = 12.sp, letterSpacing = 0.5.sp),
            maxLines = 1,
            modifier = Modifier
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(terraFillColor())
                .clickable { aportando = true }
                .padding(horizontal = 12.dp, vertical = 11.dp)
        )
    }
}

/** Icono con la insignia de no leídos (número o "9+"). */
@Composable
internal fun BadgedIcon(count: Long, icon: @Composable () -> Unit) {
    if (count > 0) {
        BadgedBox(badge = {
            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                Text(if (count > 9) "9+" else count.toString(), color = Color.White)
            }
        }) { icon() }
    } else icon()
}

/** Sol / Luna que alterna el tema. Lee el ThemeManager vía hiltViewModel. */
@Composable
internal fun ThemeToggleButton() {
    val vm: ThemeToggleViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val isDark = when (mode) {
        com.meteomontana.android.ui.theme.ThemeMode.DARK -> true
        com.meteomontana.android.ui.theme.ThemeMode.LIGHT -> false
        com.meteomontana.android.ui.theme.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    IconButton(onClick = vm::toggle, modifier = Modifier.size(38.dp)) {
        Icon(
            imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
            contentDescription = stringResource(R.string.a11y_toggle_theme),
            tint = MaterialTheme.colorScheme.onBackground
        )
    }
}

@dagger.hilt.android.lifecycle.HiltViewModel
class ThemeToggleViewModel @javax.inject.Inject constructor(
    private val themeManager: com.meteomontana.android.ui.theme.ThemeManager
) : androidx.lifecycle.ViewModel() {
    val mode = themeManager.mode
    fun toggle() = themeManager.toggle()
}
