package com.chuckerteam.chucker.internal.ui.transaction

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.chuckerteam.chucker.R

/**
 * Copy button row of the payload tab: tap copies the raw payload, long-press opens a menu with
 * the "raw" and "formatted" options.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PayloadCopyRow(
    onCopyRaw: () -> Unit,
    onCopyFormatted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Box(
                modifier =
                    Modifier
                        .combinedClickable(
                            role = Role.Button,
                            onClick = onCopyRaw,
                            onLongClick = { menuExpanded = true },
                        ).padding(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.chucker_ic_copy),
                    contentDescription = stringResource(R.string.chucker_copy_response),
                    tint = Color.Unspecified,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chucker_copy_raw)) },
                    onClick = {
                        menuExpanded = false
                        onCopyRaw()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chucker_copy_formatted)) },
                    onClick = {
                        menuExpanded = false
                        onCopyFormatted()
                    },
                )
            }
        }
    }
}
