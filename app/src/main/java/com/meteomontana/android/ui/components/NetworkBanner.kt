package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.meteomontana.android.domain.port.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NetworkBannerViewModel @Inject constructor(
    networkMonitor: NetworkMonitor
) : ViewModel() {
    val isOnline = networkMonitor.isOnline
}

/**
 * Banner global "● SIN CONEXIÓN" que se muestra solo cuando NetworkMonitor reporta offline.
 * Pónlo arriba del NavHost en MainScreen para que aparezca en todas las pantallas.
 */
@Composable
fun NetworkBanner(viewModel: NetworkBannerViewModel = hiltViewModel()) {
    val online by viewModel.isOnline.collectAsState()
    if (online) return
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            "● SIN CONEXIÓN — usando datos guardados",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}
