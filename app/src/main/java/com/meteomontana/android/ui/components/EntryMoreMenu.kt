package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.ui.theme.Terra

/**
 * «⋯» of a logged entry with «Editar» and «Borrar» (journal «Mis vías/bloques» and board days, §26). Either action may be null.
 * The labels default to the journal ones.
 */
@Composable
fun EntryMoreMenu(
    onEdit: (() -> Unit)?, onDelete: (() -> Unit)?,
    editLabel: String = stringResource(R.string.journal_edit_ascent),
    deleteLabel: String = stringResource(R.string.journal_delete_from_log)
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(cumbrePillBg()).clickable { open = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.MoreHoriz, contentDescription = stringResource(R.string.journal_more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        CumbreDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (onEdit != null) DropdownMenuItem(
                text = { Text(editLabel) },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = Terra) },
                onClick = { open = false; onEdit() }
            )
            if (onDelete != null) DropdownMenuItem(
                text = { Text(deleteLabel, color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDelete() }
            )
        }
    }
}
