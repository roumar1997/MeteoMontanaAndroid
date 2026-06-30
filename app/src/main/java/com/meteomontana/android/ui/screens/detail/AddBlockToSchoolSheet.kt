package com.meteomontana.android.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.data.api.dto.CreateBlockRequest
import com.meteomontana.android.ui.components.CumbreChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBlockToSchoolSheet(
    schoolLat: Double,
    schoolLon: Double,
    onDismiss: () -> Unit,
    onSave: (CreateBlockRequest) -> Unit
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val blockTypes = listOf(
        "BLOCK" to stringResource(R.string.add_block_sheet_type_block),
        "PARKING" to stringResource(R.string.add_block_sheet_type_parking),
        "ZONE" to stringResource(R.string.add_block_sheet_type_zone)
    )

    var type by remember { mutableStateOf("BLOCK") }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var latStr by remember { mutableStateOf(schoolLat.toString()) }
    var lonStr by remember { mutableStateOf(schoolLon.toString()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.94f).padding(16.dp),
               verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.add_block_sheet_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)

            Label(stringResource(R.string.add_block_sheet_type))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                blockTypes.forEach { (value, label) ->
                    CumbreChip(label, type == value, { type = value })
                }
            }

            Label(stringResource(R.string.add_block_sheet_name))
            OutlinedTextField(value = name, onValueChange = { name = it },
                placeholder = { Text(when (type) {
                    "BLOCK" -> stringResource(R.string.add_block_sheet_name_hint_block)
                    "PARKING" -> stringResource(R.string.add_block_sheet_name_hint_parking)
                    else -> stringResource(R.string.add_block_sheet_name_hint_zone)
                }) },
                singleLine = true, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Label(stringResource(R.string.add_block_sheet_latitude))
                    OutlinedTextField(value = latStr, onValueChange = { latStr = it },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Column(modifier = Modifier.weight(1f)) {
                    Label(stringResource(R.string.add_block_sheet_longitude))
                    OutlinedTextField(value = lonStr, onValueChange = { lonStr = it },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }

            Label(stringResource(R.string.add_block_sheet_description))
            OutlinedTextField(value = description, onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth().height(80.dp))

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val lat = latStr.replace(",", ".").toDoubleOrNull() ?: schoolLat
                    val lon = lonStr.replace(",", ".").toDoubleOrNull() ?: schoolLon
                    onSave(CreateBlockRequest(
                        type = type, name = name.trim(),
                        lat = lat, lon = lon,
                        photoPath = null,
                        description = description.takeIf { it.isNotBlank() },
                        lines = emptyList()
                    ))
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1C1C1A), contentColor = Color.White
                ),
                shape = MaterialTheme.shapes.small
            ) { Text(stringResource(R.string.propose_submit)) }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
