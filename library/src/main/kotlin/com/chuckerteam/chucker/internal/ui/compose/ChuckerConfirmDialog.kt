package com.chuckerteam.chucker.internal.ui.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.chuckerteam.chucker.internal.data.model.DialogData

/** Compose replacement for `Context.showDialog(DialogData, ...)`. */
@Composable
internal fun ChuckerConfirmDialog(
    dialogData: DialogData,
    onPositiveClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogData.title) },
        text = { Text(dialogData.message) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onPositiveClick()
                },
            ) { Text(dialogData.positiveButtonText.orEmpty()) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dialogData.negativeButtonText.orEmpty()) }
        },
    )
}
