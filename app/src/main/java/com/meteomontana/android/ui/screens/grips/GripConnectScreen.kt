package com.meteomontana.android.ui.screens.grips

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.GripScaleDevice

@Composable
fun GripConnectScreen(
    onBack: () -> Unit,
    onConnected: () -> Unit,
    viewModel: GripConnectViewModel = hiltViewModel()
) {
    val devices by viewModel.devices.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    var renameTarget by remember { mutableStateOf<GripScaleDevice?>(null) }
    var bluetoothOn by remember { mutableStateOf(viewModel.isBluetoothEnabled) }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Manifest.permission.BLUETOOTH_SCAN
    } else {
        Manifest.permission.ACCESS_FINE_LOCATION
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted && bluetoothOn) viewModel.startScan() }
    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* onResume ya re-comprueba isBluetoothEnabled */ }

    fun tryStartScan() {
        if (!bluetoothOn) return
        if (viewModel.hasPermission) viewModel.startScan() else permLauncher.launch(permission)
    }

    LaunchedEffect(Unit) { tryStartScan() }

    // Pantalla siempre encendida — las manos están ocupadas con la báscula.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Re-comprueba el estado del Bluetooth al volver de Ajustes/activarlo.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowOn = viewModel.isBluetoothEnabled
                if (nowOn != bluetoothOn) { bluetoothOn = nowOn; if (nowOn) tryStartScan() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.stopScan(); onBack() }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Conectar báscula", style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (!bluetoothOn) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("El Bluetooth está apagado", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
                    Button(onClick = { enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }) {
                        Text("ACTIVAR BLUETOOTH")
                    }
                }
            }
            return@Column
        }

        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (scanning) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).then(Modifier))
                Text(
                    if (scanning) "Buscando básculas WH-C06 cercanas…" else "Escaneo detenido",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (devices.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Enciende la báscula y espera unos segundos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(devices, key = { it.id }) { device ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { viewModel.connect(device.id); onConnected() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Bluetooth, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(device.alias ?: "Báscula WH-C06", style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground)
                            Text("${device.id} · señal ${device.rssi} dBm",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { renameTarget = device }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Renombrar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }

    renameTarget?.let { device ->
        var text by remember(device.id) { mutableStateOf(device.alias ?: "") }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Nombre de la báscula") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text("p.ej. Mi báscula azul") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameDevice(device.id, text.trim())
                    renameTarget = null
                }) { Text("GUARDAR") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}
