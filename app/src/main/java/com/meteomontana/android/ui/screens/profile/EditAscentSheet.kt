package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.AscentStyle
import com.meteomontana.android.ui.components.CumbreSheetShape
import com.meteomontana.android.ui.components.cumbreSheetSurface
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * «Editar ascenso» (menu «⋯» / deslizar de «Mis bloques» y «Mis vias»): fecha + estilo
 * en una hoja corta. Guarda con un solo boton; espejo de EditAscentSheet de iOS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditAscentSheet(
    initialDate: String,
    initialStyle: AscentStyle,
    onSave: (date: String, style: AscentStyle) -> Unit,
    onDismiss: () -> Unit
) {
    var date by remember { mutableStateOf(initialDate.take(10)) }
    var style by remember { mutableStateOf(initialStyle) }
    var showDatePicker by remember { mutableStateOf(false) }
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = runCatching {
                LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
            })
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            }
        ) { DatePicker(state = pickerState) }
    }
    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        shape = CumbreSheetShape
    ) {
        Column(
            Modifier.fillMaxWidth()
                .cumbreSheetSurface(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.lg)
        ) {
            Text(stringResource(R.string.journal_edit_ascent_title), style = EyebrowTextStyle, color = Terra,
                modifier = Modifier.padding(top = 4.dp))
            SheetLabel(stringResource(R.string.ascent_when))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { showDatePicker = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = Terra)
                Text(shortJournalDate(date), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground)
            }
            SheetLabel(stringResource(R.string.journal_edit_style))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surface).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                StyleSegment(stringResource(R.string.journal_style_none), style == AscentStyle.NONE,
                    onClick = { style = AscentStyle.NONE }, modifier = Modifier.weight(1f))
                StyleSegment(stringResource(R.string.ascent_onsight), style == AscentStyle.ONSIGHT,
                    onClick = { style = AscentStyle.ONSIGHT }, modifier = Modifier.weight(1f))
                StyleSegment(stringResource(R.string.ascent_flash), style == AscentStyle.FLASH,
                    onClick = { style = AscentStyle.FLASH }, modifier = Modifier.weight(1f))
            }
            Button(
                onClick = { onSave(date, style) },
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Terra, contentColor = Color.White)
            ) { Text(stringResource(R.string.journal_edit_save), style = EyebrowTextStyle, color = Color.White) }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, style = EyebrowTextStyle.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 18.dp, bottom = 7.dp))
}

@Composable
private fun StyleSegment(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(9.dp))
            .background(if (on) MaterialTheme.colorScheme.background else Color.Transparent)
            .border(1.dp, if (on) MaterialTheme.colorScheme.outline else Color.Transparent, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            color = if (on) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
