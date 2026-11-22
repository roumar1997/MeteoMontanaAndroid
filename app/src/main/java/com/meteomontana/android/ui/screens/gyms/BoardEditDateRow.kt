package com.meteomontana.android.ui.screens.gyms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.ui.theme.Terra
import kotlinx.datetime.LocalDate
import java.time.ZoneOffset

/** «CUÁNDO» of the board editor (§26): the day of the block, never in the future. Same picker as «Editar ascenso». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoardEditDateRow(date: LocalDate, onPick: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
            }
        )
        DatePickerDialog(onDismissRequest = { open = false }, confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { ms ->
                    val d = java.time.Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    onPick(LocalDate(d.year, d.monthValue, d.dayOfMonth))
                }
                open = false
            }) { Text("OK") }
        }) { DatePicker(state = state) }
    }
    Eyebrow(stringResource(R.string.ascent_when))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surface).clickable { open = true }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = Terra)
        val label = java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth)
            .format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMM yyyy", java.util.Locale.getDefault()))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
    }
}
